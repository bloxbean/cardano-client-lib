package com.bloxbean.cardano.statetrees.rocksdb.exceptions;

import com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RocksDbKey;

import java.util.Arrays;
import java.util.Objects;

/**
 * Exception thrown when a specific RocksDB operation fails.
 *
 * <p>This exception provides detailed context about failed database operations,
 * including the operation type, the key involved, and the underlying cause.
 * This information is valuable for debugging, monitoring, and error recovery.</p>
 *
 * <p><b>Operation Types:</b></p>
 * <ul>
 *   <li>GET - Failed to retrieve a value</li>
 *   <li>PUT - Failed to store a value</li>
 *   <li>DELETE - Failed to remove a key</li>
 *   <li>ITERATOR - Failed during iteration operations</li>
 *   <li>EXIST - Failed to check key existence</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try {
 *     byte[] value = db.get(cf, key.toBytes());
 * } catch (RocksDBException e) {
 *     throw new RocksDbOperationException("GET", key, e);
 * }
 *
 * // Handling the exception
 * try {
 *     nodeStore.get(nodeKey);
 * } catch (RocksDbOperationException e) {
 *     if ("GET".equals(e.getOperation()) && e.getKey() instanceof NodeHashKey) {
 *         // Handle node retrieval failure specifically
 *         logger.warn("Node not found: " + e.getKey());
 *         return null;
 *     }
 *     throw e; // Re-throw if can't handle
 * }
 * }</pre>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class RocksDbOperationException extends RocksDbStorageException {

    /**
     * The operation that failed (e.g., "GET", "PUT", "DELETE").
     */
    private final String operation;

    /**
     * The key involved in the failed operation.
     */
    private final RocksDbKey key;

    /**
     * Creates a new RocksDbOperationException for a failed operation.
     *
     * @param operation the operation that failed (must not be null)
     * @param key       the key involved in the operation (may be null for some operations)
     * @param cause     the underlying cause of the failure
     * @throws IllegalArgumentException if operation is null or empty
     */
    public RocksDbOperationException(String operation, RocksDbKey key, Throwable cause) {
        super(buildMessage(operation, key), cause);

        this.operation = Objects.requireNonNull(operation, "Operation cannot be null");
        if (operation.trim().isEmpty()) {
            throw new IllegalArgumentException("Operation cannot be empty");
        }

        this.key = key; // Key may be null for some operations
    }

    /**
     * Creates a new RocksDbOperationException with a custom message.
     *
     * @param operation the operation that failed
     * @param key       the key involved in the operation
     * @param message   a custom error message
     * @param cause     the underlying cause of the failure
     */
    public RocksDbOperationException(String operation, RocksDbKey key, String message, Throwable cause) {
        super(message, cause);
        this.operation = Objects.requireNonNull(operation, "Operation cannot be null");
        this.key = key;
    }

    /**
     * Returns the operation that failed.
     *
     * @return the operation name (e.g., "GET", "PUT", "DELETE")
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the key involved in the failed operation.
     *
     * @return the key, or null if no specific key was involved
     */
    public RocksDbKey getKey() {
        return key;
    }

    /**
     * Checks if this exception is for a specific operation type.
     *
     * @param operationType the operation type to check
     * @return true if the operation matches, false otherwise
     */
    public boolean isOperation(String operationType) {
        return operation.equalsIgnoreCase(operationType);
    }

    /**
     * Checks if this exception involves a specific key type.
     *
     * @param keyType the key type class to check
     * @return true if the key is of the specified type, false otherwise
     */
    public boolean isKeyType(Class<? extends RocksDbKey> keyType) {
        return key != null && keyType.isInstance(key);
    }

    /**
     * Returns detailed diagnostic information about this operation failure.
     *
     * <p>This method provides comprehensive debugging information including
     * the operation type, key details, and failure context.</p>
     *
     * @return detailed diagnostic information
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("RocksDB Operation Failure:\n");
        info.append("  Operation: ").append(operation).append("\n");

        if (key != null) {
            info.append("  Key Type: ").append(key.getClass().getSimpleName()).append("\n");
            info.append("  Key Value: ").append(key.toString()).append("\n");
            info.append("  Key Length: ").append(key.length()).append(" bytes\n");
        } else {
            info.append("  Key: <none>\n");
        }

        Throwable cause = getCause();
        if (cause != null) {
            info.append("  Cause: ").append(cause.getClass().getSimpleName())
                    .append(" - ").append(cause.getMessage()).append("\n");
        }

        return info.toString();
    }

    /**
     * Builds a descriptive error message for the operation failure.
     *
     * @param operation the failed operation
     * @param key       the key involved
     * @return a formatted error message
     */
    private static String buildMessage(String operation, RocksDbKey key) {
        StringBuilder message = new StringBuilder();
        message.append("RocksDB ").append(operation).append(" operation failed");

        if (key != null) {
            message.append(" for key type ").append(key.getClass().getSimpleName());

            // Include key details for debugging
            byte[] keyBytes = key.toBytes();
            if (keyBytes.length <= 8) {
                // For short keys, include the full hex representation
                message.append(" [").append(bytesToHex(keyBytes)).append("]");
            } else {
                // For long keys, include just the first few bytes
                byte[] prefix = Arrays.copyOf(keyBytes, 4);
                message.append(" [").append(bytesToHex(prefix)).append("...]");
            }
        }

        return message.toString();
    }

    /**
     * Converts bytes to hexadecimal string representation.
     *
     * @param bytes the bytes to convert
     * @return hexadecimal string
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
