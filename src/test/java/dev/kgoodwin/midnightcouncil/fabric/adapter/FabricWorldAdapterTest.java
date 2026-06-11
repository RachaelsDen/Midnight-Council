package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricWorldAdapterTest {

    private PlayerList playerList;
    private FabricWorldAdapter adapter;

    @BeforeEach
    void setUp() {
        MinecraftServer server = mock(MinecraftServer.class);
        playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        adapter = new FabricWorldAdapter(server);
    }

    @Test
    void getPlayerPositionReturnsEmptyForMissingPlayer() {
        Optional<Position> position = adapter.getPlayerPosition(PlayerReference.ofName("missing"));

        assertTrue(position.isEmpty());
    }

    @Test
    void getPlayerPositionDoesNotThrowForUuidShapedMissingReference() {
        Optional<Position> position = adapter.getPlayerPosition(PlayerReference.ofUuid(java.util.UUID.fromString("00000000-0000-0000-0000-000000000123")));

        assertEquals(Optional.empty(), position);
    }
}
