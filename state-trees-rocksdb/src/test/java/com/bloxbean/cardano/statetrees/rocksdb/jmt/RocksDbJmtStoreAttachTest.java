package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStoreConfig;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbJmtStoreAttachTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    @Test
    void attachCreatesColumnFamiliesForNewDb() throws Exception {
        Path dbPath = tempDir.resolve("jmt-shared-db");

        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, dbPath.toString());
             RocksDbJmtStore store = RocksDbJmtStore.attach(db, "shared")) {

            JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
                    .enableNodeCache(true).nodeCacheSize(128)
                    .enableValueCache(true).valueCacheSize(128)
                    .build();

            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));
            tree.commit(1, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("300"));
            updates.put(bytes("bob"), bytes("400"));
            tree.commit(3, updates);

            assertTrue(store.latestRoot().isPresent());
            assertArrayEquals(bytes("200"), tree.get(bytes("bob"), 1L));
            assertArrayEquals(bytes("400"), tree.get(bytes("bob")));
        }
    }

    @Test
    void attachReusesExistingColumnFamilies() throws Exception {
        Path dbPath = tempDir.resolve("jmt-shared-reuse");
        String namespace = "app";

        // Seed RocksDB using the standalone factory so column families exist on disk.
        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString(), namespace)) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("carol"), bytes("300"));
            tree.commit(1, updates);
            assertArrayEquals(bytes("300"), tree.get(bytes("carol")));
        }

        // Reopen RocksDB externally and attach using existing handles.
        RocksDbJmtStore.ColumnFamilies cfNames = RocksDbJmtStore.columnFamilies(namespace);
        List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
        List<ColumnFamilyOptions> cfOptions = new ArrayList<>();
        descriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptionsFor("default", cfNames, cfOptions)));
        descriptors.add(new ColumnFamilyDescriptor(cfNames.nodes().getBytes(StandardCharsets.UTF_8), cfOptionsFor(cfNames.nodes(), cfNames, cfOptions)));
        descriptors.add(new ColumnFamilyDescriptor(cfNames.values().getBytes(StandardCharsets.UTF_8), cfOptionsFor(cfNames.values(), cfNames, cfOptions)));
        descriptors.add(new ColumnFamilyDescriptor(cfNames.roots().getBytes(StandardCharsets.UTF_8), cfOptionsFor(cfNames.roots(), cfNames, cfOptions)));
        descriptors.add(new ColumnFamilyDescriptor(cfNames.stale().getBytes(StandardCharsets.UTF_8), cfOptionsFor(cfNames.stale(), cfNames, cfOptions)));

        Map<String, ColumnFamilyHandle> handlesMap = new HashMap<>();
        ListAutoCloseable<ColumnFamilyHandle> handles = new ListAutoCloseable<>();
        try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(dbOptions, dbPath.toString(), descriptors, handles)) {

            handlesMap.put(cfNames.nodes(), handles.get(1));
            handlesMap.put(cfNames.values(), handles.get(2));
            handlesMap.put(cfNames.roots(), handles.get(3));
            handlesMap.put(cfNames.stale(), handles.get(4));

            try (RocksDbJmtStore store = RocksDbJmtStore.attach(db, namespace, handlesMap)) {
                JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH);
                assertArrayEquals(bytes("300"), tree.get(bytes("carol")));

                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                updates.put(bytes("dave"), bytes("400"));
                tree.commit(2, updates);
                assertArrayEquals(bytes("400"), tree.get(bytes("dave")));
                assertEquals(2L, store.latestRoot().orElseThrow().version());
            }
        } finally {
            handles.close();
            cfOptions.forEach(RocksDbJmtStoreAttachTest::closeQuietly);
        }
    }

    @Test
    void historicalReadsAndDeletes() throws Exception {
        Path dbPath = tempDir.resolve("jmt-history-db");
        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString())) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            tree.commit(1, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("200"));
            tree.commit(2, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), null);
            tree.commit(3, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("300"));
            tree.commit(4, updates);

            assertArrayEquals(bytes("100"), tree.get(bytes("alice"), 1));
            assertArrayEquals(bytes("200"), tree.get(bytes("alice"), 2));
            assertEquals(null, tree.get(bytes("alice"), 3));
            assertArrayEquals(bytes("300"), tree.get(bytes("alice")));
        }
    }

    @Test
    void prunePoliciesAffectHistoricalValues() throws Exception {
        Path safePath = tempDir.resolve("jmt-prune-safe");
        try (RocksDbJmtStore store = RocksDbJmtStore.open(safePath.toString())) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("carol"), bytes("100"));
            tree.commit(1, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("carol"), bytes("200"));
            tree.commit(3, updates);

            JellyfishMerkleTreeStore.PruneReport report = tree.prune(1);
            assertEquals(0, report.nodesPruned());

            assertArrayEquals(bytes("100"), tree.get(bytes("carol"), 2));
            assertArrayEquals(bytes("200"), tree.get(bytes("carol")));
        }

        Path aggressivePath = tempDir.resolve("jmt-prune-aggressive");
        RocksDbJmtStore.Options aggressive = RocksDbJmtStore.Options.builder()
                .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.AGGRESSIVE)
                .build();
        try (RocksDbJmtStore store = RocksDbJmtStore.open(aggressivePath.toString(), aggressive)) {
            assertEquals(RocksDbJmtStore.ValuePrunePolicy.AGGRESSIVE,
                    getPrunePolicy(store));
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("dave"), bytes("100"));
            tree.commit(1, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("dave"), bytes("200"));
            tree.commit(3, updates);

            JellyfishMerkleTreeStore.PruneReport aggressiveReport = tree.prune(1);
            assertTrue(aggressiveReport.nodesPruned() > 0);
            byte[] historical = tree.get(bytes("dave"), 1);
            assertNull(historical, "Aggressive prune should drop historical value");
            assertArrayEquals(bytes("200"), tree.get(bytes("dave")));
        }
    }

    private static RocksDbJmtStore.ValuePrunePolicy getPrunePolicy(RocksDbJmtStore store) throws Exception {
        java.lang.reflect.Field field = RocksDbJmtStore.class.getDeclaredField("storeOptions");
        field.setAccessible(true);
        RocksDbJmtStore.Options options = (RocksDbJmtStore.Options) field.get(store);
        return options.prunePolicy();
    }

    @Test
    void truncateAfterRemovesFutureVersions() throws Exception {
        Path dbPath = tempDir.resolve("jmt-truncate-db");
        RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
                .enableRollbackIndex(true)
                .build();
        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString(), options)) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, JellyfishMerkleTreeStoreConfig.defaults());

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("erin"), bytes("100"));
            tree.commit(1, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("erin"), bytes("150"));
            tree.commit(2, updates);

            updates = new LinkedHashMap<>();
            updates.put(bytes("erin"), bytes("200"));
            tree.commit(3, updates);

            tree.truncateAfter(2);

            assertEquals(2L, store.latestRoot().orElseThrow().version());
            assertArrayEquals(bytes("150"), tree.get(bytes("erin")));
            assertEquals(null, tree.get(bytes("erin"), 3));

            updates = new LinkedHashMap<>();
            updates.put(bytes("erin"), bytes("180"));
            tree.commit(3, updates);

            assertArrayEquals(bytes("180"), tree.get(bytes("erin")));
            assertArrayEquals(bytes("150"), tree.get(bytes("erin"), 2));
        }
    }

    private static ColumnFamilyOptions cfOptionsFor(String name,
                                                    RocksDbJmtStore.ColumnFamilies cfNames,
                                                    List<ColumnFamilyOptions> created) {
        ColumnFamilyOptions opts = new ColumnFamilyOptions().setOptimizeFiltersForHits(true);
        if (name.equals(cfNames.values())) {
            opts.useFixedLengthPrefixExtractor(32);
        }
        created.add(opts);
        return opts;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class ListAutoCloseable<T extends AutoCloseable> extends ArrayList<T> implements AutoCloseable {
        @Override
        public void close() {
            for (T item : this) {
                closeQuietly(item);
            }
            this.clear();
        }
    }
}
