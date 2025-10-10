package com.bloxbean.cardano.statetrees.common;

import java.util.Arrays;
import java.util.Objects;

/**
 * Type-safe wrapper for node hashes in the Merkle Patricia Trie.
 *
 * <p>This immutable class provides a type-safe alternative to raw byte arrays
 * for representing node hashes, reducing the risk of parameter confusion and
 * improving code clarity. All hashes are expected to be 32 bytes (256 bits)
 * as produced by Blake2b-256.</p>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li>Type safety - prevents mixing up hash arrays with other byte arrays</li>
 *   <li>Immutability - hash values cannot be modified after creation</li>
 *   <li>Validation - ensures hash length is correct at construction time</li>
 *   <li>Proper equals/hashCode - enables safe use in collections</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * byte[] rawHash = Blake2b256.digest(nodeData);
 * NodeHash hash = NodeHash.of(rawHash);
 *
 * // Type-safe method signatures
 * void storeNode(NodeHash hash, byte[] nodeData) { ... }
 * }</pre>
 *
 * @since 0.8.0
 */
public final class NodeHash {

    /**
     * Expected length of Blake2b-256 hashes in bytes.
     */
    public static final int HASH_LENGTH = 32;

    private final byte[] hashBytes;

    /**
     * Private constructor to enforce use of factory methods.
     *
     * @param hashBytes the hash bytes (will be cloned for immutability)
     */
    private NodeHash(byte[] hashBytes) {
        this.hashBytes = hashBytes.clone();
    }

    /**
     * Creates a NodeHash from a byte array.
     *
     * @param hashBytes the hash bytes, must be exactly 32 bytes
     * @return a new NodeHash instance
     * @throws IllegalArgumentException if hashBytes is null or not 32 bytes
     */
    public static NodeHash of(byte[] hashBytes) {
        Objects.requireNonNull(hashBytes, "Hash bytes cannot be null");
        if (hashBytes.length != HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "Hash must be " + HASH_LENGTH + " bytes, got " + hashBytes.length);
        }
        return new NodeHash(hashBytes);
    }

    /**
     * Returns the hash as a byte array.
     *
     * <p>The returned array is a defensive copy to maintain immutability.</p>
     *
     * @return a copy of the hash bytes
     */
    public byte[] getBytes() {
        return hashBytes.clone();
    }

    /**
     * Returns the hash length in bytes.
     *
     * @return always returns {@link #HASH_LENGTH}
     */
    public int getLength() {
        return HASH_LENGTH;
    }

    /**
     * Returns the raw hash bytes as a defensive copy.
     *
     * @return a copy of the hash bytes
     */
    public byte[] toBytes() {
        return hashBytes.clone();
    }

    /**
     * Returns a hexadecimal string representation of this hash.
     *
     * @return lowercase hex string (64 characters)
     */
    public String toHexString() {
        StringBuilder sb = new StringBuilder(HASH_LENGTH * 2);
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Creates a NodeHash from a hexadecimal string.
     *
     * @param hexString hex string (64 characters, case-insensitive)
     * @return a new NodeHash instance
     * @throws IllegalArgumentException if hexString is invalid
     */
    public static NodeHash fromHexString(String hexString) {
        Objects.requireNonNull(hexString, "Hex string cannot be null");

        String cleanHex = hexString.toLowerCase();
        if (cleanHex.startsWith("0x")) {
            cleanHex = cleanHex.substring(2);
        }

        if (cleanHex.length() != HASH_LENGTH * 2) {
            throw new IllegalArgumentException(
                    "Hex string must be " + (HASH_LENGTH * 2) + " characters, got " + cleanHex.length());
        }

        byte[] bytes = new byte[HASH_LENGTH];
        try {
            for (int i = 0; i < HASH_LENGTH; i++) {
                int high = Character.digit(cleanHex.charAt(i * 2), 16);
                int low = Character.digit(cleanHex.charAt(i * 2 + 1), 16);
                if (high == -1 || low == -1) {
                    throw new IllegalArgumentException("Invalid hex character in: " + hexString);
                }
                bytes[i] = (byte) ((high << 4) | low);
            }
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid hex string format: " + hexString, e);
        }

        return new NodeHash(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NodeHash nodeHash = (NodeHash) obj;
        return Arrays.equals(hashBytes, nodeHash.hashBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hashBytes);
    }

    @Override
    public String toString() {
        return "NodeHash{" + toHexString() + "}";
    }
}
