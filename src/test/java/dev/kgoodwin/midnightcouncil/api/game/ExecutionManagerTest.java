package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.ExecutionResolved;
import dev.kgoodwin.midnightcouncil.api.event.GameEvent;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionManagerTest {

	private GameEventDispatcher dispatcher;
	private List<GameEvent> dispatchedEvents;
	private ExecutionManager executionManager;
	private GameState state;

	@BeforeEach
	void setUp() {
		dispatcher = new GameEventDispatcher();
		dispatchedEvents = new ArrayList<>();
		dispatcher.registerListener(ExecutionResolved.class, dispatchedEvents::add);
		executionManager = new ExecutionManager(dispatcher);
		state = new GameState();
	}

	private PlayerEntry registerAlivePlayer(int seat, String name) {
		PlayerReference ref = PlayerReference.ofName(name);
		PlayerEntry entry = new PlayerEntry(seat, name, false, ref);
		state.getPlayers().register(entry);
		return entry;
	}

	private PlayerEntry registerStoryteller(int seat, String name) {
		PlayerReference ref = PlayerReference.ofName(name);
		PlayerEntry entry = new PlayerEntry(seat, name, true, ref);
		state.getPlayers().register(entry);
		return entry;
	}

	@Test
	void executeKillsPlayer() {
		PlayerEntry entry = registerAlivePlayer(1, "Alice");

		executionManager.execute(state, entry.getPlayerReference());

		assertFalse(entry.isAlive());
		assertEquals(LifeState.DEAD, entry.getLifeState());
	}

	@Test
	void executeSetsMarkedSeatOnGameState() {
		PlayerEntry entry = registerAlivePlayer(3, "Bob");

		executionManager.execute(state, entry.getPlayerReference());

		assertTrue(state.getMarkedSeat().isPresent());
		assertEquals(3, state.getMarkedSeat().getAsInt());
	}

	@Test
	void executeFiresExecutionResolvedEvent() {
		PlayerEntry entry = registerAlivePlayer(2, "Charlie");
		PlayerReference ref = entry.getPlayerReference();

		executionManager.execute(state, ref);

		assertEquals(1, dispatchedEvents.size());
		ExecutionResolved event = (ExecutionResolved) dispatchedEvents.get(0);
		assertEquals(ref, event.executed());
	}

	@Test
	void cannotExecuteDeadPlayer() {
		PlayerEntry entry = registerAlivePlayer(1, "DeadPlayer");
		entry.kill();

		PlayerReference ref = entry.getPlayerReference();
		assertThrows(IllegalStateException.class, () -> executionManager.execute(state, ref));
		assertTrue(dispatchedEvents.isEmpty());
	}

	@Test
	void cannotExecuteStoryteller() {
		PlayerEntry entry = registerStoryteller(0, "Storyteller");

		PlayerReference ref = entry.getPlayerReference();
		assertThrows(IllegalStateException.class, () -> executionManager.execute(state, ref));
		assertTrue(dispatchedEvents.isEmpty());
	}

	@Test
	void cannotExecuteUnregisteredPlayer() {
		PlayerReference unknown = PlayerReference.ofName("Nobody");

		assertThrows(IllegalStateException.class, () -> executionManager.execute(state, unknown));
		assertTrue(dispatchedEvents.isEmpty());
	}

	@Test
	void canExecuteReturnsTrueForAliveNonStorytellerPlayer() {
		PlayerEntry entry = registerAlivePlayer(1, "ValidPlayer");

		assertTrue(executionManager.canExecute(state, entry.getPlayerReference()));
	}

	@Test
	void canExecuteReturnsFalseForDeadPlayer() {
		PlayerEntry entry = registerAlivePlayer(1, "DeadPlayer");
		entry.kill();

		assertFalse(executionManager.canExecute(state, entry.getPlayerReference()));
	}

	@Test
	void canExecuteReturnsFalseForStoryteller() {
		PlayerEntry entry = registerStoryteller(0, "Storyteller");

		assertFalse(executionManager.canExecute(state, entry.getPlayerReference()));
	}

	@Test
	void canExecuteReturnsFalseForUnregisteredPlayer() {
		PlayerReference unknown = PlayerReference.ofName("Nobody");

		assertFalse(executionManager.canExecute(state, unknown));
	}

	@Test
	void constructorRejectsNullDispatcher() {
		assertThrows(NullPointerException.class, () -> new ExecutionManager(null));
	}

	@Test
	void executeRejectsNullState() {
		PlayerReference ref = PlayerReference.ofName("Alice");
		assertThrows(NullPointerException.class, () -> executionManager.execute(null, ref));
	}

	@Test
	void executeRejectsNullPlayer() {
		assertThrows(NullPointerException.class, () -> executionManager.execute(state, null));
	}

	@Test
	void canExecuteRejectsNullState() {
		PlayerReference ref = PlayerReference.ofName("Alice");
		assertThrows(NullPointerException.class, () -> executionManager.canExecute(null, ref));
	}

	@Test
	void canExecuteRejectsNullPlayer() {
		assertFalse(executionManager.canExecute(state, null));
	}
}
