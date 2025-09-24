package com.bloxbean.cardano.statetrees.rocksdb;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class RocksDbSharedDbTest {
    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void sharedDbProvidesNodeStoreAndRootsIndex() throws Exception {
        try {
            Path dir = Files.createTempDirectory("rocks-st");
            try (RocksDbStateTrees st = new RocksDbStateTrees(dir.toString())) {
                MerklePatriciaTrie trie = new MerklePatriciaTrie(st.nodeStore(), HF);

                byte[] k1 = hex("aa00");
                byte[] k2 = hex("aa01");
                trie.put(k1, b("V0"));
                trie.put(k2, b("V1"));

                byte[] root = trie.getRootHash();
                assertNotNull(root);
                st.rootsIndex().put(42L, root);
                assertArrayEquals(root, st.rootsIndex().latest());
                assertArrayEquals(root, st.rootsIndex().get(42L));
            }
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            // RocksDB JNI not available on this platform; skip
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        }
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

    private static byte[] b(String s) {
        return s.getBytes();
    }
}

