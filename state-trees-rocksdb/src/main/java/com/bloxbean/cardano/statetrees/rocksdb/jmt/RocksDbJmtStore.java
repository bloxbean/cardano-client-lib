package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JmtEncoding;
import com.bloxbean.cardano.statetrees.jmt.JmtNode;
import com.bloxbean.cardano.statetrees.jmt.NodeKey;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Status;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtSchema.*;

/**
 * RocksDB-backed implementation of {@link JmtStore}.
 */
public final class RocksDbJmtStore implements JmtStore {

    /**
     * Returns the column family names used by JMT when the supplied namespace/prefix is applied.
     *
     * @param namespace optional namespace to prefix to the internal column family names (may be {@code null})
     * @return resolved column family names
     */
    public static ColumnFamilies columnFamilies(String namespace) {
        RocksDbJmtSchema.ColumnFamilies names = RocksDbJmtSchema.columnFamilies(namespace);
        return new ColumnFamilies(names.nodes(), names.values(), names.roots(), names.stale());
    }

    public static final class ColumnFamilies {
        private final String nodes;
        private final String values;
        private final String roots;
        private final String stale;

        private ColumnFamilies(String nodes, String values, String roots, String stale) {
            this.nodes = nodes;
            this.values = values;
            this.roots = roots;
            this.stale = stale;
        }

        public String nodes() {
            return nodes;
        }

        public String values() {
            return values;
        }

        public String roots() {
            return roots;
        }

        public String stale() {
            return stale;
        }
    }

    public enum ValuePrunePolicy {SAFE, AGGRESSIVE}

    /** Snapshot of selected RocksDB DB-wide properties for observability. */
    public static final class DbProperties {
        private final long pendingCompactionBytes;
        private final int runningCompactions;
        private final int runningFlushes;
        private final long curSizeActiveMemTable;
        private final long curSizeAllMemTables;
        private final long numImmutableMemTables;

        public DbProperties(long pendingCompactionBytes,
                            int runningCompactions,
                            int runningFlushes,
                            long curSizeActiveMemTable,
                            long curSizeAllMemTables,
                            long numImmutableMemTables) {
            this.pendingCompactionBytes = pendingCompactionBytes;
            this.runningCompactions = runningCompactions;
            this.runningFlushes = runningFlushes;
            this.curSizeActiveMemTable = curSizeActiveMemTable;
            this.curSizeAllMemTables = curSizeAllMemTables;
            this.numImmutableMemTables = numImmutableMemTables;
        }

        public long pendingCompactionBytes() { return pendingCompactionBytes; }
        public int runningCompactions() { return runningCompactions; }
        public int runningFlushes() { return runningFlushes; }
        public long curSizeActiveMemTable() { return curSizeActiveMemTable; }
        public long curSizeAllMemTables() { return curSizeAllMemTables; }
        public long numImmutableMemTables() { return numImmutableMemTables; }
    }

    public static final class Options {
        private final String namespace;
        private final boolean enableRollbackIndex;
        private final ValuePrunePolicy prunePolicy;
        private final boolean disableWalForBatches;
        private final boolean syncOnCommit;
        private final boolean syncOnPrune;
        private final boolean syncOnTruncate;
        private final RocksDbConfig rocksDbConfig;

        private Options(Builder builder) {
            this.namespace = builder.namespace;
            this.enableRollbackIndex = builder.enableRollbackIndex;
            this.prunePolicy = builder.prunePolicy;
            this.disableWalForBatches = builder.disableWalForBatches;
            this.syncOnCommit = builder.syncOnCommit;
            this.syncOnPrune = builder.syncOnPrune;
            this.syncOnTruncate = builder.syncOnTruncate;
            this.rocksDbConfig = builder.rocksDbConfig != null ? builder.rocksDbConfig : RocksDbConfig.balanced();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Options defaults() {
            return new Builder().build();
        }

        public String namespace() {
            return namespace;
        }

        public boolean enableRollbackIndex() {
            return enableRollbackIndex;
        }

        public ValuePrunePolicy prunePolicy() {
            return prunePolicy;
        }

        public boolean disableWalForBatches() {
            return disableWalForBatches;
        }

        /** If true, set WriteOptions.sync(true) when flushing commit batches. */
        public boolean syncOnCommit() {
            return syncOnCommit;
        }

        /** If true, set WriteOptions.sync(true) for prune writes. */
        public boolean syncOnPrune() {
            return syncOnPrune;
        }

        /** If true, set WriteOptions.sync(true) for truncate writes. */
        public boolean syncOnTruncate() {
            return syncOnTruncate;
        }

        /** Returns the RocksDB configuration settings. */
        public RocksDbConfig rocksDbConfig() {
            return rocksDbConfig;
        }

        public static final class Builder {
            private String namespace;
            private boolean enableRollbackIndex;
            private ValuePrunePolicy prunePolicy = ValuePrunePolicy.SAFE;
            private boolean disableWalForBatches = false;
            private boolean syncOnCommit = true;
            private boolean syncOnPrune = true;
            private boolean syncOnTruncate = true;
            private RocksDbConfig rocksDbConfig;

            public Builder namespace(String namespace) {
                this.namespace = namespace;
                return this;
            }

            public Builder enableRollbackIndex(boolean enableRollbackIndex) {
                this.enableRollbackIndex = enableRollbackIndex;
                return this;
            }

            public Builder prunePolicy(ValuePrunePolicy prunePolicy) {
                this.prunePolicy = Objects.requireNonNull(prunePolicy, "prunePolicy");
                return this;
            }

            /** Disable WAL in WriteOptions for commit batches (unsafe; for benchmarking only). */
            public Builder disableWalForBatches(boolean disableWal) {
                this.disableWalForBatches = disableWal;
                return this;
            }

            /** Enable/disable WriteOptions.sync for commit batches (default true for durability). */
            public Builder syncOnCommit(boolean sync) {
                this.syncOnCommit = sync;
                return this;
            }

            /** Enable/disable WriteOptions.sync for prune operations (default true for durability). */
            public Builder syncOnPrune(boolean sync) {
                this.syncOnPrune = sync;
                return this;
            }

            /** Enable/disable WriteOptions.sync for truncate operations (default true for durability). */
            public Builder syncOnTruncate(boolean sync) {
                this.syncOnTruncate = sync;
                return this;
            }

            /**
             * Sets the RocksDB configuration to use. If not specified, defaults to {@link RocksDbConfig#balanced()}.
             *
             * @param rocksDbConfig configuration for RocksDB performance tuning
             * @return this builder
             */
            public Builder rocksDbConfig(RocksDbConfig rocksDbConfig) {
                this.rocksDbConfig = rocksDbConfig;
                return this;
            }

            public Options build() {
                return new Options(this);
            }
        }
    }

    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final ColumnFamilyHandle cfValues;
    private final ColumnFamilyHandle cfRoots;
    private final ColumnFamilyHandle cfStale;
    private final ColumnFamilyHandle cfNodesByVersion;
    private final ColumnFamilyHandle cfValuesByVersion;
    private final RocksDbJmtSchema.ColumnFamilies names;
    private final boolean ownsDb;
    private final List<ColumnFamilyHandle> ownedHandles;
    private final List<AutoCloseable> ownedResources;
    private final Options storeOptions;

    private static final int KEY_HASH_LENGTH = 32;
    private static final int VALUE_KEY_LENGTH = KEY_HASH_LENGTH + Long.BYTES;
    private static final byte VALUE_PRESENT = 1;
    private static final byte VALUE_TOMBSTONE = 0;
    private static final byte[] INDEX_PLACEHOLDER = new byte[0];

    private static byte[] valueKey(byte[] keyHash, long version) {
        byte[] key = new byte[VALUE_KEY_LENGTH];
        System.arraycopy(keyHash, 0, key, 0, KEY_HASH_LENGTH);
        ByteBuffer.wrap(key, KEY_HASH_LENGTH, Long.BYTES).putLong(version);
        return key;
    }

    private static byte[] valueVersionKey(long version, byte[] keyHash) {
        byte[] key = new byte[Long.BYTES + KEY_HASH_LENGTH];
        ByteBuffer.wrap(key).putLong(version);
        System.arraycopy(keyHash, 0, key, Long.BYTES, KEY_HASH_LENGTH);
        return key;
    }

    private static byte[] nodeVersionKey(long version, byte[] nodeKeyBytes) {
        byte[] key = new byte[Long.BYTES + nodeKeyBytes.length];
        ByteBuffer.wrap(key).putLong(version);
        System.arraycopy(nodeKeyBytes, 0, key, Long.BYTES, nodeKeyBytes.length);
        return key;
    }

    private static byte[] encodeValue(byte[] value) {
        byte[] encoded = new byte[1 + value.length];
        encoded[0] = VALUE_PRESENT;
        System.arraycopy(value, 0, encoded, 1, value.length);
        return encoded;
    }

    private static byte[] tombstoneValue() {
        return new byte[]{VALUE_TOMBSTONE};
    }

    private static boolean isTombstone(byte[] encoded) {
        return encoded.length == 0 || encoded[0] == VALUE_TOMBSTONE;
    }

    private static ValueEntry decodeValue(byte[] encoded, long version) {
        if (isTombstone(encoded)) {
            return ValueEntry.tombstone(version);
        }
        byte[] value = Arrays.copyOfRange(encoded, 1, encoded.length);
        return ValueEntry.value(value, version);
    }

    private static boolean hasKeyPrefix(byte[] rawKey, byte[] keyHash) {
        if (rawKey.length < KEY_HASH_LENGTH) return false;
        for (int i = 0; i < KEY_HASH_LENGTH; i++) {
            if (rawKey[i] != keyHash[i]) return false;
        }
        return true;
    }

    private static long decodeVersion(byte[] rawKey) {
        return ByteBuffer.wrap(rawKey, KEY_HASH_LENGTH, Long.BYTES).getLong();
    }

    private static ReadOptions prefixReadOptions() {
        return new ReadOptions().setPrefixSameAsStart(true);
    }

    private static ColumnFamilyOptions selectOptions(String cfName,
                                                     String defaultName,
                                                     RocksDbJmtSchema.ColumnFamilies names,
                                                     ColumnFamilyOptions defaultCfOptions,
                                                     ColumnFamilyOptions valuesCfOptions,
                                                     ColumnFamilyOptions indexCfOptions,
                                                     boolean rollbackEnabled) {
        if (cfName.equals(names.values())) {
            return valuesCfOptions;
        }
        if (rollbackEnabled && (cfName.equals(names.nodesByVersion()) || cfName.equals(names.valuesByVersion()))) {
            return indexCfOptions;
        }
        if (cfName.equals(defaultName)) {
            return defaultCfOptions;
        }
        return defaultCfOptions;
    }

    private static final class ValueEntry {
        final byte[] value;
        final boolean tombstone;
        final long version;

        private ValueEntry(byte[] value, boolean tombstone, long version) {
            this.value = value;
            this.tombstone = tombstone;
            this.version = version;
        }

        static ValueEntry value(byte[] value, long version) {
            return new ValueEntry(value, false, version);
        }

        static ValueEntry tombstone(long version) {
            return new ValueEntry(null, true, version);
        }
    }

    private ValueEntry seekValue(byte[] keyHash, long version) throws RocksDBException {
        byte[] seekKey = valueKey(keyHash, version);
        try (ReadOptions options = prefixReadOptions();
             RocksIterator iterator = db.newIterator(cfValues, options)) {
            iterator.seekForPrev(seekKey);
            while (iterator.isValid()) {
                byte[] rawKey = iterator.key();
                if (!hasKeyPrefix(rawKey, keyHash)) {
                    return null;
                }
                long foundVersion = decodeVersion(rawKey);
                byte[] encoded = iterator.value();
                return decodeValue(encoded, foundVersion);
            }
        }
        return null;
    }

    private int pruneValues(long versionInclusive) {
        if (versionInclusive < 0) {
            return 0;
        }
        int pruned = 0;
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions();
             ReadOptions readOptions = new ReadOptions().setTotalOrderSeek(true);
             RocksIterator iterator = db.newIterator(cfValues, readOptions)) {
            if (storeOptions != null && storeOptions.syncOnPrune()) {
                writeOptions.setSync(true);
            }
            byte[] currentKeyHash = null;
            java.util.List<byte[]> deletions = new java.util.ArrayList<>();
            byte[] sentinel = null;
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                byte[] rawKey = iterator.key();
                byte[] keyHash = Arrays.copyOfRange(rawKey, 0, KEY_HASH_LENGTH);
                long version = decodeVersion(rawKey);

                if (currentKeyHash == null || !Arrays.equals(currentKeyHash, keyHash)) {
                    pruned += applyValueDeletions(deletions, sentinel, batch);
                    deletions.clear();
                    sentinel = null;
                    currentKeyHash = keyHash;
                }

                if (Long.compareUnsigned(version, versionInclusive) <= 0) {
                    deletions.add(Arrays.copyOf(rawKey, rawKey.length));
                    if (storeOptions.prunePolicy() == ValuePrunePolicy.SAFE) {
                        sentinel = Arrays.copyOf(rawKey, rawKey.length);
                    }
                }

            }

            pruned += applyValueDeletions(deletions, sentinel, batch);

            if (pruned > 0) {
                db.write(writeOptions, batch);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to prune JMT values", e);
        }
        return pruned;
    }

    private int applyValueDeletions(java.util.List<byte[]> deletions, byte[] sentinel, WriteBatch batch) throws RocksDBException {
        if (deletions.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (byte[] rawKey : deletions) {
            if (storeOptions.prunePolicy() == ValuePrunePolicy.SAFE && sentinel != null && Arrays.equals(rawKey, sentinel)) {
                continue;
            }
            batch.delete(cfValues, rawKey);
            if (cfValuesByVersion != null) {
                long version = decodeVersion(rawKey);
                byte[] keyHash = Arrays.copyOfRange(rawKey, 0, KEY_HASH_LENGTH);
                batch.delete(cfValuesByVersion, valueVersionKey(version, keyHash));
            }
            removed++;
        }
        return removed;
    }

    /**
     * Opens (or creates) a dedicated RocksDB instance at the supplied path with default column
     * families for the JMT store. The store owns the database lifecycle.
     */
    public RocksDbJmtStore(String dbPath) {
        this(openStandalone(dbPath, Options.defaults()));
    }

    /**
     * Opens (or creates) a dedicated RocksDB instance with a namespace applied to the column
     * family names. The store owns the database lifecycle.
     */
    public RocksDbJmtStore(String dbPath, String namespace) {
        this(openStandalone(dbPath, Options.builder().namespace(namespace).build()));
    }

    /**
     * Factory method mirroring {@link #RocksDbJmtStore(String)}.
     */
    public static RocksDbJmtStore open(String dbPath) {
        return new RocksDbJmtStore(openStandalone(dbPath, Options.defaults()));
    }

    /**
     * Factory method mirroring {@link #RocksDbJmtStore(String, String)}.
     */
    public static RocksDbJmtStore open(String dbPath, String namespace) {
        return new RocksDbJmtStore(openStandalone(dbPath, Options.builder().namespace(namespace).build()));
    }

    public static RocksDbJmtStore open(String dbPath, Options options) {
        return new RocksDbJmtStore(openStandalone(dbPath, options));
    }

    /**
     * Attaches the store to an already opened RocksDB instance. Column families are created on
     * demand using the provided namespace. The caller retains ownership of the {@link RocksDB}
     * instance.
     *
     * @param db        shared RocksDB instance
     * @param namespace optional namespace/prefix for column family names
     * @return a store bound to the supplied database
     */
    public static RocksDbJmtStore attach(RocksDB db, String namespace) {
        return attach(db, Options.builder().namespace(namespace).build(), Map.of());
    }

    /**
     * Attaches the store to an already opened RocksDB instance using existing column-family
     * handles when provided. Any missing handle will cause the method to create the column family.
     *
     * @param db              shared RocksDB instance
     * @param namespace       optional namespace/prefix for column family names
     * @param existingHandles map of column family names to handles that are already managed by the caller
     * @return a store bound to the supplied database
     */
    public static RocksDbJmtStore attach(RocksDB db,
                                         String namespace,
                                         Map<String, ColumnFamilyHandle> existingHandles) {
        return attach(db, Options.builder().namespace(namespace).build(), existingHandles);
    }

    public static RocksDbJmtStore attach(RocksDB db,
                                         Options options,
                                         Map<String, ColumnFamilyHandle> existingHandles) {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(options, "options");
        try {
            return new RocksDbJmtStore(attachInternal(db, options, existingHandles));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to attach RocksDbJmtStore", e);
        }
    }

    public static RocksDbJmtStore attach(RocksDB db, Options options) {
        return attach(db, options, Map.of());
    }

    private RocksDbJmtStore(Init init) {
        this.db = init.db;
        this.cfNodes = init.nodes;
        this.cfValues = init.values;
        this.cfRoots = init.roots;
        this.cfStale = init.stale;
        this.cfNodesByVersion = init.nodesByVersion;
        this.cfValuesByVersion = init.valuesByVersion;
        this.names = init.names;
        this.ownsDb = init.ownsDb;
        this.ownedHandles = init.ownedHandles;
        this.ownedResources = init.ownedResources;
        this.storeOptions = init.options;
    }

    private static void ensureDescriptor(String key, byte[] nameBytes, ColumnFamilyOptions cfOptions,
                                         List<ColumnFamilyDescriptor> descriptors,
                                         Map<String, Integer> indexes) {
        if (!indexes.containsKey(key)) {
            indexes.put(key, descriptors.size());
            descriptors.add(new ColumnFamilyDescriptor(nameBytes, cfOptions));
        }
    }

    @Override
    public Optional<VersionedRoot> latestRoot() {
        try {
            byte[] root = db.get(cfRoots, LATEST_ROOT_KEY);
            if (root == null) {
                return Optional.empty();
            }
            byte[] versionBytes = db.get(cfRoots, LATEST_VERSION_KEY);
            long version = versionBytes == null ? -1 : ByteBuffer.wrap(versionBytes).getLong();
            if (version < 0) {
                // Fallback to scanning the last entry if version metadata is missing.
                try (ReadOptions readOptions = new ReadOptions();
                     RocksIterator iterator = db.newIterator(cfRoots, readOptions)) {
                    iterator.seekToLast();
                    while (iterator.isValid()) {
                        byte[] key = iterator.key();
                        if (Arrays.equals(key, LATEST_ROOT_KEY) || Arrays.equals(key, LATEST_VERSION_KEY)) {
                            iterator.prev();
                            continue;
                        }
                        version = ByteBuffer.wrap(key).getLong();
                        root = iterator.value();
                        break;
                    }
                }
            }
            if (version < 0) {
                return Optional.empty();
            }
            return Optional.of(new VersionedRoot(version, root));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read latest JMT root", e);
        }
    }

    @Override
    public Optional<byte[]> rootHash(long version) {
        try {
            byte[] root = db.get(cfRoots, versionKey(version));
            return root == null ? Optional.empty() : Optional.of(root.clone());
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read JMT root for version " + version, e);
        }
    }

    @Override
    public Optional<NodeEntry> getNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        byte[] searchKey = NodeKey.of(path, version).toBytes();
        try (ReadOptions options = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfNodes, options)) {
            iterator.seekForPrev(searchKey);
            while (iterator.isValid()) {
                byte[] keyBytes = iterator.key();
                NodeKey nodeKey = NodeKey.fromBytes(keyBytes);
                int cmp = comparePath(nodeKey.path(), path);
                if (cmp < 0) {
                    break;
                }
                if (cmp == 0 && Long.compareUnsigned(nodeKey.version(), version) <= 0) {
                    byte[] value = iterator.value();
                    JmtNode node = JmtEncoding.decode(value);
                    return Optional.of(new NodeEntry(nodeKey, node));
                }
                iterator.prev();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<JmtNode> getNode(NodeKey nodeKey) {
        Objects.requireNonNull(nodeKey, "nodeKey");
        try {
            byte[] nodeBytes = db.get(cfNodes, nodeKey.toBytes());
            return nodeBytes == null ? Optional.empty() : Optional.of(JmtEncoding.decode(nodeBytes));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to load JMT node", e);
        }
    }

    @Override
    public Optional<NodeEntry> floorNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        byte[] searchKey = NodeKey.of(path, version).toBytes();
        try (ReadOptions options = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfNodes, options)) {
            iterator.seekForPrev(searchKey);
            while (iterator.isValid()) {
                byte[] keyBytes = iterator.key();
                NodeKey nodeKey = NodeKey.fromBytes(keyBytes);
                if (comparePath(nodeKey.path(), path) <= 0 &&
                        Long.compareUnsigned(nodeKey.version(), version) <= 0) {
                    JmtNode node = JmtEncoding.decode(iterator.value());
                    return Optional.of(new NodeEntry(nodeKey, node));
                }
                iterator.prev();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<NodeEntry> ceilingNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        byte[] searchKey = NodeKey.of(path, version).toBytes();
        try (ReadOptions options = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfNodes, options)) {
            iterator.seek(searchKey);
            while (iterator.isValid()) {
                byte[] keyBytes = iterator.key();
                NodeKey nodeKey = NodeKey.fromBytes(keyBytes);
                if (comparePath(nodeKey.path(), path) >= 0 &&
                        Long.compareUnsigned(nodeKey.version(), version) <= 0) {
                    JmtNode node = JmtEncoding.decode(iterator.value());
                    return Optional.of(new NodeEntry(nodeKey, node));
                }
                iterator.next();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> getValue(byte[] keyHash) {
        Objects.requireNonNull(keyHash, "keyHash");
        try {
            ValueEntry entry = seekValue(keyHash, -1L);
            if (entry == null || entry.tombstone) {
                return Optional.empty();
            }
            return Optional.of(entry.value.clone());
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to load JMT value", e);
        }
    }

    @Override
    public Optional<byte[]> getValueAt(byte[] keyHash, long version) {
        Objects.requireNonNull(keyHash, "keyHash");
        try {
            ValueEntry entry = seekValue(keyHash, version);
            if (entry == null || entry.tombstone) {
                return Optional.empty();
            }
            return Optional.of(entry.value.clone());
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to load JMT value at version", e);
        }
    }

    @Override
    public CommitBatch beginCommit(long version, CommitConfig config) {
        Objects.requireNonNull(config, "config");
        return new RocksCommitBatch(version);
    }

    public void applyCommit(JellyfishMerkleTree.CommitResult result) {
        Objects.requireNonNull(result, "result");
        try (CommitBatch batch = beginCommit(result.version(), CommitConfig.defaults())) {
            for (Map.Entry<NodeKey, JmtNode> entry : result.nodes().entrySet()) {
                batch.putNode(entry.getKey(), entry.getValue());
            }
            for (NodeKey stale : result.staleNodes()) {
                batch.markStale(stale);
            }
            for (JellyfishMerkleTree.CommitResult.ValueOperation op : result.valueOperations()) {
                switch (op.type()) {
                    case PUT:
                        batch.putValue(op.keyHash(), op.value());
                        break;
                    case DELETE:
                        batch.deleteValue(op.keyHash());
                        break;
                    default:
                        throw new IllegalStateException("Unhandled value operation: " + op.type());
                }
            }
            batch.setRootHash(result.rootHash());
            batch.commit();
        }
    }

    @Override
    public List<NodeKey> staleNodesUpTo(long versionInclusive) {
        List<NodeKey> results = new ArrayList<>();
        try (ReadOptions readOptions = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfStale, readOptions)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                byte[] key = iterator.key();
                long staleSince = ByteBuffer.wrap(key, 0, 8).getLong();
                if (staleSince > versionInclusive) break;
                byte[] nodeKeyBytes = Arrays.copyOfRange(key, 8, key.length);
                results.add(NodeKey.fromBytes(nodeKeyBytes));
            }
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public int pruneUpTo(long versionInclusive) {
        int nodesPruned = 0;
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions();
             ReadOptions readOptions = new ReadOptions();
             RocksIterator iterator = db.newIterator(cfStale, readOptions)) {
            if (storeOptions != null && storeOptions.syncOnPrune()) {
                writeOptions.setSync(true);
            }
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                byte[] key = iterator.key();
                long staleSince = ByteBuffer.wrap(key, 0, 8).getLong();
                if (staleSince > versionInclusive) {
                    break;
                }
                byte[] nodeKeyBytes = Arrays.copyOfRange(key, 8, key.length);
                batch.delete(cfNodes, nodeKeyBytes);
                if (cfNodesByVersion != null) {
                    NodeKey nodeKey = NodeKey.fromBytes(nodeKeyBytes);
                    batch.delete(cfNodesByVersion, nodeVersionKey(nodeKey.version(), nodeKeyBytes));
                }
                batch.delete(cfStale, key);
                nodesPruned++;
            }
            if (nodesPruned > 0) {
                db.write(writeOptions, batch);
            }
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to prune JMT nodes", e);
        }
        int valuesPruned = pruneValues(versionInclusive);
        return nodesPruned + valuesPruned;
    }

    @Override
    public void truncateAfter(long versionExclusive) {
        if (!storeOptions.enableRollbackIndex()) {
            throw new UnsupportedOperationException("Rollback indices are disabled for this store");
        }
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {
            if (storeOptions != null && storeOptions.syncOnTruncate()) {
                writeOptions.setSync(true);
            }

            // Roots
            try (RocksIterator iterator = db.newIterator(cfRoots)) {
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    byte[] key = iterator.key();
                    if (Arrays.equals(key, LATEST_ROOT_KEY) || Arrays.equals(key, LATEST_VERSION_KEY)) {
                        continue;
                    }
                    long version = ByteBuffer.wrap(key).getLong();
                    if (Long.compareUnsigned(version, versionExclusive) > 0) {
                        batch.delete(cfRoots, key);
                    }
                }
            }

            byte[] retainedRoot = db.get(cfRoots, versionKey(versionExclusive));
            if (retainedRoot != null) {
                batch.put(cfRoots, LATEST_ROOT_KEY, retainedRoot);
                batch.put(cfRoots, LATEST_VERSION_KEY, versionKey(versionExclusive));
            } else {
                batch.delete(cfRoots, LATEST_ROOT_KEY);
                batch.delete(cfRoots, LATEST_VERSION_KEY);
            }

            // Stale markers
            try (RocksIterator iterator = db.newIterator(cfStale)) {
                for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                    byte[] key = iterator.key();
                    long staleSince = ByteBuffer.wrap(key, 0, 8).getLong();
                    if (Long.compareUnsigned(staleSince, versionExclusive) > 0) {
                        batch.delete(cfStale, key);
                    }
                }
            }

            // Nodes by version
            if (cfNodesByVersion != null) {
                try (RocksIterator iterator = db.newIterator(cfNodesByVersion)) {
                    iterator.seek(versionKey(versionExclusive + 1));
                    while (iterator.isValid()) {
                        byte[] key = iterator.key();
                        long version = ByteBuffer.wrap(key, 0, Long.BYTES).getLong();
                        if (Long.compareUnsigned(version, versionExclusive) <= 0) {
                            break;
                        }
                        byte[] nodeKeyBytes = Arrays.copyOfRange(key, Long.BYTES, key.length);
                        batch.delete(cfNodes, nodeKeyBytes);
                        batch.delete(cfNodesByVersion, key);
                        iterator.next();
                    }
                }
            }

            // Values by version
            if (cfValuesByVersion != null) {
                try (RocksIterator iterator = db.newIterator(cfValuesByVersion)) {
                    iterator.seek(versionKey(versionExclusive + 1));
                    while (iterator.isValid()) {
                        byte[] key = iterator.key();
                        long version = ByteBuffer.wrap(key, 0, Long.BYTES).getLong();
                        if (Long.compareUnsigned(version, versionExclusive) <= 0) {
                            break;
                        }
                        byte[] keyHash = Arrays.copyOfRange(key, Long.BYTES, key.length);
                        batch.delete(cfValues, valueKey(keyHash, version));
                        batch.delete(cfValuesByVersion, key);
                        iterator.next();
                    }
                }
            }

            db.write(writeOptions, batch);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to truncate JMT store", e);
        }
    }

    @Override
    public void close() {
        for (ColumnFamilyHandle handle : ownedHandles) {
            closeQuietly(handle);
        }
        if (ownsDb) {
            closeQuietly(db);
        }
        for (AutoCloseable resource : ownedResources) {
            closeQuietly(resource);
        }
    }

    /**
     * Returns a snapshot of selected RocksDB properties useful for monitoring compaction/flush pressure.
     * Values default to 0 if a property is unavailable.
     */
    public DbProperties sampleDbProperties() {
        try {
            long pending = parseLong(db.getProperty("rocksdb.estimate-pending-compaction-bytes"));
            int runningComp = (int) parseLong(db.getProperty("rocksdb.num-running-compactions"));
            int runningFlush = (int) parseLong(db.getProperty("rocksdb.num-running-flushes"));
            long activeMem = parseLong(db.getProperty("rocksdb.cur-size-active-mem-table"));
            long allMem = parseLong(db.getProperty("rocksdb.cur-size-all-mem-tables"));
            long imm = parseLong(db.getProperty("rocksdb.num-immutable-mem-table"));
            return new DbProperties(pending, runningComp, runningFlush, activeMem, allMem, imm);
        } catch (Exception e) {
            return new DbProperties(0, 0, 0, 0, 0, 0);
        }
    }

    private static long parseLong(String s) {
        if (s == null) return 0L;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            // Some builds return key=value style; attempt to strip non-digits
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0L;
            try { return Long.parseLong(digits); } catch (NumberFormatException ex) { return 0L; }
        }
    }

    private int comparePath(NibblePath a, NibblePath b) {
        int[] an = a.getNibbles();
        int[] bn = b.getNibbles();
        int len = Math.min(an.length, bn.length);
        for (int i = 0; i < len; i++) {
            int diff = Integer.compare(an[i], bn[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return Integer.compare(an.length, bn.length);
    }

    private static byte[] staleKey(long version, NodeKey nodeKey) {
        byte[] nodeBytes = nodeKey.toBytes();
        byte[] key = new byte[8 + nodeBytes.length];
        ByteBuffer.wrap(key).putLong(version).put(nodeBytes);
        return key;
    }

    private static byte[] versionKey(long version) {
        return ByteBuffer.allocate(Long.BYTES).putLong(version).array();
    }

    private final class RocksCommitBatch implements CommitBatch {

        private final long version;
        private final WriteBatch batch = new WriteBatch();
        private final WriteOptions writeOptions = new WriteOptions();
        private boolean committed;
        private byte[] rootHash;

        private RocksCommitBatch(long version) {
            this.version = version;
            if (storeOptions != null && storeOptions.disableWalForBatches()) {
                this.writeOptions.setDisableWAL(true);
            }
            if (storeOptions != null && storeOptions.syncOnCommit()) {
                this.writeOptions.setSync(true);
            }
        }

        @Override
        public void putNode(NodeKey nodeKey, JmtNode node) {
            Objects.requireNonNull(nodeKey, "nodeKey");
            Objects.requireNonNull(node, "node");
            try {
                byte[] nodeBytes = nodeKey.toBytes();
                batch.put(cfNodes, nodeBytes, node.encode());
                if (cfNodesByVersion != null) {
                    batch.put(cfNodesByVersion, nodeVersionKey(nodeKey.version(), nodeBytes), INDEX_PLACEHOLDER);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage node write", e);
            }
        }

        @Override
        public void markStale(NodeKey nodeKey) {
            Objects.requireNonNull(nodeKey, "nodeKey");
            try {
                batch.put(cfStale, staleKey(version, nodeKey), new byte[0]);
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage stale marker", e);
            }
        }

        @Override
        public void putValue(byte[] keyHash, byte[] value) {
            Objects.requireNonNull(keyHash, "keyHash");
            Objects.requireNonNull(value, "value");
            try {
                byte[] valueKey = valueKey(keyHash, version);
                batch.put(cfValues, valueKey, encodeValue(value));
                if (cfValuesByVersion != null) {
                    batch.put(cfValuesByVersion, valueVersionKey(version, keyHash), INDEX_PLACEHOLDER);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage value write", e);
            }
        }

        @Override
        public void deleteValue(byte[] keyHash) {
            Objects.requireNonNull(keyHash, "keyHash");
            try {
                byte[] valueKey = valueKey(keyHash, version);
                batch.put(cfValues, valueKey, tombstoneValue());
                if (cfValuesByVersion != null) {
                    batch.put(cfValuesByVersion, valueVersionKey(version, keyHash), INDEX_PLACEHOLDER);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage value delete", e);
            }
        }

        @Override
        public void setRootHash(byte[] rootHash) {
            this.rootHash = rootHash == null ? null : rootHash.clone();
        }

        @Override
        public void commit() {
            if (committed) {
                throw new IllegalStateException("Commit already applied");
            }
            try {
                if (rootHash != null) {
                    batch.put(cfRoots, versionKey(version), rootHash);
                    batch.put(cfRoots, LATEST_ROOT_KEY, rootHash);
                    batch.put(cfRoots, LATEST_VERSION_KEY, versionKey(version));
                }
                db.write(writeOptions, batch);
                committed = true;
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to flush JMT batch", e);
            } finally {
                closeInternal();
            }
        }

        @Override
        public void close() {
            if (!committed) {
                closeInternal();
            }
        }

        private void closeInternal() {
            try {
                batch.close();
            } catch (Exception ignored) {
            }
            try {
                writeOptions.close();
            } catch (Exception ignored) {
            }
            committed = true;
        }
    }

    private static Init openStandalone(String dbPath, Options options) {
        RocksDB.loadLibrary();
        File directory = new File(dbPath);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Failed to create RocksDB directory: " + dbPath);
        }

        // Apply RocksDB configuration
        RocksDbConfig config = options.rocksDbConfig();
        org.rocksdb.Cache blockCache = config.createBlockCache();

        ColumnFamilyOptions defaultCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        config.applyToCfOptions(defaultCfOptions, blockCache);

        ColumnFamilyOptions valuesCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        valuesCfOptions.useFixedLengthPrefixExtractor(KEY_HASH_LENGTH);
        config.applyToCfOptions(valuesCfOptions, blockCache);

        ColumnFamilyOptions indexCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        indexCfOptions.useFixedLengthPrefixExtractor(Long.BYTES);
        config.applyToCfOptions(indexCfOptions, blockCache);

        DBOptions dbOptions = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
        config.applyToDbOptions(dbOptions);

        RocksDbJmtSchema.ColumnFamilies names = RocksDbJmtSchema.columnFamilies(options.namespace());

        RocksDB db = null;
        List<ColumnFamilyHandle> handles = new ArrayList<>();
        boolean success = false;
        try (org.rocksdb.Options listOptions = new org.rocksdb.Options().setCreateIfMissing(true)) {
            List<byte[]> existing = RocksDB.listColumnFamilies(listOptions, dbPath);

            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            Map<String, Integer> indexes = new HashMap<>();

            String defaultName = new String(RocksDB.DEFAULT_COLUMN_FAMILY, StandardCharsets.UTF_8);

            for (byte[] name : existing) {
                String cfName = new String(name, StandardCharsets.UTF_8);
                ColumnFamilyOptions cfOpts = selectOptions(cfName, defaultName, names, defaultCfOptions, valuesCfOptions, indexCfOptions, options.enableRollbackIndex());
                descriptors.add(new ColumnFamilyDescriptor(name, cfOpts));
                indexes.put(cfName, descriptors.size() - 1);
            }

            ensureDescriptor(defaultName, RocksDB.DEFAULT_COLUMN_FAMILY,
                    selectOptions(defaultName, defaultName, names, defaultCfOptions, valuesCfOptions, indexCfOptions, options.enableRollbackIndex()),
                    descriptors, indexes);
            ensureDescriptor(names.nodes(), names.nodes().getBytes(StandardCharsets.UTF_8), defaultCfOptions, descriptors, indexes);
            ensureDescriptor(names.values(), names.values().getBytes(StandardCharsets.UTF_8), valuesCfOptions, descriptors, indexes);
            ensureDescriptor(names.roots(), names.roots().getBytes(StandardCharsets.UTF_8), defaultCfOptions, descriptors, indexes);
            ensureDescriptor(names.stale(), names.stale().getBytes(StandardCharsets.UTF_8), defaultCfOptions, descriptors, indexes);
            if (options.enableRollbackIndex()) {
                ensureDescriptor(names.nodesByVersion(), names.nodesByVersion().getBytes(StandardCharsets.UTF_8), indexCfOptions, descriptors, indexes);
                ensureDescriptor(names.valuesByVersion(), names.valuesByVersion().getBytes(StandardCharsets.UTF_8), indexCfOptions, descriptors, indexes);
            }

            db = RocksDB.open(dbOptions, dbPath, descriptors, handles);

            ColumnFamilyHandle nodes = handles.get(indexes.get(names.nodes()));
            ColumnFamilyHandle values = handles.get(indexes.get(names.values()));
            ColumnFamilyHandle roots = handles.get(indexes.get(names.roots()));
            ColumnFamilyHandle stale = handles.get(indexes.get(names.stale()));
            ColumnFamilyHandle nodesByVersion = options.enableRollbackIndex() ? handles.get(indexes.get(names.nodesByVersion())) : null;
            ColumnFamilyHandle valuesByVersion = options.enableRollbackIndex() ? handles.get(indexes.get(names.valuesByVersion())) : null;

            List<ColumnFamilyHandle> ownedHandles = new ArrayList<>(handles);
            List<AutoCloseable> ownedResources = new ArrayList<>();
            if (blockCache != null) {
                ownedResources.add(blockCache);
            }
            ownedResources.add(defaultCfOptions);
            ownedResources.add(valuesCfOptions);
            if (options.enableRollbackIndex()) {
                ownedResources.add(indexCfOptions);
            }
            ownedResources.add(dbOptions);

            success = true;
            return new Init(db, nodes, values, roots, stale, nodesByVersion, valuesByVersion, names, options, true, ownedHandles, ownedResources);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to initialise RocksDbJmtStore", e);
        } finally {
            if (!success) {
                handles.forEach(RocksDbJmtStore::closeQuietly);
                closeQuietly(db);
                closeQuietly(defaultCfOptions);
                closeQuietly(valuesCfOptions);
                closeQuietly(indexCfOptions);
                closeQuietly(dbOptions);
            }
        }
    }

    private static Init attachInternal(RocksDB db,
                                       Options options,
                                       Map<String, ColumnFamilyHandle> existingHandles) throws RocksDBException {
        RocksDbJmtSchema.ColumnFamilies names = RocksDbJmtSchema.columnFamilies(options.namespace());

        // Apply RocksDB configuration
        RocksDbConfig config = options.rocksDbConfig();
        org.rocksdb.Cache blockCache = config.createBlockCache();

        ColumnFamilyOptions defaultCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        config.applyToCfOptions(defaultCfOptions, blockCache);

        ColumnFamilyOptions valuesCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        valuesCfOptions.useFixedLengthPrefixExtractor(KEY_HASH_LENGTH);
        config.applyToCfOptions(valuesCfOptions, blockCache);

        ColumnFamilyOptions indexCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        indexCfOptions.useFixedLengthPrefixExtractor(Long.BYTES);
        config.applyToCfOptions(indexCfOptions, blockCache);

        List<ColumnFamilyHandle> ownedHandles = new ArrayList<>();
        boolean success = false;
        try {
            Map<String, ColumnFamilyHandle> handles = existingHandles == null ? Map.of() : existingHandles;
            ColumnFamilyHandle nodes = ensureHandle(db, names.nodes(), defaultCfOptions, handles, ownedHandles);
            ColumnFamilyHandle values = ensureHandle(db, names.values(), valuesCfOptions, handles, ownedHandles);
            ColumnFamilyHandle roots = ensureHandle(db, names.roots(), defaultCfOptions, handles, ownedHandles);
            ColumnFamilyHandle stale = ensureHandle(db, names.stale(), defaultCfOptions, handles, ownedHandles);
            ColumnFamilyHandle nodesByVersion = options.enableRollbackIndex() ? ensureHandle(db, names.nodesByVersion(), indexCfOptions, handles, ownedHandles) : null;
            ColumnFamilyHandle valuesByVersion = options.enableRollbackIndex() ? ensureHandle(db, names.valuesByVersion(), indexCfOptions, handles, ownedHandles) : null;

            List<AutoCloseable> ownedResources = new ArrayList<>();
            if (blockCache != null) {
                ownedResources.add(blockCache);
            }
            ownedResources.add(defaultCfOptions);
            ownedResources.add(valuesCfOptions);
            if (options.enableRollbackIndex()) {
                ownedResources.add(indexCfOptions);
            }

            success = true;
            return new Init(db, nodes, values, roots, stale, nodesByVersion, valuesByVersion, names, options, false, ownedHandles, ownedResources);
        } finally {
            if (!success) {
                ownedHandles.forEach(RocksDbJmtStore::closeQuietly);
                closeQuietly(defaultCfOptions);
                closeQuietly(valuesCfOptions);
                closeQuietly(indexCfOptions);
            }
        }
    }

    private static ColumnFamilyHandle ensureHandle(RocksDB db,
                                                   String name,
                                                   ColumnFamilyOptions cfOptions,
                                                   Map<String, ColumnFamilyHandle> existing,
                                                   List<ColumnFamilyHandle> ownedHandles) throws RocksDBException {
        ColumnFamilyHandle supplied = existing == null ? null : existing.get(name);
        if (supplied != null) {
            return supplied;
        }
        try {
            ColumnFamilyHandle created = db.createColumnFamily(new ColumnFamilyDescriptor(name.getBytes(StandardCharsets.UTF_8), cfOptions));
            ownedHandles.add(created);
            return created;
        } catch (RocksDBException e) {
            Status status = e.getStatus();
            if (status != null && status.getCode() == Status.Code.InvalidArgument && status.getState().contains("exists")) {
                throw new IllegalStateException("Column family '" + name + "' already exists. Provide its handle via attach(...)", e);
            }
            throw e;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class Init {
        final RocksDB db;
        final ColumnFamilyHandle nodes;
        final ColumnFamilyHandle values;
        final ColumnFamilyHandle roots;
        final ColumnFamilyHandle stale;
        final ColumnFamilyHandle nodesByVersion;
        final ColumnFamilyHandle valuesByVersion;
        final RocksDbJmtSchema.ColumnFamilies names;
        final Options options;
        final boolean ownsDb;
        final List<ColumnFamilyHandle> ownedHandles;
        final List<AutoCloseable> ownedResources;

        Init(RocksDB db,
             ColumnFamilyHandle nodes,
             ColumnFamilyHandle values,
             ColumnFamilyHandle roots,
             ColumnFamilyHandle stale,
             ColumnFamilyHandle nodesByVersion,
             ColumnFamilyHandle valuesByVersion,
             RocksDbJmtSchema.ColumnFamilies names,
             Options options,
             boolean ownsDb,
             List<ColumnFamilyHandle> ownedHandles,
             List<AutoCloseable> ownedResources) {
            this.db = db;
            this.nodes = nodes;
            this.values = values;
            this.roots = roots;
            this.stale = stale;
            this.nodesByVersion = nodesByVersion;
            this.valuesByVersion = valuesByVersion;
            this.names = names;
            this.options = options;
            this.ownsDb = ownsDb;
            this.ownedHandles = ownedHandles == null ? List.of() : List.copyOf(ownedHandles);
            this.ownedResources = ownedResources == null ? List.of() : List.copyOf(ownedResources);
        }
    }
}
