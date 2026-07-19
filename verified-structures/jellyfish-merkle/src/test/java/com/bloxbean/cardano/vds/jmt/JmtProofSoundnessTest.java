package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial proof-verification tests. These assert that forged / tampered proofs are
 * REJECTED, and that honest proofs still verify. They exist to lock in the leaf-hash
 * binding fix (H(0x00 || keyHash || valueHash)) that closed the earlier forgery holes.
 */
class JmtProofSoundnessTest {

    private HashFunction hashFn;
    private CommitmentScheme commitments;
    private InMemoryJmtStore store;
    private JellyfishMerkleTree tree;

    @BeforeEach
    void setUp() {
        hashFn = Blake2b256::digest;
        commitments = new ClassicJmtCommitmentScheme(hashFn);
        store = new InMemoryJmtStore();
        tree = new JellyfishMerkleTree(store, commitments, hashFn);
    }

    private byte[] putSingle(long version, String key, String value) {
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(key.getBytes(), value.getBytes());
        return tree.put(version, updates).rootHash();
    }

    private static JmtProof newProof(JmtProof.ProofType type, List<JmtProof.BranchStep> steps,
                                     byte[] value, byte[] valueHash, NibblePath suffix, byte[] leafKeyHash,
                                     byte[] conflictingKeyHash, byte[] conflictingValueHash,
                                     NibblePath conflictingSuffix) {
        try {
            Constructor<JmtProof> ctor = JmtProof.class.getDeclaredConstructor(
                    JmtProof.ProofType.class, List.class, byte[].class, byte[].class,
                    NibblePath.class, byte[].class, byte[].class, byte[].class, NibblePath.class);
            ctor.setAccessible(true);
            return ctor.newInstance(type, steps, value, valueHash, suffix, leafKeyHash,
                    conflictingKeyHash, conflictingValueHash, conflictingSuffix);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- positive baselines ----

    @Test
    void honestInclusionVerifies() {
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put("alice".getBytes(), "100".getBytes());
        updates.put("bob".getBytes(), "200".getBytes());
        updates.put("carol".getBytes(), "300".getBytes());
        byte[] root = tree.put(1L, updates).rootHash();

        for (String k : new String[]{"alice", "bob", "carol"}) {
            JmtProof proof = tree.getProof(k.getBytes(), 1L).orElseThrow();
            assertEquals(JmtProof.ProofType.INCLUSION, proof.type(), k);
            byte[] value = proof.value();
            assertTrue(JmtProofVerifier.verify(root, k.getBytes(), value, proof, hashFn, commitments),
                    "honest inclusion must verify for " + k);
            // wire path too
            byte[] wire = tree.getProofWire(k.getBytes(), 1L).orElseThrow();
            assertTrue(tree.verifyProofWire(root, k.getBytes(), value, true, wire),
                    "honest wire inclusion must verify for " + k);
        }
    }

    @Test
    void honestNonInclusionVerifies() {
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put("alice".getBytes(), "100".getBytes());
        updates.put("bob".getBytes(), "200".getBytes());
        byte[] root = tree.put(1L, updates).rootHash();

        byte[] absent = "does-not-exist".getBytes();
        JmtProof proof = tree.getProof(absent, 1L).orElseThrow();
        assertNotEquals(JmtProof.ProofType.INCLUSION, proof.type());
        assertTrue(JmtProofVerifier.verify(root, absent, null, proof, hashFn, commitments),
                "honest non-inclusion must verify");
    }

    // ---- forgeries that MUST be rejected ----

    @Test
    void forgedNonInclusionOfPresentKeyRejected() {
        byte[] root = putSingle(1L, "alice", "balance:100");

        byte[] realKeyHash = hashFn.digest("alice".getBytes());
        NibblePath realSuffix = NibblePath.of(Nibbles.toNibbles(realKeyHash));
        byte[] realValueHash = hashFn.digest("balance:100".getBytes());
        byte[] bogusConflict = hashFn.digest("mallory".getBytes());

        JmtProof forged = newProof(JmtProof.ProofType.NON_INCLUSION_DIFFERENT_LEAF,
                Collections.emptyList(), null, null, null, null,
                bogusConflict, realValueHash, realSuffix);

        assertFalse(JmtProofVerifier.verify(root, "alice".getBytes(), null, forged, hashFn, commitments),
                "must reject forged non-inclusion of a present key");
    }

    @Test
    void malformedConflictingDigestLengthsCannotReuseARealLeafPreimage() {
        byte[] key = "alice".getBytes();
        byte[] value = "balance:100".getBytes();
        byte[] root = putSingle(1L, "alice", "balance:100");
        byte[] keyHash = hashFn.digest(key);
        byte[] valueHash = hashFn.digest(value);

        // Without digest-size validation, moving the final key-hash byte to the value-hash
        // prefix preserves 0x00 || keyHash || valueHash while making the claimed key different.
        byte[] shortKeyHash = Arrays.copyOf(keyHash, keyHash.length - 1);
        byte[] longValueHash = new byte[valueHash.length + 1];
        longValueHash[0] = keyHash[keyHash.length - 1];
        System.arraycopy(valueHash, 0, longValueHash, 1, valueHash.length);
        JmtProof malformed = JmtProof.nonInclusionDifferentLeaf(
                Collections.emptyList(), shortKeyHash, longValueHash, NibblePath.EMPTY);

        assertFalse(JmtProofVerifier.verify(root, key, null, malformed, hashFn, commitments));
    }

    @Test
    void forgedInclusionOfAbsentKeyRejected() {
        byte[] root = putSingle(1L, "alice", "balance:100");

        byte[] realValueHash = hashFn.digest("balance:100".getBytes());
        NibblePath realSuffix = NibblePath.of(Nibbles.toNibbles(hashFn.digest("alice".getBytes())));
        byte[] fakeKey = "attacker-key".getBytes();

        JmtProof forged = newProof(JmtProof.ProofType.INCLUSION, Collections.emptyList(),
                "balance:100".getBytes(), realValueHash, realSuffix, hashFn.digest(fakeKey),
                null, null, null);

        assertFalse(JmtProofVerifier.verify(root, fakeKey, "balance:100".getBytes(), forged, hashFn, commitments),
                "must reject forged inclusion of an absent key");
    }

    @Test
    void tamperedValueRejected() {
        byte[] root = putSingle(1L, "alice", "balance:100");
        JmtProof proof = tree.getProof("alice".getBytes(), 1L).orElseThrow();
        assertFalse(JmtProofVerifier.verify(root, "alice".getBytes(), "balance:999".getBytes(), proof, hashFn, commitments),
                "must reject inclusion proof checked against a different value");
    }

    @Test
    void wrongRootRejected() {
        byte[] root = putSingle(1L, "alice", "balance:100");
        JmtProof proof = tree.getProof("alice".getBytes(), 1L).orElseThrow();
        byte[] wrongRoot = root.clone();
        wrongRoot[0] ^= 0x01;
        assertFalse(JmtProofVerifier.verify(wrongRoot, "alice".getBytes(), proof.value(), proof, hashFn, commitments),
                "must reject against a wrong root");
    }

    @Test
    void mismatchedKeyRejected() {
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put("alice".getBytes(), "100".getBytes());
        updates.put("bob".getBytes(), "200".getBytes());
        byte[] root = tree.put(1L, updates).rootHash();

        // Proof generated for alice, but verified as if it were bob's key.
        JmtProof aliceProof = tree.getProof("alice".getBytes(), 1L).orElseThrow();
        assertFalse(JmtProofVerifier.verify(root, "bob".getBytes(), aliceProof.value(), aliceProof, hashFn, commitments),
                "must reject alice's proof presented for bob");
    }

    @Test
    void alteredSiblingHashRejected() {
        // Build a multi-key tree so proofs contain branch steps with sibling hashes.
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < 32; i++) {
            updates.put(("key-" + i).getBytes(), ("val-" + i).getBytes());
        }
        byte[] root = tree.put(1L, updates).rootHash();

        String target = "key-7";
        JmtProof proof = tree.getProof(target.getBytes(), 1L).orElseThrow();
        assertEquals(JmtProof.ProofType.INCLUSION, proof.type());
        assertFalse(proof.steps().isEmpty(), "expected branch steps for a 32-key tree");

        // Corrupt one sibling hash in the deepest branch step.
        List<JmtProof.BranchStep> steps = proof.steps();
        JmtProof.BranchStep last = steps.get(steps.size() - 1);
        byte[][] children = last.childHashes();
        int victim = -1;
        for (int i = 0; i < 16; i++) {
            if (i != last.childIndex() && children[i] != null) { victim = i; break; }
        }
        assertTrue(victim >= 0, "expected a sibling to corrupt");
        children[victim] = children[victim].clone();
        children[victim][0] ^= 0x01;
        JmtProof.BranchStep tampered = new JmtProof.BranchStep(last.prefix(), children, last.childIndex(),
                last.hasSingleNeighbor(), last.neighborNibble(), last.forkNeighborPrefix(),
                last.forkNeighborRoot(), last.leafNeighborKeyHash(), last.leafNeighborValueHash());
        java.util.List<JmtProof.BranchStep> newSteps = new java.util.ArrayList<>(steps);
        newSteps.set(newSteps.size() - 1, tampered);

        JmtProof tamperedProof = newProof(JmtProof.ProofType.INCLUSION, newSteps, proof.value(),
                proof.valueHash(), proof.suffix(), proof.leafKeyHash(), null, null, null);
        assertFalse(JmtProofVerifier.verify(root, target.getBytes(), proof.value(), tamperedProof, hashFn, commitments),
                "must reject a proof with an altered sibling hash");
    }

    @Test
    void wireNonInclusionNotAcceptedAsInclusion() {
        // Multi-key tree so an absent key yields a NON_INCLUSION_EMPTY (missing-branch) wire proof.
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) {
            updates.put(("k-" + i).getBytes(), ("v-" + i).getBytes());
        }
        byte[] root = tree.put(1L, updates).rootHash();

        byte[] absent = "definitely-absent-key".getBytes();
        JmtProof proof = tree.getProof(absent, 1L).orElseThrow();
        assertNotEquals(JmtProof.ProofType.INCLUSION, proof.type());

        byte[] wire = tree.getProofWire(absent, 1L).orElseThrow();
        // Honest non-inclusion still verifies (including = false).
        assertTrue(tree.verifyProofWire(root, absent, null, false, wire), "honest wire non-inclusion must verify");
        // The same absence proof MUST NOT be accepted as an inclusion of any invented value.
        assertFalse(tree.verifyProofWire(root, absent, "forged-value".getBytes(), true, wire),
                "a non-inclusion wire proof must not verify as inclusion");
    }

    @Test
    void emptyTreeWireNotAcceptedAsInclusion() {
        // Empty tree at version 1.
        byte[] root = tree.put(1L, new LinkedHashMap<>()).rootHash();
        byte[] anyKey = "anything".getBytes();
        JmtProof proof = tree.getProof(anyKey, 1L).orElseThrow();
        assertEquals(JmtProof.ProofType.NON_INCLUSION_EMPTY, proof.type());
        byte[] wire = tree.getProofWire(anyKey, 1L).orElseThrow();
        assertFalse(tree.verifyProofWire(root, anyKey, "forged".getBytes(), true, wire),
                "empty-tree proof must not verify as inclusion");
    }

    @Test
    void digestLengthMisconfigurationThrowsInsteadOfSilentFalse() {
        byte[] root = putSingle(1L, "alice", "balance:100");
        JmtProof proof = tree.getProof("alice".getBytes(), 1L).orElseThrow();

        // A hash function whose digest length disagrees with the commitment scheme is a caller
        // configuration bug and must surface loudly, not be swallowed as "proof invalid".
        HashFunction wrongLength = data -> new byte[28];
        assertThrows(IllegalArgumentException.class,
                () -> JmtProofVerifier.verify(root, "alice".getBytes(), "balance:100".getBytes(),
                        proof, wrongLength, commitments));
    }

    @Test
    void garbageWireRejected() {
        byte[] root = putSingle(1L, "alice", "balance:100");
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, 0x03};
        assertFalse(tree.verifyProofWire(root, "alice".getBytes(), "balance:100".getBytes(), true, garbage),
                "must reject garbage wire bytes (not throw an unchecked error to the caller as success)");
    }

    @Test
    void wireWithTrailingCborItemRejected() {
        byte[] root = putSingle(1L, "alice", "balance:100");
        byte[] wire = tree.getProofWire("alice".getBytes(), 1L).orElseThrow();
        byte[] malleable = Arrays.copyOf(wire, wire.length + 1);
        malleable[malleable.length - 1] = (byte) 0x80; // a second, empty CBOR array

        assertFalse(tree.verifyProofWire(root, "alice".getBytes(), "balance:100".getBytes(), true, malleable));
    }

    @Test
    void oversizedWireProofRejectedBeforeDecoding() {
        byte[] root = putSingle(1L, "alice", "balance:100");
        byte[] oversized = new byte[1024 * 1024 + 1];
        assertFalse(tree.verifyProofWire(root, "alice".getBytes(), "balance:100".getBytes(), true, oversized));
    }

    @Test
    void hugeDeclaredWireContainerRejectedBeforeObjectAllocation() {
        byte[] root = putSingle(1L, "alice", "balance:100");
        byte[] hugeArray = new byte[]{(byte) 0x9A, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        assertFalse(tree.verifyProofWire(root, "alice".getBytes(),
                "balance:100".getBytes(), true, hugeArray));
    }

    @Test
    void extensionNodeWireProofIsRejectedByStableProfile() throws Exception {
        byte[] root = putSingle(1L, "alice", "balance:100");
        JmtExtensionNode extension = JmtExtensionNode.of(
                new byte[]{0x11}, Blake2b256.digest("child".getBytes()));
        Array proof = new Array();
        proof.add(new ByteString(extension.encode()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new CborEncoder(output).encode(proof);

        assertFalse(tree.verifyProofWire(root, "alice".getBytes(),
                "balance:100".getBytes(), true, output.toByteArray()));
    }

    @Test
    void uncommittedCompressedPathMetadataIsRejectedFromWireProof() throws Exception {
        byte[] key = "alice".getBytes();
        byte[] value = "balance:100".getBytes();
        byte[] keyHash = hashFn.digest(key);
        byte[] valueHash = hashFn.digest(value);
        byte[] leafHash = commitments.commitLeaf(keyHash, valueHash);
        int childNibble = Nibbles.toNibbles(keyHash)[0];
        int siblingNibble = (childNibble + 1) & 0x0F;
        byte[] siblingHash = Blake2b256.digest("sibling".getBytes());
        int bitmap = (1 << childNibble) | (1 << siblingNibble);
        byte[][] compact = childNibble < siblingNibble
                ? new byte[][]{leafHash, siblingHash}
                : new byte[][]{siblingHash, leafHash};
        JmtInternalNode internal = JmtInternalNode.of(bitmap, compact, new byte[]{0x00});
        byte[][] full = new byte[16][];
        full[childNibble] = leafHash;
        full[siblingNibble] = siblingHash;
        byte[] root = commitments.commitBranch(NibblePath.EMPTY, full);

        Array proof = new Array();
        proof.add(new ByteString(internal.encode()));
        proof.add(new ByteString(JmtLeafNode.of(keyHash, valueHash).encode()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new CborEncoder(output).encode(proof);

        assertFalse(tree.verifyProofWire(root, key, value, true, output.toByteArray()));
    }
}
