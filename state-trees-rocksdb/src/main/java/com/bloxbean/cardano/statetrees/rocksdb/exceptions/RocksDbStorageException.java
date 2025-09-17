package com.bloxbean.cardano.statetrees.rocksdb.exceptions;

/**
 * Base exception for all RocksDB storage-related errors.
 * 
 * <p>This is the root exception type for all storage-related failures in the
 * RocksDB persistence layer. It provides a structured exception hierarchy that
 * allows callers to handle different types of storage failures appropriately.</p>
 * 
 * <p><b>Exception Hierarchy:</b></p>
 * <ul>
 *   <li>{@link RocksDbStorageException} - Base for all storage errors</li>
 *   <li>{@link RocksDbOperationException} - Specific operation failures</li>
 *   <li>{@link RocksDbBatchException} - Batch operation failures</li>
 *   <li>{@link RocksDbConfigurationException} - Configuration errors</li>
 * </ul>
 * 
 * <p><b>Design Principles:</b></p>
 * <ul>
 *   <li>Checked exceptions for recoverable errors that callers should handle</li>
 *   <li>Rich context information for debugging and monitoring</li>
 *   <li>Structured hierarchy for targeted exception handling</li>
 *   <li>Integration with RocksDB native exceptions as root causes</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try {
 *     nodeStore.put(key, value);
 * } catch (RocksDbOperationException e) {
 *     // Handle specific operation failure
 *     logger.error("Failed to store node: " + e.getOperation(), e);
 *     throw new ServiceException("Storage unavailable", e);
 * } catch (RocksDbStorageException e) {
 *     // Handle any other storage error
 *     logger.error("Storage error: " + e.getMessage(), e);
 *     throw new ServiceException("Unexpected storage error", e);
 * }
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public abstract class RocksDbStorageException extends Exception {
    
    /**
     * Creates a new RocksDbStorageException with the specified message.
     * 
     * @param message the detailed error message
     */
    protected RocksDbStorageException(String message) {
        super(message);
    }
    
    /**
     * Creates a new RocksDbStorageException with the specified message and cause.
     * 
     * @param message the detailed error message
     * @param cause the underlying cause (typically a RocksDBException)
     */
    protected RocksDbStorageException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Returns a detailed description of this storage exception.
     * 
     * <p>This method provides a standardized format for describing storage
     * exceptions that includes the exception type, message, and cause
     * information for debugging purposes.</p>
     * 
     * @return a detailed description of this exception
     */
    public String getDetailedDescription() {
        StringBuilder description = new StringBuilder();
        description.append(getClass().getSimpleName()).append(": ").append(getMessage());
        
        Throwable cause = getCause();
        if (cause != null) {
            description.append(" (caused by ").append(cause.getClass().getSimpleName())
                      .append(": ").append(cause.getMessage()).append(")");
        }
        
        return description.toString();
    }
    
    /**
     * Checks if this exception was caused by a RocksDB-specific error.
     * 
     * @return true if the root cause is a RocksDBException, false otherwise
     */
    public boolean isRocksDbError() {
        Throwable cause = getCause();
        while (cause != null) {
            if (cause.getClass().getSimpleName().equals("RocksDBException")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}