package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void fullLifecycle() {
		GameSession session = new GameSession();
		assertEquals(GamePhase.IDLE, session.getState().getPhase());

		session.startSetup();
		assertEquals(GamePhase.SETUP, session.getState().getPhase());

		session.addPlayer(PlayerReference.ofName("alice"), "Alice", 1);
		session.addPlayer(PlayerReference.ofName("bob"), "Bob", 2);
		assertEquals(2, session.getState().getPlayers().getPlayers().size());

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
		PlayerReference alice = PlayerReference.ofName("alice");
		session.addPlayer(alice, "Alice", 1);
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
		PlayerReference alice = PlayerReference.ofName("alice");
		session.addPlayer(alice, "Alice", 1);
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
		session.startSeating();
		assertEquals(0, session.getState().getDayCount());

		session.startGame();
		assertEquals(1, session.getState().getDayCount());
	}

	@Test
	void resetSessionClearsPlayerRegistry() {
		GameSession session = new GameSession();
		session.startSetup();
		session.addPlayer(PlayerReference.ofName("alice"), "Alice", 1);
		session.addPlayer(PlayerReference.ofName("bob"), "Bob", 2);
		assertEquals(2, session.getState().getPlayers().getPlayers().size());

		session.startSeating();
		session.startGame();
		session.endGame();
		session.resetSession();

		assertTrue(session.getState().getPlayers().getPlayers().isEmpty());
		assertEquals(GamePhase.IDLE, session.getState().getPhase());
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
	void revivePlayerFiresEvent() {
		GameSession session = new GameSession();
		session.startSetup();
		PlayerReference alice = PlayerReference.ofName("alice");
		session.addPlayer(alice, "Alice", 1);
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
		PlayerReference alice = PlayerReference.ofName("alice");
		session.addPlayer(alice, "Alice", 1);
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
}
