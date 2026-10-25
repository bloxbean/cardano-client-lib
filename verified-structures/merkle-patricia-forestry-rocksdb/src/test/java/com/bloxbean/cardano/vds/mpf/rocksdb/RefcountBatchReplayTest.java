package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RefcountLedger;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.vds.mpf.rocksdb.GcTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The static refcount helpers replay the WriteBatch they stage into (Codex review round 3). Every test runs on the
 * default column family and on a named one, in key-prefix namespace 0x05.
 */
class RefcountBatchReplayTest {

    @TempDir
    Path tmp;

    private static final byte NS = 0x05;

    private final class Fixture implements AutoCloseable {
        final RocksDbStateTrees st;
        final RocksDB db;
        final ColumnFamilyHandle cf;
        final KeyPrefixer kp = new KeyPrefixer(NS);
        final RocksDbNodeStore store;

        Fixture(boolean defaultCf) {
            st = new RocksDbStateTrees(tmp.resolve(defaultCf ? "default" : "named").toString());
            db = st.db();
            cf = defaultCf ? db.getDefaultColumnFamily() : st.nodeStore().nodesHandle();
            store = new RocksDbNodeStore(db, cf, kp);
        }

        long increment(byte[] root, WriteBatch wb) throws Exception {
            return RefcountGcStrategy.incrementAll(db, cf, cf, root, wb, kp);
        }

        long[] decrement(byte[] root, WriteBatch wb) throws Exception {
            return RefcountGcStrategy.decrementAll(db, cf, cf, root, wb, kp);
        }

        void write(WriteBatch wb) throws Exception {
            try (WriteOptions wo = new WriteOptions()) {
                db.write(wo, wb);
            }
        }

        byte[] ref(byte[] hash) {
            return kp.prefix(RefcountLedger.refKey(hash));
        }

        @Override
        public void close() {
            st.close();
        }
    }

    private static Map<String, byte[]> twelveKeys(MpfTrie trie) {
        Map<String, byte[]> model = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            trie.put(b("key-" + i), b("value-" + i));
            model.put(hex("key-" + i), b("value-" + i));
        }
        return model;
    }

    private static byte[] num(long v) {
        return ByteBuffer.allocate(8).putLong(v).array();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nodesOnlyInTheBatchSurviveIncrementDecrementIncrement(boolean defaultCf) throws Exception {
        try (Fixture f = new Fixture(defaultCf); WriteBatch wb = new WriteBatch()) {
            Map<String, byte[]> model = new LinkedHashMap<>();
            byte[] root = f.store.withBatch(wb, () -> {
                MpfTrie trie = new MpfTrie(f.store);
                model.putAll(twelveKeys(trie));
                return trie.getRootHash();
            });
            f.increment(root, wb);
            f.decrement(root, wb);
            f.increment(root, wb);
            f.write(wb);
            assertIntact(f.store, root, model, "root");
            assertEquals(reach(f.store, root)[0], countRefcountKeys(f.store));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedNewChildrenAreRestoredWhenSwitchingRootsInOneBatch(boolean defaultCf) throws Exception {
        // Codex P1: roots A and B share children that exist only in the batch; incrementAll(A), decrementAll(A)
        // deleted them, and incrementAll(B) could not restore them because their bytes were not in the DB.
        try (Fixture f = new Fixture(defaultCf); WriteBatch wb = new WriteBatch()) {
            byte[][] roots = new byte[2][];
            Map<String, byte[]> modelB = new LinkedHashMap<>();
            f.store.withBatch(wb, () -> {
                MpfTrie trie = new MpfTrie(f.store);
                modelB.putAll(twelveKeys(trie));
                roots[0] = trie.getRootHash();
                trie.put(b("key-0"), b("updated"));
                modelB.put(hex("key-0"), b("updated"));
                roots[1] = trie.getRootHash();
                return null;
            });
            f.increment(roots[0], wb);
            f.decrement(roots[0], wb);
            f.increment(roots[1], wb);
            f.write(wb);
            assertIntact(f.store, roots[1], modelB, "B");
            assertEquals(reach(f.store, roots[1])[0], countRefcountKeys(f.store));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mergeRecordsOnTheNamespaceAreRejectedBeforeStaging(boolean defaultCf) throws Exception {
        try (Fixture f = new Fixture(defaultCf); WriteBatch wb = new WriteBatch()) {
            MpfTrie trie = new MpfTrie(f.store);
            twelveKeys(trie);
            byte[] root = trie.getRootHash();
            if (defaultCf) wb.merge(f.ref(root), num(1));
            else wb.merge(f.cf, f.ref(root), num(1));
            int before = wb.count();
            assertThrows(IllegalStateException.class, () -> f.increment(root, wb));
            assertThrows(IllegalStateException.class, () -> f.decrement(root, wb));
            assertEquals(before, wb.count(), "nothing may be staged");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deleteRangesOverlappingTheNamespaceAreRejectedBeforeStaging(boolean defaultCf) throws Exception {
        try (Fixture f = new Fixture(defaultCf)) {
            MpfTrie trie = new MpfTrie(f.store);
            twelveKeys(trie);
            byte[] root = trie.getRootHash();
            byte[] ref = f.ref(root);
            List<byte[][]> ranges = List.of(
                    new byte[][]{{NS}, {NS + 1}},
                    new byte[][]{ref, Arrays.copyOf(ref, ref.length + 1)},
                    new byte[][]{{0x00}, {(byte) 0xFF, (byte) 0xFF}});
            for (byte[][] range : ranges) {
                try (WriteBatch wb = new WriteBatch()) {
                    if (defaultCf) wb.deleteRange(range[0], range[1]);
                    else wb.deleteRange(f.cf, range[0], range[1]);
                    assertThrows(IllegalStateException.class, () -> f.increment(root, wb));
                    assertEquals(1, wb.count(), "nothing may be staged");
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recordsOfOtherNamespacesOtherColumnFamiliesAndLogDataAreIgnored(boolean defaultCf) throws Exception {
        try (Fixture f = new Fixture(defaultCf); WriteBatch wb = new WriteBatch()) {
            MpfTrie trie = new MpfTrie(f.store);
            Map<String, byte[]> model = twelveKeys(trie);
            byte[] root = trie.getRootHash();
            ColumnFamilyHandle otherCf = defaultCf ? f.st.nodeStore().nodesHandle() : f.db.getDefaultColumnFamily();
            byte[] otherNsKey = new KeyPrefixer((byte) (NS + 1)).prefix(b("x"));
            wb.merge(f.cf, otherNsKey, num(1));                           // same CF, other namespace
            wb.deleteRange(f.cf, new byte[]{NS + 1}, new byte[]{NS + 2});  // same CF, range after the namespace
            wb.deleteRange(f.cf, new byte[]{0x00}, new byte[]{NS});        // same CF, range before the namespace
            wb.merge(otherCf, f.ref(root), num(1));                        // other CF, same key
            wb.deleteRange(otherCf, new byte[]{0x00}, new byte[]{(byte) 0xFF});
            wb.putLogData(b("note"));
            f.increment(root, wb);
            // drop the foreign records again before committing (they would need a merge operator)
            try (WriteBatch clean = new WriteBatch()) {
                f.increment(root, clean);
                f.write(clean);
            }
            assertIntact(f.store, root, model, "root");
            assertEquals(reach(f.store, root)[0], countRefcountKeys(f.store));
        }
    }

    @Test
    void nodeDeletedInTheBatchWithoutAnyBytesFailsFast() throws Exception {
        try (Fixture f = new Fixture(false)) {
            byte[][] rootAndChild = new byte[2][];
            byte[][] rootBytes = new byte[1][];
            try (WriteBatch staging = new WriteBatch()) {
                f.store.withBatch(staging, () -> {
                    MpfTrie trie = new MpfTrie(f.store);
                    twelveKeys(trie);
                    rootAndChild[0] = trie.getRootHash();
                    rootBytes[0] = f.store.get(rootAndChild[0]);
                    rootAndChild[1] = NodeRefParser.childRefs(rootBytes[0]).get(0);
                    return null;
                }); // never written: the nodes exist nowhere
            }
            try (WriteBatch wb = new WriteBatch()) {
                wb.put(f.cf, f.kp.prefix(rootAndChild[0]), rootBytes[0]);
                wb.delete(f.cf, f.kp.prefix(rootAndChild[1]));
                assertThrows(IllegalStateException.class, () -> f.increment(rootAndChild[0], wb));
            }
        }
    }
}
