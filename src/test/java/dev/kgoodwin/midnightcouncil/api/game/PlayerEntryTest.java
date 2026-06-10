package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import org.junit.jupiter.api.Test;

class PlayerEntryTest {

	@Test
	void playerEntryDefaultsToAliveAndAwake() {
		PlayerReference playerReference = PlayerReference.ofName("alice");
		PlayerEntry playerEntry = new PlayerEntry(1, "Alice", false, playerReference);

		assertEquals(1, playerEntry.getSeatNumber());
		assertEquals("Alice", playerEntry.getDisplayName());
		assertEquals(LifeState.ALIVE, playerEntry.getLifeState());
		assertEquals(SleepState.AWAKE, playerEntry.getSleepState());
		assertFalse(playerEntry.isStoryteller());
		assertEquals(playerReference, playerEntry.getPlayerReference());
		assertTrue(playerEntry.isAlive());
		assertFalse(playerEntry.isSleeping());
	}

	@Test
	void playerEntryAllowsMutableLifeAndSleepState() {
		PlayerEntry playerEntry = new PlayerEntry(2, "Bob", true, PlayerReference.ofName("bob"));

		playerEntry.kill();
		playerEntry.sleep();

		assertEquals(LifeState.DEAD, playerEntry.getLifeState());
		assertEquals(SleepState.SLEEPING, playerEntry.getSleepState());
		assertFalse(playerEntry.isAlive());
		assertTrue(playerEntry.isSleeping());

		playerEntry.revive();
		playerEntry.wake();

		assertEquals(LifeState.ALIVE, playerEntry.getLifeState());
		assertEquals(SleepState.AWAKE, playerEntry.getSleepState());
	}

	@Test
	void playerEntrySupportsExplicitInitialStates() {
		PlayerEntry playerEntry = new PlayerEntry(
				3,
				"Carol",
				LifeState.DEAD,
				SleepState.SLEEPING,
				false,
				PlayerReference.ofName("carol"));

		assertEquals(LifeState.DEAD, playerEntry.getLifeState());
		assertEquals(SleepState.SLEEPING, playerEntry.getSleepState());
	}

	@Test
	void playerEntryRejectsInvalidConstructorValues() {
		assertThrows(IllegalArgumentException.class, () -> new PlayerEntry(-1, "Alice", false, PlayerReference.ofName("alice")));
		assertThrows(IllegalArgumentException.class, () -> new PlayerEntry(1, " ", false, PlayerReference.ofName("alice")));
	}
}
