package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.WorldAdapter;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
        LOG.debug("setBlock({}, {}) — stub", position, blockType);
    }

    @Override
    public void clearBlock(Position position) {
        LOG.debug("clearBlock({}) — stub", position);
    }

    @Override
    public String getBlockType(Position position) {
        LOG.debug("getBlockType({}) — stub", position);
        return "air";
    }

    @Override
    public void spawnEntity(String entityType, Position position) {
        LOG.debug("spawnEntity({}, {}) — stub", entityType, position);
    }

    @Override
    public Optional<Position> getPlayerPosition(PlayerReference playerReference) {
        ServerPlayer player = server.getPlayerList()
                .getPlayer(UUID.fromString(playerReference.value()));
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
}
