package com.bloxbean.cardano.statetrees.mpt.mpf;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.common.util.Bytes;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.mpt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbNodeStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);
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
                SecureTrie trie = new SecureTrie(store, HASH);

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
                SecureTrie trie = new SecureTrie(store, HASH, latestRoot);
                assertRandomProofs(trie, latestRoot, keyPool, queries);
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        }
    }

    private static void applyUpdates(SecureTrie trie, Map<byte[], byte[]> updates) {
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
                                               byte[] expectedRoot,
                                               byte[] computedRoot,
                                               byte[] wire,
                                               MpfProof decoded) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" proof mismatch")
          .append(" including=").append(including)
          .append(" key=").append(Bytes.toHex(key))
          .append(" keyHash=").append(Bytes.toHex(HASH.digest(key)))
          .append(" root=").append(Bytes.toHex(root == null ? new byte[0] : root))
          .append(" expected=").append(Bytes.toHex(expectedRoot))
          .append(" computed=").append(Bytes.toHex(computedRoot))
          .append("\n    wire=").append(Bytes.toHex(wire))
          .append("\n    decodedSteps=");

        List<MpfProof.Step> steps = decoded.steps();
        for (int idx = 0; idx < steps.size(); idx++) {
            MpfProof.Step step = steps.get(idx);
            sb.append(step.getClass().getSimpleName()).append("(skip=").append(step.skip());
            if (step instanceof MpfProof.BranchStep) {
                MpfProof.BranchStep br = (MpfProof.BranchStep) step;
                sb.append(", neighbors=");
                byte[][] neighbors = br.neighbors();
                for (int n = 0; n < neighbors.length; n++) {
                    sb.append(n == 0 ? '[' : ',');
                    sb.append(Bytes.toHex(neighbors[n]));
                }
                sb.append(']');
                if (br.branchValueHash() != null) {
                    sb.append(", branchValue=").append(Bytes.toHex(br.branchValueHash()));
                }
            } else if (step instanceof MpfProof.ForkStep) {
                MpfProof.ForkStep fk = (MpfProof.ForkStep) step;
                sb.append(", nibble=").append(fk.nibble())
                  .append(", prefix=").append(Bytes.toHex(fk.prefix()))
                  .append(", root=").append(Bytes.toHex(fk.root()));
            } else if (step instanceof MpfProof.LeafStep) {
                MpfProof.LeafStep lf = (MpfProof.LeafStep) step;
                sb.append(", neighborKey=").append(Bytes.toHex(lf.keyHash()))
                  .append(", valueHash=").append(Bytes.toHex(lf.valueHash()));
            }
            sb.append(')');
            if (idx + 1 < steps.size()) sb.append(", ");
        }
        return sb.toString();
    }

    private static void assertRandomProofs(SecureTrie trie, byte[] root, List<byte[]> keyPool, int queries) {
        trie.setRootHash(root);
        for (int i = 0; i < queries; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            byte[] expected = trie.get(key);
            boolean including = expected != null;
            byte[] wire = trie.getProofWire(key).orElseThrow(() -> new AssertionError("no proof for key"));

            MpfProof decoded = MpfProofDecoder.decode(wire);
            byte[] computed = decoded.computeRoot(key, expected, including, HASH, COMMITMENTS);
            byte[] normalizedComputed = computed == null ? COMMITMENTS.nullHash() : computed;
            byte[] normalizedExpected = root == null ? COMMITMENTS.nullHash() : root;
            if (!Arrays.equals(normalizedExpected, normalizedComputed)) {
                fail(buildMismatchMessage("RocksDB", including, key, root, normalizedExpected, normalizedComputed, wire, decoded));
            }

            boolean ok = trie.verifyProofWire(root, key, expected, including, wire);
            if (!ok) {
                fail(buildMismatchMessage("RocksDB verify", including, key, root, normalizedExpected, normalizedComputed, wire, decoded));
            }
        }
    }

    private static boolean existsMultiLevelProof(SecureTrie trie, byte[] root, List<byte[]> keyPool) {
        int checks = Math.min(keyPool.size(), 80);
        for (int i = 0; i < checks; i++) {
            byte[] key = keyPool.get(RNG.nextInt(keyPool.size()));
            byte[] wire = trie.getProofWire(key).orElse(null);
            if (wire == null) continue;
            String json = safeProofJson(wire);
            int steps = countSteps(json);
            if (steps >= 2) return true;
        }
        return false;
    }

    private static String safeProofJson(byte[] wire) {
        try {
            return MpfProofFormatter.toJson(wire);
        } catch (Exception e) {
            return "<decode-error:" + e.getMessage() + ">";
        }
    }

    private static int countSteps(String json) {
        // quick heuristic: number of occurrences of '"type"'
        int count = 0;
        int idx = 0;
        while (true) {
            int p = json.indexOf("\"type\"", idx);
            if (p < 0) break;
            count++;
            idx = p + 6;
        }
        return count;
    }
}
