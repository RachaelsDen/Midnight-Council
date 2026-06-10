package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class VoiceClientService implements VoiceClientConnection {

	private final VoiceCodec codec;
	private final LinkedList<short[]> pendingAudio = new LinkedList<>();

	private PlayerReference playerId;
	private MicrophoneState microphoneState = MicrophoneState.MUTED;
	private Position position = new Position(0, 0, 0);
	private boolean connected;
	private long sequenceNumber;
	private long lastPacketTime;

	public VoiceClientService(VoiceCodec codec) {
		this.codec = Objects.requireNonNull(codec, "codec");
	}

	public void connect(PlayerReference playerId) {
		Objects.requireNonNull(playerId, "playerId");
		if (connected) {
			throw new IllegalStateException("Already connected");
		}
		this.playerId = playerId;
		this.connected = true;
		this.sequenceNumber = 0;
		this.pendingAudio.clear();
	}

	public void disconnect() {
		if (!connected) {
			throw new IllegalStateException("Not connected");
		}
		this.connected = false;
		this.playerId = null;
		this.pendingAudio.clear();
	}

	public AudioPacket sendAudio(short[] pcmData) {
		if (!connected) {
			throw new IllegalStateException("Not connected");
		}
		byte[] encoded = codec.encode(pcmData);
		long seq = sequenceNumber++;
		long timestamp = System.currentTimeMillis();
		lastPacketTime = timestamp;
		return new AudioPacket(playerId, encoded, seq, timestamp);
	}

	public short[] receiveAudio(AudioPacket packet) {
		if (!connected) {
			return null;
		}
		short[] pcm = codec.decode(packet.encodedData());
		lastPacketTime = System.currentTimeMillis();
		pendingAudio.add(pcm);
		return pcm;
	}

	public List<short[]> getPendingAudio() {
		return new ArrayList<>(pendingAudio);
	}

	public void clearPendingAudio() {
		pendingAudio.clear();
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
