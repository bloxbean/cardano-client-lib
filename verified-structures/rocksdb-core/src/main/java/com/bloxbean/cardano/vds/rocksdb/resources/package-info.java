/**
 * Modern resource management utilities for RocksDB operations.
 *
 * <p>This package provides safe, RAII-based resource management for RocksDB
 * components to prevent resource leaks and ensure proper cleanup even in
 * the presence of exceptions. The utilities follow modern Java best practices
 * and eliminate the error-prone manual resource tracking patterns.</p>
 *
 * <p><b>Core Components:</b></p>
 * <ul>
 *   <li>{@link com.bloxbean.cardano.vds.rocksdb.resources.RocksDbResources} -
 *       Automatic resource cleanup with LIFO ordering</li>
 *   <li>{@link com.bloxbean.cardano.vds.rocksdb.resources.RocksDbInitializer} -
 *       Safe database initialization with proper error handling</li>
 * </ul>
 *
 * <p><b>Key Benefits:</b></p>
 * <ul>
 *   <li>Eliminates resource leaks through automatic cleanup</li>
 *   <li>Provides comprehensive error reporting for cleanup failures</li>
 *   <li>Follows RAII (Resource Acquisition Is Initialization) principles</li>
 *   <li>Thread-safe resource registration and management</li>
 *   <li>Proper exception handling with aggregated error reporting</li>
 * </ul>
 *
 * <p><b>Usage Pattern:</b></p>
 * <pre>{@code
 * // Safe initialization with automatic cleanup
 * try (RocksDbInitializer.Result result = RocksDbInitializer.builder(dbPath)
 *         .withRequiredColumnFamily("nodes")
 *         .withRequiredColumnFamily("roots")
 *         .initialize()) {
 *
 *     RocksDB db = result.getDatabase();
 *     ColumnFamilyHandle nodes = result.getColumnFamily("nodes");
 *
 *     // Use resources safely...
 *
 * } // All resources automatically cleaned up
 * }</pre>
 *
 * @since 0.8.0
 */
package com.bloxbean.cardano.vds.rocksdb.resources;
