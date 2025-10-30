package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the three get methods: get(), get(version), and getProof().
 */
class JellyfishMerkleTreeGetMethodsTest {

    private JellyfishMerkleTree tree;
    private InMemoryJmtStore store;

    @BeforeEach
    void setUp() {
        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);
        store = new InMemoryJmtStore();
        tree = new JellyfishMerkleTree(store, commitments, hashFn);

        // Setup: Create multiple versions with different data
        Map<byte[], byte[]> v1 = new LinkedHashMap<>();
        v1.put("key1".getBytes(), "value1-v1".getBytes());
        v1.put("key2".getBytes(), "value2-v1".getBytes());
        tree.put(1, v1);

        Map<byte[], byte[]> v2 = new LinkedHashMap<>();
        v2.put("key1".getBytes(), "value1-v2".getBytes()); // Update key1
        v2.put("key3".getBytes(), "value3-v2".getBytes()); // Add key3
        tree.put(2, v2);

        Map<byte[], byte[]> v3 = new LinkedHashMap<>();
        v3.put("key2".getBytes(), "value2-v3".getBytes()); // Update key2
        v3.put("key4".getBytes(), "value4-v3".getBytes()); // Add key4
        tree.put(3, v3);
    }

    @Test
    void testGetLatestVersion() {
        // get() returns latest version (version 3 in this case)
        Optional<byte[]> value1 = tree.get("key1".getBytes());
        Optional<byte[]> value2 = tree.get("key2".getBytes());
        Optional<byte[]> value3 = tree.get("key3".getBytes());
        Optional<byte[]> value4 = tree.get("key4".getBytes());
        Optional<byte[]> valueNonExistent = tree.get("key999".getBytes());

        assertTrue(value1.isPresent());
        assertArrayEquals("value1-v2".getBytes(), value1.get()); // Latest update was in v2

        assertTrue(value2.isPresent());
        assertArrayEquals("value2-v3".getBytes(), value2.get()); // Latest update was in v3

        assertTrue(value3.isPresent());
        assertArrayEquals("value3-v2".getBytes(), value3.get()); // Added in v2

        assertTrue(value4.isPresent());
        assertArrayEquals("value4-v3".getBytes(), value4.get()); // Added in v3

        assertFalse(valueNonExistent.isPresent());
    }

    @Test
    void testGetAtSpecificVersion() {
        // Test key1 across versions
        Optional<byte[]> key1v1 = tree.get("key1".getBytes(), 1);
        Optional<byte[]> key1v2 = tree.get("key1".getBytes(), 2);
        Optional<byte[]> key1v3 = tree.get("key1".getBytes(), 3);

        assertTrue(key1v1.isPresent());
        assertArrayEquals("value1-v1".getBytes(), key1v1.get());

        assertTrue(key1v2.isPresent());
        assertArrayEquals("value1-v2".getBytes(), key1v2.get());

        assertTrue(key1v3.isPresent());
        assertArrayEquals("value1-v2".getBytes(), key1v3.get()); // No change in v3

        // Test key3 which only exists from v2 onwards
        Optional<byte[]> key3v1 = tree.get("key3".getBytes(), 1);
        Optional<byte[]> key3v2 = tree.get("key3".getBytes(), 2);
        Optional<byte[]> key3v3 = tree.get("key3".getBytes(), 3);

        assertFalse(key3v1.isPresent()); // Didn't exist in v1
        assertTrue(key3v2.isPresent());
        assertArrayEquals("value3-v2".getBytes(), key3v2.get());
        assertTrue(key3v3.isPresent());
        assertArrayEquals("value3-v2".getBytes(), key3v3.get());
    }

    @Test
    void testGetProofInclusion() {
        // Test inclusion proof
        Optional<JmtProof> proofOpt = tree.getProof("key1".getBytes(), 2);

        assertTrue(proofOpt.isPresent());
        JmtProof proof = proofOpt.get();

        assertNotNull(proof.value());
        assertArrayEquals("value1-v2".getBytes(), proof.value());
        assertEquals(JmtProof.ProofType.INCLUSION, proof.type());

        // Verify the proof can be verified
        byte[] rootHash = store.rootHash(2).orElseThrow();
        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);
        assertTrue(JmtProofVerifier.verify(
                rootHash,
                "key1".getBytes(),
                proof.value(),
                proof,
                hashFn,
                commitments
        ));
    }

    @Test
    void testGetProofNonExistent() {
        // Test non-inclusion proof
        Optional<JmtProof> proofOpt = tree.getProof("key999".getBytes(), 2);

        assertTrue(proofOpt.isPresent());
        JmtProof proof = proofOpt.get();

        assertNull(proof.value());
        assertTrue(proof.type() == JmtProof.ProofType.NON_INCLUSION_EMPTY ||
                   proof.type() == JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF);

        // Verify the non-inclusion proof
        byte[] rootHash = store.rootHash(2).orElseThrow();
        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);
        assertTrue(JmtProofVerifier.verify(
                rootHash,
                "key999".getBytes(),
                null,
                proof,
                hashFn,
                commitments
        ));
    }

    @Test
    void testGetProofBeforeKeyExists() {
        // key3 was added in v2, query v1
        Optional<JmtProof> proofOpt = tree.getProof("key3".getBytes(), 1);

        assertTrue(proofOpt.isPresent());
        JmtProof proof = proofOpt.get();

        assertNull(proof.value());
        assertEquals(JmtProof.ProofType.NON_INCLUSION_EMPTY, proof.type());
    }

    @Test
    void testPerformanceComparison() {
        // Warm up
        for (int i = 0; i < 100; i++) {
            tree.get("key1".getBytes(), 2);
            tree.getProof("key1".getBytes(), 2);
        }

        // Measure get()
        long startGet = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            tree.get("key1".getBytes(), 2);
        }
        long getTime = System.nanoTime() - startGet;

        // Measure getProof()
        long startProof = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            tree.getProof("key1".getBytes(), 2);
        }
        long proofTime = System.nanoTime() - startProof;

        double speedup = (double) proofTime / getTime;

        System.out.printf("Performance comparison (1000 iterations):%n");
        System.out.printf("  get():        %,d ns/op (%.2f μs/op)%n",
                getTime / 1000, getTime / 1000.0 / 1000.0);
        System.out.printf("  getProof():   %,d ns/op (%.2f μs/op)%n",
                proofTime / 1000, proofTime / 1000.0 / 1000.0);
        System.out.printf("  Speedup:      %.2fx%n", speedup);

        // For in-memory store, expect at least 2x speedup for get()
        assertTrue(speedup >= 2.0,
                String.format("Expected get() to be at least 2x faster, but was only %.2fx", speedup));
    }

    @Test
    void testAllMethodsReturnConsistentResults() {
        byte[] key = "key1".getBytes();
        long version = 2;

        // Both get methods should return the same value as getProof
        Optional<byte[]> value1 = tree.get(key, version);
        Optional<JmtProof> proofOpt = tree.getProof(key, version);

        assertTrue(value1.isPresent());
        assertTrue(proofOpt.isPresent());
        assertArrayEquals(value1.get(), proofOpt.get().value());
        assertArrayEquals("value1-v2".getBytes(), value1.get());
    }

    @Test
    void testProofProperties() {
        Optional<JmtProof> proofOpt = tree.getProof("key1".getBytes(), 2);
        assertTrue(proofOpt.isPresent());

        JmtProof proof = proofOpt.get();
        assertEquals(JmtProof.ProofType.INCLUSION, proof.type());
        assertNotNull(proof.value());
        assertArrayEquals("value1-v2".getBytes(), proof.value());
        assertFalse(proof.steps().isEmpty());
        System.out.println("JmtProof type: " + proof.type() + ", steps: " + proof.steps().size());
    }
}
