package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handle for monitoring and controlling a running transaction flow.
 * <p>
 * Provides methods to:
 * <ul>
 *     <li>Wait for flow completion</li>
 *     <li>Check current status</li>
 *     <li>Get intermediate results</li>
 *     <li>Cancel execution</li>
 * </ul>
 */
public class FlowHandle {
    private final TxFlow flow;
    private final CompletableFuture<FlowResult> resultFuture;
    private volatile FlowStatus currentStatus = FlowStatus.PENDING;
    private volatile String currentStepId;
    private volatile boolean cancelled = false;
    private final AtomicInteger completedStepCount = new AtomicInteger(0);

    /**
     * Create a new FlowHandle.
     *
     * @param flow the flow being executed
     * @param resultFuture the future that will complete with the result
     */
    public FlowHandle(TxFlow flow, CompletableFuture<FlowResult> resultFuture) {
        this.flow = flow;
        this.resultFuture = resultFuture;
    }

    /**
     * Get the flow being executed.
     *
     * @return the flow
     */
    public TxFlow getFlow() {
        return flow;
    }

    /**
     * Get the current execution status.
     *
     * @return the current status
     */
    public FlowStatus getStatus() {
        return currentStatus;
    }

    /**
     * Get the ID of the currently executing step.
     *
     * @return the current step ID, or empty if no step is executing
     */
    public Optional<String> getCurrentStepId() {
        return Optional.ofNullable(currentStepId);
    }

    /**
     * Get the number of completed steps.
     *
     * @return the completed step count
     */
    public int getCompletedStepCount() {
        return completedStepCount.get();
    }

    /**
     * Get the total number of steps.
     *
     * @return the total step count
     */
    public int getTotalStepCount() {
        return flow.getSteps().size();
    }

    /**
     * Check if the flow is still running.
     *
     * @return true if the flow is running
     */
    public boolean isRunning() {
        return currentStatus == FlowStatus.IN_PROGRESS;
    }

    /**
     * Check if the flow has completed (successfully or with failure).
     *
     * @return true if the flow has completed
     */
    public boolean isDone() {
        return resultFuture.isDone();
    }

    /**
     * Wait for the flow to complete and return the result.
     *
     * @return the flow result
     * @throws InterruptedException if interrupted while waiting
     */
    public FlowResult await() throws InterruptedException {
        try {
            return resultFuture.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Flow execution failed", cause);
        }
    }

    /**
     * Wait for the flow to complete with a timeout.
     *
     * @param timeout the maximum time to wait
     * @return the flow result
     * @throws InterruptedException if interrupted while waiting
     * @throws TimeoutException if the wait timed out
     */
    public FlowResult await(Duration timeout) throws InterruptedException, TimeoutException {
        try {
            return resultFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Flow execution failed", cause);
        }
    }

    /**
     * Get the result if completed, without blocking.
     * <p>
     * Returns {@code Optional.empty()} if the flow is still running or was cancelled.
     * Throws the flow's exception if it completed exceptionally.
     *
     * @return the result if available
     * @throws RuntimeException if the flow completed with an exception
     */
    public Optional<FlowResult> getResult() {
        if (!resultFuture.isDone()) {
            return Optional.empty();
        }
        try {
            return Optional.of(resultFuture.get());
        } catch (java.util.concurrent.CancellationException e) {
            return Optional.empty();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Flow execution failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Get the underlying CompletableFuture.
     *
     * @return the result future
     */
    public CompletableFuture<FlowResult> getResultFuture() {
        return resultFuture;
    }

    /**
     * Cancel the flow execution.
     * <p>
     * Note: This will attempt to cancel execution, but steps that have
     * already started cannot be undone.
     *
     * @return true if cancellation was successful
     */
    public boolean cancel() {
        cancelled = true;
        currentStatus = FlowStatus.CANCELLED;
        return resultFuture.cancel(true);
    }

    // Internal methods for updating status (package-private for FlowExecutor access)

    void updateStatus(FlowStatus status) {
        this.currentStatus = status;
    }

    void updateCurrentStep(String stepId) {
        this.currentStepId = stepId;
    }

    void incrementCompletedSteps() {
        this.completedStepCount.incrementAndGet();
    }

    void resetCompletedSteps() {
        this.completedStepCount.set(0);
    }

    /**
     * Check if cancellation has been requested.
     *
     * @return true if cancel() has been called
     */
    boolean isCancelled() {
        return cancelled;
    }

    @Override
    public String toString() {
        return "FlowHandle{" +
                "flowId='" + flow.getId() + '\'' +
                ", status=" + currentStatus +
                ", currentStepId='" + currentStepId + '\'' +
                ", progress=" + completedStepCount.get() + "/" + getTotalStepCount() +
                ", done=" + isDone() +
                '}';
    }
}
