package com.bloxbean.cardano.client.watcher.storage;

/**
 * Exception thrown when a requested watch is not found in storage.
 */
public class WatchNotFoundException extends StorageException {
    
    private final String watchId;
    
    public WatchNotFoundException(String watchId) {
        super("Watch not found: " + watchId);
        this.watchId = watchId;
    }
    
    public WatchNotFoundException(String watchId, String message) {
        super(message);
        this.watchId = watchId;
    }
    
    public String getWatchId() {
        return watchId;
    }
}