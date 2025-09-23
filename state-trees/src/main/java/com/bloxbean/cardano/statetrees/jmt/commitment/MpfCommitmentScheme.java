package com.bloxbean.cardano.statetrees.jmt.commitment;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.common.util.Bytes;

import java.util.Arrays;

/**
 * MPF-compatible commitment scheme that matches the Aiken on-chain implementation.
 */
public final class MpfCommitmentScheme implements CommitmentScheme {

    private static final int RADIX = 16;

    private final HashFunction hashFn;
    private final int digestLength;
    private final byte[] nullHash;

    public MpfCommitmentScheme(HashFunction hashFn) {
        this.hashFn = hashFn;
        byte[] probe = hashFn.digest(new byte[0]);
        this.digestLength = probe.length;
        this.nullHash = new byte[digestLength];
    }

    @Override
    public byte[] commitBranch(NibblePath prefix, byte[][] childHashes) {
        if (childHashes.length != RADIX) {
            throw new IllegalArgumentException("Branch must have 16 children");
        }
        byte[][] level = new byte[RADIX][];
        for (int i = 0; i < RADIX; i++) {
            byte[] child = childHashes[i];
            level[i] = sanitizeChild(child);
        }

        byte[] root = merkleRoot(level);
        byte[] prefixBytes = prefix.isEmpty() ? new byte[0] : toNibbleBytes(prefix);
        return hashFn.digest(Bytes.concat(prefixBytes, root));
    }

    @Override
    public byte[] commitLeaf(NibblePath suffix, byte[] valueHash) {
        if (valueHash == null || valueHash.length != digestLength) {
            throw new IllegalArgumentException("valueHash must be digest-sized");
        }
        int[] nibbles = suffix.getNibbles();
        boolean odd = (nibbles.length & 1) == 1;

        byte[] head;
        int offset;
        if (odd) {
            head = new byte[]{0x00, (byte) (nibbles[0] & 0xFF)};
            offset = 1;
        } else {
            head = new byte[]{(byte) 0xFF};
            offset = 0;
        }

        byte[] tail = packTail(nibbles, offset);
        return hashFn.digest(Bytes.concat(head, tail, Arrays.copyOf(valueHash, valueHash.length)));
    }

    @Override
    public byte[] nullHash() {
        return Arrays.copyOf(nullHash, nullHash.length);
    }

    private byte[] sanitizeChild(byte[] child) {
        if (child == null) return nullHash();
        if (child.length != digestLength) {
            throw new IllegalArgumentException("child hash must be digest-sized");
        }
        return Arrays.copyOf(child, child.length);
    }

    private byte[] merkleRoot(byte[][] nodes) {
        if (nodes.length != RADIX) {
            throw new IllegalArgumentException("Expected 16 nodes for merkle root");
        }
        byte[][] current = new byte[RADIX][];
        for (int i = 0; i < RADIX; i++) {
            current[i] = sanitizeChild(nodes[i]);
        }

        int size = current.length;
        while (size > 1) {
            int parentSize = size / 2;
            byte[][] next = new byte[parentSize][];
            for (int i = 0; i < parentSize; i++) {
                byte[] left = current[2 * i];
                byte[] right = current[2 * i + 1];
                next[i] = hashFn.digest(Bytes.concat(left, right));
            }
            current = next;
            size = parentSize;
        }
        return current[0];
    }

    private static byte[] toNibbleBytes(NibblePath path) {
        int[] n = path.getNibbles();
        byte[] out = new byte[n.length];
        for (int i = 0; i < n.length; i++) out[i] = (byte) (n[i] & 0xFF);
        return out;
    }

    private static byte[] packTail(int[] nibbles, int offset) {
        if (offset >= nibbles.length) {
            return new byte[0];
        }
        int[] slice = Arrays.copyOfRange(nibbles, offset, nibbles.length);
        return Nibbles.fromNibbles(slice);
    }
}

