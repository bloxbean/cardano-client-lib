package com.bloxbean.cardano.statetrees.jmt.mpf;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.common.util.Bytes;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.JmtProofVerifier;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MpfProofSerializerTest {

  private static final HashFunction HASH = Blake2b256::digest;
  private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);

  private JellyfishMerkleTree tree;
  private byte[] version1Root;

  @BeforeEach
  void setUp() {
    tree = new JellyfishMerkleTree(COMMITMENTS, HASH);

    Map<byte[], byte[]> updates = new LinkedHashMap<>();
    updates.put(bytes("alice"), bytes("100"));
    updates.put(bytes("bob"), bytes("200"));
    updates.put(bytes("carol"), bytes("300"));

    JellyfishMerkleTree.CommitResult result = tree.commit(1, updates);
    version1Root = result.rootHash();
  }

  @Test
  void inclusionProofSerialisesToExpectedHex() {
    Optional<JmtProof> proof = tree.getProof(bytes("alice"), 1);
    assertTrue(proof.isPresent());
    byte[] cbor = MpfProofSerializer.toCbor(proof.get(), HASH, COMMITMENTS);

    String actualHex = Bytes.toHex(cbor);
    assertEquals("81d879820058804e80802838c3b4fae0948c36e2172448d5f2a67e55887c32dc8717446524941688b5d37a1a31fc4ef3da04f9c22339d4e03af5020c5bdc32b1a4a5902dbb1a7e0eb923b0cbd24df54401d998531feead35a47a99f4deed205de4af81120f97610000000000000000000000000000000000000000000000000000000000000000", actualHex);

    assertTrue(JmtProofVerifier.verify(version1Root, bytes("alice"), bytes("100"), proof.get(), HASH, COMMITMENTS));

    Optional<byte[]> apiCbor = tree.getMpfProofCbor(bytes("alice"), 1);
    assertTrue(apiCbor.isPresent());
    assertArrayEquals(cbor, apiCbor.get());

    MpfProof decoded = MpfProofDecoder.decode(cbor);
    byte[] decodedRoot = decoded.computeRoot(bytes("alice"), bytes("100"), true, HASH, COMMITMENTS);
    assertArrayEquals(version1Root, decodedRoot);
    assertTrue(MpfProofVerifier.verify(version1Root, bytes("alice"), bytes("100"), true, cbor, HASH, COMMITMENTS));
  }

  @Test
  void nonInclusionProofSerialisesToExpectedHex() {
    Optional<JmtProof> proof = tree.getProof(bytes("dave"), 1);
    assertTrue(proof.isPresent());
    byte[] cbor = MpfProofSerializer.toCbor(proof.get(), HASH, COMMITMENTS);

    String actualHex = Bytes.toHex(cbor);
    assertEquals("81d879820058804e80802838c3b4fae0948c36e2172448d5f2a67e55887c32dc871744652494161bbce9c0747aa60ff35a5b494aab8e274f4603be5483ab7421116c790158dd65a839a1739d9d1a5e863db7ac28624078213e02b474d081cd4b37b1825027aacb0000000000000000000000000000000000000000000000000000000000000000", actualHex);

    assertTrue(JmtProofVerifier.verify(version1Root, bytes("dave"), null, proof.get(), HASH, COMMITMENTS));

    Optional<byte[]> apiCbor = tree.getMpfProofCbor(bytes("dave"), 1);
    assertTrue(apiCbor.isPresent());
    assertArrayEquals(cbor, apiCbor.get());

    MpfProof decoded = MpfProofDecoder.decode(cbor);
    byte[] decodedRoot = decoded.computeRoot(bytes("dave"), null, false, HASH, COMMITMENTS);
    assertArrayEquals(version1Root, decodedRoot);
    assertTrue(MpfProofVerifier.verify(version1Root, bytes("dave"), null, false, cbor, HASH, COMMITMENTS));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
