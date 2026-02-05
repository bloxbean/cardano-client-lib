package com.bloxbean.cardano.client.txflow;

/**
 * Backoff strategies for retry delays.
 * <p>
 * Used by {@link RetryPolicy} to determine how long to wait between retry attempts.
 */
public enum BackoffStrategy {
    /**
     * Fixed delay between retries.
     * The same delay is used for every attempt.
     */
    FIXED,

    /**
     * Linearly increasing delay.
     * Delay = initialDelay * attemptNumber
     */
    LINEAR,

    /**
     * Exponentially increasing delay.
     * Delay = initialDelay * 2^(attemptNumber - 1)
     */
    EXPONENTIAL
}
