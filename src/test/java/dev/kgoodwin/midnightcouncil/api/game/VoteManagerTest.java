package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.VoteResolved;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoteManagerTest {

	private GameEventDispatcher dispatcher;
	private GameState state;
	private VoteManager voteManager;

	@BeforeEach
	void setUp() {
		dispatcher = new GameEventDispatcher();
		state = new GameState();
		voteManager = new VoteManager(dispatcher);
	}

	private void setupGameWithPlayers() {
		state.setPhase(GamePhase.SETUP);
		state.getPlayers().register(new PlayerEntry(1, "Alice", false, PlayerReference.ofName("alice")));
		state.getPlayers().register(new PlayerEntry(2, "Bob", false, PlayerReference.ofName("bob")));
		state.getPlayers().register(new PlayerEntry(3, "Carol", false, PlayerReference.ofName("carol")));
		state.getPlayers().register(new PlayerEntry(4, "Dave", false, PlayerReference.ofName("dave")));
		state.getPlayers().register(new PlayerEntry(5, "Eve", false, PlayerReference.ofName("eve")));
		state.setPhase(GamePhase.SEATING);
		state.setPhase(GamePhase.DAY);
	}

	@Test
	void startVoteBeginsVoteRound() {
		setupGameWithPlayers();
		PlayerReference nominee = PlayerReference.ofName("alice");

		voteManager.startVote(state, nominee);

		assertTrue(voteManager.isVoteInProgress());
		assertTrue(voteManager.getNominee().isPresent());
		assertEquals(nominee, voteManager.getNominee().orElseThrow());
	}

	@Test
	void startVoteSetsEligibleVotersToNotVoted() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("alice"));

		assertEquals(VoteManager.VoteState.NOT_VOTED,
				voteManager.getVoteState(PlayerReference.ofName("bob")));
		assertEquals(VoteManager.VoteState.NOT_VOTED,
				voteManager.getVoteState(PlayerReference.ofName("carol")));
	}

	@Test
	void nomineeCanVoteOnOwnNomination() {
		setupGameWithPlayers();
		PlayerReference nominee = PlayerReference.ofName("alice");
		voteManager.startVote(state, nominee);

		assertTrue(voteManager.getVoteOrder().contains(nominee));
		assertEquals(VoteManager.VoteState.NOT_VOTED, voteManager.getVoteState(nominee));
	}

	@Test
	void castVoteRecordsYes() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("alice"));

		voteManager.castVote(PlayerReference.ofName("bob"), true);

		assertEquals(VoteManager.VoteState.VOTED_YES,
				voteManager.getVoteState(PlayerReference.ofName("bob")));
	}

	@Test
	void castVoteRecordsNo() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("alice"));

		voteManager.castVote(PlayerReference.ofName("bob"), false);

		assertEquals(VoteManager.VoteState.VOTED_NO,
				voteManager.getVoteState(PlayerReference.ofName("bob")));
	}

	@Test
	void cannotVoteTwice() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("alice"));

		voteManager.castVote(PlayerReference.ofName("bob"), true);

		assertThrows(IllegalStateException.class,
				() -> voteManager.castVote(PlayerReference.ofName("bob"), false));
	}

	@Test
	void tallyCompletesWhenAllEligibleVoted() {
		setupGameWithPlayers();
		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("alice"), true);
		voteManager.castVote(PlayerReference.ofName("bob"), true);
		voteManager.castVote(PlayerReference.ofName("carol"), false);
		voteManager.castVote(PlayerReference.ofName("dave"), true);
		voteManager.castVote(PlayerReference.ofName("eve"), true);

		assertEquals(1, events.size());
		assertFalse(voteManager.isVoteInProgress());
	}

	@Test
	void voteResolvedFiredWithCorrectYesCount() {
		setupGameWithPlayers();
		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("alice"), true);
		voteManager.castVote(PlayerReference.ofName("bob"), true);
		voteManager.castVote(PlayerReference.ofName("carol"), false);
		voteManager.castVote(PlayerReference.ofName("dave"), true);
		voteManager.castVote(PlayerReference.ofName("eve"), true);

		VoteResolved event = events.getFirst();
		assertEquals(PlayerReference.ofName("alice"), event.nominee());
		assertEquals(4, event.voteCount());
	}

	@Test
	void voteResolvedThresholdIsSimpleMajority() {
		setupGameWithPlayers();
		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("alice"), false);
		voteManager.castVote(PlayerReference.ofName("bob"), true);
		voteManager.castVote(PlayerReference.ofName("carol"), true);
		voteManager.castVote(PlayerReference.ofName("dave"), false);
		voteManager.castVote(PlayerReference.ofName("eve"), false);

		VoteResolved event = events.getFirst();
		assertEquals(3, event.threshold());
	}

	@Test
	void unanimousYesVote() {
		setupGameWithPlayers();
		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("alice"), true);
		voteManager.castVote(PlayerReference.ofName("bob"), true);
		voteManager.castVote(PlayerReference.ofName("carol"), true);
		voteManager.castVote(PlayerReference.ofName("dave"), true);
		voteManager.castVote(PlayerReference.ofName("eve"), true);

		VoteResolved event = events.getFirst();
		assertEquals(5, event.voteCount());
		assertEquals(3, event.threshold());
	}

	@Test
	void unanimousNoVote() {
		setupGameWithPlayers();
		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("alice"), false);
		voteManager.castVote(PlayerReference.ofName("bob"), false);
		voteManager.castVote(PlayerReference.ofName("carol"), false);
		voteManager.castVote(PlayerReference.ofName("dave"), false);
		voteManager.castVote(PlayerReference.ofName("eve"), false);

		VoteResolved event = events.getFirst();
		assertEquals(0, event.voteCount());
		assertEquals(3, event.threshold());
	}

	@Test
	void tieVote() {
		setupGameWithPlayers();
		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("alice"), true);
		voteManager.castVote(PlayerReference.ofName("bob"), true);
		voteManager.castVote(PlayerReference.ofName("carol"), false);
		voteManager.castVote(PlayerReference.ofName("dave"), false);
		voteManager.castVote(PlayerReference.ofName("eve"), true);

		VoteResolved event = events.getFirst();
		assertEquals(3, event.voteCount());
	}

	@Test
	void cannotVoteForDeadPlayer() {
		setupGameWithPlayers();
		state.getPlayers().getByPlayerReference(PlayerReference.ofName("bob"))
				.ifPresent(PlayerEntry::kill);

		voteManager.startVote(state, PlayerReference.ofName("alice"));

		assertThrows(IllegalArgumentException.class,
				() -> voteManager.castVote(PlayerReference.ofName("bob"), true));
	}

	@Test
	void deadPlayersExcludedFromVoteOrder() {
		setupGameWithPlayers();
		state.getPlayers().getByPlayerReference(PlayerReference.ofName("bob"))
				.ifPresent(PlayerEntry::kill);

		voteManager.startVote(state, PlayerReference.ofName("alice"));

		assertFalse(voteManager.getVoteOrder().contains(PlayerReference.ofName("bob")));
		assertEquals(4, voteManager.getVoteOrder().size());
	}

	@Test
	void cannotStartVoteWhenOneIsInProgress() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("alice"));

		assertThrows(IllegalStateException.class,
				() -> voteManager.startVote(state, PlayerReference.ofName("bob")));
	}

	@Test
	void cannotVoteWhenNoVoteIsInProgress() {
		assertThrows(IllegalStateException.class,
				() -> voteManager.castVote(PlayerReference.ofName("bob"), true));
	}

	@Test
	void resetClearsVoteState() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("bob"), true);

		voteManager.reset();

		assertFalse(voteManager.isVoteInProgress());
		assertTrue(voteManager.getNominee().isEmpty());
		assertEquals(VoteManager.VoteState.NOT_VOTED,
				voteManager.getVoteState(PlayerReference.ofName("bob")));
	}

	@Test
	void voteOrderIsAscendingSeatNumber() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("carol"));

		List<PlayerReference> order = voteManager.getVoteOrder();
		assertEquals(5, order.size());
		assertEquals(PlayerReference.ofName("alice"), order.get(0));
		assertEquals(PlayerReference.ofName("bob"), order.get(1));
		assertEquals(PlayerReference.ofName("carol"), order.get(2));
		assertEquals(PlayerReference.ofName("dave"), order.get(3));
		assertEquals(PlayerReference.ofName("eve"), order.get(4));
	}

	@Test
	void noEligibleVotersResolvesImmediately() {
		state.setPhase(GamePhase.SETUP);
		state.getPlayers().register(new PlayerEntry(1, "Alice", false, PlayerReference.ofName("alice")));
		state.getPlayers().register(new PlayerEntry(0, "Storyteller", true, PlayerReference.ofName("st")));
		state.setPhase(GamePhase.SEATING);
		state.setPhase(GamePhase.DAY);

		// Kill alice so no alive non-storyteller players remain
		state.getPlayers().getByPlayerReference(PlayerReference.ofName("alice"))
				.ifPresent(PlayerEntry::kill);

		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));

		assertEquals(1, events.size());
		assertFalse(voteManager.isVoteInProgress());
		VoteResolved event = events.getFirst();
		assertEquals(0, event.voteCount());
		assertEquals(0, event.threshold());
	}

	@Test
	void cannotVoteForNonExistentPlayer() {
		setupGameWithPlayers();
		voteManager.startVote(state, PlayerReference.ofName("alice"));

		assertThrows(IllegalArgumentException.class,
				() -> voteManager.castVote(PlayerReference.ofName("unknown"), true));
	}

	@Test
	void cannotStartVoteForNonExistentNominee() {
		setupGameWithPlayers();

		assertThrows(IllegalArgumentException.class,
				() -> voteManager.startVote(state, PlayerReference.ofName("ghost")));
	}

	@Test
	void storytellerExcludedFromVoteOrder() {
		state.setPhase(GamePhase.SETUP);
		state.getPlayers().register(new PlayerEntry(1, "Alice", false, PlayerReference.ofName("alice")));
		state.getPlayers().register(new PlayerEntry(2, "Bob", false, PlayerReference.ofName("bob")));
		state.getPlayers().register(new PlayerEntry(0, "Storyteller", true, PlayerReference.ofName("st")));
		state.setPhase(GamePhase.SEATING);
		state.setPhase(GamePhase.DAY);

		voteManager.startVote(state, PlayerReference.ofName("alice"));

		assertFalse(voteManager.getVoteOrder().contains(PlayerReference.ofName("st")));
		assertEquals(2, voteManager.getVoteOrder().size());
		assertTrue(voteManager.getVoteOrder().contains(PlayerReference.ofName("bob")));
	}

	@Test
	void canStartNewVoteAfterPreviousResolves() {
		setupGameWithPlayers();
		List<VoteResolved> events = new ArrayList<>();
		dispatcher.registerListener(VoteResolved.class, events::add);

		voteManager.startVote(state, PlayerReference.ofName("alice"));
		voteManager.castVote(PlayerReference.ofName("alice"), true);
		voteManager.castVote(PlayerReference.ofName("bob"), true);
		voteManager.castVote(PlayerReference.ofName("carol"), true);
		voteManager.castVote(PlayerReference.ofName("dave"), false);
		voteManager.castVote(PlayerReference.ofName("eve"), false);

		assertEquals(1, events.size());
		assertFalse(voteManager.isVoteInProgress());

		voteManager.startVote(state, PlayerReference.ofName("bob"));
		assertTrue(voteManager.isVoteInProgress());
		assertEquals(PlayerReference.ofName("bob"), voteManager.getNominee().orElseThrow());
	}
}
