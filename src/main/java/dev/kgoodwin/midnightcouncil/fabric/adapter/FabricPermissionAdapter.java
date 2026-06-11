package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PermissionAdapter;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricPermissionAdapter implements PermissionAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricPermissionAdapter.class);

    private final MinecraftServer server;

    public FabricPermissionAdapter(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean isStoryteller(PlayerReference playerReference) {
        ServerPlayer player = FabricPlayerResolver.resolve(server, playerReference);
        if (player == null) {
            LOG.warn("isStoryteller: player {} not online", playerReference.value());
            return false;
        }
        return server.getPlayerList().isOp(player.nameAndId());
    }
}
