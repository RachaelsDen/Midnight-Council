package dev.kgoodwin.midnightcouncil.fabric;

import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricConfigAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricLoggerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricPermissionAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricSchedulerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricWorldAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MidnightCouncilMod implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(MidnightCouncilMod.class);

    private FabricConfigAdapter configAdapter;
    private FabricWorldAdapter worldAdapter;
    private FabricNetworkAdapter networkAdapter;
    private FabricPermissionAdapter permissionAdapter;
    private FabricSchedulerAdapter schedulerAdapter;
    private FabricLoggerAdapter loggerAdapter;

    @Override
    public void onInitialize() {
        LOG.info("Midnight Council initializing");
        PayloadTypeRegistry.clientboundPlay().register(MidnightCouncilPayload.TYPE, MidnightCouncilPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (schedulerAdapter == null) {
                schedulerAdapter = new FabricSchedulerAdapter(server);
                wireAdapters(server);
            }
            schedulerAdapter.tick();
        });
    }

    private void wireAdapters(net.minecraft.server.MinecraftServer server) {
        configAdapter = new FabricConfigAdapter(
                FabricLoader.getInstance().getConfigDir(),
                "midnightcouncil.properties");
        configAdapter.load();

        worldAdapter = new FabricWorldAdapter(server);
        networkAdapter = new FabricNetworkAdapter(server);
        permissionAdapter = new FabricPermissionAdapter(server);
        loggerAdapter = new FabricLoggerAdapter(MidnightCouncilMod.class);

        LOG.info("Midnight Council adapters wired");
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
