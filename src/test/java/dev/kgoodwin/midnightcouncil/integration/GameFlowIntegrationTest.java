package dev.kgoodwin.midnightcouncil.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.ExecutionResolved;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;
import dev.kgoodwin.midnightcouncil.api.event.TimerExpired;
import dev.kgoodwin.midnightcouncil.api.event.VoteResolved;
import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.GameStateCodec;
import dev.kgoodwin.midnightcouncil.api.game.GameStateSnapshot;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.TimerManager;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;
import dev.kgoodwin.midnightcouncil.fabric.MidnightCouncilMod;
import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricNetworkAdapter;
import dev.kgoodwin.midnightcouncil.fabric.networking.MidnightCouncilPayload;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameFlowIntegrationTest {

    private static final String STATE_CHANNEL = "midnightcouncil:state";

    @TempDir
    Path tempDir;

    private MidnightCouncilMod mod;
    private MinecraftServer server;
    private List<MidnightCouncilPayload> sentPayloads;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() throws Exception {
        sentPayloads = new ArrayList<>();
        mod = new MidnightCouncilMod();
        setPrivateField(mod, "configDirOverride", tempDir);
        Files.writeString(tempDir.resolve("midnightcouncil.properties"), String.join(System.lineSeparator(),
                "voice.port=0", "voice.distance=24.0", "voice.connectTokenSecret=test-secret"));
        server = mock(MinecraftServer.class);
        invokeVoid(mod, "onServerStarted", MinecraftServer.class, server);
        setPrivateField(mod, "networkAdapter", createStateBroadcastCapturingAdapter(sentPayloads));
        registerStateBroadcastListeners(mod);
        sentPayloads.clear();
    }

    @AfterEach
    void tearDown() {
        invokeVoid(mod, "onServerStopping", MinecraftServer.class, server);
        invokeVoid(mod, "onServerStopped", MinecraftServer.class, server);
    }

    @Test
    void fullGameLifecycleProducesBroadcastAtEachPhaseTransition() {
        GameSession gameSession = mod.gameSession();

        gameSession.startSetup();
        for (int seat = 1; seat <= 6; seat++) {
            gameSession.addPlayer(PlayerReference.ofName("player" + seat), "Player " + seat, seat);
        }
        gameSession.startSeating();
        gameSession.startGame();
        gameSession.transitionPhase(GamePhase.NOMINATION);
        gameSession.transitionPhase(GamePhase.VOTING);
        gameSession.transitionPhase(GamePhase.EXECUTION);
        gameSession.transitionPhase(GamePhase.NIGHT);
        gameSession.transitionPhase(GamePhase.DAY);

        long stateBroadcasts = countStateBroadcasts();
        assertTrue(stateBroadcasts >= 8,
                "Each of the 8 phase transitions should produce a state broadcast; got " + stateBroadcasts);

        GameStateSnapshot decoded = decodeLastStateBroadcast();
        assertEquals(GamePhase.DAY, decoded.phase());
        assertEquals(2, decoded.dayCount());
        assertEquals(1, decoded.nightCount());
    }

    @Test
    void nominationAndVotingFlowProducesEventsAndBroadcasts() {
        GameSession gameSession = mod.gameSession();
        GameState state = gameSession.getState();
        NominationManager nominationManager = getPrivateField(mod, "nominationManager");
        VoteManager voteManager = getPrivateField(mod, "voteManager");
        ExecutionManager executionManager = getPrivateField(mod, "executionManager");

        PlayerReference player1 = PlayerReference.ofName("player1");
        PlayerReference player2 = PlayerReference.ofName("player2");
        PlayerReference player3 = PlayerReference.ofName("player3");
        PlayerReference player4 = PlayerReference.ofName("player4");
        PlayerReference player5 = PlayerReference.ofName("player5");
        PlayerReference storyteller = PlayerReference.ofName("storyteller");

        gameSession.startSetup();
        gameSession.addPlayer(player1, "Player 1", 1);
        gameSession.addPlayer(player2, "Player 2", 2);
        gameSession.addPlayer(player3, "Player 3", 3);
        gameSession.addPlayer(player4, "Player 4", 4);
        gameSession.addPlayer(player5, "Player 5", 5);
        state.getPlayers().register(new PlayerEntry(0, "Storyteller", true, storyteller));

        gameSession.startSeating();
        gameSession.startGame();
        gameSession.transitionPhase(GamePhase.NOMINATION);

        nominationManager.nominate(state, player1, player3);

        GameStateSnapshot afterNomination = decodeLastStateBroadcast();
        assertTrue(afterNomination.nominatedSeat().isPresent(),
                "Nomination should set nominatedSeat in the broadcast state");
        assertEquals(3, afterNomination.nominatedSeat().getAsInt());

        sentPayloads.clear();
        gameSession.transitionPhase(GamePhase.VOTING);

        voteManager.startVote(state, player3);
        List<PlayerReference> voteOrder = voteManager.getVoteOrder();
        assertEquals(5, voteOrder.size(), "All 5 alive non-storyteller players should be eligible voters");
        for (PlayerReference voter : voteOrder) {
            voteManager.castVote(voter, true);
        }

        long voteBroadcasts = countStateBroadcasts();
        assertTrue(voteBroadcasts >= 1,
                "VoteResolved event should trigger at least one state broadcast");

        sentPayloads.clear();
        gameSession.transitionPhase(GamePhase.EXECUTION);

        executionManager.execute(state, storyteller, player3);

        GameStateSnapshot afterExecution = decodeLastStateBroadcast();
        GameStateSnapshot.PlayerSnapshot executedSnapshot = afterExecution.players().stream()
                .filter(p -> p.seatNumber() == 3)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Player 3 should be present in decoded state"));
        assertEquals(LifeState.DEAD, executedSnapshot.lifeState(),
                "Executed player should have DEAD life state in broadcast");

        assertFalse(state.getPlayers().getByPlayerReference(player3).orElseThrow().isAlive(),
                "Player 3 should be dead in the live game state");
    }

    @Test
    void playerJoinLeaveDuringSetupUpdatesState() {
        GameSession gameSession = mod.gameSession();

        PlayerReference player1 = PlayerReference.ofName("player1");
        PlayerReference player2 = PlayerReference.ofName("player2");
        PlayerReference player3 = PlayerReference.ofName("player3");

        gameSession.startSetup();
        gameSession.addPlayer(player1, "Player 1", 1);
        gameSession.addPlayer(player2, "Player 2", 2);
        gameSession.removePlayer(player1);
        gameSession.addPlayer(player3, "Player 3", 3);

        long stateBroadcasts = countStateBroadcasts();
        assertTrue(stateBroadcasts >= 4,
                "Add/remove operations should each trigger a state broadcast; got " + stateBroadcasts);

        GameStateSnapshot finalState = decodeLastStateBroadcast();
        assertEquals(2, finalState.players().size(),
                "Exactly 2 players should remain after join/leave sequence");

        assertTrue(finalState.players().stream()
                        .anyMatch(p -> p.playerReference().equals(player2.value())),
                "Player 2 should be present in final state");
        assertTrue(finalState.players().stream()
                        .anyMatch(p -> p.playerReference().equals(player3.value())),
                "Player 3 should be present in final state");
        assertFalse(finalState.players().stream()
                        .anyMatch(p -> p.playerReference().equals(player1.value())),
                "Player 1 should be absent from final state after removal");
    }

    @Test
    void timerExpiredEventTriggersStateBroadcast() {
        GameSession gameSession = mod.gameSession();

        gameSession.getDispatcher().dispatch(
                new TimerExpired(TimerManager.TimerType.DISCUSSION, 180));

        long stateBroadcasts = countStateBroadcasts();
        assertTrue(stateBroadcasts >= 1,
                "TimerExpired event should trigger a state broadcast");
    }

    private long countStateBroadcasts() {
        return sentPayloads.stream()
                .filter(p -> p.channel().equals(STATE_CHANNEL))
                .count();
    }

    private GameStateSnapshot decodeLastStateBroadcast() {
        List<MidnightCouncilPayload> statePayloads = sentPayloads.stream()
                .filter(p -> p.channel().equals(STATE_CHANNEL))
                .toList();
        assertFalse(statePayloads.isEmpty(), "Expected at least one state broadcast payload");
        MidnightCouncilPayload lastState = statePayloads.get(statePayloads.size() - 1);
        return GameStateCodec.decode(lastState.bytes());
    }

    private static void registerStateBroadcastListeners(MidnightCouncilMod mod) {
        var dispatcher = mod.gameSession().getDispatcher();
        dispatcher.registerListener(PhaseChanged.class, event -> broadcastState(mod));
        dispatcher.registerListener(PlayerStateChanged.class, event -> broadcastState(mod));
        dispatcher.registerListener(NominationOpened.class, event -> broadcastState(mod));
        dispatcher.registerListener(VoteResolved.class, event -> broadcastState(mod));
        dispatcher.registerListener(ExecutionResolved.class, event -> broadcastState(mod));
        dispatcher.registerListener(TimerExpired.class, event -> broadcastState(mod));
    }

    private static void broadcastState(MidnightCouncilMod mod) {
        FabricNetworkAdapter adapter = getPrivateField(mod, "networkAdapter");
        if (adapter == null) {
            return;
        }
        adapter.broadcastPublicPayload(STATE_CHANNEL, GameStateCodec.encode(mod.gameSession().getState()));
    }

    private static void invokeVoid(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramType);
            method.setAccessible(true);
            method.invoke(target, arg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setPrivateField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static FabricNetworkAdapter createStateBroadcastCapturingAdapter(
            List<MidnightCouncilPayload> sentPayloads) {
        FabricNetworkAdapter adapter = mock(FabricNetworkAdapter.class);
        doAnswer(invocation -> {
            sentPayloads.add(new MidnightCouncilPayload(invocation.getArgument(0), invocation.getArgument(1)));
            return null;
        }).when(adapter).broadcastPublicPayload(anyString(), any());
        return adapter;
    }
}
