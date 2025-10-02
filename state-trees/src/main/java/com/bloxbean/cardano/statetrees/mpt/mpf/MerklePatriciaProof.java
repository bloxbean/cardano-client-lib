package com.bloxbean.cardano.statetrees.mpt.mpf;

import com.bloxbean.cardano.statetrees.common.NibblePath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Internal MPF-oriented proof model for MPT.
 */
public final class MerklePatriciaProof {

    public enum Type {INCLUSION, NON_INCLUSION_MISSING_BRANCH, NON_INCLUSION_DIFFERENT_LEAF}

    private final Type type;
    private final List<BranchStep> steps;
    private final byte[] value;
    private final byte[] valueHash;
    private final NibblePath suffix;
    private final byte[] conflictingKeyHash;
    private final byte[] conflictingValueHash;
    private final NibblePath conflictingSuffix;

    private MerklePatriciaProof(Type type,
                                List<BranchStep> steps,
                                byte[] value,
                                byte[] valueHash,
                                NibblePath suffix,
                                byte[] conflictingKeyHash,
                                byte[] conflictingValueHash,
                                NibblePath conflictingSuffix) {
        this.type = Objects.requireNonNull(type);
        this.steps = immutable(steps);
        this.value = copy(value);
        this.valueHash = copy(valueHash);
        this.suffix = suffix;
        this.conflictingKeyHash = copy(conflictingKeyHash);
        this.conflictingValueHash = copy(conflictingValueHash);
        this.conflictingSuffix = conflictingSuffix;
    }

    public Type getType() {
        return type;
    }

    public List<BranchStep> getSteps() {
        return steps;
    }

    public byte[] getValue() {
        return copy(value);
    }

    public byte[] getValueHash() {
        return copy(valueHash);
    }

    public NibblePath getSuffix() {
        return suffix;
    }

    public byte[] getConflictingKeyHash() {
        return copy(conflictingKeyHash);
    }

    public byte[] getConflictingValueHash() {
        return copy(conflictingValueHash);
    }

    public NibblePath getConflictingSuffix() {
        return conflictingSuffix;
    }

    public static MerklePatriciaProof inclusion(List<BranchStep> steps, byte[] value, byte[] valueHash, NibblePath suffix) {
        return new MerklePatriciaProof(Type.INCLUSION, steps, value, valueHash, suffix, null, null, null);
    }

    public static MerklePatriciaProof nonInclusionMissingBranch(List<BranchStep> steps) {
        return new MerklePatriciaProof(Type.NON_INCLUSION_MISSING_BRANCH, steps, null, null, null, null, null, null);
    }

    public static MerklePatriciaProof nonInclusionDifferentLeaf(List<BranchStep> steps, byte[] conflictingKeyHash, byte[] conflictingValueHash, NibblePath conflictingSuffix) {
        return new MerklePatriciaProof(Type.NON_INCLUSION_DIFFERENT_LEAF, steps, null, null, null, conflictingKeyHash, conflictingValueHash, conflictingSuffix);
    }

    private static List<BranchStep> immutable(List<BranchStep> src) {
        Objects.requireNonNull(src, "steps");
        List<BranchStep> out = new ArrayList<>(src.size());
        for (BranchStep s : src) out.add(s.cloneStep());
        return Collections.unmodifiableList(out);
    }

    private static byte[] copy(byte[] in) {
        return in == null ? null : Arrays.copyOf(in, in.length);
    }

    public static final class BranchStep {
        private final NibblePath skipPath;
        private final byte[][] childHashes;
        private final int childIndex;
        private final byte[] branchValueHash;

        public BranchStep(NibblePath skipPath, byte[][] childHashes, int childIndex, byte[] branchValueHash) {
            this.skipPath = Objects.requireNonNull(skipPath);
            this.childHashes = cloneMat(Objects.requireNonNull(childHashes));
            this.childIndex = childIndex;
            this.branchValueHash = branchValueHash == null ? null : Arrays.copyOf(branchValueHash, branchValueHash.length);
        }

        private BranchStep cloneStep() {
            return new BranchStep(skipPath, childHashes, childIndex, branchValueHash);
        }

        public int skip() {
            return skipPath.length();
        }

        public NibblePath skipPath() {
            return skipPath;
        }

        public byte[][] childHashes() {
            return cloneMat(childHashes);
        }

        public int childIndex() {
            return childIndex;
        }

        public byte[] branchValueHash() {
            return branchValueHash == null ? null : Arrays.copyOf(branchValueHash, branchValueHash.length);
        }

        private static byte[][] cloneMat(byte[][] m) {
            byte[][] c = new byte[m.length][];
            for (int i = 0; i < m.length; i++) c[i] = m[i] == null ? null : Arrays.copyOf(m[i], m[i].length);
            return c;
        }
    }
}
