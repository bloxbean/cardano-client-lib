package com.bloxbean.cardano.vds.jmt.proof;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.jmt.JmtEncoding;
import com.bloxbean.cardano.vds.jmt.JmtExtensionNode;
import com.bloxbean.cardano.vds.jmt.JmtInternalNode;
import com.bloxbean.cardano.vds.jmt.JmtLeafNode;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Classic wire codec: proof is a CBOR array of ByteStrings, each a CBOR-encoded node
 * (JmtInternalNode/JmtLeafNode/JmtExtensionNode) along the path.
 *
 * <p>This is the default format for this library. It is inspired by JMT path proofs but is not
 * wire-compatible with Diem/Aptos. The wire format is a CBOR array where each element is a CBOR-encoded node along the
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
 * @since 0.8.0
 */
public final class ClassicJmtProofCodec implements JmtProofCodec {

    private static final int MAX_WIRE_BYTES = 1024 * 1024;

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
        // A verifier consumes untrusted bytes: malformed or structurally invalid proofs are
        // "invalid" (false), never an unchecked exception thrown at the caller.
        try {
            return verifyInternal(expectedRoot, key, valueOrNull, including, wire, hashFn, cs);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean verifyInternal(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including,
                                   byte[] wire, HashFunction hashFn, CommitmentScheme cs) {
        if (wire == null || wire.length > MAX_WIRE_BYTES) return false;
        if (!including && valueOrNull != null) return false;
        byte[] normalizedExpected = expectedRoot == null ? cs.nullHash() : expectedRoot;
        byte[] keyHash = hashFn.digest(key);
        if (keyHash == null || keyHash.length == 0 || normalizedExpected.length != keyHash.length
                || cs.nullHash().length != keyHash.length) {
            return false;
        }
        int[] keyNibbles = Nibbles.toNibbles(keyHash);
        // At most one path node can consume each key nibble, plus one terminal leaf.
        List<JmtNode> nodes = decodeNodes(wire, keyNibbles.length + 1);

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
                if (depth >= keyNibbles.length) return false;
                internalDepths[i] = depth;
                int nib = keyNibbles[depth];
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
                com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP hp = Nibbles.unpackHP(ex.hpBytes());
                int[] seg = hp.nibbles;
                if (hp.isLeaf || seg.length == 0 || ex.childHash().length != keyHash.length) return false;
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

        // Proof-type / claim consistency. An inclusion claim can ONLY be satisfied by a proof
        // that terminates at the queried key's leaf; a non-inclusion (missing-branch / empty)
        // proof must never be accepted as inclusion. Without this, a genuine NON_INCLUSION_EMPTY
        // wire proof presented with including=true reconstructs the real root and forges inclusion
        // of an absent key.
        if (including && !terminalLeaf) {
            return false;
        }
        if (!nodes.isEmpty() && !terminalLeaf && !terminalMissingBranch) {
            return false;
        }

        // Bottom-up recomputation
        byte[] computed = null;
        if (terminalLeaf) {
            if (including) {
                if (valueOrNull == null) return false;
                if (!Arrays.equals(leafNode.keyHash(), keyHash)) return false;
                byte[] valueHash = hashFn.digest(valueOrNull);
                if (!Arrays.equals(valueHash, leafNode.valueHash())) return false;
                // Leaf commitment binds the key hash; derive it from the queried key.
                computed = cs.commitLeaf(keyHash, valueHash);
            } else {
                // A leaf that matches the queried key proves PRESENCE — it cannot back a
                // non-inclusion claim.
                if (Arrays.equals(leafNode.keyHash(), keyHash)) return false;
                // The conflicting leaf must lie on the queried key's path (shared prefix of length depth).
                int[] ln = Nibbles.toNibbles(leafNode.keyHash());
                if (depth > keyNibbles.length || ln.length < depth) return false;
                for (int i = 0; i < depth; i++) {
                    if (ln[i] != keyNibbles[i]) return false;
                }
                computed = cs.commitLeaf(leafNode.keyHash(), leafNode.valueHash());
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
                computed = hashFn.digest(com.bloxbean.cardano.vds.core.util.Bytes.concat(new byte[]{0x02}, ex.hpBytes(), child));
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

    private static List<JmtNode> decodeNodes(byte[] wire, int maxNodes) {
        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(wire)).decode();
            if (items.size() != 1 || !(items.get(0) instanceof Array)) {
                throw new IllegalArgumentException("Classic JMT proof must contain exactly one CBOR array");
            }
            Array arr = (Array) items.get(0);
            if (arr.getDataItems().size() > maxNodes) {
                throw new IllegalArgumentException("Classic JMT proof exceeds maximum path length");
            }
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
