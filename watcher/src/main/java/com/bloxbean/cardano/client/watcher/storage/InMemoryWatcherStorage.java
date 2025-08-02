package com.bloxbean.cardano.client.watcher.storage;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of WatcherStorage.
 * 
 * Uses ConcurrentHashMap for thread-safe operations and provides
 * automatic cleanup mechanisms for bounded memory usage.
 */
public class InMemoryWatcherStorage implements WatcherStorage {
    
    private final ConcurrentHashMap<String, StoredWatch> watches;
    private final ReadWriteLock lock;
    
    // Active statuses for quick filtering
    private static final Set<WatchStatus> ACTIVE_STATUSES = EnumSet.of(
        WatchStatus.BUILDING,
        WatchStatus.SUBMITTED, 
        WatchStatus.PENDING,
        WatchStatus.WATCHING,
        WatchStatus.RETRYING,
        WatchStatus.REBUILDING
    );
    
    public InMemoryWatcherStorage() {
        this.watches = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }
    
    @Override
    public void store(StoredWatch watch) throws StorageException {
        if (watch == null) {
            throw new IllegalArgumentException("Watch cannot be null");
        }
        
        String watchId = watch.getWatchId();
        if (watchId == null || watchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Watch ID cannot be null or empty");
        }
        
        lock.writeLock().lock();
        try {
            if (watches.containsKey(watchId)) {
                throw new WatchAlreadyExistsException(watchId);
            }
            watches.put(watchId, watch);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Optional<StoredWatch> retrieve(String watchId) throws StorageException {
        if (watchId == null || watchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Watch ID cannot be null or empty");
        }
        
        lock.readLock().lock();
        try {
            return Optional.ofNullable(watches.get(watchId));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void update(StoredWatch watch) throws StorageException {
        if (watch == null) {
            throw new IllegalArgumentException("Watch cannot be null");
        }
        
        String watchId = watch.getWatchId();
        if (watchId == null || watchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Watch ID cannot be null or empty");
        }
        
        lock.writeLock().lock();
        try {
            if (!watches.containsKey(watchId)) {
                throw new WatchNotFoundException(watchId);
            }
            watches.put(watchId, watch);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean remove(String watchId) throws StorageException {
        if (watchId == null || watchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Watch ID cannot be null or empty");
        }
        
        lock.writeLock().lock();
        try {
            return watches.remove(watchId) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public List<StoredWatch> findByStatus(WatchStatus status) throws StorageException {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        
        lock.readLock().lock();
        try {
            return watches.values().stream()
                .filter(watch -> status.equals(watch.getStatus()))
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<StoredWatch> findActive() throws StorageException {
        lock.readLock().lock();
        try {
            return watches.values().stream()
                .filter(watch -> ACTIVE_STATUSES.contains(watch.getStatus()))
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void storeBatch(List<StoredWatch> watchesToStore) throws StorageException {
        if (watchesToStore == null) {
            throw new IllegalArgumentException("Watches list cannot be null");
        }
        
        if (watchesToStore.isEmpty()) {
            return;
        }
        
        // Validate all watches first
        for (StoredWatch watch : watchesToStore) {
            if (watch == null) {
                throw new IllegalArgumentException("Watch in batch cannot be null");
            }
            if (watch.getWatchId() == null || watch.getWatchId().trim().isEmpty()) {
                throw new IllegalArgumentException("Watch ID cannot be null or empty");
            }
        }
        
        lock.writeLock().lock();
        try {
            // Check for existing watches
            for (StoredWatch watch : watchesToStore) {
                if (watches.containsKey(watch.getWatchId())) {
                    throw new WatchAlreadyExistsException(watch.getWatchId());
                }
            }
            
            // Store all watches
            for (StoredWatch watch : watchesToStore) {
                watches.put(watch.getWatchId(), watch);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void updateBatch(List<StoredWatch> watchesToUpdate) throws StorageException {
        if (watchesToUpdate == null) {
            throw new IllegalArgumentException("Watches list cannot be null");
        }
        
        if (watchesToUpdate.isEmpty()) {
            return;
        }
        
        // Validate all watches first
        for (StoredWatch watch : watchesToUpdate) {
            if (watch == null) {
                throw new IllegalArgumentException("Watch in batch cannot be null");
            }
            if (watch.getWatchId() == null || watch.getWatchId().trim().isEmpty()) {
                throw new IllegalArgumentException("Watch ID cannot be null or empty");
            }
        }
        
        lock.writeLock().lock();
        try {
            // Check that all watches exist
            for (StoredWatch watch : watchesToUpdate) {
                if (!watches.containsKey(watch.getWatchId())) {
                    throw new WatchNotFoundException(watch.getWatchId());
                }
            }
            
            // Update all watches
            for (StoredWatch watch : watchesToUpdate) {
                watches.put(watch.getWatchId(), watch);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get the current number of stored watches.
     * Useful for monitoring and testing.
     * 
     * @return the number of stored watches
     */
    public int size() {
        lock.readLock().lock();
        try {
            return watches.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clear all stored watches.
     * Useful for testing and cleanup.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            watches.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Perform cleanup operations.
     * Currently a no-op but can be extended for automatic cleanup logic.
     */
    public void cleanup() {
        // For future use - could implement automatic cleanup of old watches
        // based on timestamp, status, etc.
    }
    
    /**
     * Get storage statistics for monitoring.
     * 
     * @return map containing storage statistics
     */
    public Map<String, Object> getStats() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalWatches", watches.size());
            
            // Count by status
            Map<WatchStatus, Long> statusCounts = watches.values().stream()
                .collect(Collectors.groupingBy(
                    StoredWatch::getStatus,
                    Collectors.counting()
                ));
            
            stats.put("statusCounts", statusCounts);
            stats.put("activeWatches", statusCounts.entrySet().stream()
                .filter(entry -> ACTIVE_STATUSES.contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum());
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
}