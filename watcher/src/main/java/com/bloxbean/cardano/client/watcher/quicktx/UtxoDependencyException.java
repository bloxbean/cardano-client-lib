package com.bloxbean.cardano.client.watcher.quicktx;

/**
 * Exception thrown when UTXO dependencies cannot be resolved.
 * 
 * This exception indicates that a step's dependency on previous step outputs
 * could not be satisfied, typically because:
 * - The referenced step has not completed successfully
 * - The referenced step produced no outputs
 * - The selection strategy found no matching UTXOs
 */
public class UtxoDependencyException extends RuntimeException {
    
    /**
     * Create a UTXO dependency exception with a message.
     * 
     * @param message the error message
     */
    public UtxoDependencyException(String message) {
        super(message);
    }
    
    /**
     * Create a UTXO dependency exception with a message and cause.
     * 
     * @param message the error message
     * @param cause the underlying cause
     */
    public UtxoDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}