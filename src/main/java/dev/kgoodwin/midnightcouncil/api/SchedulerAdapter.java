package dev.kgoodwin.midnightcouncil.api;

public interface SchedulerAdapter extends PlatformInterface {

	void runNextTick(Runnable task);

	void runAfterDelay(long delayTicks, Runnable task);
}
