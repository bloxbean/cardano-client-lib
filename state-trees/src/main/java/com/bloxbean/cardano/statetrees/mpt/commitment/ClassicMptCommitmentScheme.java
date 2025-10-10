package com.bloxbean.cardano.statetrees.mpt.commitment;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.common.util.Bytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Classic Merkle Patricia Trie commitment scheme.
 *
 * <p>This scheme preserves the branch value "slot" by incorporating a branch-terminal
 * value (when present) into the branch commitment before prefix mixing. Concretely,
 * the branch commitment is computed as a binary Merkle root over the 16 child
 * commitments plus an optional 17th leaf representing the branch value, where odd
 * layers carry the last node up. The compressed prefix (if any) is then mixed by
 * hashing {@code prefixBytes || branchRoot}. Leaf and extension commitments follow
 * the same nibble/HP conventions used by the MPF scheme for consistency.</p>
 */
public final class ClassicMptCommitmentScheme implements CommitmentScheme {

    private static final int RADIX = 16;

    private final HashFunction hashFn;
    private final int digestLength;
    private final byte[] nullHash;

    public ClassicMptCommitmentScheme(HashFunction hashFn) {
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

        List<byte[]> leaves = new ArrayList<>(RADIX + 1);
        for (int i = 0; i < RADIX; i++) {
            leaves.add(sanitizeChild(childHashes[i]));
        }
        if (valueHash != null) {
            // Represent the branch-terminal value as a leaf with empty suffix.
            byte[] valueCommit = commitLeaf(NibblePath.EMPTY, sanitizeChild(valueHash));
            leaves.add(valueCommit);
        }

        byte[] branchRoot = leaves.isEmpty() ? nullHash() : merkleRootVar(leaves);
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
            // Empty suffix marker matches MPF leaf convention.
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

    private byte[] sanitizeChild(byte[] child) {
        if (child == null) return nullHash();
        if (child.length != digestLength) {
            throw new IllegalArgumentException("child commitment must be digest-sized");
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

    private static byte[] packTail(int[] nibbles, int offset) {
        if (offset >= nibbles.length) {
            return Bytes.EMPTY;
        }
        int[] slice = Arrays.copyOfRange(nibbles, offset, nibbles.length);
        return Nibbles.fromNibbles(slice);
    }

    /**
     * Binary Merkle root for an arbitrary number of leaves. When a level has
     * an odd number of nodes, the last node is carried up unchanged.
     */
    private byte[] merkleRootVar(List<byte[]> leaves) {
        if (leaves.isEmpty()) return nullHash();
        List<byte[]> layer = new ArrayList<>(leaves);
        while (layer.size() > 1) {
            int pairs = layer.size() / 2;
            List<byte[]> next = new ArrayList<>(pairs + (layer.size() % 2));
            for (int i = 0; i < pairs; i++) {
                byte[] left = layer.get(2 * i);
                byte[] right = layer.get(2 * i + 1);
                next.add(hashFn.digest(Bytes.concat(left, right)));
            }
            if ((layer.size() & 1) == 1) {
                next.add(layer.get(layer.size() - 1));
            }
            layer = next;
        }
        return layer.get(0);
    }
}

