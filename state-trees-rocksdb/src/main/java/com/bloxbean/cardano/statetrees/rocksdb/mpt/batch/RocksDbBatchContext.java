package com.bloxbean.cardano.statetrees.rocksdb.mpt.batch;

import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbBatchException;
import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbOperationException;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RocksDbKey;
import org.rocksdb.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modern batch context using try-with-resources instead of ThreadLocal.
 *
 * <p>This class replaces the problematic ThreadLocal-based batch management
 * with a safer, RAII-based approach. It provides read-your-writes consistency
 * within a batch context while ensuring proper resource cleanup and eliminating
 * memory leak risks associated with ThreadLocal usage.</p>
 *
 * <p><b>Key Improvements over ThreadLocal approach:</b></p>
 * <ul>
 *   <li>Eliminates ThreadLocal memory leak potential</li>
 *   <li>Automatic resource cleanup with try-with-resources</li>
 *   <li>Explicit batch lifecycle management</li>
 *   <li>Type-safe key operations</li>
 *   <li>Comprehensive error handling and reporting</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * try (RocksDbBatchContext batch = RocksDbBatchContext.create(db)) {
 *     batch.put(cfNodes, NodeHashKey.of(hash1), data1);
 *     batch.put(cfNodes, NodeHashKey.of(hash2), data2);
 *
 *     // Read-your-writes consistency
 *     byte[] retrieved = batch.get(cfNodes, NodeHashKey.of(hash1));
 *     assertArrayEquals(data1, retrieved);
 *
 *     batch.commit(); // Atomic commit of all operations
 * } // Automatic resource cleanup
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. Each thread should
 * create its own batch context. Unlike ThreadLocal, this design makes the
 * single-threaded nature explicit and prevents accidental sharing bugs.</p>
 *
 * @since 0.8.0
 */
public final class RocksDbBatchContext implements AutoCloseable {

    /**
     * The RocksDB instance for database operations.
     */
    private final RocksDB db;

    /**
     * The WriteBatch for accumulating operations.
     */
    private final WriteBatch batch;

    /**
     * WriteOptions for the batch commit operation.
     */
    private final WriteOptions writeOptions;

    /**
     * Staged writes for read-your-writes consistency.
     * Maps (column family + key) to value bytes.
     */
    private final Map<StagedWriteKey, byte[]> stagedWrites;

    /**
     * Flag to track whether this batch has been committed.
     */
    private final AtomicBoolean committed = new AtomicBoolean(false);

    /**
     * Flag to track whether this batch has been closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Counter for the number of operations in this batch.
     */
    private int operationCount = 0;

    /**
     * Private constructor - use factory methods to create instances.
     *
     * @param db           the RocksDB instance
     * @param writeOptions the write options for batch commit
     */
    private RocksDbBatchContext(RocksDB db, WriteOptions writeOptions) {
        this.db = Objects.requireNonNull(db, "RocksDB instance cannot be null");
        this.writeOptions = writeOptions != null ? writeOptions : new WriteOptions();
        this.batch = new WriteBatch();
        this.stagedWrites = new HashMap<>();
    }

    /**
     * Creates a new RocksDbBatchContext with default write options.
     *
     * @param db the RocksDB instance (must not be null)
     * @return a new batch context ready for operations
     * @throws IllegalArgumentException if db is null
     */
    public static RocksDbBatchContext create(RocksDB db) {
        return new RocksDbBatchContext(db, null);
    }

    /**
     * Creates a new RocksDbBatchContext with custom write options.
     *
     * @param db           the RocksDB instance (must not be null)
     * @param writeOptions the write options for batch commit (null for default)
     * @return a new batch context ready for operations
     * @throws IllegalArgumentException if db is null
     */
    public static RocksDbBatchContext create(RocksDB db, WriteOptions writeOptions) {
        return new RocksDbBatchContext(db, writeOptions);
    }

    /**
     * Adds a PUT operation to this batch.
     *
     * <p>The operation is staged in the WriteBatch and also cached for
     * read-your-writes consistency. The actual write to the database
     * occurs when {@link #commit()} is called.</p>
     *
     * @param cf    the column family handle
     * @param key   the type-safe key
     * @param value the value bytes (must not be null)
     * @throws RocksDbOperationException if the operation fails
     * @throws IllegalStateException     if the batch is closed or committed
     */
    public void put(ColumnFamilyHandle cf, RocksDbKey key, byte[] value) throws RocksDbOperationException {
        validateState("PUT");
        Objects.requireNonNull(cf, "Column family handle cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");

        try {
            byte[] keyBytes = key.toBytes();
            batch.put(cf, keyBytes, value);

            // Stage the write for read-your-writes consistency
            StagedWriteKey stagedKey = new StagedWriteKey(cf, key);
            stagedWrites.put(stagedKey, value.clone());

            operationCount++;
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("PUT", key, e);
        }
    }

    /**
     * Retrieves a value from this batch context with read-your-writes consistency.
     *
     * <p>This method first checks the staged writes for any uncommitted changes,
     * then falls back to reading from the database. This ensures that reads
     * within the same batch can see writes that haven't been committed yet.</p>
     *
     * @param cf  the column family handle
     * @param key the type-safe key
     * @return the value bytes, or null if not found
     * @throws RocksDbOperationException if the operation fails
     * @throws IllegalStateException     if the batch is closed
     */
    public byte[] get(ColumnFamilyHandle cf, RocksDbKey key) throws RocksDbOperationException {
        validateState("GET");
        Objects.requireNonNull(cf, "Column family handle cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");

        // Check staged writes first for read-your-writes consistency
        StagedWriteKey stagedKey = new StagedWriteKey(cf, key);
        byte[] stagedValue = stagedWrites.get(stagedKey);
        if (stagedValue != null) {
            return stagedValue.clone(); // Return defensive copy
        }

        // Fall back to database read
        try {
            return db.get(cf, key.toBytes());
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("GET", key, e);
        }
    }

    /**
     * Adds a DELETE operation to this batch.
     *
     * <p>The delete is staged in the WriteBatch and the key is removed from
     * the staged writes cache to maintain consistency.</p>
     *
     * @param cf  the column family handle
     * @param key the type-safe key
     * @throws RocksDbOperationException if the operation fails
     * @throws IllegalStateException     if the batch is closed or committed
     */
    public void delete(ColumnFamilyHandle cf, RocksDbKey key) throws RocksDbOperationException {
        validateState("DELETE");
        Objects.requireNonNull(cf, "Column family handle cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");

        try {
            batch.delete(cf, key.toBytes());

            // Remove from staged writes to maintain consistency
            StagedWriteKey stagedKey = new StagedWriteKey(cf, key);
            stagedWrites.remove(stagedKey);

            operationCount++;
        } catch (RocksDBException e) {
            throw new RocksDbOperationException("DELETE", key, e);
        }
    }

    /**
     * Commits all operations in this batch to the database.
     *
     * <p>This operation is atomic - either all operations succeed or none do.
     * After successful commit, this batch context cannot be used for further
     * operations.</p>
     *
     * @throws RocksDbBatchException if the commit fails
     * @throws IllegalStateException if already committed or closed
     */
    public void commit() throws RocksDbBatchException {
        if (committed.get()) {
            throw new IllegalStateException("Batch already committed");
        }
        if (closed.get()) {
            throw new IllegalStateException("Batch already closed");
        }

        try {
            long batchSizeBytes = batch.data().length;
            db.write(writeOptions, batch);
            committed.set(true);
        } catch (RocksDBException e) {
            throw new RocksDbBatchException("Failed to commit batch", operationCount, e);
        }
    }

    /**
     * Returns the number of operations currently staged in this batch.
     *
     * @return the operation count
     */
    public int getOperationCount() {
        return operationCount;
    }

    /**
     * Returns the approximate size of the batch in bytes.
     *
     * @return the batch size in bytes
     */
    public long getBatchSizeBytes() {
        try {
            return batch.data().length;
        } catch (RocksDBException e) {
            return -1; // Size unavailable
        }
    }

    /**
     * Checks whether this batch has been committed.
     *
     * @return true if committed, false otherwise
     */
    public boolean isCommitted() {
        return committed.get();
    }

    /**
     * Checks whether this batch has been closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes this batch context and releases all resources.
     *
     * <p>This method is idempotent and safe to call multiple times.
     * After closing, the batch context cannot be used for any operations.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            // Clear staged writes to help GC
            stagedWrites.clear();

            // Close RocksDB resources
            try {
                batch.close();
            } catch (Exception e) {
                // Log but don't throw in close()
                System.err.println("Warning: Error closing WriteBatch: " + e.getMessage());
            }

            // Note: We don't close writeOptions here as it might be shared
            // The caller is responsible for managing WriteOptions lifecycle
        }
    }

    /**
     * Validates that this batch context is in a valid state for operations.
     *
     * @param operation the operation being attempted
     * @throws IllegalStateException if the batch is in an invalid state
     */
    private void validateState(String operation) {
        if (closed.get()) {
            throw new IllegalStateException("Cannot perform " + operation + " on closed batch");
        }
        if (committed.get()) {
            throw new IllegalStateException("Cannot perform " + operation + " on committed batch");
        }
    }

    /**
     * Key for staging writes in the read-your-writes cache.
     * Combines column family and key for uniqueness.
     */
    private static final class StagedWriteKey {
        private final ColumnFamilyHandle columnFamily;
        private final RocksDbKey key;

        StagedWriteKey(ColumnFamilyHandle columnFamily, RocksDbKey key) {
            this.columnFamily = columnFamily;
            this.key = key;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            StagedWriteKey other = (StagedWriteKey) obj;
            return Objects.equals(columnFamily, other.columnFamily) &&
                    Objects.equals(key, other.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(columnFamily, key);
        }

        @Override
        public String toString() {
            return String.format("StagedWriteKey[cf=%s, key=%s]",
                    columnFamily != null ? "handle" : "null", key);
        }
    }
}
