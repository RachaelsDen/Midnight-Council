package dev.kgoodwin.midnightcouncil.fabric;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricConfigAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricLoggerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricPermissionAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricSchedulerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricWorldAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class MidnightCouncilMod implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(MidnightCouncilMod.class);

    private FabricConfigAdapter configAdapter;
    private FabricWorldAdapter worldAdapter;
    private FabricNetworkAdapter networkAdapter;
    private FabricPermissionAdapter permissionAdapter;
    private FabricSchedulerAdapter schedulerAdapter;
    private FabricLoggerAdapter loggerAdapter;
    private MinecraftServer currentServer;
    private Path configDirOverride;

    @Override
    public void onInitialize() {
        LOG.info("Midnight Council initializing");
        PayloadTypeRegistry.clientboundPlay().register(MidnightCouncilPayload.TYPE, MidnightCouncilPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MidnightCouncilPayload.TYPE, MidnightCouncilPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MidnightCouncilPayload.TYPE, (payload, context) -> {
            FabricNetworkAdapter currentNetworkAdapter = networkAdapter;
            if (currentNetworkAdapter == null) {
                LOG.debug("Skipping serverbound payload dispatch before adapters are wired");
                return;
            }
            dispatchServerboundPayload(currentNetworkAdapter, context.player().getUUID(), payload);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    void onServerTick(MinecraftServer server) {
        if (server != currentServer || schedulerAdapter == null) {
            currentServer = server;
            schedulerAdapter = new FabricSchedulerAdapter(server);
            wireAdapters(server);
        }
        schedulerAdapter.tick();
    }

    void onServerStopped(MinecraftServer server) {
        if (server == currentServer) {
            clearAdapters();
        }
    }

    private void wireAdapters(MinecraftServer server) {
        configAdapter = new FabricConfigAdapter(
                configDirectory(),
                "midnightcouncil.properties");
        configAdapter.load();

        worldAdapter = new FabricWorldAdapter(server);
        networkAdapter = new FabricNetworkAdapter(server);
        permissionAdapter = new FabricPermissionAdapter(server);
        loggerAdapter = new FabricLoggerAdapter(MidnightCouncilMod.class);

        LOG.info("Midnight Council adapters wired");
    }

    private void clearAdapters() {
        currentServer = null;
        configAdapter = null;
        worldAdapter = null;
        networkAdapter = null;
        permissionAdapter = null;
        schedulerAdapter = null;
        loggerAdapter = null;
    }

    void setConfigDirOverride(Path configDirOverride) {
        this.configDirOverride = configDirOverride;
    }

    private Path configDirectory() {
        return configDirOverride != null ? configDirOverride : FabricLoader.getInstance().getConfigDir();
    }

    static void dispatchServerboundPayload(FabricNetworkAdapter networkAdapter, java.util.UUID playerUuid, MidnightCouncilPayload payload) {
        networkAdapter.dispatchInboundPayload(PlayerReference.from(playerUuid), payload.channel(), payload.bytes());
    }

    FabricConfigAdapter configAdapter() {
        return configAdapter;
    }

    FabricWorldAdapter worldAdapter() {
        return worldAdapter;
    }

    FabricNetworkAdapter networkAdapter() {
        return networkAdapter;
    }

    FabricPermissionAdapter permissionAdapter() {
        return permissionAdapter;
    }

    FabricSchedulerAdapter schedulerAdapter() {
        return schedulerAdapter;
    }

    FabricLoggerAdapter loggerAdapter() {
        return loggerAdapter;
    }
}
