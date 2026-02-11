package com.bloxbean.cardano.client.txflow.exec.store;

import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Snapshot of a flow's execution state for persistence.
 * <p>
 * This class captures the essential state of a flow that needs to be
 * persisted for recovery after application restart.
 * <p>
 * Example usage for persistence:
 * <pre>{@code
 * // Save flow state when submitted
 * FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
 *     .flowId(flow.getId())
 *     .status(FlowStatus.IN_PROGRESS)
 *     .startedAt(Instant.now())
 *     .steps(stepSnapshots)
 *     .build();
 * stateStore.saveFlowState(snapshot);
 *
 * // Load pending flows on restart
 * List<FlowStateSnapshot> pending = stateStore.loadPendingFlows();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStateSnapshot {

    /**
     * The unique identifier of this flow.
     */
    private String flowId;

    /**
     * Current status of the flow.
     */
    private FlowStatus status;

    /**
     * When the flow execution started.
     */
    private Instant startedAt;

    /**
     * When the flow execution completed (success or failure).
     * Null if still in progress.
     */
    private Instant completedAt;

    /**
     * State snapshots for each step in the flow.
     */
    @Builder.Default
    private List<StepStateSnapshot> steps = new ArrayList<>();

    /**
     * Flow-level variables that may be needed for recovery.
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * Description of the flow for identification.
     */
    private String description;

    /**
     * Total number of steps in the flow.
     */
    private int totalSteps;

    /**
     * Number of steps that have been completed.
     */
    private int completedSteps;

    /**
     * Optional metadata for application-specific data.
     */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Check if this flow is still in progress.
     *
     * @return true if the flow has not completed
     */
    public boolean isInProgress() {
        return status == FlowStatus.IN_PROGRESS || status == FlowStatus.PENDING;
    }

    /**
     * Check if this flow has pending transactions that need tracking.
     *
     * @return true if any step needs confirmation tracking
     */
    public boolean hasPendingTransactions() {
        if (steps == null) return false;
        return steps.stream().anyMatch(StepStateSnapshot::needsTracking);
    }

    /**
     * Get the step snapshot for a specific step.
     *
     * @param stepId the step identifier
     * @return the step snapshot, or null if not found
     */
    public StepStateSnapshot getStep(String stepId) {
        if (steps == null) return null;
        return steps.stream()
                .filter(s -> stepId.equals(s.getStepId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all steps that need tracking.
     *
     * @return list of steps with pending transactions
     */
    public List<StepStateSnapshot> getPendingSteps() {
        if (steps == null) return new ArrayList<>();
        return steps.stream()
                .filter(StepStateSnapshot::needsTracking)
                .collect(Collectors.toList());
    }

    /**
     * Add a step snapshot.
     *
     * @param step the step snapshot to add
     */
    public void addStep(StepStateSnapshot step) {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        steps.add(step);
    }

    /**
     * Update a step snapshot.
     *
     * @param step the updated step snapshot
     */
    public void updateStep(StepStateSnapshot step) {
        if (steps == null) return;
        for (int i = 0; i < steps.size(); i++) {
            if (step.getStepId().equals(steps.get(i).getStepId())) {
                steps.set(i, step);
                return;
            }
        }
        // Step not found, add it
        steps.add(step);
    }
}
