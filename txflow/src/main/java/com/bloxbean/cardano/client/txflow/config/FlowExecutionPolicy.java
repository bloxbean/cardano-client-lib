package com.bloxbean.cardano.client.txflow.config;

import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.codec.DiagnosticSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Server-owned constraints applied before an execution plan can be produced.
 *
 * <p>A flow's {@link FlowExecutionSettings} are requests, not authority. This
 * policy converts permitted numeric requests to effective values, adding a
 * warning when a limit is capped. With {@link Builder#strictSettings(boolean)},
 * the same difference is an error. Disallowed semantic choices such as a
 * chaining mode, rollback action, or monitoring horizon are always errors and
 * are never silently substituted.</p>
 *
 * <p>Evaluation does not throw for policy violations. It returns diagnostics
 * alongside the effective settings so compilation can report every problem in
 * one pass.</p>
 */
public final class FlowExecutionPolicy {
    private final int maxSteps;
    private final Set<String> allowedNetworks;
    private final Set<ChainingMode> allowedModes;
    private final boolean requireValidityInterval;
    private final long maxValidityWindowSlots;
    private final SpendingContentionPolicy spendingContention;
    private final Duration maxQueueWait;
    private final boolean allowConcurrentSpendingOptOut;
    private final int maxRetryAttempts;
    private final Duration maxConfirmationTimeout;
    private final boolean strictSettings;
    private final Set<String> allowedResourcePrefixes;
    private final int maxRollbackRecoveryCycles;
    private final Set<RollbackAction> allowedRollbackActions;
    private final Set<RollbackMonitoringHorizon> allowedRollbackHorizons;
    private final Duration maxRequestedValidityWindow;

    private FlowExecutionPolicy(Builder builder) {
        this.maxSteps = builder.maxSteps;
        this.allowedNetworks = Set.copyOf(builder.allowedNetworks);
        this.allowedModes = Set.copyOf(builder.allowedModes);
        this.requireValidityInterval = builder.requireValidityInterval;
        this.maxValidityWindowSlots = builder.maxValidityWindowSlots;
        this.spendingContention = builder.spendingContention;
        this.maxQueueWait = builder.maxQueueWait;
        this.allowConcurrentSpendingOptOut = builder.allowConcurrentSpendingOptOut;
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.maxConfirmationTimeout = builder.maxConfirmationTimeout;
        this.strictSettings = builder.strictSettings;
        this.allowedResourcePrefixes = Set.copyOf(builder.allowedResourcePrefixes);
        this.maxRollbackRecoveryCycles = builder.maxRollbackRecoveryCycles;
        this.allowedRollbackActions = Set.copyOf(builder.allowedRollbackActions);
        this.allowedRollbackHorizons = Set.copyOf(builder.allowedRollbackHorizons);
        this.maxRequestedValidityWindow = builder.maxRequestedValidityWindow;
    }

    /**
     * Starts a policy with broad defaults and no network or resource-prefix allowlist.
     *
     * @return execution-policy builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the default broadly permissive policy.
     *
     * <p>The policy still retains defensive numeric ceilings such as 1,000
     * steps, 100 retry attempts, and a seven-day confirmation timeout.</p>
     *
     * @return default policy
     */
    public static FlowExecutionPolicy permissive() {
        return builder().build();
    }

    /**
     * Evaluates flow-wide settings and returns the values compilation should use.
     *
     * @param definition flow definition containing requested execution settings
     * @return effective settings and immutable policy diagnostics
     */
    public Evaluation evaluate(TxFlow definition) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        if (definition.getSteps().size() > maxSteps) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_POLICY_MAX_STEPS",
                    "Flow exceeds server step limit", "$.spec.steps"));
        }
        if (definition.getNetwork() != null && !allowedNetworks.isEmpty()
                && !allowedNetworks.contains(definition.getNetwork())) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_POLICY_NETWORK",
                    "Network is not allowed by server policy", "$.spec.network"));
        }
        FlowExecutionSettings requestedSettings = definition.getExecutionSettings();
        FlowExecutionSettings.FlowExecutionSettingsBuilder effective = requestedSettings.toBuilder();
        ChainingMode requested = requestedSettings.getChainingMode();
        if (requested != null && !allowedModes.contains(requested)) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_POLICY_MODE",
                    "Execution mode is not allowed by server policy", "$.spec.execution.mode"));
        }
        RetryPolicy retry = requestedSettings.getRetryPolicy();
        if (retry != null && retry.getMaxAttempts() > maxRetryAttempts) {
            diagnostics.add(settingCap("TXFLOW_POLICY_RETRY_CAPPED",
                    "Retry attempts exceed the server limit", "$.spec.execution.retry.max_attempts"));
            if (!strictSettings) {
                effective.retryPolicy(RetryPolicy.builder()
                        .maxAttempts(maxRetryAttempts)
                        .backoffStrategy(retry.getBackoffStrategy())
                        .initialDelay(retry.getInitialDelay())
                        .maxDelay(retry.getMaxDelay())
                        .retryOnTimeout(retry.isRetryOnTimeout())
                        .retryOnNetworkError(retry.isRetryOnNetworkError())
                        .jitterFactor(retry.getJitterFactor()).build());
            }
        }
        ConfirmationConfig confirmation = requestedSettings.getConfirmationConfig();
        if (confirmation != null && confirmation.getTimeout().compareTo(maxConfirmationTimeout) > 0) {
            diagnostics.add(settingCap("TXFLOW_POLICY_CONFIRMATION_TIMEOUT_CAPPED",
                    "Confirmation timeout exceeds the server limit",
                    "$.spec.execution.confirmation.timeout"));
            if (!strictSettings) {
                effective.confirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(confirmation.getMinConfirmations())
                        .checkInterval(confirmation.getCheckInterval())
                        .timeout(maxConfirmationTimeout)
                        .maxRollbackRetries(confirmation.getMaxRollbackRetries())
                        .waitForBackendAfterRollback(confirmation.isWaitForBackendAfterRollback())
                        .postRollbackWaitAttempts(confirmation.getPostRollbackWaitAttempts())
                        .postRollbackUtxoSyncDelay(confirmation.getPostRollbackUtxoSyncDelay())
                        .requiredAuthoritativeAbsences(
                                confirmation.getRequiredAuthoritativeAbsences())
                        .build());
            }
        }
        RollbackPolicy rollback = requestedSettings.getRollbackPolicy();
        if (rollback != null) {
            if (!allowedRollbackActions.contains(rollback.action())) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_POLICY_ROLLBACK_ACTION",
                        "Rollback action is not allowed by server policy",
                        "$.spec.execution.rollback.action"));
            }
            if (!allowedRollbackHorizons.contains(rollback.monitoringHorizon())) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_POLICY_ROLLBACK_HORIZON",
                        "Rollback monitoring horizon is not allowed by server policy",
                        "$.spec.execution.rollback.monitoring_horizon"));
            }
            if (rollback.maxRecoveryCycles() > maxRollbackRecoveryCycles) {
                diagnostics.add(settingCap("TXFLOW_POLICY_ROLLBACK_CYCLES_CAPPED",
                        "Rollback recovery cycles exceed the server limit",
                        "$.spec.execution.rollback.max_recovery_cycles"));
                if (!strictSettings) {
                    effective.rollbackPolicy(new RollbackPolicy(rollback.action(),
                            rollback.monitoringHorizon(), rollback.rebuildScope(),
                            maxRollbackRecoveryCycles, rollback.reinclusionWindow(),
                            rollback.minimumConsistentAbsenceObservations()));
                }
            }
        }
        ValidityPolicy validity = requestedSettings.getValidityPolicy();
        if (validity != null && maxRequestedValidityWindow != null
                && validity.window().compareTo(maxRequestedValidityWindow) > 0) {
            diagnostics.add(settingCap("TXFLOW_POLICY_VALIDITY_PREFERENCE_CAPPED",
                    "Requested validity window exceeds the server limit",
                    "$.spec.execution.validity.window"));
            if (!strictSettings) {
                effective.validityPolicy(new ValidityPolicy(maxRequestedValidityWindow,
                        validity.resubmitSafetyMargin()));
            }
        }
        return new Evaluation(effective.build(), diagnostics);
    }

    private FlowDiagnostic settingCap(String code, String message, String path) {
        return strictSettings ? FlowDiagnostic.error(code, message, path)
                : new FlowDiagnostic(code, DiagnosticSeverity.WARNING, message, path,
                null, null, null);
    }

    /**
     * Evaluates transaction-local validity requirements against a portable
     * transaction node.
     *
     * @param transaction transaction object containing an optional {@code context}
     * @param path diagnostic path identifying the transaction in the source document
     * @return policy diagnostics for the transaction
     */
    public List<FlowDiagnostic> evaluateTransaction(JsonNode transaction, String path) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        JsonNode context = transaction.path("context");
        JsonNode from = context.get("valid_from_slot");
        JsonNode to = context.get("valid_to_slot");
        if (requireValidityInterval && (from == null || to == null || !from.isIntegralNumber() || !to.isIntegralNumber())) {
            diagnostics.add(FlowDiagnostic.error("TXFLOW_POLICY_VALIDITY_REQUIRED",
                    "Server policy requires valid_from_slot and valid_to_slot", path + ".context"));
        }
        if (from != null && to != null && from.isIntegralNumber() && to.isIntegralNumber()) {
            long window = to.longValue() - from.longValue();
            if (window < 0) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_VALIDITY_INTERVAL_INVALID",
                        "valid_to_slot must not precede valid_from_slot", path + ".context"));
            } else if (maxValidityWindowSlots > 0 && window > maxValidityWindowSlots) {
                diagnostics.add(FlowDiagnostic.error("TXFLOW_POLICY_VALIDITY_WINDOW",
                        "Transaction validity interval exceeds server limit", path + ".context"));
            }
        }
        return diagnostics;
    }

    /**
     * Returns the policy for overlapping canonical spending identities.
     *
     * @return spending contention policy
     */
    public SpendingContentionPolicy getSpendingContention() {
        return spendingContention;
    }

    /**
     * Returns the maximum wait used by serialized spending-resource acquisition.
     *
     * @return maximum queue wait; zero means do not wait
     */
    public Duration getMaxQueueWait() {
        return maxQueueWait;
    }

    /**
     * Reports whether a request may opt out of spending-resource coordination.
     *
     * @return whether per-request concurrent-spending opt-out is allowed
     */
    public boolean isConcurrentSpendingOptOutAllowed() {
        return allowConcurrentSpendingOptOut;
    }

    /**
     * Tests a logical resource URI against the configured literal prefix allowlist.
     * An empty allowlist permits every reference.
     *
     * @param reference normalized logical resource reference
     * @return whether policy permits the reference
     */
    public boolean isResourceAllowed(String reference) {
        return allowedResourcePrefixes.isEmpty()
                || allowedResourcePrefixes.stream().anyMatch(reference::startsWith);
    }

    /**
     * Outcome of evaluating flow-wide execution requests.
     *
     * @param effectiveSettings settings after any permitted numeric caps
     * @param diagnostics immutable warnings and errors produced by policy
     */
    public record Evaluation(FlowExecutionSettings effectiveSettings,
                             List<FlowDiagnostic> diagnostics) {
        /**
         * Creates an evaluation and defensively snapshots its diagnostics.
         *
         * @param effectiveSettings settings after any permitted numeric caps
         * @param diagnostics warnings and errors produced by policy
         */
        public Evaluation {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /** Builder for a server-owned execution safety envelope. */
    public static final class Builder {
        private int maxSteps = 1_000;
        private Set<String> allowedNetworks = Set.of();
        private Set<ChainingMode> allowedModes = Set.of(ChainingMode.SEQUENTIAL,
                ChainingMode.PIPELINED, ChainingMode.BATCH);
        private boolean requireValidityInterval;
        private long maxValidityWindowSlots;
        private SpendingContentionPolicy spendingContention = SpendingContentionPolicy.SERIALIZE;
        private Duration maxQueueWait = Duration.ofMinutes(2);
        private boolean allowConcurrentSpendingOptOut;
        private int maxRetryAttempts = 100;
        private Duration maxConfirmationTimeout = Duration.ofDays(7);
        private boolean strictSettings;
        private Set<String> allowedResourcePrefixes = Set.of();
        private int maxRollbackRecoveryCycles = 100;
        private Set<RollbackAction> allowedRollbackActions = Set.of(RollbackAction.values());
        private Set<RollbackMonitoringHorizon> allowedRollbackHorizons =
                Set.of(RollbackMonitoringHorizon.values());
        private Duration maxRequestedValidityWindow;

        /** Creates a builder initialized with the broad default policy limits. */
        public Builder() {
        }

        /**
         * Sets the maximum number of steps in one flow.
         *
         * @param maxSteps positive step limit
         * @return this builder
         */
        public Builder maxSteps(int maxSteps) {
            if (maxSteps < 1) throw new IllegalArgumentException("maxSteps must be positive");
            this.maxSteps = maxSteps;
            return this;
        }

        /**
         * Restricts portable network names; an empty set permits all networks.
         *
         * @param allowedNetworks permitted network names
         * @return this builder
         */
        public Builder allowedNetworks(Set<String> allowedNetworks) {
            this.allowedNetworks = Set.copyOf(allowedNetworks);
            return this;
        }

        /**
         * Sets the execution modes a flow may request.
         *
         * @param allowedModes permitted chaining modes
         * @return this builder
         */
        public Builder allowedModes(Set<ChainingMode> allowedModes) {
            this.allowedModes = Set.copyOf(allowedModes);
            return this;
        }

        /**
         * Requires each portable transaction to declare both validity bounds.
         *
         * @param value whether both absolute slot bounds are required
         * @return this builder
         */
        public Builder requireValidityInterval(boolean value) {
            this.requireValidityInterval = value;
            return this;
        }

        /**
         * Sets the largest permitted difference between absolute validity slots.
         * Zero disables this limit.
         *
         * @param value maximum slot window, or zero for no limit
         * @return this builder
         */
        public Builder maxValidityWindowSlots(long value) {
            if (value < 0) throw new IllegalArgumentException("validity window limit cannot be negative");
            this.maxValidityWindowSlots = value;
            return this;
        }

        /**
         * Selects handling for executions with overlapping spending identities.
         *
         * @param value contention policy
         * @return this builder
         */
        public Builder spendingContention(SpendingContentionPolicy value) {
            this.spendingContention = java.util.Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Sets the bounded wait used when contention is serialized.
         *
         * @param value non-negative queue wait
         * @return this builder
         */
        public Builder maxQueueWait(Duration value) {
            if (value == null || value.isNegative()) {
                throw new IllegalArgumentException("maxQueueWait cannot be null or negative");
            }
            this.maxQueueWait = value;
            return this;
        }

        /**
         * Controls whether individual execution requests may bypass spending locks.
         *
         * @param value whether opt-out is allowed
         * @return this builder
         */
        public Builder allowConcurrentSpendingOptOut(boolean value) {
            this.allowConcurrentSpendingOptOut = value;
            return this;
        }

        /**
         * Sets the server ceiling for requested retry attempts.
         *
         * @param value attempt ceiling from 1 through 100
         * @return this builder
         */
        public Builder maxRetryAttempts(int value) {
            if (value < 1 || value > 100) {
                throw new IllegalArgumentException("maxRetryAttempts must be between 1 and 100");
            }
            this.maxRetryAttempts = value;
            return this;
        }

        /**
         * Sets the server ceiling for a requested confirmation timeout.
         *
         * @param value positive timeout ceiling
         * @return this builder
         */
        public Builder maxConfirmationTimeout(Duration value) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("maxConfirmationTimeout must be positive");
            }
            this.maxConfirmationTimeout = value;
            return this;
        }

        /**
         * Makes any requested-to-effective numeric difference an error instead
         * of a warning with a capped value.
         *
         * @param value whether numeric caps are strict
         * @return this builder
         */
        public Builder strictSettings(boolean value) {
            this.strictSettings = value;
            return this;
        }

        /**
         * Sets literal URI prefixes from which a flow may reference resources.
         * An empty set disables prefix filtering.
         *
         * @param value permitted reference prefixes
         * @return this builder
         */
        public Builder allowedResourcePrefixes(Set<String> value) {
            this.allowedResourcePrefixes = Set.copyOf(value);
            return this;
        }

        /**
         * Sets the ceiling for requested automated rollback recovery cycles.
         *
         * @param value recovery-cycle ceiling from 0 through 100
         * @return this builder
         */
        public Builder maxRollbackRecoveryCycles(int value) {
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException("maxRollbackRecoveryCycles must be between 0 and 100");
            }
            this.maxRollbackRecoveryCycles = value;
            return this;
        }

        /**
         * Sets rollback responses that portable flows may request.
         *
         * @param value allowed actions
         * @return this builder
         */
        public Builder allowedRollbackActions(Set<RollbackAction> value) {
            this.allowedRollbackActions = Set.copyOf(value);
            return this;
        }

        /**
         * Sets rollback monitoring horizons that portable flows may request.
         *
         * @param value allowed horizons
         * @return this builder
         */
        public Builder allowedRollbackHorizons(Set<RollbackMonitoringHorizon> value) {
            this.allowedRollbackHorizons = Set.copyOf(value);
            return this;
        }

        /**
         * Sets the ceiling for the portable duration-based validity preference.
         *
         * @param value positive validity-window ceiling
         * @return this builder
         */
        public Builder maxRequestedValidityWindow(Duration value) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("maxRequestedValidityWindow must be positive");
            }
            this.maxRequestedValidityWindow = value;
            return this;
        }

        /**
         * Creates an immutable policy snapshot.
         *
         * @return execution policy
         */
        public FlowExecutionPolicy build() {
            return new FlowExecutionPolicy(this);
        }
    }
}
