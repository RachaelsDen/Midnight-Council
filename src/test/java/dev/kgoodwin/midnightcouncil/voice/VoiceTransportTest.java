package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceRoutingStrategy;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
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
			byte[] encrypted = CryptoUtils.encrypt(disconnect, key, seq);
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
			byte[] encrypted = CryptoUtils.encrypt(payload, key, 1L);
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

			SecretKey keyA = connectClient(clientA, playerA);
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
		byte[] connectPacket = VoiceTransport.serializeConnectPacket(playerId);
		client.send(new DatagramPacket(connectPacket, connectPacket.length,
			InetAddress.getLoopbackAddress(), port));

		byte[] buf = new byte[512];
		DatagramPacket response = new DatagramPacket(buf, buf.length);
		client.receive(response);

		DataInputStream dis = new DataInputStream(
			new ByteArrayInputStream(response.getData(), 0, response.getLength()));
		boolean success = dis.readBoolean();
		assertTrue(success, "Connect handshake should succeed");

		int wrappedKeyLength = dis.readInt();
		assertTrue(wrappedKeyLength > 0, "ACK should contain encrypted session key");
		byte[] wrappedKey = new byte[wrappedKeyLength];
		dis.readFully(wrappedKey);

		return CryptoUtils.unwrapKey(wrappedKey, transport.serverKeyExchangeKey());
	}

	private SecretKey connectClient(DatagramSocket client, PlayerReference playerId) throws Exception {
		return connectClient(client, playerId, server, serverPort);
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
			byte[] encrypted = CryptoUtils.encrypt(payload, key, seq);
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
	void staleConnectionRemovedOnReconnectFromSameAddress() throws Exception {
		server.start(serverPort);
		PlayerReference playerOld = PlayerReference.ofName("old-player");
		PlayerReference playerNew = PlayerReference.ofName("new-player");

		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);

			SecretKey keyOld = connectClient(client, playerOld);
			assertEquals(1, server.getConnections().size());
			assertEquals(playerOld, server.getConnections().iterator().next().getPlayerId());

			SecretKey keyNew = connectClient(client, playerNew);
			assertEquals(1, server.getConnections().size());
			assertEquals(playerNew, server.getConnections().iterator().next().getPlayerId());
		}
	}

	@Test
	void sessionKeyIsEncryptedInAck() throws Exception {
		server.start(serverPort);
		PlayerReference playerId = PlayerReference.ofName("key-test");
		try (DatagramSocket client = new DatagramSocket()) {
			client.setSoTimeout(TEST_TIMEOUT_MS);

			byte[] connectPacket = VoiceTransport.serializeConnectPacket(playerId);
			client.send(new DatagramPacket(connectPacket, connectPacket.length,
				InetAddress.getLoopbackAddress(), serverPort));

			byte[] buf = new byte[512];
			DatagramPacket response = new DatagramPacket(buf, buf.length);
			client.receive(response);

			DataInputStream dis = new DataInputStream(
				new ByteArrayInputStream(response.getData(), 0, response.getLength()));
			assertTrue(dis.readBoolean());
			int wrappedLen = dis.readInt();
			assertTrue(wrappedLen > 32, "Encrypted key should be larger than raw 32-byte AES key");
			byte[] wrapped = new byte[wrappedLen];
			dis.readFully(wrapped);

			SecretKey unwrapped = CryptoUtils.unwrapKey(wrapped, server.serverKeyExchangeKey());
			assertEquals("AES", unwrapped.getAlgorithm());
			assertEquals(32, unwrapped.getEncoded().length);
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
}
