package dev.kgoodwin.midnightcouncil.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.ConfigAdapter;
import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.SchedulerAdapter;
import dev.kgoodwin.midnightcouncil.api.event.ExecutionResolved;
import dev.kgoodwin.midnightcouncil.api.event.NominationOpened;
import dev.kgoodwin.midnightcouncil.api.event.PhaseChanged;
import dev.kgoodwin.midnightcouncil.api.event.PlayerStateChanged;
import dev.kgoodwin.midnightcouncil.api.event.TimerExpired;
import dev.kgoodwin.midnightcouncil.api.event.VoteResolved;
import dev.kgoodwin.midnightcouncil.api.game.ExecutionManager;
import dev.kgoodwin.midnightcouncil.api.game.GameSession;
import dev.kgoodwin.midnightcouncil.api.game.GameState;
import dev.kgoodwin.midnightcouncil.api.game.NominationManager;
import dev.kgoodwin.midnightcouncil.api.game.TimerManager;
import dev.kgoodwin.midnightcouncil.api.game.VoteManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class FullGameCycleTest {

	@Test
	void fullGameCycleRunsThroughCommandLikeApiSequence() {
		GameSession session = new GameSession();
		GameState state = session.getState();
		NominationManager nominationManager = new NominationManager(session.getDispatcher());
		VoteManager voteManager = new VoteManager(session.getDispatcher());
		ExecutionManager executionManager = new ExecutionManager(session.getDispatcher());
		FakeScheduler scheduler = new FakeScheduler();
		FakeConfig config = new FakeConfig();
		config.set("discussionTimerSeconds", 15L);
		config.set("nominationTimerSeconds", 10L);
		TimerManager timerManager = new TimerManager(scheduler, config, session.getDispatcher());

		List<PhaseChanged> phaseEvents = new ArrayList<>();
		List<PlayerStateChanged> playerStateEvents = new ArrayList<>();
		List<NominationOpened> nominationEvents = new ArrayList<>();
		List<VoteResolved> voteEvents = new ArrayList<>();
		List<ExecutionResolved> executionEvents = new ArrayList<>();
		List<TimerExpired> timerEvents = new ArrayList<>();

		session.getDispatcher().registerListener(PhaseChanged.class, phaseEvents::add);
		session.getDispatcher().registerListener(PlayerStateChanged.class, playerStateEvents::add);
		session.getDispatcher().registerListener(NominationOpened.class, nominationEvents::add);
		session.getDispatcher().registerListener(VoteResolved.class, voteEvents::add);
		session.getDispatcher().registerListener(ExecutionResolved.class, executionEvents::add);
		session.getDispatcher().registerListener(TimerExpired.class, timerEvents::add);

		PlayerReference alice = PlayerReference.ofName("alice");
		PlayerReference bob = PlayerReference.ofName("bob");
		PlayerReference carol = PlayerReference.ofName("carol");
		PlayerReference dave = PlayerReference.ofName("dave");
		PlayerReference eve = PlayerReference.ofName("eve");
		List<PlayerReference> players = List.of(alice, bob, carol, dave, eve);

		session.startSetup();
		registerPlayer(session, alice, "Alice", 1);
		registerPlayer(session, bob, "Bob", 2);
		registerPlayer(session, carol, "Carol", 3);
		registerPlayer(session, dave, "Dave", 4);
		registerPlayer(session, eve, "Eve", 5);

		session.startSeating();
		session.startGame();
		assertEquals(GamePhase.DAY, state.getPhase());
		assertEquals(1, state.getDayCount());
		assertEquals(5, state.getAliveCount());
		session.setPlayerAlive(carol, false);
		session.setPlayerAlive(carol, true);

		timerManager.startDiscussionTimer();
		assertTrue(timerManager.isTimerRunning());
		scheduler.runDelayedTask();
		assertFalse(timerManager.isTimerRunning());

		session.transitionPhase(GamePhase.NOMINATION);
		timerManager.startNominationTimer();
		nominationManager.nominate(state, alice, bob);
		assertEquals(2, state.getNominatedSeat().orElseThrow());
		scheduler.runDelayedTask();

		session.transitionPhase(GamePhase.VOTING);
		voteManager.startVote(state, bob);
		assertEquals(players, voteManager.getVoteOrder());
		voteManager.castVote(alice, true);
		voteManager.castVote(bob, true);
		voteManager.castVote(carol, false);
		voteManager.castVote(dave, true);
		voteManager.castVote(eve, false);

		session.transitionPhase(GamePhase.EXECUTION);
		executionManager.execute(state, bob);
		assertFalse(state.getPlayers().getByPlayerReference(bob).orElseThrow().isAlive());
		assertEquals(4, state.getAliveCount());
		assertEquals(2, state.getMarkedSeat().orElseThrow());

		session.startNight();
		assertEquals(GamePhase.NIGHT, state.getPhase());
		assertEquals(1, state.getNightCount());
		for (PlayerReference player : players) {
			session.setPlayerAsleep(player, true);
		}

		session.transitionPhase(GamePhase.DAY);
		nominationManager.resetForNewDay(state);
		for (PlayerReference player : players) {
			session.setPlayerAsleep(player, false);
		}

		session.endGame();

		assertEquals(GamePhase.GAME_OVER, state.getPhase());
		assertEquals(List.of(
				GamePhase.SETUP,
				GamePhase.SEATING,
				GamePhase.DAY,
				GamePhase.NOMINATION,
				GamePhase.VOTING,
				GamePhase.EXECUTION,
				GamePhase.NIGHT,
				GamePhase.DAY,
				GamePhase.GAME_OVER), phaseEvents.stream().map(PhaseChanged::newPhase).toList());
		assertEquals(5, playerStateEvents.stream().filter(event -> event.changeType().equals("joined")).count());
		assertEquals(1, playerStateEvents.stream().filter(event -> event.changeType().equals("killed")).count());
		assertEquals(1, playerStateEvents.stream().filter(event -> event.changeType().equals("revived")).count());
		assertEquals(5, playerStateEvents.stream().filter(event -> event.changeType().equals("asleep")).count());
		assertEquals(5, playerStateEvents.stream().filter(event -> event.changeType().equals("awake")).count());
		assertEquals(1, nominationEvents.size());
		assertEquals(alice, nominationEvents.getFirst().nominator());
		assertEquals(bob, nominationEvents.getFirst().nominee());
		assertEquals(1, voteEvents.size());
		assertEquals(bob, voteEvents.getFirst().nominee());
		assertEquals(3, voteEvents.getFirst().voteCount());
		assertEquals(3, voteEvents.getFirst().threshold());
		assertEquals(1, executionEvents.size());
		assertEquals(bob, executionEvents.getFirst().executed());
		assertEquals(2, timerEvents.size());
		assertTrue(state.getNominatedSeat().isEmpty());
	}

	private static void registerPlayer(GameSession session, PlayerReference ref, String displayName, int seatNumber) {
		session.addPlayer(ref, displayName, seatNumber);
	}

	private static final class FakeScheduler implements SchedulerAdapter {

		private final List<Runnable> delayedTasks = new ArrayList<>();

		@Override
		public void runNextTick(Runnable task) {
		}

		@Override
		public void runAfterDelay(long delayTicks, Runnable task) {
			delayedTasks.add(task);
		}

		void runDelayedTask() {
			Runnable task = delayedTasks.removeFirst();
			task.run();
		}
	}

	private static final class FakeConfig implements ConfigAdapter {

		private final Map<String, Object> values = new HashMap<>();

		@Override
		public void load() {
		}

		@Override
		public void save() {
		}

		@Override
		public <T> Optional<T> get(String key, Class<T> valueType) {
			Object value = values.get(key);
			if (value == null) {
				return Optional.empty();
			}
			return Optional.of(valueType.cast(value));
		}

		@Override
		public void set(String key, Object value) {
			values.put(key, value);
		}
	}
}
