package com.bloxbean.cardano.statetrees.rocksdb;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaProof;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaVerifier;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
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
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF);
        trie.put(key, value);
        root = trie.getRootHash();

        MerklePatriciaProof proof = trie.getProof(key);
        assertThat(MerklePatriciaVerifier.verifyInclusion(root, HF, key, value, proof.getNodes())).isTrue();
      }

      // Phase 2: reopen and verify proof again
      try (RocksDbNodeStore store = new RocksDbNodeStore(dir.toString())) {
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF, root);
        MerklePatriciaProof proof = trie.getProof(key);
        assertThat(MerklePatriciaVerifier.verifyInclusion(root, HF, key, value, proof.getNodes())).isTrue();
      }
    } catch (UnsatisfiedLinkError e) {
      Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
    } finally {
      try {
        Files.walk(dir).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
          try { Files.deleteIfExists(path); } catch (Exception ignored) { }
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
