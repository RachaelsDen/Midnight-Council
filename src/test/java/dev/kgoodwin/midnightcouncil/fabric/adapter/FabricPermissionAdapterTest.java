package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricPermissionAdapterTest {

    private FabricPermissionAdapter adapter;

    @BeforeEach
    void setUp() {
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        adapter = new FabricPermissionAdapter(server);
    }

    @Test
    void isStorytellerReturnsFalseForMissingNameBackedPlayerReference() {
        boolean result = assertDoesNotThrow(() -> adapter.isStoryteller(PlayerReference.ofName("alice")));

        assertFalse(result);
    }
}
