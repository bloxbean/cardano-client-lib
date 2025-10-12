package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Proof data structure for Jellyfish Merkle Tree inclusion and non-inclusion proofs.
 */
public final class JmtProof {

    public enum ProofType {
        INCLUSION,
        NON_INCLUSION_EMPTY,
        NON_INCLUSION_DIFFERENT_LEAF
    }

    private final ProofType type;
    private final List<BranchStep> steps;
    private final byte[] value;
    private final byte[] valueHash;
    private final NibblePath suffix;
    private final byte[] leafKeyHash;
    private final byte[] conflictingKeyHash;
    private final byte[] conflictingValueHash;
    private final NibblePath conflictingSuffix;

    private JmtProof(ProofType type, List<BranchStep> steps, byte[] value, byte[] valueHash,
                     NibblePath suffix, byte[] leafKeyHash,
                     byte[] conflictingKeyHash, byte[] conflictingValueHash, NibblePath conflictingSuffix) {
        this.type = type;
        this.steps = steps;
        this.value = value;
        this.valueHash = valueHash;
        this.suffix = suffix;
        this.leafKeyHash = leafKeyHash;
        this.conflictingKeyHash = conflictingKeyHash;
        this.conflictingValueHash = conflictingValueHash;
        this.conflictingSuffix = conflictingSuffix;
    }

    public ProofType type() {
        return type;
    }

    public List<BranchStep> steps() {
        return steps;
    }

    public byte[] value() {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    public byte[] valueHash() {
        return valueHash == null ? null : Arrays.copyOf(valueHash, valueHash.length);
    }

    public NibblePath suffix() {
        return suffix;
    }

    public byte[] leafKeyHash() {
        return leafKeyHash == null ? null : Arrays.copyOf(leafKeyHash, leafKeyHash.length);
    }

    public byte[] conflictingKeyHash() {
        return conflictingKeyHash == null ? null : Arrays.copyOf(conflictingKeyHash, conflictingKeyHash.length);
    }

    public byte[] conflictingValueHash() {
        return conflictingValueHash == null ? null : Arrays.copyOf(conflictingValueHash, conflictingValueHash.length);
    }

    public NibblePath conflictingSuffix() {
        return conflictingSuffix;
    }

    static JmtProof inclusion(List<BranchStep> steps, byte[] value, byte[] valueHash,
                              NibblePath suffix, byte[] leafKeyHash) {
        return new JmtProof(ProofType.INCLUSION, immutableSteps(steps),
                copy(value), copy(valueHash), suffix, copy(leafKeyHash), null, null, null);
    }

    static JmtProof nonInclusionEmpty(List<BranchStep> steps) {
        return new JmtProof(ProofType.NON_INCLUSION_EMPTY, immutableSteps(steps),
                null, null, null, null, null, null, null);
    }

    static JmtProof nonInclusionDifferentLeaf(List<BranchStep> steps, byte[] keyHash,
                                              byte[] valueHash, NibblePath suffix) {
        return new JmtProof(ProofType.NON_INCLUSION_DIFFERENT_LEAF, immutableSteps(steps),
                null, null, null, null, copy(keyHash), copy(valueHash), suffix);
    }

    private static byte[] copy(byte[] data) {
        return data == null ? null : Arrays.copyOf(data, data.length);
    }

    private static List<BranchStep> immutableSteps(List<BranchStep> steps) {
        List<BranchStep> copy = new ArrayList<>(steps.size());
        for (BranchStep step : steps) copy.add(step.cloneStep());
        return Collections.unmodifiableList(copy);
    }

    public static final class BranchStep {
        private final NibblePath prefix;
        private final byte[][] childHashes;
        private final int childIndex;
        private final boolean singleNeighbor;
        private final int neighborNibble;
        private final NibblePath forkNeighborPrefix;
        private final byte[] forkNeighborRoot;
        private final byte[] leafNeighborKeyHash;
        private final byte[] leafNeighborValueHash;

        public BranchStep(NibblePath prefix, byte[][] childHashes, int childIndex,
                          boolean singleNeighbor, int neighborNibble,
                          NibblePath forkNeighborPrefix, byte[] forkNeighborRoot,
                          byte[] leafNeighborKeyHash, byte[] leafNeighborValueHash) {
            this.prefix = Objects.requireNonNull(prefix, "prefix");
            this.childHashes = cloneMatrix(childHashes);
            this.childIndex = childIndex;
            this.singleNeighbor = singleNeighbor;
            this.neighborNibble = neighborNibble;
            this.forkNeighborPrefix = forkNeighborPrefix;
            this.forkNeighborRoot = forkNeighborRoot == null ? null : Arrays.copyOf(forkNeighborRoot, forkNeighborRoot.length);
            this.leafNeighborKeyHash = leafNeighborKeyHash == null ? null : Arrays.copyOf(leafNeighborKeyHash, leafNeighborKeyHash.length);
            this.leafNeighborValueHash = leafNeighborValueHash == null ? null : Arrays.copyOf(leafNeighborValueHash, leafNeighborValueHash.length);
        }

        private BranchStep cloneStep() {
            return new BranchStep(prefix, childHashes, childIndex,
                    singleNeighbor, neighborNibble, forkNeighborPrefix,
                    forkNeighborRoot, leafNeighborKeyHash, leafNeighborValueHash);
        }

        public NibblePath prefix() {
            return prefix;
        }

        public byte[][] childHashes() {
            return cloneMatrix(childHashes);
        }

        public int childIndex() {
            return childIndex;
        }

        public boolean hasSingleNeighbor() {
            return singleNeighbor;
        }

        public int neighborNibble() {
            return neighborNibble;
        }

        public boolean hasForkNeighbor() {
            return forkNeighborPrefix != null && forkNeighborRoot != null;
        }

        public NibblePath forkNeighborPrefix() {
            return forkNeighborPrefix;
        }

        public byte[] forkNeighborRoot() {
            return forkNeighborRoot == null ? null : Arrays.copyOf(forkNeighborRoot, forkNeighborRoot.length);
        }

        public boolean hasLeafNeighbor() {
            return leafNeighborKeyHash != null && leafNeighborValueHash != null;
        }

        public byte[] leafNeighborKeyHash() {
            return leafNeighborKeyHash == null ? null : Arrays.copyOf(leafNeighborKeyHash, leafNeighborKeyHash.length);
        }

        public byte[] leafNeighborValueHash() {
            return leafNeighborValueHash == null ? null : Arrays.copyOf(leafNeighborValueHash, leafNeighborValueHash.length);
        }

        private static byte[][] cloneMatrix(byte[][] source) {
            byte[][] clone = new byte[source.length][];
            for (int i = 0; i < source.length; i++) {
                clone[i] = source[i] == null ? null : Arrays.copyOf(source[i], source[i].length);
            }
            return clone;
        }
    }
}
