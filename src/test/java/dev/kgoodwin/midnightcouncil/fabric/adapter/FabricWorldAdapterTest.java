package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FabricWorldAdapterTest {

    private ServerLevel serverLevel;
    private PlayerList playerList;
    private FabricWorldAdapter adapter;
    private CapturingEntitySpawner entitySpawner;

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
        entitySpawner = new CapturingEntitySpawner();
        adapter = new FabricWorldAdapter(server, entitySpawner);
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
    void setBlockFailsExplicitlyForUnknownBlockId() {
        Position position = new Position(1.5, 64.0, -3.7);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adapter.setBlock(position, "minecraft:not_a_real_block"));

        assertTrue(exception.getMessage().contains("Unknown block type"));
    }

    @Test
    void getBlockTypeReturnsActualWorldBlockId() {
        Position position = new Position(2.0, 63.0, 9.0);
        when(serverLevel.getBlockState(BlockPos.containing(2.0, 63.0, 9.0))).thenReturn(Blocks.STONE.defaultBlockState());

        String blockType = adapter.getBlockType(position);

        assertEquals("minecraft:stone", blockType);
    }

    @Test
    void spawnEntityResolvesEntityTypeAndDelegatesToSpawner() {
        Position position = new Position(3.0, 65.0, -7.0);

        adapter.spawnEntity("minecraft:pig", position);

        Assertions.assertSame(serverLevel, entitySpawner.level);
        assertEquals(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:pig")), entitySpawner.entityType);
        assertEquals(position, entitySpawner.position);
    }

    @Test
    void spawnEntityFailsExplicitlyForUnknownEntityType() {
        Position position = new Position(3.0, 65.0, -7.0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adapter.spawnEntity("minecraft:not_a_real_entity", position));

        assertTrue(exception.getMessage().contains("Unknown entity type"));
    }

    @Test
    void playSoundUsesServerLevelSoundBroadcast() {
        Position position = new Position(10.5, 64.0, -20.0);
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:entity.experience_orb.pickup"));

        adapter.playSound(position, "minecraft:entity.experience_orb.pickup", 0.7f, 1.2f);

        verify(serverLevel).playSound(null, 10.5, 64.0, -20.0, soundEvent, SoundSource.MASTER, 0.7f, 1.2f);
    }

    @Test
    void spawnParticlesUsesServerLevelParticleBroadcast() {
        Position position = new Position(5.0, 70.0, 15.0);
        SimpleParticleType particleType = (SimpleParticleType) BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse("minecraft:smoke"));

        adapter.spawnParticles("minecraft:smoke", position, 10);

        verify(serverLevel).sendParticles(particleType, 5.0, 70.0, 15.0, 10, 0.0, 0.0, 0.0, 0.0);
    }

    @Test
    void spawnParticlesFailsExplicitlyForOptionParticles() {
        Position position = new Position(5.0, 70.0, 15.0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> adapter.spawnParticles("minecraft:block", position, 10));

        assertTrue(exception.getMessage().contains("Unsupported particle type requires options"));
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

    private static final class CapturingEntitySpawner implements FabricWorldAdapter.EntitySpawner {
        private ServerLevel level;
        private EntityType<?> entityType;
        private Position position;

        @Override
        public void spawn(ServerLevel level, EntityType<?> entityType, Position position) {
            this.level = level;
            this.entityType = entityType;
            this.position = position;
        }
    }
}
