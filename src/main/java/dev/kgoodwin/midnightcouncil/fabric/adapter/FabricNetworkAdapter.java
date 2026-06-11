package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.NetworkAdapter;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import java.util.List;
import java.util.Map;
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
        List<PlayerReference> recipients = server.getPlayerList().getPlayers().stream()
                .map(player -> PlayerReference.from(player.getUUID()))
                .toList();
        dispatchToChannel(recipients, channel, payload);
    }

    @Override
    public void sendStorytellerPayload(PlayerReference storyteller, String channel, byte[] payload) {
        LOG.debug("sendStorytellerPayload(to={}, channel={}, {} bytes)",
                storyteller.value(), channel, payload.length);
        var player = FabricPlayerResolver.resolve(server, storyteller);
        if (player == null) {
            LOG.warn("sendStorytellerPayload: player {} not online", storyteller.value());
            return;
        }

        PayloadHandler handler = handlers.get(channel);
        if (handler == null) {
            LOG.warn("sendStorytellerPayload: no handler registered for channel={}", channel);
            return;
        }
        handler.handle(storyteller, channel, payload);
    }

    void dispatchToChannel(Iterable<PlayerReference> recipients, String channel, byte[] payload) {
        PayloadHandler handler = handlers.get(channel);
        if (handler == null) {
            LOG.warn("dispatchToChannel: no handler registered for channel={}", channel);
            return;
        }
        for (PlayerReference recipient : recipients) {
            handler.handle(recipient, channel, payload);
        }
    }

    @Override
    public void registerReceiver(String channel, PayloadHandler handler) {
        LOG.debug("registerReceiver(channel={})", channel);
        handlers.put(channel, handler);
    }
}
