package com.bloxbean.cardano.statetrees.api;

/**
 * Functional interface for cryptographic hash functions used in Merkle Patricia Trie operations.
 *
 * <p>This interface abstracts the hash function used for computing node hashes in the MPT,
 * allowing different hash algorithms to be plugged in. The default implementation uses
 * Blake2b-256, which is the standard for Cardano.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * HashFunction blake2b = Blake2b256::digest;
 * MerklePatriciaTrie trie = new MerklePatriciaTrie(store, blake2b);
 * }</pre>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
@FunctionalInterface
public interface HashFunction {
    /**
     * Computes the cryptographic hash of the input data.
     *
     * @param in the input byte array to hash
     * @return the hash digest as a byte array (typically 32 bytes for Blake2b-256)
     * @throws NullPointerException if input is null
     */
    byte[] digest(byte[] in);
}

