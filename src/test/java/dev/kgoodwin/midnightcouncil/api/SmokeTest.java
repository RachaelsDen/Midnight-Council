package dev.kgoodwin.midnightcouncil.api;

import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.PlayerRegistry;
import dev.kgoodwin.midnightcouncil.api.seating.SeatLayouts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SmokeTest {

	@Test
	void coreApiTypesAreLoadable() {
		assertNotNull(GamePhase.IDLE);
		assertNotNull(PlayerReference.ofName("test"));
		assertNotNull(new Position(0, 0, 0));
		assertNotNull(new GameEventDispatcher());
		assertNotNull(new GameState());
		assertNotNull(new PlayerEntry(0, "storyteller", true, PlayerReference.ofName("storyteller")));
		assertNotNull(new PlayerRegistry());
		assertNotNull(SeatLayouts.getLayout(7));
	}
}
