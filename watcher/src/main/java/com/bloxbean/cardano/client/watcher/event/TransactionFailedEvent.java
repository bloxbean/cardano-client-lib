package com.bloxbean.cardano.client.watcher.event;

import java.time.Instant;

/**
 * Event emitted when a transaction submission or confirmation fails.
 */
public class TransactionFailedEvent extends WatchEvent {
    
    private final String transactionHash;
    private final String error;
    
    public TransactionFailedEvent(String watchId, String transactionHash, String error, Instant timestamp) {
        super(watchId, WatchEventType.TRANSACTION_FAILED, timestamp);
        this.transactionHash = transactionHash;
        this.error = error;
    }
    
    public TransactionFailedEvent(String watchId, String transactionHash, String error) {
        this(watchId, transactionHash, error, Instant.now());
    }
    
    /**
     * Get the hash of the failed transaction.
     * 
     * @return the transaction hash, may be null if transaction was never submitted
     */
    public String getTransactionHash() {
        return transactionHash;
    }
    
    /**
     * Get the error message describing why the transaction failed.
     * 
     * @return the error message
     */
    public String getError() {
        return error;
    }
    
    @Override
    public String toString() {
        return "TransactionFailedEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", watchId='" + getWatchId() + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", error='" + error + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}