package dev.kgoodwin.midnightcouncil.fabric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MidnightCouncilModTest {

    private FabricNetworkAdapter networkAdapter;

    @BeforeEach
    void setUp() {
        MinecraftServer server = mock(MinecraftServer.class);
        networkAdapter = new FabricNetworkAdapter(server);
    }

    @Test
    void dispatchServerboundPayloadRoutesUuidAndPayloadToRegisteredHandler() {
        UUID playerUuid = UUID.randomUUID();
        byte[] bytes = new byte[] {4, 2};
        MidnightCouncilPayload payload = new MidnightCouncilPayload("vote", bytes);
        AtomicReference<PlayerReference> capturedPlayer = new AtomicReference<>();

        networkAdapter.registerReceiver("vote", (playerReference, channel, inboundBytes) -> {
            capturedPlayer.set(playerReference);
            assertEquals("vote", channel);
            assertArrayEquals(bytes, inboundBytes);
        });

        MidnightCouncilMod.dispatchServerboundPayload(networkAdapter, playerUuid, payload);

        assertNotNull(capturedPlayer.get());
        assertEquals(PlayerReference.from(playerUuid), capturedPlayer.get());
    }
}
