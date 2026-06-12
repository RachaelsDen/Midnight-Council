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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricWorldAdapter implements WorldAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricWorldAdapter.class);

    private final MinecraftServer server;
    private final EntitySpawner entitySpawner;

    public FabricWorldAdapter(MinecraftServer server) {
        this(server, (level, entityType, position) -> entityType.spawn(level, toBlockPos(position), EntitySpawnReason.COMMAND));
    }

    FabricWorldAdapter(MinecraftServer server, EntitySpawner entitySpawner) {
        this.server = server;
        this.entitySpawner = entitySpawner;
    }

    @Override
    public void setBlock(Position position, String blockType) {
        BlockPos blockPos = toBlockPos(position);
        Identifier blockId = Identifier.parse(blockType);
        overworld().setBlockAndUpdate(blockPos, BuiltInRegistries.BLOCK.getValue(blockId).defaultBlockState());
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
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
        if (type == null) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
        return type;
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
