package com.bloxbean.cardano.statetrees.rocksdb.mpt.keys;

/**
 * Type-safe key for reference count storage in the garbage collection system.
 *
 * <p>This key type represents reference count entries stored alongside
 * node data in RocksDB. Reference counts are stored with a special prefix
 * to distinguish them from actual node data while keeping them in the
 * same column family for atomic updates.</p>
 *
 * <p><b>Key Structure:</b></p>
 * <ul>
 *   <li>Byte 0: Prefix (0xF0) to identify refcount entries</li>
 *   <li>Bytes 1-32: The 32-byte node hash being counted</li>
 *   <li>Total length: 33 bytes</li>
 * </ul>
 *
 * <p><b>Design Rationale:</b></p>
 * <ul>
 *   <li>Co-located with node data for atomic batch operations</li>
 *   <li>Prefix-based separation prevents key collisions</li>
 *   <li>Same column family enables consistent snapshots</li>
 *   <li>Type safety prevents mixing with other key types</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * NodeHashKey nodeKey = NodeHashKey.of(hashBytes);
 * RefcountKey refKey = RefcountKey.forNode(nodeKey);
 *
 * // Store reference count alongside node data in same batch
 * batch.put(cfNodes, nodeKey, nodeData);
 * batch.put(cfNodes, refKey, refCountBytes);
 * }</pre>
 *
 * @since 0.8.0
 */
public final class RefcountKey extends RocksDbKey {

    /**
     * The prefix byte used to identify reference count entries.
     * This byte (0xF0) is unlikely to be the first byte of a valid
     * Blake2b-256 hash, providing good separation.
     */
    public static final byte REFCOUNT_PREFIX = (byte) 0xF0;

    /**
     * The expected length of a refcount key in bytes (1 prefix + 32 hash).
     */
    public static final int REFCOUNT_KEY_LENGTH = 1 + NodeHashKey.HASH_LENGTH;

    /**
     * The node hash this refcount key refers to.
     */
    private final byte[] nodeHash;

    /**
     * Private constructor - use factory method {@link #forNode(NodeHashKey)} to create instances.
     *
     * @param nodeHash the 32-byte node hash being counted
     */
    private RefcountKey(byte[] nodeHash) {
        super(createPrefixedKey(nodeHash));
        this.nodeHash = nodeHash.clone();
    }

    /**
     * Creates a RefcountKey for the specified node hash key.
     *
     * <p>This is the preferred way to create refcount keys as it ensures
     * type safety and correct key construction from a validated NodeHashKey.</p>
     *
     * @param nodeKey the node hash key to create a refcount key for (must not be null)
     * @return a new RefcountKey instance
     * @throws IllegalArgumentException if nodeKey is null
     */
    public static RefcountKey forNode(NodeHashKey nodeKey) {
        if (nodeKey == null) {
            throw new IllegalArgumentException("Node key cannot be null");
        }
        return new RefcountKey(nodeKey.getHash());
    }

    /**
     * Creates a RefcountKey directly from node hash bytes.
     *
     * <p>This method is provided for cases where you have raw hash bytes
     * but not a NodeHashKey instance. The preferred approach is to use
     * {@link #forNode(NodeHashKey)} for better type safety.</p>
     *
     * @param nodeHash the 32-byte node hash (must not be null and must be 32 bytes)
     * @return a new RefcountKey instance
     * @throws IllegalArgumentException if nodeHash is null or not 32 bytes
     */
    public static RefcountKey of(byte[] nodeHash) {
        if (nodeHash == null) {
            throw new IllegalArgumentException("Node hash cannot be null");
        }
        validateLength(nodeHash, NodeHashKey.HASH_LENGTH, "Node hash for refcount key");
        return new RefcountKey(nodeHash);
    }

    /**
     * Creates a RefcountKey from existing prefixed key bytes.
     *
     * <p>Used when reconstructing keys from RocksDB iterator results.
     * Validates the key structure and extracts the node hash.</p>
     *
     * @param prefixedKeyBytes the 33-byte prefixed key representation
     * @return a new RefcountKey instance
     * @throws IllegalArgumentException if key structure is invalid
     */
    public static RefcountKey fromBytes(byte[] prefixedKeyBytes) {
        validateLength(prefixedKeyBytes, REFCOUNT_KEY_LENGTH, "Refcount key");

        if (prefixedKeyBytes[0] != REFCOUNT_PREFIX) {
            throw new IllegalArgumentException(
                    String.format("Invalid refcount key prefix: expected 0x%02X, got 0x%02X",
                            REFCOUNT_PREFIX, prefixedKeyBytes[0]));
        }

        // Extract the node hash (bytes 1-32)
        byte[] nodeHash = new byte[NodeHashKey.HASH_LENGTH];
        System.arraycopy(prefixedKeyBytes, 1, nodeHash, 0, NodeHashKey.HASH_LENGTH);

        return new RefcountKey(nodeHash);
    }

    /**
     * Returns the node hash this refcount key refers to.
     *
     * @return a copy of the node hash bytes
     */
    public byte[] getNodeHash() {
        return nodeHash.clone();
    }

    /**
     * Returns the corresponding NodeHashKey for this refcount key.
     *
     * <p>Useful for operations that need to work with both the node
     * data and its reference count in a type-safe manner.</p>
     *
     * @return the NodeHashKey for the referenced node
     */
    public NodeHashKey getNodeHashKey() {
        return NodeHashKey.of(nodeHash);
    }

    /**
     * Checks if this refcount key is for the specified node hash key.
     *
     * @param nodeKey the node key to check against
     * @return true if this refcount key is for the specified node, false otherwise
     */
    public boolean isForNode(NodeHashKey nodeKey) {
        return nodeKey != null && nodeKey.equals(getNodeHashKey());
    }

    /**
     * Creates the prefixed key bytes from a node hash.
     *
     * @param nodeHash the 32-byte node hash
     * @return the 33-byte prefixed key
     */
    private static byte[] createPrefixedKey(byte[] nodeHash) {
        byte[] prefixedKey = new byte[REFCOUNT_KEY_LENGTH];
        prefixedKey[0] = REFCOUNT_PREFIX;
        System.arraycopy(nodeHash, 0, prefixedKey, 1, nodeHash.length);
        return prefixedKey;
    }

    /**
     * Returns a readable string representation of this refcount key.
     *
     * <p>Shows the first 8 characters of the node hash for readability.</p>
     *
     * @return a string representation like "RefcountKey[node=a1b2c3d4...]"
     */
    @Override
    public String toString() {
        String hexHash = bytesToHex(nodeHash);
        String shortHash = hexHash.length() > 8 ? hexHash.substring(0, 8) + "..." : hexHash;
        return String.format("RefcountKey[node=%s]", shortHash);
    }
}
