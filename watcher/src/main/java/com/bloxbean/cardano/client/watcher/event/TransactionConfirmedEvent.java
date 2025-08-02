package com.bloxbean.cardano.client.watcher.event;

import java.time.Instant;

/**
 * Event emitted when a transaction has been confirmed in a block.
 */
public class TransactionConfirmedEvent extends WatchEvent {
    
    private final String transactionHash;
    private final long blockHeight;
    private final int confirmations;
    
    public TransactionConfirmedEvent(String watchId, String transactionHash, 
                                   long blockHeight, int confirmations, Instant timestamp) {
        super(watchId, WatchEventType.TRANSACTION_CONFIRMED, timestamp);
        this.transactionHash = transactionHash;
        this.blockHeight = blockHeight;
        this.confirmations = confirmations;
    }
    
    public TransactionConfirmedEvent(String watchId, String transactionHash, 
                                   long blockHeight, int confirmations) {
        this(watchId, transactionHash, blockHeight, confirmations, Instant.now());
    }
    
    /**
     * Get the hash of the confirmed transaction.
     * 
     * @return the transaction hash
     */
    public String getTransactionHash() {
        return transactionHash;
    }
    
    /**
     * Get the block height where the transaction was confirmed.
     * 
     * @return the block height
     */
    public long getBlockHeight() {
        return blockHeight;
    }
    
    /**
     * Get the number of confirmations.
     * 
     * @return the number of confirmations
     */
    public int getConfirmations() {
        return confirmations;
    }
    
    @Override
    public String toString() {
        return "TransactionConfirmedEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", watchId='" + getWatchId() + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", blockHeight=" + blockHeight +
                ", confirmations=" + confirmations +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}