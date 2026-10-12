package com.pulsegate.service;

import com.pulsegate.entity.RequestEvent;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Persists request events for analytics. The rate-limit decision is already made
 * before this runs, so a logging failure never changes a response and never
 * lets a request bypass its limit.
 *
 * Async mode (default): the request thread only enqueues; a background thread writes
 * batches. The queue is bounded: when it's full, events are dropped and counted, so a
 * slow database can never block or exhaust request threads. Sync mode writes on the
 * request thread (used by tests that check events right after a request).
 */
@Component
public class RequestEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(RequestEventRecorder.class);
    private static final long WARN_INTERVAL_NANOS = 60_000_000_000L;

    private final EventBatchWriter writer;
    private final boolean async;
    private final int batchSize;
    private final BlockingQueue<RequestEvent> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong lastWarnNanos = new AtomicLong(System.nanoTime() - WARN_INTERVAL_NANOS);
    private final Thread worker;
    private volatile boolean running = true;

    public RequestEventRecorder(EventBatchWriter writer,
                                @Value("${pulsegate.events.async:true}") boolean async,
                                @Value("${pulsegate.events.queue-capacity:10000}") int queueCapacity,
                                @Value("${pulsegate.events.batch-size:500}") int batchSize) {
        this.writer = writer;
        this.async = async;
        this.batchSize = batchSize;
        this.queue = async ? new ArrayBlockingQueue<>(queueCapacity) : null;
        this.worker = async ? Thread.ofPlatform().name("event-writer").daemon().start(this::drainLoop) : null;
    }

    public void record(RequestEvent event) {
        if (!async) {
            writeSafely(List.of(event));
            return;
        }
        if (!queue.offer(event)) { // never blocks the request thread
            long total = dropped.incrementAndGet();
            warnThrottled("Event queue full; {} events dropped so far", total);
        }
    }

    /** Events dropped because the queue was full or a batch write failed. */
    public long droppedEvents() {
        return dropped.get();
    }

    public int pendingEvents() {
        return async ? queue.size() : 0;
    }

    private void drainLoop() {
        List<RequestEvent> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                RequestEvent first = queue.poll(200, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                writeSafely(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void writeSafely(List<RequestEvent> batch) {
        try {
            writer.write(batch);
        } catch (RuntimeException e) {
            long total = dropped.addAndGet(batch.size());
            RequestEvent first = batch.getFirst();
            warnThrottled("Failed to record request event (client={}, decision={}): {} ({} dropped so far)",
                    first.getClientId(), first.getDecision(), e.getMessage(), total);
        }
    }

    /** On shutdown, write what's still queued (bounded wait) before the app exits. */
    @PreDestroy
    public void shutdown() throws InterruptedException {
        running = false;
        if (worker != null) {
            worker.join(5_000);
        }
    }

    /** At most one warning a minute, so an outage doesn't flood the log. */
    private void warnThrottled(String message, Object... args) {
        long now = System.nanoTime();
        long last = lastWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(last, now)) {
            log.warn(message, args);
        }
    }
}
