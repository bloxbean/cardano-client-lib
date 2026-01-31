package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Property-style randomized tests for MPT in MPF mode backed by RocksDB.
 *
 * The test builds multiple roots by applying random put/delete updates and validates:
 *  - Proofs verify for random keys against each saved root
 *  - Proofs continue to verify after reopening RocksDB (persistence)
 *  - At least one multi-level proof exists at the final root
 */
class MptMpfPropertyRocksDbTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final Random RNG = new Random(0xBEEF);

    @TempDir
    Path tempDir;

    @Test
    void randomizedProofs_persist_and_multilevel() throws Exception {
        String dbPath = tempDir.resolve("mpt-prop-db").toString();

        int keyCount = 140;
        int versions = 12;
        int maxUpdatesPerVersion = 10;
        int queries = 50;

        List<byte[]> keyPool = generateKeys(keyCount);
        List<byte[]> roots = new ArrayList<>();

        try {
            // Phase 1: build multiple roots with random updates
            try (RocksDbNodeStore store = new RocksDbNodeStore(dbPath)) {
                MpfTrie trie = new MpfTrie(store);

                for (int v = 1; v <= versions; v++) {
                    Map<byte[], byte[]> updates = randomUpdates(keyPool, maxUpdatesPerVersion);
                    applyUpdates(trie, updates);
                    roots.add(Objects.requireNonNullElse(trie.getRootHash(), new byte[32]));

                    // spot-check some proofs immediately at this root
                    assertRandomProofs(trie, roots.get(v - 1), keyPool, Math.min(10, queries));
                }

                // Full check at final root
                byte[] latestRoot = roots.get(roots.size() - 1);
                trie.setRootHash(latestRoot);
                assertRandomProofs(trie, latestRoot, keyPool, queries);
                assertTrue(existsMultiLevelProof(trie, latestRoot, keyPool), "expected a multi-level proof at final root");
            }

            // Phase 2: reopen and verify again at final root
            try (RocksDbNodeStore store = new RocksDbNodeStore(dbPath)) {
                byte[] latestRoot = roots.get(roots.size() - 1);
                MpfTrie trie = new MpfTrie(store, latestRoot);
                assertRandomProofs(trie, latestRoot, keyPool, queries);
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        }
    }

    private static void applyUpdates(MpfTrie trie, Map<byte[], byte[]> updates) {
        for (Map.Entry<byte[], byte[]> e : updates.entrySet()) {
            if (e.getValue() == null) {
                trie.delete(e.getKey());
            } else {
                trie.put(e.getKey(), e.getValue());
            }
        }
    }

    private static List<byte[]> generateKeys(int count) {
        List<byte[]> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String s = "k-" + i + "-" + RNG.nextInt(1_000_000);
            keys.add(s.getBytes(StandardCharsets.UTF_8));
        }
        return keys;
    }

    private static Map<byte[], byte[]> randomUpdates(List<byte[]> keyPool, int maxUpdates) {
        int n = 1 + RNG.nextInt(Math.max(1, maxUpdates));
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            boolean delete = RNG.nextDouble() < 0.15; // 15% deletes
            if (delete) {
                updates.put(key, null);
            } else {
                String val = "v-" + RNG.nextInt(10_000);
                updates.put(key, val.getBytes(StandardCharsets.UTF_8));
            }
        }
        return updates;
    }

    private static String buildMismatchMessage(String label,
                                               boolean including,
                                               byte[] key,
                                               byte[] root,
                                               byte[] wire) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" proof verification failed")
          .append(" including=").append(including)
          .append(" key=").append(Bytes.toHex(key))
          .append(" keyHash=").append(Bytes.toHex(HASH.digest(key)))
          .append(" root=").append(Bytes.toHex(root == null ? new byte[0] : root))
          .append("\n    wireLength=").append(wire.length)
          .append(" bytes");
        return sb.toString();
    }

    private static void assertRandomProofs(MpfTrie trie, byte[] root, List<byte[]> keyPool, int queries) {
        trie.setRootHash(root);
        for (int i = 0; i < queries; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            byte[] expected = trie.get(key);
            boolean including = expected != null;
            byte[] wire = trie.getProofWire(key).orElseThrow(() -> new AssertionError("no proof for key"));

            // Use public API to verify proof
            boolean ok = trie.verifyProofWire(root, key, expected, including, wire);
            if (!ok) {
                fail(buildMismatchMessage("RocksDB", including, key, root, wire));
            }
        }
    }

    private static boolean existsMultiLevelProof(MpfTrie trie, byte[] root, List<byte[]> keyPool) {
        int checks = Math.min(keyPool.size(), 80);
        for (int i = 0; i < checks; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            byte[] wire = trie.getProofWire(key).orElse(null);
            if (wire == null) continue;
            // Heuristic: multi-level proofs are typically larger (>100 bytes)
            // A single-level proof is usually around 64-80 bytes
            if (wire.length > 100) return true;
        }
        return false;
    }
}
