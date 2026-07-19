package com.bloxbean.cardano.vds.jmt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class JmtEncodingTest {

    @Test
    void internalNodeRoundTripsWithOptionalHp() {
        byte[][] children = {
                Blake2b256.digest("child0".getBytes()),
                Blake2b256.digest("child1".getBytes()),
                Blake2b256.digest("child3".getBytes())
        };
        byte[] hp = new byte[]{0x10, 0x20};
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
        byte[] hp = new byte[]{0x21, 0x43};
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
        byte[] bogus = new byte[]{(byte) 0x82, 0x41, 0x05, 0x41, 0x00}; // [tag=?, ...]
        assertThrows(RuntimeException.class, () -> JmtEncoding.decode(bogus));
    }

    @Test
    void decodingRejectsTrailingCborItem() {
        byte[] encoded = JmtLeafNode.of(Blake2b256.digest("key".getBytes()),
                Blake2b256.digest("value".getBytes())).encode();
        byte[] withTrailingArray = Arrays.copyOf(encoded, encoded.length + 1);
        withTrailingArray[withTrailingArray.length - 1] = (byte) 0x80;
        assertThrows(IllegalArgumentException.class, () -> JmtEncoding.decode(withTrailingArray));
    }

    @Test
    void decodingRejectsBitmapWiderThan16Bits() throws Exception {
        Array node = new Array();
        node.add(new ByteString(new byte[]{(byte) NodeTag.INTERNAL.tag()}));
        node.add(new UnsignedInteger(BigInteger.ONE.shiftLeft(16)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new CborEncoder(output).encode(node);
        assertThrows(IllegalArgumentException.class, () -> JmtEncoding.decode(output.toByteArray()));
    }

    @Test
    void decodingRejectsHugeDeclaredContainerBeforeObjectAllocation() {
        byte[] hugeArray = new byte[]{(byte) 0x9A, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        assertThrows(IllegalArgumentException.class, () -> JmtEncoding.decode(hugeArray));
    }

    @Test
    void decodingRejectsIndefiniteLengthAndHugeDeclaredByteString() {
        assertThrows(IllegalArgumentException.class,
                () -> JmtEncoding.decode(new byte[]{(byte) 0x9F, (byte) 0xFF}));
        assertThrows(IllegalArgumentException.class,
                () -> JmtEncoding.decode(new byte[]{(byte) 0x5A, 0x7F,
                        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
    }

    @Test
    void decodingRejectsNonCanonicalCborLengths() {
        byte[] canonical = JmtLeafNode.of(Blake2b256.digest("key".getBytes()),
                Blake2b256.digest("value".getBytes())).encode();
        byte[] nonCanonicalArray = new byte[canonical.length + 1];
        nonCanonicalArray[0] = (byte) 0x98;
        nonCanonicalArray[1] = 0x03;
        System.arraycopy(canonical, 1, nonCanonicalArray, 2, canonical.length - 1);

        assertThrows(IllegalArgumentException.class, () -> JmtEncoding.decode(nonCanonicalArray));
    }
}
