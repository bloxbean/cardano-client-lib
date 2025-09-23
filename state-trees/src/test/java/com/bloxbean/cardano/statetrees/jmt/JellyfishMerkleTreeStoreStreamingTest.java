package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JellyfishMerkleTreeStoreStreamingTest {

  private static final HashFunction HASH = Blake2b256::digest;
  private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);

  @Test
  void parityWithReferenceEngine() {
    InMemoryJmtStore backend = new InMemoryJmtStore();
    JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH,
        JellyfishMerkleTreeStore.EngineMode.STREAMING);
    JellyfishMerkleTree expected = new JellyfishMerkleTree(COMMITMENTS, HASH);

    Map<byte[], byte[]> updates = new LinkedHashMap<>();
    updates.put(bytes("alice"), bytes("100"));
    updates.put(bytes("bob"), bytes("200"));
    JellyfishMerkleTree.CommitResult r1 = tree.commit(1, updates);
    expected.commit(1, updates);
    String expectedHex = com.bloxbean.cardano.statetrees.common.util.Bytes.toHex(expected.rootHash(1));
    String actualHex = com.bloxbean.cardano.statetrees.common.util.Bytes.toHex(r1.rootHash());
    assertEquals(expectedHex, actualHex);
    assertArrayEquals(expected.rootHash(1), tree.rootHash(1));

    // collision insertion under same prefix path
    Map<byte[], byte[]> col = new LinkedHashMap<>();
    col.put(bytes("alex"), bytes("175"));
    tree.commit(2, col);
    expected.commit(2, col);
    assertArrayEquals(bytes("175"), tree.get(bytes("alex")));
    assertArrayEquals(expected.rootHash(2), tree.rootHash(2));

    // Compare proofs against reference for each key at version 2
    assertProofParity(expected, tree, bytes("alice"), 2);
    assertProofParity(expected, tree, bytes("alex"), 2);
    assertProofParity(expected, tree, bytes("carol"), 2);

    Optional<JmtProof> inc = tree.getProof(bytes("alice"), 1);
    assertTrue(inc.isPresent());
    assertTrue(JmtProofVerifier.verify(tree.rootHash(1), bytes("alice"), bytes("100"), inc.get(), HASH, COMMITMENTS));

    Map<byte[], byte[]> del = new LinkedHashMap<>();
    del.put(bytes("bob"), null);
    tree.commit(3, del);
    expected.commit(3, del);
    assertNull(tree.get(bytes("bob")));
    // stale marker should be present in backend
    assertFalse(backend.staleNodesUpTo(3).isEmpty());
    assertArrayEquals(expected.rootHash(3), tree.rootHash(3));
    assertProofParity(expected, tree, bytes("bob"), 3);
  }

  private static byte[] bytes(String v) { return v.getBytes(StandardCharsets.UTF_8); }

  private static void assertProofParity(JellyfishMerkleTree reference,
                                        JellyfishMerkleTreeStore streaming,
                                        byte[] key,
                                        long version) {
    Optional<JmtProof> refProof = reference.getProof(key, version);
    Optional<JmtProof> strProof = streaming.getProof(key, version);
    assertEquals(refProof.isPresent(), strProof.isPresent(), "proof presence mismatch for key" + new String(key));
    if (refProof.isPresent()) {
      assertEquals(refProof.get().type(), strProof.get().type());
      // For inclusion, compare leaf/value hashes
      if (refProof.get().type() == JmtProof.ProofType.INCLUSION) {
        assertArrayEquals(refProof.get().leafKeyHash(), strProof.get().leafKeyHash());
        assertArrayEquals(refProof.get().valueHash(), strProof.get().valueHash());
      }
    }
  }
}
