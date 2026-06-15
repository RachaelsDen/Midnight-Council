package dev.kgoodwin.midnightcouncil.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
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
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class VoiceTransport implements VoiceServer {

	private static final int MAX_PACKET_SIZE = 2048;
	private static final int MAX_AUDIO_PAYLOAD_SIZE = 1400;
	private static final long SESSION_TIMEOUT_MS = 15_000L;
	private static final long CLEANUP_INTERVAL_MS = 5_000L;
	private static final long CONNECT_TOKEN_TTL_MS = 60_000L;
	private static final String TEST_ONLY_DEFAULT_CONNECT_TOKEN_SECRET = "midnight-council-default-voice-connect-token-secret";
	private static final byte[] CONNECT_TOKEN_DOMAIN = "midnight-voice-connect".getBytes(StandardCharsets.UTF_8);
	private static final int CONNECT_TOKEN_MAC_LENGTH = 32;
	private static final int CONNECT_TOKEN_LENGTH = Long.BYTES + CONNECT_TOKEN_MAC_LENGTH;

	private final Map<PlayerReference, VoiceConnection> connections = new ConcurrentHashMap<>();
	private final Map<SocketAddress, PlayerReference> addressMap = new ConcurrentHashMap<>();
	private final Map<ByteBuffer, Long> issuedConnectTokens = new ConcurrentHashMap<>();
	private final VoiceRoutingStrategy routingStrategy;
	private final Supplier<GameState> gameStateSupplier;
	private final SecretKey connectTokenKey;
	private final AtomicLong connectTokenSequence = new AtomicLong();
	private volatile Function<PlayerReference, Position> initialPositionProvider = playerId -> null;
	private volatile Runnable beforeRegisterConnectionHook = () -> {};
	private volatile Runnable beforeInboundStateMutationHook = () -> {};

	private volatile DatagramSocket socket;
	private volatile Thread listenerThread;
	private volatile Thread cleanupThread;
	private volatile boolean running;
	private final AtomicLong lifecycleGeneration = new AtomicLong();

	public VoiceTransport(VoiceRoutingStrategy routingStrategy, Supplier<GameState> gameStateSupplier,
			String connectTokenSecret) {
		this.routingStrategy = Objects.requireNonNull(routingStrategy, "routingStrategy");
		this.gameStateSupplier = Objects.requireNonNull(gameStateSupplier, "gameStateSupplier");
		if (connectTokenSecret == null || connectTokenSecret.isBlank()) {
			throw new IllegalArgumentException("connectTokenSecret cannot be blank");
		}
		this.connectTokenKey = deriveConnectTokenKey(connectTokenSecret);
	}

	VoiceTransport(VoiceRoutingStrategy routingStrategy) {
		this(routingStrategy, GameState::new, TEST_ONLY_DEFAULT_CONNECT_TOKEN_SECRET);
	}

	@Override
	public synchronized void start(int port) {
		if (running) {
			return;
		}
		try {
			DatagramSocket datagramSocket = new DatagramSocket(null);
			datagramSocket.setReuseAddress(true);
			datagramSocket.bind(new InetSocketAddress(port));
			socket = datagramSocket;
		} catch (SocketException e) {
			throw new IllegalStateException("Failed to open voice UDP socket on port " + port, e);
		}
		long generation = lifecycleGeneration.incrementAndGet();
		running = true;
		DatagramSocket listenerSocket = socket;
		listenerThread = new Thread(() -> runListenerLoop(listenerSocket, generation), "midnight-voice-listener");
		listenerThread.start();
		cleanupThread = new Thread(() -> runCleanupLoop(generation), "midnight-voice-cleanup");
		cleanupThread.start();
	}

	@Override
	public synchronized void stop() {
		if (!running) {
			return;
		}
		running = false;
		lifecycleGeneration.incrementAndGet();
		DatagramSocket current = socket;
		socket = null;
		if (current != null && !current.isClosed()) {
			current.close();
		}
		interruptThread(listenerThread);
		interruptThread(cleanupThread);
		listenerThread = null;
		cleanupThread = null;
		for (VoiceConnection connection : connections.values()) {
			connection.setConnected(false);
		}
		connections.clear();
		addressMap.clear();
		issuedConnectTokens.clear();
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
		Collection<VoiceClientConnection> recipients = routingStrategy.route(this, packet, currentGameState());
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

	public void updatePlayerPosition(PlayerReference playerId, Position position) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(position, "position");
		VoiceConnection connection = connections.get(playerId);
		if (connection != null) {
			connection.setPosition(position);
		}
	}

	public boolean invalidateConnectToken(byte[] token) {
		Objects.requireNonNull(token, "token");
		return issuedConnectTokens.remove(tokenKey(token)) != null;
	}

	public int getBoundPort() {
		DatagramSocket currentSocket = socket;
		if (currentSocket == null || currentSocket.isClosed()) {
			return -1;
		}
		return currentSocket.getLocalPort();
	}

	public void setInitialPositionProvider(Function<PlayerReference, Position> provider) {
		this.initialPositionProvider = provider != null ? provider : playerId -> null;
	}

	DatagramSocket socket() {
		return socket;
	}

	Thread listenerThread() {
		return listenerThread;
	}

	Thread cleanupThread() {
		return cleanupThread;
	}

	void setBeforeRegisterConnectionHook(Runnable hook) {
		this.beforeRegisterConnectionHook = hook != null ? hook : () -> {};
	}

	void setBeforeInboundStateMutationHook(Runnable hook) {
		this.beforeInboundStateMutationHook = hook != null ? hook : () -> {};
	}

	private void runListenerLoop(DatagramSocket ownedSocket, long generation) {
		byte[] buffer = new byte[MAX_PACKET_SIZE];
		while (running && lifecycleGeneration.get() == generation) {
			if (ownedSocket == null || ownedSocket.isClosed()) {
				return;
			}
			DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
			try {
				ownedSocket.receive(datagram);
				if (!isCurrentLifecycle(generation, ownedSocket)) {
					return;
				}
				handleDatagram(datagram, ownedSocket, generation);
			} catch (SocketException e) {
				if (!running || lifecycleGeneration.get() != generation || socket != ownedSocket) {
					return;
				}
			} catch (IOException e) {
				if (!running || lifecycleGeneration.get() != generation || socket != ownedSocket) {
					return;
				}
			} catch (RuntimeException e) {
				if (!running || lifecycleGeneration.get() != generation || socket != ownedSocket) {
					return;
				}
			}
		}
	}

	private void runCleanupLoop(long generation) {
		while (running && lifecycleGeneration.get() == generation) {
			try {
				Thread.sleep(CLEANUP_INTERVAL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if (!running || lifecycleGeneration.get() != generation) {
				return;
			}
			cleanupExpiredSessions();
		}
	}

	private void handleDatagram(DatagramPacket datagram, DatagramSocket ownedSocket, long generation) {
		if (!isCurrentLifecycle(generation, ownedSocket)) {
			return;
		}
		SocketAddress address = new InetSocketAddress(datagram.getAddress(), datagram.getPort());
		byte[] data = Arrays.copyOf(datagram.getData(), datagram.getLength());

		if (data.length == 0) {
			return;
		}

		PlayerReference playerId = addressMap.get(address);

		if (data.length > 0 && data[0] == PacketType.CONNECT.id) {
			handleConnect(datagram, data, ownedSocket, generation);
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
		beforeInboundStateMutationHook.run();
		if (!isCurrentLifecycle(generation, ownedSocket)) {
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
				case AUDIO -> handleIncomingAudio(connection, decrypted, ownedSocket, generation);
				case KEEPALIVE -> handleKeepAlive(connection, decrypted, ownedSocket, generation);
				case DISCONNECT -> handleDisconnectFromClient(connection, address, ownedSocket, generation);
				default -> {}
			}
		} catch (RuntimeException e) {
		}
	}

	private void handleConnect(DatagramPacket datagram, byte[] data, DatagramSocket ownedSocket, long generation) {
		if (!isCurrentLifecycle(generation, ownedSocket)) {
			return;
		}
		if (data.length < 5 + CONNECT_TOKEN_LENGTH + CryptoUtils.X25519_PUBLIC_KEY_LENGTH) {
			sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
			return;
		}
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1))) {
			int idLength = dis.readInt();
			if (!CryptoUtils.isValidFrameLength(idLength)
					|| dis.available() < idLength + CONNECT_TOKEN_LENGTH + CryptoUtils.X25519_PUBLIC_KEY_LENGTH) {
				sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			String idValue = new String(dis.readNBytes(idLength), StandardCharsets.UTF_8);
			PlayerReference playerId = PlayerReference.ofName(idValue);
			byte[] connectToken = dis.readNBytes(CONNECT_TOKEN_LENGTH);
			byte[] clientPublicKeyBytes = dis.readNBytes(CryptoUtils.X25519_PUBLIC_KEY_LENGTH);
			if (clientPublicKeyBytes.length != CryptoUtils.X25519_PUBLIC_KEY_LENGTH) {
				sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			long now = System.currentTimeMillis();
			if (!isValidConnectToken(playerId, connectToken, now)) {
				sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			if (dis.available() != 0) {
				sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			SocketAddress newAddress = new InetSocketAddress(datagram.getAddress(), datagram.getPort());
			VoiceConnection previous = connections.get(playerId);
			if (isActiveConnection(previous, now) && !newAddress.equals(toAddress(previous))) {
				sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
				return;
			}
			if (!isCurrentLifecycle(generation, ownedSocket)) {
				return;
			}

			beforeRegisterConnectionHook.run();
			if (!isCurrentLifecycle(generation, ownedSocket)) {
				return;
			}
			KeyPair serverKeyPair = CryptoUtils.generateEcdhKeyPair();
			SecretKey aesKey;
			try {
				KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
				keyAgreement.init(serverKeyPair.getPrivate());
				keyAgreement.doPhase(CryptoUtils.decodeEcdhPublicKey(clientPublicKeyBytes), true);
				aesKey = CryptoUtils.deriveSessionKey(keyAgreement.generateSecret());
			} catch (GeneralSecurityException e) {
				throw new IllegalStateException("Failed to derive ECDH session key", e);
			}
			VoiceConnection connection = new VoiceConnection(
				playerId, datagram.getAddress(), datagram.getPort(), aesKey, now
			);
			Position initialPosition = initialPositionProvider.apply(playerId);
			if (initialPosition != null) {
				connection.setPosition(initialPosition);
			}
			connection.setMicrophoneState(MicrophoneState.ACTIVE);
			connection.setSendCallback(packet -> sendAudioToConnection(connection, packet));
			if (!isCurrentLifecycle(generation, ownedSocket)) {
				return;
			}
			registerConnection(connection, newAddress);

			byte[] ack = serializeConnectAck(true, CryptoUtils.encodeEcdhPublicKey(serverKeyPair.getPublic()));
			sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), ack);
		} catch (IOException | RuntimeException e) {
			sendRaw(ownedSocket, generation, datagram.getAddress(), datagram.getPort(), serializeConnectAck(false, null));
		}
	}

	private void handleDisconnectFromClient(VoiceConnection connection, SocketAddress address, DatagramSocket ownedSocket, long generation) {
		if (!isCurrentLifecycle(generation, ownedSocket)) {
			return;
		}
		disconnectIfCurrent(connection, address);
	}

	private void handleKeepAlive(VoiceConnection connection, byte[] data, DatagramSocket ownedSocket, long generation) {
		if (!isCurrentLifecycle(generation, ownedSocket)) {
			return;
		}
		long seq = connection.nextSendSequence();
		byte[] payload = serializeKeepalivePayload();
		sendEncrypted(connection, payload, seq, ownedSocket, generation);
	}

	private void handleIncomingAudio(VoiceConnection sender, byte[] decrypted, DatagramSocket ownedSocket, long generation) {
		if (!isCurrentLifecycle(generation, ownedSocket)) {
			return;
		}
		if (!canTransmit(sender.getMicrophoneState())) {
			return;
		}
		try {
			AudioPacket packet = deserializeAudioPayload(sender.getPlayerId(), decrypted);
			Collection<VoiceClientConnection> recipients = routingStrategy.route(this, packet, currentGameState());

			for (VoiceClientConnection recipient : recipients) {
				if (recipient instanceof VoiceConnection vc && !vc.getPlayerId().equals(sender.getPlayerId())) {
					sendAudioToConnection(vc, packet, ownedSocket, generation);
				}
			}
		} catch (RuntimeException e) {
		}
	}

	private void sendAudioToConnection(VoiceConnection connection, AudioPacket packet) {
		if (!isCurrentConnection(connection)) {
			return;
		}
		byte[] payload = serializeAudioPayload(packet);
		long seq = connection.nextSendSequence();
		sendEncrypted(connection, payload, seq);
	}

	private void sendAudioToConnection(VoiceConnection connection, AudioPacket packet, DatagramSocket ownedSocket, long generation) {
		if (!isCurrentLifecycle(generation, ownedSocket) || !isCurrentConnection(connection)) {
			return;
		}
		byte[] payload = serializeAudioPayload(packet);
		long seq = connection.nextSendSequence();
		sendEncrypted(connection, payload, seq, ownedSocket, generation);
	}

	private void sendEncrypted(VoiceConnection connection, byte[] payload, long sequenceNumber) {
		if (!isCurrentConnection(connection)) {
			return;
		}
		DatagramSocket current = socket;
		if (current == null || current.isClosed() || !running) {
			return;
		}
		byte[] encrypted = CryptoUtils.encrypt(payload, connection.aesKey(), sequenceNumber,
				CryptoUtils.DIRECTION_SERVER_TO_CLIENT);
		byte[] datagramBytes = ByteBuffer.allocate(Long.BYTES + encrypted.length)
			.putLong(sequenceNumber)
			.put(encrypted)
			.array();
		sendRaw(current, connection.address(), connection.port(), datagramBytes);
	}

	private void sendEncrypted(VoiceConnection connection, byte[] payload, long sequenceNumber, DatagramSocket ownedSocket, long generation) {
		if (!isCurrentLifecycle(generation, ownedSocket) || !isCurrentConnection(connection)) {
			return;
		}
		byte[] encrypted = CryptoUtils.encrypt(payload, connection.aesKey(), sequenceNumber,
				CryptoUtils.DIRECTION_SERVER_TO_CLIENT);
		byte[] datagramBytes = ByteBuffer.allocate(Long.BYTES + encrypted.length)
			.putLong(sequenceNumber)
			.put(encrypted)
			.array();
		sendRaw(ownedSocket, generation, connection.address(), connection.port(), datagramBytes);
	}

	private void sendRaw(DatagramSocket socketToUse, InetAddress address, int port, byte[] data) {
		try {
			socketToUse.send(new DatagramPacket(data, data.length, address, port));
		} catch (IOException e) {
		}
	}

	private void sendRaw(DatagramSocket ownedSocket, long generation, InetAddress address, int port, byte[] data) {
		if (!isCurrentLifecycle(generation, ownedSocket)) {
			return;
		}
		sendRaw(ownedSocket, address, port, data);
	}

	private void cleanupExpiredSessions() {
		long now = System.currentTimeMillis();
		long cutoff = now - SESSION_TIMEOUT_MS;
		for (VoiceConnection connection : connections.values()) {
			if (connection.getLastPacketTime() < cutoff) {
				disconnectIfCurrent(connection, new InetSocketAddress(connection.address(), connection.port()));
			}
		}
		cleanupExpiredConnectTokens(now);
	}

	private byte[] serializeConnectAck(boolean success, byte[] serverPublicKey) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeBoolean(success);
			if (success) {
				if (serverPublicKey == null || serverPublicKey.length != CryptoUtils.X25519_PUBLIC_KEY_LENGTH) {
					throw new IllegalArgumentException("serverPublicKey must be a 32-byte X25519 public key");
				}
				dos.write(serverPublicKey);
			}
			return bos.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static byte[] serializeConnectPacket(PlayerReference playerId, byte[] connectToken, byte[] clientEcdhPublicKey) {
		if (clientEcdhPublicKey == null || clientEcdhPublicKey.length != CryptoUtils.X25519_PUBLIC_KEY_LENGTH) {
			throw new IllegalArgumentException("clientEcdhPublicKey must be a 32-byte X25519 public key");
		}
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.CONNECT.id);
			byte[] idBytes = playerId.value().getBytes(StandardCharsets.UTF_8);
			dos.writeInt(idBytes.length);
			dos.write(idBytes);
			dos.write(connectToken);
			dos.write(clientEcdhPublicKey);
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

	static byte[] serializeAudioPayload(AudioPacket packet) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.AUDIO.id);
			dos.writeUTF(packet.senderId().value());
			dos.writeInt(packet.encodedData().length);
			dos.write(packet.encodedData());
			dos.writeLong(packet.sequenceNumber());
			dos.writeLong(packet.timestamp());
			return bos.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static AudioPacket deserializeAudioPayload(byte[] decrypted) {
		Objects.requireNonNull(decrypted, "decrypted");
		if (decrypted.length < 1 || decrypted[0] != PacketType.AUDIO.id) {
			throw new IllegalArgumentException("Invalid AUDIO payload");
		}
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decrypted, 1, decrypted.length - 1))) {
			PlayerReference senderId = PlayerReference.ofName(dis.readUTF());
			int audioLength = dis.readInt();
			if (audioLength < 0 || audioLength > MAX_AUDIO_PAYLOAD_SIZE || dis.available() < audioLength + Long.BYTES * 2) {
				throw new IllegalArgumentException("Invalid AUDIO payload length");
			}
			byte[] audioData = dis.readNBytes(audioLength);
			long sequenceNumber = dis.readLong();
			long timestamp = dis.readLong();
			if (dis.available() != 0) {
				throw new IllegalArgumentException("Unexpected trailing AUDIO payload bytes");
			}
			return new AudioPacket(senderId, audioData, sequenceNumber, timestamp);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to deserialize AUDIO payload", e);
		}
	}

	static AudioPacket deserializeAudioPayload(PlayerReference senderId, byte[] decrypted) {
		Objects.requireNonNull(senderId, "senderId");
		Objects.requireNonNull(decrypted, "decrypted");
		if (decrypted.length < 1 || decrypted[0] != PacketType.AUDIO.id) {
			throw new IllegalArgumentException("Invalid AUDIO payload");
		}
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decrypted, 1, decrypted.length - 1))) {
			dis.readUTF();
			int audioLength = dis.readInt();
			if (audioLength < 0 || audioLength > MAX_AUDIO_PAYLOAD_SIZE || dis.available() < audioLength + Long.BYTES * 2) {
				throw new IllegalArgumentException("Invalid AUDIO payload length");
			}
			byte[] audioData = dis.readNBytes(audioLength);
			long sequenceNumber = dis.readLong();
			long timestamp = dis.readLong();
			if (dis.available() != 0) {
				throw new IllegalArgumentException("Unexpected trailing AUDIO payload bytes");
			}
			return new AudioPacket(senderId, audioData, sequenceNumber, timestamp);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to deserialize AUDIO payload", e);
		}
	}

	private static void interruptThread(Thread thread) {
		if (thread != null) {
			thread.interrupt();
		}
	}

	private synchronized void registerConnection(VoiceConnection connection, SocketAddress address) {
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

	private synchronized void disconnectIfCurrent(VoiceConnection connection, SocketAddress address) {
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

	private boolean isCurrentLifecycle(long generation, DatagramSocket ownedSocket) {
		return running
				&& lifecycleGeneration.get() == generation
				&& socket == ownedSocket
				&& ownedSocket != null
				&& !ownedSocket.isClosed();
	}

	private boolean isCurrentConnection(VoiceConnection connection) {
		return connection != null
				&& connection.isConnected()
				&& connections.get(connection.getPlayerId()) == connection;
	}

	private static boolean canTransmit(dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState microphoneState) {
		return microphoneState == dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState.ACTIVE
				|| microphoneState == dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState.PUSH_TO_TALK;
	}

	private static SocketAddress toAddress(VoiceConnection connection) {
		return new InetSocketAddress(connection.address(), connection.port());
	}

	byte[] createConnectToken(PlayerReference playerId, long issuedAtMillis) {
		try {
			long issuedAtMarker = createConnectTokenIssuedAtMarker(issuedAtMillis);
			ByteBuffer payload = ByteBuffer.allocate(Long.BYTES + CONNECT_TOKEN_MAC_LENGTH);
			payload.putLong(issuedAtMarker);
			payload.put(computeConnectTokenMac(playerId, issuedAtMarker));
			byte[] token = payload.array();
			registerConnectToken(token, issuedAtMillis + CONNECT_TOKEN_TTL_MS);
			return token;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to create connect token", e);
		}
	}

	private boolean isValidConnectToken(PlayerReference playerId, byte[] token, long now) {
		if (token.length != CONNECT_TOKEN_LENGTH) {
			return false;
		}
		ByteBuffer buffer = ByteBuffer.wrap(token);
		long issuedAtMarker = buffer.getLong();
		long issuedAtMillis = decodeConnectTokenIssuedAtMillis(issuedAtMarker);
		if (issuedAtMillis > now || now - issuedAtMillis > CONNECT_TOKEN_TTL_MS) {
			return false;
		}
		Long expiry = issuedConnectTokens.get(tokenKey(token));
		if (expiry == null || expiry < now) {
			return false;
		}
		byte[] providedMac = new byte[CONNECT_TOKEN_MAC_LENGTH];
		buffer.get(providedMac);
		try {
			byte[] expectedMac = computeConnectTokenMac(playerId, issuedAtMarker);
			return MessageDigest.isEqual(expectedMac, providedMac);
		} catch (GeneralSecurityException e) {
			return false;
		}
	}

	private long createConnectTokenIssuedAtMarker(long issuedAtMillis) {
		return (issuedAtMillis << 16) | (connectTokenSequence.incrementAndGet() & 0xFFFFL);
	}

	private static long decodeConnectTokenIssuedAtMillis(long issuedAtMarker) {
		return issuedAtMarker >>> 16;
	}

	private byte[] computeConnectTokenMac(PlayerReference playerId, long issuedAtMillis) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(connectTokenKey.getEncoded(), "HmacSHA256"));
		mac.update(CONNECT_TOKEN_DOMAIN);
		mac.update(playerId.value().getBytes(StandardCharsets.UTF_8));
		mac.update(ByteBuffer.allocate(Long.BYTES).putLong(issuedAtMillis).array());
		return mac.doFinal();
	}

	private void registerConnectToken(byte[] token, long expiryMillis) {
		issuedConnectTokens.put(tokenKey(token), expiryMillis);
	}

	private void cleanupExpiredConnectTokens(long now) {
		issuedConnectTokens.entrySet().removeIf(entry -> entry.getValue() < now);
	}

	private static ByteBuffer tokenKey(byte[] token) {
		try {
			return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(token));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to hash connect token", e);
		}
	}

	private GameState currentGameState() {
		return Objects.requireNonNull(gameStateSupplier.get(), "gameStateSupplier returned null");
	}

	private static SecretKey deriveConnectTokenKey(String secret) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, "AES");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
