package com.pulsegate.limiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final long SECOND = 1_000_000_000L;
    private final AtomicLong clock = new AtomicLong(0);

    private TokenBucket bucket(long capacity, double rate) {
        return new TokenBucket(capacity, rate, clock::get);
    }

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TokenBucket b = bucket(5, 1.0);
        for (int i = 4; i >= 0; i--) {
            ConsumeResult r = b.tryConsume();
            assertThat(r.allowed()).isTrue();
            assertThat(r.remainingTokens()).isEqualTo(i);
        }
        assertThat(b.tryConsume().allowed()).isFalse();
    }

    @Test
    void refillsOneTokenPerSecond() {
        TokenBucket b = bucket(5, 1.0);
        for (int i = 0; i < 5; i++) b.tryConsume();

        clock.addAndGet(SECOND);
        assertThat(b.tryConsume().allowed()).isTrue();
        assertThat(b.tryConsume().allowed()).isFalse();
    }

    @Test
    void neverRefillsBeyondCapacity() {
        TokenBucket b = bucket(5, 1.0);
        clock.addAndGet(3600 * SECOND);
        assertThat(b.availableTokens()).isEqualTo(5);
    }

    @Test
    void accumulatesFractionalTokens() {
        TokenBucket b = bucket(1, 2.0); // one token every 500 ms
        b.tryConsume();

        clock.addAndGet(SECOND / 4);
        assertThat(b.tryConsume().allowed()).isFalse(); // only 0.5 tokens

        clock.addAndGet(SECOND / 4);
        assertThat(b.tryConsume().allowed()).isTrue();  // now 1.0
    }

    @Test
    void reportsHowLongToWait() {
        TokenBucket b = bucket(1, 1.0);
        b.tryConsume();

        ConsumeResult rejected = b.tryConsume();
        assertThat(rejected.retryAfterNanos()).isEqualTo(SECOND);
        assertThat(rejected.retryAfterSeconds()).isEqualTo(1);

        clock.addAndGet(400_000_000L);
        assertThat(b.tryConsume().retryAfterNanos()).isEqualTo(600_000_000L);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> bucket(0, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucket(5, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentRequestsNeverSpendTheSameTokenTwice() throws Exception {
        TokenBucket b = bucket(1000, 1.0); // clock frozen -> no refill during test
        int threads = 50, attemptsPerThread = 100; // 5000 attempts for 1000 tokens
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await(); // release all threads at once for maximum contention
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (b.tryConsume().allowed()) allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(1000);
        assertThat(b.availableTokens()).isZero();
    }
}
