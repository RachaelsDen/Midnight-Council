package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceManagerTest {

	private final PersistenceManager persistenceManager = new PersistenceManager();

	@TempDir
	Path tempDir;

	private static GameState createPopulatedState() {
		GameState state = GameState.reconstruct(GamePhase.DAY, 2, 1, 3, null, true);
		state.getPlayers().register(new PlayerEntry(1, "Alice", LifeState.ALIVE, SleepState.AWAKE, false, PlayerReference.ofName("alice")));
		state.getPlayers().register(new PlayerEntry(2, "Bob", LifeState.DEAD, SleepState.AWAKE, false, PlayerReference.ofName("bob")));
		state.getPlayers().register(new PlayerEntry(3, "Storyteller", LifeState.ALIVE, SleepState.AWAKE, true, PlayerReference.ofName("storyteller")));
		return state;
	}

	private static void assertGameStateEquals(GameState expected, GameState actual) {
		assertEquals(expected.getPhase(), actual.getPhase());
		assertEquals(expected.getDayCount(), actual.getDayCount());
		assertEquals(expected.getNightCount(), actual.getNightCount());
		assertEquals(expected.getNominatedSeat(), actual.getNominatedSeat());
		assertEquals(expected.getMarkedSeat(), actual.getMarkedSeat());
		assertEquals(expected.isTimerActive(), actual.isTimerActive());

		Collection<PlayerEntry> expectedPlayers = expected.getPlayers().getPlayers();
		Collection<PlayerEntry> actualPlayers = actual.getPlayers().getPlayers();
		assertEquals(expectedPlayers.size(), actualPlayers.size());

		for (PlayerEntry expectedEntry : expectedPlayers) {
			PlayerEntry actualEntry = actual.getPlayers()
					.getBySeatNumber(expectedEntry.getSeatNumber())
					.orElseThrow(() -> new AssertionError("Missing player at seat " + expectedEntry.getSeatNumber()));
			assertPlayerEntryEquals(expectedEntry, actualEntry);
		}
	}

	private static void assertPlayerEntryEquals(PlayerEntry expected, PlayerEntry actual) {
		assertEquals(expected.getSeatNumber(), actual.getSeatNumber());
		assertEquals(expected.getDisplayName(), actual.getDisplayName());
		assertEquals(expected.getLifeState(), actual.getLifeState());
		assertEquals(expected.getSleepState(), actual.getSleepState());
		assertEquals(expected.isStoryteller(), actual.isStoryteller());
		assertEquals(expected.getPlayerReference().value(), actual.getPlayerReference().value());
	}

	@Test
	void roundTripDefaultState() throws IOException {
		GameState original = new GameState();
		Path file = tempDir.resolve("default.json");

		persistenceManager.saveToFile(original, file);
		GameState loaded = persistenceManager.loadFromFile(file);

		assertGameStateEquals(original, loaded);
	}

	@Test
	void roundTripPopulatedState() throws IOException {
		GameState original = createPopulatedState();
		Path file = tempDir.resolve("populated.json");

		persistenceManager.saveToFile(original, file);
		GameState loaded = persistenceManager.loadFromFile(file);

		assertGameStateEquals(original, loaded);
	}

	@Test
	void roundTripAllPhases() throws IOException {
		GamePhase[] phases = GamePhase.values();
		for (GamePhase phase : phases) {
			GameState original = GameState.reconstruct(phase, 5, 3, null, null, false);
			original.getPlayers().register(new PlayerEntry(1, "Player1", false, PlayerReference.ofName("p1")));
			Path file = tempDir.resolve("phase-" + phase.name() + ".json");

			persistenceManager.saveToFile(original, file);
			GameState loaded = persistenceManager.loadFromFile(file);

			assertEquals(phase, loaded.getPhase(), "Phase mismatch for " + phase);
			assertGameStateEquals(original, loaded);
		}
	}

	@Test
	void roundTripWithDeadAndSleepingPlayers() throws IOException {
		GameState original = GameState.reconstruct(GamePhase.NIGHT, 1, 1, null, null, false);
		original.getPlayers().register(new PlayerEntry(1, "Alive_Awake", LifeState.ALIVE, SleepState.AWAKE, false, PlayerReference.ofName("aa")));
		original.getPlayers().register(new PlayerEntry(2, "Dead_Awake", LifeState.DEAD, SleepState.AWAKE, false, PlayerReference.ofName("da")));
		original.getPlayers().register(new PlayerEntry(3, "Alive_Sleeping", LifeState.ALIVE, SleepState.SLEEPING, false, PlayerReference.ofName("as")));
		original.getPlayers().register(new PlayerEntry(4, "Dead_Sleeping", LifeState.DEAD, SleepState.SLEEPING, false, PlayerReference.ofName("ds")));

		Path file = tempDir.resolve("dead-sleeping.json");
		persistenceManager.saveToFile(original, file);
		GameState loaded = persistenceManager.loadFromFile(file);

		assertGameStateEquals(original, loaded);
	}

	@Test
	void roundTripEmptyPlayerList() throws IOException {
		GameState original = GameState.reconstruct(GamePhase.SETUP, 0, 0, null, null, false);
		Path file = tempDir.resolve("empty-players.json");

		persistenceManager.saveToFile(original, file);
		GameState loaded = persistenceManager.loadFromFile(file);

		assertTrue(loaded.getPlayers().getPlayers().isEmpty());
		assertGameStateEquals(original, loaded);
	}

	@Test
	void versionFieldPresentInJson() throws IOException {
		GameState state = new GameState();
		Path file = tempDir.resolve("version-check.json");

		persistenceManager.saveToFile(state, file);
		String json = Files.readString(file, StandardCharsets.UTF_8);

		assertTrue(json.contains("\"version\": 1"), "JSON must contain version field");
	}

	@Test
	void phaseFieldPresentInJson() throws IOException {
		GameState state = GameState.reconstruct(GamePhase.VOTING, 0, 0, null, null, false);
		Path file = tempDir.resolve("phase-check.json");

		persistenceManager.saveToFile(state, file);
		String json = Files.readString(file, StandardCharsets.UTF_8);

		assertTrue(json.contains("\"phase\": \"VOTING\""), "JSON must contain phase field with correct value");
	}

	@Test
	void loadMissingFileThrows() {
		Path missing = tempDir.resolve("nonexistent.json");
		assertThrows(NoSuchFileException.class, () -> persistenceManager.loadFromFile(missing));
	}

	@Test
	void loadCorruptJsonThrows() throws IOException {
		Path file = tempDir.resolve("corrupt.json");
		Files.writeString(file, "{{invalid json!!!", StandardCharsets.UTF_8);

		assertThrows(IOException.class, () -> persistenceManager.loadFromFile(file));
	}

	@Test
	void loadUnsupportedVersionThrows() throws IOException {
		String json = """
				{
				  "version": 999,
				  "phase": "IDLE",
				  "dayCount": 0,
				  "nightCount": 0,
				  "nominatedSeat": null,
				  "markedSeat": null,
				  "timerActive": false,
				  "players": []
				}
				""";
		Path file = tempDir.resolve("bad-version.json");
		Files.writeString(file, json, StandardCharsets.UTF_8);

		assertThrows(IOException.class, () -> persistenceManager.loadFromFile(file));
	}

	@Test
	void roundTripWithNominatedAndMarkedSeats() throws IOException {
		GameState original = GameState.reconstruct(GamePhase.VOTING, 3, 2, 5, 7, true);
		original.getPlayers().register(new PlayerEntry(1, "P1", false, PlayerReference.ofName("p1")));
		original.getPlayers().register(new PlayerEntry(5, "P5", false, PlayerReference.ofName("p5")));
		original.getPlayers().register(new PlayerEntry(7, "P7", false, PlayerReference.ofName("p7")));

		Path file = tempDir.resolve("seats.json");
		persistenceManager.saveToFile(original, file);
		GameState loaded = persistenceManager.loadFromFile(file);

		assertGameStateEquals(original, loaded);
		assertEquals(5, loaded.getNominatedSeat().orElseThrow());
		assertEquals(7, loaded.getMarkedSeat().orElseThrow());
		assertTrue(loaded.isTimerActive());
	}

	@Test
	void roundTripDisplayNameWithSpecialCharacters() throws IOException {
		GameState original = GameState.reconstruct(GamePhase.IDLE, 0, 0, null, null, false);
		original.getPlayers().register(new PlayerEntry(1, "Player \"The Great\"", false, PlayerReference.ofName("pg")));
		original.getPlayers().register(new PlayerEntry(2, "Line\\Break", false, PlayerReference.ofName("lb")));
		original.getPlayers().register(new PlayerEntry(3, "Tab\there", false, PlayerReference.ofName("th")));

		Path file = tempDir.resolve("special-chars.json");
		persistenceManager.saveToFile(original, file);
		GameState loaded = persistenceManager.loadFromFile(file);

		assertGameStateEquals(original, loaded);
	}

	@Test
	void saveCreatesParentDirectories() throws IOException {
		GameState state = new GameState();
		Path nested = tempDir.resolve("a").resolve("b").resolve("deep.json");

		persistenceManager.saveToFile(state, nested);
		assertTrue(Files.exists(nested));

		GameState loaded = persistenceManager.loadFromFile(nested);
		assertGameStateEquals(state, loaded);
	}

	@Test
	void nullSeatsSerializeCorrectly() throws IOException {
		GameState original = new GameState();
		Path file = tempDir.resolve("null-seats.json");

		persistenceManager.saveToFile(original, file);
		String json = Files.readString(file, StandardCharsets.UTF_8);

		assertTrue(json.contains("\"nominatedSeat\": null"));
		assertTrue(json.contains("\"markedSeat\": null"));
		assertFalse(json.contains("\"nominatedSeat\": 0"));
		assertFalse(json.contains("\"markedSeat\": 0"));
	}
}
