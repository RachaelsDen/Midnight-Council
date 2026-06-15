package dev.kgoodwin.midnightcouncil.api.event;

import dev.kgoodwin.midnightcouncil.api.game.TimerManager.TimerType;

public record TimerStarted(
	TimerType timerType,
	long durationSeconds
) implements GameEvent {
}
