package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

final class VoiceConnection implements VoiceClientConnection {

	private static final long INITIAL_RECEIVED_SEQUENCE = -1L;

	private final PlayerReference playerId;
	private final InetAddress address;
	private final int port;
	private final SecretKey aesKey;
	private final AtomicBoolean connected;
	private final AtomicLong lastPacketTime;
	private final AtomicLong sendSequence;
	private final AtomicLong lastReceivedSequence;
	private Consumer<AudioPacket> sendCallback;
	private volatile MicrophoneState microphoneState;
	private volatile Position position;

	VoiceConnection(PlayerReference playerId, InetAddress address, int port, SecretKey aesKey, long connectTime) {
		this(playerId, address, port, aesKey, connectTime, null);
	}

	VoiceConnection(PlayerReference playerId, InetAddress address, int port, SecretKey aesKey, long connectTime, Consumer<AudioPacket> sendCallback) {
		this.playerId = playerId;
		this.address = address;
		this.port = port;
		this.aesKey = aesKey;
		this.connected = new AtomicBoolean(true);
		this.lastPacketTime = new AtomicLong(connectTime);
		this.sendSequence = new AtomicLong(0);
		this.lastReceivedSequence = new AtomicLong(INITIAL_RECEIVED_SEQUENCE);
		this.sendCallback = sendCallback != null ? sendCallback : packet -> {};
		this.microphoneState = MicrophoneState.MUTED;
		this.position = new Position(0, 0, 0);
	}

	long nextSendSequence() {
		return sendSequence.getAndIncrement();
	}

	boolean checkAndAdvanceReceivedSequence(long seq) {
		while (true) {
			long last = lastReceivedSequence.get();
			if (seq <= last) return false;
			if (lastReceivedSequence.compareAndSet(last, seq)) return true;
		}
	}

	void setSendCallback(Consumer<AudioPacket> callback) {
		this.sendCallback = callback != null ? callback : packet -> {};
	}

	PlayerReference playerId() {
		return playerId;
	}

	InetAddress address() {
		return address;
	}

	int port() {
		return port;
	}

	SecretKey aesKey() {
		return aesKey;
	}

	void markSeen(long timestamp) {
		lastPacketTime.set(timestamp);
	}

	void setConnected(boolean value) {
		connected.set(value);
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
		this.microphoneState = state;
	}

	@Override
	public Position getPosition() {
		return position;
	}

	void setPosition(Position position) {
		this.position = position;
	}

	@Override
	public boolean isConnected() {
		return connected.get();
	}

	@Override
	public void sendPacket(AudioPacket packet) {
		sendCallback.accept(packet);
	}

	@Override
	public long getLastPacketTime() {
		return lastPacketTime.get();
	}
}
