package com.bloxbean.cardano.statetrees.jmt.commitment;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.common.util.Bytes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MpfCommitmentSchemeTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final int DIGEST_LENGTH = HASH.digest(new byte[0]).length;

    @Test
    void leafCommitmentMatchesReferenceForOddPrefix() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        NibblePath suffix = NibblePath.of(0xA, 0xB, 0xC);
        byte[] valueHash = HASH.digest("value".getBytes());

        assertArrayEquals(referenceLeaf(suffix, valueHash), scheme.commitLeaf(suffix, valueHash));
    }

    @Test
    void leafCommitmentMatchesReferenceForEvenPrefix() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        NibblePath suffix = NibblePath.of(0x1, 0x2, 0x3, 0x4);
        byte[] valueHash = HASH.digest("even".getBytes());

        assertArrayEquals(referenceLeaf(suffix, valueHash), scheme.commitLeaf(suffix, valueHash));
    }

    @Test
    void branchCommitmentMatchesReference() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        NibblePath prefix = NibblePath.of(0x0, 0xF);
        byte[][] children = new byte[16][];
        for (int i = 0; i < children.length; i++) {
            children[i] = (i % 3 == 0) ? null : HASH.digest(("child-" + i).getBytes());
        }

        assertArrayEquals(referenceBranch(prefix, children), scheme.commitBranch(prefix, children));
    }

    @Test
    void nullHashIsZeroDigestSized() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        byte[] nullHash = scheme.nullHash();
        assertEquals(DIGEST_LENGTH, nullHash.length);
        for (byte b : nullHash) {
            assertEquals(0, b);
        }
    }

    private static byte[] referenceLeaf(NibblePath suffix, byte[] valueHash) {
        String prefixHex = toHexDigits(suffix);
        boolean odd = (prefixHex.length() & 1) == 1;

        byte[] head;
        String tailHex;
        if (odd) {
            int headNibble = Integer.parseInt(prefixHex.substring(0, 1), 16);
            head = new byte[]{0x00, (byte) headNibble};
            tailHex = prefixHex.substring(1);
        } else {
            head = new byte[]{(byte) 0xFF};
            tailHex = prefixHex;
        }

        byte[] tail = tailHex.isEmpty() ? new byte[0] : fromHex(tailHex);
        return HASH.digest(Bytes.concat(head, tail, Arrays.copyOf(valueHash, valueHash.length)));
    }

    private static byte[] referenceBranch(NibblePath prefix, byte[][] children) {
        byte[] prefixBytes = toNibbleBytes(prefix);

        byte[][] level = new byte[children.length][];
        for (int i = 0; i < children.length; i++) {
            level[i] = sanitize(children[i]);
        }

        int size = level.length;
        while (size > 1) {
            byte[][] next = new byte[size / 2][];
            for (int i = 0; i < next.length; i++) {
                next[i] = HASH.digest(Bytes.concat(level[2 * i], level[2 * i + 1]));
            }
            level = next;
            size = level.length;
        }

        return HASH.digest(Bytes.concat(prefixBytes, level[0]));
    }

    private static byte[] sanitize(byte[] child) {
        if (child == null) return new byte[DIGEST_LENGTH];
        if (child.length != DIGEST_LENGTH) throw new IllegalArgumentException("child hash wrong length");
        return Arrays.copyOf(child, child.length);
    }

    private static byte[] toNibbleBytes(NibblePath path) {
        int[] nibbles = path.getNibbles();
        byte[] out = new byte[nibbles.length];
        for (int i = 0; i < nibbles.length; i++) out[i] = (byte) (nibbles[i] & 0xFF);
        return out;
    }

    private static String toHexDigits(NibblePath path) {
        StringBuilder sb = new StringBuilder();
        for (int nibble : path.getNibbles()) {
            sb.append(Integer.toHexString(nibble));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        if ((hex.length() & 1) == 1) {
            hex = "0" + hex;
        }
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
