package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.JmtEncoding;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.NodeKey;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessCoordinator;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessLease;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatDescriptor;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatMismatchException;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStoreInspection;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Filter;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtSchema.*;

/**
 * RocksDB-backed implementation of {@link JmtStore}.
 */
public final class RocksDbJmtStore implements JmtStore {

    private static final Map<RocksDB, Map<String, ExternalCoordinatorBinding>>
            EXTERNAL_COORDINATORS = new IdentityHashMap<>();

    /**
     * Returns the column family names used by JMT when the supplied namespace options are applied.
     *
     * @param options namespace options (may be {@code null} for defaults)
     * @return resolved column family names
     */
    public static ColumnFamilies columnFamilies(NamespaceOptions options) {
        RocksDbJmtSchema.ColumnFamilies names = RocksDbJmtSchema.columnFamilies(options);
        return new ColumnFamilies(names.nodes(), names.values(), names.roots(), names.stale(),
                names.metadata(), names.nodesByVersion(), names.valuesByVersion());
    }

    public static final class ColumnFamilies {
        private final String nodes;
        private final String values;
        private final String roots;
        private final String stale;
        private final String metadata;
        private final String nodesByVersion;
        private final String valuesByVersion;

        private ColumnFamilies(String nodes, String values, String roots, String stale,
                               String metadata, String nodesByVersion, String valuesByVersion) {
            this.nodes = nodes;
            this.values = values;
            this.roots = roots;
            this.stale = stale;
            this.metadata = metadata;
            this.nodesByVersion = nodesByVersion;
            this.valuesByVersion = valuesByVersion;
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

        public String metadata() {
            return metadata;
        }

        public String nodesByVersion() {
            return nodesByVersion;
        }

        public String valuesByVersion() {
            return valuesByVersion;
        }
    }

    public enum ValuePrunePolicy {
        /** Retain the newest value at or below the prune horizon for every key. */
        SAFE,
        /** Drop the horizon sentinel only when a newer value exists, never the live head value. */
        AGGRESSIVE
    }

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

        /**
         * Durable Cardano chain-index profile: WAL and sync writes enabled for every mutation and
         * rollback indexes created from the first commit.
         */
        public static Options production() {
            return new Builder()
                    .enableRollbackIndex(true)
                    .disableWalForBatches(false)
                    .syncOnCommit(true)
                    .syncOnPrune(true)
                    .syncOnTruncate(true)
                    .build();
        }

        /** Returns whether this option set satisfies ADR-002 durable RocksDB requirements. */
        public boolean isProductionDurable() {
            return enableRollbackIndex
                    && !disableWalForBatches
                    && syncOnCommit
                    && syncOnPrune
                    && syncOnTruncate;
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
                if (disableWalForBatches && syncOnCommit) {
                    throw new IllegalArgumentException("RocksDB cannot use sync commits with WAL "
                            + "disabled; disable syncOnCommit only for disposable benchmarks");
                }
                return new Options(this);
            }
        }
    }

    private final RocksDB db;
    private final ColumnFamilyHandle cfNodes;
    private final ColumnFamilyHandle cfValues;
    private final ColumnFamilyHandle cfRoots;
    private final ColumnFamilyHandle cfStale;
    private final ColumnFamilyHandle cfMetadata;
    private final ColumnFamilyHandle cfNodesByVersion;
    private final ColumnFamilyHandle cfValuesByVersion;
    private final RocksDbJmtSchema.ColumnFamilies names;
    private final KeyPrefixer keyPrefixer;
    private final boolean ownsDb;
    private final List<ColumnFamilyHandle> ownedHandles;
    private final List<AutoCloseable> ownedResources;
    private final Options storeOptions;
    private final JmtAccessCoordinator accessCoordinator;
    private volatile JmtFormatDescriptor formatDescriptor;

    private static final int KEY_HASH_LENGTH = 32;
    private static final int VALUE_KEY_LENGTH = KEY_HASH_LENGTH + Long.BYTES;
    private static final byte VALUE_PRESENT = 1;
    private static final byte VALUE_TOMBSTONE = 0;
    private static final byte[] INDEX_PLACEHOLDER = new byte[0];
    private static final byte[] FORMAT_DESCRIPTOR_KEY = "JMT_FORMAT".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROLLBACK_ENABLED_KEY = "JMT_ROLLBACK".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PRUNE_WATERMARK_KEY = "JMT_PRUNE_WATERMARK"
            .getBytes(StandardCharsets.UTF_8);

    private byte[] valueKey(byte[] keyHash, long version) {
        requireKeyHash(keyHash);
        byte[] key = new byte[VALUE_KEY_LENGTH];
        System.arraycopy(keyHash, 0, key, 0, KEY_HASH_LENGTH);
        ByteBuffer.wrap(key, KEY_HASH_LENGTH, Long.BYTES).putLong(version);
        return keyPrefixer.prefix(key);
    }

    private byte[] valueVersionKey(long version, byte[] keyHash) {
        requireKeyHash(keyHash);
        byte[] key = new byte[Long.BYTES + KEY_HASH_LENGTH];
        ByteBuffer.wrap(key).putLong(version);
        System.arraycopy(keyHash, 0, key, Long.BYTES, KEY_HASH_LENGTH);
        return keyPrefixer.prefix(key);
    }

    private byte[] nodeVersionKey(long version, byte[] nodeKeyBytes) {
        byte[] key = new byte[Long.BYTES + nodeKeyBytes.length];
        ByteBuffer.wrap(key).putLong(version);
        System.arraycopy(nodeKeyBytes, 0, key, Long.BYTES, nodeKeyBytes.length);
        return keyPrefixer.prefix(key);
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

    private static boolean hasKeyPrefix(byte[] unprefixedKey, byte[] keyHash) {
        if (unprefixedKey.length < KEY_HASH_LENGTH) return false;
        for (int i = 0; i < KEY_HASH_LENGTH; i++) {
            if (unprefixedKey[i] != keyHash[i]) return false;
        }
        return true;
    }

    private static long decodeVersion(byte[] unprefixedKey) {
        return ByteBuffer.wrap(unprefixedKey, KEY_HASH_LENGTH, Long.BYTES).getLong();
    }

    private static void requireKeyHash(byte[] keyHash) {
        Objects.requireNonNull(keyHash, "keyHash");
        if (keyHash.length != KEY_HASH_LENGTH) {
            throw new IllegalArgumentException("keyHash must be exactly " + KEY_HASH_LENGTH + " bytes");
        }
    }

    private static ReadOptions prefixReadOptions() {
        return new ReadOptions().setPrefixSameAsStart(true);
    }

    /**
     * Read options for scans that must cross prefix boundaries (whole-CF or version-range scans).
     *
     * <p>Column families here use fixed-length prefix extractors (33 bytes for values, 9 bytes for
     * the version indexes). A {@code prefixSameAsStart} iterator stops as soon as it leaves the first
     * prefix group, which silently truncates prune/rollback scans to a single group. Total-order seek
     * disables that optimisation so iteration visits every key in sorted order; callers filter by the
     * namespace prefix byte themselves.</p>
     */
    private static ReadOptions totalOrderReadOptions() {
        return new ReadOptions().setTotalOrderSeek(true);
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
        try (ReadOptions options = keyPrefixer.createPrefixReadOptions();
             RocksIterator iterator = db.newIterator(cfValues, options)) {
            iterator.seekForPrev(seekKey);
            while (iterator.isValid()) {
                byte[] unprefixedKey = keyPrefixer.unprefix(iterator.key());
                if (!hasKeyPrefix(unprefixedKey, keyHash)) {
                    return null;
                }
                long foundVersion = decodeVersion(unprefixedKey);
                byte[] encoded = iterator.value();
                return decodeValue(encoded, foundVersion);
            }
        }
        return null;
    }

    private int stageValuePrunes(long versionInclusive, WriteBatch batch) throws RocksDBException {
        if (versionInclusive < 0) {
            return 0;
        }
        int pruned = 0;
        // The values CF uses a 33-byte prefix extractor, so a prefixSameAsStart iterator would stop
        // after the first key hash. Use total-order seek but BOUND it to this namespace: seek to the
        // namespace prefix and stop when it ends, so prune cost is proportional to this namespace's
        // values, not every namespace sharing the CF.
        byte[] nsPrefix = keyPrefixer.prefix(new byte[0]);
        try (ReadOptions readOptions = totalOrderReadOptions();
             RocksIterator iterator = db.newIterator(cfValues, readOptions)) {
            byte[] currentKeyHash = null;
            java.util.List<byte[]> deletions = new java.util.ArrayList<>();
            byte[] sentinel = null;
            boolean hasNewer = false;
            for (iterator.seek(nsPrefix); iterator.isValid(); iterator.next()) {
                byte[] prefixedKey = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) {
                    break;
                }
                byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                if (unprefixedKey.length < VALUE_KEY_LENGTH) {
                    continue;
                }
                byte[] keyHash = Arrays.copyOfRange(unprefixedKey, 0, KEY_HASH_LENGTH);
                long version = decodeVersion(unprefixedKey);

                if (currentKeyHash == null || !Arrays.equals(currentKeyHash, keyHash)) {
                    pruned += applyValueDeletions(deletions, sentinel, hasNewer, batch);
                    deletions.clear();
                    sentinel = null;
                    hasNewer = false;
                    currentKeyHash = keyHash;
                }

                if (Long.compareUnsigned(version, versionInclusive) <= 0) {
                    deletions.add(Arrays.copyOf(prefixedKey, prefixedKey.length));
                    sentinel = Arrays.copyOf(prefixedKey, prefixedKey.length);
                } else {
                    hasNewer = true;
                }

            }

            pruned += applyValueDeletions(deletions, sentinel, hasNewer, batch);
        }
        return pruned;
    }

    private int applyValueDeletions(java.util.List<byte[]> deletions,
                                    byte[] sentinel,
                                    boolean hasNewer,
                                    WriteBatch batch) throws RocksDBException {
        if (deletions.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (byte[] prefixedKey : deletions) {
            boolean retainSentinel = storeOptions.prunePolicy() == ValuePrunePolicy.SAFE
                    || !hasNewer;
            if (retainSentinel && sentinel != null && Arrays.equals(prefixedKey, sentinel)) {
                continue;
            }
            batch.delete(cfValues, prefixedKey);
            if (cfValuesByVersion != null) {
                byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                long version = decodeVersion(unprefixedKey);
                byte[] keyHash = Arrays.copyOfRange(unprefixedKey, 0, KEY_HASH_LENGTH);
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
     * Opens (or creates) a dedicated RocksDB instance with namespace options applied.
     * The store owns the database lifecycle.
     *
     * <p>Only {@code columnFamilyPrefix} is currently supported. A non-default key prefix is
     * rejected instead of being silently ignored, which could otherwise collapse isolated trees
     * into the same namespace.</p>
     */
    public RocksDbJmtStore(String dbPath, NamespaceOptions namespaceOptions) {
        this(dbPath, supportedColumnFamilyPrefix(namespaceOptions));
    }

    private static String supportedColumnFamilyPrefix(
            NamespaceOptions namespaceOptions) {
        Objects.requireNonNull(namespaceOptions, "namespaceOptions");
        if (!namespaceOptions.usesDefaultKeyPrefix()) {
            throw new IllegalArgumentException("RocksDbJmtStore does not support NamespaceOptions.keyPrefix yet");
        }
        return namespaceOptions.columnFamilyPrefix();
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
     * @param accessCoordinator coordinator shared by all wrappers for this namespace
     * @return a store bound to the supplied database
     */
    public static RocksDbJmtStore attach(RocksDB db,
                                         String namespace,
                                         JmtAccessCoordinator accessCoordinator) {
        return attach(db, Options.builder().namespace(namespace).build(), Map.of(), accessCoordinator);
    }

    /**
     * Attaches the store to an already opened RocksDB instance using existing column-family
     * handles when provided. Any missing handle will cause the method to create the column family.
     *
     * @param db              shared RocksDB instance
     * @param namespace       optional namespace/prefix for column family names
     * @param existingHandles map of column family names to handles that are already managed by the caller
     * @param accessCoordinator coordinator shared by all wrappers for this namespace
     * @return a store bound to the supplied database
     */
    public static RocksDbJmtStore attach(RocksDB db,
                                         String namespace,
                                         Map<String, ColumnFamilyHandle> existingHandles,
                                         JmtAccessCoordinator accessCoordinator) {
        return attach(db, Options.builder().namespace(namespace).build(), existingHandles,
                accessCoordinator);
    }

    public static RocksDbJmtStore attach(RocksDB db,
                                         Options options,
                                         Map<String, ColumnFamilyHandle> existingHandles,
                                         JmtAccessCoordinator accessCoordinator) {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(accessCoordinator, "accessCoordinator");
        ExternalCoordinatorRegistration registration = registerExternalCoordinator(
                db, externalNamespaceIdentity(options), accessCoordinator);
        try {
            return new RocksDbJmtStore(attachInternal(db, options, existingHandles,
                    accessCoordinator, registration));
        } catch (RocksDBException e) {
            registration.close();
            throw new RuntimeException("Failed to attach RocksDbJmtStore", e);
        } catch (RuntimeException e) {
            registration.close();
            throw e;
        }
    }

    public static RocksDbJmtStore attach(RocksDB db,
                                         Options options,
                                         JmtAccessCoordinator accessCoordinator) {
        return attach(db, options, Map.of(), accessCoordinator);
    }

    private RocksDbJmtStore(Init init) {
        this.db = init.db;
        this.cfNodes = init.nodes;
        this.cfValues = init.values;
        this.cfRoots = init.roots;
        this.cfStale = init.stale;
        this.cfMetadata = init.metadata;
        this.cfNodesByVersion = init.nodesByVersion;
        this.cfValuesByVersion = init.valuesByVersion;
        this.names = init.names;
        this.keyPrefixer = new KeyPrefixer(init.names.keyPrefix());
        this.ownsDb = init.ownsDb;
        this.ownedHandles = init.ownedHandles;
        this.ownedResources = init.ownedResources;
        this.storeOptions = init.options;
        this.accessCoordinator = init.accessCoordinator;
        try {
            loadAndValidateExistingFormat();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public JmtAccessCoordinator accessCoordinator() {
        return accessCoordinator;
    }

    @Override
    public void ensureFormat(JmtFormatDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor").requirePersistent();
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance("ensureFormat")) {
            try {
                byte[] key = keyPrefixer.prefix(FORMAT_DESCRIPTOR_KEY);
                byte[] existing = db.get(cfMetadata, key);
                if (existing == null) {
                    if (hasNamespaceData()) {
                        throw new JmtFormatMismatchException("Non-empty RocksDB JMT namespace has no "
                                + "format descriptor; rebuild it into a fresh namespace");
                    }
                    try (WriteBatch batch = new WriteBatch();
                         WriteOptions writeOptions = new WriteOptions().setSync(true)) {
                        batch.put(cfMetadata, key, descriptor.encode());
                        batch.put(cfMetadata, keyPrefixer.prefix(ROLLBACK_ENABLED_KEY),
                                new byte[]{(byte) (storeOptions.enableRollbackIndex() ? 1 : 0)});
                        db.write(writeOptions, batch);
                    }
                    formatDescriptor = descriptor;
                    return;
                }
                JmtFormatDescriptor persisted = decodeDescriptor(existing);
                validateRollbackFeature();
                if (!persisted.equals(descriptor)) {
                    throw new JmtFormatMismatchException("RocksDB JMT format mismatch: persisted "
                            + persisted + ", requested " + descriptor);
                }
                formatDescriptor = persisted;
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to initialize RocksDB JMT format", e);
            }
        }
    }

    @Override
    public Optional<JmtFormatDescriptor> formatDescriptor() {
        return Optional.ofNullable(formatDescriptor);
    }

    @Override
    public JmtStoreInspection inspect(int maxRecords) {
        if (maxRecords <= 0) {
            throw new IllegalArgumentException("maxRecords must be > 0");
        }
        requireFormatInitialized();
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireRead("inspect")) {
            InspectionAccumulator result = new InspectionAccumulator(maxRecords);
            Snapshot snapshot = db.getSnapshot();
            try (ReadOptions readOptions = new ReadOptions()
                    .setSnapshot(snapshot)
                    .setTotalOrderSeek(true)) {
                inspectRoots(result, readOptions);
                inspectLatest(result, readOptions);
                inspectNodes(result, readOptions);
                inspectValues(result, readOptions);
                inspectStale(result, readOptions);
                if (!result.truncated && storeOptions.enableRollbackIndex()) {
                    inspectRollbackIndexes(result, readOptions);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to inspect RocksDB JMT store", e);
            } finally {
                db.releaseSnapshot(snapshot);
            }
            return result.snapshot();
        }
    }

    private void inspectRoots(InspectionAccumulator result, ReadOptions options) {
        byte[] namespacePrefix = keyPrefixer.prefix(new byte[0]);
        byte[] latestRootKey = keyPrefixer.prefix(LATEST_ROOT_KEY);
        byte[] latestVersionKey = keyPrefixer.prefix(LATEST_VERSION_KEY);
        try (RocksIterator iterator = db.newIterator(cfRoots, options)) {
            for (iterator.seek(namespacePrefix); iterator.isValid() && !result.truncated;
                 iterator.next()) {
                byte[] key = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(key)) {
                    break;
                }
                if (Arrays.equals(key, latestRootKey) || Arrays.equals(key, latestVersionKey)) {
                    continue;
                }
                byte[] unprefixed = keyPrefixer.unprefix(key);
                if (unprefixed.length != Long.BYTES) {
                    result.backendIssues.add("Malformed root key length: " + unprefixed.length);
                    continue;
                }
                if (!result.take()) {
                    break;
                }
                result.roots.add(new VersionedRoot(ByteBuffer.wrap(unprefixed).getLong(),
                        iterator.value()));
            }
        }
    }

    private void inspectLatest(InspectionAccumulator result, ReadOptions options)
            throws RocksDBException {
        byte[] root = db.get(cfRoots, options, keyPrefixer.prefix(LATEST_ROOT_KEY));
        byte[] version = db.get(cfRoots, options, keyPrefixer.prefix(LATEST_VERSION_KEY));
        if (root == null && version == null) {
            return;
        }
        if (root == null || version == null || version.length != Long.BYTES) {
            result.backendIssues.add("Malformed latest-root/latest-version pointer");
            return;
        }
        result.latestRoot = new VersionedRoot(ByteBuffer.wrap(version).getLong(), root);
    }

    private void inspectNodes(InspectionAccumulator result, ReadOptions options) {
        byte[] namespacePrefix = keyPrefixer.prefix(new byte[0]);
        try (RocksIterator iterator = db.newIterator(cfNodes, options)) {
            for (iterator.seek(namespacePrefix); iterator.isValid() && !result.truncated;
                 iterator.next()) {
                byte[] key = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(key)) {
                    break;
                }
                if (!result.take()) {
                    break;
                }
                byte[] nodeKeyBytes = keyPrefixer.unprefix(key);
                try {
                    NodeKey nodeKey = NodeKey.fromBytes(nodeKeyBytes);
                    result.nodes.add(new JmtStoreInspection.NodeRecord(nodeKey,
                            JmtEncoding.decode(iterator.value())));
                    result.expectedNodeIndexes.add(bytesId(
                            nodeVersionKey(nodeKey.version(), nodeKeyBytes)));
                } catch (RuntimeException e) {
                    result.backendIssues.add("Malformed node record: " + e.getMessage());
                }
            }
        }
    }

    private void inspectValues(InspectionAccumulator result, ReadOptions options) {
        byte[] namespacePrefix = keyPrefixer.prefix(new byte[0]);
        try (RocksIterator iterator = db.newIterator(cfValues, options)) {
            for (iterator.seek(namespacePrefix); iterator.isValid() && !result.truncated;
                 iterator.next()) {
                byte[] key = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(key)) {
                    break;
                }
                if (!result.take()) {
                    break;
                }
                byte[] unprefixed = keyPrefixer.unprefix(key);
                try {
                    if (unprefixed.length != VALUE_KEY_LENGTH) {
                        throw new IllegalArgumentException("value key length " + unprefixed.length);
                    }
                    byte[] keyHash = Arrays.copyOfRange(unprefixed, 0, KEY_HASH_LENGTH);
                    long version = decodeVersion(unprefixed);
                    ValueEntry value = decodeValue(iterator.value(), version);
                    result.values.add(new JmtStoreInspection.ValueRecord(keyHash, version,
                            value.value, value.tombstone));
                    result.expectedValueIndexes.add(bytesId(valueVersionKey(version, keyHash)));
                } catch (RuntimeException e) {
                    result.backendIssues.add("Malformed value record: " + e.getMessage());
                }
            }
        }
    }

    private void inspectStale(InspectionAccumulator result, ReadOptions options) {
        byte[] namespacePrefix = keyPrefixer.prefix(new byte[0]);
        try (RocksIterator iterator = db.newIterator(cfStale, options)) {
            for (iterator.seek(namespacePrefix); iterator.isValid() && !result.truncated;
                 iterator.next()) {
                byte[] key = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(key)) {
                    break;
                }
                if (!result.take()) {
                    break;
                }
                byte[] unprefixed = keyPrefixer.unprefix(key);
                try {
                    if (unprefixed.length <= Long.BYTES) {
                        throw new IllegalArgumentException("stale key is truncated");
                    }
                    long staleSince = ByteBuffer.wrap(unprefixed, 0, Long.BYTES).getLong();
                    NodeKey nodeKey = NodeKey.fromBytes(Arrays.copyOfRange(
                            unprefixed, Long.BYTES, unprefixed.length));
                    result.stale.add(new JmtStoreInspection.StaleRecord(staleSince, nodeKey));
                } catch (RuntimeException e) {
                    result.backendIssues.add("Malformed stale record: " + e.getMessage());
                }
            }
        }
    }

    private void inspectRollbackIndexes(InspectionAccumulator result, ReadOptions options) {
        Set<String> actualNodeIndexes = inspectIndexKeys(cfNodesByVersion, options);
        Set<String> actualValueIndexes = inspectIndexKeys(cfValuesByVersion, options);
        if (!actualNodeIndexes.equals(result.expectedNodeIndexes)) {
            result.backendIssues.add("nodes-by-version rollback index does not match node records");
        }
        if (!actualValueIndexes.equals(result.expectedValueIndexes)) {
            result.backendIssues.add("values-by-version rollback index does not match value records");
        }
    }

    private Set<String> inspectIndexKeys(ColumnFamilyHandle handle, ReadOptions options) {
        Set<String> keys = new HashSet<>();
        byte[] namespacePrefix = keyPrefixer.prefix(new byte[0]);
        try (RocksIterator iterator = db.newIterator(handle, options)) {
            for (iterator.seek(namespacePrefix); iterator.isValid(); iterator.next()) {
                byte[] key = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(key)) {
                    break;
                }
                keys.add(bytesId(key));
            }
        }
        return keys;
    }

    private static String bytesId(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    private static void ensureDescriptor(String key, byte[] nameBytes, ColumnFamilyOptions cfOptions,
                                         List<ColumnFamilyDescriptor> descriptors,
                                         Map<String, Integer> indexes) {
        if (!indexes.containsKey(key)) {
            indexes.put(key, descriptors.size());
            descriptors.add(new ColumnFamilyDescriptor(nameBytes, cfOptions));
        }
    }

    private void loadAndValidateExistingFormat() {
        try {
            byte[] encoded = db.get(cfMetadata, keyPrefixer.prefix(FORMAT_DESCRIPTOR_KEY));
            if (encoded == null) {
                if (hasNamespaceData()) {
                    throw new JmtFormatMismatchException("Non-empty RocksDB JMT namespace has no "
                            + "format descriptor; rebuild it into a fresh namespace");
                }
                return;
            }
            formatDescriptor = decodeDescriptor(encoded);
            validateRollbackFeature();
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read RocksDB JMT format metadata", e);
        }
    }

    private JmtFormatDescriptor decodeDescriptor(byte[] encoded) {
        try {
            JmtFormatDescriptor descriptor = JmtFormatDescriptor.decode(encoded);
            descriptor.requirePersistent();
            return descriptor;
        } catch (IllegalArgumentException | JmtFormatMismatchException e) {
            throw new JmtFormatMismatchException("Malformed RocksDB JMT format descriptor", e);
        }
    }

    private void validateRollbackFeature() throws RocksDBException {
        byte[] encoded = db.get(cfMetadata, keyPrefixer.prefix(ROLLBACK_ENABLED_KEY));
        if (encoded == null || encoded.length != 1 || (encoded[0] != 0 && encoded[0] != 1)) {
            throw new JmtFormatMismatchException("Missing or malformed RocksDB JMT rollback feature metadata");
        }
        boolean persistedRollback = encoded[0] == 1;
        if (persistedRollback != storeOptions.enableRollbackIndex()) {
            throw new JmtFormatMismatchException("RocksDB JMT rollback-index configuration mismatch: "
                    + "persisted=" + persistedRollback + ", requested="
                    + storeOptions.enableRollbackIndex());
        }
    }

    private boolean hasNamespaceData() throws RocksDBException {
        return hasAnyKey(cfNodes)
                || hasAnyKey(cfValues)
                || hasAnyKey(cfRoots)
                || hasAnyKey(cfStale);
    }

    private boolean hasAnyKey(ColumnFamilyHandle handle) {
        byte[] namespacePrefix = keyPrefixer.prefix(new byte[0]);
        try (ReadOptions readOptions = totalOrderReadOptions();
             RocksIterator iterator = db.newIterator(handle, readOptions)) {
            iterator.seek(namespacePrefix);
            return iterator.isValid() && keyPrefixer.hasCorrectPrefix(iterator.key());
        }
    }

    private void requireFormatInitialized() {
        if (formatDescriptor == null) {
            throw new JmtFormatMismatchException("RocksDB JMT format is not initialized; construct "
                    + "JellyfishMerkleTree with an explicit JmtProfile first");
        }
    }

    @Override
    public Optional<VersionedRoot> latestRoot() {
        try {
            byte[] prefixedLatestRootKey = keyPrefixer.prefix(LATEST_ROOT_KEY);
            byte[] prefixedLatestVersionKey = keyPrefixer.prefix(LATEST_VERSION_KEY);

            byte[] root = db.get(cfRoots, prefixedLatestRootKey);
            if (root == null) {
                return Optional.empty();
            }
            byte[] versionBytes = db.get(cfRoots, prefixedLatestVersionKey);
            long version = versionBytes == null ? -1 : ByteBuffer.wrap(versionBytes).getLong();
            if (version < 0) {
                // Fallback when the latest-version metadata is missing: scan backward for the
                // greatest per-version root IN THIS NAMESPACE. A prefixSameAsStart+seekToLast scan
                // could bind to another namespace's group and return a foreign root, so use
                // total-order iteration with an explicit namespace-prefix filter.
                try (ReadOptions readOptions = totalOrderReadOptions();
                     RocksIterator iterator = db.newIterator(cfRoots, readOptions)) {
                    for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
                        byte[] key = iterator.key();
                        if (!keyPrefixer.hasCorrectPrefix(key)) {
                            continue;
                        }
                        if (Arrays.equals(key, prefixedLatestRootKey) || Arrays.equals(key, prefixedLatestVersionKey)) {
                            continue;
                        }
                        byte[] unprefixed = keyPrefixer.unprefix(key);
                        if (unprefixed.length != Long.BYTES) {
                            continue; // not a per-version root key
                        }
                        version = ByteBuffer.wrap(unprefixed).getLong();
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
            byte[] prefixedKey = keyPrefixer.prefix(versionKey(version));
            byte[] root = db.get(cfRoots, prefixedKey);
            return root == null ? Optional.empty() : Optional.of(root.clone());
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read JMT root for version " + version, e);
        }
    }

    @Override
    public Optional<NodeEntry> getNode(long version, NibblePath path) {
        Objects.requireNonNull(path, "path");
        byte[] searchKey = keyPrefixer.prefix(NodeKey.of(path, version).toBytes());
        try (ReadOptions options = keyPrefixer.createPrefixReadOptions();
             RocksIterator iterator = db.newIterator(cfNodes, options)) {
            iterator.seekForPrev(searchKey);
            while (iterator.isValid()) {
                byte[] keyBytes = keyPrefixer.unprefix(iterator.key());
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
            byte[] prefixedKey = keyPrefixer.prefix(nodeKey.toBytes());
            byte[] nodeBytes = db.get(cfNodes, prefixedKey);
            return nodeBytes == null ? Optional.empty() : Optional.of(JmtEncoding.decode(nodeBytes));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to load JMT node", e);
        }
    }

    @Override
    public Optional<NodeEntry> floorNode(long version, NibblePath path) {
        return JmtStore.super.floorNode(version, path);
    }

    @Override
    public Optional<NodeEntry> ceilingNode(long version, NibblePath path) {
        return JmtStore.super.ceilingNode(version, path);
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
        requireFormatInitialized();
        Objects.requireNonNull(config, "config");
        JmtAccessLease lease = accessCoordinator.tryAcquireUpdate("commit", version);
        return new RocksCommitBatch(version, config, lease);
    }


    @Override
    public List<NodeKey> staleNodesUpTo(long versionInclusive) {
        requireNonNegativeVersion(versionInclusive);
        List<NodeKey> results = new ArrayList<>();
        // Bounded, namespace-filtered scan: seek to this namespace's prefix and stop when it ends,
        // consistent with the other stale/value scans (never read a foreign namespace's markers).
        byte[] nsPrefix = keyPrefixer.prefix(new byte[0]);
        try (ReadOptions readOptions = totalOrderReadOptions();
             RocksIterator iterator = db.newIterator(cfStale, readOptions)) {
            for (iterator.seek(nsPrefix); iterator.isValid(); iterator.next()) {
                byte[] prefixedKey = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) break;
                byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                long staleSince = ByteBuffer.wrap(unprefixedKey, 0, 8).getLong();
                if (Long.compareUnsigned(staleSince, versionInclusive) > 0) break;
                byte[] nodeKeyBytes = Arrays.copyOfRange(unprefixedKey, 8, unprefixedKey.length);
                results.add(NodeKey.fromBytes(nodeKeyBytes));
            }
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public int pruneUpTo(long versionInclusive) {
        requireFormatInitialized();
        requireNonNegativeVersion(versionInclusive);
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance(
                "pruneUpTo", versionInclusive)) {
            Optional<VersionedRoot> latest = latestRoot();
            if (latest.isPresent() && versionInclusive > latest.get().version()) {
                throw new IllegalArgumentException("prune horizon exceeds latest version "
                        + latest.get().version());
            }
            return pruneUpToUnderLease(versionInclusive);
        }
    }

    private int pruneUpToUnderLease(long versionInclusive) {
        int nodesPruned = 0;
        // Bounded, namespace-filtered scan (see staleNodesUpTo): deleting node rows keyed off a
        // foreign namespace's stale markers would silently drop this namespace's LIVE nodes.
        byte[] nsPrefix = keyPrefixer.prefix(new byte[0]);
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions();
             ReadOptions readOptions = totalOrderReadOptions();
             RocksIterator iterator = db.newIterator(cfStale, readOptions)) {
            if (storeOptions != null && storeOptions.syncOnPrune()) {
                writeOptions.setSync(true);
            }
            for (iterator.seek(nsPrefix); iterator.isValid(); iterator.next()) {
                byte[] prefixedKey = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) break;
                byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                long staleSince = ByteBuffer.wrap(unprefixedKey, 0, 8).getLong();
                if (Long.compareUnsigned(staleSince, versionInclusive) > 0) {
                    break;
                }
                byte[] nodeKeyBytes = Arrays.copyOfRange(unprefixedKey, 8, unprefixedKey.length);
                batch.delete(cfNodes, keyPrefixer.prefix(nodeKeyBytes));
                if (cfNodesByVersion != null) {
                    NodeKey nodeKey = NodeKey.fromBytes(nodeKeyBytes);
                    batch.delete(cfNodesByVersion, nodeVersionKey(nodeKey.version(), nodeKeyBytes));
                }
                batch.delete(cfStale, prefixedKey);
                nodesPruned++;
            }
            int valuesPruned = stageValuePrunes(versionInclusive, batch);
            int rootsPruned = stageRootPrunes(versionInclusive, batch);
            int recordsPruned = nodesPruned + valuesPruned + rootsPruned;
            if (recordsPruned > 0) {
                long currentWatermark = readPruneWatermark();
                long newWatermark = Math.max(currentWatermark, versionInclusive);
                batch.put(cfMetadata, keyPrefixer.prefix(PRUNE_WATERMARK_KEY),
                        versionKey(newWatermark));
                db.write(writeOptions, batch);
            }
            return recordsPruned;
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to atomically prune JMT nodes and values", e);
        }
    }

    private int stageRootPrunes(long versionExclusive, WriteBatch batch) throws RocksDBException {
        int pruned = 0;
        byte[] namespacePrefix = keyPrefixer.prefix(new byte[0]);
        byte[] latestRootKey = keyPrefixer.prefix(LATEST_ROOT_KEY);
        byte[] latestVersionKey = keyPrefixer.prefix(LATEST_VERSION_KEY);
        try (ReadOptions options = totalOrderReadOptions();
             RocksIterator iterator = db.newIterator(cfRoots, options)) {
            for (iterator.seek(namespacePrefix); iterator.isValid(); iterator.next()) {
                byte[] key = iterator.key();
                if (!keyPrefixer.hasCorrectPrefix(key)) {
                    break;
                }
                if (Arrays.equals(key, latestRootKey) || Arrays.equals(key, latestVersionKey)) {
                    continue;
                }
                byte[] unprefixed = keyPrefixer.unprefix(key);
                if (unprefixed.length != Long.BYTES) {
                    continue;
                }
                long version = ByteBuffer.wrap(unprefixed).getLong();
                if (version < versionExclusive) {
                    batch.delete(cfRoots, key);
                    pruned++;
                }
            }
        }
        return pruned;
    }

    @Override
    public void truncateAfter(long versionExclusive) {
        requireFormatInitialized();
        if (versionExclusive < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        try (JmtAccessLease ignored = accessCoordinator.tryAcquireMaintenance(
                "truncateAfter", versionExclusive)) {
            truncateAfterUnderLease(versionExclusive);
        }
    }

    private void truncateAfterUnderLease(long versionExclusive) {
        if (!storeOptions.enableRollbackIndex()) {
            throw new UnsupportedOperationException("Rollback indices are disabled for this store");
        }
        long pruneWatermark = readPruneWatermark();
        if (pruneWatermark >= 0 && versionExclusive < pruneWatermark) {
            throw new IllegalStateException("Cannot truncate to version " + versionExclusive
                    + " below prune watermark " + pruneWatermark);
        }
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {
            if (storeOptions != null && storeOptions.syncOnTruncate()) {
                writeOptions.setSync(true);
            }

            byte[] prefixedLatestRootKey = keyPrefixer.prefix(LATEST_ROOT_KEY);
            byte[] prefixedLatestVersionKey = keyPrefixer.prefix(LATEST_VERSION_KEY);

            // Roots: delete future roots and recompute the latest pointer from the GREATEST
            // surviving root (<= versionExclusive) rather than requiring an exact match at
            // versionExclusive (which loses the pointer when that version had no commit).
            byte[] retainedRoot = null;
            long retainedVersion = -1;
            byte[] rootsNsPrefix = keyPrefixer.prefix(new byte[0]);
            try (ReadOptions readOptions = totalOrderReadOptions();
                 RocksIterator iterator = db.newIterator(cfRoots, readOptions)) {
                for (iterator.seek(rootsNsPrefix); iterator.isValid(); iterator.next()) {
                    byte[] prefixedKey = iterator.key();
                    if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) {
                        break;
                    }
                    if (Arrays.equals(prefixedKey, prefixedLatestRootKey) || Arrays.equals(prefixedKey, prefixedLatestVersionKey)) {
                        continue;
                    }
                    byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                    if (unprefixedKey.length != Long.BYTES) {
                        continue; // not a per-version root key
                    }
                    long version = ByteBuffer.wrap(unprefixedKey).getLong();
                    if (Long.compareUnsigned(version, versionExclusive) > 0) {
                        batch.delete(cfRoots, prefixedKey);
                    } else if (retainedVersion < 0 || Long.compareUnsigned(version, retainedVersion) > 0) {
                        retainedVersion = version;
                        retainedRoot = iterator.value().clone();
                    }
                }
            }

            if (retainedVersion >= 0 && retainedRoot != null) {
                batch.put(cfRoots, prefixedLatestRootKey, retainedRoot);
                batch.put(cfRoots, prefixedLatestVersionKey, versionKey(retainedVersion));
            } else {
                batch.delete(cfRoots, prefixedLatestRootKey);
                batch.delete(cfRoots, prefixedLatestVersionKey);
            }

            // Stale markers (namespace-bounded, consistent with the other scans).
            byte[] staleNsPrefix = keyPrefixer.prefix(new byte[0]);
            try (ReadOptions readOptions = totalOrderReadOptions();
                 RocksIterator iterator = db.newIterator(cfStale, readOptions)) {
                for (iterator.seek(staleNsPrefix); iterator.isValid(); iterator.next()) {
                    byte[] prefixedKey = iterator.key();
                    if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) {
                        break;
                    }
                    byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                    long staleSince = ByteBuffer.wrap(unprefixedKey, 0, 8).getLong();
                    if (Long.compareUnsigned(staleSince, versionExclusive) > 0) {
                        batch.delete(cfStale, prefixedKey);
                    }
                }
            }

            // Nodes by version: version-range scan across the 9-byte-prefix index CF. A
            // prefixSameAsStart iterator would stop after a single version, so use total-order
            // seek and stop only when the namespace prefix changes.
            if (cfNodesByVersion != null) {
                try (ReadOptions readOptions = totalOrderReadOptions();
                     RocksIterator iterator = db.newIterator(cfNodesByVersion, readOptions)) {
                    iterator.seek(keyPrefixer.prefix(versionKey(versionExclusive + 1)));
                    while (iterator.isValid()) {
                        byte[] prefixedKey = iterator.key();
                        if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) {
                            break;
                        }
                        byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                        long version = ByteBuffer.wrap(unprefixedKey, 0, Long.BYTES).getLong();
                        if (Long.compareUnsigned(version, versionExclusive) <= 0) {
                            iterator.next();
                            continue;
                        }
                        byte[] nodeKeyBytes = Arrays.copyOfRange(unprefixedKey, Long.BYTES, unprefixedKey.length);
                        batch.delete(cfNodes, keyPrefixer.prefix(nodeKeyBytes));
                        batch.delete(cfNodesByVersion, prefixedKey);
                        iterator.next();
                    }
                }
            }

            // Values by version: same version-range scan across the 9-byte-prefix index CF.
            if (cfValuesByVersion != null) {
                try (ReadOptions readOptions = totalOrderReadOptions();
                     RocksIterator iterator = db.newIterator(cfValuesByVersion, readOptions)) {
                    iterator.seek(keyPrefixer.prefix(versionKey(versionExclusive + 1)));
                    while (iterator.isValid()) {
                        byte[] prefixedKey = iterator.key();
                        if (!keyPrefixer.hasCorrectPrefix(prefixedKey)) {
                            break;
                        }
                        byte[] unprefixedKey = keyPrefixer.unprefix(prefixedKey);
                        long version = ByteBuffer.wrap(unprefixedKey, 0, Long.BYTES).getLong();
                        if (Long.compareUnsigned(version, versionExclusive) <= 0) {
                            iterator.next();
                            continue;
                        }
                        byte[] keyHash = Arrays.copyOfRange(unprefixedKey, Long.BYTES, unprefixedKey.length);
                        batch.delete(cfValues, valueKey(keyHash, version));
                        batch.delete(cfValuesByVersion, prefixedKey);
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
     *
     * <p>Note: Properties are sampled from the primary 'nodes' column family as it contains
     * the bulk of the data and best represents overall database health.
     */
    public DbProperties sampleDbProperties() {
        try {
            // Most properties need to be queried on a specific column family, not the DB
            // We use cfNodes as it contains the bulk of data
            long pending = parseLong(db.getProperty(cfNodes, "rocksdb.estimate-pending-compaction-bytes"));
            int runningComp = (int) parseLong(db.getProperty(cfNodes, "rocksdb.num-running-compactions"));
            int runningFlush = (int) parseLong(db.getProperty(cfNodes, "rocksdb.num-running-flushes"));
            long activeMem = parseLong(db.getProperty(cfNodes, "rocksdb.cur-size-active-mem-table"));
            long allMem = parseLong(db.getProperty(cfNodes, "rocksdb.cur-size-all-mem-tables"));
            long imm = parseLong(db.getProperty(cfNodes, "rocksdb.num-immutable-mem-table"));
            return new DbProperties(pending, runningComp, runningFlush, activeMem, allMem, imm);
        } catch (Exception e) {
            // Silently return zeros if properties unavailable
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

    private byte[] staleKey(long version, NodeKey nodeKey) {
        byte[] nodeBytes = nodeKey.toBytes();
        byte[] key = new byte[8 + nodeBytes.length];
        ByteBuffer.wrap(key).putLong(version).put(nodeBytes);
        return keyPrefixer.prefix(key);
    }

    private static byte[] versionKey(long version) {
        return ByteBuffer.allocate(Long.BYTES).putLong(version).array();
    }

    private long readPruneWatermark() {
        try {
            byte[] encoded = db.get(cfMetadata, keyPrefixer.prefix(PRUNE_WATERMARK_KEY));
            if (encoded == null) {
                return -1;
            }
            if (encoded.length != Long.BYTES) {
                throw new JmtFormatMismatchException("Malformed RocksDB JMT prune watermark");
            }
            long watermark = ByteBuffer.wrap(encoded).getLong();
            requireNonNegativeVersion(watermark);
            return watermark;
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to read JMT prune watermark", e);
        }
    }

    private static void requireNonNegativeVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
    }

    private final class RocksCommitBatch implements CommitBatch {

        private final long version;
        private final CommitConfig config;
        private final JmtAccessLease lease;
        private final Thread owner = Thread.currentThread();
        private final WriteBatch batch = new WriteBatch();
        private final WriteOptions writeOptions = new WriteOptions();
        private boolean closed;
        private byte[] rootHash;

        private RocksCommitBatch(long version, CommitConfig config, JmtAccessLease lease) {
            this.version = version;
            this.config = config;
            this.lease = lease;
            if (storeOptions != null && storeOptions.disableWalForBatches()) {
                this.writeOptions.setDisableWAL(true);
            }
            if (storeOptions != null && storeOptions.syncOnCommit()) {
                this.writeOptions.setSync(true);
            }
        }

        @Override
        public void putNode(NodeKey nodeKey, JmtNode node) {
            ensureOpen();
            Objects.requireNonNull(nodeKey, "nodeKey");
            Objects.requireNonNull(node, "node");
            try {
                byte[] nodeBytes = nodeKey.toBytes();
                batch.put(cfNodes, keyPrefixer.prefix(nodeBytes), node.encode());
                if (cfNodesByVersion != null) {
                    batch.put(cfNodesByVersion, nodeVersionKey(nodeKey.version(), nodeBytes), INDEX_PLACEHOLDER);
                }
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage node write", e);
            }
        }

        @Override
        public void markStale(NodeKey nodeKey) {
            ensureOpen();
            Objects.requireNonNull(nodeKey, "nodeKey");
            try {
                batch.put(cfStale, staleKey(version, nodeKey), new byte[0]);
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to stage stale marker", e);
            }
        }

        @Override
        public void putValue(byte[] keyHash, byte[] value) {
            ensureOpen();
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
            ensureOpen();
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
            ensureOpen();
            this.rootHash = rootHash == null ? null : rootHash.clone();
        }

        @Override
        public void commit() {
            ensureOpen();
            try {
                requireRootHash(rootHash);
                Optional<VersionedRoot> latest = latestRoot();
                byte[] existingRoot = db.get(
                        cfRoots, keyPrefixer.prefix(versionKey(version)));
                if (!config.shouldApply(version, rootHash, latest,
                        Optional.ofNullable(existingRoot))) {
                    return;
                }
                batch.put(cfRoots, keyPrefixer.prefix(versionKey(version)), rootHash);
                batch.put(cfRoots, keyPrefixer.prefix(LATEST_ROOT_KEY), rootHash);
                batch.put(cfRoots, keyPrefixer.prefix(LATEST_VERSION_KEY), versionKey(version));
                db.write(writeOptions, batch);
            } catch (RocksDBException e) {
                throw new RuntimeException("Failed to flush JMT batch", e);
            } finally {
                closeInternal();
            }
        }

        @Override
        public void close() {
            closeInternal();
        }

        private void closeInternal() {
            ensureOwner();
            if (closed) {
                return;
            }
            closed = true;
            try {
                batch.close();
            } catch (Exception ignored) {
            }
            try {
                writeOptions.close();
            } catch (Exception ignored) {
            }
            lease.close();
        }

        private void ensureOpen() {
            ensureOwner();
            if (closed) {
                throw new IllegalStateException("CommitBatch already closed");
            }
        }

        private void ensureOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("CommitBatch must be used by its creating thread");
            }
        }

        private void requireRootHash(byte[] hash) {
            int expectedLength = formatDescriptor.hashLength();
            if (hash == null || hash.length != expectedLength) {
                throw new IllegalStateException("Commit root hash must be exactly "
                        + expectedLength + " bytes");
            }
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

        // All CFs use 1-byte key prefix for namespace support
        ColumnFamilyOptions defaultCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        defaultCfOptions.useFixedLengthPrefixExtractor(1);
        Filter defaultFilter = filterFrom(config.applyToCfOptions(defaultCfOptions, blockCache));

        // Values CF has composite prefix: 1-byte namespace + KEY_HASH_LENGTH
        ColumnFamilyOptions valuesCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        valuesCfOptions.useFixedLengthPrefixExtractor(1 + KEY_HASH_LENGTH);
        Filter valuesFilter = filterFrom(config.applyToCfOptions(valuesCfOptions, blockCache));

        // Index CFs have composite prefix: 1-byte namespace + Long.BYTES
        ColumnFamilyOptions indexCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        indexCfOptions.useFixedLengthPrefixExtractor(1 + Long.BYTES);
        Filter indexFilter = filterFrom(config.applyToCfOptions(indexCfOptions, blockCache));

        DBOptions dbOptions = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
        config.applyToDbOptions(dbOptions);

        NamespaceOptions namespaceOpts =
            (options.namespace() == null || options.namespace().isBlank())
                ? NamespaceOptions.defaults()
                : NamespaceOptions.columnFamily(options.namespace().trim());
        RocksDbJmtSchema.ColumnFamilies names = RocksDbJmtSchema.columnFamilies(namespaceOpts);

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
            ensureDescriptor(names.metadata(), names.metadata().getBytes(StandardCharsets.UTF_8),
                    defaultCfOptions, descriptors, indexes);
            if (options.enableRollbackIndex()) {
                ensureDescriptor(names.nodesByVersion(), names.nodesByVersion().getBytes(StandardCharsets.UTF_8), indexCfOptions, descriptors, indexes);
                ensureDescriptor(names.valuesByVersion(), names.valuesByVersion().getBytes(StandardCharsets.UTF_8), indexCfOptions, descriptors, indexes);
            }

            db = RocksDB.open(dbOptions, dbPath, descriptors, handles);

            ColumnFamilyHandle nodes = handles.get(indexes.get(names.nodes()));
            ColumnFamilyHandle values = handles.get(indexes.get(names.values()));
            ColumnFamilyHandle roots = handles.get(indexes.get(names.roots()));
            ColumnFamilyHandle stale = handles.get(indexes.get(names.stale()));
            ColumnFamilyHandle metadata = handles.get(indexes.get(names.metadata()));
            ColumnFamilyHandle nodesByVersion = options.enableRollbackIndex() ? handles.get(indexes.get(names.nodesByVersion())) : null;
            ColumnFamilyHandle valuesByVersion = options.enableRollbackIndex() ? handles.get(indexes.get(names.valuesByVersion())) : null;

            List<ColumnFamilyHandle> ownedHandles = new ArrayList<>(handles);
            List<AutoCloseable> ownedResources = new ArrayList<>();
            ownedResources.add(defaultCfOptions);
            ownedResources.add(valuesCfOptions);
            // Always own indexCfOptions: it is allocated unconditionally above, so it must be closed
            // even when the rollback index is disabled (otherwise the native handle leaks).
            ownedResources.add(indexCfOptions);
            ownedResources.add(dbOptions);
            addIfPresent(ownedResources, defaultFilter);
            addIfPresent(ownedResources, valuesFilter);
            addIfPresent(ownedResources, indexFilter);
            addIfPresent(ownedResources, blockCache);

            success = true;
            return new Init(db, nodes, values, roots, stale, metadata, nodesByVersion, valuesByVersion,
                    names, options, true, ownedHandles, ownedResources,
                    new JmtAccessCoordinator());
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
                closeQuietly(defaultFilter);
                closeQuietly(valuesFilter);
                closeQuietly(indexFilter);
                closeQuietly(blockCache);
            }
        }
    }

    private static Init attachInternal(RocksDB db,
                                       Options options,
                                       Map<String, ColumnFamilyHandle> existingHandles,
                                       JmtAccessCoordinator accessCoordinator,
                                       ExternalCoordinatorRegistration registration)
            throws RocksDBException {
        NamespaceOptions namespaceOpts =
            (options.namespace() == null || options.namespace().isBlank())
                ? NamespaceOptions.defaults()
                : NamespaceOptions.columnFamily(options.namespace().trim());
        RocksDbJmtSchema.ColumnFamilies names = RocksDbJmtSchema.columnFamilies(namespaceOpts);

        // Apply RocksDB configuration
        RocksDbConfig config = options.rocksDbConfig();
        org.rocksdb.Cache blockCache = config.createBlockCache();

        // All CFs use 1-byte key prefix for namespace support
        ColumnFamilyOptions defaultCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        defaultCfOptions.useFixedLengthPrefixExtractor(1);
        Filter defaultFilter = filterFrom(config.applyToCfOptions(defaultCfOptions, blockCache));

        // Values CF has composite prefix: 1-byte namespace + KEY_HASH_LENGTH
        ColumnFamilyOptions valuesCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        valuesCfOptions.useFixedLengthPrefixExtractor(1 + KEY_HASH_LENGTH);
        Filter valuesFilter = filterFrom(config.applyToCfOptions(valuesCfOptions, blockCache));

        // Index CFs have composite prefix: 1-byte namespace + Long.BYTES
        ColumnFamilyOptions indexCfOptions = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        indexCfOptions.useFixedLengthPrefixExtractor(1 + Long.BYTES);
        Filter indexFilter = filterFrom(config.applyToCfOptions(indexCfOptions, blockCache));

        List<ColumnFamilyHandle> ownedHandles = new ArrayList<>();
        boolean success = false;
        try {
            Map<String, ColumnFamilyHandle> handles = existingHandles == null ? Map.of() : existingHandles;
            ColumnFamilyHandle nodes = ensureHandle(db, names.nodes(), defaultCfOptions, handles, ownedHandles);
            ColumnFamilyHandle values = ensureHandle(db, names.values(), valuesCfOptions, handles, ownedHandles);
            ColumnFamilyHandle roots = ensureHandle(db, names.roots(), defaultCfOptions, handles, ownedHandles);
            ColumnFamilyHandle stale = ensureHandle(db, names.stale(), defaultCfOptions, handles, ownedHandles);
            ColumnFamilyHandle metadata = ensureHandle(db, names.metadata(), defaultCfOptions,
                    handles, ownedHandles);
            ColumnFamilyHandle nodesByVersion = options.enableRollbackIndex() ? ensureHandle(db, names.nodesByVersion(), indexCfOptions, handles, ownedHandles) : null;
            ColumnFamilyHandle valuesByVersion = options.enableRollbackIndex() ? ensureHandle(db, names.valuesByVersion(), indexCfOptions, handles, ownedHandles) : null;

            List<AutoCloseable> ownedResources = new ArrayList<>();
            ownedResources.add(defaultCfOptions);
            ownedResources.add(valuesCfOptions);
            // Always own indexCfOptions (allocated unconditionally above) to avoid a native leak
            // when the rollback index is disabled.
            ownedResources.add(indexCfOptions);
            addIfPresent(ownedResources, defaultFilter);
            addIfPresent(ownedResources, valuesFilter);
            addIfPresent(ownedResources, indexFilter);
            addIfPresent(ownedResources, blockCache);
            ownedResources.add(registration);

            success = true;
            return new Init(db, nodes, values, roots, stale, metadata, nodesByVersion, valuesByVersion,
                    names, options, false, ownedHandles, ownedResources, accessCoordinator);
        } finally {
            if (!success) {
                ownedHandles.forEach(RocksDbJmtStore::closeQuietly);
                closeQuietly(defaultCfOptions);
                closeQuietly(valuesCfOptions);
                closeQuietly(indexCfOptions);
                closeQuietly(defaultFilter);
                closeQuietly(valuesFilter);
                closeQuietly(indexFilter);
                closeQuietly(blockCache);
            }
        }
    }

    private static Filter filterFrom(org.rocksdb.BlockBasedTableConfig tableConfig) {
        return tableConfig == null ? null : tableConfig.filterPolicy();
    }

    private static void addIfPresent(List<AutoCloseable> resources, AutoCloseable resource) {
        if (resource != null) {
            resources.add(resource);
        }
    }

    private static String externalNamespaceIdentity(Options options) {
        NamespaceOptions namespaceOptions = options.namespace() == null
                || options.namespace().isBlank()
                ? NamespaceOptions.defaults()
                : NamespaceOptions.columnFamily(options.namespace().trim());
        return RocksDbJmtSchema.columnFamilies(namespaceOptions).metadata();
    }

    private static ExternalCoordinatorRegistration registerExternalCoordinator(
            RocksDB db,
            String namespace,
            JmtAccessCoordinator coordinator) {
        synchronized (EXTERNAL_COORDINATORS) {
            Map<String, ExternalCoordinatorBinding> namespaces = EXTERNAL_COORDINATORS
                    .computeIfAbsent(db, ignored -> new HashMap<>());
            ExternalCoordinatorBinding binding = namespaces.get(namespace);
            if (binding == null) {
                namespaces.put(namespace, new ExternalCoordinatorBinding(coordinator));
            } else if (binding.coordinator != coordinator) {
                throw new IllegalArgumentException("Every RocksDbJmtStore wrapper for external "
                        + "database namespace '" + namespace
                        + "' must share the same JmtAccessCoordinator");
            } else {
                binding.references++;
            }
            return new ExternalCoordinatorRegistration(db, namespace, coordinator);
        }
    }

    private static final class ExternalCoordinatorBinding {
        private final JmtAccessCoordinator coordinator;
        private int references = 1;

        private ExternalCoordinatorBinding(JmtAccessCoordinator coordinator) {
            this.coordinator = coordinator;
        }
    }

    private static final class ExternalCoordinatorRegistration implements AutoCloseable {
        private final RocksDB db;
        private final String namespace;
        private final JmtAccessCoordinator coordinator;
        private boolean closed;

        private ExternalCoordinatorRegistration(RocksDB db,
                                                String namespace,
                                                JmtAccessCoordinator coordinator) {
            this.db = db;
            this.namespace = namespace;
            this.coordinator = coordinator;
        }

        @Override
        public void close() {
            synchronized (EXTERNAL_COORDINATORS) {
                if (closed) {
                    return;
                }
                closed = true;
                Map<String, ExternalCoordinatorBinding> namespaces =
                        EXTERNAL_COORDINATORS.get(db);
                if (namespaces == null) {
                    return;
                }
                ExternalCoordinatorBinding binding = namespaces.get(namespace);
                if (binding == null || binding.coordinator != coordinator) {
                    return;
                }
                if (--binding.references == 0) {
                    namespaces.remove(namespace);
                    if (namespaces.isEmpty()) {
                        EXTERNAL_COORDINATORS.remove(db);
                    }
                }
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
        final ColumnFamilyHandle metadata;
        final ColumnFamilyHandle nodesByVersion;
        final ColumnFamilyHandle valuesByVersion;
        final RocksDbJmtSchema.ColumnFamilies names;
        final Options options;
        final boolean ownsDb;
        final List<ColumnFamilyHandle> ownedHandles;
        final List<AutoCloseable> ownedResources;
        final JmtAccessCoordinator accessCoordinator;

        Init(RocksDB db,
             ColumnFamilyHandle nodes,
             ColumnFamilyHandle values,
             ColumnFamilyHandle roots,
             ColumnFamilyHandle stale,
             ColumnFamilyHandle metadata,
             ColumnFamilyHandle nodesByVersion,
             ColumnFamilyHandle valuesByVersion,
             RocksDbJmtSchema.ColumnFamilies names,
             Options options,
             boolean ownsDb,
             List<ColumnFamilyHandle> ownedHandles,
             List<AutoCloseable> ownedResources,
             JmtAccessCoordinator accessCoordinator) {
            this.db = db;
            this.nodes = nodes;
            this.values = values;
            this.roots = roots;
            this.stale = stale;
            this.metadata = metadata;
            this.nodesByVersion = nodesByVersion;
            this.valuesByVersion = valuesByVersion;
            this.names = names;
            this.options = options;
            this.ownsDb = ownsDb;
            this.ownedHandles = ownedHandles == null ? List.of() : List.copyOf(ownedHandles);
            this.ownedResources = ownedResources == null ? List.of() : List.copyOf(ownedResources);
            this.accessCoordinator = Objects.requireNonNull(accessCoordinator, "accessCoordinator");
        }
    }

    private static final class InspectionAccumulator {
        private final int maxRecords;
        private int count;
        private boolean truncated;
        private VersionedRoot latestRoot;
        private final List<VersionedRoot> roots = new ArrayList<>();
        private final List<JmtStoreInspection.NodeRecord> nodes = new ArrayList<>();
        private final List<JmtStoreInspection.ValueRecord> values = new ArrayList<>();
        private final List<JmtStoreInspection.StaleRecord> stale = new ArrayList<>();
        private final List<String> backendIssues = new ArrayList<>();
        private final Set<String> expectedNodeIndexes = new HashSet<>();
        private final Set<String> expectedValueIndexes = new HashSet<>();

        private InspectionAccumulator(int maxRecords) {
            this.maxRecords = maxRecords;
        }

        private boolean take() {
            if (count >= maxRecords) {
                truncated = true;
                return false;
            }
            count++;
            return true;
        }

        private JmtStoreInspection snapshot() {
            return new JmtStoreInspection(roots, latestRoot, nodes, values, stale,
                    backendIssues, truncated);
        }
    }
}
