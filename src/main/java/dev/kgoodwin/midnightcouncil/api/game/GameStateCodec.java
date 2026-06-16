package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class GameStateCodec {

	private static final byte FORMAT_VERSION = 2;

	private GameStateCodec() {
	}

	public static byte[] encode(GameState state) {
		Objects.requireNonNull(state, "state");
		GameStateSnapshot snapshot = GameStateSnapshot.from(state);
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes);
			output.writeByte(FORMAT_VERSION);
			output.writeUTF(snapshot.phase().name());
			output.writeInt(snapshot.dayCount());
			output.writeInt(snapshot.nightCount());
			writeOptionalInt(output, snapshot.nominatedSeat());
			writeOptionalInt(output, snapshot.markedSeat());
			output.writeBoolean(snapshot.timerActive());
			output.writeInt(snapshot.players().size());
			for (GameStateSnapshot.PlayerSnapshot player : snapshot.players()) {
				output.writeInt(player.seatNumber());
				output.writeUTF(player.displayName());
				output.writeUTF(player.lifeState().name());
				output.writeBoolean(player.storyteller());
				output.writeUTF(player.playerReference());
			}
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static GameStateSnapshot decode(byte[] bytes) {
		Objects.requireNonNull(bytes, "bytes");
		try {
			DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
			byte formatVersion = input.readByte();
			if (formatVersion != FORMAT_VERSION) {
				throw new IOException("Unsupported format version: " + formatVersion);
			}
			String phaseName = input.readUTF();
			GamePhase phase;
			try {
				phase = GamePhase.valueOf(phaseName);
			} catch (IllegalArgumentException e) {
				throw new IOException("Invalid phase: " + phaseName, e);
			}
			int dayCount = input.readInt();
			int nightCount = input.readInt();
			OptionalInt nominatedSeat = readOptionalInt(input);
			OptionalInt markedSeat = readOptionalInt(input);
			if (dayCount < 0) {
				throw new IOException("dayCount cannot be negative: " + dayCount);
			}
			if (nightCount < 0) {
				throw new IOException("nightCount cannot be negative: " + nightCount);
			}
			if (nominatedSeat.isPresent() && nominatedSeat.getAsInt() < 0) {
				throw new IOException("nominatedSeat cannot be negative: " + nominatedSeat.getAsInt());
			}
			if (markedSeat.isPresent() && markedSeat.getAsInt() < 0) {
				throw new IOException("markedSeat cannot be negative: " + markedSeat.getAsInt());
			}
			boolean timerActive = input.readBoolean();
			int playerCount = input.readInt();
			if (playerCount < 0) {
				throw new IOException("playerCount cannot be negative: " + playerCount);
			}
			List<GameStateSnapshot.PlayerSnapshot> players = new ArrayList<>();
			Set<Integer> seenSeats = new HashSet<>();
			Set<String> seenReferences = new HashSet<>();
			for (int i = 0; i < playerCount; i++) {
				GameStateSnapshot.PlayerSnapshot player = decodePlayer(input);
				if (!seenSeats.add(player.seatNumber())) {
					throw new IOException("Duplicate seat number: " + player.seatNumber());
				}
				if (!seenReferences.add(player.playerReference())) {
					throw new IOException("Duplicate player reference: " + player.playerReference());
				}
				players.add(player);
			}
			if (input.available() != 0) {
				throw new IOException("Unexpected trailing bytes: " + input.available());
			}
			return new GameStateSnapshot(
				phase,
				dayCount,
				nightCount,
				nominatedSeat,
				markedSeat,
				timerActive,
				players);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void writeOptionalInt(DataOutputStream output, OptionalInt optional) throws IOException {
		boolean hasValue = optional.isPresent();
		output.writeBoolean(hasValue);
		if (hasValue) {
			output.writeInt(optional.getAsInt());
		}
	}

	private static OptionalInt readOptionalInt(DataInputStream input) throws IOException {
		if (input.readBoolean()) {
			return OptionalInt.of(input.readInt());
		}
		return OptionalInt.empty();
	}

	private static GameStateSnapshot.PlayerSnapshot decodePlayer(DataInputStream input) throws IOException {
		int seatNumber = input.readInt();
		if (seatNumber < 0) {
			throw new IOException("seatNumber cannot be negative: " + seatNumber);
		}
		String displayName = input.readUTF();
		if (displayName.isBlank()) {
			throw new IOException("displayName cannot be blank");
		}
		LifeState lifeState = decodeLifeState(input);
		boolean storyteller = input.readBoolean();
		String playerReference = input.readUTF();
		if (playerReference.isBlank()) {
			throw new IOException("playerReference cannot be blank");
		}
		return new GameStateSnapshot.PlayerSnapshot(
				seatNumber,
				displayName,
				lifeState,
				SleepState.AWAKE,
				storyteller,
				playerReference);
	}

	private static LifeState decodeLifeState(DataInputStream input) throws IOException {
		try {
			return LifeState.valueOf(input.readUTF());
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid life state: " + e.getMessage(), e);
		}
	}

}
