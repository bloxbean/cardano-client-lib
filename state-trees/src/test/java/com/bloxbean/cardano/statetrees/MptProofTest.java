package com.bloxbean.cardano.statetrees;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaProof;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaVerifier;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MptProofTest {

  private final HashFunction hashFn = Blake2b256::digest;

  @Test
  void inclusionProof_leafNode() {
    MerklePatriciaTrie trie = new MerklePatriciaTrie(new TestNodeStore(), hashFn);
    byte[] key = hex("aa00");
    byte[] other = hex("ab00");
    byte[] value = b("value-1");
    byte[] otherValue = b("value-2");

    trie.put(key, value);
    trie.put(other, otherValue);

    MerklePatriciaProof proof = trie.getProof(key);
    assertEquals(MerklePatriciaProof.Type.INCLUSION, proof.getType());
    assertArrayEquals(value, proof.getValue());
    assertTrue(MerklePatriciaVerifier.verifyInclusion(trie.getRootHash(), hashFn, key, value, proof.getNodes()));
    assertFalse(MerklePatriciaVerifier.verifyInclusion(trie.getRootHash(), hashFn, key, b("wrong"), proof.getNodes()));
  }

  @Test
  void inclusionProof_branchValue() {
    MerklePatriciaTrie trie = new MerklePatriciaTrie(new TestNodeStore(), hashFn);
    byte[] prefix = hex("aa");
    byte[] longer = hex("aa01");
    byte[] prefixValue = b("prefix");
    byte[] longerValue = b("longer");

    trie.put(prefix, prefixValue);
    trie.put(longer, longerValue);

    MerklePatriciaProof proof = trie.getProof(prefix);
    assertEquals(MerklePatriciaProof.Type.INCLUSION, proof.getType());
    assertArrayEquals(prefixValue, proof.getValue());
    assertTrue(MerklePatriciaVerifier.verifyInclusion(trie.getRootHash(), hashFn, prefix, prefixValue, proof.getNodes()));
  }

  @Test
  void nonInclusion_missingBranch() {
    MerklePatriciaTrie trie = new MerklePatriciaTrie(new TestNodeStore(), hashFn);
    byte[] k1 = hex("aa00");
    byte[] k2 = hex("aa02");
    trie.put(k1, b("one"));
    trie.put(k2, b("two"));

    byte[] target = hex("aa01");
    MerklePatriciaProof proof = trie.getProof(target);
    assertEquals(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, proof.getType());
    assertTrue(MerklePatriciaVerifier.verifyNonInclusion(trie.getRootHash(), hashFn, target, proof.getNodes()));
  }

  @Test
  void nonInclusion_conflictingLeaf() {
    MerklePatriciaTrie trie = new MerklePatriciaTrie(new TestNodeStore(), hashFn);
    byte[] stored = hex("aabbcc");
    byte[] query = hex("aabbd0");
    trie.put(stored, b("payload"));

    MerklePatriciaProof proof = trie.getProof(query);
    assertEquals(MerklePatriciaProof.Type.NON_INCLUSION_DIFFERENT_LEAF, proof.getType());
    assertTrue(MerklePatriciaVerifier.verifyNonInclusion(trie.getRootHash(), hashFn, query, proof.getNodes()));
  }

  @Test
  void nonInclusion_emptyTrie() {
    MerklePatriciaTrie trie = new MerklePatriciaTrie(new TestNodeStore(), hashFn);
    byte[] query = hex("aa");
    MerklePatriciaProof proof = trie.getProof(query);
    assertEquals(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, proof.getType());
    assertTrue(MerklePatriciaVerifier.verifyNonInclusion(trie.getRootHash(), hashFn, query, proof.getNodes()));
  }

  @Test
  void tamperedProofFailsVerification() {
    MerklePatriciaTrie trie = new MerklePatriciaTrie(new TestNodeStore(), hashFn);
    byte[] key = hex("aa10");
    byte[] value = b("X");
    trie.put(key, value);
    MerklePatriciaProof proof = trie.getProof(key);

    List<byte[]> corruptedNodes = new ArrayList<>(proof.getNodes());
    byte[] tampered = corruptedNodes.get(0).clone();
    tampered[0] ^= 0x01;
    corruptedNodes.set(0, tampered);

    assertFalse(MerklePatriciaVerifier.verifyInclusion(trie.getRootHash(), hashFn, key, value, corruptedNodes));
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
