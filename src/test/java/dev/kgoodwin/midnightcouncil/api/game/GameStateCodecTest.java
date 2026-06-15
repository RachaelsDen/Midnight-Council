package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import org.junit.jupiter.api.Test;

class GameStateCodecTest {

	@Test
	void roundTripPreservesAllFields() {
		GameState original = GameState.reconstruct(GamePhase.EXECUTION, 4, 2, 1, 2, true);
		original.getPlayers().register(new PlayerEntry(1, "Alice", LifeState.DEAD, SleepState.SLEEPING, false, PlayerReference.ofName("alice")));
		original.getPlayers().register(new PlayerEntry(2, "Bob", LifeState.ALIVE, SleepState.AWAKE, true, PlayerReference.ofName("bob")));

		GameStateSnapshot expected = GameStateSnapshot.from(original);
		byte[] bytes = GameStateCodec.encode(original);
		GameStateSnapshot actual = GameStateCodec.decode(bytes);

		assertEquals(expected, actual);
	}

	@Test
	void roundTripWithEmptyGameState() {
		GameState empty = new GameState();

		GameStateSnapshot expected = GameStateSnapshot.from(empty);
		byte[] bytes = GameStateCodec.encode(empty);
		GameStateSnapshot actual = GameStateCodec.decode(bytes);

		assertEquals(expected, actual);
	}

	@Test
	void roundTripWithNullOptionals() {
		GameState withoutSelections = GameState.reconstruct(GamePhase.DAY, 1, 3, null, null, false);

		byte[] bytes = GameStateCodec.encode(withoutSelections);
		GameStateSnapshot decoded = GameStateCodec.decode(bytes);

		assertEquals(GameStateSnapshot.from(withoutSelections), decoded);
		assertEquals(true, decoded.nominatedSeat().isEmpty());
		assertEquals(true, decoded.markedSeat().isEmpty());
	}

	@Test
	void playerSnapshotCapturesAllFields() {
		PlayerEntry entry = new PlayerEntry(5, "Narrator", LifeState.DEAD, SleepState.SLEEPING, true, PlayerReference.ofName("storyteller"));
		GameStateSnapshot.PlayerSnapshot snapshot = GameStateSnapshot.PlayerSnapshot.from(entry);

		assertEquals(5, snapshot.seatNumber());
		assertEquals("Narrator", snapshot.displayName());
		assertEquals(LifeState.DEAD, snapshot.lifeState());
		assertEquals(SleepState.SLEEPING, snapshot.sleepState());
		assertEquals(true, snapshot.storyteller());
		assertEquals("storyteller", snapshot.playerReference());
	}

	@Test
	void decodeThrowsOnTruncatedInput() {
		byte[] truncated = new byte[] {1};

		assertThrows(UncheckedIOException.class, () -> GameStateCodec.decode(truncated));
	}
}
