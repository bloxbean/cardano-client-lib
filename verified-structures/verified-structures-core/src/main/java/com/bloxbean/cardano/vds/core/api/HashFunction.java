package com.bloxbean.cardano.vds.core.api;

/**
 * Functional interface for cryptographic hash functions used in verifiable data structures.
 *
 * <p>This interface abstracts the hash function used for computing node hashes and commitments
 * in various data structures (such as Merkle Patricia Trie, Jellyfish Merkle Tree, and others),
 * allowing different hash algorithms to be plugged in. The default implementation uses
 * Blake2b-256, which is the standard for Cardano.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * HashFunction blake2b = Blake2b256::digest;
 * // Can be used with any structure: MPT, JMT, etc.
 * MerklePatriciaTrie mpt = new MerklePatriciaTrie(store, blake2b);
 * JellyfishMerkleTree jmt = new JellyfishMerkleTree(store, blake2b);
 * }</pre>
 *
 * @since 0.8.0
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
