package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpf.proof.ProofFormatter;
import com.bloxbean.cardano.vds.mpf.proof.ProofVerifier;
import com.bloxbean.cardano.vds.mpf.reference.ReferenceMpf;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage-guided targets for MPF proofs. In a normal build they replay the committed seeds under
 * {@code src/test/resources/.../MpfFuzzTestInputs}; with {@code JAZZER_FUZZ=1} Jazzer explores new inputs.
 */
class MpfFuzzTest {

    private static final HashFunction HF = Blake2b256::digest;
    private static final MpfCommitmentScheme CS = new MpfCommitmentScheme(HF);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final int MAX_WIRE = 64 * 1024;
    private static final byte[] FRUITS_ROOT = Bytes.fromHex("4acd78f345a686361df77541b2e0b533f53362e36620a1fdd3a13e0b61a3b078");

    /** Untrusted proof bytes: verification is total (a boolean, never an exception) and formatting only throws IAE. */
    @FuzzTest(maxDuration = "10s")
    void verifierIsTotalForUntrustedProofBytes(byte[] input) {
        byte[] wire = input == null ? new byte[0] : Arrays.copyOf(input, Math.min(input.length, MAX_WIRE));
        byte[] key = "mango[uid: 0]".getBytes(StandardCharsets.UTF_8);
        byte[] value = "🥭".getBytes(StandardCharsets.UTF_8);
        for (byte[] root : new byte[][]{FRUITS_ROOT, null, CS.nullHash()}) {
            ProofVerifier.verify(root, key, value, true, wire, HF, CS);
            ProofVerifier.verify(root, key, null, false, wire, HF, CS);
        }
        try {
            ProofFormatter.toJson(wire);
            ProofFormatter.toPlutusData(wire);
        } catch (IllegalArgumentException expected) {
            // Malformed external input is the expected outcome.
        }
    }

    /**
     * Operation traces over a small key space: the root and every probed proof equal the independent reference, and
     * each proof supports the Aiken transitions (has/delete for present keys, miss/insert for absent keys).
     */
    @FuzzTest(maxDuration = "30s")
    void operationTracesMatchReferenceAndAikenSemantics(FuzzedDataProvider data) throws Exception {
        int poolSize = data.consumeInt(1, 64);
        List<byte[]> pool = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) pool.add(("k" + i + ":" + data.consumeAsciiString(4)).getBytes(StandardCharsets.UTF_8));
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        ReferenceMpf ref = new ReferenceMpf(HF);
        Map<String, byte[]> model = new HashMap<>();
        int ops = data.consumeInt(0, 200);
        for (int i = 0; i < ops && data.remainingBytes() > 0; i++) {
            byte[] k = pool.get(data.consumeInt(0, poolSize - 1));
            if (data.consumeInt(0, 3) == 0) {
                trie.delete(k);
                ref.delete(k);
                model.remove(Bytes.toHex(k));
            } else {
                byte[] v = data.consumeBytes(6);
                trie.put(k, v);
                ref.put(k, v);
                model.put(Bytes.toHex(k), v);
            }
        }
        byte[] root = trie.getRootHash();
        assertEquals(hex(ref.root()), hex(root));

        List<byte[]> probes = new ArrayList<>(pool);
        probes.add("absent-a".getBytes(StandardCharsets.UTF_8));
        probes.add("absent-b".getBytes(StandardCharsets.UTF_8));
        for (byte[] k : probes) {
            byte[] wire = trie.getProofWire(k).orElseThrow();
            JsonNode proof = OM.readTree(ProofFormatter.toJson(wire));
            assertEquals(ref.proof(k, false), proof);
            byte[] value = model.get(Bytes.toHex(k));
            if (value != null) {
                assertTrue(ProofVerifier.verify(root, k, value, true, wire, HF, CS));
                assertFalse(ProofVerifier.verify(root, k, null, false, wire, HF, CS));
                ref.delete(k);
                assertTrue(ProofVerifier.verify(ref.root(), k, null, false, wire, HF, CS));
                ref.put(k, value);
            } else {
                assertTrue(ProofVerifier.verify(root, k, null, false, wire, HF, CS));
                ref.put(k, new byte[]{7});
                assertTrue(ProofVerifier.verify(ref.root(), k, new byte[]{7}, true, wire, HF, CS));
                ref.delete(k);
            }
        }
    }

    /**
     * Mutated proofs: a mutation either breaks verification or leaves the decoded proof unchanged (only CBOR framing
     * changed). It never makes a proof verify a different value or the opposite membership.
     */
    @FuzzTest(maxDuration = "30s")
    void mutatedProofsNeverProveSomethingElse(FuzzedDataProvider data) {
        int size = data.consumeInt(1, 48);
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        for (int i = 0; i < size; i++) trie.put(("key-" + i).getBytes(StandardCharsets.UTF_8), ("v" + i).getBytes(StandardCharsets.UTF_8));
        boolean present = data.consumeBoolean();
        byte[] key = (present ? "key-" + data.consumeInt(0, size - 1) : "absent-" + data.consumeInt(0, 1000))
                .getBytes(StandardCharsets.UTF_8);
        byte[] value = trie.get(key);
        byte[] root = trie.getRootHash();
        byte[] wire = trie.getProofWire(key).orElseThrow();
        String original = ProofFormatter.toJson(wire);

        byte[] mutated = wire.clone();
        int mutations = data.consumeInt(1, 4);
        for (int i = 0; i < mutations && mutated.length > 0; i++) {
            mutated[data.consumeInt(0, mutated.length - 1)] ^= (byte) data.consumeInt(1, 255);
        }

        if (present) {
            if (ProofVerifier.verify(root, key, value, true, mutated, HF, CS)) {
                assertEquals(original, ProofFormatter.toJson(mutated));
            }
            assertFalse(ProofVerifier.verify(root, key, "other".getBytes(StandardCharsets.UTF_8), true, mutated, HF, CS));
            assertFalse(ProofVerifier.verify(root, key, null, false, mutated, HF, CS));
        } else {
            if (ProofVerifier.verify(root, key, null, false, mutated, HF, CS)) {
                assertEquals(original, ProofFormatter.toJson(mutated));
            }
            assertFalse(ProofVerifier.verify(root, key, "any".getBytes(StandardCharsets.UTF_8), true, mutated, HF, CS));
        }
    }

    private static String hex(byte[] b) {
        return b == null ? null : Bytes.toHex(b);
    }
}
