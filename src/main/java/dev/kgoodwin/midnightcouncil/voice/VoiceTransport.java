package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceRoutingStrategy;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class VoiceTransport implements VoiceServer {

	private static final int MAX_PACKET_SIZE = 2048;
	private static final int IV_LENGTH = 12;
	private static final long SESSION_TIMEOUT_MS = 15_000L;
	private static final long CLEANUP_INTERVAL_MS = 5_000L;

	private final Map<PlayerReference, VoiceConnection> connections = new ConcurrentHashMap<>();
	private final Map<SocketAddress, PlayerReference> addressMap = new ConcurrentHashMap<>();
	private final VoiceRoutingStrategy routingStrategy;

	private volatile DatagramSocket socket;
	private volatile Thread listenerThread;
	private volatile Thread cleanupThread;
	private volatile boolean running;

	public VoiceTransport(VoiceRoutingStrategy routingStrategy) {
		this.routingStrategy = routingStrategy;
	}

	@Override
	public synchronized void start(int port) {
		if (running) {
			return;
		}
		try {
			socket = new DatagramSocket(port);
		} catch (SocketException e) {
			throw new IllegalStateException("Failed to open voice UDP socket on port " + port, e);
		}
		running = true;
		listenerThread = new Thread(this::runListenerLoop, "midnight-voice-listener");
		listenerThread.start();
		cleanupThread = new Thread(this::runCleanupLoop, "midnight-voice-cleanup");
		cleanupThread.start();
	}

	@Override
	public synchronized void stop() {
		if (!running) {
			return;
		}
		running = false;
		DatagramSocket current = socket;
		socket = null;
		if (current != null && !current.isClosed()) {
			current.close();
		}
		interruptThread(listenerThread);
		interruptThread(cleanupThread);
		listenerThread = null;
		cleanupThread = null;
		connections.clear();
		addressMap.clear();
	}

	@Override
	public void connect(VoiceClientConnection connection) {
		if (!(connection instanceof VoiceConnection vc)) {
			throw new IllegalArgumentException("Connection must be a VoiceConnection");
		}
		connections.put(vc.getPlayerId(), vc);
		addressMap.put(new InetSocketAddress(vc.address(), vc.port()), vc.getPlayerId());
	}

	@Override
	public void disconnect(VoiceClientConnection connection) {
		if (!(connection instanceof VoiceConnection vc)) {
			return;
		}
		connections.remove(vc.getPlayerId());
		addressMap.remove(new InetSocketAddress(vc.address(), vc.port()));
		vc.setConnected(false);
	}

	@Override
	public Collection<VoiceClientConnection> getConnections() {
		return new ArrayList<>(connections.values());
	}

	@Override
	public void sendAudio(AudioPacket packet) {
		if (!running) {
			return;
		}
		Collection<VoiceClientConnection> recipients = routingStrategy.route(this, packet, null);
		for (VoiceClientConnection recipient : recipients) {
			if (recipient instanceof VoiceConnection vc) {
				sendAudioToConnection(vc, packet);
			}
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	DatagramSocket socket() {
		return socket;
	}

	private void runListenerLoop() {
		byte[] buffer = new byte[MAX_PACKET_SIZE];
		while (running) {
			DatagramSocket current = socket;
			if (current == null) {
				return;
			}
			DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
			try {
				current.receive(datagram);
				handleDatagram(datagram);
			} catch (SocketException e) {
				if (running) {
					return;
				}
			} catch (IOException e) {
				if (running) {
				}
			}
		}
	}

	private void runCleanupLoop() {
		while (running) {
			try {
				Thread.sleep(CLEANUP_INTERVAL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			cleanupExpiredSessions();
		}
	}

	private void handleDatagram(DatagramPacket datagram) {
		SocketAddress address = new InetSocketAddress(datagram.getAddress(), datagram.getPort());
		byte[] data = Arrays.copyOf(datagram.getData(), datagram.getLength());

		if (data.length == 0) {
			return;
		}

		PlayerReference playerId = addressMap.get(address);

		if (playerId == null && data.length > 0 && data[0] == PacketType.CONNECT.id) {
			handleConnect(datagram, data);
			return;
		}

		if (playerId == null) {
			return;
		}

		VoiceConnection connection = connections.get(playerId);
		if (connection == null) {
			addressMap.remove(address);
			return;
		}

		if (data.length <= Long.BYTES) {
			return;
		}

		ByteBuffer buf = ByteBuffer.wrap(data);
		long sequenceNumber = buf.getLong();
		byte[] encryptedPayload = new byte[data.length - Long.BYTES];
		buf.get(encryptedPayload);

		byte[] decrypted;
		try {
			decrypted = CryptoUtils.decrypt(encryptedPayload, connection.aesKey(), sequenceNumber);
		} catch (Exception e) {
			return;
		}

		if (decrypted.length == 0) {
			return;
		}

		long now = System.currentTimeMillis();
		connection.markSeen(now);

		PacketType type = PacketType.fromId(decrypted[0]);
		switch (type) {
			case AUDIO -> handleIncomingAudio(connection, decrypted);
			case KEEPALIVE -> handleKeepAlive(connection, decrypted);
			case DISCONNECT -> handleDisconnectFromClient(connection, address);
			default -> {}
		}
	}

	private void handleConnect(DatagramPacket datagram, byte[] data) {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1))) {
			int idLength = dis.readInt();
			String idValue = new String(dis.readNBytes(idLength), StandardCharsets.UTF_8);
			PlayerReference playerId = PlayerReference.ofName(idValue);

			int keyLength = dis.readInt();
			byte[] keyBytes = dis.readNBytes(keyLength);
			SecretKey aesKey = new SecretKeySpec(keyBytes, "AES");

			long now = System.currentTimeMillis();
			VoiceConnection connection = new VoiceConnection(
				playerId, datagram.getAddress(), datagram.getPort(), aesKey, now
			);
			VoiceConnection previous = connections.put(playerId, connection);
			if (previous != null) {
				addressMap.remove(new InetSocketAddress(previous.address(), previous.port()));
				previous.setConnected(false);
			}
			addressMap.put(new InetSocketAddress(datagram.getAddress(), datagram.getPort()), playerId);

			byte[] ack = serializeConnectAck(true);
			sendRaw(datagram.getAddress(), datagram.getPort(), ack);
		} catch (IOException e) {
			byte[] ack = serializeConnectAck(false);
			sendRaw(datagram.getAddress(), datagram.getPort(), ack);
		}
	}

	private void handleDisconnectFromClient(VoiceConnection connection, SocketAddress address) {
		connections.remove(connection.getPlayerId());
		addressMap.remove(address);
		connection.setConnected(false);
	}

	private void handleKeepAlive(VoiceConnection connection, byte[] data) {
		long seq = System.currentTimeMillis();
		byte[] payload = serializeKeepalivePayload();
		sendEncrypted(connection, payload, seq);
	}

	private void handleIncomingAudio(VoiceConnection sender, byte[] decrypted) {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decrypted, 1, decrypted.length - 1))) {
			int audioLength = dis.readInt();
			byte[] audioData = dis.readNBytes(audioLength);
			long sequenceNumber = dis.readLong();
			long timestamp = dis.readLong();

			AudioPacket packet = new AudioPacket(sender.getPlayerId(), audioData, sequenceNumber, timestamp);
			Collection<VoiceClientConnection> recipients = routingStrategy.route(this, packet, null);

			for (VoiceClientConnection recipient : recipients) {
				if (recipient instanceof VoiceConnection vc && !vc.getPlayerId().equals(sender.getPlayerId())) {
					sendAudioToConnection(vc, packet);
				}
			}
		} catch (IOException e) {
		}
	}

	private void sendAudioToConnection(VoiceConnection connection, AudioPacket packet) {
		byte[] payload = serializeAudioPayload(packet);
		long seq = System.currentTimeMillis();
		sendEncrypted(connection, payload, seq);
	}

	private void sendEncrypted(VoiceConnection connection, byte[] payload, long sequenceNumber) {
		DatagramSocket current = socket;
		if (current == null || current.isClosed()) {
			return;
		}
		byte[] encrypted = CryptoUtils.encrypt(payload, connection.aesKey(), sequenceNumber);
		byte[] datagramBytes = ByteBuffer.allocate(Long.BYTES + encrypted.length)
			.putLong(sequenceNumber)
			.put(encrypted)
			.array();
		sendRaw(connection.address(), connection.port(), datagramBytes);
	}

	private void sendRaw(InetAddress address, int port, byte[] data) {
		DatagramSocket current = socket;
		if (current == null || current.isClosed()) {
			return;
		}
		try {
			current.send(new DatagramPacket(data, data.length, address, port));
		} catch (IOException e) {
		}
	}

	private void cleanupExpiredSessions() {
		long now = System.currentTimeMillis();
		long cutoff = now - SESSION_TIMEOUT_MS;
		for (VoiceConnection connection : connections.values()) {
			if (connection.getLastPacketTime() < cutoff) {
				connections.remove(connection.getPlayerId());
				addressMap.remove(new InetSocketAddress(connection.address(), connection.port()));
				connection.setConnected(false);
			}
		}
	}

	private static byte[] serializeConnectAck(boolean success) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeBoolean(success);
			return bos.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static byte[] serializeConnectPacket(PlayerReference playerId, SecretKey key) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.CONNECT.id);
			byte[] idBytes = playerId.value().getBytes(StandardCharsets.UTF_8);
			dos.writeInt(idBytes.length);
			dos.write(idBytes);
			byte[] keyBytes = key.getEncoded();
			dos.writeInt(keyBytes.length);
			dos.write(keyBytes);
			return bos.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static byte[] serializeDisconnectPacket() {
		return new byte[]{PacketType.DISCONNECT.id};
	}

	static byte[] serializeKeepalivePacket(SecretKey key, long sequenceNumber) {
		byte[] payload = serializeKeepalivePayload();
		byte[] encrypted = CryptoUtils.encrypt(payload, key, sequenceNumber);
		return ByteBuffer.allocate(Long.BYTES + encrypted.length)
			.putLong(sequenceNumber)
			.put(encrypted)
			.array();
	}

	private static byte[] serializeKeepalivePayload() {
		return new byte[]{PacketType.KEEPALIVE.id};
	}

	private static byte[] serializeAudioPayload(AudioPacket packet) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.AUDIO.id);
			dos.writeInt(packet.encodedData().length);
			dos.write(packet.encodedData());
			dos.writeLong(packet.sequenceNumber());
			dos.writeLong(packet.timestamp());
			return bos.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void interruptThread(Thread thread) {
		if (thread != null) {
			thread.interrupt();
		}
	}
}
