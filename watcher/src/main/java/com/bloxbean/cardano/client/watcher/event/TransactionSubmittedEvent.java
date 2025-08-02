package com.bloxbean.cardano.client.watcher.event;

import java.time.Instant;

/**
 * Event emitted when a transaction has been submitted to the network.
 */
public class TransactionSubmittedEvent extends WatchEvent {
    
    private final String transactionHash;
    
    public TransactionSubmittedEvent(String watchId, String transactionHash, Instant timestamp) {
        super(watchId, WatchEventType.TRANSACTION_SUBMITTED, timestamp);
        this.transactionHash = transactionHash;
    }
    
    public TransactionSubmittedEvent(String watchId, String transactionHash) {
        this(watchId, transactionHash, Instant.now());
    }
    
    /**
     * Get the hash of the submitted transaction.
     * 
     * @return the transaction hash
     */
    public String getTransactionHash() {
        return transactionHash;
    }
    
    @Override
    public String toString() {
        return "TransactionSubmittedEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", watchId='" + getWatchId() + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}