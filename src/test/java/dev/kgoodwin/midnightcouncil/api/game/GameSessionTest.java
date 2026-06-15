package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;

import org.junit.jupiter.api.Test;

class GameSessionTest {

	private static void addMinimumPlayers(GameSession session) {
		session.addPlayer(PlayerReference.ofName("alice"), "Alice", 1);
		session.addPlayer(PlayerReference.ofName("bob"), "Bob", 2);
		session.addPlayer(PlayerReference.ofName("carol"), "Carol", 3);
		session.addPlayer(PlayerReference.ofName("dave"), "Dave", 4);
		session.addPlayer(PlayerReference.ofName("eve"), "Eve", 5);
	}

	private static void addTooManyPlayers(GameSession session) {
		for (int seat = 1; seat <= 15; seat++) {
			session.addPlayer(PlayerReference.ofName("player" + seat), "Player " + seat, seat);
		}
		session.getState().getPlayers().register(
				new PlayerEntry(16, "Player 16", false, PlayerReference.ofName("player16")));
	}

	@Test
	void fullLifecycle() {
		GameSession session = new GameSession();
		assertEquals(GamePhase.IDLE, session.getState().getPhase());

		session.startSetup();
		assertEquals(GamePhase.SETUP, session.getState().getPhase());

		addMinimumPlayers(session);
		assertEquals(5, session.getState().getPlayers().getPlayers().size());

		session.startSeating();
		assertEquals(GamePhase.SEATING, session.getState().getPhase());

		session.startGame();
		assertEquals(GamePhase.DAY, session.getState().getPhase());

		session.endGame();
		assertEquals(GamePhase.GAME_OVER, session.getState().getPhase());

		session.resetSession();
		assertEquals(GamePhase.IDLE, session.getState().getPhase());
		assertTrue(session.getState().getPlayers().getPlayers().isEmpty());
	}

	@Test
	void invalidTransitionThrows() {
		GameSession session = new GameSession();
		assertThrows(IllegalStateException.class, () -> session.transitionPhase(GamePhase.DAY));
	}

	@Test
	void addPlayerOnlyValidDuringSetup() {
		GameSession session = new GameSession();
		session.startSetup();
		session.startSeating();

		assertThrows(IllegalStateException.class,
				() -> session.addPlayer(PlayerReference.ofName("alice"), "Alice", 1));
	}

	@Test
	void addPlayerRejectsSeatZero() {
		GameSession session = new GameSession();
		session.startSetup();

		assertThrows(IllegalArgumentException.class,
				() -> session.addPlayer(PlayerReference.ofName("alice"), "Alice", 0));
	}

	@Test
	void addPlayerRejectsSeatAboveRange() {
		GameSession session = new GameSession();
		session.startSetup();

		assertThrows(IllegalArgumentException.class,
				() -> session.addPlayer(PlayerReference.ofName("alice"), "Alice", 16));
	}

	@Test
	void addPlayerAllowsSeat13() {
		GameSession session = new GameSession();
		session.startSetup();

		assertDoesNotThrow(() -> session.addPlayer(PlayerReference.ofName("alice"), "Alice", 13));
	}

	@Test
	void addPlayerRejectsNegativeSeat() {
		GameSession session = new GameSession();
		session.startSetup();

		assertThrows(IllegalArgumentException.class,
				() -> session.addPlayer(PlayerReference.ofName("alice"), "Alice", -1));
	}

	@Test
	void removePlayerOnlyValidDuringSetup() {
		GameSession session = new GameSession();
		session.startSetup();
		PlayerReference alice = PlayerReference.ofName("alice");
		session.addPlayer(alice, "Alice", 1);
		session.startSeating();

		assertThrows(IllegalStateException.class, () -> session.removePlayer(alice));
	}

	@Test
	void phaseChangeFiresEvent() {
		GameSession session = new GameSession();
		List<PhaseChanged> events = new ArrayList<>();
		session.getDispatcher().registerListener(PhaseChanged.class, events::add);

		session.startSetup();

		assertEquals(1, events.size());
		assertEquals(GamePhase.IDLE, events.getFirst().oldPhase());
		assertEquals(GamePhase.SETUP, events.getFirst().newPhase());
	}

	@Test
	void playerStateChangeFiresEventOnKill() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		PlayerReference alice = PlayerReference.ofName("alice");
		session.startSeating();
		session.startGame();

		List<PlayerStateChanged> events = new ArrayList<>();
		session.getDispatcher().registerListener(PlayerStateChanged.class, events::add);

		session.setPlayerAlive(alice, false);

		assertEquals(1, events.size());
		assertEquals(alice, events.getFirst().player());
		assertEquals("killed", events.getFirst().changeType());
		assertFalse(session.getState().getPlayers().getByPlayerReference(alice).orElseThrow().isAlive());
	}

	@Test
	void playerStateChangeFiresEventOnSleep() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		PlayerReference alice = PlayerReference.ofName("alice");
		session.startSeating();
		session.startGame();

		List<PlayerStateChanged> events = new ArrayList<>();
		session.getDispatcher().registerListener(PlayerStateChanged.class, events::add);

		session.setPlayerAsleep(alice, true);

		assertEquals(1, events.size());
		assertEquals(alice, events.getFirst().player());
		assertEquals("asleep", events.getFirst().changeType());
		assertTrue(session.getState().getPlayers().getByPlayerReference(alice).orElseThrow().isSleeping());
	}

	@Test
	void startGameIncrementsDayCount() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		session.startSeating();
		assertEquals(0, session.getState().getDayCount());

		session.startGame();
		assertEquals(1, session.getState().getDayCount());
	}

	@Test
	void transitionPhaseIncrementsCountersBeforeDispatch() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		session.startSeating();

		int[] capturedDayCount = new int[1];
		int[] capturedNightCount = new int[1];
		session.getDispatcher().registerListener(PhaseChanged.class, event -> {
			capturedDayCount[0] = session.getState().getDayCount();
			capturedNightCount[0] = session.getState().getNightCount();
		});

		session.startGame();
		assertEquals(1, capturedDayCount[0], "Day count should be incremented before PhaseChanged dispatch");
		assertEquals(0, capturedNightCount[0]);

		session.startNight();
		assertEquals(1, capturedDayCount[0], "Day count should still be 1");
		assertEquals(1, capturedNightCount[0], "Night count should be incremented before PhaseChanged dispatch");
	}

	@Test
	void subsequentDayTransitionIncrementsDayCount() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		session.startSeating();
		session.startGame();
		session.startNight();

		assertEquals(1, session.getState().getDayCount());
		assertEquals(1, session.getState().getNightCount());

		session.transitionPhase(GamePhase.DAY);
		assertEquals(2, session.getState().getDayCount(), "Day count should increment on each DAY transition");
	}

	@Test
	void resetSessionClearsPlayerRegistry() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		assertEquals(5, session.getState().getPlayers().getPlayers().size());

		session.startSeating();
		session.startGame();
		session.getState().setNominatedSeat(2);
		session.getState().setMarkedSeat(4);
		session.getState().setTimerActive(true);
		session.startNight();
		session.endGame();
		session.resetSession();

		assertTrue(session.getState().getPlayers().getPlayers().isEmpty());
		assertEquals(GamePhase.IDLE, session.getState().getPhase());
		assertEquals(0, session.getState().getDayCount());
		assertEquals(0, session.getState().getNightCount());
		assertTrue(session.getState().getNominatedSeat().isEmpty());
		assertTrue(session.getState().getMarkedSeat().isEmpty());
		assertFalse(session.getState().isTimerActive());
	}

	@Test
	void removeNonexistentPlayerThrows() {
		GameSession session = new GameSession();
		session.startSetup();

		assertThrows(IllegalArgumentException.class,
				() -> session.removePlayer(PlayerReference.ofName("nobody")));
	}

	@Test
	void setPlayerAliveOnNonexistentPlayerThrows() {
		GameSession session = new GameSession();
		session.startSetup();

		assertThrows(IllegalArgumentException.class,
				() -> session.setPlayerAlive(PlayerReference.ofName("nobody"), false));
	}

	@Test
	void addPlayerReturnsEntry() {
		GameSession session = new GameSession();
		session.startSetup();
		PlayerReference alice = PlayerReference.ofName("alice");

		PlayerEntry entry = session.addPlayer(alice, "Alice", 3);

		assertEquals(3, entry.getSeatNumber());
		assertEquals("Alice", entry.getDisplayName());
		assertTrue(entry.isAlive());
		assertFalse(entry.isSleeping());
		assertFalse(entry.isStoryteller());
	}

	@Test
	void addStorytellerRegistersSeatZeroStoryteller() {
		GameSession session = new GameSession();
		session.startSetup();
		PlayerReference storyteller = PlayerReference.ofName("storyteller");

		PlayerEntry entry = session.addStoryteller(storyteller, "Storyteller");

		assertEquals(0, entry.getSeatNumber());
		assertEquals("Storyteller", entry.getDisplayName());
		assertTrue(entry.isStoryteller());
		assertEquals(entry, session.getState().getPlayers().getByPlayerReference(storyteller).orElseThrow());
	}

	@Test
	void addStorytellerThrowsOutsideSetupPhase() {
		GameSession session = new GameSession();

		assertThrows(IllegalStateException.class,
				() -> session.addStoryteller(PlayerReference.ofName("storyteller"), "Storyteller"));
	}

	@Test
	void addStorytellerDispatchesPlayerStateChanged() {
		GameSession session = new GameSession();
		session.startSetup();
		PlayerReference storyteller = PlayerReference.ofName("storyteller");
		List<PlayerStateChanged> events = new ArrayList<>();
		session.getDispatcher().registerListener(PlayerStateChanged.class, events::add);

		session.addStoryteller(storyteller, "Storyteller");

		assertEquals(1, events.size());
		assertEquals(storyteller, events.getFirst().player());
		assertEquals("registered as storyteller", events.getFirst().changeType());
	}

	@Test
	void revivePlayerFiresEvent() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		PlayerReference alice = PlayerReference.ofName("alice");
		session.startSeating();
		session.startGame();

		session.setPlayerAlive(alice, false);
		assertFalse(session.getState().getPlayers().getByPlayerReference(alice).orElseThrow().isAlive());

		List<PlayerStateChanged> events = new ArrayList<>();
		session.getDispatcher().registerListener(PlayerStateChanged.class, events::add);

		session.setPlayerAlive(alice, true);

		assertEquals(1, events.size());
		assertEquals("revived", events.getFirst().changeType());
		assertTrue(session.getState().getPlayers().getByPlayerReference(alice).orElseThrow().isAlive());
	}

	@Test
	void wakePlayerFiresEvent() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		PlayerReference alice = PlayerReference.ofName("alice");
		session.startSeating();
		session.startGame();

		session.setPlayerAsleep(alice, true);

		List<PlayerStateChanged> events = new ArrayList<>();
		session.getDispatcher().registerListener(PlayerStateChanged.class, events::add);

		session.setPlayerAsleep(alice, false);

		assertEquals(1, events.size());
		assertEquals("awake", events.getFirst().changeType());
		assertFalse(session.getState().getPlayers().getByPlayerReference(alice).orElseThrow().isSleeping());
	}

	@Test
	void startGameRejectsTooFewPlayers() {
		GameSession session = new GameSession();
		session.startSetup();
		session.addPlayer(PlayerReference.ofName("alice"), "Alice", 1);
		session.addPlayer(PlayerReference.ofName("bob"), "Bob", 2);
		session.addPlayer(PlayerReference.ofName("carol"), "Carol", 3);
		session.addPlayer(PlayerReference.ofName("dave"), "Dave", 4);
		session.startSeating();

		assertThrows(IllegalStateException.class, session::startGame);
	}

	@Test
	void startNightTransitionsAndIncrementsNightCount() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		session.startSeating();
		session.startGame();

		session.startNight();

		assertEquals(GamePhase.NIGHT, session.getState().getPhase());
		assertEquals(1, session.getState().getDayCount());
		assertEquals(1, session.getState().getNightCount());
	}

	@Test
	void startNightRejectsTooFewPlayers() {
		GameSession session = new GameSession();
		session.startSetup();
		session.addPlayer(PlayerReference.ofName("alice"), "Alice", 1);
		session.addPlayer(PlayerReference.ofName("bob"), "Bob", 2);
		session.addPlayer(PlayerReference.ofName("carol"), "Carol", 3);
		session.addPlayer(PlayerReference.ofName("dave"), "Dave", 4);
		session.startSeating();

		assertThrows(IllegalStateException.class, session::startNight);
	}

	@Test
	void startNightRejectsTooManyPlayers() {
		GameSession session = new GameSession();
		session.startSetup();
		addTooManyPlayers(session);
		session.startSeating();

		assertThrows(IllegalStateException.class, session::startNight);
	}

	@Test
	void transitionPhaseClearsNominatedSeatOutsideVotingAndExecution() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		session.startSeating();
		session.startGame();

		session.getState().setNominatedSeat(2);
		session.transitionPhase(GamePhase.NOMINATION);
		assertTrue(session.getState().getNominatedSeat().isEmpty());

		session.getState().setNominatedSeat(3);
		session.transitionPhase(GamePhase.VOTING);
		assertEquals(3, session.getState().getNominatedSeat().orElseThrow());

		session.transitionPhase(GamePhase.EXECUTION);
		assertEquals(3, session.getState().getNominatedSeat().orElseThrow());

		session.transitionPhase(GamePhase.DAY);
		assertTrue(session.getState().getNominatedSeat().isEmpty());
	}

	@Test
	void resetSessionCanBeCalledFromActiveGamePhases() {
		GameSession session = new GameSession();
		session.startSetup();
		addMinimumPlayers(session);
		session.startSeating();
		session.startGame();

		session.resetSession();

		assertEquals(GamePhase.IDLE, session.getState().getPhase());
		assertTrue(session.getState().getPlayers().getPlayers().isEmpty());
	}
}
