package com.bloxbean.cardano.client.txflow.config;

import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import lombok.Getter;

/**
 * Optional execution requests carried by a portable flow definition.
 *
 * <p>Every field is nullable: an unset field makes no flow-level request and
 * leaves the host executor or runtime default in control. For server execution,
 * {@link FlowExecutionPolicy} evaluates these requested values before they
 * become effective settings; numeric limits may be capped and semantic choices
 * may be rejected.</p>
 *
 * <p>{@link RollbackPolicy} is the portable rollback model and
 * {@link RollbackStrategy} is retained for the original executor API. New code
 * should set only the portable policy. If both are supplied to the legacy
 * executor, the legacy strategy controls action selection.</p>
 */
@Getter
public class FlowExecutionSettings {
    private static final FlowExecutionSettings EMPTY = builder().build();

    private final ChainingMode chainingMode;
    private final ConfirmationConfig confirmationConfig;
    private final RollbackStrategy rollbackStrategy;
    private final RetryPolicy retryPolicy;
    private final RollbackPolicy rollbackPolicy;
    private final ValidityPolicy validityPolicy;

    /**
     * Initializes a settings snapshot for this class or a compatibility subclass.
     *
     * @param chainingMode requested execution mode, or {@code null}
     * @param confirmationConfig requested confirmation settings, or {@code null}
     * @param rollbackStrategy legacy rollback strategy, or {@code null}
     * @param retryPolicy requested step retry policy, or {@code null}
     * @param rollbackPolicy portable rollback policy, or {@code null}
     * @param validityPolicy requested validity preferences, or {@code null}
     */
    protected FlowExecutionSettings(ChainingMode chainingMode,
                                    ConfirmationConfig confirmationConfig,
                                    RollbackStrategy rollbackStrategy,
                                    RetryPolicy retryPolicy,
                                    RollbackPolicy rollbackPolicy,
                                    ValidityPolicy validityPolicy) {
        this.chainingMode = chainingMode;
        this.confirmationConfig = confirmationConfig;
        this.rollbackStrategy = rollbackStrategy;
        this.retryPolicy = retryPolicy;
        this.rollbackPolicy = rollbackPolicy;
        this.validityPolicy = validityPolicy;
    }

    /**
     * Starts a builder in which every flow-level setting is absent.
     *
     * @return settings builder
     */
    public static FlowExecutionSettingsBuilder builder() {
        return new FlowExecutionSettingsBuilder();
    }

    /**
     * Returns the shared settings instance containing no requested overrides.
     *
     * @return empty settings
     */
    public static FlowExecutionSettings empty() {
        return EMPTY;
    }

    /**
     * Copies these requested values into a mutable builder.
     *
     * @return builder initialized from this instance
     */
    public FlowExecutionSettingsBuilder toBuilder() {
        return builder()
                .chainingMode(chainingMode)
                .confirmationConfig(confirmationConfig)
                .rollbackStrategy(rollbackStrategy)
                .retryPolicy(retryPolicy)
                .rollbackPolicy(rollbackPolicy)
                .validityPolicy(validityPolicy);
    }

    /**
     * Reports whether the flow requests at least one execution setting.
     *
     * @return {@code true} when any setting is non-null
     */
    public boolean hasAnySetting() {
        return chainingMode != null
                || confirmationConfig != null
                || rollbackStrategy != null
                || retryPolicy != null
                || rollbackPolicy != null
                || validityPolicy != null;
    }

    /** Explicit builder kept stable as part of the public configuration API. */
    public static class FlowExecutionSettingsBuilder {
        /** Requested execution mode, or {@code null} when unset. */
        protected ChainingMode chainingMode;
        /** Requested confirmation settings, or {@code null} when unset. */
        protected ConfirmationConfig confirmationConfig;
        /** Legacy rollback strategy, or {@code null} when unset. */
        protected RollbackStrategy rollbackStrategy;
        /** Requested retry policy, or {@code null} when unset. */
        protected RetryPolicy retryPolicy;
        /** Portable rollback policy, or {@code null} when unset. */
        protected RollbackPolicy rollbackPolicy;
        /** Requested validity preferences, or {@code null} when unset. */
        protected ValidityPolicy validityPolicy;

        /** Creates a builder with every flow-level request unset. */
        public FlowExecutionSettingsBuilder() {
        }

        /**
         * Requests an execution mode for the flow.
         *
         * @param value chaining mode, or {@code null} to leave it unset
         * @return this builder
         */
        public FlowExecutionSettingsBuilder chainingMode(ChainingMode value) {
            this.chainingMode = value;
            return this;
        }

        /**
         * Requests confirmation tracking settings for the flow.
         *
         * @param value confirmation settings, or {@code null} to leave them unset
         * @return this builder
         */
        public FlowExecutionSettingsBuilder confirmationConfig(ConfirmationConfig value) {
            this.confirmationConfig = value;
            return this;
        }

        /**
         * Sets the rollback strategy used by the legacy executor API.
         *
         * @param value legacy strategy, or {@code null} to leave it unset
         * @return this builder
         */
        public FlowExecutionSettingsBuilder rollbackStrategy(RollbackStrategy value) {
            this.rollbackStrategy = value;
            return this;
        }

        /**
         * Requests a retry policy for failed transaction steps.
         *
         * @param value retry policy, or {@code null} to leave it unset
         * @return this builder
         */
        public FlowExecutionSettingsBuilder retryPolicy(RetryPolicy value) {
            this.retryPolicy = value;
            return this;
        }

        /**
         * Requests portable rollback behavior for the flow.
         *
         * @param value rollback policy, or {@code null} to leave it unset
         * @return this builder
         */
        public FlowExecutionSettingsBuilder rollbackPolicy(RollbackPolicy value) {
            this.rollbackPolicy = value;
            return this;
        }

        /**
         * Requests transaction validity-window preferences.
         *
         * @param value validity policy, or {@code null} to leave it unset
         * @return this builder
         */
        public FlowExecutionSettingsBuilder validityPolicy(ValidityPolicy value) {
            this.validityPolicy = value;
            return this;
        }

        /**
         * Creates a settings snapshot containing the requested values.
         *
         * @return flow execution settings
         */
        public FlowExecutionSettings build() {
            return new FlowExecutionSettings(chainingMode, confirmationConfig,
                    rollbackStrategy, retryPolicy, rollbackPolicy, validityPolicy);
        }
    }
}
