package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.api.model.Utxo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context object that manages state and data flow between chain steps.
 * 
 * The ChainContext provides a way for steps in a transaction chain to:
 * - Access outputs from previous steps
 * - Share data between steps
 * - Track the overall execution progress
 */
public class ChainContext {
    private final String chainId;
    private final Map<String, StepResult> stepResults = new ConcurrentHashMap<>();
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
    
    /**
     * Create a new ChainContext for the given chain.
     * 
     * @param chainId the chain identifier
     */
    public ChainContext(String chainId) {
        this.chainId = chainId;
    }
    
    /**
     * Get the chain ID.
     * 
     * @return the chain ID
     */
    public String getChainId() {
        return chainId;
    }
    
    /**
     * Record the result of a step execution.
     * 
     * @param stepId the step ID
     * @param result the step result
     */
    public void recordStepResult(String stepId, StepResult result) {
        stepResults.put(stepId, result);
    }
    
    /**
     * Get the result of a specific step.
     * 
     * @param stepId the step ID
     * @return the step result, or empty if not found
     */
    public Optional<StepResult> getStepResult(String stepId) {
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
            .map(StepResult::getOutputUtxos)
            .orElse(Collections.emptyList());
    }
    
    /**
     * Check if a step completed successfully.
     * 
     * @param stepId the step ID
     * @return true if the step completed successfully
     */
    public boolean hasSuccessfulStep(String stepId) {
        return Optional.ofNullable(stepResults.get(stepId))
            .map(StepResult::isSuccessful)
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
    public Map<String, StepResult> getAllStepResults() {
        return Map.copyOf(stepResults);
    }
}