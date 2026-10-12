package com.pulsegate.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    // Tiny refill rate so no meaningful tokens are added while a test runs.
    private static final double SLOW = 0.0001;

    private final RateLimiterService limiter = new RateLimiterService();

    private ConsumeResult consume(long clientId, long capacity) {
        return limiter.tryConsume(clientId, LimitSpec.tokenBucket(capacity, SLOW));
    }

    @Test
    void reusesTheSameBucketForAClient() {
        for (int i = 0; i < 3; i++) {
            assertThat(consume(1L, 3).allowed()).isTrue();
        }
        assertThat(consume(1L, 3).allowed()).isFalse();
    }

    @Test
    void clientsHaveIndependentQuotas() {
        for (int i = 0; i < 3; i++) consume(1L, 3);

        assertThat(consume(1L, 3).allowed()).isFalse();
        assertThat(consume(2L, 3).allowed()).isTrue();
    }

    @Test
    void policyChangeReplacesTheBucket() {
        consume(1L, 1);
        assertThat(consume(1L, 1).allowed()).isFalse();

        ConsumeResult afterUpgrade = consume(1L, 5);
        assertThat(afterUpgrade.allowed()).isTrue();
        assertThat(afterUpgrade.remainingTokens()).isEqualTo(4);
    }

    @Test
    void algorithmChangeSwapsLimiterType() {
        consume(1L, 1);
        assertThat(consume(1L, 1).allowed()).isFalse();

        LimitSpec window = LimitSpec.fixedWindow(2, 60);
        assertThat(limiter.tryConsume(1L, window).allowed()).isTrue();
        assertThat(limiter.tryConsume(1L, window).allowed()).isTrue();
        assertThat(limiter.tryConsume(1L, window).allowed()).isFalse();
    }

    @Test
    void resetGivesAFreshBucket() {
        consume(1L, 1);
        limiter.reset(1L);
        assertThat(consume(1L, 1).allowed()).isTrue();
    }

    @Test
    void concurrentFirstRequestsShareOneBucketPerClient() throws Exception {
        int clients = 10, capacity = 100, threads = 20, roundsPerThread = 50;
        // 20 threads x 50 rounds x 10 clients = 10,000 attempts; only 10 x 100 tokens exist.
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int r = 0; r < roundsPerThread; r++) {
                    for (long c = 1; c <= clients; c++) {
                        if (consume(c, capacity).allowed()) allowed.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(clients * capacity);
        assertThat(limiter.bucketCount()).isEqualTo(clients);
    }
}
