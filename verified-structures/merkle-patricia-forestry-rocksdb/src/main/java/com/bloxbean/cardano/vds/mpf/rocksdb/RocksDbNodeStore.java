package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.DiskMarkSet;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RefcountLedger;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * RocksDB-based implementation of NodeStore for persistent MPT node storage.
 *
 * <p>This implementation provides high-performance, persistent storage for MPT nodes
 * using RocksDB with the following features:</p>
 * <ul>
 *   <li>Dedicated column family for node storage</li>
 *   <li>ThreadLocal batch support for atomic operations</li>
 *   <li>Read-your-writes consistency within batches</li>
 *   <li>Automatic column family creation</li>
 *   <li>Support for both standalone and shared DB instances</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe through ThreadLocal
 * isolation. Each thread can have its own batch operation context without affecting
 * other threads.</p>
 *
 * <p><b>Batch Operations:</b> Use {@link #withBatch(WriteBatch, java.util.concurrent.Callable)}
 * to execute multiple operations atomically:</p>
 * <pre>{@code
 * try (WriteBatch batch = new WriteBatch()) {
 *     nodeStore.withBatch(batch, () -> {
 *         trie.put(key1, value1);
 *         trie.put(key2, value2);
 *         return trie.getRootHash();
 *     });
 *     db.write(new WriteOptions(), batch);
 * }
 * }</pre>
 *
 * <p><b>Performance Notes:</b></p>
 * <ul>
 *   <li>Batched writes are significantly faster than individual puts</li>
 *   <li>The staged cache provides read-your-writes consistency without DB queries</li>
 *   <li>Consider tuning RocksDB options for your workload</li>
 * </ul>
 *
 * @since 0.8.0
 */
public class RocksDbNodeStore implements NodeStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RocksDbNodeStore.class);

    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final KeyPrefixer keyPrefixer;
    private final DBOptions options;
    private final List<AutoCloseable> closeables;
    private static final ThreadLocal<WriteBatch> TL_BATCH = new ThreadLocal<>();
    // Nodes staged by stores of another database, column family or namespace than the session owner's (whose
    // nodes are read from the session view instead).
    private static final ThreadLocal<java.util.Map<String, byte[]>> TL_STAGED = new ThreadLocal<>();
    // Per-batch state used by RocksDbStateTrees.putRootWithRefcount when it runs inside withBatch(): the store that
    // opened the batch, and the replayed view of the open batch for the owner's CF + namespace (refcounts and the
    // staged last version), kept up to date by put/delete so putRootWithRefcount does not replay the whole batch on
    // every call. The view is rebuilt from the batch itself whenever it is stale, so a batch shared by several
    // sessions is described correctly.
    private static final ThreadLocal<RocksDbNodeStore> TL_OWNER = new ThreadLocal<>();
    private static final ThreadLocal<RefcountLedger.BatchView> TL_VIEW = new ThreadLocal<>();

    public static final String CF_NODES = "nodes";

    /**
     * Creates a new RocksDbNodeStore with its own database instance.
     *
     * <p>This constructor creates and manages a complete RocksDB instance including:
     * <ul>
     *   <li>Database directory creation if it doesn't exist</li>
     *   <li>Column family discovery and creation</li>
     *   <li>Proper resource management for cleanup</li>
     * </ul>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @throws RuntimeException if RocksDB initialization fails
     */
    public RocksDbNodeStore(String dbPath) {
        this(dbPath, NamespaceOptions.defaults());
    }

    public RocksDbNodeStore(String dbPath, NamespaceOptions namespaceOptions) {
        try {
            RocksDB.loadLibrary();
            File dbDirectory = new File(dbPath);
            if (!dbDirectory.exists()) dbDirectory.mkdirs();

            RocksDbMptSchema.ColumnFamilies schema = RocksDbMptSchema.columnFamilies(namespaceOptions);

            java.util.List<byte[]> existingCfNames;
            try (org.rocksdb.Options tempOpts = new org.rocksdb.Options().setCreateIfMissing(true)) {
                existingCfNames = RocksDB.listColumnFamilies(tempOpts, dbPath);
            }

            // CF options with 1-byte prefix extractor for namespace support
            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
            columnFamilyOptions.useFixedLengthPrefixExtractor(1);

            java.util.List<ColumnFamilyDescriptor> cfDescriptors = new java.util.ArrayList<>();
            int nodesColumnFamilyIndex = -1;
            for (byte[] cfName : existingCfNames) {
                cfDescriptors.add(new ColumnFamilyDescriptor(cfName, columnFamilyOptions));
                if (Arrays.equals(cfName, schema.nodes().getBytes())) nodesColumnFamilyIndex = cfDescriptors.size() - 1;
            }
            boolean hasDefaultCf = false;
            for (byte[] cfName : existingCfNames)
                if (Arrays.equals(cfName, RocksDB.DEFAULT_COLUMN_FAMILY)) {
                    hasDefaultCf = true;
                    break;
                }
            if (!hasDefaultCf)
                cfDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            if (nodesColumnFamilyIndex < 0) {
                cfDescriptors.add(new ColumnFamilyDescriptor(schema.nodes().getBytes(), columnFamilyOptions));
                nodesColumnFamilyIndex = cfDescriptors.size() - 1;
            }

            java.util.List<ColumnFamilyHandle> cfHandles = new java.util.ArrayList<>();
            this.options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
            this.db = RocksDB.open(this.options, dbPath, cfDescriptors, cfHandles);
            this.cfNodes = cfHandles.get(nodesColumnFamilyIndex);
            DiskMarkSet.dropStale(db, cfHandles, java.util.List.of(cfNodes)); // GC leftovers of a crashed run
            this.keyPrefixer = new KeyPrefixer(schema.keyPrefix());
            this.closeables = new java.util.ArrayList<>();
            this.closeables.add(columnFamilyOptions);
            this.closeables.add(cfNodes);
            this.closeables.add(options);
            this.closeables.add(db);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to open RocksDB NodeStore", e);
        }
    }

    /**
     * Creates a RocksDbNodeStore using an existing RocksDB instance.
     *
     * <p>This constructor is used when sharing a RocksDB instance across multiple
     * components. The caller retains responsibility for managing the database lifecycle.
     * This instance will not close the provided database or column family handle.</p>
     *
     * @param db      the existing RocksDB instance
     * @param cfNodes the column family handle for node storage
     */
    public RocksDbNodeStore(RocksDB db, ColumnFamilyHandle cfNodes) {
        this(db, cfNodes, new KeyPrefixer((byte) 0x00));
    }

    public RocksDbNodeStore(RocksDB db, ColumnFamilyHandle cfNodes, KeyPrefixer keyPrefixer) {
        this.db = db;
        this.cfNodes = cfNodes;
        this.keyPrefixer = keyPrefixer;
        this.options = null;
        this.closeables = java.util.Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inside {@link #withBatch} a node put in the open batch is visible before the batch is written (a staged
     * delete is not: the committed node stays readable until the batch is written). For the session's own database,
     * column family and namespace, visibility follows the batch's contents: after {@code clear()},
     * {@code rollbackToSavePoint()} or raw writes the batch is replayed, so discarded nodes are no longer visible.</p>
     */
    @Override
    public byte[] get(byte[] hash) {
        try {
            WriteBatch batch = TL_BATCH.get();
            RefcountLedger.BatchView view = batch != null ? sessionView() : null;
            if (view != null) {
                view.ensureCurrent(batch, keyPrefixer);
                byte[] prefixed = keyPrefixer.prefix(hash);
                byte[] staged = view.stagedPut(HexUtil.encodeHexString(prefixed));
                return staged != null ? staged : db.get(cfNodes, prefixed);
            }
            java.util.Map<String, byte[]> stagedWrites = TL_STAGED.get();
            if (stagedWrites != null) {
                byte[] cachedValue = stagedWrites.get(HexUtil.encodeHexString(hash));
                if (cachedValue != null) return cachedValue;
            }
            return db.get(cfNodes, keyPrefixer.prefix(hash));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read node from RocksDB", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>When operating within a batch context, writes are staged both in the WriteBatch
     * and a ThreadLocal cache for immediate read visibility. Otherwise, writes directly
     * to the database.</p>
     */
    @Override
    public void put(byte[] hash, byte[] nodeBytes) {
        try {
            WriteBatch currentBatch = TL_BATCH.get();
            if (currentBatch != null) {
                byte[] prefixed = keyPrefixer.prefix(hash);
                RefcountLedger.BatchView view = sessionView();
                if (view == null) RefcountLedger.BatchView.markSessionStale(currentBatch); // another namespace's write
                boolean current = view != null && view.isCurrent(currentBatch);
                currentBatch.put(cfNodes, prefixed, nodeBytes);
                if (current) {
                    view.recordPut(cfNodes.getID(), prefixed, nodeBytes);
                    view.sync(currentBatch);
                }
                if (view != null) return;
                java.util.Map<String, byte[]> stagedWrites = TL_STAGED.get();
                if (stagedWrites == null) {
                    stagedWrites = new java.util.HashMap<>();
                    TL_STAGED.set(stagedWrites);
                }
                stagedWrites.put(HexUtil.encodeHexString(hash), nodeBytes);
            } else {
                db.put(cfNodes, keyPrefixer.prefix(hash), nodeBytes);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to write node to RocksDB", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>When operating within a batch context, deletes are staged in the WriteBatch
     * and the entry is removed from the ThreadLocal cache to maintain consistency.</p>
     */
    @Override
    public void delete(byte[] hash) {
        try {
            WriteBatch currentBatch = TL_BATCH.get();
            if (currentBatch != null) {
                byte[] prefixed = keyPrefixer.prefix(hash);
                RefcountLedger.BatchView view = sessionView();
                if (view == null) RefcountLedger.BatchView.markSessionStale(currentBatch);
                boolean current = view != null && view.isCurrent(currentBatch);
                currentBatch.delete(cfNodes, prefixed);
                if (current) {
                    view.recordDelete(cfNodes.getID(), prefixed);
                    view.sync(currentBatch);
                }
                java.util.Map<String, byte[]> stagedWrites = TL_STAGED.get();
                if (stagedWrites != null) stagedWrites.remove(HexUtil.encodeHexString(hash));
            } else {
                db.delete(cfNodes, keyPrefixer.prefix(hash));
            }
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to delete node from RocksDB", e);
        }
    }

    /**
     * Returns the underlying RocksDB database instance.
     *
     * <p>Provides access to the raw database for advanced operations, monitoring,
     * or integration with other RocksDB-based components.</p>
     *
     * @return the RocksDB instance
     */
    public RocksDB db() {
        return db;
    }

    /**
     * Returns the column family handle used for node storage.
     *
     * <p>Useful for custom operations, batch management, or integration
     * with other components that need direct access to the nodes column family.</p>
     *
     * @return the nodes column family handle
     */
    public ColumnFamilyHandle nodesHandle() {
        return cfNodes;
    }

    /**
     * Returns the key prefixer used for namespace support.
     *
     * <p>Components that directly access RocksDB (like GC) need this to properly
     * prefix/unprefix keys when iterating over the database.</p>
     *
     * @return the key prefixer instance
     */
    public KeyPrefixer keyPrefixer() {
        return keyPrefixer;
    }

    /**
     * Executes the provided work within a batch context.
     *
     * <p>All NodeStore operations performed within the work block will be staged
     * in the provided WriteBatch instead of being immediately committed. This enables:</p>
     * <ul>
     *   <li>Atomic multi-operation transactions</li>
     *   <li>Read-your-writes consistency within the batch</li>
     *   <li>Improved performance for bulk operations</li>
     * </ul>
     *
     * <p><b>Important:</b> The caller is responsible for actually writing the batch
     * to the database using {@code db.write(writeOptions, batch)}.</p>
     *
     * @param <T>   the return type of the work function
     * @param batch the WriteBatch to stage operations in
     * @param work  the work to execute within the batch context
     * @return the result of the work function
     * @throws RuntimeException if the work function throws an exception
     */
    public <T> T withBatch(WriteBatch batch, java.util.concurrent.Callable<T> work) {
        if (TL_BATCH.get() != null) {
            throw new IllegalStateException("Nested withBatch() calls are not supported. "
                    + "Compose operations within a single batch context instead.");
        }
        TL_BATCH.set(batch);
        TL_STAGED.set(new java.util.HashMap<>());
        TL_OWNER.set(this);
        RefcountLedger.BatchView view = new RefcountLedger.BatchView(cfNodes.getID(), keyPrefixer.getNamespaceId(),
                RocksDbRootsIndex.lastVersionKeySuffix());
        TL_VIEW.set(view);
        RefcountLedger.BatchView.openSession(batch, view);
        try {
            // An empty batch is described right away; a batch that already holds records is replayed on first use.
            if (batch.count() == 0) view.ensureCurrent(batch, keyPrefixer);
            return work.call();
        } catch (Exception e) {
            throw new RuntimeException("Batch operation failed", e);
        } finally {
            TL_BATCH.remove();
            TL_STAGED.remove();
            TL_OWNER.remove();
            TL_VIEW.remove();
            RefcountLedger.BatchView.closeSession();
        }
    }

    /**
     * The batch opened by {@link #withBatch} in the current thread, or {@code null} when none is open.
     *
     * <p>A batch opened by another instance over the same database, column family and namespace is joined (its
     * staged nodes are visible to {@link #get}). A batch of an unrelated store cannot be joined safely, so this
     * throws instead of letting the caller commit on its own outside that batch.</p>
     */
    WriteBatch activeBatch() {
        RocksDbNodeStore owner = TL_OWNER.get();
        if (owner == null) return null;
        if (owner == this || sameNamespace(owner)) return TL_BATCH.get();
        throw new IllegalStateException("A batch of a node store on another database, column family or namespace "
                + "is open on this thread; record roots of this namespace outside that batch, or open the batch on a "
                + "store of this namespace.");
    }

    /** Replay cache of the active batch for this store's CF + namespace, or {@code null} when none is open. */
    RefcountLedger.BatchView activeBatchView() {
        return activeBatch() != null ? TL_VIEW.get() : null;
    }

    /** The open session's view when this store may use it (same database, column family and namespace). */
    private RefcountLedger.BatchView sessionView() {
        RocksDbNodeStore owner = TL_OWNER.get();
        return owner != null && (owner == this || sameNamespace(owner)) ? TL_VIEW.get() : null;
    }

    private boolean sameNamespace(RocksDbNodeStore other) {
        return other.db == db
                && other.cfNodes.getID() == cfNodes.getID()
                && other.keyPrefixer.getNamespaceId() == keyPrefixer.getNamespaceId();
    }

    /**
     * Closes this node store and releases all associated resources.
     *
     * <p>For instances created with the path constructor, this closes the database,
     * column family handles, and options. For instances created with an existing
     * database, this is a no-op since resource ownership remains with the caller.</p>
     */
    @Override
    public void close() {
        for (AutoCloseable resource : closeables) {
            try {
                resource.close();
            } catch (Exception e) {
                log.warn("Failed to close resource: {}", resource.getClass().getSimpleName(), e);
            }
        }
    }
}
