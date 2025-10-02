package com.bloxbean.cardano.statetrees.jmt.mpf;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.util.Bytes;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serialises {@link JmtProof} instances into the MPF CBOR format expected by the Aiken verifier.
 */
public final class MpfProofSerializer {

    private static final int TAG_BRANCH = 121;
    private static final int TAG_FORK = 122;
    private static final int TAG_LEAF = 123;

    private MpfProofSerializer() {
        throw new AssertionError("Utility class");
    }

    public static byte[] toCbor(JmtProof proof, byte[] key, HashFunction hashFn, CommitmentScheme commitments) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Array stepsArray = new Array();
            for (JmtProof.BranchStep step : proof.steps()) {
                stepsArray.add(encodeStep(step, hashFn, commitments));
            }
            new CborEncoder(baos).encode(stepsArray);
        } catch (CborException e) {
            throw new IllegalStateException("Failed to encode MPF proof", e);
        }
        return baos.toByteArray();
    }

    private static DataItem encodeStep(JmtProof.BranchStep step, HashFunction hashFn, CommitmentScheme commitments) {
        int skip = step.prefix().length();

        if (step.hasSingleNeighbor()) {
            if (step.hasLeafNeighbor()) {
                Array leafArray = new Array();
                leafArray.add(new UnsignedInteger(skip));
                leafArray.add(new ByteString(step.leafNeighborKeyHash()));
                leafArray.add(new ByteString(step.leafNeighborValueHash()));
                leafArray.setTag(new Tag(TAG_LEAF));
                return leafArray;
            }
            if (step.hasForkNeighbor()) {
                Array neighborArray = new Array();
                neighborArray.add(new UnsignedInteger(step.neighborNibble()));
                neighborArray.add(new ByteString(nibbleBytes(step.forkNeighborPrefix())));
                neighborArray.add(new ByteString(step.forkNeighborRoot()));
                neighborArray.setTag(new Tag(TAG_BRANCH));

                Array forkArray = new Array();
                forkArray.add(new UnsignedInteger(skip));
                forkArray.add(neighborArray);
                forkArray.setTag(new Tag(TAG_FORK));
                return forkArray;
            }
        }

        Array branchArray = new Array();
        branchArray.add(new UnsignedInteger(skip));
        branchArray.add(new ByteString(concatNeighbors(step, hashFn, commitments)));
        branchArray.setTag(new Tag(TAG_BRANCH));
        return branchArray;
    }

    private static byte[] concatNeighbors(JmtProof.BranchStep step, HashFunction hashFn, CommitmentScheme commitments) {
        List<byte[]> neighbors = computeNeighbors(step, hashFn, commitments);
        int digestLength = neighbors.isEmpty() ? 0 : neighbors.get(0).length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(neighbors.size() * digestLength);
        for (byte[] neighbor : neighbors) {
            out.write(neighbor, 0, neighbor.length);
        }
        return out.toByteArray();
    }

    private static List<byte[]> computeNeighbors(JmtProof.BranchStep step, HashFunction hashFn, CommitmentScheme commitments) {
        byte[][] childHashes = step.childHashes();
        byte[] nullHash = commitments.nullHash();
        byte[][] nodes = new byte[childHashes.length][];
        for (int i = 0; i < childHashes.length; i++) {
            nodes[i] = childHashes[i] == null ? Arrays.copyOf(nullHash, nullHash.length) : childHashes[i];
        }

        int me = step.childIndex();
        List<byte[]> neighbors = new ArrayList<>(4);
        int pivot = 8;
        int n = 8;
        while (n >= 1) {
            int start;
            int end;
            if (me < pivot) {
                start = pivot;
                end = pivot + n;
                neighbors.add(merkleRoot(nodes, start, end, hashFn));
                pivot -= (n >> 1);
            } else {
                start = pivot - n;
                end = pivot;
                neighbors.add(merkleRoot(nodes, start, end, hashFn));
                pivot += (n >> 1);
            }
            n >>= 1;
        }
        return neighbors;
    }

    private static byte[] merkleRoot(byte[][] nodes, int start, int end, HashFunction hashFn) {
        int length = end - start;
        List<byte[]> layer = new ArrayList<>(length);
        for (int i = start; i < end; i++) {
            layer.add(Arrays.copyOf(nodes[i], nodes[i].length));
        }
        while (layer.size() > 1) {
            List<byte[]> next = new ArrayList<>(layer.size() / 2);
            for (int i = 0; i < layer.size(); i += 2) {
                next.add(hashFn.digest(Bytes.concat(layer.get(i), layer.get(i + 1))));
            }
            layer = next;
        }
        return layer.get(0);
    }

    private static byte[] nibbleBytes(NibblePath path) {
        int[] nibbles = path.getNibbles();
        byte[] out = new byte[nibbles.length];
        for (int i = 0; i < nibbles.length; i++) {
            out[i] = (byte) (nibbles[i] & 0xFF);
        }
        return out;
    }
}
