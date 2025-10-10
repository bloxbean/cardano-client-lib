package com.bloxbean.cardano.statetrees.rocksdb.mpt.batch;

import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbStorageException;

/**
 * Functional interface for batch operations on RocksDB.
 *
 * <p>This interface enables a functional programming approach to batch operations,
 * providing better composability, error handling, and resource management compared
 * to imperative callback patterns. Operations are executed within a managed batch
 * context that ensures proper resource cleanup and transactional semantics.</p>
 *
 * <p><b>Key Benefits:</b></p>
 * <ul>
 *   <li>Functional composition of batch operations</li>
 *   <li>Type-safe batch context with automatic resource management</li>
 *   <li>Clear separation between operation logic and infrastructure</li>
 *   <li>Improved testability through pure functions</li>
 *   <li>Enhanced error handling and recovery patterns</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Simple batch operation
 * BatchOperation<Void> storeNodes = batch -> {
 *     batch.put(cfNodes, nodeKey1, nodeData1);
 *     batch.put(cfNodes, nodeKey2, nodeData2);
 *     return null;
 * };
 *
 * // Batch operation with return value
 * BatchOperation<byte[]> storeAndRetrieve = batch -> {
 *     batch.put(cfNodes, nodeKey, nodeData);
 *     return batch.get(cfNodes, nodeKey); // Read-your-writes
 * };
 *
 * // Complex operation with error handling
 * BatchOperation<Integer> processNodes = batch -> {
 *     int processed = 0;
 *     for (NodeData node : nodesToProcess) {
 *         try {
 *             batch.put(cfNodes, node.getKey(), node.getData());
 *             processed++;
 *         } catch (RocksDbOperationException e) {
 *             // Handle individual node errors
 *             logger.warn("Failed to process node: " + node.getKey(), e);
 *         }
 *     }
 *     return processed;
 * };
 * }</pre>
 *
 * <p><b>Error Handling:</b> Operations should throw RocksDbStorageException
 * or its subclasses for storage-related errors. The batch executor will
 * handle cleanup and provide appropriate context in case of failures.</p>
 *
 * @param <T> the return type of the batch operation
 * @since 0.8.0
 */
@FunctionalInterface
public interface BatchOperation<T> {

    /**
     * Executes the batch operation within the provided context.
     *
     * <p>Implementations should use the provided batch context to perform
     * database operations. The context provides read-your-writes consistency
     * and will be automatically committed after successful execution.</p>
     *
     * <p><b>Important:</b> Implementations should NOT call {@code batch.commit()}
     * themselves. The batch executor handles commit and cleanup automatically.</p>
     *
     * @param batch the batch context for database operations
     * @return the result of the operation (may be null)
     * @throws RocksDbStorageException if a storage error occurs
     * @throws Exception               if any other error occurs during execution
     */
    T execute(RocksDbBatchContext batch) throws RocksDbStorageException, Exception;
}
