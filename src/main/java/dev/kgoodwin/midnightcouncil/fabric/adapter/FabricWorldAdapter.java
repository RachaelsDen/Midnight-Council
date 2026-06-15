package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.WorldAdapter;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FabricWorldAdapter implements WorldAdapter {

    private final MinecraftServer server;
    private final EntitySpawner entitySpawner;

    public FabricWorldAdapter(MinecraftServer server) {
        this(server, (level, entityType, position) -> {
            Vec3 exactPosition = new Vec3(position.x(), position.y(), position.z());
            Entity entity = entityType.spawn(level, toBlockPos(position), EntitySpawnReason.COMMAND);
            if (entity != null) {
                entity.setPos(exactPosition.x(), exactPosition.y(), exactPosition.z());
            }
        });
    }

    FabricWorldAdapter(MinecraftServer server, EntitySpawner entitySpawner) {
        this.server = server;
        this.entitySpawner = entitySpawner;
    }

    @Override
    public void setBlock(Position position, String blockType) {
        BlockPos blockPos = toBlockPos(position);
        overworld().setBlockAndUpdate(blockPos, requireBlock(blockType).defaultBlockState());
    }

    @Override
    public void clearBlock(Position position) {
        overworld().setBlockAndUpdate(toBlockPos(position), Blocks.AIR.defaultBlockState());
    }

    @Override
    public String getBlockType(Position position) {
        BlockState blockState = overworld().getBlockState(toBlockPos(position));
        return BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
    }

    @Override
    public void spawnEntity(String entityType, Position position) {
        entitySpawner.spawn(overworld(), requireEntityType(entityType), position);
    }

    @Override
    public Optional<Position> getPlayerPosition(PlayerReference playerReference) {
        ServerPlayer player = FabricPlayerResolver.resolve(server, playerReference);
        if (player != null) {
            return Optional.of(new Position(player.getX(), player.getY(), player.getZ()));
        }
        return Optional.empty();
    }

    @Override
    public void playSound(Position position, String soundId, float volume, float pitch) {
        overworld().playSound(
                null,
                position.x(),
                position.y(),
                position.z(),
                requireSoundEvent(soundId),
                SoundSource.MASTER,
                volume,
                pitch);
    }

    @Override
    public void spawnParticles(String particleId, Position position, int count) {
        overworld().sendParticles(
                requireSimpleParticleType(particleId),
                position.x(),
                position.y(),
                position.z(),
                count,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private ServerLevel overworld() {
        return server.overworld();
    }

    private static BlockPos toBlockPos(Position position) {
        return BlockPos.containing(position.x(), position.y(), position.z());
    }

    private static EntityType<?> requireEntityType(String entityType) {
        Identifier entityId = Identifier.parse(entityType);
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown entity type: " + entityType));
    }

    private static Block requireBlock(String blockType) {
        Identifier blockId = Identifier.parse(blockType);
        return BuiltInRegistries.BLOCK.getOptional(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown block type: " + blockType));
    }

    private static SoundEvent requireSoundEvent(String soundId) {
        Identifier id = Identifier.parse(soundId);
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (soundEvent == null) {
            throw new IllegalArgumentException("Unknown sound event: " + soundId);
        }
        return soundEvent;
    }

    private static SimpleParticleType requireSimpleParticleType(String particleId) {
        Identifier id = Identifier.parse(particleId);
        ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
        if (particleType == null) {
            throw new IllegalArgumentException("Unknown particle type: " + particleId);
        }
        if (particleType instanceof SimpleParticleType simpleParticleType) {
            return simpleParticleType;
        }
        throw new IllegalArgumentException("Unsupported particle type requires options: " + particleId);
    }

    @FunctionalInterface
    interface EntitySpawner {
        void spawn(ServerLevel level, EntityType<?> entityType, Position position);
    }
}
