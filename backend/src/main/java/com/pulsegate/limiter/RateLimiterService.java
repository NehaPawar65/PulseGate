package com.pulsegate.limiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Holds one rate limiter per client, in memory.
 * Single-instance only: quotas are not shared across multiple app instances.
 */
@Component
public class RateLimiterService {

    private record Entry(LimitSpec spec, RateLimiter limiter) {}

    private final ConcurrentMap<Long, Entry> limiters = new ConcurrentHashMap<>();

    public ConsumeResult tryConsume(long clientId, LimitSpec spec) {
        // compute() is atomic per key: concurrent first requests share one limiter,
        // and a changed policy (algorithm or limits) swaps in a fresh one exactly once.
        Entry entry = limiters.compute(clientId, (id, existing) ->
                existing != null && existing.spec().equals(spec)
                        ? existing
                        : new Entry(spec, spec.newLimiter()));
        return entry.limiter().tryConsume();
    }

    public void reset(long clientId) {
        limiters.remove(clientId);
    }

    int bucketCount() {
        return limiters.size();
    }
}
