package com.bloxbean.cardano.statetrees;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.mode.JmtModes;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.mpt.mpf.MpfProofFormatter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompatibilityTest {

    @Test
    void generateProof() {
        HashFunction hash = Blake2b256::digest; // our default
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hash); // or constructor used in your code
        trie.put("mango".getBytes(), "100".getBytes());
        trie.put("apple".getBytes(), "200".getBytes());
        trie.put("orange".getBytes(), "300".getBytes());
        byte[] root = trie.getRootHash();
        byte[] key = "mango".getBytes();
        byte[] value = "100".getBytes();
        byte[] proof = trie.getProofWire(key).orElseThrow();

        System.out.println("ROOT_HEX=" + encodeHexString(root));
        System.out.println("KEY_HEX="  + encodeHexString(key));
        System.out.println("VAL_HEX="  + encodeHexString(value));
        System.out.println("PROOF_HEX="+ encodeHexString(proof));

        boolean verify = trie.verifyProofWire(root, key, "100".getBytes(), true, proof);
        System.out.println("VERIFY=" + verify);

        String json = MpfProofFormatter.toJson(proof);
        System.out.println("JSON=" + json);

        String aiken = MpfProofFormatter.toAiken(proof);
        System.out.println("AIKEN=" + aiken);
        assertTrue(verify);
    }

    @Test
    void generateProofJmt() {
        HashFunction hash = Blake2b256::digest; // our default
        JellyfishMerkleTree tree = new JellyfishMerkleTree(JmtModes.mpf(hash), hash);

        tree.commit(1, Map.of("mango".getBytes(), "100".getBytes()));
        tree.commit(2, Map.of("apple".getBytes(), "200".getBytes()));
        tree.commit(3, Map.of("orange".getBytes(), "300".getBytes()));
        tree.commit(4, Map.of("mango".getBytes(), "200".getBytes()));

        byte[] root = tree.rootHash(4);

        byte[] key = "mango".getBytes();
        byte[] value = "200".getBytes();

        byte[] proof = tree.getProofWire(key, 4).orElseThrow();

        System.out.println("ROOT_HEX=" + encodeHexString(root));
        System.out.println("KEY_HEX="  + encodeHexString(key));
        System.out.println("VAL_HEX="  + encodeHexString(value));
        System.out.println("PROOF_HEX="+ encodeHexString(proof));

        boolean verify = tree.verifyProofWire(root, key, value, true, proof);
        System.out.println("VERIFY=" + verify);

        String json = MpfProofFormatter.toJson(proof);
        System.out.println("JSON=" + json);

        String aiken = MpfProofFormatter.toAiken(proof);
        System.out.println("AIKEN=" + aiken);
        assertTrue(verify);

    }
}
