package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.mode.JmtModes;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JmtMpfProofMultiStepTest {

    @Test
    void inclusion_multi_step_paths_verify() {
        HashFunction hash = Blake2b256::digest;
        JellyfishMerkleTree tree = new JellyfishMerkleTree(JmtModes.mpf(hash), hash);

        tree.commit(1, Map.of(b("mango"), b("100")));
        tree.commit(2, Map.of(b("apple"), b("200")));
        tree.commit(3, Map.of(b("orange"), b("300")));

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(b("mango"), b("200"));
        updates.put(b("apple"), b("900"));
        tree.commit(4, updates);

        byte[] root = tree.rootHash(4);
        System.out.println("Root at v4: " + com.bloxbean.cardano.statetrees.common.util.Bytes.toHex(root));

        // apple -> 900 (multi-step path)
        byte[] k1 = b("apple");
        byte[] v1 = b("900");
        System.out.println("Apple key: " + new String(k1));
        System.out.println("Apple key hash: " + com.bloxbean.cardano.statetrees.common.util.Bytes.toHex(hash.digest(k1)));
        System.out.println("Apple value: " + new String(v1));

        JmtProof proofObj = tree.getProof(k1, 4L).orElseThrow();
        System.out.println("Apple proof steps: " + proofObj.steps().size());
        for (int i = 0; i < proofObj.steps().size(); i++) {
            JmtProof.BranchStep step = proofObj.steps().get(i);
            System.out.println("  Step " + i + ": prefix=" + step.prefix() + ", childIndex=" + step.childIndex());
        }

        byte[] p1 = tree.getProofWire(k1, 4L).orElseThrow();
        System.out.println("suffix=" + proofObj.suffix());
        System.out.println("steps size=" + proofObj.steps().size());

        assertTrue(tree.verifyProofWire(root, k1, v1, true, p1), "apple inclusion should verify at v4");
    }

    @Test
    void inclusion_multi_step_paths_verify_store() {
        JmtStore backend = new InMemoryJmtStore();
        HashFunction hash = Blake2b256::digest;
        JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, JmtModes.mpf(hash), hash);

        tree.commit(1, Map.of(b("mango"), b("100")));
        tree.commit(2, Map.of(b("apple"), b("200")));
        tree.commit(3, Map.of(b("orange"), b("300")));

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(b("mango"), b("200"));
        updates.put(b("apple"), b("900"));
        tree.commit(4, updates);

        byte[] root = tree.rootHash(4);
        System.out.println("Root at v4: " + com.bloxbean.cardano.statetrees.common.util.Bytes.toHex(root));

        // apple -> 900 (multi-step path)
        byte[] k1 = b("apple");
        byte[] v1 = b("900");
        System.out.println("Apple key: " + new String(k1));
        System.out.println("Apple key hash: " + com.bloxbean.cardano.statetrees.common.util.Bytes.toHex(hash.digest(k1)));
        System.out.println("Apple value: " + new String(v1));

        JmtProof proofObj = tree.getProof(k1, 4L).orElseThrow();
        System.out.println("Apple proof steps: " + proofObj.steps().size());
        for (int i = 0; i < proofObj.steps().size(); i++) {
            JmtProof.BranchStep step = proofObj.steps().get(i);
            System.out.println("  Step " + i + ": prefix=" + step.prefix() + ", childIndex=" + step.childIndex());
        }

        byte[] p1 = tree.getProofWire(k1, 4L).orElseThrow();
        System.out.println("suffix=" + proofObj.suffix());
        System.out.println("steps size=" + proofObj.steps().size());
        // No additional debug logging here

        assertTrue(tree.verifyProofWire(root, k1, v1, true, p1), "apple inclusion should verify at v4");
    }

    private static byte[] b(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
