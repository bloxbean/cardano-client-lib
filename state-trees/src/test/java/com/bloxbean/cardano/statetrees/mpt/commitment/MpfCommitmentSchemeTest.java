package com.bloxbean.cardano.statetrees.mpt.commitment;

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
    void leafCommitmentMatchesReferenceOddSuffix() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        NibblePath suffix = NibblePath.of(0xA, 0xB, 0xC);
        byte[] valueHash = HASH.digest("leaf-value".getBytes());

        assertArrayEquals(referenceLeaf(suffix, valueHash), scheme.commitLeaf(suffix, valueHash));
    }

    @Test
    void leafCommitmentMatchesReferenceEvenSuffix() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        NibblePath suffix = NibblePath.of(0x1, 0x2, 0x3, 0x4);
        byte[] valueHash = HASH.digest("even".getBytes());

        assertArrayEquals(referenceLeaf(suffix, valueHash), scheme.commitLeaf(suffix, valueHash));
    }

    @Test
    void branchCommitmentMatchesReferenceWithoutValue() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        byte[][] children = new byte[16][];
        for (int i = 0; i < children.length; i++) {
            children[i] = (i & 1) == 0 ? HASH.digest(("child-" + i).getBytes()) : null;
        }

        assertArrayEquals(referenceBranch(NibblePath.EMPTY, children),
                scheme.commitBranch(NibblePath.EMPTY, children, null));
    }

    @Test
    void branchCommitmentIgnoresBranchValueInMpfMode() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        byte[][] children = new byte[16][];
        children[3] = HASH.digest("left".getBytes());
        children[7] = HASH.digest("right".getBytes());
        byte[] branchValue = HASH.digest("branch-value".getBytes());

        byte[] withValue = scheme.commitBranch(NibblePath.EMPTY, children, branchValue);
        byte[] withoutValue = scheme.commitBranch(NibblePath.EMPTY, children, null);
        assertArrayEquals(withoutValue, withValue);
    }

    @Test
    void nullHashIsDigestSizedZeros() {
        MpfCommitmentScheme scheme = new MpfCommitmentScheme(HASH);
        byte[] nullHash = scheme.nullHash();
        assertEquals(DIGEST_LENGTH, nullHash.length);
        for (byte b : nullHash) {
            assertEquals(0, b);
        }
    }

    private static byte[] referenceLeaf(NibblePath suffix, byte[] valueHash) {
        String hex = toHexDigits(suffix);
        boolean odd = (hex.length() & 1) == 1;

        byte[] head;
        String tailHex;
        if (odd) {
            int headNibble = Integer.parseInt(hex.substring(0, 1), 16);
            head = new byte[]{0x00, (byte) headNibble};
            tailHex = hex.substring(1);
        } else {
            head = new byte[]{(byte) 0xFF};
            tailHex = hex;
        }

        byte[] tail = tailHex.isEmpty() ? Bytes.EMPTY : fromHex(tailHex);
        return HASH.digest(Bytes.concat(head, tail, Arrays.copyOf(valueHash, valueHash.length)));
    }

    private static byte[] referenceBranch(NibblePath prefix, byte[][] children) {
        byte[] prefixBytes = prefix.isEmpty() ? Bytes.EMPTY : toNibbleBytes(prefix);
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

    private static byte[] merkleRoot(byte[][] children) {
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
        return level[0];
    }

    private static byte[] sanitize(byte[] child) {
        if (child == null) return new byte[DIGEST_LENGTH];
        if (child.length != DIGEST_LENGTH) {
            throw new IllegalArgumentException("child hash wrong length");
        }
        return Arrays.copyOf(child, child.length);
    }

    private static byte[] toNibbleBytes(NibblePath path) {
        int[] nibbles = path.getNibbles();
        byte[] out = new byte[nibbles.length];
        for (int i = 0; i < nibbles.length; i++) {
            out[i] = (byte) (nibbles[i] & 0xFF);
        }
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
