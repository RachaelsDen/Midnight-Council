package dev.kgoodwin.midnightcouncil.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.kgoodwin.midnightcouncil.voice.VoiceClientService;
import dev.kgoodwin.midnightcouncil.voice.VoiceClientTransport;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;
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
    void statePayloadIsDeserializedAndAccessible() {
        dev.kgoodwin.midnightcouncil.api.game.GameState state = new dev.kgoodwin.midnightcouncil.api.game.GameState();
        state.setPhase(dev.kgoodwin.midnightcouncil.api.GamePhase.SETUP);
        byte[] encoded = dev.kgoodwin.midnightcouncil.api.game.GameStateCodec.encode(state);

        MidnightCouncilClient client = new MidnightCouncilClient();
        client.dispatchClientboundPayload("midnightcouncil:state", encoded);

        dev.kgoodwin.midnightcouncil.api.game.GameStateSnapshot snapshot = client.getCurrentGameState();
        assertNotNull(snapshot);
        assertEquals(dev.kgoodwin.midnightcouncil.api.GamePhase.SETUP, snapshot.phase());
    }

    @Test
    void gameStateClearedOnDisconnect() {
        dev.kgoodwin.midnightcouncil.api.game.GameState state = new dev.kgoodwin.midnightcouncil.api.game.GameState();
        state.setPhase(dev.kgoodwin.midnightcouncil.api.GamePhase.SETUP);
        byte[] encoded = dev.kgoodwin.midnightcouncil.api.game.GameStateCodec.encode(state);

        MidnightCouncilClient client = new MidnightCouncilClient();
        client.dispatchClientboundPayload("midnightcouncil:state", encoded);
        assertNotNull(client.getCurrentGameState());

        client.onDisconnect();

        assertNull(client.getCurrentGameState());
    }

    @Test
    void malformedStatePayloadDoesNotCrash() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        byte[] garbage = new byte[]{(byte) 0xFF, 0x00, 0x42};

        client.dispatchClientboundPayload("midnightcouncil:state", garbage);

        assertNull(client.getCurrentGameState());
    }

    @Test
    void clearActiveVoiceTransportPreservesChannelHandlers() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        AtomicBoolean stateDispatched = new AtomicBoolean();
        AtomicBoolean eventDispatched = new AtomicBoolean();

        client.registerChannelHandler("midnightcouncil:state", bytes -> stateDispatched.set(true));
        client.registerChannelHandler("midnightcouncil:event", bytes -> eventDispatched.set(true));

        client.clearActiveVoiceTransport();

        client.dispatchClientboundPayload("midnightcouncil:state", new byte[]{7, 7});
        client.dispatchClientboundPayload("midnightcouncil:event", new byte[]{8, 8});

        assertTrue(stateDispatched.get());
        assertTrue(eventDispatched.get());
    }

    @Test
    void getInstanceReturnsInitializedInstance() {
        assertNull(MidnightCouncilClient.getInstance());
    }

    @Test
    void staleGenerationTransportIsClosedAndNotPublished() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        VoiceClientTransport staleTransport = mock(VoiceClientTransport.class);
        VoiceClientService staleService = mock(VoiceClientService.class);
        long generation = client.currentVoiceSessionGeneration();

        client.clearActiveVoiceTransport();

        assertFalse(client.publishActiveVoiceTransport(generation, staleTransport, staleService));
        verify(staleService).isConnected();
        verify(staleTransport).close();
        assertNull(client.activeVoiceTransportForTest());
        assertNull(client.activeVoiceServiceForTest());
    }

    @Test
    void clearActiveVoiceTransportClosesPublishedTransport() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        VoiceClientTransport activeTransport = mock(VoiceClientTransport.class);
        VoiceClientService activeService = mock(VoiceClientService.class);
        when(activeService.isConnected()).thenReturn(true);

        assertTrue(client.publishActiveVoiceTransport(client.currentVoiceSessionGeneration(), activeTransport, activeService));
        assertSame(activeTransport, client.activeVoiceTransportForTest());
        assertSame(activeService, client.activeVoiceServiceForTest());

        client.clearActiveVoiceTransport();

        verify(activeService).isConnected();
        verify(activeService).disconnect();
        verify(activeTransport).close();
        assertNull(client.activeVoiceTransportForTest());
        assertNull(client.activeVoiceServiceForTest());
    }

    @Test
    void finishConnectedVoiceTransportSetupClosesTransportWhenServiceSetupFails() throws Exception {
        MidnightCouncilClient client = new MidnightCouncilClient();
        VoiceClientTransport transport = mock(VoiceClientTransport.class);
        FabricVoiceAdapter.VoiceConnectHandoff handoff = new FabricVoiceAdapter.VoiceConnectHandoff(
                24454,
                "player-one",
                new byte[40]);

        assertThrows(IllegalStateException.class, () -> client.finishConnectedVoiceTransportSetup(
                InetAddress.getLoopbackAddress(),
                handoff,
                client.currentVoiceSessionGeneration(),
                transport,
                ignored -> {
                    throw new IllegalStateException("boom");
                }));

        verify(transport).close();
        verifyNoMoreInteractions(transport);
        assertNull(client.activeVoiceTransportForTest());
        assertNull(client.activeVoiceServiceForTest());
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
