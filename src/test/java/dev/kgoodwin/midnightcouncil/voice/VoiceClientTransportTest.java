package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceRoutingStrategy;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceClientTransportTest {

    private static final int TEST_TIMEOUT_MS = 2_000;

    private VoiceTransport server;
    private int serverPort;

    @BeforeEach
    void setUp() throws Exception {
        try (DatagramSocket probe = new DatagramSocket(0)) {
            serverPort = probe.getLocalPort();
        }
        server = new VoiceTransport(new NullRoutingStrategy(), GameState::new, "token-secret");
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
