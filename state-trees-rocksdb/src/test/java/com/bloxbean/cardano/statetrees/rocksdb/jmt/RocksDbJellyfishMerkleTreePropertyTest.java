package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStoreConfig;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.JmtProofVerifier;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
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

class RocksDbJellyfishMerkleTreePropertyTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    @Test
    void randomized_commits_keep_reference_and_store_in_lockstep() {
        long seed = 0x1C0FFEEBEEF5EEDL;
        Random random = new Random(seed);

        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (RocksDbJmtStore backingStore = new RocksDbJmtStore(tempDir.resolve("jmt-db").toString())) {
            JellyfishMerkleTreeStore storeTree = new JellyfishMerkleTreeStore(backingStore, COMMITMENTS, HASH,
                    JellyfishMerkleTreeStore.EngineMode.REFERENCE, JellyfishMerkleTreeStoreConfig.defaults());

            Map<String, byte[]> keyBytes = new HashMap<>();
            Map<String, byte[]> state = new HashMap<>();

            int maxVersions = 200;
            for (int version = 1; version <= maxVersions; version++) {
                List<Mutation> mutations = buildRandomMutations(random, state, keyBytes);

                reference.commit(version, toUpdateMap(mutations));
                storeTree.commit(version, toUpdateMap(mutations));

                byte[] expectedRoot = reference.rootHash(version);
                assertArrayEquals(expectedRoot, storeTree.rootHash(version), "root mismatch at version " + version);
                assertArrayEquals(expectedRoot, storeTree.latestRootHash(), "latest root mismatch at version " + version);

                validateRandomLookups(random, version, reference, storeTree, state, keyBytes, expectedRoot, mutations);
            }
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
                mutations.add(new Mutation(keyBytes.get(keyHex), null));
            } else {
                String keyHex = allocateKey(random, keyBytes);
                if (!touched.add(keyHex)) {
                    i--;
                    continue;
                }
                byte[] value = randomValue(random);
                state.put(keyHex, value.clone());
                mutations.add(new Mutation(keyBytes.get(keyHex), value));
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

        byte[] absentKey = randomAbsentKey(random, state, keyBytes);
        Optional<JmtProof> absentProof = reference.getProof(absentKey.clone(), version);
        assertTrue(absentProof.isPresent(), "missing non-inclusion proof at version " + version);
        assertTrue(JmtProofVerifier.verify(expectedRoot, absentKey, null, absentProof.get(), HASH, COMMITMENTS));
        Optional<byte[]> absentWire = storeTree.getProofWire(absentKey.clone(), version);
        assertTrue(absentWire.isPresent(), "missing non-inclusion wire proof at version " + version);
        assertTrue(storeTree.verifyProofWire(expectedRoot, absentKey, null, false, absentWire.get()),
                "store wire non-inclusion verification failed at version " + version);
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

        private Mutation(byte[] key, byte[] value) {
            this.key = key.clone();
            this.value = value == null ? null : value.clone();
        }
    }
}
