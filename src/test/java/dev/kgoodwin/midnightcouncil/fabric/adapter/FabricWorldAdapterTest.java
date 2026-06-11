package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricWorldAdapterTest {

    private ServerLevel serverLevel;
    private PlayerList playerList;
    private FabricWorldAdapter adapter;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        MinecraftServer server = mock(MinecraftServer.class);
        serverLevel = mock(ServerLevel.class);
        playerList = mock(PlayerList.class);
        when(server.overworld()).thenReturn(serverLevel);
        when(server.getPlayerList()).thenReturn(playerList);
        adapter = new FabricWorldAdapter(server);
    }

    @Test
    void setBlockResolvesBlockAndUpdatesOverworld() {
        Position position = new Position(1.5, 64.0, -3.7);

        adapter.setBlock(position, "minecraft:stone");

        verify(serverLevel).setBlockAndUpdate(eq(BlockPos.containing(1.5, 64.0, -3.7)), eq(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void clearBlockReplacesTargetWithAir() {
        Position position = new Position(-2.2, 70.0, 5.9);

        adapter.clearBlock(position);

        verify(serverLevel).setBlockAndUpdate(eq(BlockPos.containing(-2.2, 70.0, 5.9)), eq(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void getBlockTypeReturnsActualWorldBlockId() {
        Position position = new Position(2.0, 63.0, 9.0);
        when(serverLevel.getBlockState(BlockPos.containing(2.0, 63.0, 9.0))).thenReturn(Blocks.STONE.defaultBlockState());

        String blockType = adapter.getBlockType(position);

        assertEquals("minecraft:stone", blockType);
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
