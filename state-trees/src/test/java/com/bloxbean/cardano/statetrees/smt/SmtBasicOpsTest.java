package com.bloxbean.cardano.statetrees.smt;

import com.bloxbean.cardano.statetrees.TestNodeStore;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.NodeStore;
import com.bloxbean.cardano.statetrees.api.SparseMerkleTree;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmtBasicOpsTest {

  private final HashFunction blake2b = Blake2b256::digest;

  @Test
  void putGet_singleKey() {
    NodeStore store = new TestNodeStore();
    SparseMerkleTree smt = new SparseMerkleTree(store, blake2b);

    byte[] key = "alice".getBytes();
    byte[] val = "100".getBytes();

    smt.put(key, val);
    assertThat(smt.get(key)).isEqualTo(val);
    assertThat(smt.getRootHash()).isNotNull();
  }

  @Test
  void put_overwriteSameKey() {
    NodeStore store = new TestNodeStore();
    SparseMerkleTree smt = new SparseMerkleTree(store, blake2b);

    byte[] key = "bob".getBytes();
    byte[] v1 = "1".getBytes();
    byte[] v2 = "2".getBytes();

    smt.put(key, v1);
    byte[] root1 = smt.getRootHash();
    smt.put(key, v2);
    byte[] root2 = smt.getRootHash();

    assertThat(smt.get(key)).isEqualTo(v2);
    assertThat(root2).isNotNull().isNotEqualTo(root1);
  }

  @Test
  void putGet_twoKeys_diverging() {
    NodeStore store = new TestNodeStore();
    SparseMerkleTree smt = new SparseMerkleTree(store, blake2b);

    byte[] k1 = "hello".getBytes();
    byte[] v1 = "world".getBytes();
    byte[] k2 = "help".getBytes();
    byte[] v2 = "assist".getBytes();

    smt.put(k1, v1);
    smt.put(k2, v2);

    assertThat(smt.get(k1)).isEqualTo(v1);
    assertThat(smt.get(k2)).isEqualTo(v2);
  }

  @Test
  void delete_singleKey() {
    NodeStore store = new TestNodeStore();
    SparseMerkleTree smt = new SparseMerkleTree(store, blake2b);

    byte[] k = "only".getBytes();
    byte[] v = "one".getBytes();
    smt.put(k, v);
    byte[] root1 = smt.getRootHash();

    smt.delete(k);
    assertThat(smt.get(k)).isNull();
    assertThat(smt.getRootHash()).isNull();
    assertThat(root1).isNotNull();
  }

  @Test
  void delete_oneOfTwo() {
    NodeStore store = new TestNodeStore();
    SparseMerkleTree smt = new SparseMerkleTree(store, blake2b);

    byte[] k1 = "k1".getBytes();
    byte[] v1 = "v1".getBytes();
    byte[] k2 = "k2".getBytes();
    byte[] v2 = "v2".getBytes();

    smt.put(k1, v1);
    smt.put(k2, v2);
    byte[] rootBefore = smt.getRootHash();

    smt.delete(k1);
    assertThat(smt.get(k1)).isNull();
    assertThat(smt.get(k2)).isEqualTo(v2);
    assertThat(smt.getRootHash()).isNotNull().isNotEqualTo(rootBefore);
  }
}
