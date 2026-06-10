package dev.kgoodwin.midnightcouncil.fabric.adapter;

import dev.kgoodwin.midnightcouncil.api.SchedulerAdapter;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FabricSchedulerAdapter implements SchedulerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FabricSchedulerAdapter.class);

    private final MinecraftServer server;
    private final Queue<Runnable> nextTickTasks = new ArrayDeque<>();
    private long currentTick = 0;

    public FabricSchedulerAdapter(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void runNextTick(Runnable task) {
        nextTickTasks.add(task);
    }

    @Override
    public void runAfterDelay(long delayTicks, Runnable task) {
        long targetTick = currentTick + delayTicks;
        server.execute(() -> {
            long remaining = targetTick - currentTick;
            if (remaining <= 0) {
                task.run();
            } else {
                runAfterDelay(remaining, task);
            }
        });
    }

    public void tick() {
        currentTick++;
        Runnable task;
        while ((task = nextTickTasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                LOG.error("Scheduled task threw exception", e);
            }
        }
    }
}
