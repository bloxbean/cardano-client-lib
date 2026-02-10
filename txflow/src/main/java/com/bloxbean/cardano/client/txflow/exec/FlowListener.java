package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;

/**
 * Listener interface for flow execution events.
 * <p>
 * Implement this interface to receive callbacks during flow execution.
 * This is useful for logging, monitoring, and custom event handling.
 */
public interface FlowListener {

    /**
     * Called when flow execution starts.
     *
     * @param flow the flow being executed
     */
    default void onFlowStarted(TxFlow flow) {
    }

    /**
     * Called when flow execution completes successfully.
     *
     * @param flow the flow that was executed
     * @param result the flow result
     */
    default void onFlowCompleted(TxFlow flow, FlowResult result) {
    }

    /**
     * Called when flow execution fails.
     *
     * @param flow the flow that failed
     * @param result the flow result containing error info
     */
    default void onFlowFailed(TxFlow flow, FlowResult result) {
    }

    /**
     * Called when a step starts execution.
     *
     * @param step the step starting
     * @param stepIndex the 0-based index of the step
     * @param totalSteps the total number of steps
     */
    default void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
    }

    /**
     * Called when a step completes successfully.
     *
     * @param step the step that completed
     * @param result the step result
     */
    default void onStepCompleted(FlowStep step, FlowStepResult result) {
    }

    /**
     * Called when a step fails.
     *
     * @param step the step that failed
     * @param result the step result containing error info
     */
    default void onStepFailed(FlowStep step, FlowStepResult result) {
    }

    /**
     * Called when a transaction is submitted for a step.
     *
     * @param step the step
     * @param transactionHash the submitted transaction hash
     */
    default void onTransactionSubmitted(FlowStep step, String transactionHash) {
    }

    /**
     * Called when a transaction is confirmed for a step.
     *
     * @param step the step
     * @param transactionHash the confirmed transaction hash
     */
    default void onTransactionConfirmed(FlowStep step, String transactionHash) {
    }

    /**
     * Called when a transaction is first included in a block.
     * <p>
     * This indicates the transaction has been included but may not have reached
     * the minimum confirmation depth yet.
     *
     * @param step the step
     * @param transactionHash the transaction hash
     * @param blockHeight the block height where the transaction was included
     */
    default void onTransactionInBlock(FlowStep step, String transactionHash, long blockHeight) {
    }

    /**
     * Called when the confirmation depth changes during monitoring.
     * <p>
     * This callback is invoked periodically as new blocks are added to the chain.
     * Useful for displaying progress to users or logging.
     *
     * @param step the step
     * @param transactionHash the transaction hash
     * @param depth the current confirmation depth
     * @param status the current confirmation status
     */
    default void onConfirmationDepthChanged(FlowStep step, String transactionHash, int depth, ConfirmationStatus status) {
    }

    /**
     * Called when a transaction rollback is detected.
     * <p>
     * This indicates that a transaction which was previously included in the chain
     * has been removed due to a chain reorganization.
     *
     * @param step the step whose transaction was rolled back
     * @param transactionHash the rolled back transaction hash
     * @param previousBlockHeight the block height where the transaction was previously included
     */
    default void onTransactionRolledBack(FlowStep step, String transactionHash, long previousBlockHeight) {
    }

    /**
     * Called when a step is being retried after a failure.
     * <p>
     * Note: This is called BEFORE the retry attempt starts. The attemptNumber indicates
     * which attempt just failed, not the upcoming retry attempt number.
     *
     * @param step the step being retried
     * @param attemptNumber the attempt number that just failed (1-indexed: 1 = first attempt failed, 2 = second attempt failed, etc.)
     * @param maxAttempts the maximum number of attempts configured
     * @param lastError the error from the failed attempt
     */
    default void onStepRetry(FlowStep step, int attemptNumber, int maxAttempts, Throwable lastError) {
    }

    /**
     * Called when all retry attempts are exhausted for a step.
     *
     * @param step the step that exhausted retries
     * @param totalAttempts the total number of attempts made
     * @param lastError the error from the final attempt
     */
    default void onStepRetryExhausted(FlowStep step, int totalAttempts, Throwable lastError) {
    }

    /**
     * Called when a step is being rebuilt after a rollback.
     * <p>
     * This callback is invoked when using {@link RollbackStrategy#REBUILD_FROM_FAILED}
     * and a rollback is detected for a step. The step will be rebuilt with fresh
     * UTXOs from the current chain state.
     *
     * @param step the step being rebuilt
     * @param attemptNumber the current rebuild attempt number (1-indexed)
     * @param maxAttempts the maximum number of rebuild attempts allowed
     * @param reason the reason for rebuilding (e.g., "rollback detected")
     */
    default void onStepRebuilding(FlowStep step, int attemptNumber, int maxAttempts, String reason) {
    }

    /**
     * Called when the entire flow is being restarted due to a rollback.
     * <p>
     * This callback is invoked when using {@link RollbackStrategy#REBUILD_ENTIRE_FLOW}
     * and a rollback is detected. All step results will be cleared and the flow
     * will be re-executed from the beginning.
     *
     * @param flow the flow being restarted
     * @param attemptNumber the current restart attempt number (1-indexed)
     * @param maxAttempts the maximum number of restart attempts allowed
     * @param reason the reason for restarting (e.g., "rollback detected at step X")
     */
    default void onFlowRestarting(TxFlow flow, int attemptNumber, int maxAttempts, String reason) {
    }

    /**
     * A no-op listener that does nothing.
     */
    FlowListener NOOP = new FlowListener() {};

    /**
     * Composite listener that forwards events to multiple listeners.
     *
     * @param listeners the listeners to compose
     * @return a composite listener
     */
    static FlowListener composite(FlowListener... listeners) {
        if (listeners == null || listeners.length == 0) {
            return NOOP;
        }
        if (listeners.length == 1) {
            return listeners[0];
        }
        return new CompositeFlowListener(listeners);
    }
}

/**
 * Internal composite listener implementation.
 * <p>
 * Wraps all listener invocations in try-catch to ensure that an exception
 * from one listener does not prevent other listeners from being notified
 * or crash the flow execution.
 */
@lombok.extern.slf4j.Slf4j
class CompositeFlowListener implements FlowListener {
    private final FlowListener[] listeners;

    CompositeFlowListener(FlowListener[] listeners) {
        this.listeners = listeners.clone();
    }

    @Override
    public void onFlowStarted(TxFlow flow) {
        for (FlowListener listener : listeners) {
            try {
                listener.onFlowStarted(flow);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onFlowStarted: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onFlowCompleted(TxFlow flow, FlowResult result) {
        for (FlowListener listener : listeners) {
            try {
                listener.onFlowCompleted(flow, result);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onFlowCompleted: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onFlowFailed(TxFlow flow, FlowResult result) {
        for (FlowListener listener : listeners) {
            try {
                listener.onFlowFailed(flow, result);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onFlowFailed: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
        for (FlowListener listener : listeners) {
            try {
                listener.onStepStarted(step, stepIndex, totalSteps);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onStepStarted: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onStepCompleted(FlowStep step, FlowStepResult result) {
        for (FlowListener listener : listeners) {
            try {
                listener.onStepCompleted(step, result);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onStepCompleted: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onStepFailed(FlowStep step, FlowStepResult result) {
        for (FlowListener listener : listeners) {
            try {
                listener.onStepFailed(step, result);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onStepFailed: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onTransactionSubmitted(FlowStep step, String transactionHash) {
        for (FlowListener listener : listeners) {
            try {
                listener.onTransactionSubmitted(step, transactionHash);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onTransactionSubmitted: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onTransactionConfirmed(FlowStep step, String transactionHash) {
        for (FlowListener listener : listeners) {
            try {
                listener.onTransactionConfirmed(step, transactionHash);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onTransactionConfirmed: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onTransactionInBlock(FlowStep step, String transactionHash, long blockHeight) {
        for (FlowListener listener : listeners) {
            try {
                listener.onTransactionInBlock(step, transactionHash, blockHeight);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onTransactionInBlock: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onConfirmationDepthChanged(FlowStep step, String transactionHash, int depth, ConfirmationStatus status) {
        for (FlowListener listener : listeners) {
            try {
                listener.onConfirmationDepthChanged(step, transactionHash, depth, status);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onConfirmationDepthChanged: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onTransactionRolledBack(FlowStep step, String transactionHash, long previousBlockHeight) {
        for (FlowListener listener : listeners) {
            try {
                listener.onTransactionRolledBack(step, transactionHash, previousBlockHeight);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onTransactionRolledBack: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onStepRetry(FlowStep step, int attemptNumber, int maxAttempts, Throwable lastError) {
        for (FlowListener listener : listeners) {
            try {
                listener.onStepRetry(step, attemptNumber, maxAttempts, lastError);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onStepRetry: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onStepRetryExhausted(FlowStep step, int totalAttempts, Throwable lastError) {
        for (FlowListener listener : listeners) {
            try {
                listener.onStepRetryExhausted(step, totalAttempts, lastError);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onStepRetryExhausted: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onStepRebuilding(FlowStep step, int attemptNumber, int maxAttempts, String reason) {
        for (FlowListener listener : listeners) {
            try {
                listener.onStepRebuilding(step, attemptNumber, maxAttempts, reason);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onStepRebuilding: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void onFlowRestarting(TxFlow flow, int attemptNumber, int maxAttempts, String reason) {
        for (FlowListener listener : listeners) {
            try {
                listener.onFlowRestarting(flow, attemptNumber, maxAttempts, reason);
            } catch (Exception e) {
                log.warn("Listener {} threw exception in onFlowRestarting: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
