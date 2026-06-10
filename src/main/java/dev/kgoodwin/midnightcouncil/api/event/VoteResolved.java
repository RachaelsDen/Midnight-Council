package dev.kgoodwin.midnightcouncil.api.event;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

public record VoteResolved(
	PlayerReference nominee,
	int voteCount,
	int threshold
) implements GameEvent {
}
