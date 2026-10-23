package com.bloxbean.cardano.vds.mpf.proof;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.commitment.CommitmentScheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * MPF proof representation used for both inclusion and non-inclusion verification.
 *
 * <p>{@link #computeRoot} is a port of the on-chain verifier of
 * {@code aiken-lang/merkle-patricia-forestry} 2.1.0 ({@code including} / {@code excluding} with
 * {@code do_branch}, {@code do_fork}, {@code merkle_16} and {@code sparse_merkle_16}). A proof accepted here is
 * accepted by Aiken. A few malformed shapes Aiken would also accept (for example a neighbor nibble equal to the
 * path nibble) are rejected, so CCL never accepts a proof Aiken rejects.</p>
 */
public final class WireProof {

    private final List<Step> steps;

    WireProof(List<Step> steps) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public List<Step> steps() {
        return steps;
    }

    /**
     * Computes the root this proof implies for {@code key}.
     *
     * @param key       the original (unhashed) key
     * @param value     the value for an inclusion proof; ignored for a non-inclusion proof
     * @param including {@code true} for Aiken's {@code including} (root with the element), {@code false} for
     *                  {@code excluding} (root without it)
     * @return the implied root, or {@code null} when excluding over an empty proof (the empty trie)
     * @throws IllegalArgumentException if the proof is malformed for this key
     */
    public byte[] computeRoot(byte[] key, byte[] value, boolean including,
                              HashFunction hashFn, CommitmentScheme commitments) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(hashFn, "hashFn");
        Objects.requireNonNull(commitments, "commitments");

        byte[] pathBytes = hashFn.digest(key);
        Ctx ctx = new Ctx(toNibbles(pathBytes), pathBytes.length, hashFn, commitments);
        if (including) {
            if (value == null) {
                throw new IllegalArgumentException("Value required for inclusion proof");
            }
            return doIncluding(ctx, hashFn.digest(value), 0, 0);
        }
        if (steps.isEmpty()) {
            return null;
        }
        return doExcluding(ctx, 0, 0);
    }

    // ------------------------------------------------------------------ Aiken port

    private static final class Ctx {
        final int[] path;
        final int digestLength;
        final HashFunction hashFn;
        final CommitmentScheme commitments;
        final byte[] nullHash;
        final byte[] nullHash2;
        final byte[] nullHash4;
        final byte[] nullHash8;

        Ctx(int[] path, int digestLength, HashFunction hashFn, CommitmentScheme commitments) {
            this.path = path;
            this.digestLength = digestLength;
            this.hashFn = hashFn;
            this.commitments = commitments;
            this.nullHash = commitments.nullHash();
            this.nullHash2 = WireProof.combine(hashFn, nullHash, nullHash);
            this.nullHash4 = WireProof.combine(hashFn, nullHash2, nullHash2);
            this.nullHash8 = WireProof.combine(hashFn, nullHash4, nullHash4);
        }

        byte[] combine(byte[] left, byte[] right) {
            return WireProof.combine(hashFn, left, right);
        }
    }

    /** {@code do_including}. */
    private byte[] doIncluding(Ctx ctx, byte[] valueHash, int cursor, int ix) {
        if (ix == steps.size()) {
            return leafCommit(ctx, ctx.path, cursor, valueHash);
        }
        Step step = steps.get(ix);
        int nextCursor = nextCursor(ctx, cursor, step);
        byte[] root = doIncluding(ctx, valueHash, nextCursor + 1, ix + 1);
        return rollUp(ctx, step, cursor, nextCursor, root);
    }

    /** {@code do_excluding}. */
    private byte[] doExcluding(Ctx ctx, int cursor, int ix) {
        if (ix == steps.size()) {
            return ctx.nullHash;
        }
        Step step = steps.get(ix);
        boolean last = ix + 1 == steps.size();
        int nextCursor = nextCursor(ctx, cursor, step);

        if (last && step instanceof ForkStep) {
            ForkStep fork = (ForkStep) step;
            // CCL-only check: the neighbor must not sit under the query path's own nibble.
            if (fork.nibble() == ctx.path[nextCursor]) {
                throw new IllegalArgumentException("Fork neighbor nibble equals path nibble");
            }
            byte[] neighborPrefix = Bytes.concat(new byte[]{(byte) fork.nibble()}, fork.prefix());
            byte[] prefix = fork.skip() == 0
                    ? neighborPrefix
                    : Bytes.concat(nibbles(ctx.path, cursor, cursor + fork.skip()), neighborPrefix);
            return ctx.combine(prefix, fork.root());
        }
        if (last && step instanceof LeafStep) {
            LeafStep leaf = (LeafStep) step;
            int[] keyPath = toNibbles(leaf.keyHash());
            checkLeafNeighbor(ctx, keyPath, nextCursor);
            return leafCommit(ctx, keyPath, cursor, leaf.valueHash());
        }

        byte[] root = doExcluding(ctx, nextCursor + 1, ix + 1);
        return rollUp(ctx, step, cursor, nextCursor, root);
    }

    private static int nextCursor(Ctx ctx, int cursor, Step step) {
        int skip = step.skip();
        if (skip < 0 || cursor + skip >= ctx.path.length) {
            throw new IllegalArgumentException("Proof step skips past the end of the path");
        }
        return cursor + skip;
    }

    private static byte[] rollUp(Ctx ctx, Step step, int cursor, int nextCursor, byte[] root) {
        if (step instanceof BranchStep) {
            return doBranch(ctx, (BranchStep) step, cursor, nextCursor, root);
        }
        if (step instanceof ForkStep) {
            ForkStep fork = (ForkStep) step;
            return doFork(ctx, cursor, nextCursor, root, fork.nibble(), ctx.combine(fork.prefix(), fork.root()));
        }
        if (step instanceof LeafStep) {
            LeafStep leaf = (LeafStep) step;
            int[] keyPath = toNibbles(leaf.keyHash());
            checkLeafNeighbor(ctx, keyPath, nextCursor);
            // Neighbor { prefix: suffix(key, next_cursor + 1), nibble: nibble(key, next_cursor), root: value }
            byte[] neighborHash = leafCommit(ctx, keyPath, nextCursor + 1, leaf.valueHash());
            return doFork(ctx, cursor, nextCursor, root, keyPath[nextCursor], neighborHash);
        }
        throw new IllegalArgumentException("Unknown step type " + step.getClass());
    }

    /** {@code do_branch}. */
    private static byte[] doBranch(Ctx ctx, BranchStep br, int cursor, int nextCursor, byte[] root) {
        byte[][] n = br.neighbors();
        byte[] merkle = merkle16(ctx, ctx.path[nextCursor], root, n[0], n[1], n[2], n[3]);
        if (br.branchValueHash() != null) {
            // Only for commitment schemes that commit branch values (never MPF/Aiken).
            byte[] valueCommit = ctx.commitments.commitLeaf(NibblePath.EMPTY, br.branchValueHash());
            merkle = ctx.hashFn.digest(Bytes.concat(merkle, valueCommit));
        }
        return ctx.combine(nibbles(ctx.path, cursor, nextCursor), merkle);
    }

    /** {@code do_fork}, with {@code neighborHash = combine(neighbor.prefix, neighbor.root)}. */
    private static byte[] doFork(Ctx ctx, int cursor, int nextCursor, byte[] root, int neighborNibble,
                                 byte[] neighborHash) {
        int branch = ctx.path[nextCursor];
        if (branch == neighborNibble) {
            throw new IllegalArgumentException("Fork neighbor nibble equals path nibble");
        }
        return ctx.combine(nibbles(ctx.path, cursor, nextCursor),
                sparseMerkle16(ctx, branch, root, neighborNibble, neighborHash));
    }

    /** CCL-only check: a Leaf neighbor shares the path up to the branch and sits under another nibble. */
    private static void checkLeafNeighbor(Ctx ctx, int[] keyPath, int nextCursor) {
        if (keyPath.length != ctx.path.length) {
            throw new IllegalArgumentException("Leaf neighbor key has the wrong length");
        }
        for (int i = 0; i < nextCursor; i++) {
            if (keyPath[i] != ctx.path[i]) {
                throw new IllegalArgumentException("Leaf neighbor path mismatch");
            }
        }
        if (keyPath[nextCursor] == ctx.path[nextCursor]) {
            throw new IllegalArgumentException("Leaf neighbor nibble equals path nibble");
        }
    }

    /** {@code combine(suffix(path, cursor), valueHash)}, routed through the commitment scheme. */
    private static byte[] leafCommit(Ctx ctx, int[] path, int cursor, byte[] valueHash) {
        return ctx.commitments.commitLeaf(NibblePath.of(Arrays.copyOfRange(path, cursor, path.length)), valueHash);
    }

    private static byte[] merkle16(Ctx c, int branch, byte[] root, byte[] n8, byte[] n4, byte[] n2, byte[] n1) {
        if (branch <= 7) {
            return c.combine(merkle8(c, branch, root, n4, n2, n1), n8);
        }
        return c.combine(n8, merkle8(c, branch - 8, root, n4, n2, n1));
    }

    private static byte[] merkle8(Ctx c, int branch, byte[] root, byte[] n4, byte[] n2, byte[] n1) {
        if (branch <= 3) {
            return c.combine(merkle4(c, branch, root, n2, n1), n4);
        }
        return c.combine(n4, merkle4(c, branch - 4, root, n2, n1));
    }

    private static byte[] merkle4(Ctx c, int branch, byte[] root, byte[] n2, byte[] n1) {
        if (branch <= 1) {
            return c.combine(branch == 0 ? c.combine(root, n1) : c.combine(n1, root), n2);
        }
        return c.combine(n2, branch == 2 ? c.combine(root, n1) : c.combine(n1, root));
    }

    private static byte[] sparseMerkle16(Ctx c, int me, byte[] meHash, int neighbor, byte[] neighborHash) {
        if (me <= 7) {
            if (neighbor <= 7) {
                return c.combine(sparseMerkle8(c, me, meHash, neighbor, neighborHash), c.nullHash8);
            }
            return c.combine(merkle8(c, me, meHash, c.nullHash4, c.nullHash2, c.nullHash),
                    merkle8(c, neighbor - 8, neighborHash, c.nullHash4, c.nullHash2, c.nullHash));
        }
        if (neighbor >= 8) {
            return c.combine(c.nullHash8, sparseMerkle8(c, me - 8, meHash, neighbor - 8, neighborHash));
        }
        return c.combine(merkle8(c, neighbor, neighborHash, c.nullHash4, c.nullHash2, c.nullHash),
                merkle8(c, me - 8, meHash, c.nullHash4, c.nullHash2, c.nullHash));
    }

    private static byte[] sparseMerkle8(Ctx c, int me, byte[] meHash, int neighbor, byte[] neighborHash) {
        if (me <= 3) {
            if (neighbor <= 3) {
                return c.combine(sparseMerkle4(c, me, meHash, neighbor, neighborHash), c.nullHash4);
            }
            return c.combine(merkle4(c, me, meHash, c.nullHash2, c.nullHash),
                    merkle4(c, neighbor - 4, neighborHash, c.nullHash2, c.nullHash));
        }
        if (neighbor >= 4) {
            return c.combine(c.nullHash4, sparseMerkle4(c, me - 4, meHash, neighbor - 4, neighborHash));
        }
        return c.combine(merkle4(c, neighbor, neighborHash, c.nullHash2, c.nullHash),
                merkle4(c, me - 4, meHash, c.nullHash2, c.nullHash));
    }

    private static byte[] sparseMerkle4(Ctx c, int me, byte[] meHash, int neighbor, byte[] neighborHash) {
        if (me <= 1) {
            if (neighbor <= 1) {
                return c.combine(combineMe(c, me, meHash, neighborHash), c.nullHash2);
            }
            return c.combine(combineMe(c, me, meHash, c.nullHash), combineMe(c, neighbor, neighborHash, c.nullHash));
        }
        if (neighbor >= 2) {
            return c.combine(c.nullHash2, combineMe(c, me, meHash, neighborHash));
        }
        return c.combine(combineMe(c, neighbor, neighborHash, c.nullHash), combineMe(c, me, meHash, c.nullHash));
    }

    /** {@code if me % 2 == 0 { combine(me_hash, other) } else { combine(other, me_hash) }}. */
    private static byte[] combineMe(Ctx c, int me, byte[] meHash, byte[] other) {
        return me % 2 == 0 ? c.combine(meHash, other) : c.combine(other, meHash);
    }

    /** {@code nibbles(path, start, end)}: one byte per nibble. */
    private static byte[] nibbles(int[] path, int start, int end) {
        if (start >= end) return Bytes.EMPTY;
        byte[] out = new byte[end - start];
        for (int i = start; i < end; i++) out[i - start] = (byte) path[i];
        return out;
    }

    private static byte[] combine(HashFunction hashFn, byte[] left, byte[] right) {
        return hashFn.digest(Bytes.concat(left, right));
    }

    private static int[] toNibbles(byte[] bytes) {
        int[] out = new int[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i] = (bytes[i] >> 4) & 0x0F;
            out[2 * i + 1] = bytes[i] & 0x0F;
        }
        return out;
    }

    // ------------------------------------------------------------------ steps

    interface Step {
        int skip();
    }

    static final class BranchStep implements Step {
        private final int skip;
        private final byte[][] neighbors;
        private final int childIndex;
        private final byte[] branchValueHash;

        BranchStep(int skip, byte[][] neighbors, int childIndex, byte[] branchValueHash) {
            this.skip = skip;
            this.neighbors = neighbors;
            this.childIndex = childIndex;
            this.branchValueHash = branchValueHash;
        }

        @Override
        public int skip() {
            return skip;
        }

        byte[][] neighbors() {
            return neighbors;
        }

        int childIndex() {
            return childIndex;
        }

        byte[] branchValueHash() {
            return branchValueHash;
        }
    }

    static final class ForkStep implements Step {
        private final int skip;
        private final int nibble;
        private final byte[] prefix;
        private final byte[] root;

        ForkStep(int skip, int nibble, byte[] prefix, byte[] root) {
            this.skip = skip;
            this.nibble = nibble;
            this.prefix = prefix;
            this.root = root;
        }

        @Override
        public int skip() {
            return skip;
        }

        int nibble() {
            return nibble;
        }

        byte[] prefix() {
            return prefix;
        }

        byte[] root() {
            return root;
        }
    }

    static final class LeafStep implements Step {
        private final int skip;
        private final byte[] keyHash;
        private final byte[] valueHash;

        LeafStep(int skip, byte[] keyHash, byte[] valueHash) {
            this.skip = skip;
            this.keyHash = keyHash;
            this.valueHash = valueHash;
        }

        @Override
        public int skip() {
            return skip;
        }

        byte[] keyHash() {
            return keyHash;
        }

        byte[] valueHash() {
            return valueHash;
        }
    }
}
