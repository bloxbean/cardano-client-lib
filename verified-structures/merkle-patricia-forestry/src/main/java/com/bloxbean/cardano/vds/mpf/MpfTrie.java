package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.mpf.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpf.proof.ProofFormatter;
import com.bloxbean.cardano.vds.mpf.proof.ProofSerializer;
import com.bloxbean.cardano.vds.mpf.proof.TraversalProof;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Merkle Patricia Forestry Trie - the primary API for Cardano developers.
 *
 * <p><b>This is THE recommended class for Cardano smart contract development.</b></p>
 *
 * <p>This implementation is fully compatible with Cardano's
 * <a href="https://github.com/aiken-lang/merkle-patricia-forestry">merkle-patricia-forestry</a>
 * library in Aiken. All keys are automatically hashed using Blake2b-256 before storage,
 * matching Aiken's behavior exactly.</p>
 *
 * <p><b>Quick Start:</b></p>
 * <pre>{@code
 * // Simplest usage: Blake2b-256 + MPF mode (default)
 * MpfTrie trie = new MpfTrie(store);
 *
 * // Store data
 * trie.put("account123".getBytes(), accountData);
 *
 * // Get root hash for on-chain verification
 * byte[] rootHash = trie.getRootHash();
 *
 * // Generate proof for Aiken validator
 * Optional<ListPlutusData> proof = trie.getProofPlutusData("account123".getBytes());
 * }</pre>
 *
 * <p><b>With existing root:</b></p>
 * <pre>{@code
 * MpfTrie trie = new MpfTrie(store, existingRoot);
 * }</pre>
 *
 * <p><b>Why MpfTrie Guarantees Aiken Compatibility:</b></p>
 * <ul>
 *   <li>All keys hashed to exactly 32 bytes (64 nibbles) using Blake2b-256</li>
 *   <li>All keys terminate at the same depth (64 levels)</li>
 *   <li>No key can be a prefix of another at termination</li>
 *   <li>All values stored at leaves only (no branch values)</li>
 *   <li>Uses MPF commitment scheme (identical to Aiken's)</li>
 * </ul>
 *
 * <p><b>When to Use MpfTrie:</b></p>
 * <ul>
 *   <li>Building Cardano smart contracts or dApps</li>
 *   <li>Need compatibility with Aiken merkle-patricia-forestry</li>
 *   <li>Generating proofs for on-chain verification</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. External synchronization
 * is required for concurrent access.</p>
 *
 * @see <a href="https://github.com/aiken-lang/merkle-patricia-forestry">Aiken Merkle Patricia Forestry</a>
 * @since 0.8.0
 */
public final class MpfTrie {
    /**
     * The underlying trie implementation.
     */
    private final Impl impl;

    /**
     * The hash function used to hash keys before storage.
     */
    private final HashFunction hashFn;


    /**
     * Creates a new MpfTrie optimized for Cardano with Blake2b-256 hashing and MPF mode.
     *
     * <p>This is the simplest and recommended constructor for Cardano developers.
     * Uses Blake2b-256 for key hashing (as required by Cardano/Aiken) and MPF
     * commitment scheme for on-chain compatibility.</p>
     *
     * <p>Original keys are always stored in leaf nodes for debugging and introspection
     * via {@link #getAllEntries()}. Keys are NOT used in commitment calculations,
     * maintaining full Aiken compatibility.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @throws NullPointerException if store is null
     */
    public MpfTrie(NodeStore store) {
        this(store, null);
    }

    /**
     * Creates an MpfTrie optimized for Cardano with existing root, using Blake2b-256 and MPF mode.
     *
     * <p>Use this constructor to load an existing trie from storage by providing
     * its root hash. This is the recommended constructor for resuming from a
     * previously committed state.</p>
     *
     * <p>Original keys are always stored in leaf nodes for debugging and introspection
     * via {@link #getAllEntries()}. Keys are NOT used in commitment calculations,
     * maintaining full Aiken compatibility.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @param root  the root hash of an existing trie, or null for empty trie
     * @throws NullPointerException if store is null
     */
    public MpfTrie(NodeStore store, byte[] root) {
        this.hashFn = Blake2b256::digest;
        this.impl = new Impl(store, hashFn, root, new MpfCommitmentScheme(hashFn));
    }

    /**
     * Sets the root hash of this trie.
     *
     * <p>This method allows switching to a different trie state by changing the root.
     * Useful for reverting to previous states or switching between different tries
     * that share the same storage backend.</p>
     *
     * @param root the new root hash, or null to reset to empty trie
     */
    public void setRootHash(byte[] root) {
        impl.setRootHash(root);
    }

    /**
     * Returns the current root hash of the trie.
     *
     * <p>The root hash uniquely identifies the entire state of the trie. This is
     * the value you would commit on-chain for verification against proofs.</p>
     *
     * @return the root hash as a byte array, or null if the trie is empty
     */
    public byte[] getRootHash() {
        return impl.getRootHash();
    }

    /**
     * Stores a key-value pair in the trie.
     *
     * <p>The key is automatically hashed using Blake2b-256 before storage,
     * ensuring uniform distribution and Aiken compatibility.</p>
     *
     * <p>The original key is stored in the leaf node for debugging and introspection
     * via {@link #getAllEntries()}. Keys are NOT used in commitment calculations.</p>
     *
     * @param key   the original key (will be hashed before storage)
     * @param value the value to store (must not be null)
     * @throws IllegalArgumentException if value is null
     * @throws NullPointerException     if key is null
     */
    public void put(byte[] key, byte[] value) {
        impl.put(hashFn.digest(key), value, key);
    }

    /**
     * Retrieves a value by its original key.
     *
     * <p>The key is hashed using the same hash function used during storage.</p>
     *
     * @param key the original key to look up
     * @return the value as a byte array, or null if the key doesn't exist
     * @throws NullPointerException if key is null
     */
    public byte[] get(byte[] key) {
        return impl.get(hashFn.digest(key));
    }

    /**
     * Removes a key-value pair from the trie.
     *
     * <p>The key is hashed before deletion. If the original key doesn't exist,
     * this operation has no effect.</p>
     *
     * @param key the original key to delete
     * @throws NullPointerException if key is null
     */
    public void delete(byte[] key) {
        impl.delete(hashFn.digest(key));
    }

    /**
     * Builds a mode-bound wire proof for the given key.
     *
     * <p>The key is automatically hashed before proof generation.</p>
     *
     * @param key the original key to generate proof for
     * @return the wire-format proof bytes, or empty if proof cannot be generated
     */
    public Optional<byte[]> getProofWire(byte[] key) {
        return impl.getProofWire(hashFn.digest(key));
    }

    /**
     * Generate an MPF inclusion/exclusion proof in PlutusData format suitable for
     * passing directly to Aiken validators.
     *
     * <p>This is the primary method for generating proofs for on-chain verification.
     * It combines proof generation with PlutusData formatting in a single call.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = new MpfTrie(store);
     * trie.put("apple".getBytes(), "red".getBytes());
     *
     * // Get proof for on-chain validator
     * Optional<ListPlutusData> proof = trie.getProofPlutusData("apple".getBytes());
     * proof.ifPresent(p -> {
     *     DataItem di = p.serialize();
     *     byte[] proofCbor = CborSerializationUtil.serialize(di);
     *     // Submit proofCbor as redeemer/datum to your Aiken validator
     * });
     * }</pre>
     *
     * @param key the query key (will be hashed before proof generation)
     * @return PlutusData representation of the proof matching Aiken's ProofStep type,
     *         or empty if no proof can be generated
     * @see ProofFormatter#toPlutusData(byte[])
     */
    public Optional<ListPlutusData> getProofPlutusData(byte[] key) {
        return getProofWire(key)
                .map(ProofFormatter::toPlutusData);
    }

    /**
     * Verifies a wire-format proof against an expected root.
     *
     * <p>This method validates that the proof correctly demonstrates either the
     * inclusion or non-inclusion of a key-value pair in the trie.</p>
     *
     * @param expectedRoot the expected trie root hash to verify against
     * @param key          the original key (will be hashed for verification)
     * @param valueOrNull  the expected value, or null for non-inclusion proof
     * @param including    {@code true} for inclusion proof (verifies key exists with given value),
     *                     {@code false} for non-inclusion proof (verifies key does not exist)
     * @param wire         the wire-format proof bytes as produced by {@link #getProofWire(byte[])}
     * @return {@code true} if the proof is valid, {@code false} otherwise
     */
    public boolean verifyProofWire(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including, byte[] wire) {
        return impl.verifyProofWire(expectedRoot, key, valueOrNull, including, wire);
    }

    /**
     * Returns all key-value entries stored in the trie.
     *
     * <p>This method traverses the entire trie and returns all stored entries.
     * Each entry contains:
     * <ul>
     *   <li>{@link Entry#getPath()} - the Blake2b-256 hashed path</li>
     *   <li>{@link Entry#getKey()} - the original unhashed key</li>
     *   <li>{@link Entry#getValue()} - the stored value</li>
     * </ul>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = new MpfTrie(store);
     * trie.put("key1".getBytes(), "value1".getBytes());
     * trie.put("key2".getBytes(), "value2".getBytes());
     *
     * List<MpfTrie.Entry> entries = trie.getAllEntries();
     * assertEquals(2, entries.size());
     * // Access original keys
     * for (Entry e : entries) {
     *     System.out.println(new String(e.getKey())); // "key1" or "key2"
     * }
     * }</pre>
     *
     * @return list of all entries in the trie, empty list if trie is empty
     * @since 0.8.0
     */
    public List<Entry> getAllEntries() {
        return impl.getAllEntries();
    }

    /**
     * Returns up to the specified number of entries from the trie.
     *
     * <p>This method is useful for large tries where loading all entries would be
     * memory-intensive. Entries are returned in depth-first traversal order.</p>
     *
     * <p>Each entry contains:
     * <ul>
     *   <li>{@link Entry#getPath()} - the Blake2b-256 hashed path</li>
     *   <li>{@link Entry#getKey()} - the original unhashed key</li>
     *   <li>{@link Entry#getValue()} - the stored value</li>
     * </ul>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * // Get first 100 entries for sampling
     * List<MpfTrie.Entry> sample = trie.getEntries(100);
     *
     * // Process entries in batches
     * int batchSize = 1000;
     * List<MpfTrie.Entry> batch = trie.getEntries(batchSize);
     * }</pre>
     *
     * @param limit maximum number of entries to return (must be positive)
     * @return list of up to limit entries, empty list if trie is empty
     * @throws IllegalArgumentException if limit is not positive
     * @since 0.8.0
     */
    public List<Entry> getEntries(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        return impl.getEntries(limit);
    }

    /**
     * Returns statistics about the trie structure.
     *
     * <p>This method is more efficient than {@link #getAllEntries()} when only
     * counts are needed, as it does not allocate entry objects.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = new MpfTrie(store);
     * trie.put("key1".getBytes(), "value1".getBytes());
     * trie.put("key2".getBytes(), "value2".getBytes());
     * trie.put("key3".getBytes(), "value3".getBytes());
     *
     * TrieStatistics stats = trie.getStatistics();
     * assertEquals(3, stats.getEntryCount());
     * assertTrue(stats.getBranchCount() > 0);
     * System.out.println("Max depth: " + stats.getMaxDepth());
     * }</pre>
     *
     * @return statistics about the trie structure
     * @since 0.8.0
     */
    public TrieStatistics getStatistics() {
        return impl.getStatistics();
    }

    /**
     * Returns the number of entries (key-value pairs) in the trie.
     *
     * <p>This method traverses the trie to count entries. For large tries,
     * this operation may be slow (O(n)).</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = new MpfTrie(store);
     * assertEquals(0, trie.computeSize());
     *
     * trie.put("key1".getBytes(), "value1".getBytes());
     * trie.put("key2".getBytes(), "value2".getBytes());
     * assertEquals(2, trie.computeSize());
     * }</pre>
     *
     * @return the number of entries in the trie
     * @since 0.8.0
     */
    public int computeSize() {
        return impl.getStatistics().getEntryCount();
    }

    /**
     * Returns an ASCII text representation of the tree structure.
     *
     * <p>Useful for debugging and visualization. Shows all nodes (Branch, Extension, Leaf),
     * their hashes (truncated), paths as nibble arrays, and values as hex strings.</p>
     *
     * <p><b>Example output:</b></p>
     * <pre>
     * Root: 0x1a2b3c4d...
     * [Branch] hash=0x5e6f7a8b... value=&lt;none&gt;
     *   [0] -&gt; (empty)
     *   [1] -&gt; [Extension] path=[8,6,5] hash=0x7a8b9c0d...
     *     [Leaf] path=[6,c,6,f] value=0x776f726c64
     *   ...
     * </pre>
     *
     * @return formatted tree string showing the complete structure
     * @since 0.8.0
     */
    public String printTree() {
        return impl.printTree();
    }

    /**
     * Returns a structured tree representation for custom rendering.
     *
     * <p>This method returns a {@link TreeNode} structure that can be used for:
     * <ul>
     *   <li>Custom rendering in web applications</li>
     *   <li>HTML tree visualizers</li>
     *   <li>Custom text formatters</li>
     *   <li>Data analysis tools</li>
     * </ul>
     *
     * <p>The structure can be serialized to JSON using {@link TreeNode#toJson(TreeNode)}
     * or the convenience method {@link #printTreeJson()}.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = new MpfTrie(store);
     * trie.put("hello".getBytes(), "world".getBytes());
     *
     * TreeNode structure = trie.getTreeStructure();
     * if (structure instanceof TreeNode.LeafTreeNode leaf) {
     *     System.out.println("Value: " + leaf.getValue());
     *     System.out.println("Key: " + leaf.getKey()); // original key
     * }
     * }</pre>
     *
     * @return the tree structure as a TreeNode, or null if trie is empty
     * @since 0.8.0
     */
    public TreeNode getTreeStructure() {
        return impl.getTreeStructure();
    }

    /**
     * Returns tree structure for a subtree at the given nibble prefix.
     *
     * <p>This method enables incremental exploration of large tries. Users can
     * navigate by providing nibble prefixes, loading subtrees on demand with
     * a limited number of nodes.</p>
     *
     * <p>In MpfTrie, keys are hashed to 32 bytes (64 nibbles) using Blake2b-256.
     * The first nibble of the hashed key divides entries into 16 buckets (0-f),
     * making prefix-based exploration natural for UIs.</p>
     *
     * <p>When the node limit is reached, unexpanded subtrees are represented as
     * {@link TreeNode.TruncatedTreeNode} instances, which contain the hash and
     * metadata needed to request expansion later.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * // Get root structure (limited to 100 nodes)
     * TreeNode root = trie.getTreeStructure(new int[]{}, 100);
     *
     * // Explore subtree under nibble 5
     * TreeNode subtree = trie.getTreeStructure(new int[]{5}, 100);
     *
     * // Explore deeper: nibbles 5, a (10), 3
     * TreeNode deeper = trie.getTreeStructure(new int[]{5, 10, 3}, 100);
     *
     * // Handle truncated nodes for further expansion
     * if (node instanceof TreeNode.TruncatedTreeNode truncated) {
     *     // Request subtree at this path
     *     TreeNode expanded = trie.getTreeStructure(pathToNode, 100);
     * }
     * }</pre>
     *
     * @param nibblePrefix path of nibbles (0-15) to navigate, empty array for root
     * @param maxNodes     maximum nodes to include (must be positive)
     * @return tree structure starting at prefix, or null if prefix not found or trie is empty
     * @throws IllegalArgumentException if maxNodes is not positive or any nibble is out of range [0-15]
     * @since 0.8.0
     */
    public TreeNode getTreeStructure(int[] nibblePrefix, int maxNodes) {
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("maxNodes must be positive: " + maxNodes);
        }
        for (int nibble : nibblePrefix) {
            if (nibble < 0 || nibble > 15) {
                throw new IllegalArgumentException("nibble out of range [0-15]: " + nibble);
            }
        }
        return impl.getTreeStructure(nibblePrefix, maxNodes);
    }

    /**
     * Returns a JSON representation of the tree structure.
     *
     * <p>This convenience method combines {@link #getTreeStructure()} with JSON
     * serialization for easy debugging and visualization.</p>
     *
     * <p><b>Example output:</b></p>
     * <pre>{@code
     * {
     *   "type" : "leaf",
     *   "path" : [6, 8, 6, 5, 6, 12, ...],
     *   "value" : "776f726c64",
     *   "key" : "68656c6c6f"
     * }
     * }</pre>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = new MpfTrie(store);
     * trie.put("hello".getBytes(), "world".getBytes());
     *
     * String json = trie.printTreeJson();
     * System.out.println(json);
     * }</pre>
     *
     * @return JSON string representation of the tree, or "null" if trie is empty
     * @since 0.8.0
     */
    public String printTreeJson() {
        TreeNode structure = getTreeStructure();
        return TreeNode.toJson(structure);
    }

    /**
     * Represents a key-value pair returned by getAllEntries and getEntries operations.
     *
     * <p>This immutable class encapsulates the results of trie operations,
     * providing access to the path (hashed key), value, and the original key.</p>
     *
     * @since 0.8.0
     */
    public static final class Entry {
        private final byte[] path;
        private final byte[] value;
        private final byte[] key;

        /**
         * Creates a new Entry with the given path and value.
         *
         * @param path  the path bytes (this is the hashed key in MpfTrie)
         * @param value the value bytes
         */
        public Entry(byte[] path, byte[] value) {
            this(path, value, null);
        }

        /**
         * Creates a new Entry with the given path, value, and original key.
         *
         * @param path  the path bytes (this is the hashed key in MpfTrie)
         * @param value the value bytes
         * @param key   the original (unhashed) key, or null if not available
         */
        public Entry(byte[] path, byte[] value, byte[] key) {
            this.path = path;
            this.value = value;
            this.key = key;
        }

        /**
         * Returns the path of this entry.
         *
         * <p>In MpfTrie, this is the Blake2b-256 hashed key, not the original key.
         * Use {@link #getKey()} to get the unhashed original key.</p>
         *
         * @return the path (hashed key) as a byte array
         */
        public byte[] getPath() {
            return path;
        }

        /**
         * Returns the value of this entry.
         *
         * @return the value as a byte array
         */
        public byte[] getValue() {
            return value;
        }

        /**
         * Returns the original (unhashed) key.
         *
         * <p>This is the key that was originally passed to {@link #put(byte[], byte[])}.
         * </p>
         *
         * @return the original (unhashed) key, or null if not available
         */
        public byte[] getKey() {
            return key;
        }
    }

    /**
     * Private implementation of the Merkle Patricia Trie data structure.
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
     */
    private static final class Impl {
        private final HashFunction hashFn;
        private final NodePersistence persistence;
        private final CommitmentScheme commitments;

        private byte[] root; // nullable => empty trie

        /**
         * Creates a new Merkle Patricia Trie implementation with custom commitment scheme.
         *
         * @param store       the node storage backend (must not be null)
         * @param hashFn      the hash function for node hashing (must not be null)
         * @param root        the initial root hash, or null for an empty trie
         * @param commitments the commitment scheme for node hashing (must not be null)
         * @throws NullPointerException if any parameter is null
         */
        Impl(NodeStore store, HashFunction hashFn, byte[] root, CommitmentScheme commitments) {
            Objects.requireNonNull(store, "NodeStore");
            this.hashFn = Objects.requireNonNull(hashFn, "HashFunction");
            this.commitments = Objects.requireNonNull(commitments, "CommitmentScheme");
            this.persistence = new NodePersistence(store, commitments, hashFn);
            this.root = root == null || root.length == 0 ? null : root;
        }

        /**
         * Sets the root hash of the trie.
         */
        void setRootHash(byte[] root) {
            this.root = root == null || root.length == 0 ? null : root;
        }

        /**
         * Gets the current root hash of the trie.
         */
        byte[] getRootHash() {
            return root;
        }

        /**
         * Inserts or updates a key-value pair in the trie, optionally storing the original key.
         *
         * @param path  the path to insert (this is the hashed key in MpfTrie usage)
         * @param value the value to associate with the key
         * @param key   the original (unhashed) key for storage, or null if not storing
         * @throws IllegalArgumentException if value is null
         */
        void put(byte[] path, byte[] value, byte[] key) {
            if (value == null) throw new IllegalArgumentException("value cannot be null; use delete");

            int[] nibblePath = Nibbles.toNibbles(path);
            NodeHash result = putAtNew(this.root, nibblePath, 0, value, key);
            this.root = result != null ? result.toBytes() : null;
        }

        /**
         * Retrieves a value by its key.
         */
        byte[] get(byte[] key) {
            int[] nibblePath = Nibbles.toNibbles(key);
            return getAtNew(this.root, nibblePath, 0);
        }

        /**
         * Deletes a key-value pair from the trie.
         */
        void delete(byte[] key) {
            int[] nibblePath = Nibbles.toNibbles(key);
            NodeHash result = deleteAtNew(this.root, nibblePath, 0);
            this.root = result != null ? result.toBytes() : null;
        }

        /**
         * Builds an MPF-style inclusion or non-inclusion proof for the provided key.
         */
        private TraversalProof getProof(byte[] key) {
            Objects.requireNonNull(key, "key");
            if (this.root == null) {
                return TraversalProof.nonInclusionMissingBranch(Collections.emptyList());
            }

            int[] keyNibbles = Nibbles.toNibbles(key);
            List<TraversalProof.Step> steps = new ArrayList<>();
            NibblePath pendingPrefix = NibblePath.EMPTY;
            byte[] currentHash = this.root;
            int depth = 0;
            List<Integer> traversedNibbles = new ArrayList<>();

            while (true) {
                Node node = persistence.load(NodeHash.of(currentHash));
                if (node == null) {
                    return TraversalProof.nonInclusionMissingBranch(steps);
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
                        steps.add(new TraversalProof.BranchStep(pendingPrefix, childHashes, childIndex, branchValueHash));
                        pendingPrefix = NibblePath.EMPTY;

                        if (branchValue != null) {
                            return TraversalProof.inclusion(steps, branchValue, branchValueHash, NibblePath.EMPTY);
                        }
                        return TraversalProof.nonInclusionMissingBranch(steps);
                    }

                    byte[] childCommit = branch.getChild(childIndex);
                    if (childCommit == null || childCommit.length == 0) {
                        byte[] branchValueHash = null;
                        if (commitments.encodesBranchValueInBranchCommitment()) {
                            branchValueHash = branch.getValue() == null ? null : hashFn.digest(branch.getValue());
                        }
                        steps.add(new TraversalProof.BranchStep(pendingPrefix, childHashes, childIndex, branchValueHash));
                        pendingPrefix = NibblePath.EMPTY;
                        return TraversalProof.nonInclusionMissingBranch(steps);
                    }

                    byte[] branchValueHash = null;
                    if (commitments.encodesBranchValueInBranchCommitment()) {
                        branchValueHash = branch.getValue() == null ? null : hashFn.digest(branch.getValue());
                    }
                    steps.add(new TraversalProof.BranchStep(pendingPrefix, childHashes, childIndex, branchValueHash));
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
                        int matched = mismatchPosition < 0 ? 0 : mismatchPosition;
                        if (matched >= extNibbles.length) {
                            return TraversalProof.nonInclusionMissingBranch(steps);
                        }

                        byte[] childCommit = extension.getChild();
                        if (childCommit == null || childCommit.length == 0) {
                            return TraversalProof.nonInclusionMissingBranch(steps);
                        }

                        int[] skipPrefix = matched == 0 ? new int[0] : Arrays.copyOf(extNibbles, matched);
                        int neighborNibble = extNibbles[matched];
                        int[] suffixNibbles = matched + 1 < extNibbles.length
                                ? Arrays.copyOfRange(extNibbles, matched + 1, extNibbles.length)
                                : new int[0];

                        NibblePath forkSkip = concat(pendingPrefix, skipPrefix);
                        NibblePath forkSuffix = NibblePath.of(suffixNibbles);
                        byte[] flattenedCommit = persistence.computeExtensionCommitForProof(extension);

                        steps.add(new TraversalProof.ForkStep(forkSkip, neighborNibble, forkSuffix, flattenedCommit));
                        return TraversalProof.nonInclusionMissingBranch(steps);
                    }

                    if (steps.isEmpty()) {
                        pendingPrefix = concat(pendingPrefix, extNibbles);
                    } else {
                        TraversalProof.Step lastStep = steps.remove(steps.size() - 1);
                        if (!(lastStep instanceof TraversalProof.BranchStep)) {
                            throw new IllegalStateException("Expected BranchStep before extension but found " + lastStep.getClass().getSimpleName());
                        }
                        TraversalProof.BranchStep last = (TraversalProof.BranchStep) lastStep;
                        NibblePath extended = concat(last.skipPath(), extNibbles);
                        steps.add(new TraversalProof.BranchStep(extended, last.childHashes(), last.childIndex(), last.branchValueHash()));
                    }

                    for (int nibble : extNibbles) {
                        traversedNibbles.add(nibble);
                    }
                    depth += extNibbles.length;
                    byte[] childCommit = extension.getChild();
                    if (childCommit == null || childCommit.length == 0) {
                        return TraversalProof.nonInclusionMissingBranch(steps);
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
                            return TraversalProof.inclusion(steps, value, valueHash, suffix);
                        }
                    }

                    int[] conflictingPathNibbles = new int[traversedNibbles.size() + leafNibbles.length];
                    for (int i = 0; i < traversedNibbles.size(); i++) {
                        conflictingPathNibbles[i] = traversedNibbles.get(i);
                    }
                    System.arraycopy(leafNibbles, 0, conflictingPathNibbles, traversedNibbles.size(), leafNibbles.length);
                    byte[] conflictingKeyHash = Nibbles.fromNibbles(conflictingPathNibbles);

                    byte[] conflictingValueHash = hashFn.digest(leaf.getValue());
                    NibblePath conflictingSuffix = NibblePath.of(leafNibbles);
                    return TraversalProof.nonInclusionDifferentLeaf(steps, conflictingKeyHash, conflictingValueHash, conflictingSuffix);
                }

                throw new IllegalStateException("Unsupported node type " + node.getClass().getSimpleName());
            }
        }

        /**
         * Returns an MPF wire proof for the given key.
         */
        Optional<byte[]> getProofWire(byte[] key) {
            Objects.requireNonNull(key, "key");
            TraversalProof proof = getProof(key);
            byte[] wire = ProofSerializer.toCbor(proof, key, hashFn, commitments);
            return Optional.of(wire);
        }

        /**
         * Verifies an MPF wire proof against the supplied root/key/value.
         */
        boolean verifyProofWire(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including, byte[] wire) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(wire, "wire");
            return com.bloxbean.cardano.vds.mpf.proof.ProofVerifier
                    .verify(expectedRoot, key, valueOrNull, including, wire, hashFn, commitments);
        }

        // ======================================
        // Visitor-Based Implementation Methods
        // ======================================

        /**
         * Visitor-based implementation for inserting values using immutable nodes.
         */
        private NodeHash putAtNew(byte[] nodeHash, int[] keyNibbles, int position, byte[] value, byte[] key) {
            if (nodeHash == null) {
                int[] remainingNibbles = NibbleArrays.slice(keyNibbles, position, keyNibbles.length);
                byte[] hp = Nibbles.packHP(true, remainingNibbles);
                LeafNode leaf = LeafNode.of(hp, value, key);
                return persistence.persist(leaf);
            }

            Node node = persistence.load(NodeHash.of(nodeHash));
            if (node == null) {
                int[] remainingNibbles = NibbleArrays.slice(keyNibbles, position, keyNibbles.length);
                byte[] hp = Nibbles.packHP(true, remainingNibbles);
                LeafNode leaf = LeafNode.of(hp, value, key);
                return persistence.persist(leaf);
            }

            PutOperationVisitor putVisitor = new PutOperationVisitor(persistence, keyNibbles, position, value, key);
            return node.accept(putVisitor);
        }

        /**
         * Visitor-based implementation for retrieving values using immutable nodes.
         */
        private byte[] getAtNew(byte[] nodeHash, int[] keyNibbles, int position) {
            if (nodeHash == null) {
                return null;
            }

            Node node = persistence.load(NodeHash.of(nodeHash));
            if (node == null) {
                return null;
            }

            GetOperationVisitor getVisitor = new GetOperationVisitor(persistence, keyNibbles, position);
            return node.accept(getVisitor);
        }

        /**
         * Visitor-based implementation for deleting values using immutable nodes.
         */
        private NodeHash deleteAtNew(byte[] nodeHash, int[] keyNibbles, int position) {
            if (nodeHash == null) {
                return null;
            }

            Node node = persistence.load(NodeHash.of(nodeHash));
            if (node == null) {
                return null;
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

        // ======================================
        // Traversal / Debug Methods
        // ======================================

        /**
         * Collects all key-value entries stored in the trie using the EntriesCollectorVisitor.
         */
        List<Entry> getAllEntries() {
            if (root == null) {
                return Collections.emptyList();
            }

            Node rootNode = persistence.load(NodeHash.of(root));
            if (rootNode == null) {
                return Collections.emptyList();
            }

            EntriesCollectorVisitor visitor = new EntriesCollectorVisitor(persistence);
            rootNode.accept(visitor);
            return visitor.getEntries();
        }

        /**
         * Collects up to the specified number of key-value entries from the trie.
         */
        List<Entry> getEntries(int limit) {
            if (root == null) {
                return Collections.emptyList();
            }

            Node rootNode = persistence.load(NodeHash.of(root));
            if (rootNode == null) {
                return Collections.emptyList();
            }

            EntriesCollectorVisitor visitor = new EntriesCollectorVisitor(persistence, limit);
            rootNode.accept(visitor);
            return visitor.getEntries();
        }

        /**
         * Returns statistics about the trie structure using the StatisticsVisitor.
         */
        TrieStatistics getStatistics() {
            if (root == null) {
                return TrieStatistics.empty();
            }

            Node rootNode = persistence.load(NodeHash.of(root));
            if (rootNode == null) {
                return TrieStatistics.empty();
            }

            StatisticsVisitor visitor = new StatisticsVisitor(persistence);
            rootNode.accept(visitor);
            return visitor.getStatistics();
        }

        /**
         * Returns an ASCII text representation of the tree structure using the TreePrinterVisitor.
         */
        String printTree() {
            if (root == null) {
                return "(empty trie)";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Root: 0x").append(HexUtil.encodeHexString(root)).append("\n");

            Node rootNode = persistence.load(NodeHash.of(root));
            if (rootNode == null) {
                return sb.append("(root node not found)").toString();
            }

            TreePrinterVisitor visitor = new TreePrinterVisitor(persistence, sb, 0, "", root);
            rootNode.accept(visitor);
            return sb.toString();
        }

        /**
         * Returns a structured tree representation using the TreeStructureVisitor.
         */
        TreeNode getTreeStructure() {
            if (root == null) {
                return null;
            }

            Node rootNode = persistence.load(NodeHash.of(root));
            if (rootNode == null) {
                return null;
            }

            TreeStructureVisitor visitor = new TreeStructureVisitor(persistence, root);
            return rootNode.accept(visitor);
        }

        /**
         * Returns tree structure for a subtree at the given nibble prefix.
         */
        TreeNode getTreeStructure(int[] nibblePrefix, int maxNodes) {
            if (root == null) {
                return null;
            }

            byte[] currentHash = root;
            int prefixPos = 0;

            while (prefixPos < nibblePrefix.length && currentHash != null) {
                Node node = persistence.load(NodeHash.of(currentHash));
                if (node == null) {
                    return null;
                }

                if (node instanceof BranchNode) {
                    BranchNode branch = (BranchNode) node;
                    int nibble = nibblePrefix[prefixPos];
                    byte[] childHash = branch.getChild(nibble);
                    if (childHash == null || childHash.length == 0) {
                        return null;
                    }
                    currentHash = childHash;
                    prefixPos++;
                } else if (node instanceof ExtensionNode) {
                    ExtensionNode ext = (ExtensionNode) node;
                    Nibbles.HP hp = Nibbles.unpackHP(ext.getHp());
                    int[] extNibbles = hp.nibbles;

                    for (int i = 0; i < extNibbles.length && prefixPos < nibblePrefix.length; i++) {
                        if (extNibbles[i] != nibblePrefix[prefixPos]) {
                            return null;
                        }
                        prefixPos++;
                    }
                    currentHash = ext.getChild();
                } else {
                    break;
                }
            }

            Node targetNode = persistence.load(NodeHash.of(currentHash));
            if (targetNode == null) {
                return null;
            }

            TreeStructureVisitor visitor = maxNodes > 0
                    ? new TreeStructureVisitor(persistence, currentHash, maxNodes)
                    : new TreeStructureVisitor(persistence, currentHash);
            return targetNode.accept(visitor);
        }
    }
}
