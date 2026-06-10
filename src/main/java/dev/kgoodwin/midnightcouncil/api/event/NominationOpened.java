package dev.kgoodwin.midnightcouncil.api.event;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

public record NominationOpened(
	PlayerReference nominator,
	PlayerReference nominee
) implements GameEvent {
}
