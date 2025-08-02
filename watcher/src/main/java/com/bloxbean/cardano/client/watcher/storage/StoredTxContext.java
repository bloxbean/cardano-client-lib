package com.bloxbean.cardano.client.watcher.storage;

import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.watcher.api.WatchConfig;
// import com.fasterxml.jackson.annotation.JsonCreator;
// import com.fasterxml.jackson.annotation.JsonProperty;
// import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializable representation of TxContext for storage and rollback recovery.
 * 
 * This class captures the essential components needed to recreate a TxContext:
 * - The AbstractTx array used to compose the transaction
 * - Watch metadata and configuration
 * - Current state for rollback scenarios
 * 
 * Key Design Decisions:
 * 1. Store AbstractTx array as JSON for flexibility
 * 2. Store QuickTxBuilder configuration separately 
 * 3. Support multiple serialization strategies
 * 4. Enable rollback scenarios through rebuild() method
 */
public class StoredTxContext {
    
    // private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String watchId;
    private final String description;
    private final byte[] serializedTxData; // Serialized AbstractTx array
    private final String txBuilderConfig;  // QuickTxBuilder configuration as JSON
    private final WatchConfig watchConfig;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final int retryCount;
    private final String lastTransactionHash;
    private final byte[] lastSignedTransaction; // For resubmission scenarios
    
    // @JsonCreator
    private StoredTxContext(String watchId,
            String description,
            byte[] serializedTxData,
            String txBuilderConfig,
            WatchConfig watchConfig,
            Instant createdAt,
            Instant updatedAt,
            int retryCount,
            String lastTransactionHash,
            byte[] lastSignedTransaction) {
        this.watchId = Objects.requireNonNull(watchId, "watchId cannot be null");
        this.description = description;
        this.serializedTxData = serializedTxData;
        this.txBuilderConfig = txBuilderConfig;
        this.watchConfig = watchConfig;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = updatedAt;
        this.retryCount = retryCount;
        this.lastTransactionHash = lastTransactionHash;
        this.lastSignedTransaction = lastSignedTransaction;
    }
    
    /**
     * Create StoredTxContext from TxContext and metadata.
     * 
     * @param txContext the QuickTxBuilder TxContext to serialize
     * @param watchId the watch identifier
     * @param description optional description
     * @param watchConfig watch configuration
     * @return new StoredTxContext instance
     */
    public static StoredTxContext fromTxContext(QuickTxBuilder.TxContext txContext, 
                                               String watchId, 
                                               String description,
                                               WatchConfig watchConfig) {
        try {
            // Extract and serialize the AbstractTx array from TxContext
            // Note: This requires reflection or TxContext API enhancement to access txList
            // For now, we'll store minimal data and enhance later
            byte[] serializedData = serializeTxContext(txContext);
            String builderConfig = serializeBuilderConfig(txContext);
            
            return new Builder()
                .watchId(watchId)
                .description(description)
                .serializedTxData(serializedData)
                .txBuilderConfig(builderConfig)
                .watchConfig(watchConfig)
                .createdAt(Instant.now())
                .build();
                
        } catch (Exception e) {
            throw new TxContextSerializationException("Failed to serialize TxContext: " + e.getMessage(), e);
        }
    }
    
    /**
     * Rebuild TxContext from stored data using new suppliers.
     * 
     * This is the key method for rollback recovery. It recreates the TxContext
     * with fresh suppliers to handle scenarios where UTXOs have changed.
     * 
     * @param quickTxBuilder the QuickTxBuilder with current suppliers
     * @return rebuilt TxContext ready for transaction building
     */
    public QuickTxBuilder.TxContext rebuild(QuickTxBuilder quickTxBuilder) {
        try {
            // Deserialize the AbstractTx array
            AbstractTx<?>[] abstractTxs = deserializeTxData(serializedTxData);
            
            // Compose using the new QuickTxBuilder (with fresh suppliers)
            QuickTxBuilder.TxContext rebuilt = quickTxBuilder.compose(abstractTxs);
            
            // Apply stored configuration
            applyBuilderConfig(rebuilt, txBuilderConfig);
            
            return rebuilt;
            
        } catch (Exception e) {
            throw new TxContextSerializationException("Failed to rebuild TxContext: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if we have a signed transaction for resubmission.
     * 
     * @return true if a signed transaction is available for resubmission
     */
    public boolean hasSignedTransactionForResubmission() {
        return lastSignedTransaction != null && lastSignedTransaction.length > 0;
    }
    
    /**
     * Get the signed transaction bytes for resubmission.
     * 
     * @return signed transaction bytes or null if not available
     */
    public byte[] getSignedTransactionBytes() {
        return lastSignedTransaction != null ? lastSignedTransaction.clone() : null;
    }
    
    /**
     * Create a copy with updated signed transaction data.
     * 
     * @param transactionHash the transaction hash
     * @param signedTransactionBytes the signed transaction bytes
     * @return new StoredTxContext with updated transaction data
     */
    public StoredTxContext withTransactionData(String transactionHash, byte[] signedTransactionBytes) {
        return toBuilder()
            .lastTransactionHash(transactionHash)
            .lastSignedTransaction(signedTransactionBytes)
            .updatedAt(Instant.now())
            .build();
    }
    
    /**
     * Create a copy with incremented retry count.
     * 
     * @return new StoredTxContext with incremented retry count
     */
    public StoredTxContext withIncrementedRetry() {
        return toBuilder()
            .retryCount(retryCount + 1)
            .updatedAt(Instant.now())
            .build();
    }
    
    // Getters
    public String getWatchId() { return watchId; }
    public String getDescription() { return description; }
    public WatchConfig getWatchConfig() { return watchConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getRetryCount() { return retryCount; }
    public String getLastTransactionHash() { return lastTransactionHash; }
    
    /**
     * Serialize to JSON for storage.
     * 
     * @return JSON representation
     */
    public String toJson() {
        // TODO: Implement proper JSON serialization without Jackson dependency
        // For MVP, return simple string representation
        return "{}";
    }
    
    /**
     * Deserialize from JSON.
     * 
     * @param json JSON representation
     * @return StoredTxContext instance
     */
    public static StoredTxContext fromJson(String json) {
        // TODO: Implement proper JSON deserialization
        // For MVP, create empty instance
        throw new TxContextSerializationException("JSON deserialization not implemented yet");
    }
    
    public Builder toBuilder() {
        return new Builder()
            .watchId(watchId)
            .description(description)
            .serializedTxData(serializedTxData)
            .txBuilderConfig(txBuilderConfig)
            .watchConfig(watchConfig)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .retryCount(retryCount)
            .lastTransactionHash(lastTransactionHash)
            .lastSignedTransaction(lastSignedTransaction);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Private serialization helpers
    
    private static byte[] serializeTxContext(QuickTxBuilder.TxContext txContext) {
        // TODO: Implement TxContext serialization
        // This requires either:
        // 1. Reflection to access private txList field
        // 2. Enhancement to TxContext API to expose AbstractTx array
        // 3. Custom serialization strategy
        
        // For MVP, we'll use a simple approach
        // In production, this needs proper implementation
        return new byte[0]; // Placeholder
    }
    
    private static String serializeBuilderConfig(QuickTxBuilder.TxContext txContext) {
        // TODO: Serialize TxContext configuration (signers, options, etc.)
        // This includes:
        // - Signer configuration
        // - Fee payer settings  
        // - Collateral settings
        // - UTXO selection strategy
        // - Other TxContext options
        
        return "{}"; // Placeholder JSON
    }
    
    private static AbstractTx<?>[] deserializeTxData(byte[] serializedData) {
        // TODO: Implement deserialization of AbstractTx array
        // This is the reverse of serializeTxContext()
        
        return new AbstractTx[0]; // Placeholder
    }
    
    private static void applyBuilderConfig(QuickTxBuilder.TxContext txContext, String config) {
        // TODO: Apply stored configuration to rebuilt TxContext
        // This includes re-applying:
        // - Signers (if available)
        // - Configuration options
        // - Builder settings
        
        // Placeholder - no-op for now
    }
    
    /**
     * Builder for StoredTxContext.
     */
    public static class Builder {
        private String watchId;
        private String description;
        private byte[] serializedTxData;
        private String txBuilderConfig;
        private WatchConfig watchConfig;
        private Instant createdAt;
        private Instant updatedAt;
        private int retryCount = 0;
        private String lastTransactionHash;
        private byte[] lastSignedTransaction;
        
        public Builder watchId(String watchId) {
            this.watchId = watchId;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder serializedTxData(byte[] serializedTxData) {
            this.serializedTxData = serializedTxData;
            return this;
        }
        
        public Builder txBuilderConfig(String txBuilderConfig) {
            this.txBuilderConfig = txBuilderConfig;
            return this;
        }
        
        public Builder watchConfig(WatchConfig watchConfig) {
            this.watchConfig = watchConfig;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }
        
        public Builder lastTransactionHash(String lastTransactionHash) {
            this.lastTransactionHash = lastTransactionHash;
            return this;
        }
        
        public Builder lastSignedTransaction(byte[] lastSignedTransaction) {
            this.lastSignedTransaction = lastSignedTransaction;
            return this;
        }
        
        public StoredTxContext build() {
            return new StoredTxContext(watchId, description, serializedTxData, txBuilderConfig,
                watchConfig, createdAt, updatedAt, retryCount, lastTransactionHash, lastSignedTransaction);
        }
    }
    
    /**
     * Exception for TxContext serialization/deserialization errors.
     */
    public static class TxContextSerializationException extends RuntimeException {
        public TxContextSerializationException(String message) {
            super(message);
        }
        
        public TxContextSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}