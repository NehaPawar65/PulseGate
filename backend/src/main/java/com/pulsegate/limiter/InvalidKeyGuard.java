package com.pulsegate.limiter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits failed API-key attempts per client IP. Once an IP has used up its allowance,
 * its requests are rejected before any database work, so a flood of made-up keys
 * cannot turn into a flood of DB lookups and inserts.
 *
 * Uses the connection's remote address. Behind a reverse proxy, enable
 * server.forward-headers-strategy so the real client IP is used.
 */
@Component
public class InvalidKeyGuard {

    /** Above this many tracked IPs, fully recovered entries are dropped. */
    static final int CLEANUP_THRESHOLD = 10_000;

    private final long capacity;
    private final double refillPerSecond;
    private final ConcurrentMap<String, TokenBucket> attempts = new ConcurrentHashMap<>();

    public InvalidKeyGuard(@Value("${pulsegate.invalid-key.capacity:30}") long capacity,
                           @Value("${pulsegate.invalid-key.refill-per-second:1.0}") double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    /** True while this IP has no failed attempts left. Checked before the key lookup. */
    public boolean isBlocked(String ip) {
        TokenBucket bucket = attempts.get(ip);
        return bucket != null && bucket.availableTokens() < 1;
    }

    /** Spends one failed attempt for this IP. */
    public void recordFailure(String ip) {
        if (attempts.size() > CLEANUP_THRESHOLD) {
            attempts.values().removeIf(bucket -> bucket.availableTokens() >= capacity);
        }
        attempts.computeIfAbsent(ip, k -> new TokenBucket(capacity, refillPerSecond)).tryConsume();
    }

    /** Upper bound on the wait until one more attempt is allowed. */
    public long retryAfterSeconds() {
        return Math.max(1, (long) Math.ceil(1 / refillPerSecond));
    }

    int trackedAddresses() {
        return attempts.size();
    }
}
