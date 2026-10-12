package com.pulsegate.limiter;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Quota state in Redis, so every gateway instance enforces one shared limit per client.
 * Each check is a single Lua script: Redis runs it atomically, using its own clock.
 *
 * If Redis is unreachable, falls back to per-instance in-memory limiting: limits get
 * weaker (each instance enforces its own), but they are never switched off.
 */
@Component
@ConditionalOnProperty(name = "pulsegate.limiter.store", havingValue = "redis")
public class RedisRateLimiterService implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);
    private static final String KEY_PREFIX = "pulsegate:limit:";
    private static final long WARN_INTERVAL_NANOS = 60_000_000_000L;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TOKEN_BUCKET =
            RedisScript.of(new ClassPathResource("redis/token_bucket.lua"), List.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> FIXED_WINDOW =
            RedisScript.of(new ClassPathResource("redis/fixed_window.lua"), List.class);

    private final StringRedisTemplate redis;
    private final InMemoryRateLimiterService fallback = new InMemoryRateLimiterService();
    private final AtomicLong lastWarnNanos = new AtomicLong(System.nanoTime() - WARN_INTERVAL_NANOS);

    public RedisRateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Opens the connection and loads both scripts at startup, so the first real request
     * doesn't pay the connect cost (and time out into the fallback). Failure is not fatal.
     */
    @PostConstruct
    public void warmUp() {
        try {
            redis.execute(TOKEN_BUCKET, List.of(KEY_PREFIX + "warmup"), "1", "1");
            redis.execute(FIXED_WINDOW, List.of(KEY_PREFIX + "warmup-window"), "1", "1");
            log.info("Rate limiter using Redis (shared quotas across instances)");
        } catch (DataAccessException e) {
            warnThrottled(e);
        }
    }

    @Override
    public ConsumeResult tryConsume(long clientId, LimitSpec spec) {
        // The policy is part of the key: a changed policy starts a fresh quota, as in memory.
        String key = KEY_PREFIX + clientId + ":" + spec.fingerprint();
        try {
            List<?> reply = switch (spec.algorithm()) {
                case TOKEN_BUCKET -> redis.execute(TOKEN_BUCKET, List.of(key),
                        String.valueOf(spec.capacity()), String.valueOf(spec.refillTokensPerSecond()));
                case FIXED_WINDOW -> redis.execute(FIXED_WINDOW, List.of(key),
                        String.valueOf(spec.capacity()), String.valueOf(spec.windowSeconds()));
            };
            boolean allowed = asLong(reply.get(0)) == 1;
            long remaining = asLong(reply.get(1));
            double waitSeconds = Double.parseDouble(String.valueOf(reply.get(2)));
            return new ConsumeResult(allowed, remaining, (long) Math.ceil(waitSeconds * 1_000_000_000));
        } catch (DataAccessException e) {
            warnThrottled(e);
            return fallback.tryConsume(clientId, spec);
        }
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    /** At most one warning a minute, so an outage doesn't flood the log. */
    private void warnThrottled(DataAccessException e) {
        long now = System.nanoTime();
        long last = lastWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(last, now)) {
            log.warn("Redis unavailable, using per-instance in-memory limits until it recovers: {}", e.getMessage());
        }
    }
}
