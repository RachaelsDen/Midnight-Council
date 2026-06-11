package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.ConfigAdapter;
import dev.kgoodwin.midnightcouncil.api.SchedulerAdapter;
import dev.kgoodwin.midnightcouncil.api.event.GameEventDispatcher;
import dev.kgoodwin.midnightcouncil.api.event.TimerExpired;

import java.util.Objects;

public class TimerManager {

	public enum TimerType {
		NONE, DISCUSSION, NOMINATION
	}

	private static final long DEFAULT_DISCUSSION_SECONDS = 180;
	private static final long DEFAULT_NOMINATION_SECONDS = 30;
	private static final long TICKS_PER_SECOND = 20;

	private final SchedulerAdapter scheduler;
	private final ConfigAdapter config;
	private final GameEventDispatcher dispatcher;

	private volatile TimerType currentType = TimerType.NONE;
	private volatile long durationSeconds;
	private volatile long generation;
	private volatile long startNanos;

	public TimerManager(SchedulerAdapter scheduler, ConfigAdapter config, GameEventDispatcher dispatcher) {
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		this.config = Objects.requireNonNull(config, "config");
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
	}

	public void startDiscussionTimer() {
		startTimer(TimerType.DISCUSSION, getDiscussionDuration());
	}

	public void startNominationTimer() {
		startTimer(TimerType.NOMINATION, getNominationDuration());
	}

	public void stopTimer() {
		generation++;
		currentType = TimerType.NONE;
		durationSeconds = 0;
		startNanos = 0;
	}

	public boolean isTimerRunning() {
		return currentType != TimerType.NONE;
	}

	public long getRemainingSeconds() {
		if (!isTimerRunning()) {
			return 0;
		}

		long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
		long remainingSeconds = durationSeconds - elapsedSeconds;
		return Math.max(0, remainingSeconds);
	}

	public TimerType getTimerType() {
		return currentType;
	}

	private void startTimer(TimerType type, long seconds) {
		generation++;
		currentType = type;
		durationSeconds = seconds;
		startNanos = System.nanoTime();

		long activeGeneration = generation;
		scheduler.runAfterDelay(seconds * TICKS_PER_SECOND, () -> {
			if (generation != activeGeneration) {
				return;
			}
			currentType = TimerType.NONE;
			durationSeconds = 0;
			startNanos = 0;
			dispatcher.dispatch(new TimerExpired(type, seconds));
		});
	}

	private long getDiscussionDuration() {
		return config.get("discussionTimerSeconds", Long.class)
				.orElse(DEFAULT_DISCUSSION_SECONDS);
	}

	private long getNominationDuration() {
		return config.get("nominationTimerSeconds", Long.class)
				.orElse(DEFAULT_NOMINATION_SECONDS);
	}
}
