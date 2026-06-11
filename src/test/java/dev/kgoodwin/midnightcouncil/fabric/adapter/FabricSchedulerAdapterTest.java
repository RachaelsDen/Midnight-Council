package dev.kgoodwin.midnightcouncil.fabric.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FabricSchedulerAdapterTest {

    @Test
    void runAfterDelayWaitsForRequestedTickCount() {
        FabricSchedulerAdapter adapter = new FabricSchedulerAdapter(null);
        AtomicInteger runs = new AtomicInteger();

        adapter.runAfterDelay(3, runs::incrementAndGet);

        adapter.tick();
        adapter.tick();
        assertEquals(0, runs.get());

        adapter.tick();
        assertEquals(1, runs.get());
    }

    @Test
    void runAfterDelayExecutesMultipleTasksInTargetOrder() {
        FabricSchedulerAdapter adapter = new FabricSchedulerAdapter(null);
        List<String> executionOrder = new ArrayList<>();

        adapter.runAfterDelay(4, () -> executionOrder.add("four"));
        adapter.runAfterDelay(2, () -> executionOrder.add("two"));
        adapter.runAfterDelay(3, () -> executionOrder.add("three"));

        adapter.tick();
        adapter.tick();
        adapter.tick();
        adapter.tick();

        assertEquals(List.of("two", "three", "four"), executionOrder);
    }

    @Test
    void runNextTickStillExecutesBeforeLaterDelayedTasks() {
        FabricSchedulerAdapter adapter = new FabricSchedulerAdapter(null);
        List<String> executionOrder = new ArrayList<>();

        adapter.runNextTick(() -> executionOrder.add("next"));
        adapter.runAfterDelay(2, () -> executionOrder.add("delayed"));

        adapter.tick();
        adapter.tick();

        assertEquals(List.of("next", "delayed"), executionOrder);
    }

    @Test
    void schedulerContinuesAfterTaskThrows() {
        FabricSchedulerAdapter adapter = new FabricSchedulerAdapter(null);
        AtomicInteger runs = new AtomicInteger();

        adapter.runAfterDelay(1, () -> {
            throw new IllegalStateException("boom");
        });
        adapter.runAfterDelay(1, runs::incrementAndGet);

        adapter.tick();

        assertEquals(1, runs.get());
    }

    @Test
    void nestedRunNextTickDefersToFollowingTick() {
        FabricSchedulerAdapter adapter = new FabricSchedulerAdapter(null);
        List<String> executionOrder = new ArrayList<>();

        adapter.runNextTick(() -> {
            executionOrder.add("outer");
            adapter.runNextTick(() -> executionOrder.add("inner"));
        });

        adapter.tick();
        assertEquals(List.of("outer"), executionOrder);

        adapter.tick();
        assertEquals(List.of("outer", "inner"), executionOrder);
    }

    @Test
    void zeroDelayScheduledDuringTickRunsOnFollowingTick() {
        FabricSchedulerAdapter adapter = new FabricSchedulerAdapter(null);
        List<String> executionOrder = new ArrayList<>();

        adapter.runAfterDelay(1, () -> {
            executionOrder.add("outer");
            adapter.runAfterDelay(0, () -> executionOrder.add("inner"));
        });

        adapter.tick();
        assertEquals(List.of("outer"), executionOrder);

        adapter.tick();
        assertEquals(List.of("outer", "inner"), executionOrder);
    }

    @Test
    void zeroDelayScheduledFromNextTickRunsOnFollowingTick() {
        FabricSchedulerAdapter adapter = new FabricSchedulerAdapter(null);
        List<String> executionOrder = new ArrayList<>();

        adapter.runNextTick(() -> {
            executionOrder.add("outer");
            adapter.runAfterDelay(0, () -> executionOrder.add("inner"));
        });

        adapter.tick();
        assertEquals(List.of("outer"), executionOrder);

        adapter.tick();
        assertEquals(List.of("outer", "inner"), executionOrder);
    }
}
