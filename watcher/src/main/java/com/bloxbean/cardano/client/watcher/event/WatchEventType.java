package com.bloxbean.cardano.client.watcher.event;

/**
 * Types of watch events that can be emitted.
 */
public enum WatchEventType {
    /**
     * Transaction has been submitted to the network.
     */
    TRANSACTION_SUBMITTED,
    
    /**
     * Transaction has been confirmed in a block.
     */
    TRANSACTION_CONFIRMED,
    
    /**
     * Transaction submission or confirmation failed.
     */
    TRANSACTION_FAILED,
    
    /**
     * Watch status has changed.
     */
    WATCH_STATUS_CHANGED,
    
    /**
     * A rollback has been detected affecting the watched transaction.
     */
    ROLLBACK_DETECTED,
    
    /**
     * Watch operation has been cancelled.
     */
    WATCH_CANCELLED,
    
    /**
     * Watch operation has been retried.
     */
    WATCH_RETRIED,
    
    /**
     * Watch operation has started.
     */
    WATCH_STARTED,
    
    /**
     * Watch operation has completed successfully.
     */
    WATCH_COMPLETED,
    
    /**
     * Watch operation has failed.
     */
    WATCH_FAILED
}