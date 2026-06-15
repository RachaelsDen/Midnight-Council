package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record GameStateSnapshot(
	GamePhase phase,
	int dayCount,
	int nightCount,
	OptionalInt nominatedSeat,
	OptionalInt markedSeat,
	boolean timerActive,
	List<PlayerSnapshot> players
) {
	public record PlayerSnapshot(
		int seatNumber,
		String displayName,
		LifeState lifeState,
		SleepState sleepState,
		boolean storyteller,
		String playerReference
	) {
		public static PlayerSnapshot from(PlayerEntry entry) {
			Objects.requireNonNull(entry, "entry");
			return new PlayerSnapshot(
				entry.getSeatNumber(),
				entry.getDisplayName(),
				entry.getLifeState(),
				entry.getSleepState(),
				entry.isStoryteller(),
				entry.getPlayerReference().value());
		}
	}

	public static GameStateSnapshot from(GameState state) {
		Objects.requireNonNull(state, "state");
		return new GameStateSnapshot(
			state.getPhase(),
			state.getDayCount(),
			state.getNightCount(),
			state.getNominatedSeat(),
			state.getMarkedSeat(),
			state.isTimerActive(),
			state.getPlayers().getPlayers().stream().map(PlayerSnapshot::from).toList());
	}
}
