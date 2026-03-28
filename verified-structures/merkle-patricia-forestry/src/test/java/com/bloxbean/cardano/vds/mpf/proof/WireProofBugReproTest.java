package com.bloxbean.cardano.vds.mpf.proof;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the WireProof.branchFromSparse ForkStep bug.
 *
 * <p>When a trie has 3+ entries where two share a prefix (producing an extension node
 * between branches), the proof generation previously folded extension nibbles into the
 * preceding BranchStep's skip. The verifier interprets skip as nibbles BEFORE the branch,
 * so folding nibbles that come AFTER the branch shifted the nibble index, selecting the
 * wrong branch slot during verification.</p>
 *
 * <p>Reproducer: keys "AAAA", "AAAB", "DDkDFDsDs" produce hashed paths where two
 * share prefix "32" (creating Extension→Branch), causing proof verification failure.</p>
 */
class WireProofBugReproTest {

    private final HashFunction hashFn = Blake2b256::digest;

    @Test
    void multiEntryInclusionProof_withExtensionBetweenBranches() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        byte[] k1 = "AAAA".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "AAAB".getBytes(StandardCharsets.UTF_8);
        byte[] k3 = "DDkDFDsDs".getBytes(StandardCharsets.UTF_8);

        trie.put(k1, "v1".getBytes(StandardCharsets.UTF_8));
        trie.put(k2, "v2".getBytes(StandardCharsets.UTF_8));
        trie.put(k3, "v3".getBytes(StandardCharsets.UTF_8));

        byte[] root = trie.getRootHash();
        assertNotNull(root);

        // All three keys must have verifiable inclusion proofs
        for (byte[][] kv : new byte[][][] {
                {k1, "v1".getBytes(StandardCharsets.UTF_8)},
                {k2, "v2".getBytes(StandardCharsets.UTF_8)},
                {k3, "v3".getBytes(StandardCharsets.UTF_8)}
        }) {
            byte[] key = kv[0];
            byte[] value = kv[1];

            assertArrayEquals(value, trie.get(key));

            Optional<byte[]> wire = trie.getProofWire(key);
            assertTrue(wire.isPresent());
            assertTrue(trie.verifyProofWire(root, key, value, true, wire.get()),
                    "inclusion proof must verify for key: " + new String(key));
        }
    }

    @Test
    void twoKeyProof_noExtension_stillWorks() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        byte[] k1 = "AAAA".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "AAAB".getBytes(StandardCharsets.UTF_8);

        trie.put(k1, "v1".getBytes(StandardCharsets.UTF_8));
        trie.put(k2, "v2".getBytes(StandardCharsets.UTF_8));

        byte[] root = trie.getRootHash();
        assertNotNull(root);

        Optional<byte[]> wire1 = trie.getProofWire(k1);
        assertTrue(wire1.isPresent());
        assertTrue(trie.verifyProofWire(root, k1, "v1".getBytes(StandardCharsets.UTF_8), true, wire1.get()));

        Optional<byte[]> wire2 = trie.getProofWire(k2);
        assertTrue(wire2.isPresent());
        assertTrue(trie.verifyProofWire(root, k2, "v2".getBytes(StandardCharsets.UTF_8), true, wire2.get()));
    }

    @Test
    void fourKeyProof_deeperExtensions() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        // Create a scenario with multiple extension levels
        String[] keys = {"alpha", "beta", "gamma", "delta", "epsilon"};
        for (String k : keys) {
            trie.put(k.getBytes(StandardCharsets.UTF_8), ("val-" + k).getBytes(StandardCharsets.UTF_8));
        }

        byte[] root = trie.getRootHash();
        assertNotNull(root);

        for (String k : keys) {
            byte[] key = k.getBytes(StandardCharsets.UTF_8);
            byte[] value = ("val-" + k).getBytes(StandardCharsets.UTF_8);

            Optional<byte[]> wire = trie.getProofWire(key);
            assertTrue(wire.isPresent(), "proof must exist for key: " + k);
            assertTrue(trie.verifyProofWire(root, key, value, true, wire.get()),
                    "inclusion proof must verify for key: " + k);
        }
    }
}
