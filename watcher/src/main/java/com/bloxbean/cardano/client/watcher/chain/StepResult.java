package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Result of executing a single step in a transaction chain.
 * 
 * Contains the outcome of step execution including transaction hash,
 * output UTXOs, spent inputs, and any error information.
 */
public class StepResult {
    private final String stepId;
    private final boolean successful;
    private final WatchStatus status;
    private final String transactionHash;
    private final List<Utxo> outputUtxos;
    private final List<TransactionInput> spentInputs;
    private final Throwable error;
    private final Instant completedAt;
    
    /**
     * Create a successful step result.
     * 
     * @param stepId the step ID
     * @param status the final status
     * @param transactionHash the transaction hash
     * @param outputUtxos the output UTXOs
     * @param spentInputs the spent transaction inputs
     */
    public StepResult(String stepId, WatchStatus status, String transactionHash, List<Utxo> outputUtxos, List<TransactionInput> spentInputs) {
        this.stepId = stepId;
        this.successful = status == WatchStatus.CONFIRMED;
        this.status = status;
        this.transactionHash = transactionHash;
        this.outputUtxos = outputUtxos != null ? List.copyOf(outputUtxos) : Collections.emptyList();
        this.spentInputs = spentInputs != null ? List.copyOf(spentInputs) : Collections.emptyList();
        this.error = null;
        this.completedAt = Instant.now();
    }
    
    /**
     * Create a successful step result (backward compatibility).
     * 
     * @param stepId the step ID
     * @param status the final status
     * @param transactionHash the transaction hash
     * @param outputUtxos the output UTXOs
     */
    public StepResult(String stepId, WatchStatus status, String transactionHash, List<Utxo> outputUtxos) {
        this(stepId, status, transactionHash, outputUtxos, Collections.emptyList());
    }
    
    /**
     * Create a failed step result.
     * 
     * @param stepId the step ID
     * @param error the error that caused the failure
     */
    public StepResult(String stepId, Throwable error) {
        this.stepId = stepId;
        this.successful = false;
        this.status = WatchStatus.FAILED;
        this.transactionHash = null;
        this.outputUtxos = Collections.emptyList();
        this.spentInputs = Collections.emptyList();
        this.error = error;
        this.completedAt = Instant.now();
    }
    
    /**
     * Create a successful step result.
     * 
     * @param stepId the step ID
     * @param transactionHash the transaction hash
     * @param outputUtxos the output UTXOs
     * @param spentInputs the spent transaction inputs
     * @return successful step result
     */
    public static StepResult success(String stepId, String transactionHash, List<Utxo> outputUtxos, List<TransactionInput> spentInputs) {
        return new StepResult(stepId, WatchStatus.CONFIRMED, transactionHash, outputUtxos, spentInputs);
    }
    
    /**
     * Create a successful step result (backward compatibility).
     * 
     * @param stepId the step ID
     * @param transactionHash the transaction hash
     * @param outputUtxos the output UTXOs
     * @return successful step result
     */
    public static StepResult success(String stepId, String transactionHash, List<Utxo> outputUtxos) {
        return success(stepId, transactionHash, outputUtxos, Collections.emptyList());
    }
    
    /**
     * Create a failed step result.
     * 
     * @param stepId the step ID
     * @param error the error
     * @return failed step result
     */
    public static StepResult failure(String stepId, Throwable error) {
        return new StepResult(stepId, error);
    }
    
    /**
     * Get the step ID.
     * 
     * @return the step ID
     */
    public String getStepId() {
        return stepId;
    }
    
    /**
     * Check if the step was successful.
     * 
     * @return true if successful
     */
    public boolean isSuccessful() {
        return successful;
    }
    
    /**
     * Get the final status.
     * 
     * @return the status
     */
    public WatchStatus getStatus() {
        return status;
    }
    
    /**
     * Get the transaction hash.
     * 
     * @return the transaction hash, or null if failed
     */
    public String getTransactionHash() {
        return transactionHash;
    }
    
    /**
     * Get the output UTXOs.
     * 
     * @return the output UTXOs
     */
    public List<Utxo> getOutputUtxos() {
        return outputUtxos;
    }
    
    /**
     * Get the spent transaction inputs.
     * 
     * @return the spent inputs
     */
    public List<TransactionInput> getSpentInputs() {
        return spentInputs;
    }
    
    /**
     * Get the error if the step failed.
     * 
     * @return the error, or null if successful
     */
    public Throwable getError() {
        return error;
    }
    
    /**
     * Get when the step completed.
     * 
     * @return the completion timestamp
     */
    public Instant getCompletedAt() {
        return completedAt;
    }
    
    @Override
    public String toString() {
        return "StepResult{" +
                "stepId='" + stepId + '\'' +
                ", successful=" + successful +
                ", status=" + status +
                ", transactionHash='" + transactionHash + '\'' +
                ", outputUtxos=" + outputUtxos.size() +
                ", spentInputs=" + spentInputs.size() +
                ", error=" + error +
                ", completedAt=" + completedAt +
                '}';
    }
}