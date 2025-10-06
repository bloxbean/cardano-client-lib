package com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.modern;

import com.bloxbean.cardano.statetrees.rocksdb.mpt.keys.RocksDbKey;

import java.util.Arrays;

/**
 * Type-safe key for MPT node storage using 32-byte hash identifiers.
 *
 * <p>This class provides type safety for node hash keys, preventing
 * accidental mixing of node hashes with other types of keys in the
 * storage layer. It enforces the 32-byte hash requirement and provides
 * efficient equality and hashing operations.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Compile-time type safety for node hash keys</li>
 *   <li>Immutable design with defensive copying</li>
 *   <li>Efficient equals/hashCode implementation</li>
 *   <li>Clear validation of hash length requirements</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create from 32-byte hash
 * byte[] hash = computeNodeHash(nodeData);
 * NodeHashKey key = NodeHashKey.of(hash);
 *
 * // Use with storage operations
 * Optional<byte[]> nodeData = repository.getNode(key);
 * repository.putNode(key, updatedData);
 *
 * // Type safety prevents mixing with other keys
 * RootHashKey rootKey = ...; // Different type
 * repository.getNode(rootKey); // Compilation error
 * }</pre>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class NodeHashKey extends RocksDbKey {

    /**
     * Required length for node hashes (32 bytes for SHA-256).
     */
    public static final int HASH_LENGTH = 32;

    /**
     * Private constructor - use factory methods for creation.
     *
     * @param hash the 32-byte node hash
     */
    private NodeHashKey(byte[] hash) {
        super(hash.clone()); // Defensive copy
    }

    /**
     * Creates a NodeHashKey from a 32-byte hash.
     *
     * @param hash the node hash (must be exactly 32 bytes)
     * @return a new NodeHashKey instance
     * @throws IllegalArgumentException if hash is not 32 bytes
     */
    public static NodeHashKey of(byte[] hash) {
        validateHashLength(hash, HASH_LENGTH, "Node hash");
        return new NodeHashKey(hash);
    }

    /**
     * Creates a NodeHashKey from a hex string representation.
     *
     * @param hexHash the node hash as a hex string (64 characters)
     * @return a new NodeHashKey instance
     * @throws IllegalArgumentException if hex string is invalid
     */
    public static NodeHashKey fromHex(String hexHash) {
        if (hexHash == null) {
            throw new IllegalArgumentException("Hex hash cannot be null");
        }
        if (hexHash.length() != HASH_LENGTH * 2) {
            throw new IllegalArgumentException("Hex hash must be " + (HASH_LENGTH * 2) + " characters, got: " + hexHash.length());
        }

        try {
            byte[] hash = hexStringToByteArray(hexHash);
            return new NodeHashKey(hash);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex characters in hash: " + hexHash, e);
        }
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
        return String.format("NodeHashKey[%s...]", toShortHex());
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
