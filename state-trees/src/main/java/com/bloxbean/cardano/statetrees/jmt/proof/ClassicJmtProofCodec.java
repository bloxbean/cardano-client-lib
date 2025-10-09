package com.bloxbean.cardano.statetrees.jmt.proof;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.jmt.JmtEncoding;
import com.bloxbean.cardano.statetrees.jmt.JmtExtensionNode;
import com.bloxbean.cardano.statetrees.jmt.JmtInternalNode;
import com.bloxbean.cardano.statetrees.jmt.JmtLeafNode;
import com.bloxbean.cardano.statetrees.jmt.JmtNode;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Classic wire codec: proof is a CBOR array of ByteStrings, each a CBOR-encoded node
 * (JmtInternalNode/JmtLeafNode/JmtExtensionNode) along the path.
 *
 * <p>This is the default implementation compatible with Diem's JMT reference implementation.
 * The wire format is a CBOR array where each element is a CBOR-encoded node along the
 * Merkle path from root to leaf.
 *
 * <p><b>Wire Format Structure:</b>
 * <pre>
 * [
 *   node_0,  // CBOR-encoded JmtInternalNode (root level)
 *   node_1,  // CBOR-encoded JmtInternalNode (next level)
 *   ...
 *   node_n   // CBOR-encoded JmtLeafNode (terminal, if inclusion/conflicting leaf)
 * ]
 * </pre>
 *
 * @see JmtProofCodec
 * @since 0.6.0
 */
public final class ClassicJmtProofCodec implements JmtProofCodec {

    @Override
    public byte[] toWire(JmtProof proof, byte[] key, HashFunction hashFn, CommitmentScheme cs) {
        try {
            Array arr = new Array();
            // Branch steps → internal nodes
            for (JmtProof.BranchStep step : proof.steps()) {
                int bitmap = 0;
                byte[][] full = step.childHashes(); // expected 16 length with nulls for empty
                int present = 0;
                for (int i = 0; i < 16; i++) {
                    if (full[i] != null) {
                        bitmap |= (1 << i);
                        present++;
                    }
                }
                byte[][] compact = new byte[present][];
                int ci = 0;
                for (int i = 0; i < 16; i++) {
                    if (full[i] != null) compact[ci++] = full[i];
                }
                byte[] enc = JmtInternalNode.of(bitmap, compact, null).encode();
                arr.add(new ByteString(enc));
            }

            // Terminal leaf if applicable
            switch (proof.type()) {
                case INCLUSION: {
                    byte[] keyHash = proof.leafKeyHash();
                    byte[] valueHash = proof.valueHash();
                    byte[] enc = JmtLeafNode.of(keyHash, valueHash).encode();
                    arr.add(new ByteString(enc));
                    break;
                }
                case NON_INCLUSION_DIFFERENT_LEAF: {
                    byte[] keyHash = proof.conflictingKeyHash();
                    byte[] valueHash = proof.conflictingValueHash();
                    byte[] enc = JmtLeafNode.of(keyHash, valueHash).encode();
                    arr.add(new ByteString(enc));
                    break;
                }
                case NON_INCLUSION_EMPTY:
                    // No leaf node; terminates at missing branch
                    break;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(arr);
            return baos.toByteArray();
        } catch (CborException e) {
            throw new IllegalStateException("Failed to encode Classic JMT proof", e);
        }
    }

    @Override
    public boolean verify(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including,
                          byte[] wire, HashFunction hashFn, CommitmentScheme cs) {
        byte[] normalizedExpected = expectedRoot == null ? cs.nullHash() : expectedRoot;
        List<JmtNode> nodes = decodeNodes(wire);

        byte[] keyHash = hashFn.digest(key);
        int[] keyNibbles = Nibbles.toNibbles(keyHash);

        // Forward pass to determine depth at each internal and terminal condition
        int depth = 0;
        int lastIndex = nodes.size() - 1;
        int[] internalDepths = new int[nodes.size()];
        Arrays.fill(internalDepths, -1);

        boolean terminalMissingBranch = false;
        boolean terminalLeaf = false;
        JmtLeafNode leafNode = null;

        for (int i = 0; i < nodes.size(); i++) {
            JmtNode node = nodes.get(i);
            if (node instanceof JmtInternalNode) {
                JmtInternalNode in = (JmtInternalNode) node;
                internalDepths[i] = depth;
                int nib = depth < keyNibbles.length ? keyNibbles[depth] : 0;
                int bitmap = in.bitmap();
                boolean hasChild = ((bitmap >>> nib) & 1) == 1;
                if (!hasChild) {
                    terminalMissingBranch = true;
                    if (i != lastIndex) {
                        throw new IllegalArgumentException("Extra nodes after missing-branch terminal");
                    }
                    break;
                }
                depth++; // step into the child path
                continue;
            }
            if (node instanceof JmtExtensionNode) {
                // Enforce nibble segment match; HP bytes already include mode flags, but we only use nibbles
                JmtExtensionNode ex = (JmtExtensionNode) node;
                com.bloxbean.cardano.statetrees.common.nibbles.Nibbles.HP hp = Nibbles.unpackHP(ex.hpBytes());
                int[] seg = hp.nibbles;
                if (depth + seg.length > keyNibbles.length) return false;
                for (int j = 0; j < seg.length; j++) {
                    if (keyNibbles[depth + j] != seg[j]) return false;
                }
                depth += seg.length;
                continue;
            }
            if (node instanceof JmtLeafNode) {
                leafNode = (JmtLeafNode) node;
                terminalLeaf = true;
                if (i != lastIndex) {
                    throw new IllegalArgumentException("Extra nodes after terminal leaf");
                }
                break;
            }
            throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getSimpleName());
        }

        // Bottom-up recomputation
        byte[] computed = null;
        if (terminalLeaf) {
            if (including) {
                if (valueOrNull == null) return false;
                if (!Arrays.equals(leafNode.keyHash(), keyHash)) return false;
                byte[] valueHash = hashFn.digest(valueOrNull);
                if (!Arrays.equals(valueHash, leafNode.valueHash())) return false;
                int[] suffixNibs = Arrays.copyOfRange(keyNibbles, depth, keyNibbles.length);
                computed = cs.commitLeaf(NibblePath.of(suffixNibs), valueHash);
            } else {
                if (Arrays.equals(leafNode.keyHash(), keyHash)) return false; // conflicting leaf must differ
                int[] ln = Nibbles.toNibbles(leafNode.keyHash());
                int[] suffixNibs = Arrays.copyOfRange(ln, depth, ln.length);
                computed = cs.commitLeaf(NibblePath.of(suffixNibs), leafNode.valueHash());
            }
        } else if (terminalMissingBranch) {
            computed = null; // child is absent; parent will see NULL at the required slot
        }

        // Ascend through nodes in reverse
        for (int i = nodes.size() - 1; i >= 0; i--) {
            JmtNode node = nodes.get(i);
            if (node instanceof JmtExtensionNode) {
                JmtExtensionNode ex = (JmtExtensionNode) node;
                byte[] child = computed == null ? cs.nullHash() : computed;
                computed = hashFn.digest(com.bloxbean.cardano.statetrees.common.util.Bytes.concat(new byte[]{0x02}, ex.hpBytes(), child));
                continue;
            }
            if (node instanceof JmtInternalNode) {
                JmtInternalNode in = (JmtInternalNode) node;
                // Expand compact children according to bitmap
                byte[][] full = new byte[16][];
                int bitmap = in.bitmap();
                byte[][] compact = in.childHashes();
                int ci = 0;
                for (int b = 0; b < 16; b++) {
                    if (((bitmap >>> b) & 1) == 1) full[b] = Arrays.copyOf(compact[ci++], compact[ci - 1].length);
                }
                int idDepth = internalDepths[i];
                if (idDepth < 0) return false;
                int nib = idDepth < keyNibbles.length ? keyNibbles[idDepth] : 0;
                // Place the computed child at the traversed nibble
                full[nib] = computed == null ? null : Arrays.copyOf(computed, computed.length);
                computed = cs.commitBranch(NibblePath.EMPTY, full);
                continue;
            }
            if (node instanceof JmtLeafNode) {
                // Already handled as terminal
                continue;
            }
            throw new IllegalArgumentException("Unsupported node type during ascend: " + node.getClass().getSimpleName());
        }

        byte[] normalizedComputed = computed == null ? cs.nullHash() : computed;
        return Arrays.equals(normalizedExpected, normalizedComputed);
    }

    private static List<JmtNode> decodeNodes(byte[] wire) {
        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(wire)).decode();
            if (items.isEmpty() || !(items.get(0) instanceof Array)) {
                throw new IllegalArgumentException("Classic JMT proof must be a CBOR array");
            }
            Array arr = (Array) items.get(0);
            List<JmtNode> nodes = new ArrayList<>(arr.getDataItems().size());
            for (DataItem di : arr.getDataItems()) {
                if (!(di instanceof ByteString)) {
                    throw new IllegalArgumentException("Classic JMT proof elements must be ByteStrings");
                }
                byte[] enc = ((ByteString) di).getBytes();
                nodes.add(JmtEncoding.decode(enc));
            }
            return nodes;
        } catch (CborException e) {
            throw new IllegalArgumentException("Failed to decode Classic JMT proof", e);
        }
    }
}
