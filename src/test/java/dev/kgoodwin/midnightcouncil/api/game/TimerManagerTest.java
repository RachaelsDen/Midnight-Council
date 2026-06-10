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

	@BeforeEach
	void setUp() {
		scheduler = new FakeScheduler();
		config = new FakeConfig();
		dispatcher = new GameEventDispatcher();
		dispatchedEvents = new ArrayList<>();
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
		timerManager.startDiscussionTimer();

		assertTrue(timerManager.isTimerRunning());
		assertEquals(TimerType.DISCUSSION, timerManager.getTimerType());
		assertEquals(120L, timerManager.getRemainingSeconds());
	}

	@Test
	void startDiscussionTimerUsesDefaultWhenNotConfigured() {
		timerManager.startDiscussionTimer();

		assertTrue(timerManager.isTimerRunning());
		assertEquals(180, timerManager.getRemainingSeconds());
	}

	@Test
	void startNominationTimerUsesConfiguredDuration() {
		config.set("nominationTimerSeconds", 45L);
		timerManager.startNominationTimer();

		assertTrue(timerManager.isTimerRunning());
		assertEquals(TimerType.NOMINATION, timerManager.getTimerType());
		assertEquals(45L, timerManager.getRemainingSeconds());
	}

	@Test
	void startNominationTimerUsesDefaultWhenNotConfigured() {
		timerManager.startNominationTimer();

		assertTrue(timerManager.isTimerRunning());
		assertEquals(30, timerManager.getRemainingSeconds());
	}

	@Test
	void stopTimerStopsRunningTimer() {
		timerManager.startDiscussionTimer();
		assertTrue(timerManager.isTimerRunning());

		timerManager.stopTimer();

		assertFalse(timerManager.isTimerRunning());
		assertEquals(0, timerManager.getRemainingSeconds());
		assertEquals(TimerType.NONE, timerManager.getTimerType());
	}

	@Test
	void stopTimerWhenNotRunningIsNoOp() {
		assertFalse(timerManager.isTimerRunning());
		timerManager.stopTimer();
		assertFalse(timerManager.isTimerRunning());
	}

	@Test
	void startingNewTimerReplacesOldOne() {
		config.set("discussionTimerSeconds", 120L);
		config.set("nominationTimerSeconds", 45L);

		timerManager.startDiscussionTimer();
		assertEquals(TimerType.DISCUSSION, timerManager.getTimerType());
		assertEquals(120L, timerManager.getRemainingSeconds());

		timerManager.startNominationTimer();
		assertEquals(TimerType.NOMINATION, timerManager.getTimerType());
		assertEquals(45L, timerManager.getRemainingSeconds());
	}

	@Test
	void timerExpiredEventFiresWhenSchedulerDelayElapses() {
		config.set("discussionTimerSeconds", 5L);
		timerManager.startDiscussionTimer();

		assertTrue(timerManager.isTimerRunning());
		assertTrue(scheduler.hasPendingDelayedTask());

		scheduler.runDelayedTask();

		assertFalse(timerManager.isTimerRunning());
		assertEquals(0, timerManager.getRemainingSeconds());
		assertEquals(TimerType.NONE, timerManager.getTimerType());
		assertEquals(1, dispatchedEvents.size());

		TimerExpired event = (TimerExpired) dispatchedEvents.get(0);
		assertEquals(TimerType.DISCUSSION, event.timerType());
		assertEquals(5, event.durationSeconds());
	}

	@Test
	void stoppingTimerCancelsScheduledExpiry() {
		config.set("nominationTimerSeconds", 30L);
		timerManager.startNominationTimer();

		timerManager.stopTimer();

		scheduler.runDelayedTaskIfAny();

		assertTrue(dispatchedEvents.isEmpty());
		assertFalse(timerManager.isTimerRunning());
	}

	@Test
	void timerExpiredEventFiresForNominationTimer() {
		config.set("nominationTimerSeconds", 20L);
		timerManager.startNominationTimer();

		scheduler.runDelayedTask();

		assertEquals(1, dispatchedEvents.size());
		TimerExpired event = (TimerExpired) dispatchedEvents.get(0);
		assertEquals(TimerType.NOMINATION, event.timerType());
		assertEquals(20, event.durationSeconds());
	}

	@Test
	void startingNewTimerCancelsPreviousExpiry() {
		config.set("discussionTimerSeconds", 100L);
		timerManager.startDiscussionTimer();

		config.set("nominationTimerSeconds", 25L);
		timerManager.startNominationTimer();

		scheduler.runDelayedTask();

		assertEquals(1, dispatchedEvents.size());
		TimerExpired event = (TimerExpired) dispatchedEvents.get(0);
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
