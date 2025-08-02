package com.bloxbean.cardano.client.watcher.api;

/**
 * Status of a watched transaction.
 */
public enum WatchStatus {
    /**
     * Transaction is being built/prepared for submission.
     */
    BUILDING,
    
    /**
     * Transaction has been submitted to the network.
     */
    SUBMITTED,
    
    /**
     * Transaction is pending confirmation.
     */
    PENDING,
    
    /**
     * Transaction is being actively watched.
     */
    WATCHING,
    
    /**
     * Transaction watch is retrying after failure.
     */
    RETRYING,
    
    /**
     * Transaction has been confirmed.
     */
    CONFIRMED,
    
    /**
     * Transaction failed to be submitted or confirmed.
     */
    FAILED,
    
    /**
     * Watch operation was cancelled.
     */
    CANCELLED,
    
    /**
     * Transaction is being rebuilt due to rollback.
     */
    REBUILDING
}