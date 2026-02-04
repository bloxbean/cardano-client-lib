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
}
