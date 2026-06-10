package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
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
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class VoiceTransport implements VoiceServer {

	private static final int MAX_PACKET_SIZE = 2048;
	private static final int MAX_AUDIO_PAYLOAD_SIZE = 1400;
	private static final long SESSION_TIMEOUT_MS = 15_000L;
	private static final long CLEANUP_INTERVAL_MS = 5_000L;
	private static final long CONNECT_TOKEN_TTL_MS = 60_000L;
	private static final String TEST_ONLY_DEFAULT_KEY_EXCHANGE_SECRET = "midnight-council-default-voice-key-exchange-secret";
	private static final String TEST_ONLY_DEFAULT_CONNECT_TOKEN_SECRET = "midnight-council-default-voice-connect-token-secret";
	private static final byte[] CONNECT_TOKEN_DOMAIN = "midnight-voice-connect".getBytes(StandardCharsets.UTF_8);
	private static final int CONNECT_TOKEN_MAC_LENGTH = 32;
	private static final int CONNECT_TOKEN_LENGTH = Long.BYTES + CONNECT_TOKEN_MAC_LENGTH;

	private final Map<PlayerReference, VoiceConnection> connections = new ConcurrentHashMap<>();
	private final Map<SocketAddress, PlayerReference> addressMap = new ConcurrentHashMap<>();
	private final VoiceRoutingStrategy routingStrategy;
	private final SecretKey serverKeyExchangeKey;
	private final SecretKey connectTokenKey;

	private volatile DatagramSocket socket;
	private volatile Thread listenerThread;
	private volatile Thread cleanupThread;
	private volatile boolean running;

	public VoiceTransport(VoiceRoutingStrategy routingStrategy, String keyExchangeSecret, String connectTokenSecret) {
		this.routingStrategy = Objects.requireNonNull(routingStrategy, "routingStrategy");
		if (keyExchangeSecret == null || keyExchangeSecret.isBlank()) {
			throw new IllegalArgumentException("keyExchangeSecret cannot be blank");
		}
		if (connectTokenSecret == null || connectTokenSecret.isBlank()) {
			throw new IllegalArgumentException("connectTokenSecret cannot be blank");
		}
		if (keyExchangeSecret.equals(connectTokenSecret)) {
			throw new IllegalArgumentException("keyExchangeSecret and connectTokenSecret must differ");
		}
		this.serverKeyExchangeKey = deriveSecretKey(keyExchangeSecret);
		this.connectTokenKey = deriveSecretKey(connectTokenSecret);
	}

	VoiceTransport(VoiceRoutingStrategy routingStrategy) {
		this(routingStrategy, TEST_ONLY_DEFAULT_KEY_EXCHANGE_SECRET, TEST_ONLY_DEFAULT_CONNECT_TOKEN_SECRET);
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
		vc.setSendCallback(packet -> sendAudioToConnection(vc, packet));
		registerConnection(vc, new InetSocketAddress(vc.address(), vc.port()));
	}

	@Override
	public void disconnect(VoiceClientConnection connection) {
		if (!(connection instanceof VoiceConnection vc)) {
			return;
		}
		disconnectIfCurrent(vc, new InetSocketAddress(vc.address(), vc.port()));
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

	public byte[] createConnectToken(PlayerReference playerId) {
		Objects.requireNonNull(playerId, "playerId");
		return createConnectToken(playerId, System.currentTimeMillis());
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
				if (!running) {
					return;
				}
			} catch (IOException e) {
				if (!running) {
					return;
				}
			} catch (RuntimeException e) {
				if (!running) {
					return;
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

		if (data.length > 0 && data[0] == PacketType.CONNECT.id) {
			handleConnect(datagram, data);
			return;
		}

		if (playerId == null) {
			return;
		}

		VoiceConnection connection = connections.get(playerId);
		if (connection == null) {
			addressMap.remove(address, playerId);
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
			decrypted = CryptoUtils.decrypt(encryptedPayload, connection.aesKey(), sequenceNumber,
					CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		} catch (Exception e) {
			return;
		}

		if (!connection.checkAndAdvanceReceivedSequence(sequenceNumber)) {
			return;
		}

		if (decrypted.length == 0) {
			return;
		}

		long now = System.currentTimeMillis();
		connection.markSeen(now);

		PacketType type = PacketType.fromIdSafe(decrypted[0]);
		if (type == null) {
			return;
		}
		try {
			switch (type) {
				case AUDIO -> handleIncomingAudio(connection, decrypted);
				case KEEPALIVE -> handleKeepAlive(connection, decrypted);
				case DISCONNECT -> handleDisconnectFromClient(connection, address);
				default -> {}
			}
		} catch (RuntimeException e) {
		}
	}

	private void handleConnect(DatagramPacket datagram, byte[] data) {
		if (data.length < 5 + CONNECT_TOKEN_LENGTH) {
			sendRaw(datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
			return;
		}
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1))) {
			int idLength = dis.readInt();
			if (!CryptoUtils.isValidFrameLength(idLength) || dis.available() < idLength + CONNECT_TOKEN_LENGTH) {
				sendRaw(datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			String idValue = new String(dis.readNBytes(idLength), StandardCharsets.UTF_8);
			PlayerReference playerId = PlayerReference.ofName(idValue);
			byte[] connectToken = dis.readNBytes(CONNECT_TOKEN_LENGTH);
			long now = System.currentTimeMillis();
			if (!isValidConnectToken(playerId, connectToken, now)) {
				sendRaw(datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			if (dis.available() != 0) {
				sendRaw(datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			SocketAddress newAddress = new InetSocketAddress(datagram.getAddress(), datagram.getPort());
			VoiceConnection previous = connections.get(playerId);
			if (isActiveConnection(previous, now) && !newAddress.equals(toAddress(previous))) {
				sendRaw(datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}

			SecretKey aesKey = generateSessionKey();
			VoiceConnection connection = new VoiceConnection(
				playerId, datagram.getAddress(), datagram.getPort(), aesKey, now
			);
			connection.setSendCallback(packet -> sendAudioToConnection(connection, packet));
			registerConnection(connection, newAddress);

			byte[] ack = serializeConnectAck(true, aesKey);
			sendRaw(datagram.getAddress(), datagram.getPort(), ack);
		} catch (IOException | RuntimeException e) {
			sendRaw(datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
		}
	}

	private void handleDisconnectFromClient(VoiceConnection connection, SocketAddress address) {
		disconnectIfCurrent(connection, address);
	}

	private void handleKeepAlive(VoiceConnection connection, byte[] data) {
		long seq = connection.nextSendSequence();
		byte[] payload = serializeKeepalivePayload();
		sendEncrypted(connection, payload, seq);
	}

	private void handleIncomingAudio(VoiceConnection sender, byte[] decrypted) {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decrypted, 1, decrypted.length - 1))) {
			int audioLength = dis.readInt();
			if (audioLength < 0 || audioLength > MAX_AUDIO_PAYLOAD_SIZE || dis.available() < audioLength + Long.BYTES * 2) {
				return;
			}
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
		long seq = connection.nextSendSequence();
		sendEncrypted(connection, payload, seq);
	}

	private void sendEncrypted(VoiceConnection connection, byte[] payload, long sequenceNumber) {
		DatagramSocket current = socket;
		if (current == null || current.isClosed()) {
			return;
		}
		byte[] encrypted = CryptoUtils.encrypt(payload, connection.aesKey(), sequenceNumber,
				CryptoUtils.DIRECTION_SERVER_TO_CLIENT);
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
				disconnectIfCurrent(connection, new InetSocketAddress(connection.address(), connection.port()));
			}
		}
	}

	private byte[] serializeConnectAck(boolean success, SecretKey sessionKey) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeBoolean(success);
			if (success && sessionKey != null) {
				byte[] wrappedKey = CryptoUtils.wrapKey(sessionKey, serverKeyExchangeKey);
				dos.writeInt(wrappedKey.length);
				dos.write(wrappedKey);
			}
			return bos.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static byte[] serializeConnectPacket(PlayerReference playerId, byte[] connectToken) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.CONNECT.id);
			byte[] idBytes = playerId.value().getBytes(StandardCharsets.UTF_8);
			dos.writeInt(idBytes.length);
			dos.write(idBytes);
			dos.write(connectToken);
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
		byte[] encrypted = CryptoUtils.encrypt(payload, key, sequenceNumber,
				CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
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

	private void registerConnection(VoiceConnection connection, SocketAddress address) {
		PlayerReference playerId = connection.getPlayerId();
		VoiceConnection previous = connections.get(playerId);
		PlayerReference existingAtAddress = addressMap.get(address);
		if (existingAtAddress != null && !existingAtAddress.equals(playerId)) {
			VoiceConnection oldAtAddress = connections.get(existingAtAddress);
			if (oldAtAddress != null && connections.remove(existingAtAddress, oldAtAddress)) {
				addressMap.remove(address, existingAtAddress);
				oldAtAddress.setConnected(false);
			} else {
				addressMap.remove(address, existingAtAddress);
			}
		}

		connections.put(playerId, connection);
		if (previous != null && previous != connection) {
			addressMap.remove(new InetSocketAddress(previous.address(), previous.port()), playerId);
			previous.setConnected(false);
		}
		addressMap.put(address, playerId);
		connection.setConnected(true);
	}

	private void disconnectIfCurrent(VoiceConnection connection, SocketAddress address) {
		if (connections.remove(connection.getPlayerId(), connection)) {
			addressMap.remove(address, connection.getPlayerId());
			connection.setConnected(false);
		}
	}

	private static boolean isActiveConnection(VoiceConnection connection, long now) {
		return connection != null
				&& connection.isConnected()
				&& connection.getLastPacketTime() >= now - SESSION_TIMEOUT_MS;
	}

	private static SocketAddress toAddress(VoiceConnection connection) {
		return new InetSocketAddress(connection.address(), connection.port());
	}

	byte[] createConnectToken(PlayerReference playerId, long issuedAtMillis) {
		try {
			ByteBuffer payload = ByteBuffer.allocate(Long.BYTES + CONNECT_TOKEN_MAC_LENGTH);
			payload.putLong(issuedAtMillis);
			payload.put(computeConnectTokenMac(playerId, issuedAtMillis));
			return payload.array();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to create connect token", e);
		}
	}

	private boolean isValidConnectToken(PlayerReference playerId, byte[] token, long now) {
		if (token.length != CONNECT_TOKEN_LENGTH) {
			return false;
		}
		ByteBuffer buffer = ByteBuffer.wrap(token);
		long issuedAtMillis = buffer.getLong();
		if (issuedAtMillis > now || now - issuedAtMillis > CONNECT_TOKEN_TTL_MS) {
			return false;
		}
		byte[] providedMac = new byte[CONNECT_TOKEN_MAC_LENGTH];
		buffer.get(providedMac);
		try {
			byte[] expectedMac = computeConnectTokenMac(playerId, issuedAtMillis);
			return MessageDigest.isEqual(expectedMac, providedMac);
		} catch (GeneralSecurityException e) {
			return false;
		}
	}

	private byte[] computeConnectTokenMac(PlayerReference playerId, long issuedAtMillis) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(connectTokenKey.getEncoded(), "HmacSHA256"));
		mac.update(CONNECT_TOKEN_DOMAIN);
		mac.update(playerId.value().getBytes(StandardCharsets.UTF_8));
		mac.update(ByteBuffer.allocate(Long.BYTES).putLong(issuedAtMillis).array());
		return mac.doFinal();
	}

	private static SecretKey generateSessionKey() {
		try {
			KeyGenerator kg = KeyGenerator.getInstance("AES");
			kg.init(256);
			return kg.generateKey();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to generate session key", e);
		}
	}

	private static SecretKey deriveSecretKey(String secret) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, "AES");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	SecretKey serverKeyExchangeKey() {
		return serverKeyExchangeKey;
	}
}
