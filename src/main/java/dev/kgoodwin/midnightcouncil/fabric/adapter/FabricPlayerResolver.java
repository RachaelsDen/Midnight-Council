package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class FabricPlayerResolver {

    private FabricPlayerResolver() {
    }

    static ServerPlayer resolve(MinecraftServer server, PlayerReference playerReference) {
        String value = playerReference.value();
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(value));
            if (player != null) {
                return player;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to name lookup for name-backed player references.
        }
        return server.getPlayerList().getPlayerByName(value);
    }
}
