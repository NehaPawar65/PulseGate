package com.pulsegate.limiter;

import java.util.function.LongSupplier;

/**
 * Thread-safe fixed-window counter: at most {@code capacity} requests per window.
 * State is two fields (window start, count), so every operation is O(1).
 *
 * Known trade-off vs. token bucket: windows reset all at once, so a client can send
 * {@code capacity} requests at the end of one window and {@code capacity} more at the
 * start of the next - up to 2x capacity in a short burst across the boundary.
 */
public class FixedWindowCounter implements RateLimiter {

    private final long capacity;
    private final long windowNanos;
    private final LongSupplier nanoClock;

    private long windowStartNanos;
    private long count;

    public FixedWindowCounter(long capacity, long windowSeconds) {
        this(capacity, windowSeconds, System::nanoTime);
    }

    // Package-private: tests inject a fake clock.
    FixedWindowCounter(long capacity, long windowSeconds, LongSupplier nanoClock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be > 0");
        }
        this.capacity = capacity;
        this.windowNanos = windowSeconds * 1_000_000_000L;
        this.nanoClock = nanoClock;
        this.windowStartNanos = nanoClock.getAsLong();
    }

    @Override
    public synchronized ConsumeResult tryConsume() {
        long now = rollWindow();
        if (count < capacity) {
            count++;
            return new ConsumeResult(true, capacity - count, 0);
        }
        return new ConsumeResult(false, 0, windowStartNanos + windowNanos - now);
    }

    @Override
    public synchronized long availableTokens() {
        rollWindow();
        return capacity - count;
    }

    public long getCapacity() {
        return capacity;
    }

    /** Advances to the window containing "now", keeping windows aligned to the first one. */
    private long rollWindow() {
        long now = nanoClock.getAsLong();
        long elapsed = now - windowStartNanos;
        if (elapsed >= windowNanos) {
            windowStartNanos = now - (elapsed % windowNanos);
            count = 0;
        }
        return now;
    }
}
