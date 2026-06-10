package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.SecretKey;

final class VoiceConnection implements VoiceClientConnection {

	private final PlayerReference playerId;
	private final InetAddress address;
	private final int port;
	private final SecretKey aesKey;
	private final AtomicBoolean connected;
	private final AtomicLong lastPacketTime;
	private volatile MicrophoneState microphoneState;
	private volatile Position position;

	VoiceConnection(PlayerReference playerId, InetAddress address, int port, SecretKey aesKey, long connectTime) {
		this.playerId = playerId;
		this.address = address;
		this.port = port;
		this.aesKey = aesKey;
		this.connected = new AtomicBoolean(true);
		this.lastPacketTime = new AtomicLong(connectTime);
		this.microphoneState = MicrophoneState.MUTED;
		this.position = new Position(0, 0, 0);
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
	}

	@Override
	public long getLastPacketTime() {
		return lastPacketTime.get();
	}
}
