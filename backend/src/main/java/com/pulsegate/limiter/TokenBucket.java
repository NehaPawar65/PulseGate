package com.pulsegate.limiter;

import java.util.function.LongSupplier;

/**
 * Thread-safe token bucket. State is two fields; refill is computed lazily
 * on each call, so every operation is O(1) time and memory.
 */
public class TokenBucket implements RateLimiter {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long capacity;
    private final double refillTokensPerSecond;
    private final LongSupplier nanoClock;

    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(long capacity, double refillTokensPerSecond) {
        this(capacity, refillTokensPerSecond, System::nanoTime);
    }

    // Package-private: tests inject a fake clock.
    TokenBucket(long capacity, double refillTokensPerSecond, LongSupplier nanoClock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("refillTokensPerSecond must be > 0");
        }
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.nanoClock = nanoClock;
        this.tokens = capacity; // start full so a new client can burst
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    @Override
    public synchronized ConsumeResult tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return new ConsumeResult(true, (long) tokens, 0);
        }
        long waitNanos = (long) Math.ceil((1 - tokens) * NANOS_PER_SECOND / refillTokensPerSecond);
        return new ConsumeResult(false, 0, waitNanos);
    }

    @Override
    public synchronized long availableTokens() {
        refill();
        return (long) tokens;
    }

    public long getCapacity() {
        return capacity;
    }

    public double getRefillTokensPerSecond() {
        return refillTokensPerSecond;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillTokensPerSecond / NANOS_PER_SECOND);
            lastRefillNanos = now;
        }
    }
}
