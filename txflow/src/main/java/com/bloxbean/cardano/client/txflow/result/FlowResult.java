package com.bloxbean.cardano.client.txflow.result;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Aggregated result of executing a complete transaction flow.
 * <p>
 * Contains the overall outcome, individual step results, and timing information.
 */
@Getter
public class FlowResult {
    private final String flowId;
    private final FlowStatus status;
    private final List<FlowStepResult> stepResults;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Throwable error;

    private FlowResult(Builder builder) {
        this.flowId = builder.flowId;
        this.status = builder.status;
        this.stepResults = List.copyOf(builder.stepResults);
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.error = builder.error;
    }

    /**
     * Check if the flow completed successfully.
     *
     * @return true if all steps completed successfully
     */
    public boolean isSuccessful() {
        return status == FlowStatus.COMPLETED;
    }

    /**
     * Check if the flow failed.
     *
     * @return true if the flow failed
     */
    public boolean isFailed() {
        return status == FlowStatus.FAILED;
    }

    /**
     * Get the total execution duration.
     *
     * @return the duration from start to completion
     */
    public Duration getDuration() {
        if (startedAt == null || completedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(startedAt, completedAt);
    }

    /**
     * Get the number of completed steps.
     *
     * @return count of successful steps
     */
    public int getCompletedStepCount() {
        return (int) stepResults.stream()
                .filter(FlowStepResult::isSuccessful)
                .count();
    }

    /**
     * Get the total number of steps.
     *
     * @return total step count
     */
    public int getTotalStepCount() {
        return stepResults.size();
    }

    /**
     * Get the result of a specific step.
     *
     * @param stepId the step ID
     * @return the step result, or empty if not found
     */
    public Optional<FlowStepResult> getStepResult(String stepId) {
        return stepResults.stream()
                .filter(r -> stepId.equals(r.getStepId()))
                .findFirst();
    }

    /**
     * Get all transaction hashes from successful steps.
     *
     * @return list of transaction hashes
     */
    public List<String> getTransactionHashes() {
        List<String> hashes = new ArrayList<>();
        for (FlowStepResult result : stepResults) {
            if (result.isSuccessful() && result.getTransactionHash() != null) {
                hashes.add(result.getTransactionHash());
            }
        }
        return hashes;
    }

    /**
     * Get the failed step result if the flow failed.
     *
     * @return the failed step result, or empty if flow didn't fail
     */
    public Optional<FlowStepResult> getFailedStep() {
        return stepResults.stream()
                .filter(r -> r.getStatus() == FlowStatus.FAILED)
                .findFirst();
    }

    /**
     * Create a builder for FlowResult.
     *
     * @param flowId the flow ID
     * @return a new builder
     */
    public static Builder builder(String flowId) {
        return new Builder(flowId);
    }

    @Override
    public String toString() {
        return "FlowResult{" +
                "flowId='" + flowId + '\'' +
                ", status=" + status +
                ", completedSteps=" + getCompletedStepCount() + "/" + getTotalStepCount() +
                ", duration=" + getDuration() +
                (error != null ? ", error=" + error.getMessage() : "") +
                '}';
    }

    /**
     * Builder for FlowResult.
     */
    public static class Builder {
        private final String flowId;
        private FlowStatus status = FlowStatus.PENDING;
        private final List<FlowStepResult> stepResults = new ArrayList<>();
        private Instant startedAt;
        private Instant completedAt;
        private Throwable error;

        private Builder(String flowId) {
            this.flowId = flowId;
        }

        public Builder withStatus(FlowStatus status) {
            this.status = status;
            return this;
        }

        public Builder addStepResult(FlowStepResult result) {
            this.stepResults.add(result);
            return this;
        }

        public Builder withStepResults(List<FlowStepResult> results) {
            this.stepResults.clear();
            this.stepResults.addAll(results);
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder withError(Throwable error) {
            this.error = error;
            return this;
        }

        public FlowResult build() {
            return new FlowResult(this);
        }

        /**
         * Build a successful flow result.
         *
         * @return the flow result with COMPLETED status
         */
        public FlowResult success() {
            this.status = FlowStatus.COMPLETED;
            if (completedAt == null) {
                completedAt = Instant.now();
            }
            return build();
        }

        /**
         * Build a failed flow result.
         *
         * @param error the error that caused the failure
         * @return the flow result with FAILED status
         */
        public FlowResult failure(Throwable error) {
            this.status = FlowStatus.FAILED;
            this.error = error;
            if (completedAt == null) {
                completedAt = Instant.now();
            }
            return build();
        }
    }
}
