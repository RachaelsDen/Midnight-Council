package dev.kgoodwin.midnightcouncil.fabric;

import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricConfigAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricLoggerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricPermissionAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricSchedulerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricWorldAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MidnightCouncilMod implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(MidnightCouncilMod.class);

    private FabricSchedulerAdapter schedulerAdapter;

    @Override
    public void onInitialize() {
        LOG.info("Midnight Council initializing");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (schedulerAdapter == null) {
                schedulerAdapter = new FabricSchedulerAdapter(server);
                wireAdapters(server);
            }
            schedulerAdapter.tick();
        });
    }

    private void wireAdapters(net.minecraft.server.MinecraftServer server) {
        var configAdapter = new FabricConfigAdapter(
                FabricLoader.getInstance().getConfigDir(),
                "midnightcouncil.properties");
        configAdapter.load();

        var worldAdapter = new FabricWorldAdapter(server);
        var networkAdapter = new FabricNetworkAdapter(server);
        var permissionAdapter = new FabricPermissionAdapter(server);
        var loggerAdapter = new FabricLoggerAdapter(MidnightCouncilMod.class);

        LOG.info("Midnight Council adapters wired");
    }
}
