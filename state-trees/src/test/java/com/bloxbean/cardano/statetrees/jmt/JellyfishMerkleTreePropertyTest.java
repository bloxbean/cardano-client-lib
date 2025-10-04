package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests that drive random sequences of updates through both the
 * in-memory Jellyfish Merkle Tree and the store-backed façade, asserting that
 * roots, point lookups, and proofs stay in sync.
 */
class JellyfishMerkleTreePropertyTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void randomized_commits_keep_reference_and_store_in_lockstep() {
        long seed = 0x1C0FFEEBEEF5EEDL;
        Random random = new Random(seed);

        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore backingStore = new InMemoryJmtStore()) {
            JellyfishMerkleTreeStore storeTree = new JellyfishMerkleTreeStore(backingStore, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, JellyfishMerkleTreeStoreConfig.defaults());

            Map<String, byte[]> keyBytes = new HashMap<>();      // hex -> key bytes
            Map<String, byte[]> state = new HashMap<>();         // hex -> latest value (null = absent)

            int maxVersions = 200;
            for (int version = 1; version <= maxVersions; version++) {
                List<Mutation> mutations = buildRandomMutations(random, state, keyBytes);

                reference.commit(version, toUpdateMap(mutations));
                storeTree.commit(version, toUpdateMap(mutations));

                byte[] referenceRoot = reference.rootHash(version);
                byte[] storeRoot = storeTree.rootHash(version);
                if (!java.util.Arrays.equals(referenceRoot, storeRoot)) {
                    System.out.println("Root mismatch at version " + version);
                    System.out.println("Reference=" + HexUtil.encodeHexString(referenceRoot));
                    System.out.println("Store=" + HexUtil.encodeHexString(storeRoot));
                    for (Mutation m : mutations) {
                        System.out.println("Mutation key=" + HexUtil.encodeHexString(m.key) + " value=" + (m.value == null ? "null" : HexUtil.encodeHexString(m.value)));
                    }
                }
                assertArrayEquals(referenceRoot, storeRoot, "root mismatch at version " + version);

                assertArrayEquals(referenceRoot, storeTree.latestRootHash(), "latest root mismatch at version " + version);

                validateRandomLookups(random, version, reference, storeTree, state, keyBytes, referenceRoot, mutations);
            }

        } catch (Exception e) {
            fail("Unexpected exception during property test execution", e);
        }
    }

    @Test
    void debug_root_mismatch_scenario() throws Exception {
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore backingStore = new InMemoryJmtStore()) {
            JellyfishMerkleTreeStore storeTree = new JellyfishMerkleTreeStore(backingStore, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, JellyfishMerkleTreeStoreConfig.defaults());

            java.util.LinkedHashMap<byte[], byte[]> v1 = new java.util.LinkedHashMap<>();
            byte[] key1 = HexUtil.decodeHexString("6b2d36333839303031333836393838373832383937");
            byte[] val1 = HexUtil.decodeHexString("7b6b621d6fb2abede25e27302ae636ec2d1fd53ea46ee110894cb87fe172439d814c0cce0f4480eae78cea064ca7f0926454661696df08ddbea0d6b1e1f8214f");
            v1.put(key1, val1);

            reference.commit(1, v1);
            storeTree.commit(1, v1);
            assertArrayEquals(reference.rootHash(1), storeTree.rootHash(1));

            byte[] absent = HexUtil.decodeHexString("6b2d616273656e742d746573742d6b657931");
            Optional<JmtProof> refAbsent = reference.getProof(absent, 1);
            Optional<JmtProof> storeAbsent = storeTree.getProof(absent, 1);
            System.out.println("reference absent type=" + refAbsent.map(JmtProof::type));
            System.out.println("store absent type=" + storeAbsent.map(JmtProof::type));
            refAbsent.ifPresent(p -> System.out.println("ref absent steps=" + p.steps().size()));
            storeAbsent.ifPresent(p -> System.out.println("store absent steps=" + p.steps().size()));

            byte[] absentV2 = HexUtil.decodeHexString("6b2d616273656e742d766572322d6b6579");

            java.util.LinkedHashMap<byte[], byte[]> v2 = new java.util.LinkedHashMap<>();
            v2.put(key1, null);
            v2.put(HexUtil.decodeHexString("6b2d3134353135343233343535313930373638343139"),
                    HexUtil.decodeHexString("7be3f2628f26214b23b3e6bdf7cd56adfbc66efda2d70613165c3b452c4e93b7b5476ef8d9c8734babe4fb822bb4c01bc5a88e707ad5d5644f59f1799635aadf"));
            v2.put(HexUtil.decodeHexString("6b2d38373134383932383937363134313030333831"),
                    HexUtil.decodeHexString("7182949d72577b0044361de78f72b3aaf3d4b2ef4f65fc92a4f7f258b4fa7a49ded37d104bc63a41ba1ce3b4598228e2d52632c19c73dcbbbe4a7cf12be9ce29"));
            v2.put(HexUtil.decodeHexString("6b2d36383531363237343236363038393939363234"),
                    HexUtil.decodeHexString("7e87ff518c8b3c87c2d64fdd930e3ae2cda51cd666d12493ab1f1618c702d92d285028bd337d5ee25e6f4f5ace7c5774e17bfa8f6e42b0c3a30b3e7cd36c1364"));

            int[] key1Path = com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(HASH.digest(key1));
            int[] key2Path = com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(HASH.digest(HexUtil.decodeHexString("6b2d3134353135343233343535313930373638343139")));
            int[] key3Path = com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(HASH.digest(HexUtil.decodeHexString("6b2d38373134383932383937363134313030333831")));
            int[] key4Path = com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.toNibbles(HASH.digest(HexUtil.decodeHexString("6b2d36383531363237343236363038393939363234")));
            System.out.println("key1 path=" + java.util.Arrays.toString(key1Path));
            System.out.println("key2 path=" + java.util.Arrays.toString(key2Path));
            System.out.println("key3 path=" + java.util.Arrays.toString(key3Path));
            System.out.println("key4 path=" + java.util.Arrays.toString(key4Path));

            JellyfishMerkleTree.CommitResult refRes = reference.commit(2, v2);
            JellyfishMerkleTree.CommitResult storeRes = storeTree.commit(2, v2);

            System.out.println("ref root=" + HexUtil.encodeHexString(refRes.rootHash()));
            System.out.println("store root=" + HexUtil.encodeHexString(storeRes.rootHash()));
            System.out.println("ref nodes=" + refRes.nodes().keySet());
            System.out.println("store nodes=" + storeRes.nodes().keySet());
            System.out.println("ref stale=" + refRes.staleNodes());
            System.out.println("store stale=" + storeRes.staleNodes());
            for (java.util.Map.Entry<NodeKey, JmtNode> e : refRes.nodes().entrySet()) {
                System.out.println("ref node detail: " + e.getKey() + " type=" + e.getValue().getClass().getSimpleName());
            }
            for (java.util.Map.Entry<NodeKey, JmtNode> e : storeRes.nodes().entrySet()) {
            System.out.println("store node detail: " + e.getKey() + " type=" + e.getValue().getClass().getSimpleName());
            }

            Optional<JmtProof> refAbsentV2 = reference.getProof(absentV2, 2);
            Optional<JmtProof> storeAbsentV2 = storeTree.getProof(absentV2, 2);
            System.out.println("reference absent@2 type=" + refAbsentV2.map(JmtProof::type));
            System.out.println("store absent@2 type=" + storeAbsentV2.map(JmtProof::type));
            refAbsentV2.ifPresent(p -> {
                System.out.println("ref absent@2 steps=" + p.steps().size());
                for (JmtProof.BranchStep step : p.steps()) {
                    System.out.println("ref step prefix=" + java.util.Arrays.toString(step.prefix().getNibbles()) +
                            " childIndex=" + step.childIndex() + " hasLeafNeighbor=" + step.hasLeafNeighbor());
                }
            });
            storeAbsentV2.ifPresent(p -> {
                System.out.println("store absent@2 steps=" + p.steps().size());
                for (JmtProof.BranchStep step : p.steps()) {
                    System.out.println("store step prefix=" + java.util.Arrays.toString(step.prefix().getNibbles()) +
                            " childIndex=" + step.childIndex() + " hasLeafNeighbor=" + step.hasLeafNeighbor());
                }
            });

            assertArrayEquals(refRes.rootHash(), storeRes.rootHash());
            assertArrayEquals(reference.rootHash(2), storeTree.rootHash(2));
            assertArrayEquals(reference.latestRootHash(), storeTree.latestRootHash());
        }
    }

    private static List<Mutation> buildRandomMutations(Random random,
                                                       Map<String, byte[]> state,
                                                       Map<String, byte[]> keyBytes) {
        List<Mutation> mutations = new ArrayList<>();
        Set<String> touched = new HashSet<>();

        int batchSize = 1 + random.nextInt(5);
        for (int i = 0; i < batchSize; i++) {
            boolean performDelete = !state.isEmpty() && random.nextDouble() < 0.25;
            if (performDelete) {
                String keyHex = pickRandomKey(random, state.keySet());
                if (keyHex == null || !touched.add(keyHex)) {
                    i--;
                    continue;
                }
                state.remove(keyHex);
                mutations.add(new Mutation(keyBytes.get(keyHex), null, keyHex));
            } else {
                String keyHex = allocateKey(random, keyBytes);
                if (!touched.add(keyHex)) {
                    i--;
                    continue;
                }
                byte[] value = randomValue(random);
                state.put(keyHex, value.clone());
                mutations.add(new Mutation(keyBytes.get(keyHex), value, keyHex));
            }
        }

        return mutations;
    }

    private static LinkedHashMap<byte[], byte[]> toUpdateMap(List<Mutation> mutations) {
        LinkedHashMap<byte[], byte[]> updates = new LinkedHashMap<>();
        for (Mutation mutation : mutations) {
            updates.put(mutation.key.clone(), mutation.value == null ? null : mutation.value.clone());
        }
        return updates;
    }

    private static void validateRandomLookups(Random random,
                                              long version,
                                              JellyfishMerkleTree reference,
                                              JellyfishMerkleTreeStore storeTree,
                                              Map<String, byte[]> state,
                                              Map<String, byte[]> keyBytes,
                                              byte[] expectedRoot,
                                              List<Mutation> appliedMutations) {
        List<String> keys = new ArrayList<>(state.keySet());
        Collections.shuffle(keys, random);

        int samples = Math.min(5, keys.size());
        for (int i = 0; i < samples; i++) {
            String keyHex = keys.get(i);
            byte[] key = keyBytes.get(keyHex);
            byte[] expectedValue = state.get(keyHex);

            byte[] refValue = reference.get(key);
            byte[] storeValue = storeTree.get(key);
            if (expectedValue == null) {
                assertNull(refValue, "reference should report null for deleted key at version " + version);
                assertNull(storeValue, "store should report null for deleted key at version " + version);
            } else {
                assertArrayEquals(expectedValue, refValue, "reference get mismatch at version " + version);
                assertArrayEquals(expectedValue, storeValue, "store get mismatch at version " + version);
            }

            Optional<JmtProof> refProof = reference.getProof(key.clone(), version);
            assertTrue(refProof.isPresent(), "missing reference proof at version " + version);
            assertTrue(JmtProofVerifier.verify(expectedRoot, key, expectedValue, refProof.get(), HASH, COMMITMENTS));

            Optional<byte[]> wire = reference.getProofWire(key.clone(), version);
            assertTrue(wire.isPresent(), "missing wire proof at version " + version);
            boolean including = expectedValue != null;
            assertTrue(reference.verifyProofWire(expectedRoot, key, expectedValue, including, wire.get()));
            Optional<byte[]> storeWire = storeTree.getProofWire(key.clone(), version);
            assertTrue(storeWire.isPresent(), "missing store wire proof at version " + version);
            boolean storeWireOk = storeTree.verifyProofWire(expectedRoot, key, expectedValue, including, storeWire.get());
            if (!storeWireOk) {
                storeTree.getProof(key.clone(), version).ifPresent(proof ->
                        System.out.println("Store proof type=" + proof.type()));
                System.out.println("Store wire verification failed at version " + version + " for key " + keyHex);
                System.out.println("Expected value=" + (expectedValue == null ? "null" : HexUtil.encodeHexString(expectedValue)));
                System.out.println("Mutation batch:");
                for (Mutation m : appliedMutations) {
                    System.out.println("  mutation key=" + HexUtil.encodeHexString(m.key) + " value=" + (m.value == null ? "null" : HexUtil.encodeHexString(m.value)));
                }
            }
            assertTrue(storeWireOk, "store wire verification failed at version " + version + " for key " + keyHex);
            assertTrue(reference.verifyProofWire(expectedRoot, key, expectedValue, including, storeWire.get()),
                    "reference rejected store wire at version " + version + " for key " + keyHex);
        }

        // Check a key that should be absent
        byte[] absentKey = randomAbsentKey(random, state, keyBytes);
        Optional<JmtProof> absentProof = reference.getProof(absentKey.clone(), version);
        assertTrue(absentProof.isPresent(), "missing non-inclusion proof at version " + version);
        assertTrue(JmtProofVerifier.verify(expectedRoot, absentKey, null, absentProof.get(), HASH, COMMITMENTS));
        Optional<byte[]> absentWire = storeTree.getProofWire(absentKey.clone(), version);
        assertTrue(absentWire.isPresent(), "missing non-inclusion wire proof at version " + version);
        boolean nonInclusionOk = storeTree.verifyProofWire(expectedRoot, absentKey, null, false, absentWire.get());
        if (!nonInclusionOk) {
            storeTree.getProof(absentKey.clone(), version).ifPresent(proof ->
                    System.out.println("Absent proof type=" + proof.type()));
            absentProof.ifPresent(proof ->
                    System.out.println("Reference absent proof type=" + proof.type()));
        }
        assertTrue(nonInclusionOk, "store wire non-inclusion verification failed at version " + version);
    }

    private static String allocateKey(Random random, Map<String, byte[]> keyBytes) {
        while (true) {
            String candidate = "k-" + Long.toUnsignedString(random.nextLong());
            byte[] key = candidate.getBytes(StandardCharsets.UTF_8);
            String hex = HexUtil.encodeHexString(key);
            if (keyBytes.containsKey(hex)) {
                continue;
            }
            keyBytes.put(hex, key.clone());
            return hex;
        }
    }

    private static String pickRandomKey(Random random, Set<String> keys) {
        if (keys.isEmpty()) {
            return null;
        }
        int index = random.nextInt(keys.size());
        int i = 0;
        for (String key : keys) {
            if (i++ == index) {
                return key;
            }
        }
        return null;
    }

    private static byte[] randomValue(Random random) {
        byte[] value = new byte[64];
        random.nextBytes(value);
        return value;
    }

    private static byte[] randomAbsentKey(Random random, Map<String, byte[]> state, Map<String, byte[]> keyBytes) {
        for (int attempts = 0; attempts < 100; attempts++) {
            String candidateStr = "k-abs-" + Long.toUnsignedString(random.nextLong());
            byte[] candidate = candidateStr.getBytes(StandardCharsets.UTF_8);
            String hex = HexUtil.encodeHexString(candidate);
            if (!state.containsKey(hex)) {
                keyBytes.putIfAbsent(hex, candidate.clone());
                return candidate;
            }
        }
        // Fallback: take a known key and flip a bit so it becomes absent
        if (!state.isEmpty()) {
            String existing = state.keySet().iterator().next();
            byte[] mutated = keyBytes.get(existing).clone();
            mutated[0] ^= 0x01;
            return mutated;
        }
        String fallbackStr = "fallback-" + Long.toUnsignedString(new SecureRandom().nextLong());
        return fallbackStr.getBytes(StandardCharsets.UTF_8);
    }

    private static final class Mutation {
        private final byte[] key;
        private final byte[] value;
        private final String hex;

        private Mutation(byte[] key, byte[] value, String hex) {
            this.key = key.clone();
            this.value = value == null ? null : value.clone();
            this.hex = hex;
        }
    }
}
