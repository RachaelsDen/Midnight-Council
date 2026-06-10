package dev.kgoodwin.midnightcouncil.api.event;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

public record ExecutionResolved(
	PlayerReference executed,
	String method
) implements GameEvent {
}
