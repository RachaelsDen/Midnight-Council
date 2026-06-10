package dev.kgoodwin.midnightcouncil.client.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.LifeState;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.SleepState;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState.GameStateSnapshot;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState.PlayerSnapshot;
import dev.kgoodwin.midnightcouncil.client.state.ClientGameState.VoiceHandshakeState;

import org.junit.jupiter.api.Test;

import java.util.List;

class ClientGameStateTest {

	@Test
	void initialStateIsEmpty() {
		ClientGameState state = new ClientGameState();

		assertTrue(state.current().isEmpty());
		assertTrue(state.localPlayerName().isEmpty());
		assertTrue(state.voiceHandshake().isEmpty());
	}

	@Test
	void updateStoresAndRetrievesSnapshot() {
		ClientGameState state = new ClientGameState();
		PlayerSnapshot player = new PlayerSnapshot("June", 1, LifeState.ALIVE, SleepState.AWAKE);
		GameStateSnapshot snapshot = new GameStateSnapshot(GamePhase.DAY, 2, 1, 3, 4, true, List.of(player));

		state.update(snapshot);

		assertEquals(snapshot, state.current().orElseThrow());
	}

	@Test
	void clearRemovesStoredState() {
		ClientGameState state = new ClientGameState();
		state.update(new GameStateSnapshot(GamePhase.DAY, 1, 0, null, null, false, List.of()));
		state.setLocalPlayer("Storyteller");
		state.updateVoiceHandshake(new VoiceHandshakeState("localhost", 24454, new byte[] {1, 2, 3}));

		state.clear();

		assertTrue(state.current().isEmpty());
		assertTrue(state.localPlayerName().isEmpty());
		assertTrue(state.voiceHandshake().isEmpty());
	}

	@Test
	void localPlayerNameRoundTrips() {
		ClientGameState state = new ClientGameState();

		state.setLocalPlayer("June");

		assertEquals("June", state.localPlayerName().orElseThrow());
	}

	@Test
	void playerSnapshotRecordConstructs() {
		PlayerSnapshot snapshot = new PlayerSnapshot("June", 2, LifeState.DEAD, SleepState.SLEEPING);

		assertEquals("June", snapshot.name());
		assertEquals(2, snapshot.seatIndex());
		assertEquals(LifeState.DEAD, snapshot.lifeState());
		assertEquals(SleepState.SLEEPING, snapshot.sleepState());
	}

	@Test
	void gameStateSnapshotRecordConstructs() {
		PlayerSnapshot player = new PlayerSnapshot("June", 2, LifeState.ALIVE, SleepState.AWAKE);
		GameStateSnapshot snapshot = new GameStateSnapshot(GamePhase.NIGHT, 3, 2, 5, 6, true, List.of(player));

		assertEquals(GamePhase.NIGHT, snapshot.phase());
		assertEquals(3, snapshot.dayCount());
		assertEquals(2, snapshot.nightCount());
		assertEquals(5, snapshot.nominatedSeat());
		assertEquals(6, snapshot.markedSeat());
		assertTrue(snapshot.timerActive());
		assertEquals(List.of(player), snapshot.players());
	}

	@Test
	void gameStateSnapshotCanBeBuiltFromApiState() {
		GameState gameState = new GameState();
		gameState.getPlayers().register(new PlayerEntry(1, "June", false, PlayerReference.ofName("june")));
		gameState.setPhase(GamePhase.SETUP);
		gameState.setPhase(GamePhase.SEATING);
		gameState.setPhase(GamePhase.DAY);
		gameState.setNominatedSeat(4);
		gameState.setMarkedSeat(5);
		gameState.setTimerActive(true);
		gameState.setDayCount(2);
		gameState.setNightCount(1);

		GameStateSnapshot snapshot = GameStateSnapshot.from(gameState);

		assertEquals(GamePhase.DAY, snapshot.phase());
		assertEquals(2, snapshot.dayCount());
		assertEquals(1, snapshot.nightCount());
		assertEquals(4, snapshot.nominatedSeat());
		assertEquals(5, snapshot.markedSeat());
		assertTrue(snapshot.timerActive());
		assertEquals(1, snapshot.players().size());
		assertEquals("June", snapshot.players().getFirst().name());
	}

	@Test
	void voiceHandshakeStateDefensivelyCopiesToken() {
		byte[] token = new byte[] {9, 8, 7};
		VoiceHandshakeState handshake = new VoiceHandshakeState("localhost", 24454, token);
		byte[] returnedToken = handshake.connectToken();

		token[0] = 0;
		returnedToken[1] = 0;

		assertFalse(token[0] == handshake.connectToken()[0]);
		assertArrayEquals(new byte[] {9, 8, 7}, handshake.connectToken());
	}
}
