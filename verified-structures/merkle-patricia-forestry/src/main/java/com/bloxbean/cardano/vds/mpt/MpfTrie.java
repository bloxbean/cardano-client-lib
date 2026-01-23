package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpt.proof.ProofFormatter;

import java.util.List;
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
 * @see MpfTrieImpl
 * @see <a href="https://github.com/aiken-lang/merkle-patricia-forestry">Aiken Merkle Patricia Forestry</a>
 * @since 0.8.0
 */
public final class MpfTrie {
    /**
     * The underlying trie implementation that stores the hashed keys.
     */
    private final MpfTrieImpl impl;

    /**
     * The hash function used to hash keys before storage.
     */
    private final HashFunction hashFn;

    /**
     * Whether to store original (unhashed) keys in leaf nodes for debugging.
     * When true, original keys are stored but NOT used in commitment calculations,
     * ensuring the root hash remains identical.
     */
    private final boolean storeOriginalKeys;


    /**
     * Creates a new MpfTrie optimized for Cardano with Blake2b-256 hashing and MPF mode.
     *
     * <p>This is the simplest and recommended constructor for Cardano developers.
     * Uses Blake2b-256 for key hashing (as required by Cardano/Aiken) and MPF
     * commitment scheme for on-chain compatibility.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @throws NullPointerException if store is null
     */
    public MpfTrie(NodeStore store) {
        this(store, null, false);
    }

    /**
     * Creates an MpfTrie optimized for Cardano with existing root, using Blake2b-256 and MPF mode.
     *
     * <p>Use this constructor to load an existing trie from storage by providing
     * its root hash. This is the recommended constructor for resuming from a
     * previously committed state.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @param root  the root hash of an existing trie, or null for empty trie
     * @throws NullPointerException if store is null
     */
    public MpfTrie(NodeStore store, byte[] root) {
        this(store, root, false);
    }

    /**
     * Creates an MpfTrie with optional original key storage for debugging.
     *
     * <p>When storeOriginalKeys is true, the original (unhashed) keys are stored
     * alongside the values in leaf nodes. This enables debugging tools like
     * {@link #getAllEntries()} to return human-readable keys.</p>
     *
     * <p><b>Important:</b> Original keys are NOT used in commitment calculations.
     * The trie's root hash is identical whether original keys are stored or not,
     * maintaining full Aiken compatibility.</p>
     *
     * @param store             the storage backend for persisting trie nodes
     * @param root              the root hash of an existing trie, or null for empty trie
     * @param storeOriginalKeys true to store original keys for debugging
     * @throws NullPointerException if store is null
     */
    public MpfTrie(NodeStore store, byte[] root, boolean storeOriginalKeys) {
        this.hashFn = Blake2b256::digest;
        this.impl = new MpfTrieImpl(store, hashFn, root, new MpfCommitmentScheme(hashFn));
        this.storeOriginalKeys = storeOriginalKeys;
    }

    /**
     * Creates a new MpfTrie with original key storage enabled for debugging.
     *
     * <p>This factory method is a convenience for creating a debugging-enabled trie.
     * Original keys are stored in leaf nodes for tools like {@link #getAllEntries()}.</p>
     *
     * <p><b>Important:</b> Original keys are NOT used in commitment calculations.
     * The trie's root hash is identical to a trie without original key storage.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @return new MpfTrie with original key storage enabled
     * @throws NullPointerException if store is null
     */
    public static MpfTrie withOriginalKeyStorage(NodeStore store) {
        return new MpfTrie(store, null, true);
    }

    /**
     * Creates an MpfTrie from existing root with original key storage enabled for debugging.
     *
     * <p>This factory method loads an existing trie while enabling original key storage
     * for subsequent put operations.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @param root  the root hash of an existing trie, or null for empty trie
     * @return new MpfTrie with original key storage enabled
     * @throws NullPointerException if store is null
     */
    public static MpfTrie withOriginalKeyStorage(NodeStore store, byte[] root) {
        return new MpfTrie(store, root, true);
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
     * <p>If this trie was created with original key storage enabled, the original
     * key is also stored for debugging purposes (but NOT used in commitment calculations).</p>
     *
     * @param key   the original key (will be hashed before storage)
     * @param value the value to store (must not be null)
     * @throws IllegalArgumentException if value is null
     * @throws NullPointerException     if key is null
     */
    public void put(byte[] key, byte[] value) {
        if (storeOriginalKeys) {
            impl.put(hashFn.digest(key), value, key);
        } else {
            impl.put(hashFn.digest(key), value);
        }
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
     * Scans for entries with hashed keys matching a hashed prefix.
     *
     * <p><b>Important:</b> This method has limited utility in MpfTrie because:
     * <ul>
     *   <li>The prefix is hashed before scanning, losing prefix semantics</li>
     *   <li>Results contain hashed keys, not original keys</li>
     *   <li>Cryptographic hashes don't preserve prefix relationships</li>
     * </ul>
     *
     *
     * @param prefix the prefix to hash and search for
     * @param limit  maximum number of results (0 or negative for unlimited)
     * @return a list of entries with hashed keys matching the hashed prefix
     * @throws NullPointerException if prefix is null
     */
    public List<Entry> scanByPrefix(byte[] prefix, int limit) {
        return impl.scanByPrefix(hashFn.digest(prefix), limit);
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
     * @param expectedRoot the expected trie root hash
     * @param key          the original key (will be hashed for verification)
     * @param valueOrNull  the expected value, or null for non-inclusion proof
     * @param including    true for inclusion proof, false for non-inclusion proof
     * @param wire         the wire-format proof bytes
     * @return true if the proof is valid, false otherwise
     */
    public boolean verifyProofWire(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including, byte[] wire) {
        return impl.verifyProofWire(expectedRoot, key, valueOrNull, including, wire);
    }

    /**
     * Returns all key-value entries stored in the trie.
     *
     * <p>This method traverses the entire trie and returns all stored entries.
     * Note that the keys in the returned entries are the Blake2b-256 hashed keys,
     * not the original keys (since MpfTrie hashes all keys before storage).</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = new MpfTrie(store);
     * trie.put("key1".getBytes(), "value1".getBytes());
     * trie.put("key2".getBytes(), "value2".getBytes());
     *
     * List<MpfTrie.Entry> entries = trie.getAllEntries();
     * assertEquals(2, entries.size());
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
     * <p>Note that the keys in the returned entries are the Blake2b-256 hashed keys,
     * not the original keys (since MpfTrie hashes all keys before storage).</p>
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
     * MpfTrie trie = MpfTrie.withOriginalKeyStorage(store);
     * trie.put("hello".getBytes(), "world".getBytes());
     *
     * TreeNode structure = trie.getTreeStructure();
     * if (structure instanceof TreeNode.LeafTreeNode leaf) {
     *     System.out.println("Value: " + leaf.getValue());
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
     *   "originalKey" : "68656c6c6f"
     * }
     * }</pre>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * MpfTrie trie = MpfTrie.withOriginalKeyStorage(store);
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
     * Represents a key-value pair returned by scan and getAllEntries operations.
     *
     * <p>This immutable class encapsulates the results of trie operations,
     * providing access to the hashed key, value, and optionally the original (unhashed) key
     * when original key storage is enabled.</p>
     *
     * @since 0.8.0
     */
    public static final class Entry {
        private final byte[] key;
        private final byte[] value;
        private final byte[] originalKey;

        /**
         * Creates a new Entry with the given key and value.
         *
         * @param key   the key bytes (this is the hashed key in MpfTrie)
         * @param value the value bytes
         */
        public Entry(byte[] key, byte[] value) {
            this(key, value, null);
        }

        /**
         * Creates a new Entry with the given key, value, and original key.
         *
         * @param key         the key bytes (this is the hashed key in MpfTrie)
         * @param value       the value bytes
         * @param originalKey the original (unhashed) key, or null if not available
         */
        public Entry(byte[] key, byte[] value, byte[] originalKey) {
            this.key = key;
            this.value = value;
            this.originalKey = originalKey;
        }

        /**
         * Returns the key of this entry.
         *
         * <p>In MpfTrie, this is the Blake2b-256 hashed key, not the original key.
         * Use {@link #getOriginalKey()} to get the unhashed key if available.</p>
         *
         * @return the (hashed) key as a byte array
         */
        public byte[] getKey() {
            return key;
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
         * Returns the original (unhashed) key if available.
         *
         * <p>This is only populated when the trie was created with original key storage
         * enabled using {@link MpfTrie#withOriginalKeyStorage(NodeStore)} or
         * {@link MpfTrie#MpfTrie(NodeStore, byte[], boolean)}.</p>
         *
         * @return the original (unhashed) key, or null if not available
         */
        public byte[] getOriginalKey() {
            return originalKey;
        }
    }
}
