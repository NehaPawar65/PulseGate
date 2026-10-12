package com.pulsegate.exception;

public class TooManyInvalidKeyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyInvalidKeyAttemptsException(long retryAfterSeconds) {
        super("Too many requests with invalid API keys from this address. Retry after " + retryAfterSeconds + "s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
