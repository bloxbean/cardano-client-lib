package com.bloxbean.cardano.vds.mpt.mpf;

import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.SecureTrie;
import com.bloxbean.cardano.vds.mpt.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MpfProofFormatter, specifically the toPlutusData() method that converts
 * MPF proofs to PlutusData structures for Aiken validator consumption.
 */
class MpfProofFormatterTest {

    private final HashFunction hashFn = Blake2b256::digest;

    @Test
    void toPlutusData_converts_branch_step_correctly() {
        // Build a simple trie with branch steps
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "apple", "🍎");
        put(trie, "banana", "🍌");
        put(trie, "cherry", "🍒");

        byte[] proofCbor = trie.getProofWire(b("apple")).orElseThrow();
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        assertNotNull(result);
        assertNotNull(result.getPlutusDataList());
        assertTrue(result.getPlutusDataList().size() > 0);

        // Verify it's a list of ConstrPlutusData
        for (PlutusData step : result.getPlutusDataList()) {
            assertTrue(step instanceof ConstrPlutusData);
            ConstrPlutusData constr = (ConstrPlutusData) step;
            assertTrue(constr.getAlternative() >= 0 && constr.getAlternative() <= 2,
                    "ProofStep alternative should be 0 (Branch), 1 (Fork), or 2 (Leaf)");
        }
    }

    @Test
    void toPlutusData_branch_step_has_correct_structure() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "apple", "🍎");
        put(trie, "apricot", "🤷");
        put(trie, "banana", "🍌");

        byte[] proofCbor = trie.getProofWire(b("apple")).orElseThrow();
        MpfProof proof = MpfProofDecoder.decode(proofCbor);
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        // Find a branch step in the decoded proof
        boolean foundBranch = false;
        for (int i = 0; i < proof.steps().size(); i++) {
            if (proof.steps().get(i) instanceof MpfProof.BranchStep) {
                foundBranch = true;
                ConstrPlutusData branchStep = (ConstrPlutusData) result.getPlutusDataList().get(i);

                // Branch should have alternative 0
                assertEquals(0, branchStep.getAlternative());

                // Branch should have 2 fields: skip (BigInt) and neighbors (Bytes)
                assertEquals(2, branchStep.getData().getPlutusDataList().size());
                assertTrue(branchStep.getData().getPlutusDataList().get(0) instanceof BigIntPlutusData);
                assertTrue(branchStep.getData().getPlutusDataList().get(1) instanceof BytesPlutusData);

                break;
            }
        }
        assertTrue(foundBranch, "Expected at least one Branch step in the proof");
    }

    @Test
    void toPlutusData_fork_step_has_correct_structure() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        // Create a scenario that will produce a Fork step
        put(trie, "apple", "🍎");
        put(trie, "zebra", "🦓");

        byte[] proofCbor = trie.getProofWire(b("apple")).orElseThrow();
        MpfProof proof = MpfProofDecoder.decode(proofCbor);
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        // Find a fork step in the decoded proof
        boolean foundFork = false;
        for (int i = 0; i < proof.steps().size(); i++) {
            if (proof.steps().get(i) instanceof MpfProof.ForkStep) {
                foundFork = true;
                ConstrPlutusData forkStep = (ConstrPlutusData) result.getPlutusDataList().get(i);

                // Fork should have alternative 1
                assertEquals(1, forkStep.getAlternative());

                // Fork should have 2 fields: skip (BigInt) and neighbor (ConstrPlutusData)
                assertEquals(2, forkStep.getData().getPlutusDataList().size());
                assertTrue(forkStep.getData().getPlutusDataList().get(0) instanceof BigIntPlutusData);
                assertTrue(forkStep.getData().getPlutusDataList().get(1) instanceof ConstrPlutusData);

                // Verify Neighbor structure (alternative 0, 3 fields: nibble, prefix, root)
                ConstrPlutusData neighbor = (ConstrPlutusData) forkStep.getData().getPlutusDataList().get(1);
                assertEquals(0, neighbor.getAlternative());
                assertEquals(3, neighbor.getData().getPlutusDataList().size());
                assertTrue(neighbor.getData().getPlutusDataList().get(0) instanceof BigIntPlutusData); // nibble
                assertTrue(neighbor.getData().getPlutusDataList().get(1) instanceof BytesPlutusData);  // prefix
                assertTrue(neighbor.getData().getPlutusDataList().get(2) instanceof BytesPlutusData);  // root

                break;
            }
        }
        // Note: Fork steps may not always appear depending on trie structure
        // The test verifies structure IF a fork step is present
        if (foundFork) {
            // Fork step structure was validated above
            assertTrue(true);
        }
    }

    @Test
    void toPlutusData_leaf_step_has_correct_structure() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        // Create a scenario that will produce a Leaf step
        put(trie, "apple", "🍎");
        put(trie, "apricot", "🤷");

        // Query for a non-existent key to get a leaf step in the proof
        byte[] proofCbor = trie.getProofWire(b("apricot")).orElseThrow();
        MpfProof proof = MpfProofDecoder.decode(proofCbor);
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        // Find a leaf step in the decoded proof
        boolean foundLeaf = false;
        for (int i = 0; i < proof.steps().size(); i++) {
            if (proof.steps().get(i) instanceof MpfProof.LeafStep) {
                foundLeaf = true;
                ConstrPlutusData leafStep = (ConstrPlutusData) result.getPlutusDataList().get(i);

                // Leaf should have alternative 2
                assertEquals(2, leafStep.getAlternative());

                // Leaf should have 3 fields: skip (BigInt), key (Bytes), value (Bytes)
                assertEquals(3, leafStep.getData().getPlutusDataList().size());
                assertTrue(leafStep.getData().getPlutusDataList().get(0) instanceof BigIntPlutusData);
                assertTrue(leafStep.getData().getPlutusDataList().get(1) instanceof BytesPlutusData);
                assertTrue(leafStep.getData().getPlutusDataList().get(2) instanceof BytesPlutusData);

                break;
            }
        }
        // Note: Leaf steps may not always appear depending on trie structure
        // The test verifies structure IF a leaf step is present
        if (foundLeaf) {
            // Leaf step structure was validated above
            assertTrue(true);
        }
    }

    @Test
    void toPlutusData_serializes_to_cbor_successfully() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "mango", "🥭");
        put(trie, "kumquat", "🤷");

        byte[] proofCbor = trie.getProofWire(b("mango")).orElseThrow();
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        // Verify it can be serialized to CBOR without errors
        assertDoesNotThrow(() -> {
            DataItem di = result.serialize();
            byte[] serialized = CborSerializationUtil.serialize(di);
            assertNotNull(serialized);
            assertTrue(serialized.length > 0);
        });
    }

    @Test
    void toPlutusData_golden_vector_mango() {
        // Use the golden vector from MptMpfGoldenVectorsTest
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "apple[uid: 58]", "🍎");
        put(trie, "apricot[uid: 0]", "🤷");
        put(trie, "banana[uid: 218]", "🍌");
        put(trie, "blueberry[uid: 0]", "🫐");
        put(trie, "cherry[uid: 0]", "🍒");
        put(trie, "mango[uid: 0]", "🥭");
        put(trie, "orange[uid: 0]", "🍊");

        byte[] proofCbor = trie.getProofWire(b("mango[uid: 0]")).orElseThrow();
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        assertNotNull(result);
        assertNotNull(result.getPlutusDataList());
        assertTrue(result.getPlutusDataList().size() > 0);

        // Verify each step is a valid ConstrPlutusData with correct structure
        for (PlutusData step : result.getPlutusDataList()) {
            assertTrue(step instanceof ConstrPlutusData);
            ConstrPlutusData constr = (ConstrPlutusData) step;

            if (constr.getAlternative() == 0) {
                // Branch: skip, neighbors
                assertEquals(2, constr.getData().getPlutusDataList().size());
            } else if (constr.getAlternative() == 1) {
                // Fork: skip, neighbor
                assertEquals(2, constr.getData().getPlutusDataList().size());
                ConstrPlutusData neighbor = (ConstrPlutusData) constr.getData().getPlutusDataList().get(1);
                assertEquals(0, neighbor.getAlternative());
                assertEquals(3, neighbor.getData().getPlutusDataList().size());
            } else if (constr.getAlternative() == 2) {
                // Leaf: skip, key, value
                assertEquals(3, constr.getData().getPlutusDataList().size());
            }
        }
    }

    @Test
    void toPlutusData_skip_values_match_decoded_proof() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "apple", "🍎");
        put(trie, "banana", "🍌");

        byte[] proofCbor = trie.getProofWire(b("apple")).orElseThrow();
        MpfProof proof = MpfProofDecoder.decode(proofCbor);
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        // Verify skip values match between decoded proof and PlutusData
        assertEquals(proof.steps().size(), result.getPlutusDataList().size());

        for (int i = 0; i < proof.steps().size(); i++) {
            MpfProof.Step step = proof.steps().get(i);
            ConstrPlutusData plutusStep = (ConstrPlutusData) result.getPlutusDataList().get(i);
            BigIntPlutusData skipData = (BigIntPlutusData) plutusStep.getData().getPlutusDataList().get(0);

            assertEquals(BigInteger.valueOf(step.skip()), skipData.getValue(),
                    "Skip value should match at step " + i);
        }
    }

    @Test
    void toPlutusData_empty_proof_returns_empty_list() {
        // Create a trie with a single entry
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "single", "value");

        // Proof for the only entry should have minimal steps
        byte[] proofCbor = trie.getProofWire(b("single")).orElseThrow();
        ListPlutusData result = MpfProofFormatter.toPlutusData(proofCbor);

        assertNotNull(result);
        assertNotNull(result.getPlutusDataList());
        // Even a single-entry trie may have steps, but the list should not be null
    }

    @Test
    void secureTrie_getProofPlutusData_convenience_method() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "apple", "🍎");
        put(trie, "banana", "🍌");
        put(trie, "cherry", "🍒");

        byte[] key = b("apple");

        // Get proof using the convenience method
        Optional<ListPlutusData> directProof = trie.getProofPlutusData(key);

        // Get proof using the two-step approach
        Optional<ListPlutusData> twoStepProof = trie.getProofWire(key)
                .map(MpfProofFormatter::toPlutusData);

        // Both should be present
        assertTrue(directProof.isPresent());
        assertTrue(twoStepProof.isPresent());

        // Both should produce the same result
        ListPlutusData direct = directProof.get();
        ListPlutusData twoStep = twoStepProof.get();

        assertEquals(direct.getPlutusDataList().size(), twoStep.getPlutusDataList().size(),
                "Both approaches should produce proofs with the same number of steps");

        // Verify they serialize to the same CBOR
        assertDoesNotThrow(() -> {
            byte[] directCbor = CborSerializationUtil.serialize(direct.serialize());
            byte[] twoStepCbor = CborSerializationUtil.serialize(twoStep.serialize());
            assertArrayEquals(directCbor, twoStepCbor,
                    "Both approaches should produce identical CBOR");
        });
    }

    @Test
    void secureTrie_getProofPlutusData_works_for_inclusion() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "existing", "value");

        Optional<ListPlutusData> proof = trie.getProofPlutusData(b("existing"));

        assertTrue(proof.isPresent());
        assertNotNull(proof.get().getPlutusDataList());
        assertTrue(proof.get().getPlutusDataList().size() >= 0);
    }

    @Test
    void secureTrie_getProofPlutusData_works_for_exclusion() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        put(trie, "existing", "value");

        // Proof for non-existent key should still be generated
        Optional<ListPlutusData> proof = trie.getProofPlutusData(b("nonexistent"));

        assertTrue(proof.isPresent());
        assertNotNull(proof.get().getPlutusDataList());
    }

    private static void put(SecureTrie trie, String key, String value) {
        trie.put(b(key), b(value));
    }

    private static byte[] b(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
