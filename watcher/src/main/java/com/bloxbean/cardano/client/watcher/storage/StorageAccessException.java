package com.bloxbean.cardano.client.watcher.storage;

/**
 * Exception thrown when storage access fails due to I/O or connectivity issues.
 */
public class StorageAccessException extends StorageException {
    
    public StorageAccessException(String message) {
        super(message);
    }
    
    public StorageAccessException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public StorageAccessException(Throwable cause) {
        super(cause);
    }
}