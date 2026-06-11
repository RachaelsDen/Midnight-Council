package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kgoodwin.midnightcouncil.api.NetworkAdapter;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricNetworkAdapterTest {

    private MinecraftServer server;
    private PlayerList playerList;
    private FabricNetworkAdapter adapter;

    @BeforeEach
    void setUp() {
        server = mock(MinecraftServer.class);
        playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        adapter = new FabricNetworkAdapter(server);
    }

    @Test
    void broadcastPublicPayloadDispatchesOnlyToRequestedChannel() {
        AtomicInteger voteCalls = new AtomicInteger();
        AtomicInteger voiceCalls = new AtomicInteger();
        adapter.registerReceiver("vote", (playerReference, channel, payload) -> voteCalls.incrementAndGet());
        adapter.registerReceiver("voice", (playerReference, channel, payload) -> voiceCalls.incrementAndGet());

        adapter.dispatchToChannel(List.of(PlayerReference.ofName("alice"), PlayerReference.ofName("bob")), "vote", new byte[] {1, 2, 3});

        assertEquals(2, voteCalls.get());
        assertEquals(0, voiceCalls.get());
    }

    @Test
    void dispatchToChannelUsesRequestedHandlerAndPayload() {
        AtomicInteger voteCalls = new AtomicInteger();
        AtomicInteger storyCalls = new AtomicInteger();
        byte[] payload = new byte[] {9, 8, 7};
        adapter.registerReceiver("vote", (playerReference, channel, bytes) -> voteCalls.incrementAndGet());
        adapter.registerReceiver("story", (playerReference, channel, bytes) -> {
            storyCalls.incrementAndGet();
            assertEquals(PlayerReference.ofName("storyteller"), playerReference);
            assertEquals("story", channel);
            assertArrayEquals(payload, bytes);
        });

        adapter.dispatchToChannel(List.of(PlayerReference.ofName("storyteller")), "story", payload);

        assertEquals(0, voteCalls.get());
        assertEquals(1, storyCalls.get());
    }

    @Test
    void sendStorytellerPayloadSkipsOfflineStoryteller() {
        NetworkAdapter.PayloadHandler handler = mock(NetworkAdapter.PayloadHandler.class);
        adapter.registerReceiver("story", handler);

        adapter.sendStorytellerPayload(PlayerReference.ofName("missing"), "story", new byte[] {1});

        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void broadcastPublicPayloadIgnoresUnknownChannel() {
        AtomicInteger calls = new AtomicInteger();
        adapter.registerReceiver("voice", (playerReference, channel, payload) -> calls.incrementAndGet());

        adapter.dispatchToChannel(List.of(PlayerReference.ofName("alice")), "unknown", new byte[] {5});

        assertEquals(0, calls.get());
    }
}
