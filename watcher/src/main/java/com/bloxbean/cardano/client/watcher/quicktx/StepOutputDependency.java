package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.watcher.chain.ChainContext;

import java.util.List;

/**
 * Represents a dependency on outputs from a previous step in a transaction chain.
 * 
 * This class encapsulates the relationship between a step and the outputs it
 * depends on from previous steps, along with the strategy for selecting which
 * specific UTXOs to use.
 */
public class StepOutputDependency {
    private final String stepId;
    private final UtxoSelectionStrategy selectionStrategy;
    private final boolean optional;
    
    /**
     * Create a required dependency on a previous step's outputs.
     * 
     * @param stepId the ID of the step to depend on
     * @param selectionStrategy the strategy for selecting UTXOs from that step
     */
    public StepOutputDependency(String stepId, UtxoSelectionStrategy selectionStrategy) {
        this(stepId, selectionStrategy, false);
    }
    
    /**
     * Create a dependency on a previous step's outputs.
     * 
     * @param stepId the ID of the step to depend on
     * @param selectionStrategy the strategy for selecting UTXOs from that step
     * @param optional whether this dependency is optional (step can proceed without it)
     */
    public StepOutputDependency(String stepId, UtxoSelectionStrategy selectionStrategy, boolean optional) {
        this.stepId = stepId;
        this.selectionStrategy = selectionStrategy;
        this.optional = optional;
    }
    
    /**
     * Resolve this dependency using the chain context to get the actual UTXOs.
     * 
     * @param chainContext the chain context containing step results
     * @return the list of UTXOs selected by this dependency
     * @throws UtxoDependencyException if the dependency cannot be resolved and is not optional
     */
    public List<Utxo> resolveUtxos(ChainContext chainContext) {
        List<Utxo> stepOutputs = chainContext.getStepOutputs(stepId);
        if (stepOutputs.isEmpty() && !optional) {
            throw new UtxoDependencyException("Required step '" + stepId + "' has no outputs available");
        }
        return selectionStrategy.selectUtxos(stepOutputs, chainContext);
    }
    
    /**
     * Get the ID of the step this dependency references.
     * 
     * @return the step ID
     */
    public String getStepId() {
        return stepId;
    }
    
    /**
     * Get the selection strategy for this dependency.
     * 
     * @return the selection strategy
     */
    public UtxoSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }
    
    /**
     * Check if this dependency is optional.
     * 
     * @return true if optional, false if required
     */
    public boolean isOptional() {
        return optional;
    }
    
    @Override
    public String toString() {
        return "StepOutputDependency{" +
                "stepId='" + stepId + '\'' +
                ", selectionStrategy=" + selectionStrategy.getClass().getSimpleName() +
                ", optional=" + optional +
                '}';
    }
}