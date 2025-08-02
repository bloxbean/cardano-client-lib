package com.bloxbean.cardano.client.watcher.storage;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.util.List;
import java.util.Optional;

/**
 * Storage interface for persisting watch state.
 * 
 * Provides CRUD operations and query capabilities for watched transactions.
 * Implementations should be thread-safe and handle concurrent access appropriately.
 */
public interface WatcherStorage {
    
    /**
     * Store a new watch record.
     * 
     * @param watch the watch to store
     * @throws StorageException if the watch already exists or storage fails
     */
    void store(StoredWatch watch) throws StorageException;
    
    /**
     * Retrieve a watch by its ID.
     * 
     * @param watchId the watch identifier
     * @return optional containing the watch if found
     * @throws StorageException if storage access fails
     */
    Optional<StoredWatch> retrieve(String watchId) throws StorageException;
    
    /**
     * Update an existing watch record.
     * 
     * @param watch the updated watch
     * @throws StorageException if the watch doesn't exist or storage fails
     */
    void update(StoredWatch watch) throws StorageException;
    
    /**
     * Remove a watch record.
     * 
     * @param watchId the watch identifier
     * @return true if the watch was removed, false if it didn't exist
     * @throws StorageException if storage access fails
     */
    boolean remove(String watchId) throws StorageException;
    
    /**
     * Find all watches with the specified status.
     * 
     * @param status the status to filter by
     * @return list of watches with the given status (empty if none found)
     * @throws StorageException if storage access fails
     */
    List<StoredWatch> findByStatus(WatchStatus status) throws StorageException;
    
    /**
     * Find all active watches (BUILDING, SUBMITTED, PENDING, WATCHING, RETRYING, REBUILDING).
     * 
     * @return list of active watches (empty if none found)
     * @throws StorageException if storage access fails
     */
    List<StoredWatch> findActive() throws StorageException;
    
    /**
     * Store multiple watches in a single operation.
     * 
     * @param watches the watches to store
     * @throws StorageException if any watch already exists or storage fails
     */
    void storeBatch(List<StoredWatch> watches) throws StorageException;
    
    /**
     * Update multiple watches in a single operation.
     * 
     * @param watches the watches to update
     * @throws StorageException if any watch doesn't exist or storage fails
     */
    void updateBatch(List<StoredWatch> watches) throws StorageException;
}