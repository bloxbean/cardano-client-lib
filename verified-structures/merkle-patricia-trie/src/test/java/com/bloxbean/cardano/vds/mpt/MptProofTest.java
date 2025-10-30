package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpt.mpf.MpfProofVerifier;
import com.bloxbean.cardano.vds.mpt.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MptProofTest {

    private final HashFunction hashFn = Blake2b256::digest;

    @Test
    void inclusionProof_leafNode() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        byte[] key = hex("aa00");
        byte[] other = hex("ab00");
        byte[] value = b("value-1");
        byte[] otherValue = b("value-2");

        trie.put(key, value);
        trie.put(other, otherValue);

        byte[] wire = trie.getProofWire(key).orElseThrow();
        assertTrue(trie.verifyProofWire(trie.getRootHash(), key, value, true, wire));
        assertFalse(trie.verifyProofWire(trie.getRootHash(), key, b("wrong"), true, wire));
    }

    @Test
    void inclusionProof_branchValue() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        byte[] prefix = hex("aa");
        byte[] longer = hex("aa01");
        byte[] prefixValue = b("prefix");
        byte[] longerValue = b("longer");

        trie.put(prefix, prefixValue);
        trie.put(longer, longerValue);

        byte[] wire = trie.getProofWire(prefix).orElseThrow();
        assertTrue(trie.verifyProofWire(trie.getRootHash(), prefix, prefixValue, true, wire));
    }

    @Test
    void nonInclusion_missingBranch() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        byte[] k1 = hex("aa00");
        byte[] k2 = hex("aa02");
        trie.put(k1, b("one"));
        trie.put(k2, b("two"));

        byte[] target = hex("aa01");
        byte[] wire = trie.getProofWire(target).orElseThrow();
        assertTrue(trie.verifyProofWire(trie.getRootHash(), target, null, false, wire));
    }

    @Test
    void nonInclusion_conflictingLeaf() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        byte[] stored = hex("aabbcc");
        byte[] query = hex("aabbd0");
        trie.put(stored, b("payload"));

        byte[] wire = trie.getProofWire(query).orElseThrow();
        assertTrue(trie.verifyProofWire(trie.getRootHash(), query, null, false, wire));
    }

    @Test
    void nonInclusion_emptyTrie() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        byte[] query = hex("aa");
        byte[] wire = trie.getProofWire(query).orElseThrow();
        assertTrue(trie.verifyProofWire(trie.getRootHash(), query, null, false, wire));
    }

    @Test
    void tamperedProofFailsVerification() {
        SecureTrie trie = new SecureTrie(new TestNodeStore(), hashFn);
        byte[] key = hex("aa10");
        byte[] value = b("X");
        trie.put(key, value);
        byte[] wire = trie.getProofWire(key).orElseThrow();
        assertTrue(MpfProofVerifier.verify(trie.getRootHash(), key, value, true, wire, hashFn, new MpfCommitmentScheme(hashFn)));
        byte[] tampered = wire.clone();
        tampered[0] ^= 0x01;

        assertTrue(trie.verifyProofWire(trie.getRootHash(), key, value, true, wire));
        assertThrows(IllegalArgumentException.class,
                () -> MpfProofVerifier.verify(trie.getRootHash(), key, value, true, tampered, hashFn, new MpfCommitmentScheme(hashFn)));
    }

    private static byte[] hex(String hex) {
        String s = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (s.length() % 2 != 0) {
            s = "0" + s;
        }
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static byte[] b(String input) {
        return input.getBytes();
    }
}
