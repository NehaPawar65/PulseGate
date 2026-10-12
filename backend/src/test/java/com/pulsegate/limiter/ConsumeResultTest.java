package com.pulsegate.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsumeResultTest {

    @Test
    void allowedHasNoRetryAfter() {
        assertThat(new ConsumeResult(true, 4, 0).retryAfterSeconds()).isZero();
    }

    @Test
    void retryAfterRoundsUpToWholeSeconds() {
        assertThat(new ConsumeResult(false, 0, 1).retryAfterSeconds()).isEqualTo(1);              // 1 ns
        assertThat(new ConsumeResult(false, 0, 1_000_000_000L).retryAfterSeconds()).isEqualTo(1); // exactly 1 s
        assertThat(new ConsumeResult(false, 0, 1_000_000_001L).retryAfterSeconds()).isEqualTo(2); // just over
    }

    @Test
    void rejectedNeverSaysRetryImmediately() {
        assertThat(new ConsumeResult(false, 0, 0).retryAfterSeconds()).isEqualTo(1);
    }
}
