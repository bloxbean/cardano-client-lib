package com.bloxbean.cardano.vds.mpf.commitment;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.core.util.Bytes;

import java.util.Arrays;
import java.util.Objects;

/**
 * Merkle Patricia Forestry commitment scheme for the hexary trie.
 *
 * <p>This implementation mirrors the MPF reference (Aiken + JS) commitment
 * semantics so the resulting roots and proofs are byte-for-byte compatible with
 * the on-chain verifier. Branch commitments combine the skipped nibble prefix
 * with the sparse-merkle root of the 16 child digests. Leaf commitments follow
 * MPF's odd/even nibble encoding and operate over the hash of the stored value.
 * Extension nodes simply combine their compressed prefix with the child
 * commitment.</p>
 */
public final class MpfCommitmentScheme implements CommitmentScheme {

    private static final int RADIX = 16;

    private final HashFunction hashFn;
    private final int digestLength;
    private final byte[] nullHash;

    public MpfCommitmentScheme(HashFunction hashFn) {
        this.hashFn = Objects.requireNonNull(hashFn, "hashFn");
        byte[] probe = hashFn.digest(new byte[0]);
        this.digestLength = probe.length;
        this.nullHash = new byte[digestLength];
    }

    @Override
    public byte[] commitBranch(NibblePath prefix, byte[][] childHashes, byte[] valueHash) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(childHashes, "childHashes");
        if (childHashes.length != RADIX) {
            throw new IllegalArgumentException("branch must expose exactly 16 child slots");
        }

        byte[][] level = new byte[RADIX][];
        for (int i = 0; i < RADIX; i++) {
            level[i] = sanitizeChild(childHashes[i]);
        }

        byte[] branchRoot = merkleRoot(level);
        byte[] prefixBytes = prefix.isEmpty() ? Bytes.EMPTY : toNibbleBytes(prefix);
        return hashFn.digest(Bytes.concat(prefixBytes, branchRoot));
    }

    @Override
    public byte[] commitLeaf(NibblePath suffix, byte[] valueHash) {
        Objects.requireNonNull(suffix, "suffix");
        Objects.requireNonNull(valueHash, "valueHash");
        if (valueHash.length != digestLength) {
            throw new IllegalArgumentException("valueHash must be a digest-sized array");
        }
        int[] nibbles = suffix.getNibbles();
        if (nibbles.length == 0) {
            // Empty suffix is encoded as 0xFF per MPF spec.
            return hashFn.digest(Bytes.concat(new byte[]{(byte) 0xFF}, valueHash));
        }
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
        return hashFn.digest(Bytes.concat(head, tail, valueHash));
    }

    @Override
    public byte[] commitExtension(NibblePath path, byte[] childHash) {
        Objects.requireNonNull(path, "path");
        byte[] sanitizedChild = sanitizeChild(childHash);
        byte[] prefixBytes = path.isEmpty() ? Bytes.EMPTY : toNibbleBytes(path);
        return hashFn.digest(Bytes.concat(prefixBytes, sanitizedChild));
    }

    @Override
    public byte[] nullHash() {
        return Arrays.copyOf(nullHash, nullHash.length);
    }

    @Override
    public boolean encodesBranchValueInBranchCommitment() {
        return false;
    }

    private byte[] sanitizeChild(byte[] child) {
        if (child == null) return nullHash();
        if (child.length != digestLength) {
            throw new IllegalArgumentException("child commitment must be digest-sized");
        }
        return Arrays.copyOf(child, child.length);
    }

    private byte[] merkleRoot(byte[][] nodes) {
        if (nodes.length != RADIX) {
            throw new IllegalArgumentException("expected 16 child commitments");
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
        int[] nibbles = path.getNibbles();
        byte[] out = new byte[nibbles.length];
        for (int i = 0; i < nibbles.length; i++) {
            out[i] = (byte) (nibbles[i] & 0xFF);
        }
        return out;
    }

    private static byte[] packTail(int[] nibbles, int offset) {
        if (offset >= nibbles.length) {
            return Bytes.EMPTY;
        }
        int[] slice = Arrays.copyOfRange(nibbles, offset, nibbles.length);
        return Nibbles.fromNibbles(slice);
    }
}
