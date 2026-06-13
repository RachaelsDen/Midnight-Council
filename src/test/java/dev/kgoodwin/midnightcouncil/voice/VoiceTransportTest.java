package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceRoutingStrategy;

import javax.crypto.KeyGenerator;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceTransportTest {

	private static final int TEST_TIMEOUT_MS = 2000;

	private VoiceTransport server;
	private int serverPort;

	@BeforeEach
	void setUp() throws Exception {
		try (DatagramSocket probe = new DatagramSocket(0)) {
			serverPort = probe.getLocalPort();
		}
		server = new VoiceTransport(new TestRoutingStrategy());
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void startsAndStopsCleanly() {
		assertFalse(server.isRunning());
		server.start(serverPort);
		assertTrue(server.isRunning());
		server.stop();
		assertFalse(server.isRunning());
	}

	@Test
	void doubleStartIsNoop() {
		server.start(serverPort);
		assertTrue(server.isRunning());
		server.start(serverPort);
		assertTrue(server.isRunning());
		server.stop();
	}

	@Test
	void doubleStopIsNoop() {
		server.stop();
		assertFalse(server.isRunning());
	}

	@Test
	void isRunningFalseBeforeStart() {
		assertFalse(server.isRunning());
	}

	@Test
	void connectAddsToConnections() throws Exception {
		server.start(serverPort);
		VoiceConnection vc = createTestConnection("player1");
		server.connect(vc);
		Collection<VoiceClientConnection> conns = server.getConnections();
		assertEquals(1, conns.size());
		assertTrue(conns.contains(vc));
	}

	@Test
	void disconnectRemovesFromConnections() throws Exception {
		server.start(serverPort);
		VoiceConnection vc = createTestConnection("player1");
		server.connect(vc);
		assertEquals(1, server.getConnections().size());
		server.disconnect(vc);
		assertEquals(0, server.getConnections().size());
		assertFalse(vc.isConnected());
	}

	@Test
	void updatePlayerPositionUpdatesRegisteredConnection() throws Exception {
		server.start(serverPort);
		VoiceConnection vc = createTestConnection("player1");
		server.connect(vc);

		Position updated = new Position(12.5, 64.0, -8.25);
		server.updatePlayerPosition(vc.getPlayerId(), updated);

		assertEquals(updated, vc.getPosition());
	}

	@Test
	void updatePlayerPositionIgnoresUnknownPlayer() {
		server.updatePlayerPosition(PlayerReference.ofName("missing"), new Position(1.0, 2.0, 3.0));
		assertTrue(server.getConnections().isEmpty());
	}

	@Test
	void initialPositionProviderSeedsUdpConnectionPosition() throws Exception {
		server.setInitialPositionProvider(playerId -> new Position(4.0, 5.0, 6.0));
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("seeded-client");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			connectClient(client, playerId);

			Collection<VoiceClientConnection> conns = server.getConnections();
			assertEquals(1, conns.size());
			assertEquals(new Position(4.0, 5.0, 6.0), conns.iterator().next().getPosition());
		}
	}

	@Test
	void stopDisconnectsHeldConnections() throws Exception {
		server.start(serverPort);
		VoiceConnection first = createTestConnection("player1");
		VoiceConnection second = createTestConnection("player2");
		server.connect(first);
		server.connect(second);
		Collection<VoiceClientConnection> held = server.getConnections();

		server.stop();

		assertEquals(0, server.getConnections().size());
		for (VoiceClientConnection connection : held) {
			assertFalse(connection.isConnected());
		}
	}

	@Test
	void getConnectionsReturnsDefensiveCopy() throws Exception {
		server.start(serverPort);
		VoiceConnection vc = createTestConnection("player1");
		server.connect(vc);
		Collection<VoiceClientConnection> conns = server.getConnections();
		conns.clear();
		assertEquals(1, server.getConnections().size());
	}

	@Test
	void connectRejectsNonVoiceConnection() {
		server.start(serverPort);
		boolean threw = false;
		try {
			server.connect(new VoiceClientConnection() {
				@Override public PlayerReference getPlayerId() { return PlayerReference.ofName("x"); }
				@Override public MicrophoneState getMicrophoneState() { return null; }
				@Override public void setMicrophoneState(MicrophoneState state) {}
				@Override public dev.kgoodwin.midnightcouncil.api.Position getPosition() { return null; }
				@Override public boolean isConnected() { return true; }
				@Override public void sendPacket(AudioPacket packet) {}
				@Override public long getLastPacketTime() { return 0; }
			});
		} catch (IllegalArgumentException e) {
			threw = true;
		}
		assertTrue(threw);
	}

	@Test
	void constructorRequiresNonBlankConnectTokenSecret() {
		assertThrows(IllegalArgumentException.class,
				() -> new VoiceTransport(new TestRoutingStrategy(), GameState::new, " "));
	}

	@Test
	void customConnectTokenSecretsProduceDifferentTokens() {
		PlayerReference playerId = PlayerReference.ofName("token-player");
		long issuedAt = 123456789L;
		VoiceTransport first = new VoiceTransport(new TestRoutingStrategy(), GameState::new, "token-secret-one");
		VoiceTransport second = new VoiceTransport(new TestRoutingStrategy(), GameState::new, "token-secret-two");

		assertFalse(java.util.Arrays.equals(
				first.createConnectToken(playerId, issuedAt),
				second.createConnectToken(playerId, issuedAt)));
	}

	@Test
	void clientHandshakeViaUdp() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("udp-client");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(client, playerId);
			assertNotNull(key);
		}
	}

	@Test
	void keepaliveUpdatesLastPacketTime() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("keepalive-client");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(client, playerId);

			long beforeKeepalive = 1L;

			byte[] keepalive = VoiceTransport.serializeKeepalivePacket(key, beforeKeepalive);
			client.send(new DatagramPacket(keepalive, keepalive.length,
				InetAddress.getLoopbackAddress(), serverPort));

			byte[] buf = new byte[256];
			DatagramPacket response = new DatagramPacket(buf, buf.length);
			client.receive(response);

			Thread.sleep(100);

			Collection<VoiceClientConnection> conns = server.getConnections();
			assertEquals(1, conns.size());
			VoiceClientConnection vc = conns.iterator().next();
			assertTrue(vc.getLastPacketTime() >= beforeKeepalive,
				"lastPacketTime should be updated after keepalive");
		}
	}

	@Test
	void disconnectPacketRemovesConnection() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("disconnect-client");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(client, playerId);

			assertEquals(1, server.getConnections().size());

			byte[] disconnect = VoiceTransport.serializeDisconnectPacket();
			long seq = 1L;
			byte[] encrypted = CryptoUtils.encrypt(disconnect, key, seq,
					CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
			byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
				.putLong(seq)
				.put(encrypted)
				.array();
			client.send(new DatagramPacket(framed, framed.length,
				InetAddress.getLoopbackAddress(), serverPort));

			Thread.sleep(200);

			assertEquals(0, server.getConnections().size());
		}
	}

	@Test
	void unknownClientsRejected() throws Exception {
		server.start(serverPort);
		SecretKey key = generateAesKey();

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);

			byte[] payload = new byte[]{PacketType.AUDIO.id};
			byte[] encrypted = CryptoUtils.encrypt(payload, key, 1L,
					CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
			byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
				.putLong(1L)
				.put(encrypted)
				.array();
			client.send(new DatagramPacket(framed, framed.length,
				InetAddress.getLoopbackAddress(), serverPort));

			DatagramPacket response = new DatagramPacket(new byte[256], 256);
			boolean received = false;
			try {
				client.receive(response);
				received = true;
			} catch (SocketTimeoutException e) {
			}
			assertFalse(received, "Server should not respond to unknown client");
		}
	}

	@Test
	void audioSendReceiveBetweenTwoClients() throws Exception {
		TestRoutingStrategy routing = new TestRoutingStrategy();
		server.stop();
		server = new VoiceTransport(routing);
		server.start(serverPort);

		PlayerReference playerA = PlayerReference.ofName("playerA");
		PlayerReference playerB = PlayerReference.ofName("playerB");

		try (DatagramSocket clientA = new DatagramSocket();
				 DatagramSocket clientB = new DatagramSocket()) {
			clientA.setSoTimeout(TEST_TIMEOUT_MS);
			clientB.setSoTimeout(TEST_TIMEOUT_MS);

			connectClient(clientA, playerA);
			SecretKey keyB = connectClient(clientB, playerB);

			assertEquals(2, server.getConnections().size());

			routing.setBroadcast(true);

			byte[] audioData = "hello from A".getBytes();
			AudioPacket audioPacket = new AudioPacket(playerA, audioData, 1L, System.currentTimeMillis());
			server.sendAudio(audioPacket);

			DatagramPacket response = new DatagramPacket(new byte[2048], 2048);
			boolean received = false;
			try {
				clientB.receive(response);
				received = true;
			} catch (SocketTimeoutException e) {
			}
			assertTrue(received, "Client B should receive audio from A");
			byte[] decrypted = decryptServerDatagram(response, keyB);
			assertEquals(PacketType.AUDIO.id, decrypted[0]);
			try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(decrypted, 1, decrypted.length - 1))) {
				int audioLength = dis.readInt();
				byte[] receivedAudio = dis.readNBytes(audioLength);
				long sequenceNumber = dis.readLong();
				long timestamp = dis.readLong();
				assertEquals(audioData.length, audioLength);
				assertTrue(java.util.Arrays.equals(audioData, receivedAudio));
				assertEquals(audioPacket.sequenceNumber(), sequenceNumber);
				assertEquals(audioPacket.timestamp(), timestamp);
			}
		}
	}

	@Test
	void timeoutDetection() throws Exception {
		VoiceTransport shortTimeoutServer = new VoiceTransport(new TestRoutingStrategy());
		int port;
		try (DatagramSocket probe = new DatagramSocket(0)) {
			port = probe.getLocalPort();
		}
		shortTimeoutServer.start(port);

		PlayerReference playerId = PlayerReference.ofName("timeout-client");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			connectClient(client, playerId, shortTimeoutServer, port);

			assertEquals(1, shortTimeoutServer.getConnections().size());

			VoiceClientConnection vc = shortTimeoutServer.getConnections().iterator().next();
			assertTrue(vc instanceof VoiceConnection);
			((VoiceConnection) vc).markSeen(System.currentTimeMillis() - 20_000);

			Thread.sleep(6_000);

			assertEquals(0, shortTimeoutServer.getConnections().size());
		} finally {
			shortTimeoutServer.stop();
		}
	}

	@Test
	void threadSafetyConcurrentConnectsDisconnects() throws Exception {
		server.start(serverPort);
		int threadCount = 10;
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger errors = new AtomicInteger(0);

		for (int i = 0; i < threadCount; i++) {
			final int idx = i;
			new Thread(() -> {
				try {
					VoiceConnection vc = createTestConnection("thread-" + idx);
					server.connect(vc);
					Thread.sleep(10);
					server.disconnect(vc);
				} catch (Exception e) {
					errors.incrementAndGet();
				} finally {
					latch.countDown();
				}
			}).start();
		}

		assertTrue(latch.await(5, TimeUnit.SECONDS));
		assertEquals(0, errors.get());
		assertEquals(0, server.getConnections().size());
	}

	private VoiceConnection createTestConnection(String name) throws Exception {
		return new VoiceConnection(
			PlayerReference.ofName(name),
			InetAddress.getLoopbackAddress(),
			5000 + (int) (Math.random() * 1000),
			generateAesKey(),
			System.currentTimeMillis()
		);
	}

	private SecretKey connectClient(DatagramSocket client, PlayerReference playerId,
									VoiceTransport transport, int port) throws Exception {
		KeyPair clientKeyPair = CryptoUtils.generateEcdhKeyPair();
		byte[] connectPacket = VoiceTransport.serializeConnectPacket(
				playerId,
				transport.createConnectToken(playerId),
				CryptoUtils.encodeEcdhPublicKey(clientKeyPair.getPublic()));
		client.send(new DatagramPacket(connectPacket, connectPacket.length,
			InetAddress.getLoopbackAddress(), port));

		byte[] buf = new byte[512];
		DatagramPacket response = new DatagramPacket(buf, buf.length);
		client.receive(response);

		DataInputStream dis = new DataInputStream(
			new ByteArrayInputStream(response.getData(), 0, response.getLength()));
		boolean success = dis.readBoolean();
		assertTrue(success, "Connect handshake should succeed");

		byte[] serverPublicKey = dis.readNBytes(CryptoUtils.X25519_PUBLIC_KEY_LENGTH);
		assertEquals(CryptoUtils.X25519_PUBLIC_KEY_LENGTH, serverPublicKey.length);
		assertEquals(0, dis.available(), "ACK should contain only the success flag and server public key");

		KeyAgreement keyAgreement = KeyAgreement.getInstance("X25519");
		keyAgreement.init(clientKeyPair.getPrivate());
		keyAgreement.doPhase(CryptoUtils.decodeEcdhPublicKey(serverPublicKey), true);
		return CryptoUtils.deriveSessionKey(keyAgreement.generateSecret());
	}

	private SecretKey connectClient(DatagramSocket client, PlayerReference playerId) throws Exception {
		return connectClient(client, playerId, server, serverPort);
	}

	private boolean readAckSuccess(DatagramSocket client) throws Exception {
		byte[] buf = new byte[512];
		DatagramPacket response = new DatagramPacket(buf, buf.length);
		client.receive(response);
		DataInputStream dis = new DataInputStream(
				new ByteArrayInputStream(response.getData(), 0, response.getLength()));
		return dis.readBoolean();
	}

	private void sendKeepaliveAndExpectResponse(DatagramSocket client, SecretKey key, long sequenceNumber)
			throws Exception {
		byte[] keepalive = VoiceTransport.serializeKeepalivePacket(key, sequenceNumber);
		client.send(new DatagramPacket(keepalive, keepalive.length,
				InetAddress.getLoopbackAddress(), serverPort));
		DatagramPacket response = new DatagramPacket(new byte[256], 256);
		client.receive(response);
		byte[] decrypted = decryptServerDatagram(response, key);
		assertEquals(PacketType.KEEPALIVE.id, decrypted[0]);
	}

	private static byte[] serializeRawConnectPacket(String rawPlayerId, byte[] token, byte[] clientPublicKey) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.CONNECT.id);
			byte[] idBytes = rawPlayerId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			dos.writeInt(idBytes.length);
			dos.write(idBytes);
			dos.write(token);
			dos.write(clientPublicKey);
			return bos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static byte[] generateClientPublicKeyBytes() {
		return CryptoUtils.encodeEcdhPublicKey(CryptoUtils.generateEcdhKeyPair().getPublic());
	}

	private static byte[] decryptServerDatagram(DatagramPacket response, SecretKey key) {
		ByteBuffer buffer = ByteBuffer.wrap(response.getData(), 0, response.getLength());
		long sequenceNumber = buffer.getLong();
		byte[] encrypted = new byte[response.getLength() - Long.BYTES];
		buffer.get(encrypted);
		return CryptoUtils.decrypt(encrypted, key, sequenceNumber, CryptoUtils.DIRECTION_SERVER_TO_CLIENT);
	}

	private void sendInboundAudio(DatagramSocket client, SecretKey key, byte[] audioData, long sequenceNumber,
			long timestamp) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeByte(PacketType.AUDIO.id);
		dos.writeInt(audioData.length);
		dos.write(audioData);
		dos.writeLong(sequenceNumber);
		dos.writeLong(timestamp);
		byte[] encrypted = CryptoUtils.encrypt(bos.toByteArray(), key, sequenceNumber,
				CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
			.putLong(sequenceNumber)
			.put(encrypted)
			.array();
		client.send(new DatagramPacket(framed, framed.length,
				InetAddress.getLoopbackAddress(), serverPort));
	}

	@Test
	void unknownPacketTypeDoesNotKillListener() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("test-unknown-type");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(client, playerId);

			byte[] payload = new byte[]{(byte) 0xFF};
			long seq = 1L;
			byte[] encrypted = CryptoUtils.encrypt(payload, key, seq,
					CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
			byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
				.putLong(seq)
				.put(encrypted)
				.array();
			client.send(new DatagramPacket(framed, framed.length,
				InetAddress.getLoopbackAddress(), serverPort));

			Thread.sleep(200);

			assertTrue(server.isRunning());
			assertEquals(1, server.getConnections().size());
		}
	}

	@Test
	void malformedConnectFrameRejected() throws Exception {
		server.start(serverPort);
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);

			byte[] malformed = new byte[]{PacketType.CONNECT.id, 0x00, 0x00, 0x00, (byte) 0xFF};
			client.send(new DatagramPacket(malformed, malformed.length,
				InetAddress.getLoopbackAddress(), serverPort));

			byte[] buf = new byte[256];
			DatagramPacket response = new DatagramPacket(buf, buf.length);
			client.receive(response);

			DataInputStream dis = new DataInputStream(
				new ByteArrayInputStream(response.getData(), 0, response.getLength()));
			assertFalse(dis.readBoolean(), "Malformed CONNECT should be rejected");
		}
	}

	@Test
	void negativeLengthConnectFrameRejected() throws Exception {
		server.start(serverPort);
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.CONNECT.id);
			dos.writeInt(-1);
			byte[] negativeLen = bos.toByteArray();
			client.send(new DatagramPacket(negativeLen, negativeLen.length,
				InetAddress.getLoopbackAddress(), serverPort));

			byte[] buf = new byte[256];
			DatagramPacket response = new DatagramPacket(buf, buf.length);
			client.receive(response);

			DataInputStream dis = new DataInputStream(
				new ByteArrayInputStream(response.getData(), 0, response.getLength()));
			assertFalse(dis.readBoolean(), "Negative length CONNECT should be rejected");
		}
	}

	@Test
	void replayedSequenceRejected() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("replay-client");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);
			SecretKey key = connectClient(client, playerId);

			long seq = 42L;
			byte[] keepalive = VoiceTransport.serializeKeepalivePacket(key, seq);
			client.send(new DatagramPacket(keepalive, keepalive.length,
				InetAddress.getLoopbackAddress(), serverPort));

			byte[] buf = new byte[256];
			DatagramPacket response = new DatagramPacket(buf, buf.length);
			client.receive(response);

			client.send(new DatagramPacket(keepalive, keepalive.length,
				InetAddress.getLoopbackAddress(), serverPort));

			boolean secondResponse = false;
			try {
				client.receive(response);
				secondResponse = true;
			} catch (SocketTimeoutException e) {
			}
			assertFalse(secondResponse, "Replayed packet should be silently dropped");
		}
	}

	@Test
	void replayedConnectTokenRejectedAfterDisconnect() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("replay-connect-client");
		byte[] token = server.createConnectToken(playerId);
		byte[] connectPacket = VoiceTransport.serializeConnectPacket(playerId, token, generateClientPublicKeyBytes());

		try (DatagramSocket firstClient = new DatagramSocket();
				 DatagramSocket secondClient = new DatagramSocket()) {
			firstClient.setSoTimeout(TEST_TIMEOUT_MS);
			secondClient.setSoTimeout(TEST_TIMEOUT_MS);

			firstClient.send(new DatagramPacket(connectPacket, connectPacket.length,
					InetAddress.getLoopbackAddress(), serverPort));
			assertTrue(readAckSuccess(firstClient));

			VoiceConnection live = (VoiceConnection) server.getConnections().iterator().next();
			server.disconnect(live);

			secondClient.send(new DatagramPacket(connectPacket, connectPacket.length,
					InetAddress.getLoopbackAddress(), serverPort));
			assertFalse(readAckSuccess(secondClient));
			assertEquals(0, server.getConnections().size());
		}
	}

	@Test
	void sendAudioPassesCurrentGameStateToRoutingStrategy() {
		server.stop();
		GameState state = new GameState();
		StateCapturingRoutingStrategy routing = new StateCapturingRoutingStrategy();
		server = new VoiceTransport(routing, () -> state, "token-secret");
		server.start(serverPort);

		AudioPacket packet = new AudioPacket(PlayerReference.ofName("sender"), new byte[]{1}, 1L, 1L);
		server.sendAudio(packet);

		assertTrue(routing.lastState() == state);
	}

	@Test
	void incomingAudioPassesCurrentGameStateToRoutingStrategy() throws Exception {
		server.stop();
		GameState state = new GameState();
		StateCapturingRoutingStrategy routing = new StateCapturingRoutingStrategy();
		server = new VoiceTransport(routing, () -> state, "token-secret");
		server.start(serverPort);

		PlayerReference playerId = PlayerReference.ofName("stateful-client");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(client, playerId, server, serverPort);
			VoiceConnection live = (VoiceConnection) server.getConnections().iterator().next();
			live.setMicrophoneState(MicrophoneState.ACTIVE);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.AUDIO.id);
			byte[] audioData = new byte[]{9, 8, 7};
			dos.writeInt(audioData.length);
			dos.write(audioData);
			dos.writeLong(1L);
			dos.writeLong(123L);
			byte[] encrypted = CryptoUtils.encrypt(bos.toByteArray(), key, 1L,
					CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
			byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
				.putLong(1L)
				.put(encrypted)
				.array();
			client.send(new DatagramPacket(framed, framed.length,
					InetAddress.getLoopbackAddress(), serverPort));

			assertTrue(routing.awaitInvocation());
			assertTrue(routing.lastState() == state);
		}
	}

	@Test
	void mutedIncomingAudioDoesNotReachRoutingStrategy() throws Exception {
		server.stop();
		CountingRoutingStrategy routing = new CountingRoutingStrategy();
		server = new VoiceTransport(routing, GameState::new, "token-secret");
		server.start(serverPort);

		PlayerReference playerId = PlayerReference.ofName("muted-client");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);
			SecretKey key = connectClient(client, playerId, server, serverPort);

			sendInboundAudio(client, key, new byte[] {1, 2, 3}, 1L, 123L);

			assertFalse(routing.awaitInvocation(200));
			assertEquals(0, routing.invocationCount());
		}
	}

	@Test
	void activeIncomingAudioReachesRoutingStrategy() throws Exception {
		server.stop();
		CountingRoutingStrategy routing = new CountingRoutingStrategy();
		server = new VoiceTransport(routing, GameState::new, "token-secret");
		server.start(serverPort);

		PlayerReference playerId = PlayerReference.ofName("active-client");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(client, playerId, server, serverPort);
			VoiceConnection live = (VoiceConnection) server.getConnections().iterator().next();
			live.setMicrophoneState(MicrophoneState.ACTIVE);

			sendInboundAudio(client, key, new byte[] {4, 5, 6}, 1L, 456L);

			assertTrue(routing.awaitInvocation());
			assertEquals(1, routing.invocationCount());
		}
	}

	@Test
	void staleConnectionRemovedOnReconnectFromSameAddress() throws Exception {
		server.start(serverPort);
		PlayerReference playerOld = PlayerReference.ofName("old-player");
		PlayerReference playerNew = PlayerReference.ofName("new-player");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);

			connectClient(client, playerOld);
			assertEquals(1, server.getConnections().size());
			assertEquals(playerOld, server.getConnections().iterator().next().getPlayerId());

			connectClient(client, playerNew);
			assertEquals(1, server.getConnections().size());
			assertEquals(playerNew, server.getConnections().iterator().next().getPlayerId());
		}
	}

	@Test
	void ecdhHandshakeProducesUniqueSessionKeys() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("key-test");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey firstKey = connectClient(client, playerId);
			SecretKey secondKey = connectClient(client, playerId);

			assertFalse(java.util.Arrays.equals(firstKey.getEncoded(), secondKey.getEncoded()));
		}
	}

	@Test
	void ecdhSessionKeyIsNotDerivedFromSharedSecret() throws Exception {
		server.start(serverPort);
		PlayerReference firstPlayer = PlayerReference.ofName("ecdh-player-one");
		PlayerReference secondPlayer = PlayerReference.ofName("ecdh-player-two");
		try (DatagramSocket firstClient = new DatagramSocket();
				 DatagramSocket secondClient = new DatagramSocket()) {
			firstClient.setSoTimeout(TEST_TIMEOUT_MS);
			secondClient.setSoTimeout(TEST_TIMEOUT_MS);

			SecretKey firstKey = connectClient(firstClient, firstPlayer);
			SecretKey secondKey = connectClient(secondClient, secondPlayer);

			assertFalse(java.util.Arrays.equals(firstKey.getEncoded(), secondKey.getEncoded()));
		}
	}

	@Test
	void sendPacketCallbackSendsAudio() throws Exception {
		AtomicReference<AudioPacket> captured = new AtomicReference<>();
		VoiceConnection vc = new VoiceConnection(
			PlayerReference.ofName("callback-test"),
			InetAddress.getLoopbackAddress(),
			9999,
			generateAesKey(),
			System.currentTimeMillis(),
			captured::set
		);

		AudioPacket packet = new AudioPacket(
			PlayerReference.ofName("sender"), new byte[]{1, 2, 3}, 1L, System.currentTimeMillis());
		vc.sendPacket(packet);

		assertEquals(packet, captured.get());
	}

	@Test
	void blankPlayerIdConnectRejectedAndListenerStaysAlive() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("valid-client");

		try (DatagramSocket validClient = new DatagramSocket();
				 DatagramSocket invalidClient = new DatagramSocket()) {
			validClient.setSoTimeout(TEST_TIMEOUT_MS);
			invalidClient.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(validClient, playerId);

			byte[] invalidConnect = serializeRawConnectPacket(" ", server.createConnectToken(playerId), generateClientPublicKeyBytes());
			invalidClient.send(new DatagramPacket(invalidConnect, invalidConnect.length,
					InetAddress.getLoopbackAddress(), serverPort));

			assertFalse(readAckSuccess(invalidClient));
			sendKeepaliveAndExpectResponse(validClient, key, 1L);
			assertEquals(1, server.getConnections().size());
		}
	}

	@Test
	void forgedTokenConnectRejectedAndListenerStaysAlive() throws Exception {
		server.start(serverPort);
		PlayerReference validPlayer = PlayerReference.ofName("voice-valid");
		PlayerReference forgedPlayer = PlayerReference.ofName("voice-forged");

		try (DatagramSocket validClient = new DatagramSocket();
				 DatagramSocket attacker = new DatagramSocket()) {
			validClient.setSoTimeout(TEST_TIMEOUT_MS);
			attacker.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey validKey = connectClient(validClient, validPlayer);

			byte[] forgedConnect = VoiceTransport.serializeConnectPacket(
					forgedPlayer,
					server.createConnectToken(validPlayer),
					generateClientPublicKeyBytes());
			attacker.send(new DatagramPacket(forgedConnect, forgedConnect.length,
					InetAddress.getLoopbackAddress(), serverPort));

			assertFalse(readAckSuccess(attacker));
			sendKeepaliveAndExpectResponse(validClient, validKey, 1L);
			assertEquals(1, server.getConnections().size());
		}
	}

	@Test
	void expiredTokenConnectRejected() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("expired-token-player");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			byte[] expiredConnect = VoiceTransport.serializeConnectPacket(
					playerId,
					server.createConnectToken(playerId, System.currentTimeMillis() - 120_000L),
					generateClientPublicKeyBytes());
			client.send(new DatagramPacket(expiredConnect, expiredConnect.length,
					InetAddress.getLoopbackAddress(), serverPort));

			assertFalse(readAckSuccess(client));
			assertEquals(0, server.getConnections().size());
		}
	}

	@Test
	void activePlayerReconnectFromDifferentAddressRejected() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("active-player");

		try (DatagramSocket firstClient = new DatagramSocket();
				 DatagramSocket secondClient = new DatagramSocket()) {
			firstClient.setSoTimeout(TEST_TIMEOUT_MS);
			secondClient.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(firstClient, playerId);

			byte[] connectPacket = VoiceTransport.serializeConnectPacket(
					playerId,
					server.createConnectToken(playerId),
					generateClientPublicKeyBytes());
			secondClient.send(new DatagramPacket(connectPacket, connectPacket.length,
					InetAddress.getLoopbackAddress(), serverPort));

			assertFalse(readAckSuccess(secondClient));
			sendKeepaliveAndExpectResponse(firstClient, key, 1L);
			assertEquals(1, server.getConnections().size());
			VoiceConnection current = (VoiceConnection) server.getConnections().iterator().next();
			assertEquals(firstClient.getLocalPort(), current.port());
		}
	}

	@Test
	void stalePlayerReconnectFromDifferentAddressAccepted() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("stale-player");

		try (DatagramSocket firstClient = new DatagramSocket();
				 DatagramSocket secondClient = new DatagramSocket()) {
			firstClient.setSoTimeout(TEST_TIMEOUT_MS);
			secondClient.setSoTimeout(TEST_TIMEOUT_MS);
			connectClient(firstClient, playerId);

			VoiceConnection oldConnection = (VoiceConnection) server.getConnections().iterator().next();
			oldConnection.markSeen(System.currentTimeMillis() - 20_000L);

			SecretKey replacementKey = connectClient(secondClient, playerId);

			assertEquals(1, server.getConnections().size());
			VoiceConnection current = (VoiceConnection) server.getConnections().iterator().next();
			assertEquals(secondClient.getLocalPort(), current.port());
			assertFalse(oldConnection.isConnected());
			sendKeepaliveAndExpectResponse(secondClient, replacementKey, 1L);
		}
	}

	@Test
	void stopThenImmediateRestartRetiresOldListenerThreads() throws Exception {
		server.start(serverPort);
		Thread firstListener = server.listenerThread();
		Thread firstCleanup = server.cleanupThread();

		server.stop();
		server.start(serverPort);
		Thread secondListener = server.listenerThread();
		Thread secondCleanup = server.cleanupThread();

		assertFalse(firstListener == secondListener);
		assertFalse(firstCleanup == secondCleanup);
		firstListener.join(TEST_TIMEOUT_MS);
		firstCleanup.join(TEST_TIMEOUT_MS);
		assertFalse(firstListener.isAlive());
		assertFalse(firstCleanup.isAlive());
	}

	@Test
	void inFlightConnectCannotRegisterAfterStopAndRestart() throws Exception {
		server.start(serverPort);
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		server.setBeforeRegisterConnectionHook(() -> {
			paused.countDown();
			try {
				release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});

		PlayerReference playerId = PlayerReference.ofName("inflight-connect");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);
			byte[] connectPacket = VoiceTransport.serializeConnectPacket(
					playerId,
					server.createConnectToken(playerId),
					generateClientPublicKeyBytes());
			Thread sender = new Thread(() -> {
				try {
					client.send(new DatagramPacket(connectPacket, connectPacket.length,
							InetAddress.getLoopbackAddress(), serverPort));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			sender.start();

			assertTrue(paused.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
			server.stop();
			server.start(serverPort);
			release.countDown();
			sender.join(TEST_TIMEOUT_MS);
			Thread.sleep(100);

			assertEquals(0, server.getConnections().size());
			SecretKey key = connectClient(client, playerId);
			assertNotNull(key);
		}
	}

	@Test
	void disconnectingStaleHandleDoesNotRemoveReplacementConnection() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("replaceable-player");

		try (DatagramSocket firstClient = new DatagramSocket();
				 DatagramSocket secondClient = new DatagramSocket()) {
			firstClient.setSoTimeout(TEST_TIMEOUT_MS);
			secondClient.setSoTimeout(TEST_TIMEOUT_MS);
			connectClient(firstClient, playerId);

			VoiceConnection oldConnection = (VoiceConnection) server.getConnections().iterator().next();
			oldConnection.markSeen(System.currentTimeMillis() - 20_000L);
			SecretKey replacementKey = connectClient(secondClient, playerId);

			server.disconnect(oldConnection);

			assertEquals(1, server.getConnections().size());
			VoiceConnection current = (VoiceConnection) server.getConnections().iterator().next();
			assertEquals(secondClient.getLocalPort(), current.port());
			sendKeepaliveAndExpectResponse(secondClient, replacementKey, 1L);
		}
	}

	@Test
	void concurrentSamePlayerConnectLeavesSingleCurrentConnection() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("racing-player");
		VoiceConnection first = new VoiceConnection(
				playerId,
				InetAddress.getLoopbackAddress(),
				41001,
				generateAesKey(),
				System.currentTimeMillis());
		VoiceConnection second = new VoiceConnection(
				playerId,
				InetAddress.getLoopbackAddress(),
				41002,
				generateAesKey(),
				System.currentTimeMillis());

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Thread firstThread = new Thread(() -> {
			ready.countDown();
			awaitLatch(start);
			server.connect(first);
		});
		Thread secondThread = new Thread(() -> {
			ready.countDown();
			awaitLatch(start);
			server.connect(second);
		});

		firstThread.start();
		secondThread.start();
		assertTrue(ready.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
		start.countDown();
		firstThread.join(TEST_TIMEOUT_MS);
		secondThread.join(TEST_TIMEOUT_MS);

		assertEquals(1, server.getConnections().size());
		VoiceConnection current = (VoiceConnection) server.getConnections().iterator().next();
		VoiceConnection stale = current == first ? second : first;
		assertTrue(current.isConnected());
		assertFalse(stale.isConnected());

		server.disconnect(stale);

		assertEquals(1, server.getConnections().size());
		assertTrue(server.getConnections().iterator().next() == current);
	}

	@Test
	void disconnectedHandleCannotSendPacket() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("stale-send-player");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);
			SecretKey key = connectClient(client, playerId);
			VoiceConnection live = (VoiceConnection) server.getConnections().iterator().next();

			byte[] audioData = "hello".getBytes();
			AudioPacket packet = new AudioPacket(playerId, audioData, 1L, System.currentTimeMillis());
			live.sendPacket(packet);
			DatagramPacket firstResponse = new DatagramPacket(new byte[2048], 2048);
			client.receive(firstResponse);
			decryptServerDatagram(firstResponse, key);

			server.disconnect(live);
			live.sendPacket(packet);

			DatagramPacket staleResponse = new DatagramPacket(new byte[2048], 2048);
			boolean received = false;
			try {
				client.receive(staleResponse);
				received = true;
			} catch (SocketTimeoutException expected) {
			}
			assertFalse(received);
		}
	}

	@Test
	void inFlightKeepaliveCannotMutateOrReplyAfterStopAndRestart() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("inflight-keepalive");
		CountDownLatch paused = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);
			SecretKey key = connectClient(client, playerId);
			VoiceConnection live = (VoiceConnection) server.getConnections().iterator().next();
			long before = live.getLastPacketTime();

			server.setBeforeInboundStateMutationHook(() -> {
				paused.countDown();
				try {
					release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});

			byte[] keepalive = VoiceTransport.serializeKeepalivePacket(key, 1L);
			Thread sender = new Thread(() -> {
				try {
					client.send(new DatagramPacket(keepalive, keepalive.length,
							InetAddress.getLoopbackAddress(), serverPort));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			sender.start();

			assertTrue(paused.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
			server.stop();
			server.start(serverPort);
			release.countDown();
			sender.join(TEST_TIMEOUT_MS);

			DatagramPacket response = new DatagramPacket(new byte[256], 256);
			boolean received = false;
			try {
				client.receive(response);
				received = true;
			} catch (SocketTimeoutException expected) {
			}

			assertFalse(received);
			assertEquals(before, live.getLastPacketTime());
			assertFalse(live.isConnected());
		}
	}

	@Test
	void routingReturnedDisconnectedHandleCannotReceiveAudio() throws Exception {
		server.stop();
		StickyRoutingStrategy routing = new StickyRoutingStrategy();
		server = new VoiceTransport(routing);
		server.start(serverPort);

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(200);
			PlayerReference playerId = PlayerReference.ofName("stale-routed-player");
			SecretKey key = connectClient(client, playerId);
			VoiceConnection live = (VoiceConnection) server.getConnections().iterator().next();
			routing.setRecipients(List.of(live));

			AudioPacket packet = new AudioPacket(PlayerReference.ofName("sender"), "hello".getBytes(), 1L, System.currentTimeMillis());
			server.sendAudio(packet);
			DatagramPacket firstResponse = new DatagramPacket(new byte[2048], 2048);
			client.receive(firstResponse);
			decryptServerDatagram(firstResponse, key);

			server.disconnect(live);
			server.sendAudio(packet);

			DatagramPacket staleResponse = new DatagramPacket(new byte[2048], 2048);
			boolean received = false;
			try {
				client.receive(staleResponse);
				received = true;
			} catch (SocketTimeoutException expected) {
			}
			assertFalse(received);
		}
	}

	@Test
	void oversizedAudioPayloadDroppedWithoutStoppingListener() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("oversized-audio-client");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);
			SecretKey key = connectClient(client, playerId);

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			dos.writeByte(PacketType.AUDIO.id);
			dos.writeInt(5_000);
			byte[] encrypted = CryptoUtils.encrypt(bos.toByteArray(), key, 1L,
					CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
			byte[] framed = ByteBuffer.allocate(Long.BYTES + encrypted.length)
				.putLong(1L)
				.put(encrypted)
				.array();
			client.send(new DatagramPacket(framed, framed.length,
					InetAddress.getLoopbackAddress(), serverPort));

			sendKeepaliveAndExpectResponse(client, key, 2L);
			assertTrue(server.isRunning());
		}
	}

	private static SecretKey generateAesKey() {
		try {
			KeyGenerator kg = KeyGenerator.getInstance("AES");
			kg.init(256);
			return kg.generateKey();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static class TestRoutingStrategy implements VoiceRoutingStrategy {
		private volatile boolean broadcast;

		void setBroadcast(boolean broadcast) {
			this.broadcast = broadcast;
		}

		@Override
		public Collection<VoiceClientConnection> route(
			dev.kgoodwin.midnightcouncil.api.voice.VoiceServer server,
			AudioPacket packet,
			dev.kgoodwin.midnightcouncil.api.game.GameState state
		) {
			if (broadcast) {
				List<VoiceClientConnection> result = new ArrayList<>();
				for (VoiceClientConnection c : server.getConnections()) {
					if (!c.getPlayerId().equals(packet.senderId())) {
						result.add(c);
					}
				}
				return result;
			}
			return List.of();
		}
	}

	private static class StickyRoutingStrategy implements VoiceRoutingStrategy {
		private volatile Collection<VoiceClientConnection> recipients = List.of();

		void setRecipients(Collection<VoiceClientConnection> recipients) {
			this.recipients = List.copyOf(recipients);
		}

		@Override
		public Collection<VoiceClientConnection> route(
			dev.kgoodwin.midnightcouncil.api.voice.VoiceServer server,
			AudioPacket packet,
			dev.kgoodwin.midnightcouncil.api.game.GameState state
		) {
			return recipients;
		}
	}

	private static void awaitLatch(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	private static class StateCapturingRoutingStrategy implements VoiceRoutingStrategy {
		private final AtomicReference<GameState> lastState = new AtomicReference<>();
		private final CountDownLatch invoked = new CountDownLatch(1);

		GameState lastState() {
			return lastState.get();
		}

		boolean awaitInvocation() throws InterruptedException {
			return invoked.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		}

		@Override
		public Collection<VoiceClientConnection> route(
			dev.kgoodwin.midnightcouncil.api.voice.VoiceServer server,
			AudioPacket packet,
			dev.kgoodwin.midnightcouncil.api.game.GameState state
		) {
			lastState.set(state);
			invoked.countDown();
			return List.of();
		}
	}

	private static class CountingRoutingStrategy implements VoiceRoutingStrategy {
		private final AtomicInteger invocationCount = new AtomicInteger();
		private final CountDownLatch invoked = new CountDownLatch(1);

		int invocationCount() {
			return invocationCount.get();
		}

		boolean awaitInvocation() throws InterruptedException {
			return awaitInvocation(TEST_TIMEOUT_MS);
		}

		boolean awaitInvocation(long timeoutMs) throws InterruptedException {
			return invoked.await(timeoutMs, TimeUnit.MILLISECONDS);
		}

		@Override
		public Collection<VoiceClientConnection> route(
			dev.kgoodwin.midnightcouncil.api.voice.VoiceServer server,
			AudioPacket packet,
			dev.kgoodwin.midnightcouncil.api.game.GameState state
		) {
			invocationCount.incrementAndGet();
			invoked.countDown();
			return List.of();
		}
	}
}
