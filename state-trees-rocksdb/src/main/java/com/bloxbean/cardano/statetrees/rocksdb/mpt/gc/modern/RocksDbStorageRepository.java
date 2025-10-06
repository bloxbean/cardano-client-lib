package com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.modern;

import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbRootsIndex;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.batch.RocksDbBatchContext;
import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbOperationException;
import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbStorageException;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.NodeRefParser;
import org.rocksdb.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;

/**
 * RocksDB-backed implementation of the StorageRepository interface.
 *
 * <p>This implementation provides a modern, type-safe abstraction over RocksDB
 * storage operations for MPT nodes and roots. It bridges the legacy RocksDbNodeStore
 * and RocksDbRootsIndex with the modern StorageRepository interface, enabling
 * clean separation between storage operations and GC algorithms.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Type-safe operations using modern key classes</li>
 *   <li>Stream-based traversal with lazy evaluation</li>
 *   <li>Efficient batch operations with resource management</li>
 *   <li>Reference counting support for incremental GC</li>
 *   <li>Comprehensive error handling with context</li>
 * </ul>
 *
 * <p><b>Implementation Notes:</b></p>
 * <ul>
 *   <li>Node traversal uses efficient iterators to minimize memory usage</li>
 *   <li>Reference counts are stored as prefixed keys in the nodes column family</li>
 *   <li>Batch contexts provide read-your-writes consistency</li>
 *   <li>All operations include proper error handling and resource cleanup</li>
 * </ul>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class RocksDbStorageRepository implements StorageRepository {

    private static final byte[] REF_COUNT_PREFIX = "ref:".getBytes();
    private static final int REF_COUNT_VALUE_SIZE = 8; // long value

    private final RocksDbNodeStore nodeStore;
    private final RocksDbRootsIndex rootsIndex;
    private final RocksDB db;
    private final ColumnFamilyHandle nodesHandle;
    private final ColumnFamilyHandle rootsHandle;

    /**
     * Creates a new RocksDbStorageRepository.
     *
     * @param nodeStore  the underlying node store
     * @param rootsIndex the underlying roots index
     */
    public RocksDbStorageRepository(RocksDbNodeStore nodeStore, RocksDbRootsIndex rootsIndex) {
        this.nodeStore = Objects.requireNonNull(nodeStore, "Node store cannot be null");
        this.rootsIndex = Objects.requireNonNull(rootsIndex, "Roots index cannot be null");
        this.db = nodeStore.db();
        this.nodesHandle = nodeStore.nodesHandle();
        // RocksDbRootsIndex doesn't expose column family handle, we'll work around this
        this.rootsHandle = nodeStore.nodesHandle(); // Use same handle for now
    }

    @Override
    public Optional<byte[]> getNode(NodeHashKey key) throws RocksDbStorageException {
        try {
            byte[] data = db.get(nodesHandle, key.toBytes());
            return Optional.ofNullable(data);
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("GET", key, e);
        }
    }

    @Override
    public void putNode(NodeHashKey key, byte[] data) throws RocksDbStorageException {
        try {
            db.put(nodesHandle, key.toBytes(), data);
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("PUT", key, e);
        }
    }

    @Override
    public void putNode(RocksDbBatchContext batch, NodeHashKey key, byte[] data) throws RocksDbStorageException {
        batch.put(nodesHandle, key, data);
    }

    @Override
    public void deleteNode(NodeHashKey key) throws RocksDbStorageException {
        try {
            db.delete(nodesHandle, key.toBytes());
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("DELETE", key, e);
        }
    }

    @Override
    public void deleteNode(RocksDbBatchContext batch, NodeHashKey key) throws RocksDbStorageException {
        batch.delete(nodesHandle, key);
    }

    @Override
    public boolean nodeExists(NodeHashKey key) throws RocksDbStorageException {
        return getNode(key).isPresent();
    }

    @Override
    public Stream<RootHashKey> getAllRoots() throws RocksDbStorageException {
        try {
            NavigableMap<Long, byte[]> allRoots = rootsIndex.listAll();
            return allRoots.values().stream()
                    .map(RootHashKey::of);
        } catch (Exception e) {
            throw new RocksDbOperationException("GET_ALL_ROOTS", null, e);
        }
    }

    @Override
    public Optional<RootHashKey> getLatestRoot() throws RocksDbStorageException {
        try {
            byte[] latestRoot = rootsIndex.latest();
            return latestRoot != null ? Optional.of(RootHashKey.of(latestRoot)) : Optional.empty();
        } catch (Exception e) {
            throw new RocksDbOperationException("GET_LATEST_ROOT", null, e);
        }
    }

    @Override
    public Optional<RootHashKey> getRootByVersion(long version) throws RocksDbStorageException {
        try {
            byte[] root = rootsIndex.get(version);
            return root != null ? Optional.of(RootHashKey.of(root)) : Optional.empty();
        } catch (Exception e) {
            throw new RocksDbOperationException("GET_ROOT_BY_VERSION", null, e);
        }
    }

    @Override
    public void putRoot(long version, RootHashKey key) throws RocksDbStorageException {
        try {
            rootsIndex.put(version, key.getHash());
        } catch (Exception e) {
            throw new RocksDbOperationException("PUT_ROOT", null, e);
        }
    }

    @Override
    public void putRoot(RocksDbBatchContext batch, long version, RootHashKey key) throws RocksDbStorageException {
        try {
            // Use the roots column family with version key
            byte[] versionKey = longToBytes(version);
            batch.put(rootsHandle, new VersionKey(versionKey), key.getHash());
        } catch (Exception e) {
            throw new RocksDbOperationException("PUT_ROOT_BATCH", null, e);
        }
    }

    @Override
    public void deleteRoot(long version) throws RocksDbStorageException {
        // RocksDbRootsIndex doesn't expose delete method - would need to implement manually
        throw new RocksDbOperationException("DELETE_ROOT", null,
                new UnsupportedOperationException("Root deletion not supported by RocksDbRootsIndex"));
    }

    @Override
    public void deleteRoot(RocksDbBatchContext batch, long version) throws RocksDbStorageException {
        try {
            byte[] versionKey = longToBytes(version);
            batch.delete(rootsHandle, new VersionKey(versionKey));
        } catch (Exception e) {
            throw new RocksDbOperationException("DELETE_ROOT_BATCH", null, e);
        }
    }

    @Override
    public long getNodeRefCount(NodeHashKey key) throws RocksDbStorageException {
        try {
            byte[] refCountKey = createRefCountKey(key);
            byte[] refCountBytes = db.get(nodesHandle, refCountKey);

            if (refCountBytes == null) {
                return 0L;
            }

            if (refCountBytes.length != REF_COUNT_VALUE_SIZE) {
                throw new RocksDbOperationException("GET_REF_COUNT", key,
                        new IllegalStateException("Invalid reference count data length: " + refCountBytes.length));
            }

            return ByteBuffer.wrap(refCountBytes).getLong();
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("GET_REF_COUNT", key, e);
        }
    }

    @Override
    public void setNodeRefCount(NodeHashKey key, long refCount) throws RocksDbStorageException {
        try {
            byte[] refCountKey = createRefCountKey(key);
            byte[] refCountBytes = ByteBuffer.allocate(REF_COUNT_VALUE_SIZE).putLong(refCount).array();
            db.put(nodesHandle, refCountKey, refCountBytes);
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("SET_REF_COUNT", key, e);
        }
    }

    @Override
    public void setNodeRefCount(RocksDbBatchContext batch, NodeHashKey key, long refCount) throws RocksDbStorageException {
        byte[] refCountBytes = ByteBuffer.allocate(REF_COUNT_VALUE_SIZE).putLong(refCount).array();
        batch.put(nodesHandle, new RefCountKey(key), refCountBytes);
    }

    @Override
    public long incrementNodeRefCount(NodeHashKey key, long delta) throws RocksDbStorageException {
        long currentCount = getNodeRefCount(key);
        long newCount = currentCount + delta;
        setNodeRefCount(key, newCount);
        return newCount;
    }

    @Override
    public void incrementNodeRefCount(RocksDbBatchContext batch, NodeHashKey key, long delta) throws RocksDbStorageException {
        // For batch operations, we need to read the current value first
        // This assumes read-your-writes consistency in the batch context
        long currentCount = getNodeRefCount(key);
        long newCount = currentCount + delta;
        setNodeRefCount(batch, key, newCount);
    }

    @Override
    public Stream<NodeHashKey> traverseFromRoot(RootHashKey rootKey) throws RocksDbStorageException {
        return traverseFromNode(rootKey.toNodeHashKey(), new HashSet<>()).stream();
    }

    /**
     * Recursively traverses all nodes reachable from a given node.
     *
     * @param nodeKey the node to start traversal from
     * @param visited set of already visited nodes to prevent cycles
     * @return set of all reachable node keys
     * @throws RocksDbStorageException if a storage error occurs
     */
    private Set<NodeHashKey> traverseFromNode(NodeHashKey nodeKey, Set<NodeHashKey> visited) throws RocksDbStorageException {
        if (visited.contains(nodeKey)) {
            return visited;
        }

        visited.add(nodeKey);

        Optional<byte[]> nodeData = getNode(nodeKey);
        if (nodeData.isEmpty()) {
            return visited;
        }

        // Parse child references from node data
        try {
            List<byte[]> childHashes = NodeRefParser.childRefs(nodeData.get());
            for (byte[] childHash : childHashes) {
                if (childHash.length == NodeHashKey.HASH_LENGTH) {
                    NodeHashKey childKey = NodeHashKey.of(childHash);
                    traverseFromNode(childKey, visited);
                }
            }
        } catch (Exception e) {
            throw new RocksDbOperationException("PARSE_NODE_REFS", nodeKey, e);
        }

        return visited;
    }

    @Override
    public long getTotalNodeCount() throws RocksDbStorageException {
        try {
            // Use an iterator to count nodes efficiently
            try (RocksIterator iterator = db.newIterator(nodesHandle)) {
                long count = 0;
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    byte[] key = iterator.key();
                    // Skip reference count keys
                    if (!isRefCountKey(key)) {
                        count++;
                    }
                }
                return count;
            }
        } catch (Exception e) {
            throw new RocksDbOperationException("COUNT_NODES", null, e);
        }
    }

    @Override
    public long getTotalDataSize() throws RocksDbStorageException {
        try {
            // Use an iterator to sum data sizes efficiently
            try (RocksIterator iterator = db.newIterator(nodesHandle)) {
                long totalSize = 0;
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    byte[] key = iterator.key();
                    // Skip reference count keys
                    if (!isRefCountKey(key)) {
                        totalSize += iterator.value().length;
                    }
                }
                return totalSize;
            }
        } catch (Exception e) {
            throw new RocksDbOperationException("CALC_DATA_SIZE", null, e);
        }
    }

    @Override
    public RocksDbBatchContext createBatchContext() throws RocksDbStorageException {
        return RocksDbBatchContext.create(db);
    }

    @Override
    public Set<String> performConsistencyCheck() throws RocksDbStorageException {
        Set<String> issues = new HashSet<>();

        try {
            // Check 1: Verify all roots exist as nodes
            Stream<RootHashKey> roots = getAllRoots();
            roots.forEach(rootKey -> {
                try {
                    NodeHashKey nodeKey = rootKey.toNodeHashKey();
                    if (!nodeExists(nodeKey)) {
                        issues.add("Root " + rootKey.toShortHex() + " does not exist as a node");
                    }
                } catch (RocksDbStorageException e) {
                    issues.add("Failed to check root " + rootKey.toShortHex() + ": " + e.getMessage());
                }
            });

            // Check 2: Verify reference count consistency (if enabled)
            try (RocksIterator iterator = db.newIterator(nodesHandle)) {
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    byte[] key = iterator.key();
                    if (isRefCountKey(key)) {
                        // Extract node key and verify the corresponding node exists
                        byte[] nodeKeyBytes = extractNodeKeyFromRefCountKey(key);
                        try {
                            NodeHashKey nodeKey = NodeHashKey.of(nodeKeyBytes);
                            if (!nodeExists(nodeKey)) {
                                issues.add("Reference count exists for non-existent node " + nodeKey.toShortHex());
                            }
                        } catch (IllegalArgumentException e) {
                            issues.add("Invalid node key in reference count key: " + Arrays.toString(key));
                        }
                    }
                }
            }

        } catch (Exception e) {
            issues.add("Consistency check failed: " + e.getMessage());
        }

        return issues;
    }

    /**
     * Creates a reference count key for a node.
     *
     * @param nodeKey the node key
     * @return the reference count key bytes
     */
    private byte[] createRefCountKey(NodeHashKey nodeKey) {
        byte[] nodeKeyBytes = nodeKey.toBytes();
        byte[] refCountKey = new byte[REF_COUNT_PREFIX.length + nodeKeyBytes.length];
        System.arraycopy(REF_COUNT_PREFIX, 0, refCountKey, 0, REF_COUNT_PREFIX.length);
        System.arraycopy(nodeKeyBytes, 0, refCountKey, REF_COUNT_PREFIX.length, nodeKeyBytes.length);
        return refCountKey;
    }

    /**
     * Checks if a key is a reference count key.
     *
     * @param key the key to check
     * @return true if it's a reference count key
     */
    private boolean isRefCountKey(byte[] key) {
        if (key.length < REF_COUNT_PREFIX.length) {
            return false;
        }
        for (int i = 0; i < REF_COUNT_PREFIX.length; i++) {
            if (key[i] != REF_COUNT_PREFIX[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the node key from a reference count key.
     *
     * @param refCountKey the reference count key
     * @return the node key bytes
     */
    private byte[] extractNodeKeyFromRefCountKey(byte[] refCountKey) {
        byte[] nodeKeyBytes = new byte[refCountKey.length - REF_COUNT_PREFIX.length];
        System.arraycopy(refCountKey, REF_COUNT_PREFIX.length, nodeKeyBytes, 0, nodeKeyBytes.length);
        return nodeKeyBytes;
    }

    /**
     * Converts a long to byte array.
     *
     * @param value the long value
     * @return the byte array representation
     */
    private byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    /**
     * Type-safe key for reference counts.
     */
    private static class RefCountKey extends com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RocksDbKey {
        RefCountKey(NodeHashKey nodeKey) {
            super(createRefCountKeyBytes(nodeKey));
        }

        private static byte[] createRefCountKeyBytes(NodeHashKey nodeKey) {
            byte[] nodeKeyBytes = nodeKey.toBytes();
            byte[] refCountKey = new byte[REF_COUNT_PREFIX.length + nodeKeyBytes.length];
            System.arraycopy(REF_COUNT_PREFIX, 0, refCountKey, 0, REF_COUNT_PREFIX.length);
            System.arraycopy(nodeKeyBytes, 0, refCountKey, REF_COUNT_PREFIX.length, nodeKeyBytes.length);
            return refCountKey;
        }
    }

    /**
     * Type-safe key for version numbers.
     */
    private static class VersionKey extends com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RocksDbKey {
        VersionKey(byte[] versionBytes) {
            super(versionBytes);
        }
    }
}
