package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.WorldAdapter;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricWorldAdapter implements WorldAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricWorldAdapter.class);

    private final MinecraftServer server;

    public FabricWorldAdapter(MinecraftServer server) {
        this.server = server;
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
        LOG.debug("spawnEntity({}, {}) — stub", entityType, position);
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
        LOG.debug("playSound({}, {}, {}, {}) — stub", position, soundId, volume, pitch);
    }

    @Override
    public void spawnParticles(String particleId, Position position, int count) {
        LOG.debug("spawnParticles({}, {}, {}) — stub", particleId, position, count);
    }

    private ServerLevel overworld() {
        return server.overworld();
    }

    private static BlockPos toBlockPos(Position position) {
        return BlockPos.containing(position.x(), position.y(), position.z());
    }
}
