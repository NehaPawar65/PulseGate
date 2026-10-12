package com.pulsegate.limiter;

/** One client's quota. Implementations must be thread-safe and O(1) per call. */
public interface RateLimiter {

    /** Atomically checks the quota and, if allowed, spends one unit of it. */
    ConsumeResult tryConsume();

    /** Units that could be spent right now. */
    long availableTokens();
}
