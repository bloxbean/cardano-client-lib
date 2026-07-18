package com.bloxbean.cardano.client.txflow.exec;

/**
 * Outcome of a cooperative cancellation request made through a
 * {@link FlowExecutionHandle}.
 *
 * <p>A successful request signals the running execution; it does not interrupt
 * the executor task or imply that the terminal result is already available.</p>
 */
public enum CancellationResult {
    /** The cancellation signal was recorded by this call. */
    REQUESTED,
    /** A previous call already recorded the cancellation signal. */
    ALREADY_REQUESTED,
    /** The execution had already reached a terminal result. */
    ALREADY_TERMINAL
}
