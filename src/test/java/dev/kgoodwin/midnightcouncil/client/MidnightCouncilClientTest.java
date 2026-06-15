package dev.kgoodwin.midnightcouncil.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.kgoodwin.midnightcouncil.voice.VoiceClientTransport;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

class MidnightCouncilClientTest {

    @Test
    void staleGenerationTransportIsClosedAndNotPublished() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        VoiceClientTransport staleTransport = mock(VoiceClientTransport.class);
        long generation = client.currentVoiceSessionGeneration();

        client.clearActiveVoiceTransport();

        assertFalse(client.publishActiveVoiceTransport(generation, staleTransport));
        verify(staleTransport).close();
        assertNull(client.activeVoiceTransportForTest());
    }

    @Test
    void clearActiveVoiceTransportClosesPublishedTransport() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        VoiceClientTransport activeTransport = mock(VoiceClientTransport.class);

        assertTrue(client.publishActiveVoiceTransport(client.currentVoiceSessionGeneration(), activeTransport));
        assertSame(activeTransport, client.activeVoiceTransportForTest());

        client.clearActiveVoiceTransport();

        verify(activeTransport).close();
        assertNull(client.activeVoiceTransportForTest());
    }

    @Test
    void resolveVoiceHostReturnsLoopbackForNonInetSocketAddress() throws Exception {
        SocketAddress nonInetAddress = new SocketAddress() {};

        InetAddress resolvedHost = MidnightCouncilClient.resolveVoiceHost(nonInetAddress);

        assertEquals(InetAddress.getLoopbackAddress(), resolvedHost);
    }

    @Test
    void resolveVoiceHostResolvesInetSocketAddress() throws Exception {
        SocketAddress inetAddress = new InetSocketAddress("127.0.0.1", 25565);

        InetAddress resolvedHost = MidnightCouncilClient.resolveVoiceHost(inetAddress);

        assertEquals(InetAddress.getByName("127.0.0.1"), resolvedHost);
    }
}
