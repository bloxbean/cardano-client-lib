package com.bloxbean.cardano.client.txflow;

/**
 * Exception thrown when a flow step dependency cannot be resolved.
 */
public class FlowDependencyException extends RuntimeException {

    public FlowDependencyException(String message) {
        super(message);
    }

    public FlowDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
