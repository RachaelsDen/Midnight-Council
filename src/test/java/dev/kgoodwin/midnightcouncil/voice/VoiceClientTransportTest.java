package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceRoutingStrategy;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceClientTransportTest {

    private static final int TEST_TIMEOUT_MS = 2_000;

    private VoiceTransport server;
    private int serverPort;
    private RecordingRoutingStrategy routingStrategy;

    @BeforeEach
    void setUp() throws Exception {
        try (DatagramSocket probe = new DatagramSocket(0)) {
            serverPort = probe.getLocalPort();
        }
        routingStrategy = new RecordingRoutingStrategy();
        server = new VoiceTransport(routingStrategy, GameState::new, "token-secret");
        server.start(serverPort);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void connectRegistersServerConnectionAndSharesSessionKey() throws Exception {
        PlayerReference playerId = PlayerReference.ofName("transport-client");
        byte[] token = server.createConnectToken(playerId);

        try (VoiceClientTransport transport = VoiceClientTransport.connect(
                InetAddress.getLoopbackAddress(),
                serverPort,
                playerId,
                token,
                TEST_TIMEOUT_MS)) {
            awaitConnectionCount(1);
            Collection<dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection> connections = server.getConnections();
            VoiceConnection connection = (VoiceConnection) connections.iterator().next();
            SecretKey sessionKey = transport.sessionKey();

            assertNotNull(sessionKey);
            assertEquals(connection.aesKey(), sessionKey);
            assertEquals(playerId, transport.playerId());
        }
    }

    @Test
    void connectRejectsInvalidToken() {
        PlayerReference playerId = PlayerReference.ofName("transport-client");
        byte[] token = server.createConnectToken(playerId);
        server.invalidateConnectToken(token);

        assertThrows(IOException.class, () -> VoiceClientTransport.connect(
                InetAddress.getLoopbackAddress(),
                serverPort,
                playerId,
                token,
                TEST_TIMEOUT_MS));
    }

    @Test
    void closeDisconnectsServerConnection() throws Exception {
        PlayerReference playerId = PlayerReference.ofName("transport-client");
        byte[] token = server.createConnectToken(playerId);
        VoiceClientTransport transport = VoiceClientTransport.connect(
                InetAddress.getLoopbackAddress(),
                serverPort,
                playerId,
                token,
                TEST_TIMEOUT_MS);

        try {
            awaitConnectionCount(1);
        } finally {
            transport.close();
        }

        awaitConnectionCount(0);
    }

    @Test
    void scheduledKeepalivePreventsServerTimeout() throws Exception {
        PlayerReference playerId = PlayerReference.ofName("transport-client");
        byte[] token = server.createConnectToken(playerId);

        try (VoiceClientTransport transport = VoiceClientTransport.connect(
                InetAddress.getLoopbackAddress(),
                serverPort,
                playerId,
                token,
                TEST_TIMEOUT_MS)) {
            awaitConnectionCount(1);
            Thread.sleep(16_000L);
            awaitConnectionCount(1);
        }
    }

    @Test
    void deserializeAudioPayloadRoundTrip() {
        AudioPacket original = new AudioPacket(PlayerReference.ofName("sender"), new byte[]{1, 2, 3, 4}, 17L, 29L);

        AudioPacket decoded = VoiceTransport.deserializeAudioPayload(
                original.senderId(),
                VoiceTransport.serializeAudioPayload(original));

        assertEquals(original, decoded);
    }

    @Test
    void sendAudioDeliversToServer() throws Exception {
        PlayerReference playerId = PlayerReference.ofName("transport-client");
        byte[] token = server.createConnectToken(playerId);

        try (VoiceClientTransport transport = VoiceClientTransport.connect(
                InetAddress.getLoopbackAddress(),
                serverPort,
                playerId,
                token,
                TEST_TIMEOUT_MS)) {
            awaitConnectionCount(1);

            byte[] encoded = new byte[]{9, 8, 7, 6};
            long sequenceNumber = 123L;
            long timestamp = 456L;
            transport.sendAudio(encoded, sequenceNumber, timestamp);

            assertTrue(routingStrategy.awaitAudio());
            AudioPacket received = routingStrategy.lastPacket();
            assertNotNull(received);
            assertEquals(playerId, received.senderId());
            assertArrayEquals(encoded, received.encodedData());
            assertEquals(sequenceNumber, received.sequenceNumber());
            assertEquals(timestamp, received.timestamp());
        }
    }

    @Test
    void receiveLoopDeliversAudioToHandler() throws Exception {
        routingStrategy.setRecipientsSupplier(server::getConnections);
        PlayerReference playerId = PlayerReference.ofName("transport-client");
        byte[] token = server.createConnectToken(playerId);

        try (VoiceClientTransport transport = VoiceClientTransport.connect(
                InetAddress.getLoopbackAddress(),
                serverPort,
                playerId,
                token,
                TEST_TIMEOUT_MS)) {
            awaitConnectionCount(1);

            AtomicReference<AudioPacket> received = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            transport.setAudioHandler(packet -> {
                received.set(packet);
                latch.countDown();
            });

            AudioPacket outbound = new AudioPacket(PlayerReference.ofName("sender"), new byte[]{4, 5, 6}, 77L, 88L);
            server.sendAudio(outbound);

            assertTrue(latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertArrayEquals(outbound.encodedData(), received.get().encodedData());
            assertEquals(outbound.sequenceNumber(), received.get().sequenceNumber());
            assertEquals(outbound.timestamp(), received.get().timestamp());
        }
    }

    private void awaitConnectionCount(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (server.getConnections().size() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, server.getConnections().size());
    }

    private static final class RecordingRoutingStrategy implements VoiceRoutingStrategy {
        private final AtomicReference<AudioPacket> lastPacket = new AtomicReference<>();
        private final CountDownLatch audioLatch = new CountDownLatch(1);
        private volatile java.util.function.Supplier<Collection<dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection>> recipientsSupplier = List::of;

        @Override
        public Collection<dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection> route(
                dev.kgoodwin.midnightcouncil.api.voice.VoiceServer server,
                dev.kgoodwin.midnightcouncil.api.voice.AudioPacket packet,
                GameState gameState) {
            lastPacket.set(packet);
            audioLatch.countDown();
            return recipientsSupplier.get();
        }

        void setRecipientsSupplier(
                java.util.function.Supplier<Collection<dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection>> recipientsSupplier) {
            this.recipientsSupplier = recipientsSupplier;
        }

        boolean awaitAudio() throws InterruptedException {
            return audioLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        AudioPacket lastPacket() {
            return lastPacket.get();
        }
    }

    private static final class NullRoutingStrategy implements VoiceRoutingStrategy {
        @Override
        public Collection<dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection> route(
                dev.kgoodwin.midnightcouncil.api.voice.VoiceServer server,
                dev.kgoodwin.midnightcouncil.api.voice.AudioPacket packet,
                GameState gameState) {
            return java.util.List.of();
        }
    }
}
