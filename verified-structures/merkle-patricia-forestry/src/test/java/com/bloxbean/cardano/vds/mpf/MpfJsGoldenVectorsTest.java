package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpf.proof.ProofFormatter;
import com.bloxbean.cardano.vds.mpf.proof.ProofVerifier;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential test against the official off-chain library ({@code @aiken-lang/merkle-patricia-forestry}, see
 * {@code src/test/js}). For every trie in {@code js-vectors-v1.json}, CCL must produce the same root and exactly the
 * same proof steps, for present keys and for absent keys (the library proves an absent key by inserting it,
 * proving, and removing it again). The test also checks the Aiken transitions each proof must support:
 * {@code has}/{@code delete} for present keys and {@code miss}/{@code insert} for absent keys.
 */
class MpfJsGoldenVectorsTest {

    private static final HashFunction HF = Blake2b256::digest;
    private static final MpfCommitmentScheme CS = new MpfCommitmentScheme(HF);
    private static final ObjectMapper OM = new ObjectMapper();
    private static JsonNode vectors;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = MpfJsGoldenVectorsTest.class.getResourceAsStream("/mpf-compat/js-vectors-v1.json")) {
            assertNotNull(in, "missing js-vectors-v1.json");
            vectors = OM.readTree(in);
        }
        assertEquals("@aiken-lang/merkle-patricia-forestry@1.3.1", vectors.get("library").asText());
    }

    @TestFactory
    List<DynamicTest> cclProofsEqualOfficialLibraryProofs() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode t : vectors.get("tries")) {
            tests.add(DynamicTest.dynamicTest(t.get("name").asText(), () -> checkTrie(t)));
        }
        return tests;
    }

    /**
     * The 30 fruit proofs hard-coded in Aiken's own test suite ({@code merkle-patricia-forestry.tests.ak}), with the
     * root after deleting each fruit, as used by its {@code example_has/insert/delete} tests.
     */
    @TestFactory
    List<DynamicTest> cclProofsEqualAikenTestSuiteFruitProofs() throws Exception {
        JsonNode doc;
        try (InputStream in = MpfJsGoldenVectorsTest.class.getResourceAsStream("/mpf-compat/aiken-tests-fruit-proofs.json")) {
            assertNotNull(in, "missing aiken-tests-fruit-proofs.json");
            doc = OM.readTree(in);
        }
        JsonNode fruits = vectors.get("tries").get(0);
        assertEquals("fruits", fruits.get("name").asText());
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        for (JsonNode e : fruits.get("entries")) trie.put(Bytes.fromHex(e.get(0).asText()), Bytes.fromHex(e.get(1).asText()));
        assertEquals(doc.get("root").asText(), Bytes.toHex(trie.getRootHash()));

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode p : doc.get("proofs")) {
            String fruit = p.get("key").asText();
            tests.add(DynamicTest.dynamicTest(fruit, () -> {
                byte[] key = fruit.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] value = trie.get(key);
                byte[] wire = trie.getProofWire(key).orElseThrow();
                assertEquals(p.get("proof"), OM.readTree(ProofFormatter.toJson(wire)), fruit);
                byte[] root = trie.getRootHash();
                trie.delete(key);
                assertEquals(p.get("withoutRoot").asText(), Bytes.toHex(trie.getRootHash()), fruit + " without");
                trie.put(key, value);
                assertArrayEquals(root, trie.getRootHash());
            }));
        }
        assertEquals(30, tests.size());
        return tests;
    }

    private static void checkTrie(JsonNode t) throws Exception {
        String name = t.get("name").asText();
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        for (JsonNode e : t.get("entries")) trie.put(Bytes.fromHex(e.get(0).asText()), Bytes.fromHex(e.get(1).asText()));
        byte[] root = trie.getRootHash();
        assertEquals(t.get("root").asText(), Bytes.toHex(root), name + " root");

        for (JsonNode v : t.get("present")) {
            byte[] key = Bytes.fromHex(v.get("key").asText());
            byte[] value = trie.get(key);
            byte[] wire = trie.getProofWire(key).orElseThrow();
            assertProofEquals(name, v, wire);
            assertTrue(ProofVerifier.verify(root, key, value, true, wire, HF, CS), name + " has");

            // delete: including(proof) == root and excluding(proof) == root without the key
            trie.delete(key);
            byte[] without = trie.getRootHash();
            assertTrue(ProofVerifier.verify(without, key, null, false, wire, HF, CS), name + " delete/excluding");
            trie.put(key, value);
            assertArrayEquals(root, trie.getRootHash(), name + " reinsert");
        }

        for (JsonNode v : t.get("absent")) {
            byte[] key = Bytes.fromHex(v.get("key").asText());
            byte[] wire = trie.getProofWire(key).orElseThrow();
            assertProofEquals(name, v, wire);
            assertTrue(ProofVerifier.verify(root, key, null, false, wire, HF, CS), name + " miss");

            // insert: excluding(proof) == root and including(proof, value) == root with the key
            byte[] value = Bytes.fromHex("ca11ab1e");
            trie.put(key, value);
            byte[] with = trie.getRootHash();
            assertTrue(ProofVerifier.verify(with, key, value, true, wire, HF, CS), name + " insert/including");
            trie.delete(key);
            assertArrayEquals(root, trie.getRootHash(), name + " remove again");
        }
    }

    private static void assertProofEquals(String trie, JsonNode vector, byte[] wire) throws Exception {
        String key = vector.get("key").asText();
        JsonNode expected = vector.get("proof");
        JsonNode actual = OM.readTree(ProofFormatter.toJson(wire));
        assertEquals(expected, actual, trie + " proof for key " + key);

        if (vector.has("cbor")) {
            byte[] jsCbor = Bytes.fromHex(vector.get("cbor").asText());
            // The library's CBOR (indefinite-length framing) decodes to the same proof ...
            assertEquals(expected, OM.readTree(ProofFormatter.toJson(jsCbor)), trie + " JS CBOR decodes, key " + key);
            // ... and is byte-identical to CCL's on-chain PlutusData serialisation. The one exception is the empty
            // proof, which the library frames as an indefinite empty list (9fff) and CCL as a definite one (80);
            // both are the same Plutus Data value.
            ListPlutusData data = ProofFormatter.toPlutusData(wire);
            String cclCbor = Bytes.toHex(CborSerializationUtil.serialize(data.serialize()));
            if (expected.isEmpty()) {
                assertEquals("9fff", vector.get("cbor").asText());
                assertEquals("80", cclCbor);
            } else {
                assertEquals(vector.get("cbor").asText(), cclCbor, trie + " PlutusData CBOR, key " + key);
            }
        }
    }
}
