package dev.kgoodwin.midnightcouncil.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.PersistenceManager;
import dev.kgoodwin.midnightcouncil.api.game.PlayerEntry;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceRoundTripTest {

	private final PersistenceManager persistenceManager = new PersistenceManager();

	@TempDir
	Path tempDir;

	@Test
	void saveAndLoadRestoresIntegratedVotingState() throws IOException {
		GameSession session = new GameSession();
		GameState original = session.getState();
		NominationManager nominationManager = new NominationManager(session.getDispatcher());
		VoteManager voteManager = new VoteManager(session.getDispatcher());

		PlayerReference alice = PlayerReference.ofName("alice");
		PlayerReference bob = PlayerReference.ofName("bob");
		PlayerReference carol = PlayerReference.ofName("carol");
		PlayerReference dave = PlayerReference.ofName("dave");
		PlayerReference eve = PlayerReference.ofName("eve");

		prepareFivePlayerGame(session, List.of(alice, bob, carol, dave, eve));
		session.transitionPhase(GamePhase.NOMINATION);
		nominationManager.nominate(original, carol, dave);
		session.transitionPhase(GamePhase.VOTING);
		voteManager.startVote(original, dave);
		voteManager.castVote(alice, true);
		voteManager.castVote(bob, true);
		voteManager.castVote(carol, false);
		voteManager.castVote(dave, true);
		voteManager.castVote(eve, false);
		original.setTimerActive(true);

		Path file = tempDir.resolve("voting-state.json");
		persistenceManager.saveToFile(original, file);

		GameState restored = persistenceManager.loadFromFile(file);

		assertGameStateEquals(original, restored);
		assertEquals(GamePhase.VOTING, restored.getPhase());
		assertEquals(4, restored.getNominatedSeat().orElseThrow());
		assertTrue(restored.isTimerActive());
	}

	@Test
	void saveAndLoadPreservesAllDeadGameOverState() throws IOException {
		GameSession session = new GameSession();
		GameState original = session.getState();
		ExecutionManager executionManager = new ExecutionManager(session.getDispatcher());

		PlayerReference alice = PlayerReference.ofName("alice");
		PlayerReference bob = PlayerReference.ofName("bob");
		PlayerReference carol = PlayerReference.ofName("carol");
		PlayerReference dave = PlayerReference.ofName("dave");
		PlayerReference eve = PlayerReference.ofName("eve");

		prepareFivePlayerGame(session, List.of(alice, bob, carol, dave, eve));
		session.setPlayerAlive(alice, false);
		session.setPlayerAlive(bob, false);
		session.setPlayerAlive(carol, false);
		session.setPlayerAlive(dave, false);
		session.transitionPhase(GamePhase.NOMINATION);
		session.transitionPhase(GamePhase.VOTING);
		session.transitionPhase(GamePhase.EXECUTION);
		executionManager.execute(original, eve);
		session.startNight();
		session.endGame();

		Path file = tempDir.resolve("all-dead.json");
		persistenceManager.saveToFile(original, file);

		GameState restored = persistenceManager.loadFromFile(file);

		assertGameStateEquals(original, restored);
		assertEquals(GamePhase.GAME_OVER, restored.getPhase());
		assertEquals(0, restored.getAliveCount());
		assertTrue(restored.getPlayers().getPlayers().stream().noneMatch(PlayerEntry::isAlive));
		assertEquals(5, restored.getMarkedSeat().orElseThrow());
	}

	private static void prepareFivePlayerGame(GameSession session, List<PlayerReference> players) {
		session.startSetup();
		for (int i = 0; i < players.size(); i++) {
			PlayerReference player = players.get(i);
			session.addPlayer(player, player.value(), i + 1);
		}
		session.startSeating();
		session.startGame();
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
			PlayerEntry actualEntry = actual.getPlayers().getBySeatNumber(expectedEntry.getSeatNumber()).orElseThrow();
			assertEquals(expectedEntry.getDisplayName(), actualEntry.getDisplayName());
			assertEquals(expectedEntry.getLifeState(), actualEntry.getLifeState());
			assertEquals(expectedEntry.getSleepState(), actualEntry.getSleepState());
			assertEquals(expectedEntry.isStoryteller(), actualEntry.isStoryteller());
			assertEquals(expectedEntry.getPlayerReference(), actualEntry.getPlayerReference());
		}
	}
}
