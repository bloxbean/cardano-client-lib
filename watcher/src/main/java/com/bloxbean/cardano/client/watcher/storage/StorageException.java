package com.bloxbean.cardano.client.watcher.storage;

/**
 * Base exception for storage-related errors.
 * 
 * All storage implementations should throw this exception or its subclasses
 * for any storage-related failures.
 */
public class StorageException extends RuntimeException {
    
    public StorageException(String message) {
        super(message);
    }
    
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public StorageException(Throwable cause) {
        super(cause);
    }
}