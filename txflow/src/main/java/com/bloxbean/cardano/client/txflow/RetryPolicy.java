package com.bloxbean.cardano.client.txflow;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * Retry policy for failed transaction steps.
 * <p>
 * Defines how and when to retry failed steps including:
 * <ul>
 *     <li>Maximum retry attempts</li>
 *     <li>Backoff strategy (fixed, linear, exponential)</li>
 *     <li>Initial and maximum delays between retries</li>
 *     <li>Which error types are retryable</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Default policy: 3 attempts with exponential backoff
 * RetryPolicy policy = RetryPolicy.defaults();
 *
 * // Custom policy
 * RetryPolicy policy = RetryPolicy.builder()
 *     .maxAttempts(5)
 *     .backoffStrategy(BackoffStrategy.EXPONENTIAL)
 *     .initialDelay(Duration.ofSeconds(2))
 *     .maxDelay(Duration.ofSeconds(60))
 *     .build();
 *
 * // No retry
 * RetryPolicy noRetry = RetryPolicy.noRetry();
 * }</pre>
 */
@Getter
@Builder
public class RetryPolicy {

    /**
     * Maximum number of retry attempts.
     * Defaults to 3.
     */
    @Builder.Default
    private final int maxAttempts = 3;

    /**
     * Backoff strategy for calculating delays between retries.
     * Defaults to EXPONENTIAL.
     */
    @Builder.Default
    private final BackoffStrategy backoffStrategy = BackoffStrategy.EXPONENTIAL;

    /**
     * Initial delay between retries.
     * Defaults to 1 second.
     */
    @Builder.Default
    private final Duration initialDelay = Duration.ofSeconds(1);

    /**
     * Maximum delay between retries.
     * Defaults to 30 seconds.
     */
    @Builder.Default
    private final Duration maxDelay = Duration.ofSeconds(30);

    /**
     * Whether to retry on timeout errors.
     * Defaults to true.
     */
    @Builder.Default
    private final boolean retryOnTimeout = true;

    /**
     * Whether to retry on network errors.
     * Defaults to true.
     */
    @Builder.Default
    private final boolean retryOnNetworkError = true;

    /**
     * Create a default retry policy.
     * <p>
     * Default settings:
     * <ul>
     *     <li>3 maximum attempts</li>
     *     <li>Exponential backoff</li>
     *     <li>1 second initial delay</li>
     *     <li>30 seconds max delay</li>
     *     <li>Retry on timeout and network errors</li>
     * </ul>
     *
     * @return default retry policy
     */
    public static RetryPolicy defaults() {
        return RetryPolicy.builder().build();
    }

    /**
     * Create a no-retry policy.
     * <p>
     * This policy has maxAttempts=1, meaning no retries will be performed.
     *
     * @return no-retry policy
     */
    public static RetryPolicy noRetry() {
        return RetryPolicy.builder().maxAttempts(1).build();
    }

    /**
     * Calculate the delay for a given attempt number.
     * <p>
     * Attempt numbers are 1-indexed (first attempt = 1).
     *
     * @param attemptNumber the current attempt number (1-indexed)
     * @return the delay duration for this attempt
     */
    public Duration calculateDelay(int attemptNumber) {
        if (attemptNumber < 1) {
            return initialDelay;
        }

        long delayMs;
        switch (backoffStrategy) {
            case FIXED:
                delayMs = initialDelay.toMillis();
                break;
            case LINEAR:
                delayMs = initialDelay.toMillis() * attemptNumber;
                break;
            case EXPONENTIAL:
                delayMs = initialDelay.toMillis() * (1L << (attemptNumber - 1));
                break;
            default:
                delayMs = initialDelay.toMillis();
        }

        return Duration.ofMillis(Math.min(delayMs, maxDelay.toMillis()));
    }

    /**
     * Check if an error is retryable based on this policy.
     * <p>
     * The following errors are NOT retryable:
     * <ul>
     *     <li>Insufficient funds</li>
     *     <li>Invalid transaction</li>
     *     <li>Already spent UTXOs</li>
     *     <li>Confirmation timeouts (transaction may still confirm later)</li>
     * </ul>
     * <p>
     * The following errors ARE retryable (if enabled in policy):
     * <ul>
     *     <li>Network timeout errors (if retryOnTimeout is true) - NOT confirmation timeouts</li>
     *     <li>Network/connection errors (if retryOnNetworkError is true)</li>
     * </ul>
     *
     * @param error the error to check
     * @return true if the error is retryable
     */
    public boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }

        // Check for ConfirmationTimeoutException (by class name to avoid circular dependency)
        // Also check wrapped causes in case of chained exceptions
        Throwable current = error;
        while (current != null) {
            if (current.getClass().getSimpleName().equals("ConfirmationTimeoutException")) {
                return false;
            }
            current = current.getCause();
        }

        String message = error.getMessage() != null ? error.getMessage().toLowerCase() : "";

        // Non-retryable errors - these are permanent failures
        if (message.contains("insufficient") ||
            message.contains("invalid") ||
            message.contains("already spent") ||
            message.contains("utxo not found") ||
            message.contains("bad request") ||
            message.contains("confirmation timeout")) {
            return false;
        }

        // Timeout errors (network/connection timeouts, NOT confirmation timeouts)
        if (retryOnTimeout && message.contains("timeout")) {
            return true;
        }

        // Network errors
        if (retryOnNetworkError && (
            message.contains("connection") ||
            message.contains("network") ||
            message.contains("socket") ||
            message.contains("reset") ||
            message.contains("refused"))) {
            return true;
        }

        // Non-recoverable JVM errors should never be retried
        if (error instanceof Error) {
            return false;
        }

        // Default: retry on unknown errors (transient issues)
        return true;
    }

    @Override
    public String toString() {
        return "RetryPolicy{" +
                "maxAttempts=" + maxAttempts +
                ", backoffStrategy=" + backoffStrategy +
                ", initialDelay=" + initialDelay +
                ", maxDelay=" + maxDelay +
                ", retryOnTimeout=" + retryOnTimeout +
                ", retryOnNetworkError=" + retryOnNetworkError +
                '}';
    }
}
