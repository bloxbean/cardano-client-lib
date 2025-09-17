/**
 * Structured exception hierarchy for RocksDB storage operations.
 * 
 * <p>This package provides a comprehensive, structured exception hierarchy
 * that enables precise error handling and debugging for RocksDB storage
 * operations. Instead of generic RuntimeExceptions, the system uses
 * specific exception types that provide rich context information.</p>
 * 
 * <p><b>Exception Hierarchy:</b></p>
 * <ul>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbStorageException} - 
 *       Base class for all storage-related errors</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbOperationException} - 
 *       Specific operation failures (GET, PUT, DELETE, etc.)</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbBatchException} - 
 *       Batch operation failures with batch context</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbConfigurationException} - 
 *       Configuration and initialization errors</li>
 * </ul>
 * 
 * <p><b>Key Benefits:</b></p>
 * <ul>
 *   <li>Rich context information for debugging and monitoring</li>
 *   <li>Targeted exception handling based on error type</li>
 *   <li>Structured diagnostic information for troubleshooting</li>
 *   <li>Recovery suggestions for common error scenarios</li>
 *   <li>Integration with RocksDB native exceptions as root causes</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try {
 *     nodeStore.put(nodeKey, nodeData);
 * } catch (RocksDbOperationException e) {
 *     if (e.isOperation("PUT") && e.isKeyType(NodeHashKey.class)) {
 *         // Handle node storage failure specifically
 *         logger.error("Failed to store node: " + e.getKey(), e);
 *         throw new ServiceException("Node storage unavailable", e);
 *     }
 * } catch (RocksDbBatchException e) {
 *     if (e.isLargeBatch()) {
 *         // Handle large batch failure - maybe split into smaller batches
 *         logger.warn("Large batch failed, retrying with smaller chunks", e);
 *         retryWithSmallerBatches();
 *     }
 * } catch (RocksDbStorageException e) {
 *     // Handle any other storage error
 *     logger.error("Unexpected storage error", e);
 *     throw new ServiceException("Storage system unavailable", e);
 * }
 * }</pre>
 * 
 * <p><b>Error Context:</b> All exceptions in this hierarchy provide rich
 * context information through methods like {@code getDiagnosticInfo()},
 * which includes detailed error descriptions, recovery suggestions, and
 * debugging information.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
package com.bloxbean.cardano.statetrees.rocksdb.exceptions;