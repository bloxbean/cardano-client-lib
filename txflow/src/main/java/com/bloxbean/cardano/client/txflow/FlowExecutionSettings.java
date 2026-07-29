package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.RollbackPolicy;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.config.ValidityPolicy;

/**
 * @deprecated Import {@link com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings}
 * instead. This forwarding subclass is retained for pre-release source compatibility.
 */
@Deprecated
public class FlowExecutionSettings
        extends com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings {
    private static final FlowExecutionSettings EMPTY = builder().build();

    /**
     * Creates the forwarding value used by the compatibility builder.
     *
     * @param chainingMode requested chaining mode
     * @param confirmationConfig requested confirmation settings
     * @param rollbackStrategy legacy rollback strategy
     * @param retryPolicy requested retry policy
     * @param rollbackPolicy portable rollback policy
     * @param validityPolicy requested validity policy
     */
    protected FlowExecutionSettings(ChainingMode chainingMode,
                                    ConfirmationConfig confirmationConfig,
                                    RollbackStrategy rollbackStrategy,
                                    RetryPolicy retryPolicy,
                                    RollbackPolicy rollbackPolicy,
                                    ValidityPolicy validityPolicy) {
        super(chainingMode, confirmationConfig, rollbackStrategy, retryPolicy,
                rollbackPolicy, validityPolicy);
    }

    /**
     * Creates the deprecated forwarding builder.
     *
     * @return compatibility builder producing this forwarding type
     */
    public static FlowExecutionSettingsBuilder builder() {
        return new FlowExecutionSettingsBuilder();
    }

    /**
     * Returns a shared value containing no flow-level overrides.
     *
     * @return empty compatibility settings
     */
    public static FlowExecutionSettings empty() {
        return EMPTY;
    }

    @Override
    public FlowExecutionSettingsBuilder toBuilder() {
        return builder()
                .chainingMode(getChainingMode())
                .confirmationConfig(getConfirmationConfig())
                .rollbackStrategy(getRollbackStrategy())
                .retryPolicy(getRetryPolicy())
                .rollbackPolicy(getRollbackPolicy())
                .validityPolicy(getValidityPolicy());
    }

    /** @deprecated Use the builder in the config package. */
    @Deprecated
    public static class FlowExecutionSettingsBuilder extends
            com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings.FlowExecutionSettingsBuilder {
        @Override
        public FlowExecutionSettingsBuilder chainingMode(ChainingMode value) {
            super.chainingMode(value);
            return this;
        }

        @Override
        public FlowExecutionSettingsBuilder confirmationConfig(ConfirmationConfig value) {
            super.confirmationConfig(value);
            return this;
        }

        @Override
        public FlowExecutionSettingsBuilder rollbackStrategy(RollbackStrategy value) {
            super.rollbackStrategy(value);
            return this;
        }

        @Override
        public FlowExecutionSettingsBuilder retryPolicy(RetryPolicy value) {
            super.retryPolicy(value);
            return this;
        }

        @Override
        public FlowExecutionSettingsBuilder rollbackPolicy(RollbackPolicy value) {
            super.rollbackPolicy(value);
            return this;
        }

        @Override
        public FlowExecutionSettingsBuilder validityPolicy(ValidityPolicy value) {
            super.validityPolicy(value);
            return this;
        }

        @Override
        public FlowExecutionSettings build() {
            return new FlowExecutionSettings(chainingMode, confirmationConfig,
                    rollbackStrategy, retryPolicy, rollbackPolicy, validityPolicy);
        }
    }
}
