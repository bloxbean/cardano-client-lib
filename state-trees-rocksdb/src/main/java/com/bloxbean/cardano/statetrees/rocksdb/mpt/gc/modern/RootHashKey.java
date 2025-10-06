package com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.modern;

import com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RocksDbKey;

import java.util.Arrays;

/**
 * Type-safe key for MPT root hash storage using 32-byte hash identifiers.
 *
 * <p>This class provides type safety for root hash keys, distinguishing them
 * from regular node hash keys even though they use the same underlying format.
 * This distinction is important for GC algorithms that need to treat roots
 * differently from regular nodes.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Type-safe distinction from NodeHashKey</li>
 *   <li>Immutable design with defensive copying</li>
 *   <li>Efficient equals/hashCode implementation</li>
 *   <li>Conversion to/from NodeHashKey when needed</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create from 32-byte root hash
 * byte[] rootHash = getCurrentRootHash();
 * RootHashKey rootKey = RootHashKey.of(rootHash);
 *
 * // Store as versioned root
 * repository.putRoot(version, rootKey);
 *
 * // Traverse from root to get all reachable nodes
 * Stream<NodeHashKey> reachableNodes = repository.traverseFromRoot(rootKey);
 *
 * // Convert to NodeHashKey when needed for node operations
 * NodeHashKey nodeKey = rootKey.toNodeHashKey();
 * Optional<byte[]> rootNodeData = repository.getNode(nodeKey);
 * }</pre>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class RootHashKey extends RocksDbKey {

    /**
     * Required length for root hashes (32 bytes for SHA-256).
     */
    public static final int HASH_LENGTH = 32;

    /**
     * Private constructor - use factory methods for creation.
     *
     * @param hash the 32-byte root hash
     */
    private RootHashKey(byte[] hash) {
        super(hash.clone()); // Defensive copy
    }

    /**
     * Creates a RootHashKey from a 32-byte hash.
     *
     * @param hash the root hash (must be exactly 32 bytes)
     * @return a new RootHashKey instance
     * @throws IllegalArgumentException if hash is not 32 bytes
     */
    public static RootHashKey of(byte[] hash) {
        validateHashLength(hash, HASH_LENGTH, "Root hash");
        return new RootHashKey(hash);
    }

    /**
     * Creates a RootHashKey from a hex string representation.
     *
     * @param hexHash the root hash as a hex string (64 characters)
     * @return a new RootHashKey instance
     * @throws IllegalArgumentException if hex string is invalid
     */
    public static RootHashKey fromHex(String hexHash) {
        if (hexHash == null) {
            throw new IllegalArgumentException("Hex hash cannot be null");
        }
        if (hexHash.length() != HASH_LENGTH * 2) {
            throw new IllegalArgumentException("Hex hash must be " + (HASH_LENGTH * 2) + " characters, got: " + hexHash.length());
        }

        try {
            byte[] hash = hexStringToByteArray(hexHash);
            return new RootHashKey(hash);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex characters in hash: " + hexHash, e);
        }
    }

    /**
     * Creates a RootHashKey from a NodeHashKey.
     *
     * <p>This conversion is useful when a node hash is known to be a root hash
     * and needs to be treated as such for GC operations.</p>
     *
     * @param nodeKey the node hash key to convert
     * @return a new RootHashKey with the same hash
     */
    public static RootHashKey fromNodeHashKey(NodeHashKey nodeKey) {
        return new RootHashKey(nodeKey.toBytes());
    }

    /**
     * Converts this root hash key to a node hash key.
     *
     * <p>This conversion is useful when root node data needs to be accessed
     * through the node storage interface.</p>
     *
     * @return a NodeHashKey with the same hash
     */
    public NodeHashKey toNodeHashKey() {
        return NodeHashKey.of(keyBytes);
    }

    /**
     * Gets the hash bytes as a defensive copy.
     *
     * @return a copy of the hash bytes
     */
    public byte[] getHash() {
        return keyBytes.clone();
    }

    /**
     * Gets the hash as a hex string.
     *
     * @return the hash as a lowercase hex string
     */
    public String toHexString() {
        return byteArrayToHexString(keyBytes);
    }

    /**
     * Gets a shortened hex representation for display purposes.
     *
     * @return the first 8 characters of the hex hash
     */
    public String toShortHex() {
        return toHexString().substring(0, 8);
    }


    @Override
    public String toString() {
        return String.format("RootHashKey[%s...]", toShortHex());
    }

    /**
     * Converts hex string to byte array.
     *
     * @param hexString the hex string to convert
     * @return the corresponding byte array
     */
    private static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Converts byte array to hex string.
     *
     * @param bytes the byte array to convert
     * @return the corresponding hex string
     */
    private static String byteArrayToHexString(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Validates that a byte array has the expected length.
     *
     * @param data           the data to validate
     * @param expectedLength the expected length
     * @param description    description for error messages
     * @throws IllegalArgumentException if length is incorrect
     */
    private static void validateHashLength(byte[] data, int expectedLength, String description) {
        if (data == null) {
            throw new IllegalArgumentException(description + " cannot be null");
        }
        if (data.length != expectedLength) {
            throw new IllegalArgumentException(description + " must be " + expectedLength + " bytes, got: " + data.length);
        }
    }
}
