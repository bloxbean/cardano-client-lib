package com.bloxbean.cardano.client.watcher.storage;

/**
 * Exception thrown when attempting to store a watch that already exists.
 */
public class WatchAlreadyExistsException extends StorageException {
    
    private final String watchId;
    
    public WatchAlreadyExistsException(String watchId) {
        super("Watch already exists: " + watchId);
        this.watchId = watchId;
    }
    
    public WatchAlreadyExistsException(String watchId, String message) {
        super(message);
        this.watchId = watchId;
    }
    
    public String getWatchId() {
        return watchId;
    }
}