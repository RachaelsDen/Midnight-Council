package dev.kgoodwin.midnightcouncil.fabric;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.PlayerAndSeatManager;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;
import dev.kgoodwin.midnightcouncil.api.event.VoteResolved;
import dev.kgoodwin.midnightcouncil.api.event.ExecutionResolved;
import dev.kgoodwin.midnightcouncil.api.event.TimerExpired;
import dev.kgoodwin.midnightcouncil.api.game.GameStateCodec;
import dev.kgoodwin.midnightcouncil.api.game.TimerManager;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricConfigAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricLoggerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.command.MidnightCommandTree;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricPermissionAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricSchedulerAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricWorldAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MidnightCouncilMod implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(MidnightCouncilMod.class);
    private static final String CONFIG_FILE_NAME = "midnightcouncil.properties";
    private static final String VOICE_PORT_KEY = "voice.port";
    private static final String VOICE_DISTANCE_KEY = "voice.distance";
    private static final String VOICE_CONNECT_TOKEN_SECRET_KEY = "voice.connectTokenSecret";
    static final String STATE_CHANNEL = "midnightcouncil:state";
    private static final int VOICE_HANDOFF_RETRY_ATTEMPTS = 20;

    private FabricConfigAdapter configAdapter;
    private FabricWorldAdapter worldAdapter;
    private FabricNetworkAdapter networkAdapter;
    private FabricPermissionAdapter permissionAdapter;
    private FabricSchedulerAdapter schedulerAdapter;
    private FabricLoggerAdapter loggerAdapter;
    private FabricVoiceAdapter voiceAdapter;
    private final GameSession gameSession = new GameSession();
    private final VoteManager voteManager = new VoteManager(gameSession.getDispatcher());
    private final NominationManager nominationManager = new NominationManager(gameSession.getDispatcher());
    private final ExecutionManager executionManager = new ExecutionManager(gameSession.getDispatcher());
    private final PlayerAndSeatManager playerAndSeatManager = new PlayerAndSeatManager(gameSession.getDispatcher());
    private TimerManager timerManager;
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
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        ServerPlayerEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayerEvents.LEAVE.register(this::onPlayerLeave);
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
                MidnightCommandTree.register(dispatcher, gameSession));
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        GameEventDispatcher dispatcher = gameSession.getDispatcher();
        dispatcher.registerListener(PhaseChanged.class, event -> broadcastGameState());
        dispatcher.registerListener(PlayerStateChanged.class, event -> broadcastGameState());
        dispatcher.registerListener(NominationOpened.class, event -> broadcastGameState());
        dispatcher.registerListener(VoteResolved.class, event -> broadcastGameState());
        dispatcher.registerListener(ExecutionResolved.class, event -> broadcastGameState());
        dispatcher.registerListener(TimerExpired.class, event -> broadcastGameState());
    }

    void onServerStarted(MinecraftServer server) {
        ensureAdapters(server);
        FabricVoiceAdapter currentVoiceAdapter = voiceAdapter;
        if (currentVoiceAdapter != null) {
            currentVoiceAdapter.start();
        }
    }

    void onServerTick(MinecraftServer server) {
        ensureAdapters(server);
        FabricVoiceAdapter currentVoiceAdapter = voiceAdapter;
        if (currentVoiceAdapter != null) {
            currentVoiceAdapter.syncPlayerPositions();
        }
        schedulerAdapter.tick();
    }

    void onServerStopped(MinecraftServer server) {
        if (server == currentServer) {
            clearAdapters();
        }
    }

    void onServerStopping(MinecraftServer server) {
        if (server == currentServer) {
            stopVoiceAdapter();
        }
    }

    void onPlayerJoin(ServerPlayer player) {
        PlayerReference playerReference = PlayerReference.from(player.getUUID());
        queueVoiceConnectHandoff(playerReference, new Position(player.getX(), player.getY(), player.getZ()));
    }

    void onPlayerLeave(ServerPlayer player) {
        revokeVoiceConnectHandoff(PlayerReference.from(player.getUUID()));
    }

    void queueVoiceConnectHandoff(PlayerReference playerReference, Position position) {
        FabricVoiceAdapter currentVoiceAdapter = voiceAdapter;
        if (currentVoiceAdapter == null || !currentVoiceAdapter.isVoiceRunning()) {
            return;
        }
        currentVoiceAdapter.seedPlayerPosition(playerReference, position);
        sendVoiceConnectHandoff(playerReference, currentVoiceAdapter.createConnectHandoff(playerReference), VOICE_HANDOFF_RETRY_ATTEMPTS);
    }

    void revokeVoiceConnectHandoff(PlayerReference playerReference) {
        FabricVoiceAdapter currentVoiceAdapter = voiceAdapter;
        if (currentVoiceAdapter != null) {
            currentVoiceAdapter.revokePendingConnectToken(playerReference);
            currentVoiceAdapter.disconnectPlayer(playerReference);
        }
    }

    private void sendVoiceConnectHandoff(PlayerReference playerReference, byte[] handoffPayload, int attemptsRemaining) {
        FabricNetworkAdapter currentNetworkAdapter = networkAdapter;
        FabricVoiceAdapter currentVoiceAdapter = voiceAdapter;
        FabricSchedulerAdapter currentSchedulerAdapter = schedulerAdapter;
        if (currentNetworkAdapter == null || currentVoiceAdapter == null || !currentVoiceAdapter.isVoiceRunning()) {
            return;
        }
        if (!currentVoiceAdapter.isCurrentPendingConnectHandoff(playerReference, handoffPayload)) {
            return;
        }
        if (currentNetworkAdapter.sendPayloadIfSupported(playerReference, FabricVoiceAdapter.VOICE_CONNECT_CHANNEL, handoffPayload)) {
            return;
        }
        if (attemptsRemaining > 0 && currentSchedulerAdapter != null) {
            currentSchedulerAdapter.runNextTick(() -> sendVoiceConnectHandoff(playerReference, handoffPayload, attemptsRemaining - 1));
        }
    }

    private void ensureAdapters(MinecraftServer server) {
        if (server != currentServer || schedulerAdapter == null) {
            stopVoiceAdapter();
            currentServer = server;
            schedulerAdapter = new FabricSchedulerAdapter(server);
            wireAdapters(server);
        }
    }

    private void wireAdapters(MinecraftServer server) {
        configAdapter = new FabricConfigAdapter(configDirectory(), CONFIG_FILE_NAME);
        configAdapter.load();

        VoiceSettings voiceSettings = resolveVoiceSettings();
        worldAdapter = new FabricWorldAdapter(server);
        networkAdapter = new FabricNetworkAdapter(server);
        permissionAdapter = new FabricPermissionAdapter(server);
        loggerAdapter = new FabricLoggerAdapter(MidnightCouncilMod.class);
        voiceAdapter = new FabricVoiceAdapter(
                voiceSettings.port(),
                voiceSettings.distance(),
                voiceSettings.connectTokenSecret(),
                gameSession::getState);
        voiceAdapter.bindWorldAdapter(worldAdapter);

        timerManager = new TimerManager(schedulerAdapter, configAdapter, gameSession.getDispatcher());

        LOG.info("Midnight Council adapters wired");
    }

    void broadcastGameState() {
        FabricNetworkAdapter adapter = networkAdapter;
        if (adapter == null) {
            return;
        }
        byte[] encoded = GameStateCodec.encode(gameSession.getState());
        adapter.broadcastPublicPayload(STATE_CHANNEL, encoded);
    }

    private VoiceSettings resolveVoiceSettings() {
        int port = resolveConfigValue(VOICE_PORT_KEY, Integer.class, FabricVoiceAdapter.DEFAULT_VOICE_PORT);
        double distance = resolveConfigValue(VOICE_DISTANCE_KEY, Double.class, FabricVoiceAdapter.DEFAULT_VOICE_DISTANCE);
        String connectTokenSecret = configAdapter.get(VOICE_CONNECT_TOKEN_SECRET_KEY, String.class)
                .filter(secret -> !secret.isBlank())
                .orElseGet(() -> {
                    String generated = UUID.randomUUID().toString();
                    configAdapter.set(VOICE_CONNECT_TOKEN_SECRET_KEY, generated);
                    configAdapter.save();
                    return generated;
                });
        return new VoiceSettings(port, distance, connectTokenSecret);
    }

    private <T> T resolveConfigValue(String key, Class<T> valueType, T defaultValue) {
        return configAdapter.get(key, valueType).orElseGet(() -> {
            configAdapter.set(key, defaultValue);
            configAdapter.save();
            return defaultValue;
        });
    }

    private void stopVoiceAdapter() {
        FabricVoiceAdapter currentVoiceAdapter = voiceAdapter;
        if (currentVoiceAdapter != null) {
            currentVoiceAdapter.stop();
            voiceAdapter = null;
        }
    }

    private void clearAdapters() {
        currentServer = null;
        configAdapter = null;
        worldAdapter = null;
        networkAdapter = null;
        permissionAdapter = null;
        schedulerAdapter = null;
        loggerAdapter = null;
        voiceAdapter = null;
    }

    void setConfigDirOverride(Path configDirOverride) {
        this.configDirOverride = configDirOverride;
    }

    private Path configDirectory() {
        return configDirOverride != null ? configDirOverride : FabricLoader.getInstance().getConfigDir();
    }

    static void dispatchServerboundPayload(FabricNetworkAdapter networkAdapter, UUID playerUuid, MidnightCouncilPayload payload) {
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

    FabricVoiceAdapter voiceAdapter() {
        return voiceAdapter;
    }

    public GameSession gameSession() {
        return gameSession;
    }

    VoteManager voteManager() {
        return voteManager;
    }

    NominationManager nominationManager() {
        return nominationManager;
    }

    ExecutionManager executionManager() {
        return executionManager;
    }

    PlayerAndSeatManager playerAndSeatManager() {
        return playerAndSeatManager;
    }

    TimerManager timerManager() {
        return timerManager;
    }

    private record VoiceSettings(int port, double distance, String connectTokenSecret) {
    }
}
