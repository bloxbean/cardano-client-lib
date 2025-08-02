package com.bloxbean.cardano.client.watcher.event;

import java.time.Instant;

/**
 * Event emitted when a rollback is detected affecting a watched transaction.
 */
public class RollbackDetectedEvent extends WatchEvent {
    
    private final String transactionHash;
    private final long fromHeight;
    private final long toHeight;
    
    public RollbackDetectedEvent(String watchId, String transactionHash, 
                               long fromHeight, long toHeight, Instant timestamp) {
        super(watchId, WatchEventType.ROLLBACK_DETECTED, timestamp);
        this.transactionHash = transactionHash;
        this.fromHeight = fromHeight;
        this.toHeight = toHeight;
    }
    
    public RollbackDetectedEvent(String watchId, String transactionHash, 
                               long fromHeight, long toHeight) {
        this(watchId, transactionHash, fromHeight, toHeight, Instant.now());
    }
    
    /**
     * Get the hash of the affected transaction.
     * 
     * @return the transaction hash
     */
    public String getTransactionHash() {
        return transactionHash;
    }
    
    /**
     * Get the block height where the rollback started.
     * 
     * @return the starting height of the rollback
     */
    public long getFromHeight() {
        return fromHeight;
    }
    
    /**
     * Get the block height where the chain rolled back to.
     * 
     * @return the ending height after rollback
     */
    public long getToHeight() {
        return toHeight;
    }
    
    /**
     * Get the number of blocks that were rolled back.
     * 
     * @return the rollback depth
     */
    public long getRollbackDepth() {
        return fromHeight - toHeight;
    }
    
    @Override
    public String toString() {
        return "RollbackDetectedEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", watchId='" + getWatchId() + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", fromHeight=" + fromHeight +
                ", toHeight=" + toHeight +
                ", rollbackDepth=" + getRollbackDepth() +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}