package com.pulsegate.exception;

public class RateLimitExceededException extends RuntimeException {

    private final long limit;
    private final long retryAfterSeconds;

    public RateLimitExceededException(long limit, long retryAfterSeconds) {
        super("Rate limit of " + limit + " requests exceeded. Retry after " + retryAfterSeconds + "s.");
        this.limit = limit;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getLimit() { return limit; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
