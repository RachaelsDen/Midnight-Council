package dev.kgoodwin.midnightcouncil.fabric.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.fabric.MidnightCouncilMod;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MidnightCommandTreeTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void statusShowsNoGameInProgressWhenIdle() {
        GameState gameState = new GameState();

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("No game in progress", status);
    }

    @Test
    void statusRendersPlayersAndCountsInSetup() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.getPlayers().register(new PlayerEntry(1, "Ana", false, PlayerReference.ofName("ana")));
        gameState.getPlayers().register(new PlayerEntry(2, "Ben", false, PlayerReference.ofName("ben")));
        gameState.getPlayers().register(new PlayerEntry(3, "Cal", false, PlayerReference.ofName("cal")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: SETUP | Players: 3 alive / 3 total | Day: 0 | Night: 0", status);
    }

    @Test
    void statusRendersAliveDeadPlayersInDayPhase() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.setPhase(GamePhase.SEATING);
        gameState.setPhase(GamePhase.DAY);
        gameState.setDayCount(2);

        gameState.getPlayers().register(new PlayerEntry(1, "Dia", false, PlayerReference.ofName("dia")));
        PlayerEntry deadPlayer = new PlayerEntry(2, "Eli", false, PlayerReference.ofName("eli"));
        deadPlayer.kill();
        gameState.getPlayers().register(deadPlayer);
        gameState.getPlayers().register(new PlayerEntry(3, "Fay", false, PlayerReference.ofName("fay")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: DAY | Players: 2 alive / 3 total | Day: 2 | Night: 0", status);
    }

    @Test
    void statusExcludesStorytellerFromPlayerCounts() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.setPhase(GamePhase.SEATING);
        gameState.setPhase(GamePhase.DAY);
        gameState.setDayCount(1);

        gameState.getPlayers().register(new PlayerEntry(1, "Ivy", false, PlayerReference.ofName("ivy")));
        gameState.getPlayers().register(new PlayerEntry(2, "Jax", false, PlayerReference.ofName("jax")));
        gameState.getPlayers().register(new PlayerEntry(15, "Narrator", true, PlayerReference.ofName("narrator")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: DAY | Players: 2 alive / 2 total | Day: 1 | Night: 0", status);
    }

    @Test
    void statusRendersNightCountInNightPhase() {
        GameState gameState = new GameState();
        gameState.setPhase(GamePhase.SETUP);
        gameState.setPhase(GamePhase.SEATING);
        gameState.setPhase(GamePhase.DAY);
        gameState.setPhase(GamePhase.NIGHT);
        gameState.setNightCount(5);

        gameState.getPlayers().register(new PlayerEntry(1, "Gio", false, PlayerReference.ofName("gio")));
        gameState.getPlayers().register(new PlayerEntry(2, "Hal", false, PlayerReference.ofName("hal")));

        String status = MidnightCommandTree.formatStatus(gameState);

        assertEquals("Phase: NIGHT | Players: 2 alive / 2 total | Day: 0 | Night: 5", status);
    }

    @Test
    void parsesPhaseNamesCaseInsensitively() {
        assertEquals(GamePhase.NIGHT, MidnightCommandTree.parsePhaseName("night"));
        assertEquals(GamePhase.GAME_OVER, MidnightCommandTree.parsePhaseName("Game_Over"));
    }

    @Test
    void rejectsUnknownPhaseNames() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MidnightCommandTree.parsePhaseName("sunrise"));

        assertEquals("Unknown phase: sunrise", exception.getMessage());
    }

    @Test
    void findsNextAvailableSeatFromGameState() {
        GameState gameState = new GameState();
        gameState.getPlayers().register(new PlayerEntry(1, "Ana", false, PlayerReference.ofName("ana")));
        gameState.getPlayers().register(new PlayerEntry(2, "Ben", false, PlayerReference.ofName("ben")));
        gameState.getPlayers().register(new PlayerEntry(4, "Cal", false, PlayerReference.ofName("cal")));

        assertEquals(3, MidnightCommandTree.findNextAvailableSeat(gameState));
    }

    @Test
    void throwsWhenNoSeatsRemain() {
        GameState gameState = new GameState();
        for (int seat = 1; seat <= 15; seat++) {
            gameState.getPlayers().register(new PlayerEntry(seat, "P" + seat, false, PlayerReference.ofName("p" + seat)));
        }

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> MidnightCommandTree.findNextAvailableSeat(gameState));

        assertEquals("No available seats", exception.getMessage());
    }

    @Test
    void commandExecutionReportsFriendlyFailures() {
        CommandSourceStack source = mock(CommandSourceStack.class);

        int result = MidnightCommandTree.executeCommand(source, () -> {
            throw new IllegalStateException("bad state");
        });

        assertEquals(0, result);
        verify(source).sendFailure(eq(Component.literal("bad state")));
    }

    @Test
    void commandExecutionReportsSyntaxFailures() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        AtomicReference<String> captured = new AtomicReference<>();

        doAnswer(invocation -> {
            Component component = invocation.getArgument(0);
            captured.set(component.getString());
            return null;
        }).when(source).sendFailure(any());

        int result = MidnightCommandTree.executeCommand(source, () -> {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
        });

        assertEquals(0, result);
        verify(source).sendFailure(any());
        assertEquals(false, captured.get() == null || captured.get().isBlank());
    }

    @Test
    void commandExecutionReportsSuccessMessages() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        AtomicReference<String> captured = new AtomicReference<>();

        doAnswer(invocation -> {
            java.util.function.Supplier<Component> supplier = invocation.getArgument(0);
            captured.set(supplier.get().getString());
            return null;
        }).when(source).sendSuccess(any(), eq(false));

        int result = MidnightCommandTree.executeCommand(source, () -> "ok");

        assertEquals(1, result);
        assertEquals("ok", captured.get());
    }

    @Test
    void registersExpandedCommandTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        MidnightCommandTree.register(dispatcher, new MidnightCouncilMod(), new GameSession());

        AtomicBoolean hasJoin = new AtomicBoolean(false);
        AtomicBoolean hasLeave = new AtomicBoolean(false);
        AtomicBoolean hasStoryteller = new AtomicBoolean(false);
        AtomicBoolean hasSetup = new AtomicBoolean(false);
        AtomicBoolean hasStart = new AtomicBoolean(false);
        AtomicBoolean hasPhase = new AtomicBoolean(false);
        AtomicBoolean hasNominate = new AtomicBoolean(false);
        AtomicBoolean hasVote = new AtomicBoolean(false);
        AtomicBoolean hasVoteStart = new AtomicBoolean(false);
        AtomicBoolean hasExecute = new AtomicBoolean(false);
        AtomicBoolean hasTimer = new AtomicBoolean(false);

        CommandNode<CommandSourceStack> midnight = dispatcher.getRoot().getChildren().stream()
                .filter(node -> node.getName().equals("midnight"))
                .findFirst()
                .orElseThrow();

        midnight.getChildren().forEach(node -> {
                    switch (node.getName()) {
                        case "join" -> hasJoin.set(true);
                        case "leave" -> hasLeave.set(true);
                        case "storyteller" -> hasStoryteller.set(true);
                        case "setup" -> hasSetup.set(true);
                        case "start" -> hasStart.set(true);
                        case "phase" -> hasPhase.set(true);
                        case "nominate" -> hasNominate.set(true);
                        case "vote" -> {
                            hasVote.set(true);
                            hasVoteStart.set(node.getChild("start") != null);
                        }
                        case "execute" -> hasExecute.set(true);
                        case "timer" -> hasTimer.set(true);
                        default -> {
                        }
                    }
                });

        assertEquals(true, hasJoin.get());
        assertEquals(true, hasLeave.get());
        assertEquals(true, hasStoryteller.get());
        assertEquals(true, hasSetup.get());
        assertEquals(true, hasStart.get());
        assertEquals(true, hasPhase.get());
        assertEquals(true, hasNominate.get());
        assertEquals(true, hasVote.get());
        assertEquals(true, hasVoteStart.get());
        assertEquals(true, hasExecute.get());
        assertEquals(true, hasTimer.get());
    }

	@Test
	void registeredCommandTreeIncludesVoteStartSubcommand() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

		MidnightCommandTree.register(dispatcher, new MidnightCouncilMod(), new GameSession());

		CommandNode<CommandSourceStack> midnight = dispatcher.getRoot().getChild("midnight");
		assertTrue(midnight != null && midnight.getChild("vote") != null);
		assertTrue(midnight.getChild("vote").getChild("start") != null);
	}
}
