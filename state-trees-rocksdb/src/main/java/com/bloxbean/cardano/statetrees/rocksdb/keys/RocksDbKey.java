package com.bloxbean.cardano.statetrees.rocksdb.keys;

import java.util.Arrays;
import java.util.Objects;

/**
 * Abstract base class for type-safe RocksDB keys.
 *
 * <p>This class provides the foundation for a type-safe key system that prevents
 * key collision bugs and provides compile-time safety for RocksDB operations.
 * All concrete key types extend this class and provide specific validation and
 * formatting logic.</p>
 *
 * <p><b>Benefits of Type-Safe Keys:</b></p>
 * <ul>
 *   <li>Compile-time prevention of key type mixing bugs</li>
 *   <li>Self-documenting key structure and purpose</li>
 *   <li>Centralized key validation and encoding logic</li>
 *   <li>Easy to extend with new key types</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> All RocksDbKey implementations are immutable and thread-safe.</p>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public abstract class RocksDbKey {

    /**
     * The raw key bytes for storage in RocksDB.
     * Immutable after construction.
     */
    protected final byte[] keyBytes;

    /**
     * Constructs a new RocksDbKey with the specified key bytes.
     *
     * <p>Makes a defensive copy of the provided bytes to ensure immutability.</p>
     *
     * @param keyBytes the raw key bytes (must not be null)
     * @throws IllegalArgumentException if keyBytes is null
     */
    protected RocksDbKey(byte[] keyBytes) {
        this.keyBytes = Objects.requireNonNull(keyBytes, "Key bytes cannot be null").clone();
    }

    /**
     * Returns the raw key bytes for use in RocksDB operations.
     *
     * <p>Returns a defensive copy to maintain immutability of this key instance.</p>
     *
     * @return a copy of the key bytes
     */
    public final byte[] toBytes() {
        return keyBytes.clone();
    }

    /**
     * Returns the length of this key in bytes.
     *
     * @return the key length in bytes
     */
    public final int length() {
        return keyBytes.length;
    }

    /**
     * Checks if this key is equal to another object.
     *
     * <p>Two RocksDbKeys are equal if they have the same class and the same key bytes.</p>
     *
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public final boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RocksDbKey other = (RocksDbKey) obj;
        return Arrays.equals(keyBytes, other.keyBytes);
    }

    /**
     * Returns the hash code of this key.
     *
     * @return the hash code based on the key bytes and class
     */
    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), Arrays.hashCode(keyBytes));
    }

    /**
     * Returns a string representation of this key.
     *
     * <p>Subclasses should override this method to provide meaningful string representations.</p>
     *
     * @return a string representation of this key
     */
    @Override
    public String toString() {
        return String.format("%s[length=%d, bytes=%s]",
                getClass().getSimpleName(),
                keyBytes.length,
                bytesToHex(keyBytes));
    }

    /**
     * Converts a byte array to its hexadecimal string representation.
     *
     * @param bytes the bytes to convert
     * @return the hexadecimal string representation
     */
    protected static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Validates that a byte array has the expected length.
     *
     * @param bytes          the byte array to validate
     * @param expectedLength the expected length
     * @param description    a description of the key type for error messages
     * @throws IllegalArgumentException if the length is incorrect
     */
    protected static void validateLength(byte[] bytes, int expectedLength, String description) {
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException(
                    String.format("%s must be %d bytes, got %d bytes",
                            description, expectedLength, bytes.length));
        }
    }
}
