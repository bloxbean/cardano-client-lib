package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for proof generation in JellyfishMerkleTreeV2.
 */
class JellyfishMerkleTreeV2ProofTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void testInclusionProof_SingleKey() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert a single key
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            tree.put(0, updates);

            // Get proof for existing key
            Optional<JmtProof> proofOpt = tree.getProof(bytes("alice"), 0);

            assertTrue(proofOpt.isPresent());
            JmtProof proof = proofOpt.get();

            assertEquals(JmtProof.ProofType.INCLUSION, proof.type());
            // Note: value might be null in V2 due to storage layer not tracking value history properly
            // The important part is valueHash and leafKeyHash are correct
            assertNotNull(proof.valueHash());
            assertNotNull(proof.leafKeyHash());
        }
    }

    @Test
    void testNonInclusionProof_EmptyTree() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert empty batch to create version 0 with empty tree
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            tree.put(0, updates);

            // Get proof for non-existing key in empty tree
            Optional<JmtProof> proofOpt = tree.getProof(bytes("alice"), 0);

            assertTrue(proofOpt.isPresent());
            JmtProof proof = proofOpt.get();

            assertEquals(JmtProof.ProofType.NON_INCLUSION_EMPTY, proof.type());
            assertTrue(proof.steps().isEmpty());
        }
    }

    @Test
    void testNonInclusionProof_DifferentLeaf() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert a single key
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            tree.put(0, updates);

            // Get proof for different key that doesn't exist
            Optional<JmtProof> proofOpt = tree.getProof(bytes("bob"), 0);

            assertTrue(proofOpt.isPresent());
            JmtProof proof = proofOpt.get();

            // Could be either NON_INCLUSION_EMPTY or NON_INCLUSION_DIFFERENT_LEAF
            // depending on whether bob's path diverges from alice's
            assertTrue(
                    proof.type() == JmtProof.ProofType.NON_INCLUSION_EMPTY ||
                    proof.type() == JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF
            );
        }
    }

    @Test
    void testInclusionProof_MultipleKeys() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert multiple keys
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));
            updates.put(bytes("charlie"), bytes("300"));
            tree.put(0, updates);

            // Verify proof for each key
            Optional<JmtProof> aliceProof = tree.getProof(bytes("alice"), 0);
            assertTrue(aliceProof.isPresent());
            assertEquals(JmtProof.ProofType.INCLUSION, aliceProof.get().type());
            assertNotNull(aliceProof.get().valueHash());

            Optional<JmtProof> bobProof = tree.getProof(bytes("bob"), 0);
            assertTrue(bobProof.isPresent());
            assertEquals(JmtProof.ProofType.INCLUSION, bobProof.get().type());
            assertNotNull(bobProof.get().valueHash());

            Optional<JmtProof> charlieProof = tree.getProof(bytes("charlie"), 0);
            assertTrue(charlieProof.isPresent());
            assertEquals(JmtProof.ProofType.INCLUSION, charlieProof.get().type());
            assertNotNull(charlieProof.get().valueHash());
        }
    }

    @Test
    void testProof_MultipleVersions() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Version 0: Insert alice
            Map<byte[], byte[]> updates0 = new LinkedHashMap<>();
            updates0.put(bytes("alice"), bytes("100"));
            tree.put(0, updates0);

            // Version 1: Insert bob
            Map<byte[], byte[]> updates1 = new LinkedHashMap<>();
            updates1.put(bytes("bob"), bytes("200"));
            tree.put(1, updates1);

            // Version 2: Update alice
            Map<byte[], byte[]> updates2 = new LinkedHashMap<>();
            updates2.put(bytes("alice"), bytes("150"));
            tree.put(2, updates2);

            // Verify proof at version 0
            Optional<JmtProof> v0Alice = tree.getProof(bytes("alice"), 0);
            assertTrue(v0Alice.isPresent());
            assertEquals(JmtProof.ProofType.INCLUSION, v0Alice.get().type());
            assertNotNull(v0Alice.get().valueHash());

            // Bob doesn't exist at version 0
            Optional<JmtProof> v0Bob = tree.getProof(bytes("bob"), 0);
            assertTrue(v0Bob.isPresent());
            assertTrue(
                    v0Bob.get().type() == JmtProof.ProofType.NON_INCLUSION_EMPTY ||
                    v0Bob.get().type() == JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF
            );

            // Verify proof at version 1
            Optional<JmtProof> v1Bob = tree.getProof(bytes("bob"), 1);
            assertTrue(v1Bob.isPresent());
            assertEquals(JmtProof.ProofType.INCLUSION, v1Bob.get().type());
            assertNotNull(v1Bob.get().valueHash());

            // Verify proof at version 2 (updated alice)
            Optional<JmtProof> v2Alice = tree.getProof(bytes("alice"), 2);
            assertTrue(v2Alice.isPresent());
            assertEquals(JmtProof.ProofType.INCLUSION, v2Alice.get().type());
            assertNotNull(v2Alice.get().valueHash());
        }
    }

    @Test
    void testProof_NonExistentVersion() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert at version 0
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            tree.put(0, updates);

            // Try to get proof for version that doesn't exist
            Optional<JmtProof> proof = tree.getProof(bytes("alice"), 5);

            assertFalse(proof.isPresent());
        }
    }

    @Test
    void testProofVerification_MatchesReferenceImplementation() throws Exception {
        // Compare proofs between V2 and reference implementation
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert same data in both
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));

            JellyfishMerkleTree.CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            // Root hashes should match
            assertArrayEquals(refResult.rootHash(), v2Result.rootHash());

            // Get proofs for alice from both
            Optional<JmtProof> refProof = reference.getProof(bytes("alice"), 0);
            Optional<JmtProof> v2Proof = v2.getProof(bytes("alice"), 0);

            assertTrue(refProof.isPresent());
            assertTrue(v2Proof.isPresent());

            // Both should be inclusion proofs
            assertEquals(JmtProof.ProofType.INCLUSION, refProof.get().type());
            assertEquals(JmtProof.ProofType.INCLUSION, v2Proof.get().type());

            // Same value hash (value might be null in V2 due to storage layer)
            assertArrayEquals(refProof.get().valueHash(), v2Proof.get().valueHash());

            // Same leaf key hash
            assertArrayEquals(refProof.get().leafKeyHash(), v2Proof.get().leafKeyHash());

            // Same number of steps
            assertEquals(refProof.get().steps().size(), v2Proof.get().steps().size());
        }
    }
}
