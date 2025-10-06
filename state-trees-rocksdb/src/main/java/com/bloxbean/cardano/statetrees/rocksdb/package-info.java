/**
 * RocksDB-backed storage implementations for Merkle Patricia Trie state trees.
 *
 * <p>This package provides high-performance, persistent storage for Merkle Patricia Tries
 * using RocksDB as the underlying storage engine. It includes implementations of both
 * the core storage interfaces and advanced features like garbage collection.</p>
 *
 * <p><b>Core Components:</b></p>
 * <ul>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbNodeStore} - Persistent storage for MPT nodes</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbRootsIndex} - Versioned storage for trie root hashes</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbStateTrees} - Unified database manager</li>
 * </ul>
 *
 * <p><b>Advanced Features:</b></p>
 * <ul>
 *   <li>Atomic batch operations with read-your-writes consistency</li>
 *   <li>ThreadLocal operation contexts for performance</li>
 *   <li>Automatic column family management</li>
 *   <li>Comprehensive garbage collection framework (see {@code gc} package)</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * // Option 1: Use unified manager (recommended)
 * try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("/path/to/db")) {
 *     MerklePatriciaTrie trie = new MerklePatriciaTrie(
 *         stateTrees.nodeStore(),
 *         Blake2b256::digest
 *     );
 *
 *     // Perform operations
 *     trie.put("key".getBytes(), "value".getBytes());
 *
 *     // Store versioned root
 *     stateTrees.rootsIndex().put(blockHeight, trie.getRootHash());
 * }
 *
 * // Option 2: Use components separately
 * try (RocksDbNodeStore nodeStore = new RocksDbNodeStore("/path/to/nodes");
 *      RocksDbRootsIndex rootsIndex = new RocksDbRootsIndex("/path/to/roots")) {
 *
 *     MerklePatriciaTrie trie = new MerklePatriciaTrie(nodeStore, Blake2b256::digest);
 *     // ... operations
 * }
 * }</pre>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Use batch operations for bulk updates to improve write performance</li>
 *   <li>Consider RocksDB tuning options for your specific workload</li>
 *   <li>Regular garbage collection helps maintain storage efficiency</li>
 *   <li>Monitor column family sizes and compaction metrics</li>
 * </ul>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
package com.bloxbean.cardano.statetrees.rocksdb;

