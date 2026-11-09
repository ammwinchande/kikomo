package co.tz.kikomo.demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory state of one scripted-agent run. Steps are appended as they happen and read by the SPA while polling.
 */
final class DemoRun {
    static final String RUNNING = "running";
    static final String DONE = "done";
    static final String FAILED = "failed";

    final UUID id = UUID.randomUUID();
    final String scenario;
    final Instant startedAt = Instant.now();
    private final List<DemoStep> steps = new CopyOnWriteArrayList<>();
    private final AtomicInteger seq = new AtomicInteger();
    volatile String status = RUNNING;

    DemoRun(String scenario) {
        this.scenario = scenario;
    }

    String nextId() {
        return "s" + seq.incrementAndGet();
    }

    void add(DemoStep step) {
        steps.add(step);
    }

    List<DemoStep> steps() {
        return List.copyOf(steps);
    }

    DemoRunView view() {
        return new DemoRunView(id.toString(), scenario, status, startedAt.toString(), steps());
    }
}
