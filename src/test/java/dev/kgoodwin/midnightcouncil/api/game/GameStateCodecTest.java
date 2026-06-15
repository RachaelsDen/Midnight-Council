package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.OptionalInt;

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

	@Test
	void decodeThrowsOnInvalidPhaseName() throws IOException {
		byte[] bytes = writeEncodedState("INVALID_PHASE", 0, 0, OptionalInt.empty(), OptionalInt.empty(), false, 0);

		assertThrows(UncheckedIOException.class, () -> GameStateCodec.decode(bytes));
	}

	@Test
	void decodeThrowsOnNegativeDayCount() throws IOException {
		byte[] bytes = writeEncodedState(GamePhase.DAY, -1, 0, OptionalInt.empty(), OptionalInt.empty(), false, 0);

		assertThrows(UncheckedIOException.class, () -> GameStateCodec.decode(bytes));
	}

	@Test
	void decodeThrowsOnNegativeNightCount() throws IOException {
		byte[] bytes = writeEncodedState(GamePhase.DAY, 0, -1, OptionalInt.empty(), OptionalInt.empty(), false, 0);

		assertThrows(UncheckedIOException.class, () -> GameStateCodec.decode(bytes));
	}

	@Test
	void decodeRejectsHugePlayerCountWithoutPreallocation() throws IOException {
		byte[] bytes = writeEncodedState(GamePhase.IDLE, 0, 0, OptionalInt.empty(), OptionalInt.empty(), false, Integer.MAX_VALUE);

		assertThrows(UncheckedIOException.class, () -> GameStateCodec.decode(bytes));
	}

	@Test
	void decodeRejectsDuplicateSeatNumbers() throws IOException {
		byte[] bytes = writeEncodedState(
			GamePhase.DAY,
			0,
			0,
			OptionalInt.empty(),
			OptionalInt.empty(),
			false,
			2,
			new Object[][] {
				{1, "Alice", LifeState.ALIVE, SleepState.AWAKE, false, "alice"},
				{1, "Bob", LifeState.ALIVE, SleepState.AWAKE, false, "bob"}
			});

		assertThrows(UncheckedIOException.class, () -> GameStateCodec.decode(bytes));
	}

	@Test
	void decodeRejectsDuplicatePlayerReferences() throws IOException {
		byte[] bytes = writeEncodedState(
			GamePhase.DAY,
			0,
			0,
			OptionalInt.empty(),
			OptionalInt.empty(),
			false,
			2,
			new Object[][] {
				{1, "Alice", LifeState.ALIVE, SleepState.AWAKE, false, "same"},
				{2, "Bob", LifeState.ALIVE, SleepState.AWAKE, false, "same"}
			});

		assertThrows(UncheckedIOException.class, () -> GameStateCodec.decode(bytes));
	}

	private static byte[] writeEncodedState(
			GamePhase phase,
			int day,
			int night,
			OptionalInt nominatedSeat,
			OptionalInt markedSeat,
			boolean timerActive,
			int playerCount,
			Object[][] players) throws IOException {
		try (ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
				DataOutputStream output = new DataOutputStream(outputBytes)) {
			output.writeByte(1);
			output.writeUTF(phase.name());
			output.writeInt(day);
			output.writeInt(night);
			writeOptionalInt(output, nominatedSeat);
			writeOptionalInt(output, markedSeat);
			output.writeBoolean(timerActive);
			output.writeInt(playerCount);
			for (Object[] player : players) {
				output.writeInt((Integer) player[0]);
				output.writeUTF((String) player[1]);
				output.writeUTF(((LifeState) player[2]).name());
				output.writeUTF(((SleepState) player[3]).name());
				output.writeBoolean((Boolean) player[4]);
				output.writeUTF((String) player[5]);
			}
			return outputBytes.toByteArray();
		}
	}

	private static byte[] writeEncodedState(
			String phase,
			int day,
			int night,
			OptionalInt nominatedSeat,
			OptionalInt markedSeat,
			boolean timerActive,
			int playerCount,
			Object[][] players) throws IOException {
		try (ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
				DataOutputStream output = new DataOutputStream(outputBytes)) {
			output.writeByte(1);
			output.writeUTF(phase);
			output.writeInt(day);
			output.writeInt(night);
			writeOptionalInt(output, nominatedSeat);
			writeOptionalInt(output, markedSeat);
			output.writeBoolean(timerActive);
			output.writeInt(playerCount);
			for (Object[] player : players) {
				output.writeInt((Integer) player[0]);
				output.writeUTF((String) player[1]);
				output.writeUTF(((LifeState) player[2]).name());
				output.writeUTF(((SleepState) player[3]).name());
				output.writeBoolean((Boolean) player[4]);
				output.writeUTF((String) player[5]);
			}
			return outputBytes.toByteArray();
		}
	}

	private static byte[] writeEncodedState(
			GamePhase phase,
			int day,
			int night,
			OptionalInt nominatedSeat,
			OptionalInt markedSeat,
			boolean timerActive,
			int playerCount) throws IOException {
		return writeEncodedState(phase.name(), day, night, nominatedSeat, markedSeat, timerActive, playerCount, new Object[0][]);
	}

	private static byte[] writeEncodedState(
			String phase,
			int day,
			int night,
			OptionalInt nominatedSeat,
			OptionalInt markedSeat,
			boolean timerActive,
			int playerCount) throws IOException {
		return writeEncodedState(phase, day, night, nominatedSeat, markedSeat, timerActive, playerCount, new Object[0][]);
	}

	private static void writeOptionalInt(DataOutputStream output, OptionalInt optional) throws IOException {
		if (optional.isPresent()) {
			output.writeBoolean(true);
			output.writeInt(optional.getAsInt());
		} else {
			output.writeBoolean(false);
		}
	}
}
