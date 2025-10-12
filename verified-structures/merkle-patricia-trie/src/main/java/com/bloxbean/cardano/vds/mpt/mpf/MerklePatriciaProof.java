package com.bloxbean.cardano.vds.mpt.mpf;

import com.bloxbean.cardano.vds.core.NibblePath;

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
    private final List<Step> steps;
    private final byte[] value;
    private final byte[] valueHash;
    private final NibblePath suffix;
    private final byte[] conflictingKeyHash;
    private final byte[] conflictingValueHash;
    private final NibblePath conflictingSuffix;

    private MerklePatriciaProof(Type type,
                                List<Step> steps,
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

    public List<Step> getSteps() {
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

    public static MerklePatriciaProof inclusion(List<Step> steps, byte[] value, byte[] valueHash, NibblePath suffix) {
        return new MerklePatriciaProof(Type.INCLUSION, steps, value, valueHash, suffix, null, null, null);
    }

    public static MerklePatriciaProof nonInclusionMissingBranch(List<Step> steps) {
        return new MerklePatriciaProof(Type.NON_INCLUSION_MISSING_BRANCH, steps, null, null, null, null, null, null);
    }

    public static MerklePatriciaProof nonInclusionDifferentLeaf(List<Step> steps, byte[] conflictingKeyHash, byte[] conflictingValueHash, NibblePath conflictingSuffix) {
        return new MerklePatriciaProof(Type.NON_INCLUSION_DIFFERENT_LEAF, steps, null, null, null, conflictingKeyHash, conflictingValueHash, conflictingSuffix);
    }

    private static List<Step> immutable(List<Step> src) {
        Objects.requireNonNull(src, "steps");
        List<Step> out = new ArrayList<>(src.size());
        for (Step s : src) out.add(s.cloneStep());
        return Collections.unmodifiableList(out);
    }

    private static byte[] copy(byte[] in) {
        return in == null ? null : Arrays.copyOf(in, in.length);
    }

    public interface Step {
        int skip();

        NibblePath skipPath();

        Step cloneStep();
    }

    public static final class BranchStep implements Step {
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

        @Override
        public BranchStep cloneStep() {
            return new BranchStep(skipPath, childHashes, childIndex, branchValueHash);
        }

        @Override
        public int skip() {
            return skipPath.length();
        }

        @Override
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

    public static final class ForkStep implements Step {
        private final NibblePath skipPath;
        private final int neighborNibble;
        private final NibblePath suffix;
        private final byte[] neighborRoot;

        public ForkStep(NibblePath skipPath, int neighborNibble, NibblePath suffix, byte[] neighborRoot) {
            this.skipPath = Objects.requireNonNull(skipPath, "skipPath");
            this.neighborNibble = neighborNibble;
            this.suffix = Objects.requireNonNull(suffix, "suffix");
            this.neighborRoot = neighborRoot == null ? null : Arrays.copyOf(neighborRoot, neighborRoot.length);
        }

        @Override
        public ForkStep cloneStep() {
            return new ForkStep(skipPath, neighborNibble, suffix, neighborRoot);
        }

        @Override
        public int skip() {
            return skipPath.length();
        }

        @Override
        public NibblePath skipPath() {
            return skipPath;
        }

        public int neighborNibble() {
            return neighborNibble;
        }

        public NibblePath suffix() {
            return suffix;
        }

        public byte[] neighborRoot() {
            return neighborRoot == null ? null : Arrays.copyOf(neighborRoot, neighborRoot.length);
        }
    }
}
