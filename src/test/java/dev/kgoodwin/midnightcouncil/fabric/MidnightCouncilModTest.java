package dev.kgoodwin.midnightcouncil.fabric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;
import dev.kgoodwin.midnightcouncil.api.event.VoteResolved;
import dev.kgoodwin.midnightcouncil.api.event.ExecutionResolved;
import dev.kgoodwin.midnightcouncil.api.event.TimerExpired;
import dev.kgoodwin.midnightcouncil.api.game.GameStateCodec;
import dev.kgoodwin.midnightcouncil.api.game.GameStateSnapshot;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MidnightCouncilModTest {

    @TempDir
    Path tempDir;

    private MidnightCouncilMod mod;
    private FabricNetworkAdapter networkAdapter;

    @BeforeEach
    void setUp() {
        mod = new MidnightCouncilMod();
        mod.setConfigDirOverride(tempDir);
        MinecraftServer server = mock(MinecraftServer.class);
        networkAdapter = new FabricNetworkAdapter(server);
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dispatchServerboundPayloadRoutesUuidAndPayloadToRegisteredHandler() {
        UUID playerUuid = UUID.randomUUID();
        byte[] bytes = new byte[] {4, 2};
        MidnightCouncilPayload payload = new MidnightCouncilPayload("vote", bytes);
        AtomicReference<PlayerReference> capturedPlayer = new AtomicReference<>();

        networkAdapter.registerReceiver("vote", (playerReference, channel, inboundBytes) -> {
            capturedPlayer.set(playerReference);
            assertEquals("vote", channel);
            assertArrayEquals(bytes, inboundBytes);
        });

        MidnightCouncilMod.dispatchServerboundPayload(networkAdapter, playerUuid, payload);

        assertNotNull(capturedPlayer.get());
        assertEquals(PlayerReference.from(playerUuid), capturedPlayer.get());
    }

    @Test
    void rewiresAdaptersWhenNewServerInstanceAppears() {
        MinecraftServer serverA = mock(MinecraftServer.class);
        MinecraftServer serverB = mock(MinecraftServer.class);

        mod.onServerTick(serverA);
        Object configA = mod.configAdapter();
        Object worldA = mod.worldAdapter();
        Object networkA = mod.networkAdapter();
        Object permissionA = mod.permissionAdapter();
        Object schedulerA = mod.schedulerAdapter();
        Object loggerA = mod.loggerAdapter();

        mod.onServerTick(serverB);

        assertNotSame(configA, mod.configAdapter());
        assertNotSame(worldA, mod.worldAdapter());
        assertNotSame(networkA, mod.networkAdapter());
        assertNotSame(permissionA, mod.permissionAdapter());
        assertNotSame(schedulerA, mod.schedulerAdapter());
        assertNotSame(loggerA, mod.loggerAdapter());
    }

    @Test
    void doesNotRewireForSameServerInstance() {
        MinecraftServer server = mock(MinecraftServer.class);

        mod.onServerTick(server);
        Object config = mod.configAdapter();
        Object world = mod.worldAdapter();
        Object network = mod.networkAdapter();
        Object permission = mod.permissionAdapter();
        Object scheduler = mod.schedulerAdapter();
        Object logger = mod.loggerAdapter();

        mod.onServerTick(server);

        assertSame(config, mod.configAdapter());
        assertSame(world, mod.worldAdapter());
        assertSame(network, mod.networkAdapter());
        assertSame(permission, mod.permissionAdapter());
        assertSame(scheduler, mod.schedulerAdapter());
        assertSame(logger, mod.loggerAdapter());
    }

    @Test
    void schedulerTasksDoNotLeakAcrossServerRewire() {
        MinecraftServer serverA = mock(MinecraftServer.class);
        MinecraftServer serverB = mock(MinecraftServer.class);
        AtomicInteger runs = new AtomicInteger();

        mod.onServerTick(serverA);
        mod.schedulerAdapter().runNextTick(runs::incrementAndGet);

        mod.onServerTick(serverB);
        assertEquals(0, runs.get());

        mod.schedulerAdapter().runNextTick(runs::incrementAndGet);
        mod.schedulerAdapter().tick();
        assertEquals(1, runs.get());
    }

    @Test
    void clearsAdaptersWhenActiveServerStops() {
        MinecraftServer server = mock(MinecraftServer.class);

        mod.onServerTick(server);
        mod.onServerStopped(server);

        assertNull(mod.configAdapter());
        assertNull(mod.worldAdapter());
        assertNull(mod.networkAdapter());
        assertNull(mod.permissionAdapter());
        assertNull(mod.schedulerAdapter());
        assertNull(mod.loggerAdapter());
        assertNull(mod.voiceAdapter());
        assertNotNull(mod.gameSession());
    }

    @Test
    void serverStartedInitializesAndStartsVoiceAdapter() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        try {
            mod.onServerStarted(server);

            assertNotNull(mod.voiceAdapter());
            assertNotNull(mod.gameSession());
            assertTrue(mod.voiceAdapter().isVoiceRunning());
            PlayerReference playerReference = PlayerReference.from(UUID.randomUUID());
            FabricVoiceAdapter.VoiceConnectHandoff handoff = FabricVoiceAdapter.decodeConnectHandoff(
                    mod.voiceAdapter().createConnectHandoff(playerReference));
            assertTrue(handoff.port() > 0);
            assertEquals(playerReference.value(), handoff.playerId());
            assertEquals(Long.BYTES + 32, handoff.token().length);
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }

        assertNull(mod.voiceAdapter());
        assertNotNull(mod.gameSession());
    }

    @Test
    void serverStoppingStopsVoiceBeforeStoppedClearsAdapters() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        try {
            mod.onServerStarted(server);
            assertNotNull(mod.voiceAdapter());
            assertTrue(mod.voiceAdapter().isVoiceRunning());

            mod.onServerStopping(server);

            assertNull(mod.voiceAdapter());
            assertNotNull(mod.configAdapter());
            assertNotNull(mod.worldAdapter());
            assertNotNull(mod.networkAdapter());
            assertNotNull(mod.schedulerAdapter());
            assertNotNull(mod.gameSession());
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }

        assertNull(mod.configAdapter());
        assertNull(mod.worldAdapter());
        assertNull(mod.networkAdapter());
        assertNull(mod.schedulerAdapter());
        assertNotNull(mod.gameSession());
    }

    @Test
    void voiceAdapterCanRevokePendingVoiceToken() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        UUID playerUuid = UUID.randomUUID();
        PlayerReference playerReference = PlayerReference.from(playerUuid);

        try {
            mod.onServerStarted(server);
            mod.voiceAdapter().createConnectHandoff(playerReference);

            assertTrue(mod.voiceAdapter().revokePendingConnectToken(playerReference));
            assertFalse(mod.voiceAdapter().revokePendingConnectToken(playerReference));
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    @Test
    void staleQueuedHandoffRetryDoesNotSendAfterLeaveAndRejoin() throws Exception {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerReference playerReference = PlayerReference.from(UUID.randomUUID());
        AtomicBoolean canSend = new AtomicBoolean(false);
        List<MidnightCouncilPayload> sentPayloads = new ArrayList<>();

        try {
            mod.onServerStarted(server);
            setPrivateField(mod, "networkAdapter", createTestNetworkAdapter(server, sentPayloads, canSend));

            mod.queueVoiceConnectHandoff(playerReference, new dev.kgoodwin.midnightcouncil.api.Position(10.0, 64.0, -5.0));
            assertTrue(sentPayloads.isEmpty());

            mod.revokeVoiceConnectHandoff(playerReference);
            canSend.set(true);

            mod.queueVoiceConnectHandoff(playerReference, new dev.kgoodwin.midnightcouncil.api.Position(11.0, 65.0, -4.0));
            mod.schedulerAdapter().tick();

            assertEquals(1, sentPayloads.size());
            assertTrue(mod.voiceAdapter().isCurrentPendingConnectHandoff(playerReference, sentPayloads.get(0).bytes()));
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    @Test
    void voiceAdapterReadsFromSharedGameSession() throws Exception {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);

        try {
            mod.onServerStarted(server);
            GameState sharedState = mod.gameSession().getState();
            Field voiceServerField = FabricVoiceAdapter.class.getDeclaredField("voiceServer");
            voiceServerField.setAccessible(true);
            Object voiceServer = voiceServerField.get(mod.voiceAdapter());
            Field supplierField = voiceServer.getClass().getDeclaredField("gameStateSupplier");
            supplierField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Supplier<GameState> supplier = (Supplier<GameState>) supplierField.get(voiceServer);

            assertSame(sharedState, supplier.get());
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    @Test
    void gameSessionSurvivesServerRestart() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer serverA = mock(MinecraftServer.class);
        MinecraftServer serverB = mock(MinecraftServer.class);

        mod.onServerStarted(serverA);
        GameSession session = mod.gameSession();
        mod.onServerStopping(serverA);
        mod.onServerStopped(serverA);

        mod.onServerStarted(serverB);
        try {
            assertSame(session, mod.gameSession());
        } finally {
            mod.onServerStopping(serverB);
            mod.onServerStopped(serverB);
        }
    }

    @Test
    void managersWiredAfterServerStart() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);

        try {
            mod.onServerStarted(server);

            assertNotNull(mod.voteManager());
            assertNotNull(mod.nominationManager());
            assertNotNull(mod.executionManager());
            assertNotNull(mod.playerAndSeatManager());
            assertNotNull(mod.timerManager());
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    @Test
    void managersSurviveServerRestartAndTimerManagerIsRecreated() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer serverA = mock(MinecraftServer.class);
        MinecraftServer serverB = mock(MinecraftServer.class);

        mod.onServerStarted(serverA);
        Object voteManager = mod.voteManager();
        Object nominationManager = mod.nominationManager();
        Object executionManager = mod.executionManager();
        Object playerAndSeatManager = mod.playerAndSeatManager();
        Object timerManager = mod.timerManager();

        mod.onServerStopping(serverA);
        mod.onServerStopped(serverA);

        mod.onServerStarted(serverB);
        try {
            assertSame(voteManager, mod.voteManager());
            assertSame(nominationManager, mod.nominationManager());
            assertSame(executionManager, mod.executionManager());
            assertSame(playerAndSeatManager, mod.playerAndSeatManager());
            assertNotSame(timerManager, mod.timerManager());
        } finally {
            mod.onServerStopping(serverB);
            mod.onServerStopped(serverB);
        }
    }

    @Test
    void serverRestartStopsActiveTimerBeforeRewiringAdapters() throws IOException {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer serverA = mock(MinecraftServer.class);
        MinecraftServer serverB = mock(MinecraftServer.class);

        mod.onServerStarted(serverA);
        mod.timerManager().startDiscussionTimer(mod.gameSession().getState());
        assertTrue(mod.gameSession().getState().isTimerActive());

        mod.onServerStopping(serverA);
        mod.onServerStopped(serverA);

        mod.onServerStarted(serverB);
        try {
            assertFalse(mod.gameSession().getState().isTimerActive());
        } finally {
            mod.onServerStopping(serverB);
            mod.onServerStopped(serverB);
        }
    }

    @Test
    void gameEventBroadcastsStateToAllClients() throws Exception {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        List<MidnightCouncilPayload> sentPayloads = new ArrayList<>();

        try {
            mod.onServerStarted(server);
            setPrivateField(mod, "networkAdapter", createStateBroadcastCapturingAdapter(sentPayloads));
            registerStateBroadcastListeners(mod);

            // Dispatch a PhaseChanged event
            mod.gameSession().getDispatcher().dispatch(
                    new PhaseChanged(
                            GamePhase.IDLE,
                            GamePhase.SETUP));

            assertFalse(sentPayloads.isEmpty(), "State broadcast should have been sent");
            // Verify the payload is on the state channel
            assertTrue(sentPayloads.stream().anyMatch(p -> p.channel().equals("midnightcouncil:state")));
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    @Test
    void broadcastGameStateSkipsWhenAdapterNotWired() {
        // Create a fresh mod that hasn't had onServerStarted called
        MidnightCouncilMod freshMod = new MidnightCouncilMod();
        // Should not throw
        freshMod.broadcastGameState();
    }

    @Test
    void multipleEventTypesTriggerStateBroadcast() throws Exception {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        List<MidnightCouncilPayload> sentPayloads = new ArrayList<>();

        try {
            mod.onServerStarted(server);
            setPrivateField(mod, "networkAdapter", createStateBroadcastCapturingAdapter(sentPayloads));
            registerStateBroadcastListeners(mod);
            sentPayloads.clear(); // Clear voice handoff payloads

            var dispatcher = mod.gameSession().getDispatcher();

            // Dispatch several different event types
            dispatcher.dispatch(new PlayerStateChanged(
                    PlayerReference.from(UUID.randomUUID()), "join"));
            dispatcher.dispatch(new NominationOpened(
                    PlayerReference.from(UUID.randomUUID()),
                    PlayerReference.from(UUID.randomUUID())));

            // Each event should produce one broadcast
            long stateBroadcasts = sentPayloads.stream()
                    .filter(p -> p.channel().equals("midnightcouncil:state"))
                    .count();
            assertEquals(2, stateBroadcasts);
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    @Test
    void onPlayerJoinSendsCurrentStateToJoiningPlayer() throws Exception {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        List<MidnightCouncilPayload> sentPayloads = new ArrayList<>();

        try {
            mod.onServerStarted(server);
            setPrivateField(mod, "networkAdapter", createTestNetworkAdapter(server, sentPayloads, new AtomicBoolean(true)));
            sentPayloads.clear(); // Clear voice startup payloads

            UUID playerUuid = UUID.randomUUID();
            ServerPlayer mockPlayer = mock(ServerPlayer.class);
            org.mockito.Mockito.when(mockPlayer.getUUID()).thenReturn(playerUuid);
            org.mockito.Mockito.when(mockPlayer.getX()).thenReturn(0.0);
            org.mockito.Mockito.when(mockPlayer.getY()).thenReturn(64.0);
            org.mockito.Mockito.when(mockPlayer.getZ()).thenReturn(0.0);

            mod.onPlayerJoin(mockPlayer);

            mod.schedulerAdapter().tick();

            assertTrue(sentPayloads.stream().anyMatch(p -> p.channel().equals(MidnightCouncilMod.STATE_CHANNEL)),
                    "Joining player should receive current game state");
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    @Test
    void stateBroadcastContainsValidEncodedState() throws Exception {
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0",
                "voice.distance=24.0",
                "voice.connectTokenSecret=test-secret"));
        MinecraftServer server = mock(MinecraftServer.class);
        List<MidnightCouncilPayload> sentPayloads = new ArrayList<>();

        try {
            mod.onServerStarted(server);
            setPrivateField(mod, "networkAdapter", createStateBroadcastCapturingAdapter(sentPayloads));
            registerStateBroadcastListeners(mod);

            // Set a known phase on the game state
            mod.gameSession().getState().setPhase(GamePhase.SETUP);

            // Trigger a broadcast
            mod.gameSession().getDispatcher().dispatch(
                    new PhaseChanged(
                            GamePhase.IDLE,
                            GamePhase.SETUP));

            // Find the state payload and decode it
            MidnightCouncilPayload statePayload = sentPayloads.stream()
                    .filter(p -> p.channel().equals("midnightcouncil:state"))
                    .findFirst()
                    .orElseThrow();
            GameStateSnapshot decoded = GameStateCodec.decode(statePayload.bytes());
            assertEquals(GamePhase.SETUP, decoded.phase());
        } finally {
            mod.onServerStopping(server);
            mod.onServerStopped(server);
        }
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static FabricNetworkAdapter createStateBroadcastCapturingAdapter(List<MidnightCouncilPayload> sentPayloads) {
        FabricNetworkAdapter adapter = mock(FabricNetworkAdapter.class);
        doAnswer(invocation -> {
            sentPayloads.add(new MidnightCouncilPayload(invocation.getArgument(0), invocation.getArgument(1)));
            return null;
        }).when(adapter).broadcastPublicPayload(anyString(), any());
        return adapter;
    }

    private static void registerStateBroadcastListeners(MidnightCouncilMod mod) {
        var dispatcher = mod.gameSession().getDispatcher();
        dispatcher.registerListener(PhaseChanged.class, event -> mod.broadcastGameState());
        dispatcher.registerListener(PlayerStateChanged.class, event -> mod.broadcastGameState());
        dispatcher.registerListener(NominationOpened.class, event -> mod.broadcastGameState());
        dispatcher.registerListener(VoteResolved.class, event -> mod.broadcastGameState());
        dispatcher.registerListener(ExecutionResolved.class, event -> mod.broadcastGameState());
        dispatcher.registerListener(TimerExpired.class, event -> mod.broadcastGameState());
    }

    private static FabricNetworkAdapter createTestNetworkAdapter(
            MinecraftServer server,
            List<MidnightCouncilPayload> sentPayloads,
            AtomicBoolean canSend) throws ReflectiveOperationException {
        Class<?> packetSenderType = Class.forName("dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter$PacketSender");
        Class<?> availabilityType = Class.forName("dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter$PlayerAvailabilityChecker");
        Class<?> channelSupportType = Class.forName("dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter$ClientChannelSupportChecker");

        Object packetSender = Proxy.newProxyInstance(
                packetSenderType.getClassLoader(),
                new Class<?>[] {packetSenderType},
                (proxy, method, args) -> {
                    sentPayloads.add((MidnightCouncilPayload) args[1]);
                    return null;
                });
        Object availabilityChecker = Proxy.newProxyInstance(
                availabilityType.getClassLoader(),
                new Class<?>[] {availabilityType},
                (proxy, method, args) -> true);
        Object channelSupportChecker = Proxy.newProxyInstance(
                channelSupportType.getClassLoader(),
                new Class<?>[] {channelSupportType},
                (proxy, method, args) -> canSend.get());

        Constructor<FabricNetworkAdapter> constructor = FabricNetworkAdapter.class.getDeclaredConstructor(
                MinecraftServer.class,
                packetSenderType,
                availabilityType,
                channelSupportType);
        constructor.setAccessible(true);
        return constructor.newInstance(server, packetSender, availabilityChecker, channelSupportChecker);
    }
}
