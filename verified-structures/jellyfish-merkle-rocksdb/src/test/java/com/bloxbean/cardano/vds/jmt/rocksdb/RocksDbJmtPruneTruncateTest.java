package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityChecker;
import com.bloxbean.cardano.vds.jmt.integrity.JmtIntegrityMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the multi-key pruning and multi-version rollback bugs:
 * prefixSameAsStart iterators used to truncate whole-CF / version-range scans to a single
 * prefix group, so prune reclaimed only the first key and truncateAfter removed only one version.
 */
class RocksDbJmtPruneTruncateTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    private static byte[] bytes(String v) {
        return v.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void pruneReclaimsValueHistoryForEveryKeyNotJustTheFirst() {
        String[] keys = {"alpha", "bravo", "charlie", "delta", "echo"};
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("prune-db").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);

            // 3 versions; every key rewritten in every version.
            for (long version = 1; version <= 3; version++) {
                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                for (String k : keys) {
                    updates.put(bytes(k), bytes(k + "-v" + version));
                }
                tree.put(version, updates);
            }

            // Sanity: version-1 value visible for every key before pruning.
            for (String k : keys) {
                assertArrayEquals(bytes(k + "-v1"), store.getValueAt(HASH.digest(bytes(k)), 1).orElseThrow(), k);
            }

            store.pruneUpTo(2);

            assertTrue(store.rootHash(1).isEmpty(),
                    "structurally pruned versions must not retain queryable roots");

            for (String k : keys) {
                byte[] kh = HASH.digest(bytes(k));
                // version 1 history reclaimed for EVERY key (the bug left all but the first intact).
                assertTrue(store.getValueAt(kh, 1).isEmpty(), "v1 history should be pruned for " + k);
                // version 2 retained (SAFE sentinel), version 3 and latest intact.
                assertArrayEquals(bytes(k + "-v2"), store.getValueAt(kh, 2).orElseThrow(), k);
                assertArrayEquals(bytes(k + "-v3"), store.getValueAt(kh, 3).orElseThrow(), k);
                assertArrayEquals(bytes(k + "-v3"), store.getValue(kh).orElseThrow(), k);
            }
        }
    }

    @Test
    void latestReplayAndPruneCannotDeleteLiveNodes() {
        Path dbPath = tempDir.resolve("replay-prune-db");
        try (RocksDbJmtStore store = RocksDbJmtStore.open(
                dbPath.toString(), RocksDbJmtStore.Options.production())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));
            byte[] root = tree.put(1L, updates).rootHash();

            JellyfishMerkleTree.CommitResult replay = tree.put(1L, updates);
            assertArrayEquals(root, replay.rootHash());
            assertTrue(replay.staleNodes().stream().noneMatch(replay.nodes()::containsKey));

            store.pruneUpTo(1L);
            assertTrue(tree.verifyProofWire(root, bytes("alice"), bytes("100"), true,
                    tree.getProofWire(bytes("alice"), 1L).orElseThrow()));
            assertTrue(new JmtIntegrityChecker(store, JmtProfile.classicBlake2b256V1())
                    .check(JmtIntegrityMode.FULL).healthy());
        }
    }

    @Test
    void aggressivePruneNeverDeletesTheLiveHeadValue() {
        RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
                .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.AGGRESSIVE)
                .build();
        try (RocksDbJmtStore store = RocksDbJmtStore.open(
                tempDir.resolve("aggressive-db").toString(), options)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            tree.put(1L, Map.of(bytes("alice"), bytes("100")));
            tree.put(2L, Map.of(bytes("bob"), bytes("200")));

            store.pruneUpTo(2L);

            assertArrayEquals(bytes("100"), store.getValue(HASH.digest(bytes("alice"))).orElseThrow());
        }
    }

    @Test
    void pruneWatermarkRejectsUnsafeRollbackAndNegativeHorizon() {
        RocksDbJmtStore.Options options = RocksDbJmtStore.Options.production();
        Path dbPath = tempDir.resolve("watermark-db");
        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString(), options)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            tree.put(1L, Map.of(bytes("alice"), bytes("100")));
            tree.put(2L, Map.of(bytes("alice"), bytes("200")));

            assertThrows(IllegalArgumentException.class, () -> store.pruneUpTo(-1));
            store.pruneUpTo(2L);
            assertThrows(IllegalStateException.class, () -> store.truncateAfter(1L));
        }
        try (RocksDbJmtStore reopened = RocksDbJmtStore.open(dbPath.toString(), options)) {
            assertThrows(IllegalStateException.class, () -> reopened.truncateAfter(1L));
        }
    }

    @Test
    void truncateAfterRemovesAllFutureVersionsNotJustOne() {
        Path dbPath = tempDir.resolve("truncate-db");
        RocksDbJmtStore.Options opts = RocksDbJmtStore.Options.builder().enableRollbackIndex(true).build();

        byte[] root2;
        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString(), opts)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            byte[] r2 = null;
            for (long version = 1; version <= 5; version++) {
                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                updates.put(bytes("key-" + version), bytes("val-" + version));
                JellyfishMerkleTree.CommitResult r = tree.put(version, updates);
                if (version == 2) r2 = r.rootHash();
            }
            root2 = r2;

            // Sanity: versions 3..5 present before rollback.
            for (long v = 3; v <= 5; v++) {
                assertTrue(store.rootHash(v).isPresent());
                assertTrue(store.getValue(HASH.digest(bytes("key-" + v))).isPresent());
            }

            store.truncateAfter(2);

            // All future roots gone.
            for (long v = 3; v <= 5; v++) {
                assertTrue(store.rootHash(v).isEmpty(), "root " + v + " should be truncated");
                // Node + value ghosts for future versions must be gone (this is the part the bug left behind).
                assertTrue(store.getValue(HASH.digest(bytes("key-" + v))).isEmpty(),
                        "value for key-" + v + " should be truncated");
                assertTrue(store.getValueAt(HASH.digest(bytes("key-" + v)), Long.MAX_VALUE).isEmpty(),
                        "value history for key-" + v + " should be truncated");
            }
            // Surviving versions intact and latest pointer correct.
            assertArrayEquals(root2, store.rootHash(2).orElseThrow());
            assertArrayEquals(root2, store.latestRoot().orElseThrow().rootHash());
            assertEquals(2L, store.latestRoot().orElseThrow().version());
            assertArrayEquals(bytes("val-1"), store.getValue(HASH.digest(bytes("key-1"))).orElseThrow());
            assertArrayEquals(bytes("val-2"), store.getValue(HASH.digest(bytes("key-2"))).orElseThrow());
        }

        // Rollback must persist across restart, and the tree must keep working afterwards.
        try (RocksDbJmtStore reopened = RocksDbJmtStore.open(dbPath.toString(), opts)) {
            assertArrayEquals(root2, reopened.latestRoot().orElseThrow().rootHash());
            for (long v = 3; v <= 5; v++) {
                assertTrue(reopened.rootHash(v).isEmpty());
            }
            JellyfishMerkleTree tree = new JellyfishMerkleTree(reopened);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("key-3b"), bytes("val-3b"));
            JellyfishMerkleTree.CommitResult r3 = tree.put(3, updates);
            assertArrayEquals(r3.rootHash(), reopened.rootHash(3).orElseThrow());
            assertArrayEquals(bytes("val-3b"), reopened.getValue(HASH.digest(bytes("key-3b"))).orElseThrow());
        }
    }

    @Test
    void divergentReplayRejectedAndLatestPointerMonotonic() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("replay-db").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);

            Map<byte[], byte[]> a = new LinkedHashMap<>();
            a.put(bytes("alice"), bytes("100"));
            byte[] root1 = tree.put(1L, a).rootHash();

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("bob"), bytes("200"));
            tree.put(2L, v2);
            assertEquals(2L, store.latestRoot().orElseThrow().version());

            // Divergent replay of version 1 (different content → different root) is rejected.
            Map<byte[], byte[]> diverge = new LinkedHashMap<>();
            diverge.put(bytes("alice"), bytes("999"));
            assertThrows(RuntimeException.class, () -> tree.put(1L, diverge));
            assertArrayEquals(root1, store.rootHash(1L).orElseThrow(), "original v1 root must be intact");

            // Older-version replay is rejected; only the latest version is a crash-replay target.
            assertThrows(IllegalArgumentException.class, () -> tree.put(1L, a));
            assertEquals(2L, store.latestRoot().orElseThrow().version(),
                    "latest pointer must stay at 2 after rejecting version 1");
        }
    }

    @Test
    void truncateAfterVersionWithoutExactRootKeepsGreatestSurvivingPointer() {
        Path dbPath = tempDir.resolve("truncate-gap-db");
        RocksDbJmtStore.Options opts = RocksDbJmtStore.Options.builder().enableRollbackIndex(true).build();

        byte[] root2;
        try (RocksDbJmtStore store = RocksDbJmtStore.open(dbPath.toString(), opts)) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
            // Commit only at even versions 2, 4, 6 (gaps at odd versions).
            byte[] r2 = null;
            for (long version : new long[]{2, 4, 6}) {
                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                updates.put(bytes("k-" + version), bytes("v-" + version));
                JellyfishMerkleTree.CommitResult r = tree.put(version, updates);
                if (version == 2) r2 = r.rootHash();
            }
            root2 = r2;

            // Truncate after version 3 — which has NO exact root. The latest pointer must fall back
            // to version 2, not be dropped entirely.
            store.truncateAfter(3);

            assertTrue(store.rootHash(4).isEmpty());
            assertTrue(store.rootHash(6).isEmpty());
            assertArrayEquals(root2, store.rootHash(2).orElseThrow());
            assertTrue(store.latestRoot().isPresent(), "latest pointer must survive truncation to a gap version");
            assertEquals(2L, store.latestRoot().orElseThrow().version());
            assertArrayEquals(root2, store.latestRoot().orElseThrow().rootHash());
        }
    }
}
