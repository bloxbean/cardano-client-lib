package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.mode.Modes;
import com.bloxbean.cardano.vds.mpt.mode.MptMode;
import com.bloxbean.cardano.vds.mpt.mpf.MpfProofFormatter;

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
 *   <li>Storing user-provided keys (DoS protection via hashing)</li>
 * </ul>
 *
 * <p><b>When NOT to Use MpfTrie:</b></p>
 * <ul>
 *   <li>Need prefix queries on original keys → use {@link MpfTrieImpl} directly</li>
 *   <li>Building off-chain indexers → use {@link MpfTrieImpl} directly</li>
 *   <li>Want to recover original keys from trie → use {@link MpfTrieImpl} directly</li>
 * </ul>
 *
 * <p><b>Security Benefits:</b></p>
 * <ul>
 *   <li><b>DoS Protection:</b> Key hashing prevents adversarial key selection attacks</li>
 *   <li><b>Uniform Distribution:</b> Hash function ensures balanced trie structure</li>
 *   <li><b>Fixed Key Size:</b> All keys become 32 bytes for consistent depth</li>
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
        this.hashFn = Blake2b256::digest;
        this.impl = new MpfTrieImpl(store, hashFn, null, Modes.mpf(hashFn));
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
        this.hashFn = Blake2b256::digest;
        this.impl = new MpfTrieImpl(store, hashFn, root, Modes.mpf(hashFn));
    }

    /**
     * Creates a new MpfTrie with empty root and custom hash function.
     *
     * <p>Uses MPF commitment scheme for Cardano/Aiken compatibility.</p>
     *
     * @param store  the storage backend for persisting trie nodes
     * @param hashFn the hash function for both key hashing and node hashing
     * @throws NullPointerException if store or hashFn is null
     */
    public MpfTrie(NodeStore store, HashFunction hashFn) {
        this.impl = new MpfTrieImpl(store, hashFn, null, Modes.mpf(hashFn));
        this.hashFn = hashFn;
    }

    /**
     * Creates an MpfTrie with an existing root and custom hash function.
     *
     * <p>Uses MPF commitment scheme for Cardano/Aiken compatibility.</p>
     *
     * @param store  the storage backend for persisting trie nodes
     * @param hashFn the hash function for both key hashing and node hashing
     * @param root   the root hash of an existing trie, or null for empty trie
     * @throws NullPointerException if store or hashFn is null
     */
    public MpfTrie(NodeStore store, HashFunction hashFn, byte[] root) {
        this.impl = new MpfTrieImpl(store, hashFn, root, Modes.mpf(hashFn));
        this.hashFn = hashFn;
    }

    /**
     * Creates a new MpfTrie with specific mode (advanced usage).
     *
     * @param store  the storage backend
     * @param hashFn the hash function for both key hashing and node hashing
     * @param mode   the MPT mode (should be MPF for Cardano compatibility)
     */
    public MpfTrie(NodeStore store, HashFunction hashFn, MptMode mode) {
        this.impl = new MpfTrieImpl(store, hashFn, null, mode);
        this.hashFn = hashFn;
    }

    /**
     * Creates an MpfTrie with existing root and specific mode (advanced usage).
     *
     * @param store  the storage backend
     * @param hashFn the hash function for both key hashing and node hashing
     * @param root   the root hash of existing trie
     * @param mode   the MPT mode (should be MPF for Cardano compatibility)
     */
    public MpfTrie(NodeStore store, HashFunction hashFn, byte[] root, MptMode mode) {
        this.impl = new MpfTrieImpl(store, hashFn, root, mode);
        this.hashFn = hashFn;
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
     * @param key   the original key (will be hashed before storage)
     * @param value the value to store (must not be null)
     * @throws IllegalArgumentException if value is null
     * @throws NullPointerException     if key is null
     */
    public void put(byte[] key, byte[] value) {
        impl.put(hashFn.digest(key), value);
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
     * @see MpfProofFormatter#toPlutusData(byte[])
     */
    public Optional<ListPlutusData> getProofPlutusData(byte[] key) {
        return getProofWire(key)
                .map(MpfProofFormatter::toPlutusData);
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
     * Represents a key-value pair returned by scan operations.
     *
     * <p>This immutable class encapsulates the results of prefix scans,
     * providing access to both the key and its associated value.</p>
     *
     * @since 0.8.0
     */
    public static final class Entry {
        private final byte[] key;
        private final byte[] value;

        /**
         * Creates a new Entry with the given key and value.
         *
         * @param key   the key bytes
         * @param value the value bytes
         */
        public Entry(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }

        /**
         * Returns the key of this entry.
         *
         * @return the key as a byte array
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
    }
}
