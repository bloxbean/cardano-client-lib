package com.bloxbean.cardano.client.txflow.exec;

import java.time.Duration;

/**
 * @deprecated Import {@link com.bloxbean.cardano.client.txflow.config.ConfirmationConfig}
 * instead. This forwarding subclass is retained only for pre-release source compatibility.
 */
@Deprecated
public class ConfirmationConfig
        extends com.bloxbean.cardano.client.txflow.config.ConfirmationConfig {

    private ConfirmationConfig(int minConfirmations, Duration checkInterval,
                               Duration timeout, int maxRollbackRetries,
                               boolean waitForBackendAfterRollback,
                               int postRollbackWaitAttempts,
                               Duration postRollbackUtxoSyncDelay,
                               int requiredAuthoritativeAbsences) {
        super(minConfirmations, checkInterval, timeout, maxRollbackRetries,
                waitForBackendAfterRollback, postRollbackWaitAttempts,
                postRollbackUtxoSyncDelay, requiredAuthoritativeAbsences);
    }

    /**
     * Creates the deprecated forwarding builder.
     *
     * @return compatibility builder producing this forwarding type
     */
    public static ConfirmationConfigBuilder builder() {
        return new ConfirmationConfigBuilder();
    }

    /**
     * Returns the default confirmation settings using the compatibility type.
     *
     * @return default settings
     */
    public static ConfirmationConfig defaults() {
        return builder().build();
    }

    /**
     * Returns settings tuned for a fast local development network.
     *
     * @return development-network settings
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
     * Returns conservative settings suitable for a public test network.
     *
     * @return test-network settings
     */
    public static ConfirmationConfig testnet() {
        return builder()
                .minConfirmations(6)
                .checkInterval(Duration.ofSeconds(3))
                .timeout(Duration.ofMinutes(10))
                .build();
    }

    /**
     * Returns a low-latency preset that accepts one confirmation.
     *
     * @return quick confirmation settings
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

    /** @deprecated Use {@code ConfirmationConfig.Builder} in the config package. */
    @Deprecated
    public static class ConfirmationConfigBuilder
            extends com.bloxbean.cardano.client.txflow.config.ConfirmationConfig.Builder {
        @Override
        public ConfirmationConfigBuilder minConfirmations(int value) {
            super.minConfirmations(value);
            return this;
        }

        @Override
        public ConfirmationConfigBuilder checkInterval(Duration value) {
            super.checkInterval(value);
            return this;
        }

        @Override
        public ConfirmationConfigBuilder timeout(Duration value) {
            super.timeout(value);
            return this;
        }

        @Override
        public ConfirmationConfigBuilder maxRollbackRetries(int value) {
            super.maxRollbackRetries(value);
            return this;
        }

        @Override
        public ConfirmationConfigBuilder waitForBackendAfterRollback(boolean value) {
            super.waitForBackendAfterRollback(value);
            return this;
        }

        @Override
        public ConfirmationConfigBuilder postRollbackWaitAttempts(int value) {
            super.postRollbackWaitAttempts(value);
            return this;
        }

        @Override
        public ConfirmationConfigBuilder postRollbackUtxoSyncDelay(Duration value) {
            super.postRollbackUtxoSyncDelay(value);
            return this;
        }

        @Override
        public ConfirmationConfigBuilder requiredAuthoritativeAbsences(int value) {
            super.requiredAuthoritativeAbsences(value);
            return this;
        }

        @Override
        protected com.bloxbean.cardano.client.txflow.config.ConfirmationConfig create(
                int minConfirmations, Duration checkInterval, Duration timeout,
                int maxRollbackRetries, boolean waitForBackendAfterRollback,
                int postRollbackWaitAttempts, Duration postRollbackUtxoSyncDelay,
                int requiredAuthoritativeAbsences) {
            return new ConfirmationConfig(minConfirmations, checkInterval, timeout,
                    maxRollbackRetries, waitForBackendAfterRollback,
                    postRollbackWaitAttempts, postRollbackUtxoSyncDelay,
                    requiredAuthoritativeAbsences);
        }

        @Override
        public ConfirmationConfig build() {
            return (ConfirmationConfig) super.build();
        }
    }
}
