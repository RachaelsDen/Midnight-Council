package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import org.junit.jupiter.api.Test;

class GameStateTest {

	@Test
	void gameStateStartsWithExpectedDefaults() {
		GameState gameState = new GameState();

		assertEquals(GamePhase.IDLE, gameState.getPhase());
		assertEquals(0, gameState.getPlayers().getPlayers().size());
		assertTrue(gameState.getNominatedSeat().isEmpty());
		assertTrue(gameState.getMarkedSeat().isEmpty());
		assertFalse(gameState.isTimerActive());
		assertEquals(0, gameState.getDayCount());
		assertEquals(0, gameState.getNightCount());
		assertEquals(0, gameState.getAliveCount());
	}

	@Test
	void gameStateMutatesTrackedFields() {
		GameState gameState = new GameState();
		gameState.getPlayers().register(new PlayerEntry(1, "June", false, PlayerReference.ofName("june")));

		gameState.setPhase(GamePhase.SETUP);
		gameState.setNominatedSeat(2);
		gameState.setMarkedSeat(3);
		gameState.setTimerActive(true);
		gameState.incrementDayCount();
		gameState.incrementNightCount();

		assertEquals(GamePhase.SETUP, gameState.getPhase());
		assertEquals(2, gameState.getNominatedSeat().orElseThrow());
		assertEquals(3, gameState.getMarkedSeat().orElseThrow());
		assertTrue(gameState.isTimerActive());
		assertEquals(1, gameState.getDayCount());
		assertEquals(1, gameState.getNightCount());
		assertEquals(1, gameState.getAliveCount());
	}

	@Test
	void gameStatePhaseSetterValidatesTransitions() {
		GameState gameState = new GameState();

		assertThrows(IllegalStateException.class, () -> gameState.setPhase(GamePhase.DAY));

		gameState.setPhase(GamePhase.SETUP);
		assertThrows(IllegalStateException.class, () -> gameState.setPhase(GamePhase.DAY));
	}

	@Test
	void gameStateClearsMarkedAndNominatedSeats() {
		GameState gameState = new GameState();
		gameState.setNominatedSeat(4);
		gameState.setMarkedSeat(5);

		gameState.clearNominatedSeat();
		gameState.clearMarkedSeat();

		assertTrue(gameState.getNominatedSeat().isEmpty());
		assertTrue(gameState.getMarkedSeat().isEmpty());
	}
}
