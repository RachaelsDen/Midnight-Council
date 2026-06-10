package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.NetworkAdapter;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricNetworkAdapter implements NetworkAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricNetworkAdapter.class);

    private final MinecraftServer server;
    private final Map<String, PayloadHandler> handlers = new ConcurrentHashMap<>();

    public FabricNetworkAdapter(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void broadcastPublicPayload(String channel, byte[] payload) {
        LOG.debug("broadcastPublicPayload(channel={}, {} bytes)", channel, payload.length);
        for (var entry : handlers.entrySet()) {
            for (var player : server.getPlayerList().getPlayers()) {
                entry.getValue().handle(
                        PlayerReference.from(player.getUUID()),
                        channel,
                        payload);
            }
        }
    }

    @Override
    public void sendStorytellerPayload(PlayerReference storyteller, String channel, byte[] payload) {
        LOG.debug("sendStorytellerPayload(to={}, channel={}, {} bytes)",
                storyteller.value(), channel, payload.length);
        var player = server.getPlayerList()
                .getPlayer(UUID.fromString(storyteller.value()));
        if (player != null) {
            for (var entry : handlers.entrySet()) {
                entry.getValue().handle(storyteller, channel, payload);
            }
        }
    }

    @Override
    public void registerReceiver(String channel, PayloadHandler handler) {
        LOG.debug("registerReceiver(channel={})", channel);
        handlers.put(channel, handler);
    }
}
