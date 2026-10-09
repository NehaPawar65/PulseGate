package com.pulsegate.limiter;

public record ConsumeResult(boolean allowed, long remainingTokens, long retryAfterNanos) {

    /** Whole seconds for the Retry-After header; at least 1 when rejected. */
    public long retryAfterSeconds() {
        if (allowed) return 0;
        return Math.max(1, (long) Math.ceil(retryAfterNanos / 1_000_000_000.0));
    }
}
