package com.bloxbean.cardano.statetrees;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

public class TestRunner {
    private static final HashFunction HF = Blake2b256::digest;

    public static void main(String[] args) {
        try {
            testPutGetRoundtrip();
            System.out.println("All tests passed!");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testPutGetRoundtrip() {
        TestNodeStore store = new TestNodeStore();
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF);

        byte[] k1 = hex("0a0b0c");
        byte[] k2 = hex("0a0b0d");
        byte[] k3 = hex("ffff");

        System.out.println("Putting k1 -> A");
        trie.put(k1, b("A"));
        byte[] r1 = trie.getRootHash();
        System.out.println("Root after k1: " + bytesToStr(r1));
        
        byte[] val1 = trie.get(k1);
        System.out.println("Get k1: " + (val1 == null ? "null" : new String(val1)));
        assertArrayEquals(b("A"), val1);

        System.out.println("\nPutting k2 -> B");
        trie.put(k2, b("B"));
        byte[] r2 = trie.getRootHash();
        System.out.println("Root after k2: " + bytesToStr(r2));
        
        System.out.println("Get k1 after k2 put: " + (trie.get(k1) == null ? "null" : new String(trie.get(k1))));
        System.out.println("Get k2 after k2 put: " + (trie.get(k2) == null ? "null" : new String(trie.get(k2))));
        
        assertArrayEquals(b("A"), trie.get(k1));
        assertArrayEquals(b("B"), trie.get(k2));

        System.out.println("\nPutting k3 -> C");
        trie.put(k3, b("C"));
        System.out.println("Get k3: " + (trie.get(k3) == null ? "null" : new String(trie.get(k3))));
        assertArrayEquals(b("C"), trie.get(k3));
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (expected == null && actual == null) return;
        if (expected == null || actual == null) {
            throw new AssertionError("Arrays differ: expected=" + 
                (expected == null ? "null" : new String(expected)) + 
                ", actual=" + (actual == null ? "null" : new String(actual)));
        }
        if (expected.length != actual.length) {
            throw new AssertionError("Array lengths differ");
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("Arrays differ at index " + i);
            }
        }
    }

    private static byte[] hex(String h) {
        String s = h.startsWith("0x") ? h.substring(2) : h;
        int len = s.length();
        byte[] out = new byte[len/2];
        for (int i=0;i<out.length;i++) {
            int hi = Character.digit(s.charAt(2*i), 16);
            int lo = Character.digit(s.charAt(2*i+1), 16);
            out[i] = (byte)((hi<<4)|lo);
        }
        return out;
    }

    private static String bytesToStr(byte[] b) { 
        if (b==null) return "null"; 
        StringBuilder sb=new StringBuilder(); 
        for(byte x: b) sb.append(String.format("%02x", x)); 
        return sb.toString(); 
    }
    
    private static byte[] b(String s) { return s.getBytes(); }
}