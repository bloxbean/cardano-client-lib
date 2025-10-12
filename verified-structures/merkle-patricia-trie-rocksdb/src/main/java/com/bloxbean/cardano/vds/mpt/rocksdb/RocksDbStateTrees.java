package com.bloxbean.cardano.vds.mpt.rocksdb;

import com.bloxbean.cardano.vds.core.api.StateTrees;
import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.GcReport;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.strategy.OnDiskMarkSweepStrategy;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
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
 * @since 0.8.0
 */
public final class RocksDbStateTrees implements StateTrees {

    private static final String MODE_METADATA_KEY_SUFFIX = "_storage_mode";

    private final DBOptions options;
    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final ColumnFamilyHandle cfRoots;

    private final RocksDbNodeStore nodeStore;
    private final RocksDbRootsIndex rootsIndex;
    private final StorageMode storageMode;
    private final KeyPrefixer keyPrefixer;

    /**
     * Creates a new unified RocksDB state trees storage with default namespace options and multi-version mode.
     *
     * <p>Initializes a complete RocksDB instance with all required column families
     * for both node storage and root indexing. Automatically handles database
     * directory creation and column family management.</p>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @throws RuntimeException if RocksDB initialization fails
     */
    public RocksDbStateTrees(String dbPath) {
        this(dbPath, NamespaceOptions.defaults(), StorageMode.MULTI_VERSION);
    }

    /**
     * Creates a new unified RocksDB state trees storage with specified storage mode.
     *
     * <p>Uses default namespace options. This is a convenience constructor for users
     * who want to specify the storage mode (MULTI_VERSION or SINGLE_VERSION) without
     * configuring custom namespacing.</p>
     *
     * <p><b>Example - Snapshot mode:</b></p>
     * <pre>{@code
     * // Fast snapshot-only storage (no history)
     * RocksDbStateTrees stateTrees = new RocksDbStateTrees(
     *     "/data/utxo-snapshot",
     *     StorageMode.SINGLE_VERSION
     * );
     * }</pre>
     *
     * <p><b>Example - Multi-version mode:</b></p>
     * <pre>{@code
     * // Historical versioning with rollback support
     * RocksDbStateTrees stateTrees = new RocksDbStateTrees(
     *     "/data/utxo-versioned",
     *     StorageMode.MULTI_VERSION
     * );
     * }</pre>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @param storageMode the storage mode (MULTI_VERSION or SINGLE_VERSION)
     * @throws RuntimeException if RocksDB initialization fails
     * @throws IllegalStateException if storage mode doesn't match existing database
     * @see StorageMode
     */
    public RocksDbStateTrees(String dbPath, StorageMode storageMode) {
        this(dbPath, NamespaceOptions.defaults(), storageMode);
    }

    /**
     * Creates a new unified RocksDB state trees storage with custom namespace options and multi-version mode.
     *
     * <p>Enables multiple isolated trees within the same database using namespacing.</p>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @param namespaceOptions the namespace configuration for this tree
     * @throws RuntimeException if RocksDB initialization fails
     */
    public RocksDbStateTrees(String dbPath, NamespaceOptions namespaceOptions) {
        this(dbPath, namespaceOptions, StorageMode.MULTI_VERSION);
    }

    /**
     * Creates a new unified RocksDB state trees storage with custom namespace options and storage mode.
     *
     * <p>Enables multiple isolated trees within the same database using namespacing,
     * with control over whether to store multiple versions or operate in snapshot mode.</p>
     *
     * <p>Storage mode is validated against existing database metadata. If the database
     * already exists for this namespace, the mode must match. If this is a new namespace,
     * the mode is persisted for future validation.</p>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @param namespaceOptions the namespace configuration for this tree
     * @param storageMode the storage mode (MULTI_VERSION or SINGLE_VERSION)
     * @throws RuntimeException if RocksDB initialization fails
     * @throws IllegalStateException if storage mode doesn't match existing database
     */
    public RocksDbStateTrees(String dbPath, NamespaceOptions namespaceOptions, StorageMode storageMode) {
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

            this.keyPrefixer = new KeyPrefixer(schema.keyPrefix());
            this.nodeStore = new RocksDbNodeStore(db, cfNodes, keyPrefixer);
            this.rootsIndex = new RocksDbRootsIndex(db, cfRoots, keyPrefixer);

            // Validate and persist storage mode
            this.storageMode = storageMode;
            validateAndPersistStorageMode(storageMode);
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
     * Returns the current storage mode for this database.
     *
     * @return the storage mode (MULTI_VERSION or SINGLE_VERSION)
     */
    public StorageMode storageMode() {
        return storageMode;
    }

    /**
     * Validates storage mode against existing database metadata and persists it if new.
     *
     * <p>This method ensures that a database opened with a particular storage mode
     * cannot be accidentally opened with a different mode, which could lead to
     * data corruption.</p>
     *
     * @param requestedMode the storage mode being requested
     * @throws IllegalStateException if mode doesn't match existing database
     * @throws RuntimeException if RocksDB error occurs
     */
    private void validateAndPersistStorageMode(StorageMode requestedMode) {
        try {
            // Create namespace-prefixed metadata key
            byte[] prefixedMetadataKey = keyPrefixer.prefix(MODE_METADATA_KEY_SUFFIX.getBytes());

            // Read stored mode for this namespace
            byte[] storedModeBytes = db.get(cfRoots, prefixedMetadataKey);

            if (storedModeBytes != null) {
                // Existing namespace - verify mode matches
                String storedMode = new String(storedModeBytes);
                if (!storedMode.equals(requestedMode.name())) {
                    throw new IllegalStateException(
                        "Storage mode mismatch for namespace: " +
                        "Database was created with '" + storedMode + "' mode " +
                        "but is being opened with '" + requestedMode.name() + "' mode. " +
                        "Use the correct storage mode or create a new namespace."
                    );
                }
            } else {
                // New namespace - persist the mode
                db.put(cfRoots, prefixedMetadataKey, requestedMode.name().getBytes());
            }
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to validate storage mode", e);
        }
    }

    /**
     * Atomically record a new root version and increment reference counts for all reachable nodes.
     *
     * <p>This helper wraps the typical production workflow of persisting a new root: it stores the
     * root in {@link RocksDbRootsIndex} (assigning the next version) and increments reference counts
     * for every node reachable from the provided root. Both actions occur within a single RocksDB
     * WriteBatch for atomicity.</p>
     *
     * <p><b>Note:</b> This method is only valid for {@link StorageMode#MULTI_VERSION} mode.</p>
     *
     * <p>Usage:</p>
     * <pre>{@code
     * long version = stateTrees.putRootWithRefcount(trie.getRootHash());
     * }</pre>
     *
     * @param root the trie root commitment to record
     * @return the assigned version number
     * @throws IllegalStateException if called in SINGLE_VERSION mode
     * @throws RuntimeException if a RocksDB error occurs
     */
    public long putRootWithRefcount(byte[] root) {
        if (storageMode != StorageMode.MULTI_VERSION) {
            throw new IllegalStateException("putRootWithRefcount() requires MULTI_VERSION mode. Use putRootSnapshot() for SINGLE_VERSION mode.");
        }
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
     * Store a new root in snapshot mode (single-version).
     *
     * <p>In snapshot mode, there is always exactly one root at version 0. Each call to this
     * method overwrites the previous root, creating orphaned nodes that must be cleaned up
     * periodically using {@link #cleanupOrphanedNodes(GcOptions)}.</p>
     *
     * <p><b>Note:</b> This method is only valid for {@link StorageMode#SINGLE_VERSION} mode.</p>
     *
     * <p>Usage:</p>
     * <pre>{@code
     * stateTrees.putRootSnapshot(trie.getRootHash());
     * }</pre>
     *
     * @param newRoot the new trie root commitment (overwrites previous root at version 0)
     * @throws IllegalStateException if called in MULTI_VERSION mode
     * @throws RuntimeException if a RocksDB error occurs
     */
    public void putRootSnapshot(byte[] newRoot) {
        if (storageMode != StorageMode.SINGLE_VERSION) {
            throw new IllegalStateException("putRootSnapshot() requires SINGLE_VERSION mode. Use putRootWithRefcount() for MULTI_VERSION mode.");
        }
        if (newRoot == null || newRoot.length == 0) {
            throw new IllegalArgumentException("root cannot be null/empty");
        }
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            rootsIndex.withBatch(wb, () -> {
                rootsIndex.put(0L, newRoot);  // Always version 0
                return null;
            });
            db.write(wo, wb);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to write snapshot root", e);
        }
    }

    /**
     * Get the current root hash in snapshot mode.
     *
     * <p>Returns the root at version 0, or null if no root has been stored yet.</p>
     *
     * <p><b>Note:</b> This method is only valid for {@link StorageMode#SINGLE_VERSION} mode.</p>
     *
     * @return the current root hash, or null if none exists
     * @throws IllegalStateException if called in MULTI_VERSION mode
     */
    public byte[] getCurrentRoot() {
        if (storageMode != StorageMode.SINGLE_VERSION) {
            throw new IllegalStateException("getCurrentRoot() requires SINGLE_VERSION mode. Use rootsIndex().get(version) for MULTI_VERSION mode.");
        }
        return rootsIndex.get(0L);
    }

    /**
     * Clean up orphaned nodes in snapshot mode (StateTrees interface implementation).
     *
     * <p>This is the generic interface method that accepts Object for backend-agnostic
     * usage. For RocksDB, the options parameter should be a {@link GcOptions} instance.</p>
     *
     * @param options garbage collection options (must be {@link GcOptions} for RocksDB)
     * @return a GcReport wrapped as Object
     * @throws IllegalStateException if called in MULTI_VERSION mode
     * @throws IllegalArgumentException if options is not a GcOptions instance
     * @throws Exception if GC operation fails
     */
    @Override
    public Object cleanupOrphanedNodes(Object options) throws Exception {
        if (!(options instanceof GcOptions)) {
            throw new IllegalArgumentException("Options must be a GcOptions instance for RocksDB backend");
        }
        return cleanupOrphanedNodes((GcOptions) options);
    }

    /**
     * Clean up orphaned nodes in snapshot mode.
     *
     * <p>This method uses mark-and-sweep garbage collection to identify and remove nodes
     * that are no longer reachable from the current root (version 0). It should be called
     * periodically to reclaim disk space.</p>
     *
     * <p><b>Note:</b> This method is only valid for {@link StorageMode#SINGLE_VERSION} mode.</p>
     *
     * <p>Usage:</p>
     * <pre>{@code
     * GcOptions options = new GcOptions();
     * options.setProgressInterval(100000);
     * GcReport report = stateTrees.cleanupOrphanedNodes(options);
     * System.out.println("Deleted " + report.nodesDeleted() + " orphaned nodes");
     * }</pre>
     *
     * @param options garbage collection options (progress callbacks, etc.)
     * @return a report containing the number of nodes deleted and time taken
     * @throws IllegalStateException if called in MULTI_VERSION mode
     * @throws Exception if GC operation fails
     */
    public GcReport cleanupOrphanedNodes(GcOptions options) throws Exception {
        if (storageMode != StorageMode.SINGLE_VERSION) {
            throw new IllegalStateException("cleanupOrphanedNodes() requires SINGLE_VERSION mode. Use appropriate GC strategy for MULTI_VERSION mode.");
        }
        OnDiskMarkSweepStrategy markSweep = new OnDiskMarkSweepStrategy();
        RetentionPolicy policy = RetentionPolicy.keepLatestN(1);  // Keep only version 0
        return markSweep.run(nodeStore, rootsIndex, policy, options);
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
