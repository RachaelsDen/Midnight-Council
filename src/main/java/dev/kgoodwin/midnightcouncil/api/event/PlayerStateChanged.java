package dev.kgoodwin.midnightcouncil.api.event;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

public record PlayerStateChanged(
	PlayerReference player,
	String changeType
) implements GameEvent {
}
