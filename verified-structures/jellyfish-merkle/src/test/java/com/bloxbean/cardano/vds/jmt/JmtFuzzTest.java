package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtWriteConflictException;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage-guided targets for the JMT tree, its proofs and its version history. In a normal build they replay the
 * committed seeds under {@code src/test/resources/.../JmtFuzzTestInputs}; with {@code JAZZER_FUZZ=1} Jazzer explores
 * new inputs.
 *
 * <p>The oracle is a plain map per committed version and an independent recursive root builder (radix-16, a single
 * key collapses to its leaf, no extension nodes) that only shares the commitment primitives with the tree.</p>
 */
class JmtFuzzTest {

    private static final JmtProfile PROFILE = JmtProfile.classicBlake2b256V1();
    private static final HashFunction HF = PROFILE.hashFunction();
    private static final CommitmentScheme CS = PROFILE.commitmentScheme();
    private static final byte[] CLAIM = "claimed".getBytes(StandardCharsets.UTF_8);

    /**
     * Random histories (batches, version gaps, replays, divergent replays, prunes, truncations) against the model.
     * After every step: roots equal the reference, {@code get(key, v)} answers as of {@code v} or rejects versions
     * that are newer than the latest or below the prune watermark, proofs exist exactly for retained committed
     * versions and verify the model's answer (and nothing else) through both verifiers.
     */
    @FuzzTest(maxDuration = "60s")
    void operationTracesMatchModelAndReference(FuzzedDataProvider data) {
        List<byte[]> pool = keyPool(data);
        History h = new History();
        try (InMemoryJmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, PROFILE);
            int steps = data.consumeInt(1, 24);
            for (int step = 0; step < steps && data.remainingBytes() > 0; step++) {
                switch (data.consumeInt(0, 9)) {
                    case 0:
                        replayLatest(tree, h, data, pool);
                        break;
                    case 1:
                        if (h.latest() >= 0) {
                            long horizon = data.consumeLong(0, h.latest());
                            int removed = tree.pruneUpTo(horizon);
                            if (removed > 0) h.watermark = Math.max(h.watermark, horizon);
                        }
                        break;
                    case 2:
                        if (h.latest() >= 0) {
                            long target = data.consumeLong(Math.max(0, h.watermark), h.latest());
                            Long survivor = h.committed.floorKey(target);
                            if (h.watermark >= 0 && (survivor == null || survivor < h.watermark)) {
                                assertThrows(IllegalStateException.class, () -> store.truncateAfter(target),
                                        "truncation would leave no retained root");
                            } else {
                                store.truncateAfter(target);
                                h.committed.tailMap(target, false).clear();
                            }
                        }
                        break;
                    default:
                        commit(tree, h, data, pool);
                }
                checkHistory(tree, store, h, data, pool);
            }
        }
    }

    /**
     * Mutated wire proofs never verify a false claim: another value for a present key, a value for an absent key,
     * or the opposite membership.
     */
    @FuzzTest(maxDuration = "30s")
    void mutatedWireProofsNeverProveSomethingElse(FuzzedDataProvider data) {
        try (InMemoryJmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, PROFILE);
            int size = data.consumeInt(1, 40);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) updates.put(bytes("key-" + i), bytes("v" + i));
            byte[] root = tree.put(0, updates).rootHash();

            boolean present = data.consumeBoolean();
            byte[] key = bytes(present ? "key-" + data.consumeInt(0, size - 1) : "absent-" + data.consumeInt(0, 10_000));
            byte[] value = tree.get(key).orElse(null);
            byte[] wire = tree.getProofWire(key, 0).orElseThrow();
            byte[] mutated = wire.clone();
            int mutations = data.consumeInt(1, 4);
            for (int i = 0; i < mutations && mutated.length > 0; i++) {
                mutated[data.consumeInt(0, mutated.length - 1)] ^= (byte) data.consumeInt(1, 255);
            }
            if (data.consumeBoolean() && mutated.length > 1) {
                mutated = Arrays.copyOf(mutated, data.consumeInt(1, mutated.length - 1));
            }

            if (present) {
                assertFalse(tree.verifyProofWire(root, key, CLAIM, true, mutated));
                assertFalse(tree.verifyProofWire(root, key, null, false, mutated));
                assertFalse(tree.verifyProofWire(root, key, value, false, mutated));
            } else {
                assertFalse(tree.verifyProofWire(root, key, CLAIM, true, mutated));
                assertFalse(tree.verifyProofWire(root, key, CLAIM, false, mutated));
            }
        }
    }

    /**
     * Object proofs assembled from fuzzed parts (types, steps and hashes taken from real proofs of the same tree or
     * from the input) are checked by {@link JmtProofVerifier}: it never throws, and whatever it accepts is true in
     * the tree.
     */
    @FuzzTest(maxDuration = "30s")
    void objectVerifierAcceptsOnlyTrueClaims(FuzzedDataProvider data) {
        try (InMemoryJmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, PROFILE);
            int size = data.consumeInt(0, 24);
            Map<String, byte[]> model = new HashMap<>();
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                byte[] v = bytes("v" + i);
                updates.put(bytes("key-" + i), v);
                model.put("key-" + i, v);
            }
            byte[] root = tree.put(0, updates).rootHash();

            List<JmtProof> real = new ArrayList<>();
            List<byte[]> hashes = new ArrayList<>();
            for (int i = 0; i < size + 3; i++) {
                JmtProof p = tree.getProof(bytes(i < size ? "key-" + i : "absent-" + i), 0).orElseThrow();
                real.add(p);
                for (JmtProof.BranchStep s : p.steps()) {
                    for (byte[] c : s.childHashes()) if (c != null) hashes.add(c);
                }
                if (p.leafKeyHash() != null) hashes.add(p.leafKeyHash());
                if (p.valueHash() != null) hashes.add(p.valueHash());
                if (p.conflictingKeyHash() != null) hashes.add(p.conflictingKeyHash());
                if (p.conflictingValueHash() != null) hashes.add(p.conflictingValueHash());
            }
            hashes.add(CS.nullHash());
            hashes.add(root);

            JmtProof base = real.get(data.consumeInt(0, real.size() - 1));
            List<JmtProof.BranchStep> steps = new ArrayList<>(base.steps());
            int edits = data.consumeInt(0, 6);
            for (int i = 0; i < edits; i++) {
                switch (data.consumeInt(0, 3)) {
                    case 0:
                        if (!steps.isEmpty()) steps.remove(data.consumeInt(0, steps.size() - 1));
                        break;
                    case 1:
                        if (!steps.isEmpty()) {
                            int at = data.consumeInt(0, steps.size() - 1);
                            steps.set(at, withChild(steps.get(at), data.consumeInt(0, 15),
                                    pick(data, hashes)));
                        }
                        break;
                    case 2:
                        JmtProof other = real.get(data.consumeInt(0, real.size() - 1));
                        if (!other.steps().isEmpty()) steps.add(other.steps().get(data.consumeInt(0, other.steps().size() - 1)));
                        break;
                    default:
                        if (!steps.isEmpty()) {
                            int at = data.consumeInt(0, steps.size() - 1);
                            JmtProof.BranchStep s = steps.get(at);
                            steps.set(at, new JmtProof.BranchStep(s.prefix(), s.childHashes(), data.consumeInt(0, 15),
                                    false, -1, null, null, null, null));
                        }
                }
            }
            JmtProof candidate;
            switch (data.consumeInt(0, 2)) {
                case 0:
                    candidate = JmtProof.nonInclusionEmpty(steps);
                    break;
                case 1:
                    candidate = JmtProof.nonInclusionDifferentLeaf(steps, pick(data, hashes), pick(data, hashes), null);
                    break;
                default:
                    candidate = JmtProof.inclusion(steps, CLAIM, pick(data, hashes), null, pick(data, hashes));
            }

            String key = data.consumeBoolean() ? "key-" + data.consumeInt(0, Math.max(0, size - 1))
                    : "absent-" + data.consumeInt(0, 100);
            byte[] actual = model.get(key);
            List<byte[]> claims = new ArrayList<>();
            claims.add(null);
            claims.add(CLAIM);
            if (actual != null) claims.add(actual);
            claims.add(data.consumeBytes(8));
            for (byte[] claim : claims) {
                boolean accepted = JmtProofVerifier.verify(root, bytes(key), claim, candidate, HF, CS);
                if (accepted) {
                    assertTrue(actual == null ? claim == null : claim != null && Arrays.equals(actual, claim),
                            "accepted a false claim for " + key);
                }
            }
        }
    }

    // ---------------------------------------------------------------- trace driver

    /** Committed versions and their full key -> value state. */
    private static final class History {
        final NavigableMap<Long, Map<String, byte[]>> committed = new TreeMap<>();
        long watermark = -1;

        long latest() {
            return committed.isEmpty() ? -1 : committed.lastKey();
        }

        Map<String, byte[]> stateAsOf(long version) {
            Map.Entry<Long, Map<String, byte[]>> e = committed.floorEntry(version);
            return e == null ? Map.of() : e.getValue();
        }
    }

    private static List<byte[]> keyPool(FuzzedDataProvider data) {
        int size = data.consumeInt(1, 48);
        List<byte[]> pool = new ArrayList<>(size);
        for (int i = 0; i < size; i++) pool.add(bytes("k" + i + ":" + data.consumeAsciiString(3)));
        return pool;
    }

    private static Map<byte[], byte[]> batch(FuzzedDataProvider data, List<byte[]> pool) {
        Map<String, byte[]> chosen = new LinkedHashMap<>();
        int n = data.consumeInt(0, 8);
        for (int i = 0; i < n; i++) {
            byte[] k = pool.get(data.consumeInt(0, pool.size() - 1));
            chosen.put(new String(k, StandardCharsets.UTF_8), data.consumeBytes(4));
        }
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        chosen.forEach((k, v) -> updates.put(bytes(k), v));
        return updates;
    }

    private static void commit(JellyfishMerkleTree tree, History h, FuzzedDataProvider data, List<byte[]> pool) {
        long version = h.latest() < 0 ? data.consumeLong(0, 2) : h.latest() + data.consumeLong(1, 3);
        Map<byte[], byte[]> updates = batch(data, pool);
        Map<String, byte[]> next = new HashMap<>(h.stateAsOf(version));
        updates.forEach((k, v) -> next.put(new String(k, StandardCharsets.UTF_8), v));
        JellyfishMerkleTree.CommitResult result = tree.put(version, updates);
        assertArrayEquals(referenceRoot(next), result.rootHash(), "root of version " + version);
        h.committed.put(version, next);
    }

    private static void replayLatest(JellyfishMerkleTree tree, History h, FuzzedDataProvider data, List<byte[]> pool) {
        long latest = h.latest();
        if (latest < 0) return;
        Map<String, byte[]> state = h.committed.get(latest);
        Map<byte[], byte[]> updates = batch(data, pool);
        if (h.watermark >= latest) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> tree.put(latest, updates));
            assertTrue(e.getMessage().contains("cannot be replayed"), "replay after pruning it: " + e);
            return;
        }
        // Replaying onto the previous state: the replayed batch only matches if it reproduces the committed root.
        Map<String, byte[]> base = new HashMap<>(h.stateAsOf(latest - 1));
        updates.forEach((k, v) -> base.put(new String(k, StandardCharsets.UTF_8), v));
        if (Arrays.equals(referenceRoot(base), referenceRoot(state))) {
            JellyfishMerkleTree.CommitResult r = tree.put(latest, updates);
            assertArrayEquals(referenceRoot(state), r.rootHash());
            assertTrue(r.nodes().isEmpty() && r.staleNodes().isEmpty() && r.valueOperations().isEmpty(),
                    "identical replay reports no work");
        } else {
            assertThrows(JmtWriteConflictException.class, () -> tree.put(latest, updates), "divergent replay");
        }
        if (latest > 0 && h.committed.firstKey() < latest) {
            assertThrows(IllegalArgumentException.class, () -> tree.put(h.committed.firstKey(), updates),
                    "older version replay");
        }
    }

    private static void checkHistory(JellyfishMerkleTree tree, InMemoryJmtStore store, History h,
                                     FuzzedDataProvider data, List<byte[]> pool) {
        assertEquals(h.watermark < 0 ? OptionalLong.empty() : OptionalLong.of(h.watermark), store.pruneWatermark());
        long latest = h.latest();
        if (latest >= 0) {
            assertEquals(latest, store.latestRoot().orElseThrow().version());
            assertArrayEquals(referenceRoot(h.committed.get(latest)), store.latestRoot().orElseThrow().rootHash());
        } else {
            assertTrue(store.latestRoot().isEmpty());
        }

        List<byte[]> probes = new ArrayList<>();
        for (int i = 0; i < 3; i++) probes.add(pool.get(data.consumeInt(0, pool.size() - 1)));
        probes.add(bytes("never-written"));
        long top = Math.max(latest, 0) + 2;
        for (int i = 0; i < 3; i++) {
            long v = data.consumeLong(0, top);
            for (byte[] key : probes) checkRead(tree, store, h, key, v);
        }
    }

    private static void checkRead(JellyfishMerkleTree tree, InMemoryJmtStore store, History h, byte[] key, long v) {
        long latest = h.latest();
        byte[] expected = h.stateAsOf(v).get(new String(key, StandardCharsets.UTF_8));
        if (latest >= 0 && v > latest) {
            assertThrows(IllegalArgumentException.class, () -> tree.get(key, v), "future version " + v);
        } else if (h.watermark >= 0 && v < h.watermark) {
            assertThrows(IllegalArgumentException.class, () -> tree.get(key, v), "pruned version " + v);
        } else {
            byte[] got = tree.get(key, v).orElse(null);
            assertArrayEquals(expected, got, "get as of " + v);
        }

        boolean retainedRoot = h.committed.containsKey(v) && (h.watermark < 0 || v >= h.watermark);
        assertEquals(retainedRoot, store.rootHash(v).isPresent(), "root at " + v);
        if (!retainedRoot) {
            assertTrue(tree.getProof(key, v).isEmpty(), "no proof without a retained root at " + v);
            return;
        }
        byte[] root = store.rootHash(v).orElseThrow();
        assertArrayEquals(referenceRoot(h.committed.get(v)), root);
        JmtProof proof = tree.getProof(key, v).orElseThrow();
        byte[] wire = tree.getProofWire(key, v).orElseThrow();
        assertEquals(expected != null, proof.type() == JmtProof.ProofType.INCLUSION);
        for (byte[] claim : new byte[][]{null, CLAIM, expected}) {
            boolean truth = expected == null ? claim == null : claim != null && Arrays.equals(expected, claim);
            assertEquals(truth, JmtProofVerifier.verify(root, key, claim, proof, HF, CS), "object verifier at " + v);
            assertEquals(truth, tree.verifyProofWire(root, key, claim, claim != null, wire), "wire verifier at " + v);
        }
    }

    // ---------------------------------------------------------------- reference root

    private static byte[] referenceRoot(Map<String, byte[]> state) {
        List<byte[][]> leaves = new ArrayList<>(); // {keyHash, valueHash}
        state.forEach((k, v) -> leaves.add(new byte[][]{HF.digest(bytes(k)), HF.digest(v)}));
        return build(leaves, 0);
    }

    private static byte[] build(List<byte[][]> leaves, int depth) {
        if (leaves.isEmpty()) return CS.nullHash();
        if (leaves.size() == 1) return CS.commitLeaf(leaves.get(0)[0], leaves.get(0)[1]);
        List<List<byte[][]>> buckets = new ArrayList<>();
        for (int i = 0; i < 16; i++) buckets.add(new ArrayList<>());
        for (byte[][] leaf : leaves) buckets.get(Nibbles.toNibbles(leaf[0])[depth]).add(leaf);
        byte[][] children = new byte[16][];
        for (int i = 0; i < 16; i++) {
            if (!buckets.get(i).isEmpty()) children[i] = build(buckets.get(i), depth + 1);
        }
        return CS.commitBranch(NibblePath.EMPTY, children);
    }

    // ---------------------------------------------------------------- helpers

    private static JmtProof.BranchStep withChild(JmtProof.BranchStep s, int index, byte[] hash) {
        byte[][] children = s.childHashes();
        children[index] = hash;
        return new JmtProof.BranchStep(s.prefix(), children, s.childIndex(), false, -1, null, null, null, null);
    }

    private static byte[] pick(FuzzedDataProvider data, List<byte[]> hashes) {
        if (data.consumeInt(0, 7) == 0) {
            byte[] raw = data.consumeBytes(32);
            return raw.length == 32 ? raw : ByteBuffer.allocate(32).put(raw).array();
        }
        return hashes.get(data.consumeInt(0, hashes.size() - 1));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
