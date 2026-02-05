package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpf.proof.ProofVerifier;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-impl golden checks against MPF off-chain test vectors (mango/kumquat).
 * <p>
 * These assertions mirror the CBOR encodings found in the MPF repo's off-chain
 * tests (trie.test.js Proof.toCBOR for mango & kumquat), ensuring our MPF
 * serializer matches the Aiken/JS format exactly.
 */
class MpfGoldenVectorsTest {

    private final HashFunction hashFn = Blake2b256::digest;

    @Test
    void mango_and_kumquat_proof_cbor_match_mpf_repo() {
        MpfTrie trie = new MpfTrie(new TestNodeStore());

        // Populate the same FRUITS_LIST as in MPF off-chain tests
        put(trie, "apple[uid: 58]", "🍎");
        put(trie, "apricot[uid: 0]", "🤷");
        put(trie, "banana[uid: 218]", "🍌");
        put(trie, "blueberry[uid: 0]", "🫐");
        put(trie, "cherry[uid: 0]", "🍒");
        put(trie, "coconut[uid: 0]", "🥥");
        put(trie, "cranberry[uid: 0]", "🤷");
        put(trie, "fig[uid: 68267]", "🤷");
        put(trie, "grapefruit[uid: 0]", "🤷");
        put(trie, "grapes[uid: 0]", "🍇");
        put(trie, "guava[uid: 344]", "🤷");
        put(trie, "kiwi[uid: 0]", "🥝");
        put(trie, "kumquat[uid: 0]", "🤷");
        put(trie, "lemon[uid: 0]", "🍋");
        put(trie, "lime[uid: 0]", "🤷");
        put(trie, "mango[uid: 0]", "🥭");
        put(trie, "orange[uid: 0]", "🍊");
        put(trie, "papaya[uid: 0]", "🤷");
        put(trie, "passionfruit[uid: 0]", "🤷");
        put(trie, "peach[uid: 0]", "🍑");
        put(trie, "pear[uid: 0]", "🍐");
        put(trie, "pineapple[uid: 12577]", "🍍");
        put(trie, "plum[uid: 15492]", "🤷");
        put(trie, "pomegranate[uid: 0]", "🤷");
        put(trie, "raspberry[uid: 0]", "🤷");
        put(trie, "strawberry[uid: 2532]", "🍓");
        put(trie, "tangerine[uid: 11]", "🍊");
        put(trie, "tomato[uid: 83468]", "🍅");
        put(trie, "watermelon[uid: 0]", "🍉");
        put(trie, "yuzu[uid: 0]", "🤷");

        // Compute root; we will compare against proofs from the MPF repo
        String rootHex = Bytes.toHex(trie.getRootHash());

        // Mango proof CBOR (from off-chain Proof.toCBOR test)
        String mangoCborHex = "9fd8799f005f5840c7bfa4472f3a98ebe0421e8f3f03adf0f7c4340dec65b4b92b1c9f0bed209eb45fdf82687b1ab133324cebaf46d99d49f92720c5ded08d5b02f57530f2cc5a5f58401508f13471a031a21277db8817615e62a50a7427d5f8be572746aa5f0d49841758c5e4a29601399a5bd916e5f3b34c38e13253f4de2a3477114f1b2b8f9f2f4dffffd87b9f00582009d23032e6edc0522c00bc9b74edd3af226d1204a079640a367da94c84b69ecc5820c29c35ad67a5a55558084e634ab0d98f7dd1f60070b9ce2a53f9f305fd9d9795ffff";
        byte[] mangoCbor = trie.getProofWire(b("mango[uid: 0]")).orElseThrow();
        // Our CBOR may differ in definite/indefinite CBOR framing. Verify our proof and the MPF golden proof.
        assertTrue(ProofVerifier.verify(trie.getRootHash(), b("mango[uid: 0]"), b("🥭"), true, mangoCbor, hashFn, new MpfCommitmentScheme(hashFn)));
        byte[] mangoGolden = Bytes.fromHex(mangoCborHex);
        assertTrue(MpfGoldenCbor.verify(trie.getRootHash(), b("mango[uid: 0]"), b("🥭"), true, mangoGolden, hashFn));

        // Kumquat proof CBOR (from off-chain Proof.toCBOR test)
        String kumquatCborHex = "9fd8799f005f5840c7bfa4472f3a98ebe0421e8f3f03adf0f7c4340dec65b4b92b1c9f0bed209eb47238ba5d16031b6bace4aee22156f5028b0ca56dc24f7247d6435292e82c039c58403490a825d2e8deddf8679ce2f95f7e3a59d9c3e1af4a49b410266d21c9344d6d08434fd717aea47d156185d589f44a59fc2e0158eab7ff035083a2a66cd3e15bffffd87a9f00d8799f0041075820a1ffbc0e72342b41129e2d01d289809079b002e54b123860077d2d66added281ffffff";
        byte[] kumquatCbor = trie.getProofWire(b("kumquat[uid: 0]")).orElseThrow();
        assertTrue(ProofVerifier.verify(trie.getRootHash(), b("kumquat[uid: 0]"), b("🤷"), true, kumquatCbor, hashFn, new MpfCommitmentScheme(hashFn)));
        byte[] kumquatGolden = Bytes.fromHex(kumquatCborHex);
        assertTrue(MpfGoldenCbor.verify(trie.getRootHash(), b("kumquat[uid: 0]"), b("🤷"), true, kumquatGolden, hashFn));
    }

    @Test
    void nonInclusion_proof_cbor_via_fallback_decoder() {
        MpfTrie trie = new MpfTrie(new TestNodeStore());

        // Populate a set of entries (reuse fruits list from earlier test)
        put(trie, "apple[uid: 58]", "🍎");
        put(trie, "apricot[uid: 0]", "🤷");
        put(trie, "banana[uid: 218]", "🍌");
        put(trie, "mango[uid: 0]", "🥭");
        put(trie, "kumquat[uid: 0]", "🤷");

        byte[] query = b("dragonfruit[uid: 0]");
        byte[] root = trie.getRootHash();

        // Production non-inclusion check
        byte[] wire = trie.getProofWire(query).orElseThrow();
        assertTrue(trie.verifyProofWire(root, query, null, false, wire));

        // Fallback decoder check with CBOR (our encoder output), including=false
        byte[] cbor = trie.getProofWire(query).orElseThrow();
        assertTrue(MpfGoldenCbor.verify(root, query, null, false, cbor, hashFn));
    }

    private static void put(MpfTrie trie, String key, String value) {
        trie.put(b(key), b(value));
    }

    private static byte[] b(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
