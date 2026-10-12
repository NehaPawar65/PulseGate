package com.pulsegate.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Runs against the Redis from docker compose (localhost:56379, database 1). */
class RedisRateLimiterServiceTest {

    private static final double SLOW = 0.0001;
    private static final AtomicLong ids = new AtomicLong(System.nanoTime());

    private static LettuceConnectionFactory factory;
    private static RedisRateLimiterService limiter;

    @BeforeAll
    static void connect() {
        factory = connectionFactory(56379);
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
            c.serverCommands().flushDb();
            return null;
        });
        limiter = new RedisRateLimiterService(redis);
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    private static LettuceConnectionFactory connectionFactory(int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", port);
        config.setDatabase(1);
        LettuceConnectionFactory f = new LettuceConnectionFactory(config,
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
        f.afterPropertiesSet();
        f.start();
        return f;
    }

    private static long newClient() {
        return ids.incrementAndGet();
    }

    @Test
    void tokenBucketAllowsBurstThenRejects() {
        long client = newClient();
        LimitSpec spec = LimitSpec.tokenBucket(5, SLOW);
        for (int remaining = 4; remaining >= 0; remaining--) {
            ConsumeResult r = limiter.tryConsume(client, spec);
            assertThat(r.allowed()).isTrue();
            assertThat(r.remainingTokens()).isEqualTo(remaining);
        }
        ConsumeResult rejected = limiter.tryConsume(client, spec);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isPositive();
    }

    @Test
    void tokenBucketRefillsOverTime() throws Exception {
        long client = newClient();
        LimitSpec spec = LimitSpec.tokenBucket(1, 20); // one token every 50 ms
        assertThat(limiter.tryConsume(client, spec).allowed()).isTrue();
        assertThat(limiter.tryConsume(client, spec).allowed()).isFalse();
        Thread.sleep(120);
        assertThat(limiter.tryConsume(client, spec).allowed()).isTrue();
    }

    @Test
    void fixedWindowAllowsCapacityPerWindow() {
        long client = newClient();
        LimitSpec spec = LimitSpec.fixedWindow(3, 3600);
        for (int i = 0; i < 3; i++) assertThat(limiter.tryConsume(client, spec).allowed()).isTrue();
        ConsumeResult rejected = limiter.tryConsume(client, spec);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 3600L);
    }

    @Test
    void policyChangeStartsAFreshQuota() {
        long client = newClient();
        limiter.tryConsume(client, LimitSpec.tokenBucket(1, SLOW));
        assertThat(limiter.tryConsume(client, LimitSpec.tokenBucket(1, SLOW)).allowed()).isFalse();
        assertThat(limiter.tryConsume(client, LimitSpec.tokenBucket(5, SLOW)).remainingTokens()).isEqualTo(4);
    }

    /** The point of Redis: two gateway instances hitting one quota never over-grant it. */
    @Test
    void twoGatewayInstancesShareOneQuota() throws Exception {
        LettuceConnectionFactory secondFactory = connectionFactory(56379);
        RedisRateLimiterService instanceB = new RedisRateLimiterService(new StringRedisTemplate(secondFactory));
        RedisRateLimiterService instanceA = limiter;
        long client = newClient();
        LimitSpec spec = LimitSpec.tokenBucket(500, SLOW);

        int threads = 20, attemptsPerThread = 100; // 2,000 attempts for 500 tokens
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            RedisRateLimiterService instance = t % 2 == 0 ? instanceA : instanceB;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (instance.tryConsume(client, spec).allowed()) allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        secondFactory.destroy();

        assertThat(allowed.get()).isEqualTo(500);
    }

    @Test
    void fallsBackToInMemoryLimitsWhenRedisIsDown() {
        LettuceConnectionFactory deadFactory = connectionFactory(1); // nothing listens on port 1
        RedisRateLimiterService offline = new RedisRateLimiterService(new StringRedisTemplate(deadFactory));
        long client = newClient();
        LimitSpec spec = LimitSpec.tokenBucket(2, SLOW);

        assertThat(offline.tryConsume(client, spec).allowed()).isTrue();
        assertThat(offline.tryConsume(client, spec).allowed()).isTrue();
        assertThat(offline.tryConsume(client, spec).allowed()).isFalse(); // still limited, never "fail open"
        deadFactory.destroy();
    }
}
