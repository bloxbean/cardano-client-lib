package com.bloxbean.cardano.client.watcher.storage;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Specialized storage interface for TxContext-based watcher operations.
 * 
 * This interface extends the basic WatcherStorage pattern specifically for
 * TxContext storage and rollback recovery scenarios. It provides:
 * 
 * 1. TxContext serialization and deserialization
 * 2. Rollback recovery support
 * 3. Transaction resubmission capabilities
 * 4. Simplified query operations for TxContext data
 */
public interface TxContextWatcherStorage extends AutoCloseable {
    
    /**
     * Store a TxContext for watching.
     * 
     * @param storedTxContext the TxContext data to store
     * @return result indicating success or failure
     */
    Result<Void> store(StoredTxContext storedTxContext);
    
    /**
     * Retrieve stored TxContext by watch ID.
     * 
     * @param watchId the watch identifier
     * @return optional stored TxContext if found
     */
    Optional<StoredTxContext> retrieve(String watchId);
    
    /**
     * Update stored TxContext.
     * 
     * @param storedTxContext the updated TxContext data
     * @return result indicating success or failure
     */
    Result<Void> update(StoredTxContext storedTxContext);
    
    /**
     * Remove stored TxContext.
     * 
     * @param watchId the watch identifier
     * @return result indicating success or failure
     */
    Result<Void> remove(String watchId);
    
    /**
     * Find all TxContexts that need rollback recovery.
     * 
     * These are typically TxContexts that were submitted but not confirmed,
     * and may need to be rebuilt and resubmitted.
     * 
     * @param olderThan find TxContexts older than this timestamp
     * @return list of TxContexts needing recovery
     */
    List<StoredTxContext> findForRollbackRecovery(Instant olderThan);
    
    /**
     * Find TxContexts with signed transactions available for resubmission.
     * 
     * @return list of TxContexts with signed transaction data
     */
    List<StoredTxContext> findForResubmission();
    
    /**
     * Find TxContexts by retry count for cleanup or escalation.
     * 
     * @param minRetryCount minimum retry count
     * @return list of TxContexts with retry count greater than or equal to minRetryCount
     */
    List<StoredTxContext> findByRetryCount(int minRetryCount);
    
    /**
     * Cleanup old completed TxContexts.
     * 
     * @param olderThan remove TxContexts older than this timestamp
     * @return number of cleaned up records
     */
    int cleanupCompleted(Instant olderThan);
    
    /**
     * Get storage statistics for monitoring.
     * 
     * @return storage statistics
     */
    StorageStats getStats();
    
    /**
     * Factory method for in-memory storage.
     * 
     * @return new in-memory TxContext storage instance
     */
    static TxContextWatcherStorage inMemory() {
        return new InMemoryTxContextWatcherStorage();
    }
    
    /**
     * Factory method for file-based storage.
     * 
     * @param storagePath path for storage files
     * @return new file-based TxContext storage instance
     */
    static TxContextWatcherStorage file(String storagePath) {
        return new FileTxContextWatcherStorage(storagePath);
    }
    
    /**
     * Storage statistics for monitoring and maintenance.
     */
    class StorageStats {
        private final int totalRecords;
        private final int pendingRecords;
        private final int completedRecords;
        private final int failedRecords;
        private final int recordsNeedingRecovery;
        
        public StorageStats(int totalRecords, int pendingRecords, int completedRecords, 
                          int failedRecords, int recordsNeedingRecovery) {
            this.totalRecords = totalRecords;
            this.pendingRecords = pendingRecords;
            this.completedRecords = completedRecords;
            this.failedRecords = failedRecords;
            this.recordsNeedingRecovery = recordsNeedingRecovery;
        }
        
        public int getTotalRecords() { return totalRecords; }
        public int getPendingRecords() { return pendingRecords; }
        public int getCompletedRecords() { return completedRecords; }
        public int getFailedRecords() { return failedRecords; }
        public int getRecordsNeedingRecovery() { return recordsNeedingRecovery; }
        
        @Override
        public String toString() {
            return String.format("StorageStats{total=%d, pending=%d, completed=%d, failed=%d, needingRecovery=%d}",
                totalRecords, pendingRecords, completedRecords, failedRecords, recordsNeedingRecovery);
        }
    }
}