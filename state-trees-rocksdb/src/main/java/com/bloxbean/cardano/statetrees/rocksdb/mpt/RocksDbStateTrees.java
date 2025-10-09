package com.bloxbean.cardano.statetrees.rocksdb.mpt;

import com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.statetrees.rocksdb.namespace.KeyPrefixer;
import com.bloxbean.cardano.statetrees.rocksdb.namespace.NamespaceOptions;
import org.rocksdb.*;

import java.io.File;

/**
 * Unified RocksDB manager providing both NodeStore and RootsIndex from a single database.
 *
 * <p>This convenience wrapper manages a single RocksDB instance with multiple column families,
 * providing a complete storage solution for Merkle Patricia Tries with the following benefits:</p>
 * <ul>
 *   <li>Single database file reduces resource overhead</li>
 *   <li>Atomic operations across both nodes and roots</li>
 *   <li>Simplified configuration and lifecycle management</li>
 *   <li>Automatic column family creation and management</li>
 * </ul>
 *
 * <p><b>Architecture:</b> The database uses three column families:</p>
 * <ul>
 *   <li>default: RocksDB system metadata</li>
 *   <li>nodes: MPT node storage (hash → CBOR bytes)</li>
 *   <li>roots: Version-indexed root hashes (version → hash)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("/path/to/db")) {
 *     MerklePatriciaTrie trie = new MerklePatriciaTrie(
 *         stateTrees.nodeStore(),
 *         Blake2b256::digest
 *     );
 *
 *     // Perform operations
 *     trie.put(key, value);
 *     byte[] root = trie.getRootHash();
 *
 *     // Store versioned root
 *     stateTrees.rootsIndex().put(blockHeight, root);
 * }
 * }</pre>
 *
 * <p><b>Resource Management:</b> This class implements AutoCloseable and properly
 * manages all RocksDB resources. Always use try-with-resources or ensure close()
 * is called to prevent resource leaks.</p>
 *
 * <p><b>Thread Safety:</b> The underlying RocksDB instance is thread-safe, but
 * the MPT operations require external synchronization.</p>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class RocksDbStateTrees implements AutoCloseable {

    private final DBOptions options;
    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final ColumnFamilyHandle cfRoots;

    private final RocksDbNodeStore nodeStore;
    private final RocksDbRootsIndex rootsIndex;

    /**
     * Creates a new unified RocksDB state trees storage with default namespace options.
     *
     * <p>Initializes a complete RocksDB instance with all required column families
     * for both node storage and root indexing. Automatically handles database
     * directory creation and column family management.</p>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @throws RuntimeException if RocksDB initialization fails
     */
    public RocksDbStateTrees(String dbPath) {
        this(dbPath, NamespaceOptions.defaults());
    }

    /**
     * Creates a new unified RocksDB state trees storage with custom namespace options.
     *
     * <p>Enables multiple isolated trees within the same database using namespacing.</p>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @param namespaceOptions the namespace configuration for this tree
     * @throws RuntimeException if RocksDB initialization fails
     */
    public RocksDbStateTrees(String dbPath, NamespaceOptions namespaceOptions) {
        try {
            RocksDB.loadLibrary();
            File dbDirectory = new File(dbPath);
            if (!dbDirectory.exists()) dbDirectory.mkdirs();

            RocksDbMptSchema.ColumnFamilies schema = RocksDbMptSchema.columnFamilies(namespaceOptions);

            // List existing CFs and open them all; also ensure nodes and roots exist
            java.util.List<byte[]> existingCfNames = RocksDB.listColumnFamilies(new org.rocksdb.Options().setCreateIfMissing(true), dbPath);

            // CF options with 1-byte prefix extractor for namespace support
            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
            columnFamilyOptions.useFixedLengthPrefixExtractor(1);

            java.util.List<ColumnFamilyDescriptor> cfDescriptors = new java.util.ArrayList<>();
            int nodesColumnFamilyIndex = -1, rootsColumnFamilyIndex = -1;
            for (byte[] cfName : existingCfNames) {
                cfDescriptors.add(new ColumnFamilyDescriptor(cfName, columnFamilyOptions));
                if (java.util.Arrays.equals(cfName, schema.nodes().getBytes()))
                    nodesColumnFamilyIndex = cfDescriptors.size() - 1;
                if (java.util.Arrays.equals(cfName, schema.roots().getBytes()))
                    rootsColumnFamilyIndex = cfDescriptors.size() - 1;
            }
            // Ensure default CF present
            boolean hasDefaultCf = false;
            for (byte[] cfName : existingCfNames)
                if (java.util.Arrays.equals(cfName, RocksDB.DEFAULT_COLUMN_FAMILY)) {
                    hasDefaultCf = true;
                    break;
                }
            if (!hasDefaultCf)
                cfDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            // Ensure nodes/roots CFs present
            if (nodesColumnFamilyIndex < 0) {
                cfDescriptors.add(new ColumnFamilyDescriptor(schema.nodes().getBytes(), columnFamilyOptions));
                nodesColumnFamilyIndex = cfDescriptors.size() - 1;
            }
            if (rootsColumnFamilyIndex < 0) {
                cfDescriptors.add(new ColumnFamilyDescriptor(schema.roots().getBytes(), columnFamilyOptions));
                rootsColumnFamilyIndex = cfDescriptors.size() - 1;
            }

            this.options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
            java.util.List<ColumnFamilyHandle> cfHandles = new java.util.ArrayList<>();
            this.db = RocksDB.open(options, dbPath, cfDescriptors, cfHandles);
            this.cfNodes = cfHandles.get(nodesColumnFamilyIndex);
            this.cfRoots = cfHandles.get(rootsColumnFamilyIndex);

            KeyPrefixer keyPrefixer = new KeyPrefixer(schema.keyPrefix());
            this.nodeStore = new RocksDbNodeStore(db, cfNodes, keyPrefixer);
            this.rootsIndex = new RocksDbRootsIndex(db, cfRoots, keyPrefixer);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to open RocksDB for state trees", e);
        }
    }

    /**
     * Returns the NodeStore instance for this state trees database.
     *
     * <p>The NodeStore provides persistent storage for Merkle Patricia Trie nodes,
     * with support for batch operations and read-your-writes consistency.</p>
     *
     * @return the RocksDbNodeStore instance
     */
    public RocksDbNodeStore nodeStore() {
        return nodeStore;
    }

    /**
     * Returns the RootsIndex instance for this state trees database.
     *
     * <p>The RootsIndex provides versioned storage for trie root hashes,
     * enabling historical state access and version management.</p>
     *
     * @return the RocksDbRootsIndex instance
     */
    public RocksDbRootsIndex rootsIndex() {
        return rootsIndex;
    }

    /**
     * Returns the underlying RocksDB database instance.
     *
     * <p>Provides direct access to the database for advanced operations,
     * monitoring, batch management, or custom column family operations.</p>
     *
     * @return the RocksDB instance
     */
    public RocksDB db() {
        return db;
    }

    /**
     * Atomically record a new root version and increment reference counts for all reachable nodes.
     *
     * <p>This helper wraps the typical production workflow of persisting a new root: it stores the
     * root in {@link RocksDbRootsIndex} (assigning the next version) and increments reference counts
     * for every node reachable from the provided root. Both actions occur within a single RocksDB
     * WriteBatch for atomicity.</p>
     *
     * <p>Usage:</p>
     * <pre>{@code
     * long version = stateTrees.putRootWithRefcount(trie.getRootHash());
     * }</pre>
     *
     * @param root the trie root commitment to record
     * @return the assigned version number
     * @throws RuntimeException if a RocksDB error occurs
     */
    public long putRootWithRefcount(byte[] root) {
        if (root == null || root.length == 0) throw new IllegalArgumentException("root cannot be null/empty");
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            final long[] verHolder = new long[1];
            rootsIndex.withBatch(wb, () -> {
                long ver = rootsIndex.nextVersion();
                rootsIndex.put(ver, root);
                verHolder[0] = ver;
                return null;
            });
            RefcountGcStrategy.incrementAll(db, nodeStore.nodesHandle(), nodeStore.nodesHandle(), root, wb, nodeStore.keyPrefixer());
            db.write(wo, wb);
            return verHolder[0];
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to write root + refcounts atomically", e);
        }
    }

    /**
     * Closes this state trees database and releases all associated resources.
     *
     * <p>Properly shuts down the database, column family handles, and options.
     * After calling this method, the NodeStore and RootsIndex instances should
     * not be used. This method is idempotent and safe to call multiple times.</p>
     */
    @Override
    public void close() {
        // Close handles and DB; nodeStore/rootsIndex constructed from existing DB are no-ops on close
        try {
            cfNodes.close();
        } catch (Exception ignored) { /* Ignore cleanup exceptions */ }
        try {
            cfRoots.close();
        } catch (Exception ignored) { /* Ignore cleanup exceptions */ }
        try {
            db.close();
        } catch (Exception ignored) { /* Ignore cleanup exceptions */ }
        try {
            options.close();
        } catch (Exception ignored) { /* Ignore cleanup exceptions */ }
    }
}
