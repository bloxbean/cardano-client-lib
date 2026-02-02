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
 */
class CompositeFlowListener implements FlowListener {
    private final FlowListener[] listeners;

    CompositeFlowListener(FlowListener[] listeners) {
        this.listeners = listeners.clone();
    }

    @Override
    public void onFlowStarted(TxFlow flow) {
        for (FlowListener listener : listeners) {
            listener.onFlowStarted(flow);
        }
    }

    @Override
    public void onFlowCompleted(TxFlow flow, FlowResult result) {
        for (FlowListener listener : listeners) {
            listener.onFlowCompleted(flow, result);
        }
    }

    @Override
    public void onFlowFailed(TxFlow flow, FlowResult result) {
        for (FlowListener listener : listeners) {
            listener.onFlowFailed(flow, result);
        }
    }

    @Override
    public void onStepStarted(FlowStep step, int stepIndex, int totalSteps) {
        for (FlowListener listener : listeners) {
            listener.onStepStarted(step, stepIndex, totalSteps);
        }
    }

    @Override
    public void onStepCompleted(FlowStep step, FlowStepResult result) {
        for (FlowListener listener : listeners) {
            listener.onStepCompleted(step, result);
        }
    }

    @Override
    public void onStepFailed(FlowStep step, FlowStepResult result) {
        for (FlowListener listener : listeners) {
            listener.onStepFailed(step, result);
        }
    }

    @Override
    public void onTransactionSubmitted(FlowStep step, String transactionHash) {
        for (FlowListener listener : listeners) {
            listener.onTransactionSubmitted(step, transactionHash);
        }
    }

    @Override
    public void onTransactionConfirmed(FlowStep step, String transactionHash) {
        for (FlowListener listener : listeners) {
            listener.onTransactionConfirmed(step, transactionHash);
        }
    }
}
