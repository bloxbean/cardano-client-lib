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
 * Secure Merkle Patricia Trie that hashes keys before storage.
 *
 * <p><b>⚠️ CARDANO DEVELOPERS: Use this class for on-chain compatibility.</b></p>
 *
 * <p>This implementation matches the behavior of Cardano's
 * <a href="https://github.com/aiken-lang/merkle-patricia-forestry">merkle-patricia-forestry</a>
 * library in Aiken, which always hashes keys using Blake2b-256 before storage. All keys are
 * automatically hashed before being stored in the trie.</p>
 *
 * <p><b>Cardano-Optimized Constructors:</b></p>
 * <pre>{@code
 * // Simplest for Cardano: Blake2b-256 + MPF hardcoded
 * SecureTrie trie = new SecureTrie(store);
 * SecureTrie trie = new SecureTrie(store, existingRoot);
 *
 * // With custom hash function (still uses MPF mode)
 * SecureTrie trie = new SecureTrie(store, customHashFn);
 * SecureTrie trie = new SecureTrie(store, customHashFn, existingRoot);
 *
 * // With custom mode (advanced usage)
 * SecureTrie trie = new SecureTrie(store, hashFn, Modes.mpf(hashFn));
 * SecureTrie trie = new SecureTrie(store, hashFn, existingRoot, Modes.mpf(hashFn));
 * }</pre>
 *
 * <p><b>Why SecureTrie Prevents Branch Values:</b></p>
 * <p>SecureTrie automatically prevents branch values by hashing all keys to 32 bytes (64 nibbles).
 * Since all hashed keys are the same length, they all terminate at the same depth (64 levels),
 * making it impossible for one key to be a prefix of another at termination. This guarantees
 * all values are stored at leaves, ensuring full Cardano/Aiken compatibility.</p>
 *
 * <p><b>When to Use SecureTrie:</b></p>
 * <ul>
 *   <li>Building Cardano smart contracts or dApps</li>
 *   <li>Need compatibility with Aiken merkle-patricia-forestry</li>
 *   <li>Storing user-provided keys (DoS protection)</li>
 * </ul>
 *
 * <p><b>When NOT to Use SecureTrie:</b></p>
 * <ul>
 *   <li>Need prefix queries on original keys → use {@link MerklePatriciaTrie}</li>
 *   <li>Building off-chain indexers → use {@link MerklePatriciaTrie}</li>
 *   <li>Want to recover original keys from trie → use {@link MerklePatriciaTrie}</li>
 * </ul>
 *
 * <p><b>Security Benefits:</b></p>
 * <ul>
 *   <li><b>DoS Protection:</b> Prevents adversarial key selection that could create
 *       unbalanced tries with poor performance characteristics</li>
 *   <li><b>Key Privacy:</b> Original keys are not directly stored, providing some
 *       level of key confidentiality</li>
 *   <li><b>Uniform Distribution:</b> Hash function ensures keys are uniformly
 *       distributed across the trie space</li>
 * </ul>
 *
 * <p><b>Performance Benefits:</b></p>
 * <ul>
 *   <li><b>Consistent Depth:</b> Hash uniformity leads to more balanced tries</li>
 *   <li><b>Fixed Key Size:</b> All keys become fixed-length hash values (32 bytes / 64 nibbles)</li>
 *   <li><b>Better Caching:</b> Predictable access patterns improve cache performance</li>
 * </ul>
 *
 * <p><b>Trade-offs:</b></p>
 * <ul>
 *   <li><b>No Prefix Queries:</b> Cannot perform prefix scans on original keys (hashing destroys prefix relationships)</li>
 *   <li><b>Hash Overhead:</b> Additional computation for each key operation</li>
 *   <li><b>Key Recovery:</b> Original keys cannot be recovered from the trie</li>
 * </ul>
 *
 * <p><b>Implementation Note:</b> This class wraps {@link MerklePatriciaTrie} and automatically
 * applies {@code hashFn.digest(key)} before all operations, matching the behavior of
 * Aiken's {@code blake2b_256(key)} in the including/excluding functions. All constructors
 * use MPF commitment scheme by default for Cardano/Aiken compatibility.</p>
 *
 * @see MerklePatriciaTrie
 * @see HashFunction
 * @see <a href="https://github.com/aiken-lang/merkle-patricia-forestry">Cardano Merkle Patricia Forestry (Aiken)</a>
 * @since 0.8.0
 */
public final class SecureTrie {
    /**
     * The underlying Merkle Patricia Trie that stores the hashed keys.
     */
    private final MerklePatriciaTrie inner;

    /**
     * The hash function used to hash keys before storage.
     */
    private final HashFunction hashFn;

    /**
     * Creates a new SecureTrie optimized for Cardano with Blake2b-256 hashing and MPF mode.
     *
     * <p>This constructor uses Blake2b-256 for key hashing (as required by Cardano/Aiken)
     * and MPF commitment scheme for on-chain compatibility. This is the simplest constructor
     * for Cardano developers.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @throws NullPointerException if store is null
     */
    public SecureTrie(NodeStore store) {
        this.hashFn = Blake2b256::digest;
        this.inner = new MerklePatriciaTrie(store, hashFn, null, Modes.mpf(hashFn));
    }

    /**
     * Creates a SecureTrie optimized for Cardano with existing root, using Blake2b-256 and MPF mode.
     *
     * <p>This constructor uses Blake2b-256 for key hashing (as required by Cardano/Aiken)
     * and MPF commitment scheme for on-chain compatibility.</p>
     *
     * @param store the storage backend for persisting trie nodes
     * @param root  the root hash of an existing secure trie, or null for empty trie
     * @throws NullPointerException if store is null
     */
    public SecureTrie(NodeStore store, byte[] root) {
        this.hashFn = Blake2b256::digest;
        this.inner = new MerklePatriciaTrie(store, hashFn, root, Modes.mpf(hashFn));
    }

    /**
     * Creates a new SecureTrie with empty root and custom hash function.
     *
     * <p>Uses MPF commitment scheme for Cardano/Aiken compatibility.</p>
     *
     * @param store  the storage backend for persisting trie nodes
     * @param hashFn the hash function for both key hashing and node hashing
     * @throws NullPointerException if store or hashFn is null
     */
    public SecureTrie(NodeStore store, HashFunction hashFn) {
        this.inner = new MerklePatriciaTrie(store, hashFn, null, Modes.mpf(hashFn));
        this.hashFn = hashFn;
    }

    /**
     * Creates a SecureTrie with an existing root and custom hash function.
     *
     * <p>Uses MPF commitment scheme for Cardano/Aiken compatibility.</p>
     *
     * @param store  the storage backend for persisting trie nodes
     * @param hashFn the hash function for both key hashing and node hashing
     * @param root   the root hash of an existing secure trie, or null for empty trie
     * @throws NullPointerException if store or hashFn is null
     */
    public SecureTrie(NodeStore store, HashFunction hashFn, byte[] root) {
        this.inner = new MerklePatriciaTrie(store, hashFn, root, Modes.mpf(hashFn));
        this.hashFn = hashFn;
    }

    /**
     * Creates a new SecureTrie with specific mode (advanced usage).
     *
     * @param store  the storage backend
     * @param hashFn the hash function for both key hashing and node hashing
     * @param mode   the MPT mode (should be MPF for Cardano compatibility)
     */
    public SecureTrie(NodeStore store, HashFunction hashFn, MptMode mode) {
        this.inner = new MerklePatriciaTrie(store, hashFn, null, mode);
        this.hashFn = hashFn;
    }

    /**
     * Creates a SecureTrie with existing root and specific mode (advanced usage).
     *
     * @param store  the storage backend
     * @param hashFn the hash function for both key hashing and node hashing
     * @param root   the root hash of existing trie
     * @param mode   the MPT mode (should be MPF for Cardano compatibility)
     */
    public SecureTrie(NodeStore store, HashFunction hashFn, byte[] root, MptMode mode) {
        this.inner = new MerklePatriciaTrie(store, hashFn, root, mode);
        this.hashFn = hashFn;
    }

    /**
     * Sets the root hash of this secure trie.
     *
     * @param root the new root hash, or null to reset to empty trie
     */
    public void setRootHash(byte[] root) {
        inner.setRootHash(root);
    }

    /**
     * Returns the current root hash of the secure trie.
     *
     * @return the root hash as a byte array, or null if the trie is empty
     */
    public byte[] getRootHash() {
        return inner.getRootHash();
    }

    /**
     * Stores a key-value pair in the secure trie.
     *
     * <p>The key is hashed using the configured hash function before storage.
     * This ensures uniform distribution and prevents adversarial key attacks.</p>
     *
     * @param key   the original key (will be hashed before storage)
     * @param value the value to store (must not be null)
     * @throws IllegalArgumentException if value is null
     * @throws NullPointerException     if key is null
     */
    public void put(byte[] key, byte[] value) {
        inner.put(hashFn.digest(key), value);
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
        return inner.get(hashFn.digest(key));
    }

    /**
     * Removes a key-value pair from the secure trie.
     *
     * <p>The key is hashed before deletion. If the original key doesn't exist,
     * this operation has no effect.</p>
     *
     * @param key the original key to delete
     * @throws NullPointerException if key is null
     */
    public void delete(byte[] key) {
        inner.delete(hashFn.digest(key));
    }

    /**
     * Scans for entries with hashed keys matching a hashed prefix.
     *
     * <p><b>Important:</b> This method has limited utility in SecureTrie because:
     * <ul>
     *   <li>The prefix is hashed before scanning, losing prefix semantics</li>
     *   <li>Results contain hashed keys, not original keys</li>
     *   <li>Cryptographic hashes don't preserve prefix relationships</li>
     * </ul>
     *
     * <p>This method is mainly provided for API compatibility. For prefix queries
     * on original keys, use the standard {@link MerklePatriciaTrie} instead.</p>
     *
     * @param prefix the prefix to hash and search for
     * @param limit  maximum number of results (0 or negative for unlimited)
     * @return a list of entries with hashed keys matching the hashed prefix
     * @throws NullPointerException if prefix is null
     */
    public List<MerklePatriciaTrie.Entry> scanByPrefix(byte[] prefix, int limit) {
        return inner.scanByPrefix(hashFn.digest(prefix), limit);
    }

    /**
     * Builds a mode-bound wire proof (hashed key space).
     */
    public Optional<byte[]> getProofWire(byte[] key) {
        return inner.getProofWire(hashFn.digest(key));
    }

    /**
     * Generate an MPF inclusion/exclusion proof in PlutusData format suitable for
     * passing directly to Aiken validators.
     *
     * <p>This is a convenience method that combines {@link #getProofWire(byte[])} with
     * {@link MpfProofFormatter#toPlutusData(byte[])} to return a proof in the format
     * expected by Aiken's merkle-patricia-forestry library.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * SecureTrie trie = new SecureTrie(store);
     * trie.put("apple".getBytes(), "🍎".getBytes());
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

    public boolean verifyProofWire(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including, byte[] wire) {
        return inner.verifyProofWire(expectedRoot, key, valueOrNull, including, wire);
    }
}
