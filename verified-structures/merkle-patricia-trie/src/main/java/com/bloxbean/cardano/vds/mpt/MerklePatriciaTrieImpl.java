package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpt.mpf.MerklePatriciaProof;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpt.mode.Modes;
import com.bloxbean.cardano.vds.mpt.mode.MptMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core implementation of the Merkle Patricia Trie data structure.
 *
 * <p>This class implements a hexary (16-way) Merkle Patricia Trie following the Ethereum
 * specification with the following characteristics:</p>
 * <ul>
 *   <li>Three node types: Branch (16-way), Leaf, and Extension</li>
 *   <li>CBOR serialization for deterministic encoding</li>
 *   <li>Blake2b-256 hashing (configurable via HashFunction)</li>
 *   <li>Hex-Prefix (HP) encoding for path compression</li>
 *   <li>Automatic node compression and extension merging</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is NOT thread-safe. External synchronization
 * is required for concurrent access. Consider wrapping with a synchronized decorator
 * for multi-threaded environments.</p>
 *
 * <p><b>Performance Characteristics:</b></p>
 * <ul>
 *   <li>Put/Get/Delete: O(log n) where n is the number of keys</li>
 *   <li>ScanByPrefix: O(k) where k is the number of matching keys</li>
 *   <li>Space complexity: O(n) with path compression</li>
 * </ul>
 *
 */
public final class MerklePatriciaTrieImpl {
    private final NodeStore store;
    @SuppressWarnings("unused")
    private final HashFunction hashFn; // reserved for SecureTrie use
    private final NodePersistence persistence;
    @SuppressWarnings("unused")
    private final CommitmentScheme commitments;

    /**
     * The mode binding for this trie instance, which combines commitment scheme and proof codec.
     *
     * <p><b>MPF Mode (default):</b> Cardano-compatible mode for Aiken smart contracts
     * <ul>
     *   <li>Branch values NOT mixed into branch commitments (simpler hash tree)</li>
     *   <li>Optimized for SecureTrie usage (hashed keys)</li>
     *   <li>Smaller proof sizes (~32 bytes saving per affected step)</li>
     *   <li>Compatible with Aiken's merkle-patricia-forestry on-chain verifier</li>
     * </ul>
     *
     * <p><b>Classic Mode:</b> Legacy MPT compatibility (off-chain only)
     * <ul>
     *   <li>Branch values mixed into branch commitments (Ethereum-style)</li>
     *   <li>Different root hashes vs MPF mode for same data</li>
     *   <li>NOT compatible with Aiken on-chain validators</li>
     *   <li>Use only for off-chain applications requiring Ethereum MPT compatibility</li>
     * </ul>
     *
     * <p>The mode is immutable and set at construction time. To switch modes, create a new
     * trie instance with the desired mode configuration.
     *
     */
    private final MptMode mode;

    private byte[] root; // nullable => empty trie

    /**
     * Creates a new Merkle Patricia Trie implementation.
     *
     * @param store  the node storage backend (must not be null)
     * @param hashFn the hash function for node hashing (must not be null)
     * @param root   the initial root hash, or null for an empty trie
     * @throws NullPointerException if store or hashFn is null
     */
    public MerklePatriciaTrieImpl(NodeStore store, HashFunction hashFn, byte[] root) {
        this(store, hashFn, root, Modes.classic(hashFn));
    }

    public MerklePatriciaTrieImpl(NodeStore store, HashFunction hashFn, byte[] root, CommitmentScheme commitments) {
        this(store, hashFn, root, Modes.fromCommitments(commitments, false));
    }

    public MerklePatriciaTrieImpl(NodeStore store, HashFunction hashFn, byte[] root, MptMode mode) {
        this.store = Objects.requireNonNull(store, "NodeStore");
        this.hashFn = Objects.requireNonNull(hashFn, "HashFunction");
        this.mode = Objects.requireNonNull(mode, "MptMode");
        this.commitments = Objects.requireNonNull(mode.commitments(), "CommitmentScheme");
        this.persistence = new NodePersistence(store, commitments, hashFn);
        this.root = root == null || root.length == 0 ? null : root;
    }

    /**
     * Sets the root hash of the trie.
     *
     * @param root the new root hash, or null/empty for an empty trie
     */
    public void setRootHash(byte[] root) {
        this.root = root == null || root.length == 0 ? null : root;
    }

    /**
     * Gets the current root hash of the trie.
     *
     * @return the root hash, or null if the trie is empty
     */
    public byte[] getRootHash() {
        return root;
    }

    /**
     * Inserts or updates a key-value pair in the trie.
     *
     * @param key   the key to insert
     * @param value the value to associate with the key
     * @throws IllegalArgumentException if value is null
     */
    public void put(byte[] key, byte[] value) {
        if (value == null) throw new IllegalArgumentException("value cannot be null; use delete");
        int[] nibblePath = Nibbles.toNibbles(key);
        NodeHash rootHash = putAtNew(this.root, nibblePath, 0, value);
        this.root = rootHash != null ? rootHash.toBytes() : null;
    }

    /**
     * Retrieves a value by its key.
     *
     * @param key the key to look up
     * @return the associated value, or null if not found
     */
    public byte[] get(byte[] key) {
        int[] nibblePath = Nibbles.toNibbles(key);
        return getAtNew(this.root, nibblePath, 0);
    }

    /**
     * Deletes a key-value pair from the trie.
     *
     * @param key the key to delete
     */
    public void delete(byte[] key) {
        int[] nibblePath = Nibbles.toNibbles(key);
        NodeHash rootHash = deleteAtNew(this.root, nibblePath, 0);
        this.root = rootHash != null ? rootHash.toBytes() : null;
    }

    /**
     * Scans the trie for all entries with keys matching the given prefix.
     *
     * @param prefix the prefix to match
     * @param limit  the maximum number of results (0 or negative for unlimited)
     * @return a list of matching entries
     */
    public List<MerklePatriciaTrie.Entry> scanByPrefix(byte[] prefix, int limit) {
        int[] prefixNibbles = Nibbles.toNibbles(prefix);
        // If the caller provided an odd-length hex prefix padded as 0x0? (e.g., "a" -> 0x0a),
        // drop the leading zero nibble so we match nibble-level intent.
        if (prefixNibbles.length > 0 && prefixNibbles[0] == 0) {
            prefixNibbles = slice(prefixNibbles, 1, prefixNibbles.length);
        }
        List<MerklePatriciaTrie.Entry> results = new ArrayList<>();
        Deque<Integer> currentPath = new ArrayDeque<>();
        scan(this.root, prefixNibbles, 0, limit <= 0 ? Integer.MAX_VALUE : limit, currentPath, results);
        return results;
    }

    /**
     * Builds an MPF-style inclusion or non-inclusion proof for the provided key.
     */
    private MerklePatriciaProof getProof(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (this.root == null) {
            return MerklePatriciaProof.nonInclusionMissingBranch(Collections.emptyList());
        }

        int[] keyNibbles = Nibbles.toNibbles(key);
        List<MerklePatriciaProof.Step> steps = new ArrayList<>();
        NibblePath pendingPrefix = NibblePath.EMPTY;
        byte[] currentHash = this.root;
        int depth = 0;
        List<Integer> traversedNibbles = new ArrayList<>(); // Track actual path taken through trie

        while (true) {
            Node node = persistence.load(NodeHash.of(currentHash));
            if (node == null) {
                return MerklePatriciaProof.nonInclusionMissingBranch(steps);
            }

            if (node instanceof BranchNode) {
                BranchNode branch = (BranchNode) node;
                byte[][] childHashes = materializeChildHashes(branch);
                int childIndex = depth < keyNibbles.length ? keyNibbles[depth] : -1;

                if (depth >= keyNibbles.length) {
                    byte[] branchValue = branch.getValue();
                    byte[] branchValueHash = null;
                    if (commitments.encodesBranchValueInBranchCommitment()) {
                        branchValueHash = branch.getValue() == null ? null : hashFn.digest(branch.getValue());
                    }
                    steps.add(new MerklePatriciaProof.BranchStep(pendingPrefix, childHashes, childIndex, branchValueHash));
                    pendingPrefix = NibblePath.EMPTY;

                    if (branchValue != null) {
                        return MerklePatriciaProof.inclusion(steps, branchValue, branchValueHash, NibblePath.EMPTY);
                    }
                    return MerklePatriciaProof.nonInclusionMissingBranch(steps);
                }

                byte[] childCommit = branch.getChild(childIndex);
                if (childCommit == null || childCommit.length == 0) {
                    byte[] branchValueHash = null;
                    if (commitments.encodesBranchValueInBranchCommitment()) {
                        branchValueHash = branch.getValue() == null ? null : hashFn.digest(branch.getValue());
                    }
                    steps.add(new MerklePatriciaProof.BranchStep(pendingPrefix, childHashes, childIndex, branchValueHash));
                    pendingPrefix = NibblePath.EMPTY;
                    return MerklePatriciaProof.nonInclusionMissingBranch(steps);
                }

                // Add BranchStep and continue traversal
                byte[] branchValueHash = null;
                if (commitments.encodesBranchValueInBranchCommitment()) {
                    branchValueHash = branch.getValue() == null ? null : hashFn.digest(branch.getValue());
                }
                steps.add(new MerklePatriciaProof.BranchStep(pendingPrefix, childHashes, childIndex, branchValueHash));
                pendingPrefix = NibblePath.EMPTY;

                currentHash = childCommit;
                traversedNibbles.add(childIndex);
                depth++;
                continue;
            }

            if (node instanceof ExtensionNode) {
                ExtensionNode extension = (ExtensionNode) node;
                Nibbles.HP hp = Nibbles.unpackHP(extension.getHp());
                int[] extNibbles = hp.nibbles;
                // Check for path mismatch or overflow
                boolean pathMismatch = false;
                int mismatchPosition = -1;

                if (depth + extNibbles.length > keyNibbles.length) {
                    pathMismatch = true;
                    mismatchPosition = keyNibbles.length - depth;
                } else {
                    for (int i = 0; i < extNibbles.length; i++) {
                        if (keyNibbles[depth + i] != extNibbles[i]) {
                            pathMismatch = true;
                            mismatchPosition = i;
                            break;
                        }
                    }
                }

                if (pathMismatch) {
                    // Extension path diverges from query path
                    // This should not happen with temporary insertion strategy, but handle gracefully
                    int matched = mismatchPosition < 0 ? 0 : mismatchPosition;
                    if (matched >= extNibbles.length) {
                        return MerklePatriciaProof.nonInclusionMissingBranch(steps);
                    }

                    byte[] childCommit = extension.getChild();
                    if (childCommit == null || childCommit.length == 0) {
                        System.out.println("DEBUG Extension child missing during mismatch, returning nonInclusionMissingBranch");
                        return MerklePatriciaProof.nonInclusionMissingBranch(steps);
                    }

                    int[] skipPrefix = matched == 0 ? new int[0] : Arrays.copyOf(extNibbles, matched);
                    int neighborNibble = extNibbles[matched];
                    int[] suffixNibbles = matched + 1 < extNibbles.length
                            ? Arrays.copyOfRange(extNibbles, matched + 1, extNibbles.length)
                            : new int[0];

                    NibblePath forkSkip = concat(pendingPrefix, skipPrefix);
                    NibblePath forkSuffix = NibblePath.of(suffixNibbles);
                    byte[] flattenedCommit = persistence.computeExtensionCommitForProof(extension);

                    steps.add(new MerklePatriciaProof.ForkStep(forkSkip, neighborNibble, forkSuffix, flattenedCommit));
                    return MerklePatriciaProof.nonInclusionMissingBranch(steps);
                }

                // Path matches - continue traversal
                if (steps.isEmpty()) {
                    pendingPrefix = concat(pendingPrefix, extNibbles);
                } else {
                    MerklePatriciaProof.Step lastStep = steps.remove(steps.size() - 1);
                    if (!(lastStep instanceof MerklePatriciaProof.BranchStep)) {
                        throw new IllegalStateException("Expected BranchStep before extension but found " + lastStep.getClass().getSimpleName());
                    }
                    MerklePatriciaProof.BranchStep last = (MerklePatriciaProof.BranchStep) lastStep;
                    NibblePath extended = concat(last.skipPath(), extNibbles);
                    steps.add(new MerklePatriciaProof.BranchStep(extended, last.childHashes(), last.childIndex(), last.branchValueHash()));
                }

                for (int nibble : extNibbles) {
                    traversedNibbles.add(nibble);
                }
                depth += extNibbles.length;
                byte[] childCommit = extension.getChild();
                if (childCommit == null || childCommit.length == 0) {
                    return MerklePatriciaProof.nonInclusionMissingBranch(steps);
                }
                currentHash = childCommit;
                continue;
            }

            if (node instanceof LeafNode) {
                LeafNode leaf = (LeafNode) node;
                Nibbles.HP hp = Nibbles.unpackHP(leaf.getHp());
                int[] leafNibbles = hp.nibbles;
                if (depth + leafNibbles.length == keyNibbles.length) {
                    boolean matches = true;
                    for (int i = 0; i < leafNibbles.length; i++) {
                        if (keyNibbles[depth + i] != leafNibbles[i]) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        byte[] value = leaf.getValue();
                        byte[] valueHash = hashFn.digest(value);
                        NibblePath suffix = NibblePath.of(leafNibbles);
                        return MerklePatriciaProof.inclusion(steps, value, valueHash, suffix);
                    }
                }

                // Reconstruct the conflicting key hash from the actual path traversed plus the leaf's nibbles
                int[] conflictingPathNibbles = new int[traversedNibbles.size() + leafNibbles.length];
                for (int i = 0; i < traversedNibbles.size(); i++) {
                    conflictingPathNibbles[i] = traversedNibbles.get(i);
                }
                System.arraycopy(leafNibbles, 0, conflictingPathNibbles, traversedNibbles.size(), leafNibbles.length);
                byte[] conflictingKeyHash = Nibbles.fromNibbles(conflictingPathNibbles);

                byte[] conflictingValueHash = hashFn.digest(leaf.getValue());
                NibblePath conflictingSuffix = NibblePath.of(leafNibbles);
                return MerklePatriciaProof.nonInclusionDifferentLeaf(steps, conflictingKeyHash, conflictingValueHash, conflictingSuffix);
            }

            throw new IllegalStateException("Unsupported node type " + node.getClass().getSimpleName());
        }
    }

    /**
     * Returns a mode-bound wire proof (MPF in default mode).
     */
    public Optional<byte[]> getProofWire(byte[] key) {
        Objects.requireNonNull(key, "key");
        if ("CLASSIC".equalsIgnoreCase(mode.name())) {
            return Optional.of(buildClassicProofWire(key));
        } else {
            // Check if we should use temporary insertion strategy for MPF
            MerklePatriciaProof proof = getProof(key);
            byte[] wire = mode.proofCodec().toWire(proof, key, hashFn, commitments);
            return Optional.of(wire);
        }
    }

    private byte[] buildClassicProofWire(byte[] key) {
        java.util.List<byte[]> nodes = new java.util.ArrayList<>();
        if (this.root == null) {
            // empty proof: encode empty array
            return com.bloxbean.cardano.vds.mpt.mode.ClassicProofCodec.encodeNodeList(nodes);
        }
        int[] keyNibbles = Nibbles.toNibbles(key);
        byte[] currentHash = this.root;
        int depth = 0;
        while (true) {
            Node node = persistence.load(NodeHash.of(currentHash));
            if (node == null) {
                break;
            }
            nodes.add(node.encode());
            if (node instanceof BranchNode) {
                BranchNode branch = (BranchNode) node;
                if (depth >= keyNibbles.length) {
                    break; // terminal at branch
                }
                int childIndex = keyNibbles[depth];
                byte[] childCommit = branch.getChild(childIndex);
                if (childCommit == null || childCommit.length == 0) {
                    break; // missing branch
                }
                currentHash = childCommit;
                depth++;
                continue;
            }
            if (node instanceof ExtensionNode) {
                ExtensionNode extension = (ExtensionNode) node;
                Nibbles.HP hp = Nibbles.unpackHP(extension.getHp());
                int[] extNibbles = hp.nibbles;
                if (depth + extNibbles.length > keyNibbles.length) {
                    break; // mismatch (path longer than key remainder)
                }
                boolean match = true;
                for (int i = 0; i < extNibbles.length; i++) {
                    if (keyNibbles[depth + i] != extNibbles[i]) {
                        match = false;
                        break;
                    }
                }
                if (!match) {
                    break; // mismatch in extension path
                }
                depth += extNibbles.length;
                byte[] child = extension.getChild();
                if (child == null || child.length == 0) {
                    break;
                }
                currentHash = child;
                continue;
            }
            if (node instanceof LeafNode) {
                // terminal leaf (either inclusion or conflicting)
                break;
            }
            throw new IllegalStateException("Unsupported node type " + node.getClass().getSimpleName());
        }
        return com.bloxbean.cardano.vds.mpt.mode.ClassicProofCodec.encodeNodeList(nodes);
    }

    /**
     * Verifies a mode-bound wire proof against the supplied root/key/value.
     */
    public boolean verifyProofWire(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including, byte[] wire) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(wire, "wire");

        if (!"CLASSIC".equalsIgnoreCase(mode.name())) {
            // Delegate to MPF verifier (keeps existing behavior and golden compatibility)
            try {
                return com.bloxbean.cardano.vds.mpt.mpf.MpfProofVerifier
                        .verify(expectedRoot, key, valueOrNull, including, wire, hashFn, commitments);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Proof/wire format mismatch for mode " + mode.name(), e);
            }
        }

        // Classic node-encoding proof: CBOR array of ByteStrings, each a CBOR-encoded node along the path
        java.util.List<byte[]> nodes = decodeClassicWire(wire);

        // Empty trie handling
        if (expectedRoot == null || expectedRoot.length == 0) {
            return !including; // empty trie cannot include any key
        }

        if (nodes.isEmpty())
            throw new IllegalArgumentException("Classic proof wire must contain at least one node for non-empty trie");

        // Decode nodes to typed Nodes once for reuse
        java.util.List<Node> typed = new java.util.ArrayList<>(nodes.size());
        for (byte[] enc : nodes) typed.add(TrieEncoding.decode(enc));

        // Root must match the flattened commit of the first node chain
        FlattenResult rootFr = flattenCommit(typed, 0);
        if (!java.util.Arrays.equals(expectedRoot, rootFr.commit)) return false;

        // Strict consumption: the flattened chain for root must not exceed provided nodes
        if (rootFr.consumed > typed.size()) return false;

        // Validate traversal semantics against the key and inclusion flag/value
        int[] keyNibbles = com.bloxbean.cardano.vds.core.nibbles.Nibbles.toNibbles(key);
        int pos = 0;
        int idx = 0;
        while (idx < typed.size()) {
            Node node = typed.get(idx);
            if (node instanceof BranchNode) {
                BranchNode br = (BranchNode) node;
                byte[][] children = br.getChildren();
                byte[] branchValue = br.getValue();

                if (pos >= keyNibbles.length) {
                    if (including) {
                        if (branchValue == null) return false;
                        return java.util.Arrays.equals(branchValue, valueOrNull);
                    } else {
                        return branchValue == null; // non-inclusion (no value at branch)
                    }
                }

                int childIndex = keyNibbles[pos];
                byte[] childHash = (childIndex >= 0 && childIndex < 16) ? children[childIndex] : null;
                if (childHash == null || childHash.length == 0) {
                    return !including; // missing branch proves non-inclusion
                }

                // Child subtree must match the flattened commit of the subsequent chain
                if (idx + 1 >= typed.size()) return false;
                FlattenResult fr = flattenCommit(typed, idx + 1);
                if (!java.util.Arrays.equals(childHash, fr.commit)) return false;
                idx = idx + fr.consumed;
                pos++;
                continue;
            }

            if (node instanceof ExtensionNode) {
                ExtensionNode en = (ExtensionNode) node;
                com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP hp = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(en.getHp());
                int[] ext = hp.nibbles;
                if (pos + ext.length > keyNibbles.length) return !including; // mismatch ==> non-inclusion
                for (int i = 0; i < ext.length; i++) {
                    if (ext[i] != keyNibbles[pos + i]) return !including;
                }
                pos += ext.length;
                idx += 1;
                continue;
            }

            if (node instanceof LeafNode) {
                LeafNode leaf = (LeafNode) node;
                com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP hp = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(leaf.getHp());
                int[] suf = hp.nibbles;
                if (pos + suf.length == keyNibbles.length) {
                    boolean eq = true;
                    for (int i = 0; i < suf.length; i++) {
                        if (suf[i] != keyNibbles[pos + i]) {
                            eq = false;
                            break;
                        }
                    }
                    if (eq) {
                        if (!including) return false;
                        return java.util.Arrays.equals(leaf.getValue(), valueOrNull);
                    }
                }
                // conflicting leaf proves non-inclusion
                return !including;
            }

            // Unknown node
            return false;
        }

        return false; // fell through without terminal decision
    }

    private static final class FlattenResult {
        final byte[] commit;
        final int consumed;

        FlattenResult(byte[] c, int k) {
            commit = c;
            consumed = k;
        }
    }

    // Computes the flattened commitment for the subtree starting at index `start`.
    private FlattenResult flattenCommit(java.util.List<Node> nodes, int start) {
        if (start >= nodes.size()) return new FlattenResult(commitments.nullHash(), 0);
        Node node = nodes.get(start);
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP hp = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(leaf.getHp());
            com.bloxbean.cardano.vds.core.NibblePath suf = com.bloxbean.cardano.vds.core.NibblePath.of(hp.nibbles);
            byte[] vh = hashFn.digest(leaf.getValue());
            return new FlattenResult(commitments.commitLeaf(suf, vh), 1);
        }
        if (node instanceof BranchNode) {
            BranchNode br = (BranchNode) node;
            byte[][] children = new byte[16][];
            byte[][] from = br.getChildren();
            for (int i = 0; i < 16; i++)
                children[i] = (i < from.length && from[i] != null && from[i].length > 0) ? from[i] : null;
            byte[] value = br.getValue();
            byte[] valueHash = value == null ? null : hashFn.digest(value);
            return new FlattenResult(commitments.commitBranch(com.bloxbean.cardano.vds.core.NibblePath.EMPTY, children, valueHash), 1);
        }
        if (node instanceof ExtensionNode) {
            // Accumulate all extension prefixes
            int idx = start;
            com.bloxbean.cardano.vds.core.NibblePath acc = com.bloxbean.cardano.vds.core.NibblePath.EMPTY;
            while (idx < nodes.size() && nodes.get(idx) instanceof ExtensionNode) {
                ExtensionNode en = (ExtensionNode) nodes.get(idx);
                com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP hp = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(en.getHp());
                acc = acc.concat(com.bloxbean.cardano.vds.core.NibblePath.of(hp.nibbles));
                idx++;
            }
            if (idx >= nodes.size())
                return new FlattenResult(commitments.commitExtension(acc, commitments.nullHash()), idx - start);
            Node child = nodes.get(idx);
            if (child instanceof BranchNode) {
                BranchNode br = (BranchNode) child;
                byte[][] children = new byte[16][];
                byte[][] from = br.getChildren();
                for (int i = 0; i < 16; i++)
                    children[i] = (i < from.length && from[i] != null && from[i].length > 0) ? from[i] : null;
                byte[] value = br.getValue();
                byte[] valueHash = value == null ? null : hashFn.digest(value);
                byte[] c = commitments.commitBranch(acc, children, valueHash);
                return new FlattenResult(c, (idx - start) + 1);
            } else if (child instanceof LeafNode) {
                LeafNode lf = (LeafNode) child;
                com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP lhp = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(lf.getHp());
                com.bloxbean.cardano.vds.core.NibblePath suf = acc.concat(com.bloxbean.cardano.vds.core.NibblePath.of(lhp.nibbles));
                byte[] c = commitments.commitLeaf(suf, hashFn.digest(lf.getValue()));
                return new FlattenResult(c, (idx - start) + 1);
            } else {
                // path ends with extension only
                byte[] c = commitments.commitExtension(acc, commitments.nullHash());
                return new FlattenResult(c, (idx - start));
            }
        }
        throw new IllegalStateException("Unsupported node type " + node.getClass().getSimpleName());
    }

    private static java.util.List<byte[]> decodeClassicWire(byte[] wire) {
        try {
            java.util.List<co.nstant.in.cbor.model.DataItem> items = new co.nstant.in.cbor.CborDecoder(new java.io.ByteArrayInputStream(wire)).decode();
            if (items.isEmpty() || !(items.get(0) instanceof co.nstant.in.cbor.model.Array)) {
                throw new IllegalArgumentException("Classic proof wire must be an array of ByteStrings");
            }
            co.nstant.in.cbor.model.Array arr = (co.nstant.in.cbor.model.Array) items.get(0);
            java.util.List<byte[]> nodes = new java.util.ArrayList<>(arr.getDataItems().size());
            for (co.nstant.in.cbor.model.DataItem di : arr.getDataItems()) {
                if (!(di instanceof co.nstant.in.cbor.model.ByteString)) {
                    throw new IllegalArgumentException("Classic step must be a ByteString containing node CBOR");
                }
                nodes.add(((co.nstant.in.cbor.model.ByteString) di).getBytes());
            }
            return nodes;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode Classic proof wire", e);
        }
    }

    /**
     * DEPRECATED: Use putAtNew() with visitor pattern instead.
     * This method delegates to the new implementation for backward compatibility.
     *
     * @param nodeHash   the hash of the current node (null for non-existent)
     * @param keyNibbles the full key as nibbles
     * @param position   the current position in the key
     * @param value      the value to insert
     * @return the hash of the modified subtree
     */
    private byte[] putAt(byte[] nodeHash, int[] keyNibbles, int position, byte[] value) {
        NodeHash result = putAtNew(nodeHash, keyNibbles, position, value);
        return result != null ? result.toBytes() : null;
    }

    /**
     * DEPRECATED: Use NodeSplitter.splitLeafNode() with visitor pattern instead.
     * This method delegates to the new implementation for backward compatibility.
     *
     * @param existingLeaf       the existing leaf node to split
     * @param existingHash       the hash of the existing leaf
     * @param keyNibbles         the new key being inserted
     * @param position           the current position in the key
     * @param value              the new value to insert
     * @param commonPrefixLength the length of the common prefix
     * @return the hash of the new subtree structure
     */
    private byte[] splitAndInsert(LeafNode existingLeaf, byte[] existingHash, int[] keyNibbles, int position, byte[] value, int commonPrefixLength) {
        int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);
        NodeHash result = NodeSplitter.splitLeafNode(persistence, existingLeaf, remainingKey, value, commonPrefixLength);
        return result.toBytes();
    }



    /**
     * Recursively scans the trie for keys matching a prefix.
     *
     * @param nodeHash      the hash of the current node
     * @param prefixNibbles the prefix to match as nibbles
     * @param position      the current position in the traversal
     * @param limit         the maximum number of results
     * @param accumulator   the accumulated path nibbles
     * @param output        the list to add matching entries to
     */
    private void scan(byte[] nodeHash, int[] prefixNibbles, int position, int limit, Deque<Integer> accumulator, List<MerklePatriciaTrie.Entry> output) {
        if (nodeHash == null || output.size() >= limit) return;
        byte[] nodeData = store.get(nodeHash);
        if (nodeData == null) {
            return; // node not found, skip
        }
        Node node = TrieEncoding.decode(nodeData);
        if (node instanceof LeafNode) {
            LeafNode ln = (LeafNode) node;
            int[] nibbles = Nibbles.unpackHP(ln.getHp()).nibbles;
            for (int nib : nibbles) accumulator.addLast(nib);
            // check prefix match
            int[] current = toArray(accumulator);
            if (startsWith(current, prefixNibbles)) {
                output.add(new MerklePatriciaTrie.Entry(toBytes(current), ln.getValue()));
            }
            for (int i = 0; i < nibbles.length; i++) accumulator.removeLast();
        } else if (node instanceof ExtensionNode) {
            ExtensionNode en = (ExtensionNode) node;
            int[] enNibs = Nibbles.unpackHP(en.getHp()).nibbles;
            for (int nib : enNibs) accumulator.addLast(nib);
            scan(en.getChild(), prefixNibbles, position + enNibs.length, limit, accumulator, output);
            for (int i = 0; i < enNibs.length; i++) accumulator.removeLast();
        } else {
            BranchNode bn = (BranchNode) node;
            byte[] branchValue = bn.getValue();
            if (branchValue != null) {
                int[] current = toArray(accumulator);
                if (startsWith(current, prefixNibbles))
                    output.add(new MerklePatriciaTrie.Entry(toBytes(current), branchValue));
                if (output.size() >= limit) return;
            }
            for (int i = 0; i < 16 && output.size() < limit; i++) {
                accumulator.addLast(i);
                byte[] childHash = bn.getChild(i);
                scan(childHash, prefixNibbles, position + 1, limit, accumulator, output);
                accumulator.removeLast();
            }
        }
    }

    /**
     * Utility methods for array operations.
     */

    /**
     * Creates a slice of an integer array.
     *
     * @param array     the source array
     * @param fromIndex the starting index (inclusive)
     * @param toIndex   the ending index (exclusive)
     * @return a new array containing the specified slice
     */
    private static int[] slice(int[] array, int fromIndex, int toIndex) {
        int length = Math.max(0, toIndex - fromIndex);
        int[] result = new int[length];
        for (int i = 0; i < length; i++) result[i] = array[fromIndex + i];
        return result;
    }

    /**
     * Concatenates two integer arrays.
     *
     * @param first  the first array
     * @param second the second array
     * @return a new array containing elements from both arrays
     */
    private static int[] concat(int[] first, int[] second) {
        int[] result = new int[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Checks if an array starts with a given prefix.
     *
     * @param fullArray the full array to check
     * @param prefix    the prefix to match
     * @return true if fullArray starts with prefix
     */
    private static boolean startsWith(int[] fullArray, int[] prefix) {
        if (prefix.length > fullArray.length) return false;
        for (int i = 0; i < prefix.length; i++) if (fullArray[i] != prefix[i]) return false;
        return true;
    }

    /**
     * Converts a deque of integers to an array.
     *
     * @param deque the deque to convert
     * @return an array containing the deque elements in order
     */
    private static int[] toArray(Deque<Integer> deque) {
        int[] result = new int[deque.size()];
        int index = 0;
        for (int value : deque) result[index++] = value;
        return result;
    }

    /**
     * Converts nibbles array to bytes.
     *
     * @param nibbles the nibbles to convert
     * @return the corresponding byte array
     */
    private static byte[] toBytes(int[] nibbles) {
        return Nibbles.fromNibbles(nibbles);
    }
    // END OF COMMENTED OUT METHODS

    // Note: getAt, splitExtensionAndInsert, and deleteAt methods are defined
    // in the commented section above. These delegation methods were removed
    // to avoid duplicate method definitions during the refactoring transition.

    // ======================================
    // New Visitor-Based Implementation Methods
    // ======================================

    /**
     * New visitor-based implementation for inserting values using immutable nodes.
     *
     * @param nodeHash   the hash of the current node (null for non-existent)
     * @param keyNibbles the full key as nibbles
     * @param position   the current position in the key
     * @param value      the value to insert
     * @return the hash of the modified subtree
     */
    private NodeHash putAtNew(byte[] nodeHash, int[] keyNibbles, int position, byte[] value) {
        if (nodeHash == null) {
            // Create new leaf node
            int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
            byte[] hp = Nibbles.packHP(true, remainingNibbles);
            LeafNode leaf = LeafNode.of(hp, value);
            return persistence.persist(leaf);
        }

        Node node = persistence.load(NodeHash.of(nodeHash));
        if (node == null) {
            // Missing node - create new leaf
            int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
            byte[] hp = Nibbles.packHP(true, remainingNibbles);
            LeafNode leaf = LeafNode.of(hp, value);
            return persistence.persist(leaf);
        }

        PutOperationVisitor putVisitor = new PutOperationVisitor(persistence, keyNibbles, position, value);
        return node.accept(putVisitor);
    }

    /**
     * New visitor-based implementation for retrieving values using immutable nodes.
     *
     * @param nodeHash   the hash of the current node (null for non-existent)
     * @param keyNibbles the full key as nibbles
     * @param position   the current position in the key
     * @return the value if found, null otherwise
     */
    private byte[] getAtNew(byte[] nodeHash, int[] keyNibbles, int position) {
        if (nodeHash == null) {
            return null; // No node at this path
        }

        Node node = persistence.load(NodeHash.of(nodeHash));
        if (node == null) {
            return null; // Missing node
        }

        GetOperationVisitor getVisitor = new GetOperationVisitor(persistence, keyNibbles, position);
        return node.accept(getVisitor);
    }

    /**
     * New visitor-based implementation for deleting values using immutable nodes.
     *
     * @param nodeHash   the hash of the current node (null for non-existent)
     * @param keyNibbles the full key as nibbles
     * @param position   the current position in the key
     * @return the hash of the modified subtree (null if deleted)
     */
    private NodeHash deleteAtNew(byte[] nodeHash, int[] keyNibbles, int position) {
        if (nodeHash == null) {
            return null; // No node at this path
        }

        Node node = persistence.load(NodeHash.of(nodeHash));
        if (node == null) {
            return null; // Missing node - nothing to delete
        }

        DeleteOperationVisitor deleteVisitor = new DeleteOperationVisitor(persistence, keyNibbles, position);
        return node.accept(deleteVisitor);
    }

    private byte[][] materializeChildHashes(BranchNode branch) {
        byte[][] childHashes = new byte[16][];
        for (int i = 0; i < 16; i++) {
            byte[] child = branch.getChild(i);
            childHashes[i] = (child == null || child.length == 0) ? null : child;
        }
        return childHashes;
    }

    private static NibblePath concat(NibblePath base, int[] extras) {
        if (extras == null || extras.length == 0) {
            return base;
        }
        int[] baseNibbles = base.getNibbles();
        int[] combined = Arrays.copyOf(baseNibbles, baseNibbles.length + extras.length);
        System.arraycopy(extras, 0, combined, baseNibbles.length, extras.length);
        return NibblePath.of(combined);
    }

    /**
     * Find any leaf's key hash in the given subtree. Used for non-inclusion proofs when
     * an extension node diverges from the query path.
     * @param node the root of the subtree to search
     * @param traversedNibbles the nibbles traversed so far (including extension nibbles)
     * @return the full key hash of any leaf found, or null if no leaf exists
     */
    private byte[] findAnyLeafKeyHash(Node node, List<Integer> traversedNibbles) {
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            Nibbles.HP hp = Nibbles.unpackHP(leaf.getHp());
            int[] leafNibbles = hp.nibbles;

            // Reconstruct full key path: traversed nibbles + leaf nibbles
            int[] fullPath = new int[traversedNibbles.size() + leafNibbles.length];
            for (int i = 0; i < traversedNibbles.size(); i++) {
                fullPath[i] = traversedNibbles.get(i);
            }
            System.arraycopy(leafNibbles, 0, fullPath, traversedNibbles.size(), leafNibbles.length);

            return Nibbles.fromNibbles(fullPath);
        }

        if (node instanceof ExtensionNode) {
            ExtensionNode ext = (ExtensionNode) node;
            byte[] childHash = ext.getChild();
            if (childHash != null && childHash.length > 0) {
                Node child = persistence.load(NodeHash.of(childHash));
                if (child != null) {
                    Nibbles.HP hp = Nibbles.unpackHP(ext.getHp());
                    List<Integer> extendedPath = new ArrayList<>(traversedNibbles);
                    for (int nibble : hp.nibbles) {
                        extendedPath.add(nibble);
                    }
                    return findAnyLeafKeyHash(child, extendedPath);
                }
            }
        }

        if (node instanceof BranchNode) {
            BranchNode branch = (BranchNode) node;
            byte[][] childHashes = materializeChildHashes(branch);
            for (int i = 0; i < childHashes.length; i++) {
                if (childHashes[i] != null) {
                    Node child = persistence.load(NodeHash.of(childHashes[i]));
                    if (child != null) {
                        List<Integer> branchPath = new ArrayList<>(traversedNibbles);
                        branchPath.add(i);
                        byte[] result = findAnyLeafKeyHash(child, branchPath);
                        if (result != null) return result;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Find any leaf's value hash in the given subtree. Used for non-inclusion proofs.
     */
    private byte[] findAnyLeafValueHash(Node node) {
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            return leaf.getValue() != null ? hashFn.digest(leaf.getValue()) : null;
        }

        if (node instanceof ExtensionNode) {
            ExtensionNode ext = (ExtensionNode) node;
            byte[] childHash = ext.getChild();
            if (childHash != null && childHash.length > 0) {
                Node child = persistence.load(NodeHash.of(childHash));
                if (child != null) {
                    return findAnyLeafValueHash(child);
                }
            }
        }

        if (node instanceof BranchNode) {
            BranchNode branch = (BranchNode) node;
            byte[][] childHashes = materializeChildHashes(branch);
            for (int i = 0; i < childHashes.length; i++) {
                if (childHashes[i] != null) {
                    Node child = persistence.load(NodeHash.of(childHashes[i]));
                    if (child != null) {
                        byte[] result = findAnyLeafValueHash(child);
                        if (result != null) return result;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Find the suffix nibbles from the current position to any leaf in the subtree.
     * This includes all extension and leaf nibbles from the starting node.
     *
     * @param node the node to start searching from
     * @param accumulatedNibbles the nibbles accumulated so far (starting empty)
     * @return the nibble array from current position to leaf, or null if no leaf found
     */
    private int[] findLeafSuffixNibbles(Node node, List<Integer> accumulatedNibbles) {
        if (node instanceof LeafNode) {
            LeafNode leaf = (LeafNode) node;
            Nibbles.HP hp = Nibbles.unpackHP(leaf.getHp());
            int[] leafNibbles = hp.nibbles;

            // Return accumulated + leaf nibbles
            int[] result = new int[accumulatedNibbles.size() + leafNibbles.length];
            for (int i = 0; i < accumulatedNibbles.size(); i++) {
                result[i] = accumulatedNibbles.get(i);
            }
            System.arraycopy(leafNibbles, 0, result, accumulatedNibbles.size(), leafNibbles.length);
            return result;
        }

        if (node instanceof ExtensionNode) {
            ExtensionNode ext = (ExtensionNode) node;
            byte[] childHash = ext.getChild();
            if (childHash != null && childHash.length > 0) {
                Node child = persistence.load(NodeHash.of(childHash));
                if (child != null) {
                    Nibbles.HP hp = Nibbles.unpackHP(ext.getHp());
                    List<Integer> extendedPath = new ArrayList<>(accumulatedNibbles);
                    for (int nibble : hp.nibbles) {
                        extendedPath.add(nibble);
                    }
                    return findLeafSuffixNibbles(child, extendedPath);
                }
            }
        }

        if (node instanceof BranchNode) {
            BranchNode branch = (BranchNode) node;
            byte[][] childHashes = materializeChildHashes(branch);
            for (int i = 0; i < childHashes.length; i++) {
                if (childHashes[i] != null) {
                    Node child = persistence.load(NodeHash.of(childHashes[i]));
                    if (child != null) {
                        List<Integer> branchPath = new ArrayList<>(accumulatedNibbles);
                        branchPath.add(i);
                        int[] result = findLeafSuffixNibbles(child, branchPath);
                        if (result != null) return result;
                    }
                }
            }
        }

        return null;
    }
}
