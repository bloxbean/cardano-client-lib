package com.bloxbean.cardano.vds.mpt.commitment;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.MerklePatriciaTrie;
import com.bloxbean.cardano.vds.mpt.test.TestNodeStore;
import com.bloxbean.cardano.vds.mpt.mpf.MpfProofVerifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ClassicMptCommitmentSchemeTest {

    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void classicVsMpf_rootDiffers_whenBranchValuePresent() {
        NodeStore store = new TestNodeStore();
        MerklePatriciaTrie mpfTrie = new MerklePatriciaTrie(store, HF, new MpfCommitmentScheme(HF));
        MerklePatriciaTrie classicTrie = new MerklePatriciaTrie(store, HF, new ClassicMptCommitmentScheme(HF));

        byte[] kPrefix = hex("aa");          // key ends at branch (value at branch slot)
        byte[] kLonger = hex("aa01");        // extends the prefix
        byte[] vPrefix = b("P");
        byte[] vLonger = b("L");

        mpfTrie.put(kPrefix, vPrefix);
        mpfTrie.put(kLonger, vLonger);

        classicTrie.put(kPrefix, vPrefix);
        classicTrie.put(kLonger, vLonger);

        assertArrayEquals(vPrefix, mpfTrie.get(kPrefix));
        assertArrayEquals(vLonger, mpfTrie.get(kLonger));
        assertArrayEquals(vPrefix, classicTrie.get(kPrefix));
        assertArrayEquals(vLonger, classicTrie.get(kLonger));

        byte[] mpfRoot = mpfTrie.getRootHash();
        byte[] classicRoot = classicTrie.getRootHash();
        assertNotNull(mpfRoot);
        assertNotNull(classicRoot);
        assertFalse(Arrays.equals(mpfRoot, classicRoot), "Roots should differ across schemes when branch value is present");
    }

    // Intentionally not asserting cross-mode proof verification because MPF proofs operate
    // on the hashed-key space (SecureTrie). The classic-vs-mpf distinction is exercised by
    // the root difference test above in the presence of a branch-terminal value.

    private static byte[] hex(String hex) {
        String s = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (s.length() % 2 != 0) s = "0" + s;
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static byte[] b(String s) {
        return s.getBytes();
    }
}
