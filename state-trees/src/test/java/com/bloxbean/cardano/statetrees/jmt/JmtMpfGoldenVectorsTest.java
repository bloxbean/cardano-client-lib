package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.MpfGoldenCbor;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JmtMpfGoldenVectorsTest {

    private final HashFunction hashFn = Blake2b256::digest;
    private final CommitmentScheme commitments = new MpfCommitmentScheme(hashFn);

    @Test
    void mango_and_kumquat_proofs_verify_via_cbor() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(commitments, hashFn);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        put(updates, "mango[uid: 0]", "🥭");
        put(updates, "kumquat[uid: 0]", "🤷");
        tree.commit(1, updates);
        byte[] root = tree.rootHash(1);

        // Mango inclusion
        byte[] mangoKey = b("mango[uid: 0]");
        byte[] mangoVal = b("🥭");
        byte[] mangoCbor = tree.getProofWire(mangoKey, 1).orElseThrow();
        assertTrue(com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofVerifier.verify(root, mangoKey, mangoVal, true, mangoCbor, hashFn, commitments));
        assertTrue(MpfGoldenCbor.verifyJmt(root, mangoKey, mangoVal, true, mangoCbor, hashFn));

        // Kumquat inclusion (value present in this list)
        byte[] kumquatKey = b("kumquat[uid: 0]");
        byte[] kumquatVal = b("🤷");
        byte[] kumquatCbor = tree.getProofWire(kumquatKey, 1).orElseThrow();
        assertTrue(com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofVerifier.verify(root, kumquatKey, kumquatVal, true, kumquatCbor, hashFn, commitments));
        assertTrue(MpfGoldenCbor.verifyJmt(root, kumquatKey, kumquatVal, true, kumquatCbor, hashFn));
    }

    // Note: non-inclusion CBOR coverage can be added later once external fixtures are available for JMT.

    private static void put(Map<byte[], byte[]> map, String k, String v) {
        map.put(b(k), b(v));
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<byte[], byte[]> fullFruits() {
        Map<byte[], byte[]> m = new LinkedHashMap<>();
        put(m, "apple[uid: 58]", "🍎");
        put(m, "apricot[uid: 0]", "🤷");
        put(m, "banana[uid: 218]", "🍌");
        put(m, "blueberry[uid: 0]", "🫐");
        put(m, "cherry[uid: 0]", "🍒");
        put(m, "coconut[uid: 0]", "🥥");
        put(m, "cranberry[uid: 0]", "🤷");
        put(m, "fig[uid: 68267]", "🤷");
        put(m, "grapefruit[uid: 0]", "🤷");
        put(m, "grapes[uid: 0]", "🍇");
        put(m, "guava[uid: 344]", "🤷");
        put(m, "kiwi[uid: 0]", "🥝");
        put(m, "kumquat[uid: 0]", "🤷");
        put(m, "lemon[uid: 0]", "🍋");
        put(m, "lime[uid: 0]", "🤷");
        put(m, "mango[uid: 0]", "🥭");
        put(m, "orange[uid: 0]", "🍊");
        put(m, "papaya[uid: 0]", "🤷");
        put(m, "passionfruit[uid: 0]", "🤷");
        put(m, "peach[uid: 0]", "🍑");
        put(m, "pear[uid: 0]", "🍐");
        put(m, "pineapple[uid: 12577]", "🍍");
        put(m, "plum[uid: 15492]", "🤷");
        put(m, "pomegranate[uid: 0]", "🤷");
        put(m, "raspberry[uid: 0]", "🤷");
        put(m, "strawberry[uid: 2532]", "🍓");
        put(m, "tangerine[uid: 11]", "🍊");
        put(m, "tomato[uid: 83468]", "🍅");
        put(m, "watermelon[uid: 0]", "🍉");
        put(m, "yuzu[uid: 0]", "🤷");
        return m;
    }
}
