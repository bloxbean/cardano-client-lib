package com.bloxbean.cardano.client.txflow.recovery;

import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadResolver;

import java.util.Objects;

/**
 * Request to reconcile either an explicit attempt or one loaded by execution identity.
 *
 * <p>An explicit {@link FlowAttemptSnapshot} can be passed directly for
 * attempt-level recovery. Alternatively, an execution ID plus optional step ID
 * and attempt number lets {@code FlowEngine} select a persisted attempt; when a
 * selector is omitted, the engine selects the highest matching attempt number.
 * {@link FlowRecoveryCoordinator} itself requires the attempt to have already
 * been resolved.</p>
 *
 * <p>The current slot and safety margin decide whether identical signed bytes
 * remain eligible for resubmission. An external signed-payload reference also
 * requires a resolver; inline payloads do not.</p>
 */
public final class FlowRecoveryRequest {
    private final String executionId;
    private final String stepId;
    private final Integer attemptNumber;
    private final FlowAttemptSnapshot attempt;
    private final long currentSlot;
    private final long resubmitSafetyMargin;
    private final SignedPayloadResolver payloadResolver;

    /**
     * Creates a request for an already-loaded attempt.
     *
     * @param attempt persisted attempt to reconcile
     * @param currentSlot current chain slot
     * @param resubmitSafetyMargin slots reserved before the recorded upper validity bound
     * @param payloadResolver resolver for external signed payloads, or {@code null}
     *                        when the payload is inline
     */
    public FlowRecoveryRequest(FlowAttemptSnapshot attempt, long currentSlot,
                               long resubmitSafetyMargin,
                               SignedPayloadResolver payloadResolver) {
        this(null, null, null, Objects.requireNonNull(attempt, "attempt"), currentSlot,
                resubmitSafetyMargin, payloadResolver);
    }

    private FlowRecoveryRequest(String executionId, String stepId, Integer attemptNumber,
                                FlowAttemptSnapshot attempt, long currentSlot,
                                long resubmitSafetyMargin,
                                SignedPayloadResolver payloadResolver) {
        if (currentSlot < 0 || resubmitSafetyMargin < 0) {
            throw new IllegalArgumentException("slot and safety margin cannot be negative");
        }
        if (attempt == null && (executionId == null || executionId.isBlank())) {
            throw new IllegalArgumentException("attempt or executionId is required");
        }
        this.executionId = executionId;
        this.stepId = stepId;
        this.attemptNumber = attemptNumber;
        this.attempt = attempt;
        this.currentSlot = currentSlot;
        this.resubmitSafetyMargin = resubmitSafetyMargin;
        this.payloadResolver = payloadResolver;
    }

    /**
     * Starts a request for either direct-attempt or execution-identity recovery.
     *
     * @return recovery-request builder
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Returns the persisted execution identity used for store lookup.
     *
     * @return execution identity, or {@code null} for a direct attempt
     */
    public String executionId() { return executionId; }

    /**
     * Returns the optional step selector used during persisted-attempt lookup.
     *
     * @return step selector, or {@code null} to consider every step
     */
    public String stepId() { return stepId; }

    /**
     * Returns the optional attempt selector used during persisted-attempt lookup.
     *
     * @return one-based attempt selector, or {@code null} to select the latest match
     */
    public Integer attemptNumber() { return attemptNumber; }

    /**
     * Returns the transaction attempt to reconcile.
     *
     * @return explicit or engine-resolved attempt, or {@code null} before lookup
     */
    public FlowAttemptSnapshot attempt() { return attempt; }

    /**
     * Returns the chain slot against which resubmission validity is checked.
     *
     * @return current chain slot supplied by the caller
     */
    public long currentSlot() { return currentSlot; }

    /**
     * Returns the buffer retained before the transaction's upper validity bound.
     *
     * @return non-negative safety margin in slots
     */
    public long resubmitSafetyMargin() { return resubmitSafetyMargin; }

    /**
     * Returns the application resolver for an externally stored signed payload.
     *
     * @return payload resolver, or {@code null} when not configured
     */
    public SignedPayloadResolver payloadResolver() { return payloadResolver; }

    /**
     * Returns an equivalent request with its persisted attempt resolved.
     *
     * <p>This method is primarily used by {@code FlowEngine}; identity and
     * selector fields are retained so durable recovery can update the owning
     * execution.</p>
     *
     * @param resolvedAttempt attempt selected from the execution store
     * @return request containing the selected attempt
     */
    public FlowRecoveryRequest withAttempt(FlowAttemptSnapshot resolvedAttempt) {
        return new FlowRecoveryRequest(executionId, stepId, attemptNumber,
                Objects.requireNonNull(resolvedAttempt, "resolvedAttempt"), currentSlot,
                resubmitSafetyMargin, payloadResolver);
    }

    /** Builder for direct-attempt or execution-identity recovery. */
    public static final class Builder {
        private String executionId;
        private String stepId;
        private Integer attemptNumber;
        private FlowAttemptSnapshot attempt;
        private long currentSlot;
        private long resubmitSafetyMargin;
        private SignedPayloadResolver payloadResolver;

        /** Creates a builder with slot and safety margin set to zero. */
        public Builder() {
        }

        /**
         * Selects a persisted execution from the engine's store.
         *
         * @param value non-blank execution identity
         * @return this builder
         */
        public Builder executionId(String value) { executionId = value; return this; }

        /**
         * Narrows persisted-attempt selection to one step.
         *
         * @param value step identity, or {@code null} for every step
         * @return this builder
         */
        public Builder stepId(String value) { stepId = value; return this; }

        /**
         * Narrows persisted-attempt selection to one attempt number.
         *
         * @param value attempt number, or {@code null} for the latest match
         * @return this builder
         */
        public Builder attemptNumber(Integer value) { attemptNumber = value; return this; }

        /**
         * Supplies an attempt directly instead of loading it by execution identity.
         *
         * @param value persisted attempt snapshot
         * @return this builder
         */
        public Builder attempt(FlowAttemptSnapshot value) { attempt = value; return this; }

        /**
         * Supplies the current chain slot used for validity checks.
         *
         * @param value non-negative current slot
         * @return this builder
         */
        public Builder currentSlot(long value) { currentSlot = value; return this; }

        /**
         * Reserves slots before the recorded upper validity bound.
         *
         * @param value non-negative safety margin in slots
         * @return this builder
         */
        public Builder resubmitSafetyMargin(long value) { resubmitSafetyMargin = value; return this; }

        /**
         * Supplies the application resolver used for external signed payloads.
         *
         * @param value resolver, or {@code null} when payloads are inline
         * @return this builder
         */
        public Builder payloadResolver(SignedPayloadResolver value) { payloadResolver = value; return this; }

        /**
         * Creates the validated request.
         *
         * @return recovery request
         */
        public FlowRecoveryRequest build() {
            return new FlowRecoveryRequest(executionId, stepId, attemptNumber, attempt,
                    currentSlot, resubmitSafetyMargin, payloadResolver);
        }
    }
}
