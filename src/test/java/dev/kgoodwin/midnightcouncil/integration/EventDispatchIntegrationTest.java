package dev.kgoodwin.midnightcouncil.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.ExecutionResolved;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;
import dev.kgoodwin.midnightcouncil.api.event.VoteResolved;
import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EventDispatchIntegrationTest {

	@Test
	void dispatchesIntegratedManagerEventsForMaximumPlayerGame() {
		GameSession session = new GameSession();
		GameState state = session.getState();
		NominationManager nominationManager = new NominationManager(session.getDispatcher());
		VoteManager voteManager = new VoteManager(session.getDispatcher());
		ExecutionManager executionManager = new ExecutionManager(session.getDispatcher());

		List<String> eventLog = new ArrayList<>();
		AtomicInteger listenerFailures = new AtomicInteger();

		session.getDispatcher().registerListener(PhaseChanged.class,
				event -> eventLog.add("phase:" + event.oldPhase() + "->" + event.newPhase()));
		session.getDispatcher().registerListener(PlayerStateChanged.class,
				event -> eventLog.add("player:" + event.player().value() + ':' + event.changeType()));
		session.getDispatcher().registerListener(NominationOpened.class, event -> {
			listenerFailures.incrementAndGet();
			throw new IllegalStateException("listener isolation check");
		});
		session.getDispatcher().registerListener(NominationOpened.class,
				event -> eventLog.add("nomination:" + event.nominator().value() + "->" + event.nominee().value()));
		session.getDispatcher().registerListener(VoteResolved.class,
				event -> eventLog.add("vote:" + event.nominee().value() + ':' + event.voteCount() + '/' + event.threshold()));
		session.getDispatcher().registerListener(ExecutionResolved.class,
				event -> eventLog.add("execution:" + event.executed().value()));

		session.startSetup();
		List<PlayerReference> players = new ArrayList<>();
		for (int seat = 1; seat <= 12; seat++) {
			PlayerReference player = PlayerReference.ofName("player" + seat);
			players.add(player);
			session.addPlayer(player, "Player " + seat, seat);
		}

		session.startSeating();
		session.startGame();
		session.transitionPhase(GamePhase.NOMINATION);
		PlayerReference nominator = players.getFirst();
		PlayerReference nominee = players.getLast();
		assertTrue(nominationManager.canNominate(state, nominator, nominee));
		nominationManager.nominate(state, nominator, nominee);

		session.transitionPhase(GamePhase.VOTING);
		voteManager.startVote(state, nominee);
		assertEquals(players, voteManager.getVoteOrder());
		for (int i = 0; i < players.size(); i++) {
			voteManager.castVote(players.get(i), i < 7);
		}

		session.transitionPhase(GamePhase.EXECUTION);
		executionManager.execute(state, nominee);

		assertEquals(1, listenerFailures.get());
		assertTrue(eventLog.contains("nomination:player1->player12"));
		assertTrue(eventLog.contains("vote:player12:7/7"));
		assertTrue(eventLog.contains("execution:player12"));
		assertEquals(12, eventLog.stream().filter(entry -> entry.startsWith("player:") && entry.endsWith(":joined")).count());
		assertFalse(state.getPlayers().getByPlayerReference(nominee).orElseThrow().isAlive());
		assertEquals(11, state.getAliveCount());
		assertEquals(12, state.getMarkedSeat().orElseThrow());
	}
}
