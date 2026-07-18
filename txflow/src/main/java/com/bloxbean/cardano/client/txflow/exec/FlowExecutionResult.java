package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * Terminal view returned when an execution stops running.
 *
 * <p>A result can require recovery without being successful or conclusively
 * failed. Durable executions also expose their persisted attempt snapshots so
 * operators can reconcile the exact signed transaction rather than rebuild it.</p>
 *
 * @param executionId execution correlation identifier
 * @param definitionFingerprint fingerprint of the compiled portable definition
 * @param state terminal or recovery-required execution state
 * @param steps immutable step results observed by this run
 * @param attempts immutable durable attempt snapshots, empty without a store
 * @param error structured failure, or {@code null} after successful completion
 * @param startedAt execution start time
 * @param completedAt time at which this result became terminal
 */
public record FlowExecutionResult(String executionId, String definitionFingerprint,
                                  FlowExecutionState state, List<FlowStepResult> steps,
                                  List<FlowAttemptSnapshot> attempts,
                                  FlowError error, Instant startedAt, Instant completedAt) {
    /**
     * Snapshots the step and attempt lists, treating {@code null} as empty.
     *
     * @param executionId execution correlation identifier
     * @param definitionFingerprint fingerprint of the compiled definition
     * @param state terminal or recovery-required execution state
     * @param steps step results observed by this run
     * @param attempts durable attempt snapshots
     * @param error structured failure, or {@code null}
     * @param startedAt execution start time
     * @param completedAt terminal-result time
     */
    public FlowExecutionResult {
        steps = List.copyOf(steps != null ? steps : List.of());
        attempts = List.copyOf(attempts != null ? attempts : List.of());
    }

    /**
     * Creates a result without durable attempt snapshots.
     *
     * @param executionId execution correlation identifier
     * @param definitionFingerprint fingerprint of the compiled definition
     * @param state terminal or recovery-required execution state
     * @param steps step results observed by this run
     * @param error structured failure, or {@code null}
     * @param startedAt execution start time
     * @param completedAt terminal-result time
     */
    public FlowExecutionResult(String executionId, String definitionFingerprint,
                               FlowExecutionState state, List<FlowStepResult> steps,
                               FlowError error, Instant startedAt, Instant completedAt) {
        this(executionId, definitionFingerprint, state, steps, List.of(), error,
                startedAt, completedAt);
    }

    /**
     * Returns whether every required step completed successfully.
     *
     * @return {@code true} only for {@link FlowExecutionState#COMPLETED}
     */
    public boolean isSuccessful() {
        return state == FlowExecutionState.COMPLETED;
    }
}
