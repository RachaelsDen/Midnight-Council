package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
	void gamePhaseAllowsEveryValidTransition() {
		Map<GamePhase, Set<GamePhase>> expectedTransitions = expectedTransitions();

		for (GamePhase source : GamePhase.values()) {
			for (GamePhase target : expectedTransitions.get(source)) {
				assertTrue(source.canTransitionTo(target), () -> source + " should transition to " + target);
				assertEquals(target, source.transitionTo(target), () -> source + " should return " + target);
			}
		}
	}

	@Test
	void gamePhaseRejectsEveryInvalidTransition() {
		Map<GamePhase, Set<GamePhase>> expectedTransitions = expectedTransitions();

		for (GamePhase source : GamePhase.values()) {
			for (GamePhase target : GamePhase.values()) {
				if (expectedTransitions.get(source).contains(target)) {
					continue;
				}

				assertFalse(source.canTransitionTo(target), () -> source + " should reject " + target);
				assertThrows(IllegalStateException.class, () -> source.transitionTo(target), () -> source + " should throw for " + target);
			}
		}
	}

	private static Map<GamePhase, Set<GamePhase>> expectedTransitions() {
		Map<GamePhase, Set<GamePhase>> transitions = new EnumMap<>(GamePhase.class);
		transitions.put(GamePhase.IDLE, EnumSet.of(GamePhase.SETUP));
		transitions.put(GamePhase.SETUP, EnumSet.of(GamePhase.SEATING));
		transitions.put(GamePhase.SEATING, EnumSet.of(GamePhase.DAY, GamePhase.NIGHT));
		transitions.put(GamePhase.DAY, EnumSet.of(GamePhase.NOMINATION, GamePhase.NIGHT, GamePhase.GAME_OVER));
		transitions.put(GamePhase.NOMINATION, EnumSet.of(GamePhase.VOTING, GamePhase.DAY, GamePhase.NIGHT));
		transitions.put(GamePhase.VOTING, EnumSet.of(GamePhase.EXECUTION, GamePhase.DAY, GamePhase.NIGHT));
		transitions.put(GamePhase.EXECUTION, EnumSet.of(GamePhase.DAY, GamePhase.NIGHT));
		transitions.put(GamePhase.NIGHT, EnumSet.of(GamePhase.DAY, GamePhase.GAME_OVER));
		transitions.put(GamePhase.GAME_OVER, EnumSet.of(GamePhase.IDLE));
		return transitions;
	}
}
