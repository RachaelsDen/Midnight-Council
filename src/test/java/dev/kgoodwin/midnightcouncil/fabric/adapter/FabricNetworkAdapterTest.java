package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kgoodwin.midnightcouncil.api.NetworkAdapter;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import java.util.ArrayList;
import java.util.Arrays;
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
    private List<SentPayload> sentPayloads;

    @BeforeEach
    void setUp() {
        server = mock(MinecraftServer.class);
        playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        sentPayloads = new ArrayList<>();
        adapter = new FabricNetworkAdapter(
                server,
                (recipient, payload) -> sentPayloads.add(new SentPayload(recipient, payload)),
                recipient -> !recipient.value().equals("missing"),
                recipient -> !recipient.value().equals("unsupported"));
    }

    @Test
    void sendOutboundPayloadUsesRequestedRecipientsAndPayload() {
        AtomicInteger inboundCalls = new AtomicInteger();
        adapter.registerReceiver("vote", (playerReference, channel, payload) -> inboundCalls.incrementAndGet());

        adapter.sendOutboundPayload(Arrays.asList(PlayerReference.ofName("alice"), PlayerReference.ofName("bob")), "vote", new byte[] {1, 2, 3});

        assertEquals(2, sentPayloads.size());
        assertEquals(PlayerReference.ofName("alice"), sentPayloads.get(0).recipient());
        assertEquals(PlayerReference.ofName("bob"), sentPayloads.get(1).recipient());
        assertEquals("vote", sentPayloads.get(0).payload().channel());
        assertArrayEquals(new byte[] {1, 2, 3}, sentPayloads.get(0).payload().bytes());
        assertEquals(0, inboundCalls.get());
    }

    @Test
    void broadcastPublicPayloadWithNoPlayersIsNoop() {
        when(playerList.getPlayers()).thenReturn(List.of());

        adapter.broadcastPublicPayload("vote", new byte[] {1, 2, 3});

        assertEquals(0, sentPayloads.size());
    }

    @Test
    void sendOutboundPayloadSkipsUnsupportedRecipients() {
        adapter.sendOutboundPayload(
                Arrays.asList(PlayerReference.ofName("alice"), PlayerReference.ofName("unsupported"), PlayerReference.ofName("bob")),
                "vote",
                new byte[] {1, 2, 3});

        assertEquals(2, sentPayloads.size());
        assertEquals(PlayerReference.ofName("alice"), sentPayloads.get(0).recipient());
        assertEquals(PlayerReference.ofName("bob"), sentPayloads.get(1).recipient());
    }

    @Test
    void sendStorytellerPayloadSendsToOnlineRecipient() {
        AtomicInteger inboundCalls = new AtomicInteger();
        byte[] payload = new byte[] {9, 8, 7};
        adapter.registerReceiver("story", (playerReference, inboundChannel, bytes) -> inboundCalls.incrementAndGet());

        adapter.sendStorytellerPayload(PlayerReference.ofName("storyteller"), "story", payload);

        assertEquals(1, sentPayloads.size());
        assertEquals(PlayerReference.ofName("storyteller"), sentPayloads.get(0).recipient());
        assertEquals("story", sentPayloads.get(0).payload().channel());
        assertArrayEquals(payload, sentPayloads.get(0).payload().bytes());
        assertEquals(0, inboundCalls.get());
    }

    @Test
    void sendStorytellerPayloadSkipsUnsupportedRecipient() {
        adapter.sendStorytellerPayload(PlayerReference.ofName("unsupported"), "story", new byte[] {9, 8, 7});

        assertEquals(0, sentPayloads.size());
    }

    @Test
    void dispatchInboundPayloadUsesRequestedHandlerAndPayload() {
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

        adapter.dispatchInboundPayload(PlayerReference.ofName("storyteller"), "story", payload);

        assertEquals(1, storyCalls.get());
        assertEquals(0, voteCalls.get());
        assertEquals(0, sentPayloads.size());
    }

    @Test
    void sendStorytellerPayloadSkipsOfflineStoryteller() {
        NetworkAdapter.PayloadHandler handler = mock(NetworkAdapter.PayloadHandler.class);
        adapter.registerReceiver("story", handler);

        adapter.sendStorytellerPayload(PlayerReference.ofName("missing"), "story", new byte[] {1});

        assertEquals(0, sentPayloads.size());
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dispatchInboundPayloadIgnoresUnknownChannel() {
        AtomicInteger calls = new AtomicInteger();
        adapter.registerReceiver("voice", (playerReference, channel, payload) -> calls.incrementAndGet());

        adapter.dispatchInboundPayload(PlayerReference.ofName("alice"), "unknown", new byte[] {5});

        assertEquals(0, calls.get());
        assertEquals(0, sentPayloads.size());
    }

    private record SentPayload(PlayerReference recipient, MidnightCouncilPayload payload) {
    }
}
