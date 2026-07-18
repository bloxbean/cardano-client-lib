package com.bloxbean.cardano.client.txflow.config;

import lombok.Getter;

import java.time.Duration;
import java.util.Objects;

/**
 * Public configuration for transaction confirmation and legacy rollback tracking.
 *
 * <p>This is the canonical owner of confirmation configuration. Runtime code
 * consumes this type; the deprecated {@code txflow.exec} class is only a
 * source-compatible forwarding subclass. Backend-specific observation authority
 * is intentionally not configurable here: an empty lookup counts toward rollback
 * only when the backend adapter itself declares authoritative absence support.</p>
 *
 * <p>The post-rollback backend wait fields support development environments in
 * which a rollback also restarts the backend or delays its UTXO index. They are
 * compatibility settings rather than part of the portable {@link RollbackPolicy}.</p>
 */
@Getter
public class ConfirmationConfig {
    /** Required depth after inclusion; zero accepts the first in-block observation. */
    private final int minConfirmations;
    /** Interval between backend observations. */
    private final Duration checkInterval;
    /** Maximum time spent by one confirmation wait. */
    private final Duration timeout;
    /** Legacy bound for rollback re-poll or rebuild cycles. */
    private final int maxRollbackRetries;
    /** Whether to wait for backend readiness after a rollback-handling restart. */
    private final boolean waitForBackendAfterRollback;
    /** Maximum backend-readiness checks after rollback. */
    private final int postRollbackWaitAttempts;
    /** Additional delay for a backend UTXO index to catch up after it is ready. */
    private final Duration postRollbackUtxoSyncDelay;
    /** Consecutive authoritative absences required to establish rollback. */
    private final int requiredAuthoritativeAbsences;

    /**
     * Initializes a configuration from values validated by a builder.
     *
     * <p>The constructor is protected so the deprecated forwarding type can
     * preserve source compatibility; applications should use {@link #builder()}.</p>
     *
     * @param minConfirmations required confirmation depth
     * @param checkInterval interval between observations
     * @param timeout maximum confirmation wait
     * @param maxRollbackRetries legacy rollback retry bound
     * @param waitForBackendAfterRollback whether to wait for backend readiness
     * @param postRollbackWaitAttempts backend-readiness attempt limit
     * @param postRollbackUtxoSyncDelay delay for UTXO-index synchronization
     * @param requiredAuthoritativeAbsences authoritative-absence threshold
     */
    protected ConfirmationConfig(int minConfirmations, Duration checkInterval,
                                 Duration timeout, int maxRollbackRetries,
                                 boolean waitForBackendAfterRollback,
                                 int postRollbackWaitAttempts,
                                 Duration postRollbackUtxoSyncDelay,
                                 int requiredAuthoritativeAbsences) {
        this.minConfirmations = minConfirmations;
        this.checkInterval = checkInterval;
        this.timeout = timeout;
        this.maxRollbackRetries = maxRollbackRetries;
        this.waitForBackendAfterRollback = waitForBackendAfterRollback;
        this.postRollbackWaitAttempts = postRollbackWaitAttempts;
        this.postRollbackUtxoSyncDelay = postRollbackUtxoSyncDelay;
        this.requiredAuthoritativeAbsences = requiredAuthoritativeAbsences;
    }

    /**
     * Starts a configuration with production-oriented defaults.
     *
     * @return confirmation configuration builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the default configuration: depth 10, five-second polling, and a
     * 30-minute timeout.
     *
     * @return default confirmation configuration
     */
    public static ConfirmationConfig defaults() {
        return builder().build();
    }

    /**
     * Returns a fast-polling configuration that enables post-rollback backend
     * readiness waits for local development networks.
     *
     * @return development-network configuration
     */
    public static ConfirmationConfig devnet() {
        return builder()
                .minConfirmations(3)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(5))
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Returns a six-confirmation, ten-minute configuration suitable as a
     * conservative public-test-network starting point.
     *
     * @return test-network configuration
     */
    public static ConfirmationConfig testnet() {
        return builder()
                .minConfirmations(6)
                .checkInterval(Duration.ofSeconds(3))
                .timeout(Duration.ofMinutes(10))
                .build();
    }

    /**
     * Returns a one-confirmation preset with short polling and development
     * backend readiness waits.
     *
     * @return quick confirmation configuration
     */
    public static ConfirmationConfig quick() {
        return builder()
                .minConfirmations(1)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofMinutes(2))
                .waitForBackendAfterRollback(true)
                .postRollbackWaitAttempts(30)
                .postRollbackUtxoSyncDelay(Duration.ofSeconds(3))
                .build();
    }

    /** Explicit builder whose stable public shape does not depend on Lombok internals. */
    public static class Builder {
        private int minConfirmations = 10;
        private Duration checkInterval = Duration.ofSeconds(5);
        private Duration timeout = Duration.ofMinutes(30);
        private int maxRollbackRetries = 3;
        private boolean waitForBackendAfterRollback;
        private int postRollbackWaitAttempts = 5;
        private Duration postRollbackUtxoSyncDelay = Duration.ZERO;
        private int requiredAuthoritativeAbsences = 2;

        /** Creates a builder initialized with {@link ConfirmationConfig#defaults()} values. */
        public Builder() {
        }

        /**
         * Sets the confirmation depth required after inclusion.
         *
         * @param value depth from 0 through 100,000
         * @return this builder
         */
        public Builder minConfirmations(int value) {
            this.minConfirmations = value;
            return this;
        }

        /**
         * Sets the interval between transaction observations.
         *
         * @param value positive interval no longer than one day
         * @return this builder
         */
        public Builder checkInterval(Duration value) {
            this.checkInterval = value;
            return this;
        }

        /**
         * Sets the maximum duration of a confirmation wait.
         *
         * @param value positive timeout no longer than 365 days
         * @return this builder
         */
        public Builder timeout(Duration value) {
            this.timeout = value;
            return this;
        }

        /**
         * Sets the legacy rollback retry or re-poll bound.
         *
         * @param value retry bound from 0 through 100
         * @return this builder
         */
        public Builder maxRollbackRetries(int value) {
            this.maxRollbackRetries = value;
            return this;
        }

        /**
         * Enables the development-oriented backend readiness wait after rollback.
         *
         * @param value whether to perform the wait
         * @return this builder
         */
        public Builder waitForBackendAfterRollback(boolean value) {
            this.waitForBackendAfterRollback = value;
            return this;
        }

        /**
         * Sets the maximum backend-readiness observations after rollback.
         *
         * @param value attempt bound from 0 through 10,000
         * @return this builder
         */
        public Builder postRollbackWaitAttempts(int value) {
            this.postRollbackWaitAttempts = value;
            return this;
        }

        /**
         * Sets an additional UTXO-index synchronization delay after backend readiness.
         *
         * @param value non-negative delay no longer than one day
         * @return this builder
         */
        public Builder postRollbackUtxoSyncDelay(Duration value) {
            this.postRollbackUtxoSyncDelay = value;
            return this;
        }

        /**
         * Sets the consecutive authoritative-absence threshold for rollback.
         *
         * @param value threshold from 1 through 100
         * @return this builder
         */
        public Builder requiredAuthoritativeAbsences(int value) {
            this.requiredAuthoritativeAbsences = value;
            return this;
        }

        /**
         * Validates the configured bounds and creates an immutable configuration.
         *
         * @return confirmation configuration
         * @throws NullPointerException if a duration is {@code null}
         * @throws IllegalArgumentException if a numeric or duration bound is invalid
         */
        public ConfirmationConfig build() {
            Objects.requireNonNull(checkInterval, "checkInterval");
            Objects.requireNonNull(timeout, "timeout");
            Objects.requireNonNull(postRollbackUtxoSyncDelay, "postRollbackUtxoSyncDelay");
            if (minConfirmations < 0 || minConfirmations > 100_000) {
                throw new IllegalArgumentException("minConfirmations must be between 0 and 100000");
            }
            if (checkInterval.isZero() || checkInterval.isNegative()
                    || checkInterval.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException("checkInterval must be positive and at most one day");
            }
            if (timeout.isZero() || timeout.isNegative()
                    || timeout.compareTo(Duration.ofDays(365)) > 0) {
                throw new IllegalArgumentException("timeout must be positive and at most 365 days");
            }
            if (maxRollbackRetries < 0 || maxRollbackRetries > 100) {
                throw new IllegalArgumentException("maxRollbackRetries must be between 0 and 100");
            }
            if (postRollbackWaitAttempts < 0 || postRollbackWaitAttempts > 10_000) {
                throw new IllegalArgumentException("postRollbackWaitAttempts must be between 0 and 10000");
            }
            if (postRollbackUtxoSyncDelay.isNegative()
                    || postRollbackUtxoSyncDelay.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException(
                        "postRollbackUtxoSyncDelay must be between zero and one day");
            }
            if (requiredAuthoritativeAbsences < 1 || requiredAuthoritativeAbsences > 100) {
                throw new IllegalArgumentException(
                        "requiredAuthoritativeAbsences must be between 1 and 100");
            }
            return create(minConfirmations, checkInterval, timeout, maxRollbackRetries,
                    waitForBackendAfterRollback, postRollbackWaitAttempts,
                    postRollbackUtxoSyncDelay, requiredAuthoritativeAbsences);
        }

        /**
         * Factory hook used by the deprecated forwarding builder to retain its
         * concrete return type.
         *
         * @param minConfirmations required confirmation depth
         * @param checkInterval interval between observations
         * @param timeout maximum confirmation wait
         * @param maxRollbackRetries legacy rollback retry bound
         * @param waitForBackendAfterRollback whether to wait for backend readiness
         * @param postRollbackWaitAttempts backend-readiness attempt limit
         * @param postRollbackUtxoSyncDelay UTXO-index synchronization delay
         * @param requiredAuthoritativeAbsences authoritative-absence threshold
         * @return new confirmation configuration
         */
        protected ConfirmationConfig create(int minConfirmations, Duration checkInterval,
                                            Duration timeout, int maxRollbackRetries,
                                            boolean waitForBackendAfterRollback,
                                            int postRollbackWaitAttempts,
                                            Duration postRollbackUtxoSyncDelay,
                                            int requiredAuthoritativeAbsences) {
            return new ConfirmationConfig(minConfirmations, checkInterval, timeout,
                    maxRollbackRetries, waitForBackendAfterRollback,
                    postRollbackWaitAttempts, postRollbackUtxoSyncDelay,
                    requiredAuthoritativeAbsences);
        }
    }
}
