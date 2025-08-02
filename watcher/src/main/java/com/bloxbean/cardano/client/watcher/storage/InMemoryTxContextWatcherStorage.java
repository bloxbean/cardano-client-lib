package com.bloxbean.cardano.client.watcher.storage;

import com.bloxbean.cardano.client.api.model.Result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of TxContextWatcherStorage.
 * 
 * This implementation provides:
 * - Thread-safe operations using ConcurrentHashMap
 * - Fast lookups and updates
 * - No persistence (data lost on restart)
 * - Suitable for development and testing
 */
public class InMemoryTxContextWatcherStorage implements TxContextWatcherStorage {
    
    private final ConcurrentMap<String, StoredTxContext> storage = new ConcurrentHashMap<>();
    
    @Override
    public Result<Void> store(StoredTxContext storedTxContext) {
        if (storedTxContext == null) {
            return Result.error("StoredTxContext cannot be null");
        }
        
        String watchId = storedTxContext.getWatchId();
        if (storage.containsKey(watchId)) {
            return Result.error("TxContext with watchId " + watchId + " already exists");
        }
        
        storage.put(watchId, storedTxContext);
        return Result.success("TxContext stored successfully");
    }
    
    @Override
    public Optional<StoredTxContext> retrieve(String watchId) {
        if (watchId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storage.get(watchId));
    }
    
    @Override
    public Result<Void> update(StoredTxContext storedTxContext) {
        if (storedTxContext == null) {
            return Result.error("StoredTxContext cannot be null");
        }
        
        String watchId = storedTxContext.getWatchId();
        if (!storage.containsKey(watchId)) {
            return Result.error("TxContext with watchId " + watchId + " does not exist");
        }
        
        storage.put(watchId, storedTxContext);
        return Result.success("TxContext updated successfully");
    }
    
    @Override
    public Result<Void> remove(String watchId) {
        if (watchId == null) {
            return Result.error("watchId cannot be null");
        }
        
        StoredTxContext removed = storage.remove(watchId);
        if (removed == null) {
            return Result.error("TxContext with watchId " + watchId + " does not exist");
        }
        
        return Result.success("TxContext removed successfully");
    }
    
    @Override
    public List<StoredTxContext> findForRollbackRecovery(Instant olderThan) {
        if (olderThan == null) {
            return new ArrayList<>();
        }
        
        return storage.values().stream()
            .filter(ctx -> ctx.getUpdatedAt() != null && ctx.getUpdatedAt().isBefore(olderThan))
            .filter(ctx -> ctx.getRetryCount() < 3) // Don't recover if too many retries
            .filter(ctx -> ctx.getLastTransactionHash() != null) // Only contexts that were submitted
            .collect(Collectors.toList());
    }
    
    @Override
    public List<StoredTxContext> findForResubmission() {
        return storage.values().stream()
            .filter(StoredTxContext::hasSignedTransactionForResubmission)
            .filter(ctx -> ctx.getRetryCount() < 5) // Limit resubmission attempts
            .collect(Collectors.toList());
    }
    
    @Override
    public List<StoredTxContext> findByRetryCount(int minRetryCount) {
        return storage.values().stream()
            .filter(ctx -> ctx.getRetryCount() >= minRetryCount)
            .collect(Collectors.toList());
    }
    
    @Override
    public int cleanupCompleted(Instant olderThan) {
        if (olderThan == null) {
            return 0;
        }
        
        List<String> toRemove = storage.entrySet().stream()
            .filter(entry -> {
                StoredTxContext ctx = entry.getValue();
                return ctx.getUpdatedAt() != null && 
                       ctx.getUpdatedAt().isBefore(olderThan) &&
                       (ctx.getLastTransactionHash() != null); // Assume completed if has hash
            })
            .map(entry -> entry.getKey())
            .collect(Collectors.toList());
        
        toRemove.forEach(storage::remove);
        return toRemove.size();
    }
    
    @Override
    public StorageStats getStats() {
        List<StoredTxContext> all = new ArrayList<>(storage.values());
        
        int total = all.size();
        int pending = 0;
        int completed = 0;
        int failed = 0;
        int needingRecovery = 0;
        
        Instant recoveryThreshold = Instant.now().minusSeconds(300); // 5 minutes ago
        
        for (StoredTxContext ctx : all) {
            if (ctx.getLastTransactionHash() != null) {
                completed++;
            } else if (ctx.getRetryCount() > 3) {
                failed++;
            } else {
                pending++;
            }
            
            if (ctx.getUpdatedAt() != null && 
                ctx.getUpdatedAt().isBefore(recoveryThreshold) &&
                ctx.getRetryCount() < 3 &&
                ctx.getLastTransactionHash() != null) {
                needingRecovery++;
            }
        }
        
        return new StorageStats(total, pending, completed, failed, needingRecovery);
    }
    
    @Override
    public void close() {
        storage.clear();
    }
    
    /**
     * Get the current size of storage (for testing).
     * 
     * @return number of stored TxContexts
     */
    public int size() {
        return storage.size();
    }
    
    /**
     * Clear all stored data (for testing).
     */
    public void clear() {
        storage.clear();
    }
}