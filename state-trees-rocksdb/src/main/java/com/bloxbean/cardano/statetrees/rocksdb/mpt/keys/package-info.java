/**
 * Type-safe key system for RocksDB operations.
 *
 * <p>This package provides a comprehensive type-safe key system that prevents
 * key collision bugs and provides compile-time safety for RocksDB operations.
 * All keys extend the {@link com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RocksDbKey}
 * base class and provide specific validation and formatting logic.</p>
 *
 * <p><b>Key Types:</b></p>
 * <ul>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.NodeHashKey} - 32-byte node hash keys</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.VersionKey} - 8-byte version keys for roots</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.SpecialKey} - String-based metadata keys</li>
 *   <li>{@link com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RefcountKey} - Reference count keys for GC</li>
 * </ul>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li>Compile-time prevention of key type mixing bugs</li>
 *   <li>Self-documenting key structure and purpose</li>
 *   <li>Centralized key validation and encoding logic</li>
 *   <li>Easy to extend with new key types</li>
 *   <li>Consistent string representations for debugging</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create type-safe keys
 * NodeHashKey nodeKey = NodeHashKey.of(hashBytes);
 * VersionKey versionKey = VersionKey.of(12345L);
 * SpecialKey latestKey = SpecialKey.LATEST;
 * RefcountKey refKey = RefcountKey.forNode(nodeKey);
 *
 * // Type-safe operations - compiler prevents mixing key types
 * nodeStore.put(nodeKey, nodeData);      // ✅ Correct
 * rootsIndex.put(versionKey, rootHash);  // ✅ Correct
 * nodeStore.put(versionKey, nodeData);   // ❌ Compile error - prevents bugs!
 * }</pre>
 *
 * @since 0.8.0
 */
package com.bloxbean.cardano.statetrees.rocksdb.mpt.keys;
