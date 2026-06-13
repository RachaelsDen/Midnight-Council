package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.NetworkAdapter;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricNetworkAdapter implements NetworkAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricNetworkAdapter.class);

    private final MinecraftServer server;
    private final Map<String, PayloadHandler> inboundHandlers = new ConcurrentHashMap<>();
    private final PacketSender packetSender;
    private final PlayerAvailabilityChecker playerAvailabilityChecker;
    private final ClientChannelSupportChecker clientChannelSupportChecker;

    public FabricNetworkAdapter(MinecraftServer server) {
        this(
                server,
                (recipient, payload) -> {
                    var player = FabricPlayerResolver.resolve(server, recipient);
                    if (player == null) {
                        LOG.warn("sendOutboundPayload: player {} not online for channel={}", recipient.value(), payload.channel());
                        return;
                    }
                    ServerPlayNetworking.send(player, payload);
                },
                recipient -> FabricPlayerResolver.resolve(server, recipient) != null,
                recipient -> {
                    var player = FabricPlayerResolver.resolve(server, recipient);
                    return player != null && ServerPlayNetworking.canSend(player, MidnightCouncilPayload.TYPE);
                });
    }

    FabricNetworkAdapter(
            MinecraftServer server,
            PacketSender packetSender,
            PlayerAvailabilityChecker playerAvailabilityChecker,
            ClientChannelSupportChecker clientChannelSupportChecker) {
        this.server = server;
        this.packetSender = packetSender;
        this.playerAvailabilityChecker = playerAvailabilityChecker;
        this.clientChannelSupportChecker = clientChannelSupportChecker;
    }

    @Override
    public void broadcastPublicPayload(String channel, byte[] payload) {
        LOG.debug("broadcastPublicPayload(channel={}, {} bytes)", channel, payload.length);
        List<PlayerReference> recipients = server.getPlayerList().getPlayers().stream()
                .map(player -> PlayerReference.from(player.getUUID()))
                .toList();
        sendOutboundPayload(recipients, channel, payload);
    }

    @Override
    public void sendStorytellerPayload(PlayerReference storyteller, String channel, byte[] payload) {
        LOG.debug("sendStorytellerPayload(to={}, channel={}, {} bytes)",
                storyteller.value(), channel, payload.length);
        sendPayloadIfSupported(storyteller, channel, payload);
    }

    public boolean sendPayloadIfSupported(PlayerReference recipient, String channel, byte[] payload) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payload, "payload");
        if (!playerAvailabilityChecker.isOnline(recipient)) {
            LOG.warn("sendPayloadIfSupported: player {} not online", recipient.value());
            return false;
        }
        if (!clientChannelSupportChecker.canSend(recipient)) {
            LOG.debug("sendPayloadIfSupported: player {} does not support channel={} payload type", recipient.value(), channel);
            return false;
        }
        packetSender.send(recipient, new MidnightCouncilPayload(channel, payload));
        return true;
    }

    void sendOutboundPayload(Iterable<PlayerReference> recipients, String channel, byte[] payload) {
        for (PlayerReference recipient : recipients) {
            sendPayloadIfSupported(recipient, channel, payload);
        }
    }

    public void dispatchInboundPayload(PlayerReference playerReference, String channel, byte[] payload) {
        PayloadHandler handler = inboundHandlers.get(channel);
        if (handler == null) {
            LOG.warn("dispatchInboundPayload: no handler registered for channel={}", channel);
            return;
        }
        handler.handle(playerReference, channel, payload);
    }

    @Override
    public void registerReceiver(String channel, PayloadHandler handler) {
        LOG.debug("registerReceiver(channel={})", channel);
        inboundHandlers.put(channel, handler);
    }

    @FunctionalInterface
    interface PacketSender {
        void send(PlayerReference recipient, MidnightCouncilPayload payload);
    }

    @FunctionalInterface
    interface PlayerAvailabilityChecker {
        boolean isOnline(PlayerReference recipient);
    }

    @FunctionalInterface
    interface ClientChannelSupportChecker {
        boolean canSend(PlayerReference recipient);
    }
}
