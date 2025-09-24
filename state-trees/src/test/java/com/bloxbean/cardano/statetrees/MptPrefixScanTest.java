package com.bloxbean.cardano.statetrees;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie.Entry;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MptPrefixScanTest {
    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void scanPrefixCollectsMatchingKeys() {
        TestNodeStore store = new TestNodeStore();
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF);

        put(trie, "aa00", "V0");
        put(trie, "aa01", "V1");
        put(trie, "abff", "VX");
        put(trie, "bb00", "W0");

        List<Entry> aa = trie.scanByPrefix(hex("aa"), 10);
        assertEquals(2, aa.size());
        assertTrue(aa.stream().anyMatch(e -> hexStr(e.getKey()).startsWith("aa00") && s(e.getValue()).equals("V0")));
        assertTrue(aa.stream().anyMatch(e -> hexStr(e.getKey()).startsWith("aa01") && s(e.getValue()).equals("V1")));

        List<Entry> ab = trie.scanByPrefix(hex("ab"), 10);
        assertEquals(1, ab.size());
        assertEquals("abff", hexStr(ab.get(0).getKey()));
        assertEquals("VX", s(ab.get(0).getValue()));

        List<Entry> a = trie.scanByPrefix(hex("a"), 1);
        assertEquals(1, a.size()); // limited to 1
    }

    private static void put(MerklePatriciaTrie t, String k, String v) {
        t.put(hex(k), v.getBytes());
    }

    private static String s(byte[] b) {
        return new String(b);
    }

    private static byte[] hex(String h) {
        String s = h.startsWith("0x") ? h.substring(2) : h;
        if (s.length() % 2 == 1) s = "0" + s;
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String hexStr(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
