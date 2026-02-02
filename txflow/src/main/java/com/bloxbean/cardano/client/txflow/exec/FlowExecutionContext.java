package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context object that manages state and data flow between flow steps.
 * <p>
 * The FlowExecutionContext provides a way for steps in a transaction flow to:
 * <ul>
 *     <li>Access outputs from previous steps</li>
 *     <li>Share data between steps</li>
 *     <li>Track the overall execution progress</li>
 * </ul>
 * <p>
 * This class is thread-safe and can be used for concurrent step execution.
 */
public class FlowExecutionContext {
    private final String flowId;
    private final Map<String, Object> variables;
    private final Map<String, FlowStepResult> stepResults = new ConcurrentHashMap<>();
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();

    /**
     * Create a new FlowExecutionContext for the given flow.
     *
     * @param flowId the flow identifier
     */
    public FlowExecutionContext(String flowId) {
        this(flowId, Collections.emptyMap());
    }

    /**
     * Create a new FlowExecutionContext with initial variables.
     *
     * @param flowId the flow identifier
     * @param variables initial variables for the flow
     */
    public FlowExecutionContext(String flowId, Map<String, Object> variables) {
        this.flowId = flowId;
        this.variables = new ConcurrentHashMap<>(variables != null ? variables : Collections.emptyMap());
    }

    /**
     * Get the flow ID.
     *
     * @return the flow ID
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * Get a copy of the variables map.
     *
     * @return copy of variables
     */
    public Map<String, Object> getVariables() {
        return new HashMap<>(variables);
    }

    /**
     * Get a specific variable value.
     *
     * @param key the variable key
     * @return the variable value, or null if not found
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * Set a variable value.
     *
     * @param key the variable key
     * @param value the variable value
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * Record the result of a step execution.
     *
     * @param stepId the step ID
     * @param result the step result
     */
    public void recordStepResult(String stepId, FlowStepResult result) {
        stepResults.put(stepId, result);
    }

    /**
     * Get the result of a specific step.
     *
     * @param stepId the step ID
     * @return the step result, or empty if not found
     */
    public Optional<FlowStepResult> getStepResult(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId));
    }

    /**
     * Get the output UTXOs from a specific step.
     *
     * @param stepId the step ID
     * @return the list of output UTXOs, or empty list if step not found or no outputs
     */
    public List<Utxo> getStepOutputs(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId))
                .map(FlowStepResult::getOutputUtxos)
                .orElse(Collections.emptyList());
    }

    /**
     * Get the spent inputs from a specific step.
     *
     * @param stepId the step ID
     * @return the list of spent transaction inputs, or empty list if step not found or no inputs
     */
    public List<TransactionInput> getStepSpentInputs(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId))
                .map(FlowStepResult::getSpentInputs)
                .orElse(Collections.emptyList());
    }

    /**
     * Get all spent inputs from all completed steps in the flow.
     * This is useful for filtering out UTXOs that have been spent by previous steps.
     *
     * @return list of all spent transaction inputs from all completed steps
     */
    public List<TransactionInput> getAllSpentInputs() {
        List<TransactionInput> allSpentInputs = new ArrayList<>();

        for (FlowStepResult stepResult : stepResults.values()) {
            if (stepResult.isSuccessful()) {
                allSpentInputs.addAll(stepResult.getSpentInputs());
            }
        }

        return allSpentInputs;
    }

    /**
     * Check if a step completed successfully.
     *
     * @param stepId the step ID
     * @return true if the step completed successfully
     */
    public boolean hasSuccessfulStep(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId))
                .map(FlowStepResult::isSuccessful)
                .orElse(false);
    }

    /**
     * Store shared data that can be accessed by any step.
     *
     * @param key the data key
     * @param value the data value
     */
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }

    /**
     * Get shared data by key and type.
     *
     * @param key the data key
     * @param type the expected type
     * @param <T> the type parameter
     * @return the data value if found and of correct type, empty otherwise
     */
    public <T> Optional<T> getSharedData(String key, Class<T> type) {
        Object value = sharedData.get(key);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /**
     * Get all step results.
     *
     * @return map of step ID to step result
     */
    public Map<String, FlowStepResult> getAllStepResults() {
        return Map.copyOf(stepResults);
    }

    /**
     * Get the transaction hash for a specific step.
     *
     * @param stepId the step ID
     * @return the transaction hash, or empty if step not found or not successful
     */
    public Optional<String> getStepTransactionHash(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId))
                .filter(FlowStepResult::isSuccessful)
                .map(FlowStepResult::getTransactionHash);
    }
}
