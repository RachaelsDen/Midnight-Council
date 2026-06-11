package dev.kgoodwin.midnightcouncil.api;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum GamePhase {
	IDLE,
	SETUP,
	SEATING,
	DAY,
	NOMINATION,
	VOTING,
	EXECUTION,
	NIGHT,
	GAME_OVER;

	private static final Map<GamePhase, Set<GamePhase>> ALLOWED_TRANSITIONS = new EnumMap<>(GamePhase.class);

	static {
		allow(IDLE, SETUP);
		allow(SETUP, SEATING);
		allow(SEATING, DAY, NIGHT);
		allow(DAY, NOMINATION, NIGHT, GAME_OVER);
		allow(NOMINATION, VOTING, DAY, NIGHT);
		allow(VOTING, EXECUTION, DAY, NIGHT);
		allow(EXECUTION, DAY, NIGHT);
		allow(NIGHT, DAY, GAME_OVER);
		allow(GAME_OVER, IDLE);

		for (GamePhase phase : values()) {
			ALLOWED_TRANSITIONS.computeIfAbsent(phase, ignored -> Collections.emptySet());
		}
	}

	private static void allow(GamePhase source, GamePhase... targets) {
		EnumSet<GamePhase> allowedTargets = EnumSet.noneOf(GamePhase.class);
		Collections.addAll(allowedTargets, targets);
		ALLOWED_TRANSITIONS.put(source, Collections.unmodifiableSet(allowedTargets));
	}

	public boolean canTransitionTo(GamePhase target) {
		return ALLOWED_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
	}

	public GamePhase transitionTo(GamePhase target) {
		if (!canTransitionTo(target)) {
			throw new IllegalStateException("Cannot transition from " + this + " to " + target);
		}

		return target;
	}

	public boolean isInGame() {
		return switch (this) {
			case DAY, NOMINATION, VOTING, EXECUTION, NIGHT -> true;
			case IDLE, SETUP, SEATING, GAME_OVER -> false;
		};
	}
}
