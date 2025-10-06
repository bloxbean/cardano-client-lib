package com.bloxbean.cardano.statetrees.rocksdb.mpt;

import com.bloxbean.cardano.statetrees.api.NodeStore;
import org.rocksdb.*;

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
 * @author Bloxbean Project
 * @since 0.6.0
 */
public class RocksDbNodeStore implements NodeStore, AutoCloseable {

    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final DBOptions options;
    private final List<AutoCloseable> closeables;
    private static final ThreadLocal<WriteBatch> TL_BATCH = new ThreadLocal<>();
    private static final ThreadLocal<java.util.Map<String, byte[]>> TL_STAGED = new ThreadLocal<>();

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
        try {
            RocksDB.loadLibrary();
            File dbDirectory = new File(dbPath);
            if (!dbDirectory.exists()) dbDirectory.mkdirs();

            java.util.List<byte[]> existingCfNames = RocksDB.listColumnFamilies(new org.rocksdb.Options().setCreateIfMissing(true), dbPath);

            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
            java.util.List<ColumnFamilyDescriptor> cfDescriptors = new java.util.ArrayList<>();
            int nodesColumnFamilyIndex = -1;
            for (byte[] cfName : existingCfNames) {
                cfDescriptors.add(new ColumnFamilyDescriptor(cfName, columnFamilyOptions));
                if (Arrays.equals(cfName, CF_NODES.getBytes())) nodesColumnFamilyIndex = cfDescriptors.size() - 1;
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
                cfDescriptors.add(new ColumnFamilyDescriptor(CF_NODES.getBytes(), columnFamilyOptions));
                nodesColumnFamilyIndex = cfDescriptors.size() - 1;
            }

            java.util.List<ColumnFamilyHandle> cfHandles = new java.util.ArrayList<>();
            this.options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
            this.db = RocksDB.open(this.options, dbPath, cfDescriptors, cfHandles);
            this.cfNodes = cfHandles.get(nodesColumnFamilyIndex);
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
        this.db = db;
        this.cfNodes = cfNodes;
        this.options = null;
        this.closeables = java.util.Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation checks the ThreadLocal staged cache first for read-your-writes
     * consistency during batch operations, then falls back to the persistent store.</p>
     */
    @Override
    public byte[] get(byte[] hash) {
        try {
            java.util.Map<String, byte[]> stagedWrites = TL_STAGED.get();
            if (stagedWrites != null) {
                byte[] cachedValue = stagedWrites.get(java.util.Arrays.toString(hash));
                if (cachedValue != null) return cachedValue;
            }
            return db.get(cfNodes, hash);
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
                currentBatch.put(cfNodes, hash, nodeBytes);
                java.util.Map<String, byte[]> stagedWrites = TL_STAGED.get();
                if (stagedWrites == null) {
                    stagedWrites = new java.util.HashMap<>();
                    TL_STAGED.set(stagedWrites);
                }
                stagedWrites.put(java.util.Arrays.toString(hash), nodeBytes);
            } else {
                db.put(cfNodes, hash, nodeBytes);
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
                currentBatch.delete(cfNodes, hash);
                java.util.Map<String, byte[]> stagedWrites = TL_STAGED.get();
                if (stagedWrites != null) stagedWrites.remove(java.util.Arrays.toString(hash));
            } else {
                db.delete(cfNodes, hash);
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
        TL_BATCH.set(batch);
        java.util.Map<String, byte[]> previousStaged = TL_STAGED.get();
        TL_STAGED.set(new java.util.HashMap<>());
        try {
            return work.call();
        } catch (Exception e) {
            throw new RuntimeException("Batch operation failed", e);
        } finally {
            TL_BATCH.remove();
            TL_STAGED.set(previousStaged);
        }
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
            } catch (Exception ignored) {
                // Ignore cleanup exceptions to avoid masking other issues
            }
        }
    }
}
