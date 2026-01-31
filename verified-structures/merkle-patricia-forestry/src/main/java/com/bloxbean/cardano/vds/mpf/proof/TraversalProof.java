package com.bloxbean.cardano.vds.mpf.proof;

import com.bloxbean.cardano.vds.core.NibblePath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Internal proof model capturing the result of traversing a Merkle Patricia Trie.
 *
 * <p>This class represents the <b>internal/raw</b> proof data collected during trie traversal.
 * It is NOT the wire format - see {@link WireProof} for the CBOR-encoded format used for
 * verification and on-chain transmission.</p>
 *
 * <h2>Design Rationale</h2>
 * <p>The proof system uses two separate representations:</p>
 * <ul>
 *   <li><b>TraversalProof</b> (this class) - Captures the full traversal state including all 16
 *       child hashes at each branch node. This preserves complete information needed for
 *       serialization.</li>
 *   <li><b>{@link WireProof}</b> - The compact wire format with only 4 merkle sibling hashes
 *       per branch (compressed from 16). This is what gets CBOR-encoded and verified.</li>
 * </ul>
 *
 * <p>The {@link ProofSerializer} performs a <b>lossy transformation</b> from TraversalProof
 * to WireProof - the 16 child hashes are compressed into 4 merkle siblings for efficient
 * verification. This compression cannot be reversed, which is why we maintain separate classes.</p>
 *
 * <h2>Proof Types</h2>
 * <ul>
 *   <li>{@link Type#INCLUSION} - Key exists in trie, proof includes value</li>
 *   <li>{@link Type#NON_INCLUSION_MISSING_BRANCH} - Key doesn't exist, traversal ended at
 *       empty branch slot</li>
 *   <li>{@link Type#NON_INCLUSION_DIFFERENT_LEAF} - Key doesn't exist, traversal found a
 *       different leaf at the expected location</li>
 * </ul>
 *
 * <h2>Step Types</h2>
 * <ul>
 *   <li>{@link BranchStep} - Traversal through a 16-way branch node, captures all child hashes</li>
 *   <li>{@link ForkStep} - Traversal diverged at an extension node, captures neighbor info</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>This class is package-private in its usage - it's created by {@code MpfTrie}'s internal
 * implementation and consumed by {@link ProofSerializer} to produce the wire format. End users
 * typically interact with the higher-level {@code MpfTrie.getProofWire()} method.</p>
 *
 * @see WireProof
 * @see ProofSerializer
 * @see ProofVerifier
 */
public final class TraversalProof {

    /**
     * The type of proof result from trie traversal.
     */
    public enum Type {
        /** Key exists in the trie - proof includes the value */
        INCLUSION,
        /** Key doesn't exist - traversal ended at an empty branch slot */
        NON_INCLUSION_MISSING_BRANCH,
        /** Key doesn't exist - traversal found a different leaf at the expected location */
        NON_INCLUSION_DIFFERENT_LEAF
    }

    private final Type type;
    private final List<Step> steps;
    private final byte[] value;
    private final byte[] valueHash;
    private final NibblePath suffix;
    private final byte[] conflictingKeyHash;
    private final byte[] conflictingValueHash;
    private final NibblePath conflictingSuffix;

    private TraversalProof(Type type,
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

    public static TraversalProof inclusion(List<Step> steps, byte[] value, byte[] valueHash, NibblePath suffix) {
        return new TraversalProof(Type.INCLUSION, steps, value, valueHash, suffix, null, null, null);
    }

    public static TraversalProof nonInclusionMissingBranch(List<Step> steps) {
        return new TraversalProof(Type.NON_INCLUSION_MISSING_BRANCH, steps, null, null, null, null, null, null);
    }

    public static TraversalProof nonInclusionDifferentLeaf(List<Step> steps, byte[] conflictingKeyHash, byte[] conflictingValueHash, NibblePath conflictingSuffix) {
        return new TraversalProof(Type.NON_INCLUSION_DIFFERENT_LEAF, steps, null, null, null, conflictingKeyHash, conflictingValueHash, conflictingSuffix);
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

    /**
     * A single step in the trie traversal proof.
     *
     * <p>Each step represents one node visited during traversal from root to the target key.
     * The skip value indicates how many nibbles were consumed by extension/leaf prefixes
     * before reaching this node.</p>
     */
    public interface Step {
        /**
         * Returns the number of nibbles consumed before this step (from extension prefixes).
         * @return the skip count
         */
        int skip();

        /**
         * Returns the nibble path consumed before this step.
         * @return the skip path as NibblePath
         */
        NibblePath skipPath();

        /**
         * Creates a deep copy of this step.
         * @return a new Step instance with copied data
         */
        Step cloneStep();
    }

    /**
     * A proof step representing traversal through a 16-way branch node.
     *
     * <p>This step captures all 16 child hashes of the branch node. During serialization
     * to {@link WireProof}, these 16 hashes are compressed into 4 merkle sibling hashes
     * using a binary tree reduction.</p>
     *
     * <p>The child index indicates which branch was followed to continue traversal.</p>
     */
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

    /**
     * A proof step representing a fork point where the query path diverges from an extension node.
     *
     * <p>This step is used for non-inclusion proofs where the traversal encounters an extension
     * node whose path doesn't match the query key. It captures information about the neighbor
     * subtree that exists at the fork point.</p>
     *
     * <p>The neighbor information includes:</p>
     * <ul>
     *   <li>neighborNibble - The first nibble of the neighbor's path (different from query)</li>
     *   <li>suffix - Remaining nibbles of the neighbor's extension path</li>
     *   <li>neighborRoot - The commitment hash of the neighbor subtree</li>
     * </ul>
     */
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
