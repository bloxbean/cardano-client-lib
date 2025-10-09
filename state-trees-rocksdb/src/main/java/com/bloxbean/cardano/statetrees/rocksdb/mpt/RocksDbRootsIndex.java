package com.bloxbean.cardano.statetrees.rocksdb.mpt;

import com.bloxbean.cardano.statetrees.api.RootsIndex;
import com.bloxbean.cardano.statetrees.rocksdb.namespace.KeyPrefixer;
import com.bloxbean.cardano.statetrees.rocksdb.namespace.NamespaceOptions;
import org.rocksdb.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * RocksDB-based implementation of RootsIndex for versioned root hash management.
 *
 * <p>This implementation provides persistent storage for MPT root hashes with
 * version tracking, supporting the following features:</p>
 * <ul>
 *   <li>Version-based root hash storage using 8-byte long keys</li>
 *   <li>Automatic "latest" root tracking for quick access</li>
 *   <li>Monotonic version management with atomic increments</li>
 *   <li>Range queries for historical root retrieval</li>
 *   <li>ThreadLocal batch support for atomic multi-version updates</li>
 * </ul>
 *
 * <p><b>Special Keys:</b></p>
 * <ul>
 *   <li>LATEST_KEY: Always contains the most recent root hash</li>
 *   <li>LAST_VERSION_KEY: Tracks the highest version number</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Thread-safe through RocksDB's internal synchronization
 * and ThreadLocal batch isolation.</p>
 *
 * <p><b>Advanced Features:</b></p>
 * <pre>{@code
 * // Range query for historical roots
 * NavigableMap<Long, byte[]> history = rootsIndex.listRange(100, 200);
 *
 * // Atomic version increment
 * long nextVer = rootsIndex.nextVersion();
 * rootsIndex.put(nextVer, newRoot);
 * }</pre>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Version keys are stored as big-endian 8-byte arrays for proper ordering</li>
 *   <li>Range queries use RocksDB iterators for efficiency</li>
 *   <li>Batch operations reduce write amplification</li>
 * </ul>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public class RocksDbRootsIndex implements RootsIndex, AutoCloseable {

    private final RocksDB db;
    private final ColumnFamilyHandle cfRoots;
    private final KeyPrefixer keyPrefixer;
    private final DBOptions options;
    private final java.util.List<AutoCloseable> closeables;
    private static final ThreadLocal<WriteBatch> TL_BATCH = new ThreadLocal<>();

    public static final String CF_ROOTS = "roots";
    private static final byte[] LATEST_KEY = new byte[]{'L', 'A', 'T', 'E', 'S', 'T'};
    private static final byte[] LAST_VERSION_KEY = new byte[]{'V', 'E', 'R', 'S', 'I', 'O', 'N'};

    /**
     * Creates a new RocksDbRootsIndex with its own database instance.
     *
     * <p>Initializes a complete RocksDB instance for root hash storage with
     * automatic column family management and proper resource lifecycle handling.</p>
     *
     * @param dbPath the file system path where the RocksDB database should be stored
     * @throws RuntimeException if RocksDB initialization fails
     */
    public RocksDbRootsIndex(String dbPath) {
        this(dbPath, NamespaceOptions.defaults());
    }

    public RocksDbRootsIndex(String dbPath, NamespaceOptions namespaceOptions) {
        try {
            RocksDB.loadLibrary();
            File dbDirectory = new File(dbPath);
            if (!dbDirectory.exists()) dbDirectory.mkdirs();

            RocksDbMptSchema.ColumnFamilies schema = RocksDbMptSchema.columnFamilies(namespaceOptions);

            java.util.List<byte[]> existingCfNames = RocksDB.listColumnFamilies(new org.rocksdb.Options().setCreateIfMissing(true), dbPath);

            // CF options with 1-byte prefix extractor for namespace support
            ColumnFamilyOptions columnFamilyOptions = new ColumnFamilyOptions();
            columnFamilyOptions.useFixedLengthPrefixExtractor(1);

            java.util.List<ColumnFamilyDescriptor> cfDescriptors = new java.util.ArrayList<>();
            int rootsColumnFamilyIndex = -1;
            for (byte[] cfName : existingCfNames) {
                cfDescriptors.add(new ColumnFamilyDescriptor(cfName, columnFamilyOptions));
                if (Arrays.equals(cfName, schema.roots().getBytes())) rootsColumnFamilyIndex = cfDescriptors.size() - 1;
            }
            boolean hasDefaultCf = false;
            for (byte[] cfName : existingCfNames)
                if (Arrays.equals(cfName, RocksDB.DEFAULT_COLUMN_FAMILY)) {
                    hasDefaultCf = true;
                    break;
                }
            if (!hasDefaultCf)
                cfDescriptors.add(0, new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions));
            if (rootsColumnFamilyIndex < 0) {
                cfDescriptors.add(new ColumnFamilyDescriptor(schema.roots().getBytes(), columnFamilyOptions));
                rootsColumnFamilyIndex = cfDescriptors.size() - 1;
            }

            java.util.List<ColumnFamilyHandle> cfHandles = new java.util.ArrayList<>();
            this.options = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
            this.db = RocksDB.open(this.options, dbPath, cfDescriptors, cfHandles);
            this.cfRoots = cfHandles.get(rootsColumnFamilyIndex);
            this.keyPrefixer = new KeyPrefixer(schema.keyPrefix());
            this.closeables = new java.util.ArrayList<>();
            this.closeables.add(columnFamilyOptions);
            this.closeables.add(cfRoots);
            this.closeables.add(options);
            this.closeables.add(db);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to open RocksDB RootsIndex", e);
        }
    }

    /**
     * Creates a RocksDbRootsIndex using an existing RocksDB instance.
     *
     * <p>This constructor is used when sharing a RocksDB instance across multiple
     * components. The caller retains responsibility for managing the database lifecycle.
     * This instance will not close the provided database or column family handle.</p>
     *
     * @param db      the existing RocksDB instance
     * @param cfRoots the column family handle for root storage
     */
    public RocksDbRootsIndex(RocksDB db, ColumnFamilyHandle cfRoots) {
        this(db, cfRoots, new KeyPrefixer((byte) 0x00));
    }

    public RocksDbRootsIndex(RocksDB db, ColumnFamilyHandle cfRoots, KeyPrefixer keyPrefixer) {
        this.db = db;
        this.cfRoots = cfRoots;
        this.keyPrefixer = keyPrefixer;
        this.options = null;
        this.closeables = java.util.Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stores the root hash with automatic maintenance of latest pointer and
     * version tracking. Updates are batched if within a batch context.</p>
     */
    @Override
    public void put(long versionOrSlot, byte[] rootHash) {
        byte[] versionKey = keyPrefixer.prefix(toBytes(versionOrSlot));
        byte[] prefixedLatestKey = keyPrefixer.prefix(LATEST_KEY);
        byte[] prefixedLastVersionKey = keyPrefixer.prefix(LAST_VERSION_KEY);
        try {
            WriteBatch currentBatch = TL_BATCH.get();
            if (currentBatch != null) {
                currentBatch.put(cfRoots, versionKey, rootHash);
                currentBatch.put(cfRoots, prefixedLatestKey, rootHash);
            } else {
                db.put(cfRoots, versionKey, rootHash);
                db.put(cfRoots, prefixedLatestKey, rootHash);
            }
            // Maintain LAST_VERSION_KEY for monotonic versioning utilities
            long currentMaxVersion = lastVersion();
            if (versionOrSlot > currentMaxVersion) {
                if (currentBatch != null) currentBatch.put(cfRoots, prefixedLastVersionKey, toBytes(versionOrSlot));
                else db.put(cfRoots, prefixedLastVersionKey, toBytes(versionOrSlot));
            }
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to store root hash", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] get(long versionOrSlot) {
        try {
            return db.get(cfRoots, keyPrefixer.prefix(toBytes(versionOrSlot)));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to retrieve root hash for version " + versionOrSlot, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] latest() {
        try {
            return db.get(cfRoots, keyPrefixer.prefix(LATEST_KEY));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to retrieve latest root hash", e);
        }
    }

    /**
     * Returns a sorted view (ascending) of all stored versions and their root hashes.
     *
     * <p>Special metadata keys (LATEST, VERSION) are automatically excluded from the results.
     * Only properly formatted 8-byte version keys are included.</p>
     *
     * @return a NavigableMap of all version-root pairs, sorted by version
     */
    public java.util.NavigableMap<Long, byte[]> listAll() {
        java.util.NavigableMap<Long, byte[]> versionRootMap = new java.util.TreeMap<>();
        byte[] prefixedLatestKey = keyPrefixer.prefix(LATEST_KEY);
        byte[] prefixedLastVersionKey = keyPrefixer.prefix(LAST_VERSION_KEY);
        try (ReadOptions readOptions = keyPrefixer.createPrefixReadOptions();
             RocksIterator iterator = db.newIterator(cfRoots, readOptions)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                byte[] prefixedKey = iterator.key();
                if (java.util.Arrays.equals(prefixedKey, prefixedLatestKey) || java.util.Arrays.equals(prefixedKey, prefixedLastVersionKey))
                    continue;
                byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                if (unprefixedKey.length != 8) continue; // skip unknown keys
                long version = java.nio.ByteBuffer.wrap(unprefixedKey).getLong();
                byte[] rootHash = iterator.value();
                versionRootMap.put(version, rootHash);
            }
        }
        return versionRootMap;
    }

    /**
     * Returns all versions in the specified range [fromInclusive, toInclusive].
     *
     * <p>Uses RocksDB's efficient seek operation to start at the desired range
     * and iterates until the upper bound. Special metadata keys are excluded.</p>
     *
     * @param fromInclusive the starting version (inclusive)
     * @param toInclusive   the ending version (inclusive)
     * @return a NavigableMap of version-root pairs within the range, or empty if from &gt; to
     */
    public java.util.NavigableMap<Long, byte[]> listRange(long fromInclusive, long toInclusive) {
        java.util.NavigableMap<Long, byte[]> rangeResults = new java.util.TreeMap<>();
        if (fromInclusive > toInclusive) return rangeResults;

        byte[] startKey = keyPrefixer.prefix(toBytes(fromInclusive));
        byte[] prefixedLatestKey = keyPrefixer.prefix(LATEST_KEY);
        byte[] prefixedLastVersionKey = keyPrefixer.prefix(LAST_VERSION_KEY);
        try (ReadOptions readOptions = keyPrefixer.createPrefixReadOptions();
             RocksIterator iterator = db.newIterator(cfRoots, readOptions)) {
            for (iterator.seek(startKey); iterator.isValid(); iterator.next()) {
                byte[] prefixedKey = iterator.key();
                if (java.util.Arrays.equals(prefixedKey, prefixedLatestKey) || java.util.Arrays.equals(prefixedKey, prefixedLastVersionKey))
                    continue;
                byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                if (unprefixedKey.length != 8) continue;
                long version = java.nio.ByteBuffer.wrap(unprefixedKey).getLong();
                if (version > toInclusive) break;
                rangeResults.put(version, iterator.value());
            }
        }
        return rangeResults;
    }

    /**
     * Returns the highest version number that has been stored.
     *
     * <p>This method provides efficient access to the latest version without
     * scanning all entries. The version counter is maintained automatically
     * during put operations.</p>
     *
     * @return the last stored version, or -1 if no versions have been stored
     */
    public long lastVersion() {
        try {
            byte[] versionBytes = db.get(cfRoots, keyPrefixer.prefix(LAST_VERSION_KEY));
            if (versionBytes == null || versionBytes.length != 8) return -1L;
            return ByteBuffer.wrap(versionBytes).getLong();
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to retrieve last version", e);
        }
    }

    /**
     * Atomically increments and returns the next available version number.
     *
     * <p>This method provides a thread-safe way to obtain monotonically increasing
     * version numbers for new entries. The operation is atomic at the RocksDB level.</p>
     *
     * @return the next version number to use
     */
    public long nextVersion() {
        long nextVersionNumber = lastVersion() + 1;
        try {
            WriteBatch currentBatch = TL_BATCH.get();
            byte[] prefixedKey = keyPrefixer.prefix(LAST_VERSION_KEY);
            if (currentBatch != null) currentBatch.put(cfRoots, prefixedKey, toBytes(nextVersionNumber));
            else db.put(cfRoots, prefixedKey, toBytes(nextVersionNumber));
            return nextVersionNumber;
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to generate next version", e);
        }
    }

    /**
     * Executes the provided work within a batch context.
     *
     * <p>All RootsIndex operations performed within the work block will be staged
     * in the provided WriteBatch for atomic execution.</p>
     *
     * @param <T>   the return type of the work function
     * @param batch the WriteBatch to stage operations in
     * @param work  the work to execute within the batch context
     * @return the result of the work function
     * @throws RuntimeException if the work function throws an exception
     */
    public <T> T withBatch(WriteBatch batch, java.util.concurrent.Callable<T> work) {
        TL_BATCH.set(batch);
        try {
            return work.call();
        } catch (Exception e) {
            throw new RuntimeException("Batch operation failed", e);
        } finally {
            TL_BATCH.remove();
        }
    }

    /**
     * Converts a long value to its big-endian 8-byte representation.
     *
     * <p>Using big-endian ensures proper lexicographic ordering of version keys
     * in RocksDB, enabling efficient range queries and iteration.</p>
     *
     * @param version the long value to convert
     * @return the 8-byte big-endian representation
     */
    private static byte[] toBytes(long version) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(version);
        return buffer.array();
    }

    /**
     * Closes this roots index and releases all associated resources.
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
