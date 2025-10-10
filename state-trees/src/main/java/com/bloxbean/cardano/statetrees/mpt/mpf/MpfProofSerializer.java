package com.bloxbean.cardano.statetrees.mpt.mpf;

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
import com.bloxbean.cardano.statetrees.mpt.mpf.MerklePatriciaProof;
import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serialises {@link MerklePatriciaProof} instances into the MPF CBOR format expected by the Aiken verifier.
 */
public final class MpfProofSerializer {

    private static final int TAG_BRANCH = 121;
    private static final int TAG_FORK = 122;
    private static final int TAG_LEAF = 123;

    private MpfProofSerializer() {
        throw new AssertionError("Utility class");
    }

    public static byte[] toCbor(MerklePatriciaProof proof, byte[] key,
                                HashFunction hashFn, CommitmentScheme commitments) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Array stepsArray = new Array();
            for (MpfProof.Step step : toSteps(proof, key, hashFn, commitments)) {
                stepsArray.add(encodeStep(step));
            }
            new CborEncoder(baos).encode(stepsArray);
        } catch (CborException e) {
            throw new IllegalStateException("Failed to encode MPF proof", e);
        }
        return baos.toByteArray();
    }

    private static List<MpfProof.Step> toSteps(MerklePatriciaProof proof,
                                               byte[] key,
                                               HashFunction hashFn,
                                               CommitmentScheme commitments) {
        List<MpfProof.Step> steps = new ArrayList<>();
        int consumed = 0;
        for (MerklePatriciaProof.Step step : proof.getSteps()) {
            if (step instanceof MerklePatriciaProof.BranchStep) {
                MerklePatriciaProof.BranchStep branch = (MerklePatriciaProof.BranchStep) step;
                steps.add(encodeBranchStep(branch, hashFn, commitments));
            } else if (step instanceof MerklePatriciaProof.ForkStep) {
                MerklePatriciaProof.ForkStep fork = (MerklePatriciaProof.ForkStep) step;
                steps.add(encodeForkStep(fork, commitments));
            } else {
                throw new IllegalStateException("Unsupported proof step " + step.getClass());
            }
            consumed += 1 + step.skip();
        }

        // For conflicting-leaf non-inclusion, append a terminal Leaf step so the
        // verifier can reconstruct the neighbor's leaf commitment and roll it up.
        if (proof.getType() == MerklePatriciaProof.Type.NON_INCLUSION_DIFFERENT_LEAF) {
            byte[] conflictingKeyHash = proof.getConflictingKeyHash();
            byte[] valueHash = proof.getConflictingValueHash();
            if (conflictingKeyHash == null || valueHash == null) {
                throw new IllegalStateException("Conflicting key and value hash required for non-inclusion proof");
            }

            // Compute skip: number of nibbles the two keys share in common from the consumed position
            // Note: 'key' parameter is already the hashed key (trie path), don't hash again!
            String queryPathHex = Bytes.toHex(key);
            String conflictingPathHex = Bytes.toHex(conflictingKeyHash);
            int common = commonPrefixLen(queryPathHex.substring(consumed), conflictingPathHex.substring(consumed));

            steps.add(new MpfProof.LeafStep(common, conflictingKeyHash, valueHash));
        }
        return steps;
    }

    private static MpfProof.BranchStep encodeBranchStep(MerklePatriciaProof.BranchStep step,
                                                        HashFunction hashFn,
                                                        CommitmentScheme commitments) {
        byte[][] neighbors = computeNeighbors(step.childHashes(), step.childIndex(), hashFn, commitments.nullHash());
        return new MpfProof.BranchStep(step.skip(), neighbors, step.childIndex(), step.branchValueHash());
    }

    private static MpfProof.ForkStep encodeForkStep(MerklePatriciaProof.ForkStep step,
                                                    CommitmentScheme commitments) {
        byte[] prefix = toNibbleBytes(step.suffix());
        byte[] root = sanitizeRoot(step.neighborRoot(), commitments);
        return new MpfProof.ForkStep(step.skip(), step.neighborNibble(), prefix, root);
    }

    private static int commonPrefixLen(String a, String b) {
        int L = Math.min(a.length(), b.length());
        int i = 0;
        while (i < L && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    private static DataItem encodeStep(MpfProof.Step step) {
        if (step instanceof MpfProof.BranchStep) {
            MpfProof.BranchStep br = (MpfProof.BranchStep) step;
            Array branchArray = new Array();
            branchArray.add(new UnsignedInteger(br.skip()));
            branchArray.add(new ByteString(concatNeighbors(br.neighbors())));
            if (br.branchValueHash() != null) {
                branchArray.add(new ByteString(br.branchValueHash()));
            }
            branchArray.setTag(new Tag(TAG_BRANCH));
            return branchArray;
        } else if (step instanceof MpfProof.ForkStep) {
            MpfProof.ForkStep fork = (MpfProof.ForkStep) step;
            Array neighborArray = new Array();
            neighborArray.add(new UnsignedInteger(fork.nibble()));
            neighborArray.add(new ByteString(fork.prefix()));
            neighborArray.add(new ByteString(fork.root()));
            neighborArray.setTag(new Tag(TAG_BRANCH));

            Array forkArray = new Array();
            forkArray.add(new UnsignedInteger(fork.skip()));
            forkArray.add(neighborArray);
            forkArray.setTag(new Tag(TAG_FORK));
            return forkArray;
        } else if (step instanceof MpfProof.LeafStep) {
            MpfProof.LeafStep leaf = (MpfProof.LeafStep) step;
            Array leafArray = new Array();
            leafArray.add(new UnsignedInteger(leaf.skip()));
            leafArray.add(new ByteString(leaf.keyHash()));
            leafArray.add(new ByteString(leaf.valueHash()));
            leafArray.setTag(new Tag(TAG_LEAF));
            return leafArray;
        }
        throw new IllegalStateException("Unknown proof step type: " + step.getClass());
    }

    private static byte[] concatNeighbors(byte[][] neighbors) {
        if (neighbors.length == 0) return Bytes.EMPTY;
        int digestLength = neighbors[0].length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(neighbors.length * digestLength);
        for (byte[] neighbor : neighbors) {
            out.write(neighbor, 0, neighbor.length);
        }
        return out.toByteArray();
    }

    private static byte[][] computeNeighbors(byte[][] childHashes,
                                             int childIndex,
                                             HashFunction hashFn,
                                             byte[] nullHash) {
        byte[][] sanitized = new byte[16][];
        for (int i = 0; i < 16; i++) {
            byte[] child = (i < childHashes.length) ? childHashes[i] : null;
            sanitized[i] = child == null ? Arrays.copyOf(nullHash, nullHash.length) : child;
        }

        int me = childIndex >= 0 ? childIndex : 0;
        List<byte[]> neighbors = new ArrayList<>(4);
        int pivot = 8;
        int n = 8;
        while (n >= 1) {
            if (me < pivot) {
                neighbors.add(merkleRoot(sanitized, pivot, pivot + n, hashFn));
                pivot -= (n >> 1);
            } else {
                neighbors.add(merkleRoot(sanitized, pivot - n, pivot, hashFn));
                pivot += (n >> 1);
            }
            n >>= 1;
        }
        return neighbors.toArray(new byte[0][]);
    }

    private static byte[] merkleRoot(byte[][] nodes, int start, int end, HashFunction hashFn) {
        List<byte[]> layer = new ArrayList<>(end - start);
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

    private static byte[] toNibbleBytes(NibblePath path) {
        if (path == null || path.isEmpty()) {
            return Bytes.EMPTY;
        }
        int[] nibbles = path.getNibbles();
        byte[] out = new byte[nibbles.length];
        for (int i = 0; i < nibbles.length; i++) {
            out[i] = (byte) (nibbles[i] & 0xFF);
        }
        return out;
    }

    private static byte[] sanitizeRoot(byte[] root, CommitmentScheme commitments) {
        if (root == null || root.length == 0) {
            return commitments.nullHash();
        }
        return Arrays.copyOf(root, root.length);
    }
}
