package com.bloxbean.cardano.client.txflow.result;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import lombok.Getter;

import java.time.Instant;
import java.time.Clock;
import java.util.Objects;
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
    /**
     * Time at which this result snapshot was observed. For a terminal status,
     * this is the step completion time; an {@link FlowStatus#IN_PROGRESS}
     * projection uses the time at which the submitted state was reported.
     */
    private final Instant completedAt;

    /**
     * Creates a successful step result using the system clock.
     *
     * @param stepId the step ID
     * @param status the final status
     * @param transactionHash the transaction hash
     * @param outputUtxos the output UTXOs
     * @param spentInputs the spent transaction inputs
     */
    public FlowStepResult(String stepId, FlowStatus status, String transactionHash,
                          List<Utxo> outputUtxos, List<TransactionInput> spentInputs) {
        this(stepId, status, transactionHash, outputUtxos, spentInputs,
                Clock.systemUTC().instant());
    }

    /**
     * Creates a non-failure step-result snapshot at an explicit observation time.
     *
     * @param stepId step identifier
     * @param status step status at the observation time
     * @param transactionHash submitted transaction hash
     * @param outputUtxos outputs produced by the transaction
     * @param spentInputs inputs consumed by the transaction
     * @param completedAt result observation timestamp
     */
    public FlowStepResult(String stepId, FlowStatus status, String transactionHash,
                          List<Utxo> outputUtxos, List<TransactionInput> spentInputs,
                          Instant completedAt) {
        this(stepId, status, transactionHash, outputUtxos, spentInputs, null, completedAt);
    }

    private FlowStepResult(String stepId, FlowStatus status, String transactionHash,
                           List<Utxo> outputUtxos, List<TransactionInput> spentInputs,
                           Throwable error, Instant completedAt) {
        this.stepId = stepId;
        this.successful = status == FlowStatus.COMPLETED;
        this.status = status;
        this.transactionHash = transactionHash;
        this.outputUtxos = outputUtxos != null ? List.copyOf(outputUtxos) : Collections.emptyList();
        this.spentInputs = spentInputs != null ? List.copyOf(spentInputs) : Collections.emptyList();
        this.error = error;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
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
        this(stepId, error, Clock.systemUTC().instant());
    }

    /**
     * Creates a failed step result at an explicit time.
     *
     * @param stepId step identifier
     * @param error failure that terminated the step
     * @param completedAt terminal timestamp
     */
    public FlowStepResult(String stepId, Throwable error, Instant completedAt) {
        this(stepId, FlowStatus.FAILED, null, Collections.emptyList(),
                Collections.emptyList(), error, completedAt);
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

    /**
     * Creates a successful result using a caller-owned timestamp.
     *
     * @param stepId step identifier
     * @param transactionHash submitted transaction hash
     * @param outputUtxos outputs produced by the transaction
     * @param spentInputs inputs consumed by the transaction
     * @param completedAt terminal timestamp
     * @return successful step result
     */
    public static FlowStepResult successAt(String stepId, String transactionHash,
                                           List<Utxo> outputUtxos,
                                           List<TransactionInput> spentInputs,
                                           Instant completedAt) {
        return new FlowStepResult(stepId, FlowStatus.COMPLETED, transactionHash,
                outputUtxos, spentInputs, completedAt);
    }

    /**
     * Creates a failed result using a caller-owned timestamp.
     *
     * @param stepId step identifier
     * @param error failure that terminated the step
     * @param completedAt terminal timestamp
     * @return failed step result
     */
    public static FlowStepResult failureAt(String stepId, Throwable error, Instant completedAt) {
        return new FlowStepResult(stepId, error, completedAt);
    }

    /**
     * Creates a cancelled, unsubmitted step result at a caller-owned observation
     * time. Submitted transactions remain {@link FlowStatus#IN_PROGRESS} when
     * only their confirmation wait is cancelled.
     *
     * @param stepId step identifier
     * @param error cancellation cause
     * @param observedAt cancellation observation time
     * @return cancelled step result without a transaction identity
     */
    public static FlowStepResult cancelledAt(
            String stepId, Throwable error, Instant observedAt) {
        return new FlowStepResult(stepId, FlowStatus.CANCELLED, null,
                Collections.emptyList(), Collections.emptyList(),
                Objects.requireNonNull(error, "error"), observedAt);
    }

    /**
     * Projects a submitted transaction whose terminal disposition is not yet
     * known. This is used when cancellation stops confirmation polling: the
     * transaction identity must remain visible for later reconciliation, but it
     * must not be reported as successful or failed on chain.
     *
     * @param stepId step identifier
     * @param transactionHash submitted transaction hash
     * @param outputUtxos outputs carried by the submitted transaction
     * @param spentInputs inputs carried by the submitted transaction
     * @param cause reason observation stopped before a terminal disposition
     * @param observedAt projection observation time
     * @return hash-bearing in-progress step result
     */
    public static FlowStepResult submissionPendingAt(
            String stepId, String transactionHash, List<Utxo> outputUtxos,
            List<TransactionInput> spentInputs, Throwable cause, Instant observedAt) {
        return new FlowStepResult(stepId, FlowStatus.IN_PROGRESS,
                Objects.requireNonNull(transactionHash, "transactionHash"),
                outputUtxos, spentInputs, Objects.requireNonNull(cause, "cause"), observedAt);
    }

    /**
     * Creates a failed result for a transaction whose submission identity is
     * already known. The transaction identity and input/output context remain
     * available for reconciliation diagnostics even though confirmation failed.
     *
     * @param stepId step identifier
     * @param transactionHash submitted transaction hash
     * @param outputUtxos outputs carried by the submitted transaction
     * @param spentInputs inputs carried by the submitted transaction
     * @param error failure that terminated confirmation
     * @return failed step result retaining the submitted transaction identity
     */
    public static FlowStepResult failureAfterSubmission(
            String stepId, String transactionHash, List<Utxo> outputUtxos,
            List<TransactionInput> spentInputs, Throwable error) {
        return failureAfterSubmissionAt(stepId, transactionHash, outputUtxos,
                spentInputs, error, Clock.systemUTC().instant());
    }

    /**
     * Creates a failed submitted-transaction result at a caller-owned
     * observation time.
     *
     * @param stepId step identifier
     * @param transactionHash submitted transaction hash
     * @param outputUtxos outputs carried by the submitted transaction
     * @param spentInputs inputs carried by the submitted transaction
     * @param error failure that terminated confirmation
     * @param completedAt terminal observation timestamp
     * @return failed step result retaining the submitted transaction identity
     */
    public static FlowStepResult failureAfterSubmissionAt(
            String stepId, String transactionHash, List<Utxo> outputUtxos,
            List<TransactionInput> spentInputs, Throwable error, Instant completedAt) {
        return new FlowStepResult(stepId, FlowStatus.FAILED,
                Objects.requireNonNull(transactionHash, "transactionHash"),
                outputUtxos, spentInputs, error, completedAt);
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
