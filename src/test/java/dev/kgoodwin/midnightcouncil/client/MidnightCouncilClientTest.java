package dev.kgoodwin.midnightcouncil.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class MidnightCouncilClientTest {

    @Test
    void channelHandlerIsDispatchedWithPayloadBytes() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        AtomicReference<byte[]> received = new AtomicReference<>();
        byte[] expectedPayload = new byte[]{1, 2, 3};

        client.registerChannelHandler("midnightcouncil:state", received::set);
        client.dispatchClientboundPayload("midnightcouncil:state", expectedPayload);

        assertArrayEquals(expectedPayload, received.get());
    }

    @Test
    void noHandlerMeansChannelPayloadIsNotDispatched() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        AtomicBoolean dispatched = new AtomicBoolean();

        client.registerChannelHandler("midnightcouncil:state", bytes -> dispatched.set(true));
        client.dispatchClientboundPayload("midnightcouncil:unregistered", new byte[]{42});

        assertFalse(dispatched.get());
    }

    @Test
    void unregisterChannelHandlerRemovesDispatchTarget() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        AtomicBoolean dispatched = new AtomicBoolean();

        client.registerChannelHandler("midnightcouncil:state", bytes -> dispatched.set(true));
        client.unregisterChannelHandler("midnightcouncil:state");
        client.dispatchClientboundPayload("midnightcouncil:state", new byte[]{1});

        assertFalse(dispatched.get());
    }

    @Test
    void clearActiveVoiceTransportRemovesAllChannelHandlers() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        AtomicBoolean stateDispatched = new AtomicBoolean();
        AtomicBoolean eventDispatched = new AtomicBoolean();

        client.registerChannelHandler("midnightcouncil:state", bytes -> stateDispatched.set(true));
        client.registerChannelHandler("midnightcouncil:event", bytes -> eventDispatched.set(true));

        client.clearActiveVoiceTransport();

        client.dispatchClientboundPayload("midnightcouncil:state", new byte[]{7, 7});
        client.dispatchClientboundPayload("midnightcouncil:event", new byte[]{8, 8});

        assertFalse(stateDispatched.get());
        assertFalse(eventDispatched.get());
    }

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
