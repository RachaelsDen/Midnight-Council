package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.SchedulerAdapter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricSchedulerAdapter implements SchedulerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricSchedulerAdapter.class);

    private final Queue<Runnable> nextTickTasks = new ArrayDeque<>();
    private final Queue<DelayedTask> delayedTasks = new PriorityQueue<>(Comparator.comparingLong(DelayedTask::targetTick));
    private long currentTick = 0;

    public FabricSchedulerAdapter(net.minecraft.server.MinecraftServer server) {
    }

    @Override
    public void runNextTick(Runnable task) {
        nextTickTasks.add(task);
    }

    @Override
    public void runAfterDelay(long delayTicks, Runnable task) {
        delayedTasks.add(new DelayedTask(currentTick + Math.max(0L, delayTicks), task));
    }

    public void tick() {
        currentTick++;
        List<DelayedTask> dueTasks = new java.util.ArrayList<>();
        DelayedTask delayedTask;
        while ((delayedTask = delayedTasks.peek()) != null && delayedTask.targetTick() <= currentTick) {
            dueTasks.add(delayedTasks.poll());
        }

        int nextTickTaskCount = nextTickTasks.size();
        for (int index = 0; index < nextTickTaskCount; index++) {
            Runnable task = nextTickTasks.poll();
            if (task == null) {
                break;
            }
            runSafely(task);
        }

        for (DelayedTask dueTask : dueTasks) {
            runSafely(dueTask.task());
        }
    }

    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            LOG.error("Scheduled task threw exception", e);
        }
    }

    private record DelayedTask(long targetTick, Runnable task) {
    }
}
