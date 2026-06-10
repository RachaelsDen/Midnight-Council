package dev.kgoodwin.midnightcouncil.api.event;

import dev.kgoodwin.midnightcouncil.api.game.TimerManager.TimerType;

public record TimerExpired(
	TimerType timerType,
	long durationSeconds
) implements GameEvent {
}
