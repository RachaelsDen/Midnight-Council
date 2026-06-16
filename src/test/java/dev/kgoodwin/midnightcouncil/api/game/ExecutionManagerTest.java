package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
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
	private PlayerReference storytellerRef;

	@BeforeEach
	void setUp() {
		dispatcher = new GameEventDispatcher();
		dispatchedEvents = new ArrayList<>();
		dispatcher.registerListener(ExecutionResolved.class, dispatchedEvents::add);
		executionManager = new ExecutionManager(dispatcher);
		state = new GameState();
		state.setPhase(GamePhase.SETUP);
		state.setPhase(GamePhase.SEATING);
		state.setPhase(GamePhase.DAY);
		state.setPhase(GamePhase.NOMINATION);
		state.setPhase(GamePhase.VOTING);
		state.setPhase(GamePhase.EXECUTION);
		storytellerRef = registerStoryteller(0, "Storyteller").getPlayerReference();
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

		executionManager.execute(state, storytellerRef, entry.getPlayerReference());

		assertFalse(entry.isAlive());
		assertEquals(LifeState.DEAD, entry.getLifeState());
	}

	@Test
	void executeSetsMarkedSeatOnGameState() {
		PlayerEntry entry = registerAlivePlayer(3, "Bob");

		executionManager.execute(state, storytellerRef, entry.getPlayerReference());

		assertTrue(state.getMarkedSeat().isPresent());
		assertEquals(3, state.getMarkedSeat().getAsInt());
	}

	@Test
	void executeFiresExecutionResolvedEvent() {
		PlayerEntry entry = registerAlivePlayer(2, "Charlie");
		PlayerReference ref = entry.getPlayerReference();

		executionManager.execute(state, storytellerRef, ref);

		assertEquals(1, dispatchedEvents.size());
		ExecutionResolved event = (ExecutionResolved) dispatchedEvents.get(0);
		assertEquals(ref, event.executed());
	}

	@Test
	void cannotExecuteDeadPlayer() {
		PlayerEntry entry = registerAlivePlayer(1, "DeadPlayer");
		entry.kill();

		PlayerReference ref = entry.getPlayerReference();
		assertThrows(IllegalStateException.class,
				() -> executionManager.execute(state, storytellerRef, ref));
		assertTrue(dispatchedEvents.isEmpty());
	}

	@Test
	void canExecuteStoryteller() {
		PlayerEntry entry = registerStoryteller(5, "TargetStoryteller");

		PlayerReference ref = entry.getPlayerReference();
		executionManager.execute(state, storytellerRef, ref);
		assertFalse(entry.isAlive());
		assertEquals(1, dispatchedEvents.size());
	}

	@Test
	void cannotExecuteUnregisteredPlayer() {
		PlayerReference unknown = PlayerReference.ofName("Nobody");

		assertThrows(IllegalStateException.class,
				() -> executionManager.execute(state, storytellerRef, unknown));
		assertTrue(dispatchedEvents.isEmpty());
	}

	@Test
	void canExecuteReturnsTrueForAlivePlayer() {
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
	void canExecuteReturnsTrueForStoryteller() {
		assertTrue(executionManager.canExecute(state, storytellerRef));
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
		assertThrows(NullPointerException.class,
				() -> executionManager.execute(null, storytellerRef, ref));
	}

	@Test
	void executeRejectsNullPlayer() {
		assertThrows(NullPointerException.class,
				() -> executionManager.execute(state, storytellerRef, null));
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

	@Test
	void executeRejectsWrongPhase() {
		GameState dayState = GameState.reconstruct(GamePhase.DAY, 0, 0, null, null, false);
		PlayerEntry storyteller = new PlayerEntry(0, "ST", true, PlayerReference.ofName("ST"));
		dayState.getPlayers().register(storyteller);
		PlayerEntry entry = new PlayerEntry(1, "Alice", false, PlayerReference.ofName("Alice"));
		dayState.getPlayers().register(entry);

		assertThrows(IllegalStateException.class,
				() -> executionManager.execute(dayState, storyteller.getPlayerReference(), entry.getPlayerReference()));
	}

	@Test
	void canExecuteReturnsFalseOutsideExecutionPhase() {
		GameState dayState = GameState.reconstruct(GamePhase.DAY, 0, 0, null, null, false);
		PlayerEntry entry = new PlayerEntry(1, "Alice", false, PlayerReference.ofName("Alice"));
		dayState.getPlayers().register(entry);

		assertFalse(executionManager.canExecute(dayState, entry.getPlayerReference()));
	}

	@Test
	void executeRejectsNonStorytellerActor() {
		PlayerEntry actor = registerAlivePlayer(7, "NonStoryteller");
		PlayerEntry target = registerAlivePlayer(8, "Target");

		assertThrows(IllegalStateException.class,
				() -> executionManager.execute(state, actor.getPlayerReference(), target.getPlayerReference()));
		assertTrue(dispatchedEvents.isEmpty());
	}

	@Test
	void executeRejectsUnregisteredActor() {
		PlayerEntry target = registerAlivePlayer(8, "Target");
		PlayerReference unknownActor = PlayerReference.ofName("Ghost");

		assertThrows(IllegalStateException.class,
				() -> executionManager.execute(state, unknownActor, target.getPlayerReference()));
		assertTrue(dispatchedEvents.isEmpty());
	}

	@Test
	void executeRejectsNullStoryteller() {
		PlayerEntry target = registerAlivePlayer(8, "Target");

		assertThrows(NullPointerException.class,
				() -> executionManager.execute(state, null, target.getPlayerReference()));
	}

	@Test
	void canExecuteByReturnsTrueForStoryteller() {
		assertTrue(executionManager.canExecuteBy(state, storytellerRef));
	}

	@Test
	void canExecuteByReturnsFalseForNonStoryteller() {
		PlayerEntry actor = registerAlivePlayer(7, "NonStoryteller");

		assertFalse(executionManager.canExecuteBy(state, actor.getPlayerReference()));
	}

	@Test
	void canExecuteByReturnsFalseForUnregistered() {
		PlayerReference unknown = PlayerReference.ofName("Ghost");

		assertFalse(executionManager.canExecuteBy(state, unknown));
	}

	@Test
	void canExecuteByRejectsNullState() {
		assertThrows(NullPointerException.class,
				() -> executionManager.canExecuteBy(null, storytellerRef));
	}

	@Test
	void canExecuteByReturnsFalseForNullStoryteller() {
		assertFalse(executionManager.canExecuteBy(state, null));
	}
}
