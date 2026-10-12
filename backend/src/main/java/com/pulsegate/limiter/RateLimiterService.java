package com.pulsegate.limiter;

/**
 * Per-client quota check. Two implementations, chosen by pulsegate.limiter.store:
 * {@link InMemoryRateLimiterService} (single instance, default) and
 * {@link RedisRateLimiterService} (quota shared by every instance).
 */
public interface RateLimiterService {

    /** Atomically checks the client's quota under the given policy and spends one unit if allowed. */
    ConsumeResult tryConsume(long clientId, LimitSpec spec);
}
