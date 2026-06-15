package dev.kgoodwin.midnightcouncil.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.ConfigAdapter;
import dev.kgoodwin.midnightcouncil.api.SchedulerAdapter;
import dev.kgoodwin.midnightcouncil.api.event.GameEvent;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.TimerExpired;
import dev.kgoodwin.midnightcouncil.api.event.TimerStarted;
import dev.kgoodwin.midnightcouncil.api.event.TimerStopped;
import dev.kgoodwin.midnightcouncil.api.game.TimerManager.TimerType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimerManagerTest {

	private FakeScheduler scheduler;
	private FakeConfig config;
	private GameEventDispatcher dispatcher;
	private List<GameEvent> dispatchedEvents;
	private TimerManager timerManager;
	private GameState state;

	@BeforeEach
	void setUp() {
		scheduler = new FakeScheduler();
		config = new FakeConfig();
		dispatcher = new GameEventDispatcher();
		dispatchedEvents = new ArrayList<>();
		state = new GameState();
		dispatcher.registerListener(TimerStarted.class, dispatchedEvents::add);
		dispatcher.registerListener(TimerStopped.class, dispatchedEvents::add);
		dispatcher.registerListener(TimerExpired.class, dispatchedEvents::add);
		timerManager = new TimerManager(scheduler, config, dispatcher);
	}

	@Test
	void startsWithNoTimerRunning() {
		assertFalse(timerManager.isTimerRunning());
		assertEquals(0, timerManager.getRemainingSeconds());
		assertEquals(TimerType.NONE, timerManager.getTimerType());
	}

	@Test
	void startDiscussionTimerUsesConfiguredDuration() {
		config.set("discussionTimerSeconds", 120L);
		timerManager.startDiscussionTimer(state);

		assertTrue(timerManager.isTimerRunning());
		assertTrue(state.isTimerActive());
		assertEquals(TimerType.DISCUSSION, timerManager.getTimerType());
		assertEquals(120L, timerManager.getRemainingSeconds());
		TimerStarted event = (TimerStarted) dispatchedEvents.getFirst();
		assertEquals(TimerType.DISCUSSION, event.timerType());
		assertEquals(120L, event.durationSeconds());
	}

	@Test
	void startDiscussionTimerUsesDefaultWhenNotConfigured() {
		timerManager.startDiscussionTimer(state);

		assertTrue(timerManager.isTimerRunning());
		assertEquals(180, timerManager.getRemainingSeconds());
	}

	@Test
	void startNominationTimerUsesConfiguredDuration() {
		config.set("nominationTimerSeconds", 45L);
		timerManager.startNominationTimer(state);

		assertTrue(timerManager.isTimerRunning());
		assertTrue(state.isTimerActive());
		assertEquals(TimerType.NOMINATION, timerManager.getTimerType());
		assertEquals(45L, timerManager.getRemainingSeconds());
	}

	@Test
	void startNominationTimerUsesDefaultWhenNotConfigured() {
		timerManager.startNominationTimer(state);

		assertTrue(timerManager.isTimerRunning());
		assertEquals(30, timerManager.getRemainingSeconds());
	}

	@Test
	void stopTimerStopsRunningTimer() {
		timerManager.startDiscussionTimer(state);
		assertTrue(timerManager.isTimerRunning());

		timerManager.stopTimer(state);

		assertFalse(timerManager.isTimerRunning());
		assertFalse(state.isTimerActive());
		assertEquals(0, timerManager.getRemainingSeconds());
		assertEquals(TimerType.NONE, timerManager.getTimerType());
		assertTrue(dispatchedEvents.getLast() instanceof TimerStopped);
	}

	@Test
	void stopTimerWhenNotRunningIsNoOp() {
		assertFalse(timerManager.isTimerRunning());
		timerManager.stopTimer(state);
		assertFalse(timerManager.isTimerRunning());
		assertFalse(state.isTimerActive());
	}

	@Test
	void startingNewTimerReplacesOldOne() {
		config.set("discussionTimerSeconds", 120L);
		config.set("nominationTimerSeconds", 45L);

		timerManager.startDiscussionTimer(state);
		assertEquals(TimerType.DISCUSSION, timerManager.getTimerType());
		assertEquals(120L, timerManager.getRemainingSeconds());

		timerManager.startNominationTimer(state);
		assertEquals(TimerType.NOMINATION, timerManager.getTimerType());
		assertEquals(45L, timerManager.getRemainingSeconds());
	}

	@Test
	void timerExpiredEventFiresWhenSchedulerDelayElapses() {
		config.set("discussionTimerSeconds", 5L);
		timerManager.startDiscussionTimer(state);

		assertTrue(timerManager.isTimerRunning());
		assertTrue(scheduler.hasPendingDelayedTask());

		scheduler.runDelayedTask();

		assertFalse(timerManager.isTimerRunning());
		assertFalse(state.isTimerActive());
		assertEquals(0, timerManager.getRemainingSeconds());
		assertEquals(TimerType.NONE, timerManager.getTimerType());
		assertEquals(2, dispatchedEvents.size());

		TimerExpired event = (TimerExpired) dispatchedEvents.get(1);
		assertEquals(TimerType.DISCUSSION, event.timerType());
		assertEquals(5, event.durationSeconds());
	}

	@Test
	void stoppingTimerCancelsScheduledExpiry() {
		config.set("nominationTimerSeconds", 30L);
		timerManager.startNominationTimer(state);

		timerManager.stopTimer(state);

		scheduler.runDelayedTaskIfAny();

		assertEquals(2, dispatchedEvents.size());
		assertFalse(timerManager.isTimerRunning());
	}

	@Test
	void timerExpiredEventFiresForNominationTimer() {
		config.set("nominationTimerSeconds", 20L);
		timerManager.startNominationTimer(state);

		scheduler.runDelayedTask();

		assertEquals(2, dispatchedEvents.size());
		TimerExpired event = (TimerExpired) dispatchedEvents.get(1);
		assertEquals(TimerType.NOMINATION, event.timerType());
		assertEquals(20, event.durationSeconds());
	}

	@Test
	void remainingSecondsCountsDownWhileTimerRuns() throws InterruptedException {
		config.set("discussionTimerSeconds", 2L);
		timerManager.startDiscussionTimer(state);
		assertEquals(2L, timerManager.getRemainingSeconds());

		Thread.sleep(1100);

		assertTrue(timerManager.getRemainingSeconds() < 2L);
	}

	@Test
	void startingNewTimerCancelsPreviousExpiry() {
		config.set("discussionTimerSeconds", 100L);
		timerManager.startDiscussionTimer(state);

		config.set("nominationTimerSeconds", 25L);
		timerManager.startNominationTimer(state);

		scheduler.runDelayedTask();

		assertEquals(3, dispatchedEvents.size());
		TimerExpired event = (TimerExpired) dispatchedEvents.get(2);
		assertEquals(TimerType.NOMINATION, event.timerType());
	}

	@Test
	void timerExpiredEventIsAGameEvent() {
		TimerExpired event = new TimerExpired(TimerType.DISCUSSION, 60);
		assertTrue(event instanceof GameEvent);
	}

	@Test
	void timerTypeHasAllExpectedValues() {
		TimerType[] types = TimerType.values();
		assertEquals(3, types.length);
		assertNotNull(TimerType.valueOf("NONE"));
		assertNotNull(TimerType.valueOf("DISCUSSION"));
		assertNotNull(TimerType.valueOf("NOMINATION"));
	}

	@Test
	void timerManagerRejectsNullDependencies() {
		assertNotNull(assertThrowsForNull(() -> new TimerManager(null, config, dispatcher)));
		assertNotNull(assertThrowsForNull(() -> new TimerManager(scheduler, null, dispatcher)));
		assertNotNull(assertThrowsForNull(() -> new TimerManager(scheduler, config, null)));
	}

	private Throwable assertThrowsForNull(Runnable action) {
		try {
			action.run();
			return null;
		} catch (NullPointerException e) {
			return e;
		}
	}

	private static class FakeScheduler implements SchedulerAdapter {

		private Runnable delayedTask;

		@Override
		public void runNextTick(Runnable task) {
		}

		@Override
		public void runAfterDelay(long delayTicks, Runnable task) {
			this.delayedTask = task;
		}

		boolean hasPendingDelayedTask() {
			return delayedTask != null;
		}

		void runDelayedTask() {
			Runnable task = delayedTask;
			delayedTask = null;
			if (task != null) {
				task.run();
			}
		}

		void runDelayedTaskIfAny() {
			if (delayedTask != null) {
				runDelayedTask();
			}
		}
	}

	private static class FakeConfig implements ConfigAdapter {

		private final java.util.Map<String, Object> values = new java.util.HashMap<>();

		@Override
		public void load() {
		}

		@Override
		public void save() {
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Optional<T> get(String key, Class<T> valueType) {
			Object value = values.get(key);
			if (value == null) {
				return Optional.empty();
			}
			return Optional.of((T) value);
		}

		@Override
		public void set(String key, Object value) {
			values.put(key, value);
		}
	}
}
