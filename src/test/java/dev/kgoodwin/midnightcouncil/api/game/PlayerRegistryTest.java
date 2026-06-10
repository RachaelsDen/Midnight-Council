package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import org.junit.jupiter.api.Test;

class PlayerRegistryTest {

	@Test
	void playerRegistryRegistersPlayersBySeatAndReference() {
		PlayerRegistry playerRegistry = new PlayerRegistry();
		PlayerEntry playerEntry = new PlayerEntry(4, "Dani", false, PlayerReference.ofName("dani"));

		assertEquals(playerEntry, playerRegistry.register(playerEntry));
		assertTrue(playerRegistry.isClaimed(playerEntry.getPlayerReference()));
		assertEquals(playerEntry, playerRegistry.getBySeatNumber(4).orElseThrow());
		assertEquals(playerEntry, playerRegistry.getByPlayerReference(playerEntry.getPlayerReference()).orElseThrow());
		assertEquals(1, playerRegistry.getPlayers().size());
	}

	@Test
	void playerRegistrySupportsClaimAndUnclaim() {
		PlayerRegistry playerRegistry = new PlayerRegistry();
		PlayerEntry playerEntry = new PlayerEntry(5, "Eli", false, PlayerReference.ofName("eli"));

		playerRegistry.claim(playerEntry);
		assertEquals(playerEntry, playerRegistry.unclaim(playerEntry.getPlayerReference()).orElseThrow());
		assertFalse(playerRegistry.isClaimed(playerEntry.getPlayerReference()));
		assertTrue(playerRegistry.getBySeatNumber(5).isEmpty());
		assertTrue(playerRegistry.getByPlayerReference(playerEntry.getPlayerReference()).isEmpty());
	}

	@Test
	void playerRegistryRejectsDuplicateSeatOrReference() {
		PlayerRegistry playerRegistry = new PlayerRegistry();
		playerRegistry.register(new PlayerEntry(6, "Finn", false, PlayerReference.ofName("finn")));

		assertThrows(IllegalArgumentException.class, () -> playerRegistry.register(new PlayerEntry(6, "Gail", false, PlayerReference.ofName("gail"))));
		assertThrows(IllegalArgumentException.class, () -> playerRegistry.register(new PlayerEntry(7, "Finn Two", false, PlayerReference.ofName("finn"))));
	}

	@Test
	void playerRegistryAliveCountExcludesDeadPlayersAndStorytellers() {
		PlayerRegistry playerRegistry = new PlayerRegistry();
		PlayerEntry storyteller = new PlayerEntry(0, "Storyteller", true, PlayerReference.ofName("storyteller"));
		PlayerEntry alivePlayer = new PlayerEntry(1, "Hana", false, PlayerReference.ofName("hana"));
		PlayerEntry deadPlayer = new PlayerEntry(2, "Ivan", false, PlayerReference.ofName("ivan"));
		deadPlayer.kill();

		playerRegistry.register(storyteller);
		playerRegistry.register(alivePlayer);
		playerRegistry.register(deadPlayer);

		assertEquals(1, playerRegistry.getAliveCount());
	}
}
