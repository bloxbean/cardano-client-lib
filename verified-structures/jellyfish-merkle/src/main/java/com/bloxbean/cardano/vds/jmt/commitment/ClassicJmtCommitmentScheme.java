package com.bloxbean.cardano.vds.jmt.commitment;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.util.Bytes;

import java.util.Arrays;

/**
 * Classic JMT commitments using simple, fixed preimages with node-type tags.
 * <p>
 * Preimages:
 * - Leaf:     H( 0x00 || keyHash || valueHash )
 * - Internal: H( 0x01 || bitmap(2B, BE) || child[0] || ... || child[15] )  (NULL for absent)
 * <p>
 * The leaf preimage uses Diem-style full-key binding. The overall radix-16 branch commitment and
 * proof format are custom and are not compatible with Diem/Aptos JMT. Committing only to a path suffix
 * would leave the leaf unbound to its key and make inclusion/non-inclusion proofs forgeable.
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
    public byte[] commitLeaf(byte[] keyHash, byte[] valueHash) {
        if (keyHash == null || keyHash.length != digestLength) {
            throw new IllegalArgumentException("keyHash must be digest-sized");
        }
        if (valueHash == null || valueHash.length != digestLength) {
            throw new IllegalArgumentException("valueHash must be digest-sized");
        }
        // H(0x00 || keyHash || valueHash) — the key hash is bound so the leaf commitment
        // uniquely identifies (key, value); this is what makes proofs unforgeable.
        return hashFn.digest(Bytes.concat(new byte[]{TAG_LEAF},
                Arrays.copyOf(keyHash, keyHash.length),
                Arrays.copyOf(valueHash, valueHash.length)));
    }

    @Override
    public byte[] nullHash() {
        return Arrays.copyOf(nullHash, nullHash.length);
    }
}
