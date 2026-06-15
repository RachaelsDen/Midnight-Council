package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VoiceClientService implements VoiceClientConnection {

	static final int MAX_PENDING_FRAMES = 10;

	private final VoiceCodec encoderCodec;
	private final Map<PlayerReference, VoiceCodec> decoderCodecs = new HashMap<>();
	private final LinkedList<short[]> pendingAudio = new LinkedList<>();

	private PlayerReference playerId;
	private MicrophoneState microphoneState = MicrophoneState.MUTED;
	private Position position = new Position(0, 0, 0);
	private boolean connected;
	private long sequenceNumber;
	private long lastPacketTime;

	public VoiceClientService(VoiceCodec codec) {
		this.encoderCodec = Objects.requireNonNull(codec, "codec");
	}

	public void connect(PlayerReference playerId) {
		Objects.requireNonNull(playerId, "playerId");
		if (connected) {
			throw new IllegalStateException("Already connected");
		}
		this.playerId = playerId;
		this.connected = true;
		this.sequenceNumber = 0;
		this.lastPacketTime = 0L;
		this.pendingAudio.clear();
		clearDecoderCodecs();
	}

	public void disconnect() {
		if (!connected) {
			throw new IllegalStateException("Not connected");
		}
		this.connected = false;
		this.playerId = null;
		this.pendingAudio.clear();
		clearDecoderCodecs();
	}

	public AudioPacket sendAudio(short[] pcmData) {
		if (!connected) {
			throw new IllegalStateException("Not connected");
		}
		if (microphoneState == MicrophoneState.MUTED) {
			return null;
		}
		byte[] encoded = encoderCodec.encode(pcmData);
		long seq = sequenceNumber++;
		long timestamp = System.currentTimeMillis();
		lastPacketTime = timestamp;
		return new AudioPacket(playerId, encoded, seq, timestamp);
	}

	public short[] receiveAudio(AudioPacket packet) {
		if (!connected) {
			return null;
		}
		VoiceCodec decoderCodec = decoderCodecs.computeIfAbsent(packet.senderId(), this::createDecoderCodec);
		short[] pcm = decoderCodec.decode(packet.encodedData());
		lastPacketTime = System.currentTimeMillis();
		if (pendingAudio.size() == MAX_PENDING_FRAMES) {
			pendingAudio.removeFirst();
		}
		pendingAudio.add(pcm);
		return pcm;
	}

	public List<short[]> getPendingAudio() {
		return new ArrayList<>(pendingAudio);
	}

	public List<short[]> drainPendingAudio() {
		List<short[]> drained = new ArrayList<>(pendingAudio);
		pendingAudio.clear();
		return drained;
	}

	public void clearPendingAudio() {
		pendingAudio.clear();
	}

	private VoiceCodec createDecoderCodec(PlayerReference ignoredSenderId) {
		return VoiceCodec.builder()
			.sampleRate(encoderCodec.getSampleRate())
			.channels(encoderCodec.getChannels())
			.frameSize(encoderCodec.getFrameSize())
			.build();
	}

	private void clearDecoderCodecs() {
		for (VoiceCodec decoderCodec : decoderCodecs.values()) {
			decoderCodec.close();
		}
		decoderCodecs.clear();
	}

	public void setPosition(Position position) {
		this.position = Objects.requireNonNull(position, "position");
	}

	@Override
	public PlayerReference getPlayerId() {
		return playerId;
	}

	@Override
	public MicrophoneState getMicrophoneState() {
		return microphoneState;
	}

	@Override
	public void setMicrophoneState(MicrophoneState state) {
		this.microphoneState = Objects.requireNonNull(state, "state");
	}

	@Override
	public Position getPosition() {
		return position;
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public void sendPacket(AudioPacket packet) {
		receiveAudio(packet);
	}

	@Override
	public long getLastPacketTime() {
		return lastPacketTime;
	}
}
