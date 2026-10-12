package com.pulsegate.limiter;

import com.pulsegate.entity.Algorithm;

/**
 * The limiter's view of a policy: plain values, no JPA. Two equal specs mean
 * the existing limiter can be reused; any difference means the policy changed.
 */
public record LimitSpec(Algorithm algorithm, long capacity, double refillTokensPerSecond, Integer windowSeconds) {

    public static LimitSpec tokenBucket(long capacity, double refillTokensPerSecond) {
        return new LimitSpec(Algorithm.TOKEN_BUCKET, capacity, refillTokensPerSecond, null);
    }

    public static LimitSpec fixedWindow(long capacity, int windowSeconds) {
        return new LimitSpec(Algorithm.FIXED_WINDOW, capacity, 0, windowSeconds);
    }

    RateLimiter newLimiter() {
        return switch (algorithm) {
            case TOKEN_BUCKET -> new TokenBucket(capacity, refillTokensPerSecond);
            case FIXED_WINDOW -> new FixedWindowCounter(capacity, windowSeconds);
        };
    }
}
