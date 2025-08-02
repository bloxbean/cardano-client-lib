package com.bloxbean.cardano.client.watcher.storage;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable model representing a stored watch record.
 * 
 * Contains all necessary information to persist and restore watch state.
 */
public class StoredWatch {
    
    private final String watchId;
    private final String transactionId;
    private final String transactionHash;
    private final WatchStatus status;
    private final String description;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastCheckedAt;
    private final int retryCount;
    private final String lastError;
    private final String metadata;
    
    private StoredWatch(Builder builder) {
        this.watchId = Objects.requireNonNull(builder.watchId, "watchId cannot be null");
        this.transactionId = builder.transactionId;
        this.transactionHash = builder.transactionHash;
        this.status = Objects.requireNonNull(builder.status, "status cannot be null");
        this.description = builder.description;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt cannot be null");
        this.updatedAt = builder.updatedAt;
        this.lastCheckedAt = builder.lastCheckedAt;
        this.retryCount = builder.retryCount;
        this.lastError = builder.lastError;
        this.metadata = builder.metadata;
    }
    
    public String getWatchId() {
        return watchId;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public String getTransactionHash() {
        return transactionHash;
    }
    
    public WatchStatus getStatus() {
        return status;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    /**
     * Create a new builder with the same values as this instance.
     * 
     * @return a new builder for modification
     */
    public Builder toBuilder() {
        return new Builder()
                .watchId(watchId)
                .transactionId(transactionId)
                .transactionHash(transactionHash)
                .status(status)
                .description(description)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .lastCheckedAt(lastCheckedAt)
                .retryCount(retryCount)
                .lastError(lastError)
                .metadata(metadata);
    }
    
    /**
     * Create a new builder instance.
     * 
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StoredWatch that = (StoredWatch) o;
        return retryCount == that.retryCount &&
                Objects.equals(watchId, that.watchId) &&
                Objects.equals(transactionId, that.transactionId) &&
                Objects.equals(transactionHash, that.transactionHash) &&
                status == that.status &&
                Objects.equals(description, that.description) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt) &&
                Objects.equals(lastCheckedAt, that.lastCheckedAt) &&
                Objects.equals(lastError, that.lastError) &&
                Objects.equals(metadata, that.metadata);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(watchId, transactionId, transactionHash, status, description, 
                createdAt, updatedAt, lastCheckedAt, retryCount, lastError, metadata);
    }
    
    @Override
    public String toString() {
        return "StoredWatch{" +
                "watchId='" + watchId + '\'' +
                ", transactionId='" + transactionId + '\'' +
                ", transactionHash='" + transactionHash + '\'' +
                ", status=" + status +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", lastCheckedAt=" + lastCheckedAt +
                ", retryCount=" + retryCount +
                ", lastError='" + lastError + '\'' +
                ", metadata='" + metadata + '\'' +
                '}';
    }
    
    /**
     * Builder for creating StoredWatch instances.
     */
    public static class Builder {
        private String watchId;
        private String transactionId;
        private String transactionHash;
        private WatchStatus status;
        private String description;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant lastCheckedAt;
        private int retryCount = 0;
        private String lastError;
        private String metadata;
        
        public Builder watchId(String watchId) {
            this.watchId = watchId;
            return this;
        }
        
        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }
        
        public Builder transactionHash(String transactionHash) {
            this.transactionHash = transactionHash;
            return this;
        }
        
        public Builder status(WatchStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
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
        
        public Builder lastCheckedAt(Instant lastCheckedAt) {
            this.lastCheckedAt = lastCheckedAt;
            return this;
        }
        
        public Builder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }
        
        public Builder lastError(String lastError) {
            this.lastError = lastError;
            return this;
        }
        
        public Builder metadata(String metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public StoredWatch build() {
            return new StoredWatch(this);
        }
    }
}