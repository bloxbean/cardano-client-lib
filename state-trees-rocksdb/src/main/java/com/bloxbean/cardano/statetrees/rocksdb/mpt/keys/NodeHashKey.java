package com.bloxbean.cardano.statetrees.rocksdb.mpt.keys;

/**
 * Type-safe key for MPT node hashes in RocksDB storage.
 *
 * <p>This key type represents 32-byte Blake2b-256 hashes used to identify
 * and retrieve MPT nodes from storage. The type safety prevents mixing
 * node hash keys with other key types like version keys or special keys.</p>
 *
 * <p><b>Key Properties:</b></p>
 * <ul>
 *   <li>Fixed 32-byte length (Blake2b-256 hash size)</li>
 *   <li>Immutable after construction</li>
 *   <li>Validates hash length at creation time</li>
 *   <li>Provides readable string representation</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * byte[] hash = Blake2b256.digest(nodeData);
 * NodeHashKey key = NodeHashKey.of(hash);
 *
 * // Type-safe storage operations
 * nodeStore.put(key, nodeData);
 * byte[] retrieved = nodeStore.get(key);
 * }</pre>
 *
 * @since 0.8.0
 */
public final class NodeHashKey extends RocksDbKey {

    /**
     * The expected length of a node hash in bytes (Blake2b-256).
     */
    public static final int HASH_LENGTH = 32;

    /**
     * The raw hash bytes.
     */
    private final byte[] hash;

    /**
     * Private constructor - use factory method {@link #of(byte[])} to create instances.
     *
     * @param hash the 32-byte hash value
     */
    private NodeHashKey(byte[] hash) {
        super(hash);
        this.hash = hash.clone();
    }

    /**
     * Creates a new NodeHashKey from a hash byte array.
     *
     * <p>Validates that the provided hash is exactly 32 bytes as required
     * by the Blake2b-256 hash algorithm used in MPT implementations.</p>
     *
     * @param hash the 32-byte hash value (must not be null)
     * @return a new NodeHashKey instance
     * @throws IllegalArgumentException if hash is null or not 32 bytes
     */
    public static NodeHashKey of(byte[] hash) {
        if (hash == null) {
            throw new IllegalArgumentException("Node hash cannot be null");
        }
        validateLength(hash, HASH_LENGTH, "Node hash");
        return new NodeHashKey(hash);
    }

    /**
     * Returns the hash bytes as a defensive copy.
     *
     * @return a copy of the hash bytes
     */
    public byte[] getHash() {
        return hash.clone();
    }

    /**
     * Returns a readable string representation of this node hash key.
     *
     * <p>Shows the first 8 characters of the hex representation for readability
     * while still being unique enough for debugging purposes.</p>
     *
     * @return a string representation like "NodeHashKey[a1b2c3d4...]"
     */
    @Override
    public String toString() {
        String hexHash = bytesToHex(hash);
        String shortHash = hexHash.length() > 8 ? hexHash.substring(0, 8) + "..." : hexHash;
        return String.format("NodeHashKey[%s]", shortHash);
    }
}
