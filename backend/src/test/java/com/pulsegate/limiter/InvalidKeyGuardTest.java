package com.pulsegate.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidKeyGuardTest {

    // 3 failed attempts, refill so slow that none come back during the test.
    private final InvalidKeyGuard guard = new InvalidKeyGuard(3, 0.0001);

    @Test
    void blocksAnAddressAfterItsAllowanceIsUsed() {
        String ip = "203.0.113.7";
        for (int i = 0; i < 3; i++) {
            assertThat(guard.isBlocked(ip)).isFalse();
            guard.recordFailure(ip);
        }
        assertThat(guard.isBlocked(ip)).isTrue();
    }

    @Test
    void addressesAreIndependent() {
        for (int i = 0; i < 3; i++) guard.recordFailure("203.0.113.7");
        assertThat(guard.isBlocked("203.0.113.7")).isTrue();
        assertThat(guard.isBlocked("198.51.100.9")).isFalse();
    }

    @Test
    void unknownAddressIsNeverBlockedAndNotTracked() {
        assertThat(guard.isBlocked("192.0.2.1")).isFalse();
        assertThat(guard.trackedAddresses()).isZero();
    }

    @Test
    void recoversAsAttemptsRefill() throws Exception {
        InvalidKeyGuard fast = new InvalidKeyGuard(1, 50); // one attempt back every 20 ms
        fast.recordFailure("203.0.113.7");
        assertThat(fast.isBlocked("203.0.113.7")).isTrue();
        Thread.sleep(60);
        assertThat(fast.isBlocked("203.0.113.7")).isFalse();
    }

    @Test
    void retryAfterIsTimeForOneAttempt() {
        assertThat(new InvalidKeyGuard(30, 1.0).retryAfterSeconds()).isEqualTo(1);
        assertThat(new InvalidKeyGuard(30, 0.2).retryAfterSeconds()).isEqualTo(5);
        assertThat(new InvalidKeyGuard(30, 10).retryAfterSeconds()).isEqualTo(1); // never 0
    }

    @Test
    void dropsRecoveredAddressesWhenTrackingTooMany() {
        InvalidKeyGuard fast = new InvalidKeyGuard(1, 1_000_000); // refills almost instantly
        for (int i = 0; i <= InvalidKeyGuard.CLEANUP_THRESHOLD; i++) fast.recordFailure("10.0." + (i / 256) + "." + (i % 256));
        fast.recordFailure("203.0.113.7"); // triggers cleanup of the fully recovered entries
        assertThat(fast.trackedAddresses()).isLessThan(InvalidKeyGuard.CLEANUP_THRESHOLD);
    }
}
