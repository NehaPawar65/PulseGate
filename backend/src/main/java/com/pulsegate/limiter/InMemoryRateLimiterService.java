package com.pulsegate.limiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Holds one rate limiter per client, in memory.
 * Single-instance only: quotas are not shared across multiple app instances.
 * Also used by {@link RedisRateLimiterService} as its fallback when Redis is unreachable.
 */
@Component
@ConditionalOnProperty(name = "pulsegate.limiter.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiterService implements RateLimiterService {

    private record Entry(LimitSpec spec, RateLimiter limiter) {}

    private final ConcurrentMap<Long, Entry> limiters = new ConcurrentHashMap<>();

    @Override
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
