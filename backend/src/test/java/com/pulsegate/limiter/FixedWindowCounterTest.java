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

class FixedWindowCounterTest {

    private static final long SECOND = 1_000_000_000L;
    private final AtomicLong clock = new AtomicLong(0);

    private FixedWindowCounter counter(long capacity, long windowSeconds) {
        return new FixedWindowCounter(capacity, windowSeconds, clock::get);
    }

    private int consumeAll(FixedWindowCounter c, int attempts) {
        int allowed = 0;
        for (int i = 0; i < attempts; i++) {
            if (c.tryConsume().allowed()) allowed++;
        }
        return allowed;
    }

    @Test
    void allowsCapacityPerWindowThenRejects() {
        FixedWindowCounter c = counter(3, 10);
        for (int remaining = 2; remaining >= 0; remaining--) {
            ConsumeResult r = c.tryConsume();
            assertThat(r.allowed()).isTrue();
            assertThat(r.remainingTokens()).isEqualTo(remaining);
        }
        assertThat(c.tryConsume().allowed()).isFalse();
    }

    @Test
    void noPartialRefillInsideAWindow() {
        FixedWindowCounter c = counter(3, 10);
        consumeAll(c, 3);
        clock.addAndGet(9 * SECOND); // a token bucket would have refilled by now
        assertThat(c.tryConsume().allowed()).isFalse();
    }

    @Test
    void fullQuotaReturnsAtTheNextWindow() {
        FixedWindowCounter c = counter(3, 10);
        consumeAll(c, 3);
        clock.addAndGet(10 * SECOND);
        assertThat(consumeAll(c, 5)).isEqualTo(3);
    }

    @Test
    void windowsStayAlignedAfterIdlePeriods() {
        FixedWindowCounter c = counter(1, 10);
        clock.addAndGet(25 * SECOND); // windows are [0,10) [10,20) [20,30): now at 25
        assertThat(c.tryConsume().allowed()).isTrue();

        ConsumeResult rejected = c.tryConsume();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterNanos()).isEqualTo(5 * SECOND); // until 30, not 35
    }

    @Test
    void reportsTimeUntilWindowEnds() {
        FixedWindowCounter c = counter(1, 60);
        c.tryConsume();
        clock.addAndGet(45 * SECOND);
        ConsumeResult r = c.tryConsume();
        assertThat(r.retryAfterNanos()).isEqualTo(15 * SECOND);
        assertThat(r.retryAfterSeconds()).isEqualTo(15);
    }

    /** Documents the algorithm's known weakness rather than hiding it. */
    @Test
    void allowsTwiceCapacityAcrossAWindowBoundary() {
        FixedWindowCounter c = counter(5, 1);
        clock.set(900_000_000L);                // 0.9 s: end of window 1
        int before = consumeAll(c, 5);
        clock.set(1_100_000_000L);              // 1.1 s: start of window 2
        int after = consumeAll(c, 5);

        assertThat(before + after).isEqualTo(10); // 2x capacity within 0.2 s

        clock.set(900_000_000L);
        TokenBucket bucket = new TokenBucket(5, 5.0, clock::get); // same average rate: 5/s, full at 0.9 s
        int bucketAllowed = 0;
        for (int i = 0; i < 5; i++) if (bucket.tryConsume().allowed()) bucketAllowed++;
        clock.set(1_100_000_000L);
        for (int i = 0; i < 5; i++) if (bucket.tryConsume().allowed()) bucketAllowed++;
        assertThat(bucketAllowed).isEqualTo(6); // 5 burst + 1 refilled in 0.2 s
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> counter(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> counter(5, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentRequestsNeverExceedCapacity() throws Exception {
        FixedWindowCounter c = counter(1000, 60); // clock frozen inside one window
        int threads = 50, attemptsPerThread = 100;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (c.tryConsume().allowed()) allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(1000);
        assertThat(c.availableTokens()).isZero();
    }
}
