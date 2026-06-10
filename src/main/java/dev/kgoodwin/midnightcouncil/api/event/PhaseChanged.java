package dev.kgoodwin.midnightcouncil.api.event;

import dev.kgoodwin.midnightcouncil.api.GamePhase;

public record PhaseChanged(
	GamePhase oldPhase,
	GamePhase newPhase
) implements GameEvent {
}
