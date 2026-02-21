package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import lombok.Getter;

/**
 * Exception indicating a transaction rollback was detected during flow execution.
 * <p>
 * This exception is used internally by the {@link FlowExecutor} to signal rollback
 * conditions when using {@link RollbackStrategy#REBUILD_FROM_FAILED} or
 * {@link RollbackStrategy#REBUILD_ENTIRE_FLOW} strategies.
 * <p>
 * The {@code requiresFlowRestart} flag indicates whether the rollback requires
 * restarting the entire flow (for REBUILD_ENTIRE_FLOW strategy) or just rebuilding
 * the affected step (for REBUILD_FROM_FAILED strategy).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Thrown internally when rollback is detected
 * throw new RollbackException(
 *     "Transaction rolled back: " + txHash,
 *     txHash,
 *     step,
 *     previousBlockHeight,
 *     true  // requiresFlowRestart
 * );
 * }</pre>
 */
@Getter
public class RollbackException extends FlowExecutionException {

    /**
     * The transaction hash that was rolled back.
     */
    private final String txHash;

    /**
     * The flow step whose transaction was rolled back.
     */
    private final FlowStep step;

    /**
     * The block height where the transaction was previously included.
     */
    private final long previousBlockHeight;

    /**
     * Whether this rollback requires restarting the entire flow.
     * <p>
     * When true, the flow executor should clear all step results and restart
     * from the beginning. When false, only the affected step needs to be rebuilt.
     */
    private final boolean requiresFlowRestart;

    /**
     * Create a new RollbackException.
     *
     * @param message the exception message
     * @param txHash the rolled back transaction hash
     * @param step the affected flow step
     * @param previousBlockHeight the block height where the transaction was previously included
     * @param requiresFlowRestart whether the entire flow must be restarted
     */
    public RollbackException(String message, String txHash, FlowStep step,
                              long previousBlockHeight, boolean requiresFlowRestart) {
        super(message);
        this.txHash = txHash;
        this.step = step;
        this.previousBlockHeight = previousBlockHeight;
        this.requiresFlowRestart = requiresFlowRestart;
    }

    /**
     * Create a new RollbackException with a cause.
     *
     * @param message the exception message
     * @param cause the underlying cause
     * @param txHash the rolled back transaction hash
     * @param step the affected flow step
     * @param previousBlockHeight the block height where the transaction was previously included
     * @param requiresFlowRestart whether the entire flow must be restarted
     */
    public RollbackException(String message, Throwable cause, String txHash, FlowStep step,
                              long previousBlockHeight, boolean requiresFlowRestart) {
        super(message, cause);
        this.txHash = txHash;
        this.step = step;
        this.previousBlockHeight = previousBlockHeight;
        this.requiresFlowRestart = requiresFlowRestart;
    }

    /**
     * Create a RollbackException for a step rebuild scenario (REBUILD_FROM_FAILED).
     *
     * @param txHash the rolled back transaction hash
     * @param step the affected flow step
     * @param previousBlockHeight the block height where the transaction was previously included
     * @return a new RollbackException for step rebuild
     */
    public static RollbackException forStepRebuild(String txHash, FlowStep step, long previousBlockHeight) {
        return new RollbackException(
                String.format("Transaction %s rolled back at step '%s' (was in block %d)",
                        txHash, step.getId(), previousBlockHeight),
                txHash, step, previousBlockHeight, false);
    }

    /**
     * Create a RollbackException for a flow restart scenario (REBUILD_ENTIRE_FLOW).
     *
     * @param txHash the rolled back transaction hash
     * @param step the affected flow step
     * @param previousBlockHeight the block height where the transaction was previously included
     * @return a new RollbackException for flow restart
     */
    public static RollbackException forFlowRestart(String txHash, FlowStep step, long previousBlockHeight) {
        return new RollbackException(
                String.format("Transaction %s rolled back at step '%s' (was in block %d) - flow restart required",
                        txHash, step.getId(), previousBlockHeight),
                txHash, step, previousBlockHeight, true);
    }
}
