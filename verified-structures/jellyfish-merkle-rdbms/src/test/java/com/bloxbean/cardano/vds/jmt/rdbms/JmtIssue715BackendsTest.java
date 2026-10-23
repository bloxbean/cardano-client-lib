package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.JmtProofVerifier;
import com.bloxbean.cardano.vds.jmt.metrics.JmtMetrics;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtFormatMismatchException;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStoreInspection;
import com.bloxbean.cardano.vds.jmt.store.JmtWriteConflictException;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the JMT items of issue #715 (3 and 4), on every bundled backend. Ported from the
 * reproduction harness that showed the old behaviour on {@code 644e717b}.
 */
class JmtIssue715BackendsTest {

    private static final JmtProfile PROFILE = JmtProfile.classicBlake2b256V1();

    enum Backend { MEMORY, ROCKSDB, H2, SQLITE }

    @TempDir
    Path temp;

    // ---------------------------------------------------------------- #3: absence proofs prove no value

    @ParameterizedTest
    @EnumSource(Backend.class)
    void absenceProofsDoNotProveAnyValue(Backend backend) throws Exception {
        try (Harness h = open(backend)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store);
            byte[] emptyRoot = tree.put(0, Map.of()).rootHash();
            checkAbsence(tree, emptyRoot, "bob", 0, JmtProof.ProofType.NON_INCLUSION_EMPTY);

            byte[] oneLeafRoot = tree.put(1, updates("alice", "100")).rootHash();
            checkAbsence(tree, oneLeafRoot, "bob", 1, JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF);

            JmtProof inclusion = tree.getProof(bytes("alice"), 1).orElseThrow();
            assertTrue(verify(oneLeafRoot, "alice", bytes("100"), inclusion));
            assertFalse(verify(oneLeafRoot, "alice", bytes("999999"), inclusion));
            assertFalse(verify(oneLeafRoot, "alice", null, inclusion), "an inclusion proof does not prove absence");
        }
    }

    private static void checkAbsence(JellyfishMerkleTree tree, byte[] root, String missingKey, long version,
                                     JmtProof.ProofType expectedType) {
        JmtProof proof = tree.getProof(bytes(missingKey), version).orElseThrow();
        assertEquals(expectedType, proof.type());
        assertNull(proof.value());
        assertTrue(verify(root, missingKey, null, proof));
        assertFalse(verify(root, missingKey, bytes("999999"), proof));
        assertFalse(verify(root, missingKey, new byte[0], proof));
        byte[] wire = tree.getProofWire(bytes(missingKey), version).orElseThrow();
        assertTrue(tree.verifyProofWire(root, bytes(missingKey), null, false, wire));
        assertFalse(tree.verifyProofWire(root, bytes(missingKey), bytes("999999"), true, wire));
    }

    // ---------------------------------------------------------------- #3 related: which versions get() answers

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getAnswersAsOfCommittedVersionsAndRejectsFutureVersions(Backend backend) throws Exception {
        try (Harness h = open(backend)) {
            assertEquals(OptionalLong.empty(), h.store.pruneWatermark(), "a fresh namespace has no watermark");
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store);
            assertTrue(tree.get(bytes("alice"), 5).isEmpty(), "no commit yet: every key is absent");

            tree.put(1, updates("alice", "100", "bob", "200"));
            tree.put(3, updates("alice", "150"));
            assertTrue(tree.get(bytes("alice"), 0).isEmpty(), "before the first commit");
            assertArrayEquals(bytes("100"), tree.get(bytes("alice"), 2).orElseThrow(), "gap reads the earlier commit");
            assertArrayEquals(bytes("150"), tree.get(bytes("alice"), 3).orElseThrow());
            assertTrue(tree.getProof(bytes("alice"), 2).isEmpty(), "proofs need a root at exactly that version");

            IllegalArgumentException future = assertThrows(IllegalArgumentException.class,
                    () -> tree.get(bytes("alice"), 4));
            assertTrue(future.getMessage().contains("newer than the latest"), future.getMessage());
            assertThrows(IllegalArgumentException.class, () -> tree.get(bytes("alice"), 99));
            assertThrows(IllegalArgumentException.class, () -> tree.get(bytes("alice"), -1));
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void getRejectsVersionsBelowThePruneWatermark(Backend backend) throws Exception {
        try (Harness h = open(backend)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store);
            tree.put(1, updates("alice", "100", "bob", "200"));
            byte[] retainedRoot = tree.put(3, updates("alice", "150")).rootHash();
            tree.put(4, updates("carol", "300"));
            assertEquals(OptionalLong.empty(), h.store.pruneWatermark());

            assertTrue(tree.pruneUpTo(3) > 0);
            assertEquals(OptionalLong.of(3), h.store.pruneWatermark());

            // Below the horizon the old partial answers (alice empty, bob 200) are rejected.
            for (long v : new long[]{0, 1, 2}) {
                IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                        () -> tree.get(bytes("alice"), v));
                assertTrue(e.getMessage().contains("prune watermark"), e.getMessage());
            }
            assertArrayEquals(bytes("150"), tree.get(bytes("alice"), 3).orElseThrow());
            assertArrayEquals(bytes("200"), tree.get(bytes("bob"), 3).orElseThrow());
            assertArrayEquals(bytes("300"), tree.get(bytes("carol"), 4).orElseThrow());
            assertArrayEquals(retainedRoot, h.store.rootHash(3).orElseThrow());
            for (String key : List.of("alice", "bob")) {
                JmtProof proof = tree.getProof(bytes(key), 3).orElseThrow();
                assertTrue(verify(retainedRoot, key, proof.value(), proof), key);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void pruningBetweenCommitsKeepsTheRootTheHorizonIsReadFrom(Backend backend) throws Exception {
        // Found by JmtFuzzTest and review: pruning at a horizon between two commits deleted the root the horizon's
        // state is read from, so that state was unprovable and rollback to it impossible (or, before the truncate
        // guard, left the store without any root).
        try (Harness h = open(backend)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store);
            tree.put(100, updates("alice", "a1", "bob", "b1"));
            byte[] root120 = tree.put(120, updates("alice", "a2")).rootHash();
            tree.put(140, updates("bob", "b2"));

            assertTrue(tree.pruneUpTo(130) > 0);
            assertEquals(OptionalLong.of(120), h.store.pruneWatermark(), "the floor root is the effective horizon");
            assertTrue(h.store.rootHash(100).isEmpty());
            assertArrayEquals(root120, h.store.rootHash(120).orElseThrow());
            assertArrayEquals(bytes("a2"), tree.get(bytes("alice"), 130).orElseThrow());
            JmtProof proof = tree.getProof(bytes("bob"), 120).orElseThrow();
            assertTrue(verify(root120, "bob", bytes("b1"), proof));
            assertThrows(IllegalArgumentException.class, () -> tree.get(bytes("alice"), 119));

            h.store.truncateAfter(130); // back to the state as of 130 = commit 120
            assertEquals(120L, h.store.latestRoot().orElseThrow().version());
            assertArrayEquals(bytes("b1"), tree.get(bytes("bob"), 120).orElseThrow());
            IllegalStateException below = assertThrows(IllegalStateException.class, () -> h.store.truncateAfter(110));
            assertTrue(below.getMessage().contains("watermark"), below.getMessage());
        }
    }

    @Test
    void aggressivePrunePolicyOnlyAnswersFromTheLatestVersion() throws Exception {
        // AGGRESSIVE drops a key's horizon value once a newer value exists, so older states are incomplete.
        try (RocksDbJmtStore store = RocksDbJmtStore.open(temp.resolve("aggressive").toString(),
                RocksDbJmtStore.Options.builder().enableRollbackIndex(true)
                        .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.AGGRESSIVE).build())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            tree.put(1, updates("k", "a"));
            tree.put(2, updates("x", "1"));
            tree.put(3, updates("y", "1"));
            tree.put(4, updates("k", "b"));
            assertTrue(tree.pruneUpTo(2) > 0);
            assertEquals(OptionalLong.of(4), store.pruneWatermark());
            assertThrows(IllegalArgumentException.class, () -> tree.get(bytes("k"), 3), "k's value as of 3 was dropped");
            assertTrue(store.rootHash(3).isPresent(), "AGGRESSIVE keeps the roots between horizon and latest");
            assertTrue(tree.getProof(bytes("k"), 3).isEmpty(), "no proof claiming a value that is gone");
            assertArrayEquals(bytes("b"), tree.get(bytes("k"), 4).orElseThrow());
            assertThrows(IllegalStateException.class, () -> store.truncateAfter(3));
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void replayOfAnEmptyCommitAfterPruningToItStillWorks(Backend backend) throws Exception {
        try (Harness h = open(backend)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store);
            tree.put(0, updates("k", "a"));
            tree.put(1, updates("k", "b"));
            byte[] root2 = tree.put(2, Map.of()).rootHash();
            assertTrue(tree.pruneUpTo(2) > 0);
            JellyfishMerkleTree.CommitResult replay = tree.put(2, Map.of());
            assertArrayEquals(root2, replay.rootHash());
            assertTrue(replay.nodes().isEmpty());
        }
    }

    // ---------------------------------------------------------------- #4: constructors

    @ParameterizedTest
    @EnumSource(value = Backend.class, names = {"ROCKSDB", "H2", "SQLITE"})
    @SuppressWarnings("deprecation")
    void persistentStoresNeedAProfileAndTheDefaultConstructorUsesIt(Backend backend) throws Exception {
        try (Harness h = open(backend)) {
            JmtFormatMismatchException failure = assertThrows(JmtFormatMismatchException.class,
                    () -> new JellyfishMerkleTree(h.store, Blake2b256::digest));
            assertTrue(failure.getMessage().contains("JmtProfile"), failure.getMessage());
            new JellyfishMerkleTree(h.store).put(1, updates("alice", "100"));
            assertArrayEquals(bytes("100"), new JellyfishMerkleTree(h.store, PROFILE).get(bytes("alice")).orElseThrow());
            assertEquals(PROFILE.format(), h.store.formatDescriptor().orElseThrow());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedInMemoryConstructorCommitsTheSameRootsAsTheProfile() {
        try (InMemoryJmtStore legacy = new InMemoryJmtStore(); InMemoryJmtStore profiled = new InMemoryJmtStore()) {
            JellyfishMerkleTree a = new JellyfishMerkleTree(legacy, Blake2b256::digest);
            JellyfishMerkleTree b = new JellyfishMerkleTree(profiled);
            for (int v = 0; v < 5; v++) {
                Map<byte[], byte[]> u = updates("k" + v, "v" + v, "shared", "s" + v);
                assertArrayEquals(a.put(v, u).rootHash(), b.put(v, u).rootHash());
                assertArrayEquals(a.getProofWire(bytes("shared"), v).orElseThrow(),
                        b.getProofWire(bytes("shared"), v).orElseThrow());
            }
            assertFalse(legacy.formatDescriptor().orElseThrow().isPersistent());
        }
    }

    // ---------------------------------------------------------------- #4 related: replay and metrics

    @ParameterizedTest
    @EnumSource(Backend.class)
    void identicalReplayReportsNoWorkAndRecordsNoMetrics(Backend backend) throws Exception {
        try (Harness h = open(backend)) {
            CountingMetrics metrics = new CountingMetrics();
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store, PROFILE, metrics);
            tree.put(1, updates("alice", "100", "bob", "200"));
            Map<byte[], byte[]> change = updates("alice", "150");
            JellyfishMerkleTree.CommitResult original = tree.put(3, change);
            assertFalse(original.nodes().isEmpty());
            List<String> before = fingerprint(h.store);
            int commitsBefore = metrics.commits;

            JellyfishMerkleTree.CommitResult replay = tree.put(3, change);
            assertEquals(before, fingerprint(h.store), "replay changes nothing");
            assertArrayEquals(original.rootHash(), replay.rootHash());
            assertEquals(3, replay.version());
            assertTrue(replay.nodes().isEmpty());
            assertTrue(replay.staleNodes().isEmpty());
            assertTrue(replay.valueOperations().isEmpty());
            assertEquals(commitsBefore, metrics.commits, "no commit metrics for a replay");

            assertThrows(JmtWriteConflictException.class, () -> tree.put(3, updates("alice", "999")),
                    "a replay with another root is rejected");
            assertEquals(before, fingerprint(h.store));
            assertThrows(IllegalArgumentException.class, () -> tree.put(1, updates("alice", "100", "bob", "200")),
                    "older versions cannot be replayed");

            JellyfishMerkleTree.CommitResult next = tree.put(4, updates("carol", "300"));
            assertFalse(next.nodes().isEmpty());
            assertEquals(commitsBefore + 1, metrics.commits);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void replayAfterPruningItsBaseIsRejectedWithoutChanges(Backend backend) throws Exception {
        // Found by JmtFuzzTest: the replay rebuilt version 1 on pruned state and reported a "different root".
        try (Harness h = open(backend)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store);
            tree.put(0, updates("alice", "100", "bob", "200"));
            Map<byte[], byte[]> change = updates("alice", "150");
            tree.put(1, change);
            assertTrue(tree.pruneUpTo(1) > 0);
            List<String> before = fingerprint(h.store);
            JmtWriteConflictException e = assertThrows(JmtWriteConflictException.class, () -> tree.put(1, change));
            assertTrue(e.getMessage().contains("cannot be replayed") && e.getMessage().contains("pruned"), e.getMessage());
            assertEquals(before, fingerprint(h.store));
            assertFalse(tree.put(2, updates("carol", "300")).nodes().isEmpty(), "the next version still commits");
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void treePruneRecordsPruneMetrics(Backend backend) throws Exception {
        try (Harness h = open(backend)) {
            CountingMetrics metrics = new CountingMetrics();
            JellyfishMerkleTree tree = new JellyfishMerkleTree(h.store, PROFILE, metrics);
            tree.put(1, updates("alice", "100", "bob", "200"));
            tree.put(3, updates("alice", "150"));
            int pruned = tree.pruneUpTo(3);
            assertTrue(pruned > 0);
            assertEquals(1, metrics.prunes);
            assertEquals(3, metrics.lastPruneVersion);
            assertEquals(pruned, metrics.lastPruned);
        }
    }

    @Test
    void rocksDbStoreRecordsRocksDbStats() throws Exception {
        try (Harness h = open(Backend.ROCKSDB)) {
            new JellyfishMerkleTree(h.store).put(1, updates("alice", "100"));
            CountingMetrics metrics = new CountingMetrics();
            ((RocksDbJmtStore) h.store).recordStats(metrics);
            assertEquals(1, metrics.rocksDbStats);
            assertTrue(metrics.lastMemTableSize > 0, "the commit is in the active memtable");
        }
    }

    // ---------------------------------------------------------------- helpers

    private Harness open(Backend backend) {
        switch (backend) {
            case MEMORY:
                return new Harness(new InMemoryJmtStore(), null);
            case ROCKSDB:
                return new Harness(RocksDbJmtStore.open(temp.resolve("rocks-" + UUID.randomUUID()).toString(),
                        RocksDbJmtStore.Options.production()), null);
            default:
                String url = backend == Backend.H2
                        ? "jdbc:h2:mem:jmt715_" + UUID.randomUUID().toString().replace('-', '_') + ";DB_CLOSE_DELAY=-1"
                        : "jdbc:sqlite:" + temp.resolve("sqlite-" + UUID.randomUUID() + ".db");
                DbConfig config = DbConfig.builder().simpleJdbcUrl(url).build();
                try {
                    RdbmsJmtSchemaInstaller.createIfNotExists(config);
                    return new Harness(new RdbmsJmtStore(config), config);
                } catch (RuntimeException e) {
                    config.close();
                    throw e;
                }
        }
    }

    private static boolean verify(byte[] root, String key, byte[] value, JmtProof proof) {
        return JmtProofVerifier.verify(root, bytes(key), value, proof, PROFILE.hashFunction(), PROFILE.commitmentScheme());
    }

    private static List<String> fingerprint(JmtStore store) {
        JmtStoreInspection inspection = store.inspect(10000);
        assertFalse(inspection.truncated());
        assertTrue(inspection.backendIssues().isEmpty());
        List<String> records = new ArrayList<>();
        inspection.roots().forEach(r -> records.add("root " + r.version() + " " + hex(r.rootHash())));
        inspection.latestRoot().ifPresent(r -> records.add("latest " + r.version() + " " + hex(r.rootHash())));
        inspection.nodes().forEach(n -> records.add("node " + hex(n.nodeKey().toBytes()) + " " + hex(n.node().encode())));
        inspection.values().forEach(v -> records.add("value " + hex(v.keyHash()) + " " + v.version() + " "
                + hex(v.value()) + " " + v.tombstone()));
        inspection.staleRecords().forEach(s -> records.add("stale " + s.staleSince() + " " + hex(s.nodeKey().toBytes())));
        Collections.sort(records);
        return records;
    }

    private static Map<byte[], byte[]> updates(String... pairs) {
        Map<byte[], byte[]> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(bytes(pairs[i]), bytes(pairs[i + 1]));
        return values;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] value) {
        return value == null ? "null" : HexFormat.of().formatHex(value);
    }

    private static final class Harness implements AutoCloseable {
        final JmtStore store;
        final DbConfig config;

        Harness(JmtStore store, DbConfig config) {
            this.store = store;
            this.config = config;
        }

        @Override
        public void close() throws Exception {
            try {
                store.close();
                if (config != null) RdbmsJmtSchemaInstallerTest.shutdownH2(config);
            } finally {
                if (config != null) config.close();
            }
        }
    }

    private static final class CountingMetrics implements JmtMetrics {
        int commits;
        int prunes;
        long lastPruneVersion = -1;
        int lastPruned;
        int rocksDbStats;
        long lastMemTableSize;

        @Override
        public void recordCommit(long durationMillis, long version, int batchSize, int nodesWritten, int nodesStale) {
            commits++;
        }

        @Override
        public void recordProofGeneration(long durationMillis, int proofSize, boolean found) {
        }

        @Override
        public void recordRead(long durationMillis, boolean cacheHit, boolean found) {
        }

        @Override
        public void recordPrune(long durationMillis, long versionPruned, int nodesPruned, int valuesPruned) {
            prunes++;
            lastPruneVersion = versionPruned;
            lastPruned = nodesPruned + valuesPruned;
        }

        @Override
        public void recordStorageStats(long version, int rootHashSize, long nodeCount, long valueCount) {
        }

        @Override
        public void recordCacheStats(long cacheHits, long cacheMisses, int cacheSize) {
        }

        @Override
        public void recordRocksDbStats(long pendingCompactionBytes, int runningCompactions, long memTableSize,
                                       int immutableMemTables) {
            rocksDbStats++;
            lastMemTableSize = memTableSize;
        }
    }
}
