package com.bloxbean.cardano.client.txflow.result;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Result of executing a single step in a transaction flow.
 * <p>
 * Contains the outcome of step execution including transaction hash,
 * output UTXOs, spent inputs, and any error information.
 */
@Getter
public class FlowStepResult {
    private final String stepId;
    private final boolean successful;
    private final FlowStatus status;
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
    public FlowStepResult(String stepId, FlowStatus status, String transactionHash,
                          List<Utxo> outputUtxos, List<TransactionInput> spentInputs) {
        this.stepId = stepId;
        this.successful = status == FlowStatus.COMPLETED;
        this.status = status;
        this.transactionHash = transactionHash;
        this.outputUtxos = outputUtxos != null ? List.copyOf(outputUtxos) : Collections.emptyList();
        this.spentInputs = spentInputs != null ? List.copyOf(spentInputs) : Collections.emptyList();
        this.error = null;
        this.completedAt = Instant.now();
    }

    /**
     * Create a successful step result (without spent inputs).
     *
     * @param stepId the step ID
     * @param status the final status
     * @param transactionHash the transaction hash
     * @param outputUtxos the output UTXOs
     */
    public FlowStepResult(String stepId, FlowStatus status, String transactionHash, List<Utxo> outputUtxos) {
        this(stepId, status, transactionHash, outputUtxos, Collections.emptyList());
    }

    /**
     * Create a failed step result.
     *
     * @param stepId the step ID
     * @param error the error that caused the failure
     */
    public FlowStepResult(String stepId, Throwable error) {
        this.stepId = stepId;
        this.successful = false;
        this.status = FlowStatus.FAILED;
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
    public static FlowStepResult success(String stepId, String transactionHash,
                                         List<Utxo> outputUtxos, List<TransactionInput> spentInputs) {
        return new FlowStepResult(stepId, FlowStatus.COMPLETED, transactionHash, outputUtxos, spentInputs);
    }

    /**
     * Create a successful step result (without spent inputs).
     *
     * @param stepId the step ID
     * @param transactionHash the transaction hash
     * @param outputUtxos the output UTXOs
     * @return successful step result
     */
    public static FlowStepResult success(String stepId, String transactionHash, List<Utxo> outputUtxos) {
        return success(stepId, transactionHash, outputUtxos, Collections.emptyList());
    }

    /**
     * Create a failed step result.
     *
     * @param stepId the step ID
     * @param error the error
     * @return failed step result
     */
    public static FlowStepResult failure(String stepId, Throwable error) {
        return new FlowStepResult(stepId, error);
    }

    @Override
    public String toString() {
        return "FlowStepResult{" +
                "stepId='" + stepId + '\'' +
                ", successful=" + successful +
                ", status=" + status +
                ", transactionHash='" + transactionHash + '\'' +
                ", outputUtxos=" + outputUtxos.size() +
                ", spentInputs=" + spentInputs.size() +
                ", error=" + (error != null ? error.getMessage() : "null") +
                ", completedAt=" + completedAt +
                '}';
    }
}
