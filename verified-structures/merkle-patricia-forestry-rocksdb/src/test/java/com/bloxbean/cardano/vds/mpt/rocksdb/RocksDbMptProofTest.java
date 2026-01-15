package com.bloxbean.cardano.vds.mpt.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RocksDbMptProofTest {

    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void inclusionProof_survivesReopen() throws Exception {
        Path dir = Files.createTempDirectory("rocks-mpt-proof");

        try {
            byte[] key = hex("aa00");
            byte[] value = b("persisted");

            byte[] root;

            // Phase 1: write state
            try (RocksDbNodeStore store = new RocksDbNodeStore(dir.toString())) {
                com.bloxbean.cardano.vds.mpt.MpfTrie trie = new com.bloxbean.cardano.vds.mpt.MpfTrie(store);
                trie.put(key, value);
                root = trie.getRootHash();

                byte[] wire = trie.getProofWire(key).orElseThrow();
                assertThat(trie.verifyProofWire(root, key, value, true, wire)).isTrue();
            }

            // Phase 2: reopen and verify proof again
            try (RocksDbNodeStore store = new RocksDbNodeStore(dir.toString())) {
                com.bloxbean.cardano.vds.mpt.MpfTrie trie = new com.bloxbean.cardano.vds.mpt.MpfTrie(store, root);
                byte[] wire = trie.getProofWire(key).orElseThrow();
                assertThat(trie.verifyProofWire(root, key, value, true, wire)).isTrue();
            }
        } catch (UnsatisfiedLinkError e) {
            Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
        } finally {
            try {
                Files.walk(dir).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
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

    private static byte[] b(String s) {
        return s.getBytes();
    }
}
