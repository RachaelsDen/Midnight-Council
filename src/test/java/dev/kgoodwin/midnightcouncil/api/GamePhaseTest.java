package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GamePhaseTest {

	@Test
	void gamePhaseExposesExpectedPhaseOrder() {
		assertEquals(
				java.util.List.of(
						GamePhase.IDLE,
						GamePhase.SETUP,
						GamePhase.SEATING,
						GamePhase.DAY,
						GamePhase.NOMINATION,
						GamePhase.VOTING,
						GamePhase.EXECUTION,
						GamePhase.NIGHT,
						GamePhase.GAME_OVER),
				java.util.List.of(GamePhase.values()));
	}

	@Test
	void gamePhaseDefinesAllowedTransitionsMap() throws NoSuchFieldException {
		Field field = GamePhase.class.getDeclaredField("ALLOWED_TRANSITIONS");
		assertEquals(Map.class, field.getType());
		assertTrue(Modifier.isPrivate(field.getModifiers()));
		assertTrue(Modifier.isStatic(field.getModifiers()));
		assertTrue(Modifier.isFinal(field.getModifiers()));
	}

	@Test
	void gamePhaseAllowsExpectedTransitions() {
		assertTrue(GamePhase.IDLE.canTransitionTo(GamePhase.SETUP));
		assertTrue(GamePhase.SETUP.canTransitionTo(GamePhase.SEATING));
		assertTrue(GamePhase.SEATING.canTransitionTo(GamePhase.DAY));
		assertTrue(GamePhase.SEATING.canTransitionTo(GamePhase.NIGHT));
		assertTrue(GamePhase.DAY.canTransitionTo(GamePhase.NOMINATION));
		assertTrue(GamePhase.DAY.canTransitionTo(GamePhase.NIGHT));
		assertTrue(GamePhase.DAY.canTransitionTo(GamePhase.GAME_OVER));
		assertTrue(GamePhase.NOMINATION.canTransitionTo(GamePhase.VOTING));
		assertTrue(GamePhase.VOTING.canTransitionTo(GamePhase.EXECUTION));
		assertTrue(GamePhase.EXECUTION.canTransitionTo(GamePhase.DAY));
		assertTrue(GamePhase.NIGHT.canTransitionTo(GamePhase.DAY));
		assertTrue(GamePhase.GAME_OVER.canTransitionTo(GamePhase.IDLE));
	}

	@Test
	void gamePhaseRejectsInvalidTransitions() {
		assertFalse(GamePhase.IDLE.canTransitionTo(GamePhase.DAY));
		assertFalse(GamePhase.SETUP.canTransitionTo(GamePhase.GAME_OVER));
		assertThrows(IllegalStateException.class, () -> GamePhase.IDLE.transitionTo(GamePhase.DAY));
	}
}
