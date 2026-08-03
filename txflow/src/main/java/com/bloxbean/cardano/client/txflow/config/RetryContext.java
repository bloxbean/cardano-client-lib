package com.bloxbean.cardano.client.txflow.config;

import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;

/**
 * Facts available when making a phase-aware retry decision.
 *
 * @param phase processing stage that failed
 * @param category portable classification of the failure
 * @param attempt one-based attempt number
 * @param transactionHash locally known transaction hash, or {@code null} when
 *                        no transaction identity has been established
 * @param submissionOutcome best-known outcome of the submission call
 */
public record RetryContext(FlowErrorPhase phase, FlowErrorCategory category,
                           int attempt, String transactionHash,
                           SubmissionOutcome submissionOutcome) {
    /**
     * Creates validated facts for one retry decision.
     *
     * @param phase processing stage that failed
     * @param category portable classification of the failure
     * @param attempt positive one-based attempt number
     * @param transactionHash known transaction hash, or {@code null}
     * @param submissionOutcome best-known result of submission
     * @throws IllegalArgumentException if a required value is absent or the
     *                                  attempt number is not positive
     */
    public RetryContext {
        if (phase == null || category == null || submissionOutcome == null) {
            throw new IllegalArgumentException("phase, category, and submissionOutcome are required");
        }
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }

    /**
     * Creates a builder with attempt {@code 1} and submission outcome
     * {@link SubmissionOutcome#NOT_ATTEMPTED}.
     *
     * @return retry-context builder
     */
    public static Builder builder() { return new Builder(); }

    /** Builder for retry facts assembled by an execution path. */
    public static final class Builder {
        private FlowErrorPhase phase;
        private FlowErrorCategory category;
        private int attempt = 1;
        private String transactionHash;
        private SubmissionOutcome submissionOutcome = SubmissionOutcome.NOT_ATTEMPTED;

        /** Creates a builder for the first attempt before submission. */
        public Builder() {
        }

        /**
         * Sets the processing stage that failed.
         *
         * @param value error phase
         * @return this builder
         */
        public Builder phase(FlowErrorPhase value) { phase = value; return this; }

        /**
         * Sets the portable error classification.
         *
         * @param value error category
         * @return this builder
         */
        public Builder category(FlowErrorCategory value) { category = value; return this; }

        /**
         * Sets the positive one-based attempt number.
         *
         * @param value attempt number
         * @return this builder
         */
        public Builder attempt(int value) { attempt = value; return this; }

        /**
         * Sets the locally known transaction identity.
         *
         * @param value transaction hash, or {@code null} when not yet established
         * @return this builder
         */
        public Builder transactionHash(String value) { transactionHash = value; return this; }

        /**
         * Sets the best-known submission outcome.
         *
         * @param value submission outcome
         * @return this builder
         */
        public Builder submissionOutcome(SubmissionOutcome value) { submissionOutcome = value; return this; }

        /**
         * Validates and creates the retry context.
         *
         * @return retry context
         */
        public RetryContext build() { return new RetryContext(phase, category, attempt, transactionHash, submissionOutcome); }
    }
}
