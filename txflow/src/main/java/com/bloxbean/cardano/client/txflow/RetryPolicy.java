package com.bloxbean.cardano.client.txflow;

import lombok.Getter;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import com.bloxbean.cardano.client.txflow.config.RetryAction;
import com.bloxbean.cardano.client.txflow.config.RetryContext;
import com.bloxbean.cardano.client.txflow.config.RetryDecision;
import com.bloxbean.cardano.client.txflow.config.SubmissionOutcome;
import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;

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
 * <h2>Example Usage:</h2>
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
public class RetryPolicy {

    /**
     * Maximum number of retry attempts.
     * Defaults to 3.
     */
    private final int maxAttempts;

    /**
     * Backoff strategy for calculating delays between retries.
     * Defaults to EXPONENTIAL.
     */
    private final BackoffStrategy backoffStrategy;

    /**
     * Initial delay between retries.
     * Defaults to 1 second.
     */
    private final Duration initialDelay;

    /**
     * Maximum delay between retries.
     * Defaults to 30 seconds.
     */
    private final Duration maxDelay;

    /**
     * Whether to retry on timeout errors.
     * Defaults to true.
     */
    private final boolean retryOnTimeout;

    /**
     * Whether to retry on network errors.
     * Defaults to true.
     */
    private final boolean retryOnNetworkError;

    /** Fractional randomization applied to retry delays. */
    private final double jitterFactor;

    private RetryPolicy(int maxAttempts, BackoffStrategy backoffStrategy,
                        Duration initialDelay, Duration maxDelay,
                        boolean retryOnTimeout, boolean retryOnNetworkError,
                        double jitterFactor) {
        this.maxAttempts = maxAttempts;
        this.backoffStrategy = backoffStrategy;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.retryOnTimeout = retryOnTimeout;
        this.retryOnNetworkError = retryOnNetworkError;
        this.jitterFactor = jitterFactor;
    }

    public static RetryPolicyBuilder builder() {
        return new RetryPolicyBuilder();
    }

    public static class RetryPolicyBuilder {
        private int maxAttempts = 3;
        private BackoffStrategy backoffStrategy = BackoffStrategy.EXPONENTIAL;
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofSeconds(30);
        private boolean retryOnTimeout = true;
        private boolean retryOnNetworkError = true;
        private double jitterFactor = 0.20d;

        public RetryPolicyBuilder maxAttempts(int value) {
            this.maxAttempts = value;
            return this;
        }

        public RetryPolicyBuilder backoffStrategy(BackoffStrategy value) {
            this.backoffStrategy = value;
            return this;
        }

        public RetryPolicyBuilder initialDelay(Duration value) {
            this.initialDelay = value;
            return this;
        }

        public RetryPolicyBuilder maxDelay(Duration value) {
            this.maxDelay = value;
            return this;
        }

        public RetryPolicyBuilder retryOnTimeout(boolean value) {
            this.retryOnTimeout = value;
            return this;
        }

        public RetryPolicyBuilder retryOnNetworkError(boolean value) {
            this.retryOnNetworkError = value;
            return this;
        }

        public RetryPolicyBuilder jitterFactor(double value) {
            this.jitterFactor = value;
            return this;
        }

        public RetryPolicy build() {
            Objects.requireNonNull(backoffStrategy, "backoffStrategy");
            Objects.requireNonNull(initialDelay, "initialDelay");
            Objects.requireNonNull(maxDelay, "maxDelay");
            if (maxAttempts < 1 || maxAttempts > 100) {
                throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
            }
            if (initialDelay.isNegative() || maxDelay.isNegative()) {
                throw new IllegalArgumentException("retry delays cannot be negative");
            }
            if (initialDelay.compareTo(maxDelay) > 0) {
                throw new IllegalArgumentException("initialDelay cannot exceed maxDelay");
            }
            Duration maximumSupportedDelay = Duration.ofDays(365);
            if (maxDelay.compareTo(maximumSupportedDelay) > 0) {
                throw new IllegalArgumentException("maxDelay cannot exceed 365 days");
            }
            if (!Double.isFinite(jitterFactor) || jitterFactor < 0 || jitterFactor > 1) {
                throw new IllegalArgumentException("jitterFactor must be between 0 and 1");
            }
            return new RetryPolicy(maxAttempts, backoffStrategy, initialDelay, maxDelay,
                    retryOnTimeout, retryOnNetworkError, jitterFactor);
        }
    }

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

        long initialMillis = initialDelay.toMillis();
        long maximumMillis = maxDelay.toMillis();
        long delayMs;
        switch (backoffStrategy) {
            case FIXED:
                delayMs = initialMillis;
                break;
            case LINEAR:
                delayMs = saturatingMultiply(initialMillis, attemptNumber, maximumMillis);
                break;
            case EXPONENTIAL:
                delayMs = attemptNumber >= 63
                        ? maximumMillis
                        : saturatingMultiply(initialMillis, 1L << (attemptNumber - 1), maximumMillis);
                break;
            default:
                delayMs = initialMillis;
        }

        long cappedDelay = Math.min(delayMs, maximumMillis);
        if (jitterFactor == 0 || cappedDelay == 0) {
            return Duration.ofMillis(cappedDelay);
        }
        double multiplier = ThreadLocalRandom.current().nextDouble(1 - jitterFactor, 1 + jitterFactor);
        long jitteredDelay = Math.round(cappedDelay * multiplier);
        return Duration.ofMillis(Math.min(Math.max(0, jitteredDelay), maximumMillis));
    }

    private static long saturatingMultiply(long left, long right, long cap) {
        if (left == 0 || right == 0) {
            return 0;
        }
        if (right > cap / left) {
            return cap;
        }
        return left * right;
    }

    /** Phase-aware server retry decision; unknown submission is always reconciled first. */
    public RetryDecision evaluate(RetryContext context) {
        Objects.requireNonNull(context, "context");
        if (context.submissionOutcome() == SubmissionOutcome.UNKNOWN
                || context.submissionOutcome() == SubmissionOutcome.ACCEPTED) {
            return new RetryDecision(RetryAction.RECONCILE_THEN_RETRY,
                    calculateDelay(context.attempt()),
                    context.submissionOutcome() == SubmissionOutcome.UNKNOWN
                            ? "TXFLOW_SUBMISSION_UNKNOWN" : "TXFLOW_SUBMISSION_ACCEPTED");
        }
        if (context.attempt() >= maxAttempts) {
            return new RetryDecision(RetryAction.FAIL, Duration.ZERO, "TXFLOW_RETRY_EXHAUSTED");
        }
        if (context.submissionOutcome() == SubmissionOutcome.REJECTED) {
            return new RetryDecision(RetryAction.FAIL, Duration.ZERO,
                    "TXFLOW_SUBMISSION_REJECTED");
        }
        boolean transientCategory = context.category() == FlowErrorCategory.NETWORK
                || context.category() == FlowErrorCategory.BACKEND_UNAVAILABLE;
        if (!transientCategory) {
            return new RetryDecision(RetryAction.FAIL, Duration.ZERO,
                    "TXFLOW_RETRY_CATEGORY_NOT_ALLOWED");
        }
        if (context.transactionHash() != null) {
            return new RetryDecision(RetryAction.RETRY_SAME_TRANSACTION,
                    calculateDelay(context.attempt()), "TXFLOW_RETRY_IDENTICAL_TRANSACTION");
        }
        return new RetryDecision(RetryAction.REBUILD_STEP,
                calculateDelay(context.attempt()), "TXFLOW_RETRY_BEFORE_SIGNING");
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
     * <p>This legacy adapter has no execution-phase context, so unknown failures
     * are rejected conservatively. Callers should expose known transient failures
     * as a typed {@link IOException} or {@link TimeoutException}; internal runtime
     * code can use {@link #evaluate(RetryContext)} when phase and submission state
     * are available.</p>
     *
     * @param error the error to check
     * @return true if the error is retryable
     */
    public boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }

        // Inspect the complete cause chain, with fatal JVM errors taking precedence.
        Throwable current = error;
        while (current != null) {
            if (current instanceof Error) {
                return false;
            }
            if (current.getClass().getSimpleName().equals("ConfirmationTimeoutException")) {
                return false;
            }
            if (current instanceof IllegalArgumentException) {
                return false;
            }
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return retryOnTimeout;
            }
            if (current instanceof SocketException || current instanceof IOException) {
                return retryOnNetworkError;
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

        // Without phase context, an unknown failure may follow a successful submission.
        return false;
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
