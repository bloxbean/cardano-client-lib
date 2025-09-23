package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JmtEncodingTest {

  @Test
  void internalNodeRoundTripsWithOptionalHp() {
    byte[][] children = {
        Blake2b256.digest("child0".getBytes()),
        Blake2b256.digest("child1".getBytes()),
        Blake2b256.digest("child3".getBytes())
    };
    byte[] hp = new byte[] {0x10, 0x20};
    int bitmap = (1 << 0) | (1 << 1) | (1 << 3);
    JmtInternalNode node = JmtInternalNode.of(bitmap, children, hp);

    byte[] encoded = node.encode();
    JmtNode decoded = JmtEncoding.decode(encoded);
    assertTrue(decoded instanceof JmtInternalNode);
    JmtInternalNode actual = (JmtInternalNode) decoded;
    assertEquals(bitmap, actual.bitmap());
    assertArrayEquals(children[0], actual.childHashes()[0]);
    assertArrayEquals(children[1], actual.childHashes()[1]);
    assertArrayEquals(children[2], actual.childHashes()[2]);
    assertArrayEquals(hp, actual.compressedPath());
  }

  @Test
  void leafNodeRoundTrips() {
    byte[] keyHash = Blake2b256.digest("key".getBytes());
    byte[] valueHash = Blake2b256.digest("value".getBytes());
    JmtLeafNode node = JmtLeafNode.of(keyHash, valueHash);

    byte[] encoded = node.encode();
    JmtNode decoded = JmtEncoding.decode(encoded);
    assertTrue(decoded instanceof JmtLeafNode);
    JmtLeafNode actual = (JmtLeafNode) decoded;
    assertArrayEquals(keyHash, actual.keyHash());
    assertArrayEquals(valueHash, actual.valueHash());
  }

  @Test
  void extensionNodeRoundTrips() {
    byte[] hp = new byte[] {0x21, 0x43};
    byte[] childHash = Blake2b256.digest("child".getBytes());
    JmtExtensionNode node = JmtExtensionNode.of(hp, childHash);

    byte[] encoded = node.encode();
    JmtNode decoded = JmtEncoding.decode(encoded);
    assertTrue(decoded instanceof JmtExtensionNode);
    JmtExtensionNode actual = (JmtExtensionNode) decoded;
    assertArrayEquals(hp, actual.hpBytes());
    assertArrayEquals(childHash, actual.childHash());
  }

  @Test
  void decodingRejectsUnknownTag() {
    byte[] bogus = new byte[] {(byte) 0x82, 0x41, 0x05, 0x41, 0x00}; // [tag=?, ...]
    assertThrows(RuntimeException.class, () -> JmtEncoding.decode(bogus));
  }
}

