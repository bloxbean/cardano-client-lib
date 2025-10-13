package com.bloxbean.cardano.vds.jmt.commitment;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.util.Bytes;

import java.util.Arrays;

/**
 * Classic JMT commitments using simple, fixed preimages with node-type tags.
 * <p>
 * Preimages:
 * - Leaf:     H( 0x00 || suffixNibbles || valueHash )
 * - Internal: H( 0x01 || bitmap(2B, BE) || child[0] || ... || child[15] )  (NULL for absent)
 * <p>
 * Extension nodes (if any) are handled by the Classic proof codec during verification.
 */
public final class ClassicJmtCommitmentScheme implements CommitmentScheme {

    private static final byte TAG_LEAF = 0x00;
    private static final byte TAG_INTERNAL = 0x01;

    private final HashFunction hashFn;
    private final int digestLength;
    private final byte[] nullHash;

    public ClassicJmtCommitmentScheme(HashFunction hashFn) {
        this.hashFn = hashFn;
        byte[] probe = hashFn.digest(new byte[0]);
        this.digestLength = probe.length;
        this.nullHash = new byte[digestLength];
    }

    @Override
    public byte[] commitBranch(NibblePath prefixIgnored, byte[][] childHashes) {
        if (childHashes.length != 16) {
            throw new IllegalArgumentException("Branch must have 16 child slots");
        }

        int bitmap = 0;
        byte[][] full = new byte[16][];
        for (int i = 0; i < 16; i++) {
            byte[] child = childHashes[i];
            if (child != null && child.length != digestLength) {
                throw new IllegalArgumentException("Child hash must be digest-sized at index " + i);
            }
            if (child != null) {
                bitmap |= (1 << i);
                full[i] = Arrays.copyOf(child, child.length);
            } else {
                full[i] = Arrays.copyOf(nullHash, nullHash.length);
            }
        }

        byte[] bitmap2b = new byte[]{(byte) ((bitmap >>> 8) & 0xFF), (byte) (bitmap & 0xFF)};

        // Concatenate all children into a fixed preimage
        byte[] childrenConcat = new byte[16 * digestLength];
        for (int i = 0; i < 16; i++) {
            System.arraycopy(full[i], 0, childrenConcat, i * digestLength, digestLength);
        }

        return hashFn.digest(Bytes.concat(new byte[]{TAG_INTERNAL}, bitmap2b, childrenConcat));
    }

    @Override
    public byte[] commitLeaf(NibblePath suffix, byte[] valueHash) {
        if (valueHash == null || valueHash.length != digestLength) {
            throw new IllegalArgumentException("valueHash must be digest-sized");
        }
        // Zero-allocation access using get() instead of getNibbles()
        int suffixLen = suffix.length();
        byte[] suffixBytes = new byte[suffixLen];
        for (int i = 0; i < suffixLen; i++) {
            suffixBytes[i] = (byte) (suffix.get(i) & 0x0F);
        }
        return hashFn.digest(Bytes.concat(new byte[]{TAG_LEAF}, suffixBytes, Arrays.copyOf(valueHash, valueHash.length)));
    }

    @Override
    public byte[] nullHash() {
        return Arrays.copyOf(nullHash, nullHash.length);
    }
}

