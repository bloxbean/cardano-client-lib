package com.bloxbean.cardano.vds.rocksdb.exceptions;

/**
 * Exception thrown when a RocksDB batch operation fails.
 *
 * <p>This exception is specifically for failures that occur during batch
 * operations, such as WriteBatch commits or batch context management.
 * It provides additional context about the batch size and operation state
 * to help with debugging and recovery.</p>
 *
 * <p><b>Common Batch Failure Scenarios:</b></p>
 * <ul>
 *   <li>WriteBatch commit failure due to disk space or I/O errors</li>
 *   <li>Batch size exceeding limits or memory constraints</li>
 *   <li>Transaction conflicts in concurrent environments</li>
 *   <li>Batch context management errors</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try (WriteBatch batch = new WriteBatch()) {
 *     batch.put(cf, key1, value1);
 *     batch.put(cf, key2, value2);
 *     db.write(writeOptions, batch);
 * } catch (RocksDBException e) {
 *     throw new RocksDbBatchException("Failed to commit batch", batch.count(), e);
 * }
 *
 * // Handling batch exceptions
 * try {
 *     performBatchOperation();
 * } catch (RocksDbBatchException e) {
 *     if (e.getOperationCount() > 1000) {
 *         // Handle large batch failure - maybe split into smaller batches
 *         logger.warn("Large batch failed, splitting into smaller chunks: " + e.getOperationCount());
 *         retryWithSmallerBatches();
 *     } else {
 *         // Handle other batch failures
 *         throw new ServiceException("Batch operation failed", e);
 *     }
 * }
 * }</pre>
 *
 * @since 0.8.0
 */
public final class RocksDbBatchException extends RocksDbStorageException {

    /**
     * The number of operations in the failed batch.
     */
    private final int operationCount;

    /**
     * The estimated size of the batch in bytes (if available).
     */
    private final long batchSizeBytes;

    /**
     * Creates a new RocksDbBatchException with operation count.
     *
     * @param message        the detailed error message
     * @param operationCount the number of operations in the failed batch
     * @param cause          the underlying cause of the failure
     */
    public RocksDbBatchException(String message, int operationCount, Throwable cause) {
        super(enhanceMessage(message, operationCount, -1), cause);
        this.operationCount = Math.max(0, operationCount);
        this.batchSizeBytes = -1;
    }

    /**
     * Creates a new RocksDbBatchException with operation count and batch size.
     *
     * @param message        the detailed error message
     * @param operationCount the number of operations in the failed batch
     * @param batchSizeBytes the estimated size of the batch in bytes
     * @param cause          the underlying cause of the failure
     */
    public RocksDbBatchException(String message, int operationCount, long batchSizeBytes, Throwable cause) {
        super(enhanceMessage(message, operationCount, batchSizeBytes), cause);
        this.operationCount = Math.max(0, operationCount);
        this.batchSizeBytes = batchSizeBytes;
    }

    /**
     * Creates a new RocksDbBatchException for a simple message.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public RocksDbBatchException(String message, Throwable cause) {
        super(message, cause);
        this.operationCount = 0;
        this.batchSizeBytes = -1;
    }

    /**
     * Returns the number of operations in the failed batch.
     *
     * @return the operation count, or 0 if unknown
     */
    public int getOperationCount() {
        return operationCount;
    }

    /**
     * Returns the estimated size of the batch in bytes.
     *
     * @return the batch size in bytes, or -1 if unknown
     */
    public long getBatchSizeBytes() {
        return batchSizeBytes;
    }

    /**
     * Checks if this was a large batch operation.
     *
     * <p>A batch is considered "large" if it contains more than 100 operations
     * or is larger than 1MB in size.</p>
     *
     * @return true if this was a large batch, false otherwise
     */
    public boolean isLargeBatch() {
        return operationCount > 100 || batchSizeBytes > 1024 * 1024;
    }

    /**
     * Returns detailed diagnostic information about the batch failure.
     *
     * <p>This method provides comprehensive debugging information including
     * batch statistics, failure context, and potential recovery suggestions.</p>
     *
     * @return detailed diagnostic information
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("RocksDB Batch Failure:\n");
        info.append("  Operation Count: ").append(operationCount).append("\n");

        if (batchSizeBytes >= 0) {
            info.append("  Batch Size: ").append(formatBytes(batchSizeBytes)).append("\n");
        } else {
            info.append("  Batch Size: unknown\n");
        }

        info.append("  Large Batch: ").append(isLargeBatch()).append("\n");

        Throwable cause = getCause();
        if (cause != null) {
            info.append("  Cause: ").append(cause.getClass().getSimpleName())
                    .append(" - ").append(cause.getMessage()).append("\n");
        }

        // Provide recovery suggestions
        info.append("  Recovery Suggestions:\n");
        if (isLargeBatch()) {
            info.append("    - Consider splitting into smaller batches\n");
            info.append("    - Check available disk space and memory\n");
        }
        if (operationCount > 0) {
            info.append("    - Retry with exponential backoff\n");
            info.append("    - Check for transient errors in the cause\n");
        }

        return info.toString();
    }

    /**
     * Enhances the error message with batch-specific information.
     *
     * @param baseMessage    the base error message
     * @param operationCount the number of operations
     * @param batchSizeBytes the batch size in bytes
     * @return the enhanced message
     */
    private static String enhanceMessage(String baseMessage, int operationCount, long batchSizeBytes) {
        StringBuilder enhanced = new StringBuilder(baseMessage);

        if (operationCount > 0) {
            enhanced.append(" (operations: ").append(operationCount);

            if (batchSizeBytes >= 0) {
                enhanced.append(", size: ").append(formatBytes(batchSizeBytes));
            }

            enhanced.append(")");
        }

        return enhanced.toString();
    }

    /**
     * Formats a byte count into a human-readable string.
     *
     * @param bytes the byte count
     * @return formatted string like "1.5 MB"
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
