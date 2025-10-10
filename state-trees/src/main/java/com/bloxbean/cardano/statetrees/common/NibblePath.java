package com.bloxbean.cardano.statetrees.common;

import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;

import java.util.Arrays;
import java.util.Objects;

/**
 * Type-safe wrapper for nibble paths in the Merkle Patricia Trie.
 *
 * <p>This immutable class provides a type-safe alternative to raw int arrays
 * for representing nibble sequences, improving code clarity and reducing the
 * risk of parameter confusion. Nibbles are 4-bit values (0-15) used for
 * efficient path compression in the trie structure.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Type safety - prevents mixing up nibble arrays with other int arrays</li>
 *   <li>Immutability - nibble values cannot be modified after creation</li>
 *   <li>Validation - ensures all values are valid nibbles (0-15)</li>
 *   <li>Rich API - provides common operations like prefix matching and slicing</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // From byte array key
 * NibblePath keyPath = NibblePath.fromBytes("hello".getBytes());
 *
 * // From hex string
 * NibblePath hexPath = NibblePath.fromHexString("68656c6c6f");
 *
 * // Path operations
 * NibblePath suffix = keyPath.slice(2, keyPath.length());
 * boolean matches = keyPath.startsWith(NibblePath.fromHexString("68"));
 * }</pre>
 *
 * @since 0.8.0
 */
public final class NibblePath implements Comparable<NibblePath> {

    /**
     * Empty nibble path constant.
     */
    public static final NibblePath EMPTY = new NibblePath(new int[0]);

    private final int[] nibbles;

    /**
     * Private constructor to enforce use of factory methods.
     *
     * @param nibbles the nibble array (will be cloned for immutability)
     */
    private NibblePath(int[] nibbles) {
        this(nibbles, true);
    }

    private NibblePath(int[] nibbles, boolean cloneArray) {
        this.nibbles = cloneArray ? nibbles.clone() : nibbles;
    }

    /**
     * Creates a NibblePath from a nibble array.
     *
     * @param nibbles array of nibbles, each must be 0-15
     * @return a new NibblePath instance
     * @throws IllegalArgumentException if any value is not a valid nibble
     */
    public static NibblePath of(int... nibbles) {
        Objects.requireNonNull(nibbles, "Nibbles array cannot be null");
        validateNibbles(nibbles);
        return new NibblePath(nibbles);
    }

    /**
     * Creates a NibblePath directly from a raw nibble array. The caller must
     * not mutate the array after passing it to this method.
     */
    public static NibblePath fromRaw(int[] nibbles) {
        Objects.requireNonNull(nibbles, "Nibbles array cannot be null");
        validateNibbles(nibbles);
        return new NibblePath(nibbles, false);
    }

    /**
     * Creates a NibblePath from a sub-range of an existing nibble array.
     */
    public static NibblePath fromRange(int[] nibbles, int start, int length) {
        Objects.requireNonNull(nibbles, "Nibbles array cannot be null");
        if (start < 0 || length < 0 || start + length > nibbles.length) {
            throw new IndexOutOfBoundsException(
                    "Invalid range start=" + start + " length=" + length + " for array of length " + nibbles.length);
        }
        if (length == 0) {
            return EMPTY;
        }
        int[] copy = new int[length];
        System.arraycopy(nibbles, start, copy, 0, length);
        validateNibbles(copy);
        return new NibblePath(copy, false);
    }

    /**
     * Creates a NibblePath from a byte array.
     *
     * <p>Each byte is split into two nibbles (high and low 4 bits).</p>
     *
     * @param bytes the byte array to convert
     * @return a new NibblePath instance
     * @throws NullPointerException if bytes is null
     */
    public static NibblePath fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "Bytes array cannot be null");
        return new NibblePath(Nibbles.toNibbles(bytes));
    }

    /**
     * Creates a NibblePath from a hexadecimal string.
     *
     * @param hexString hex string (case-insensitive, optional 0x prefix)
     * @return a new NibblePath instance
     * @throws IllegalArgumentException if hexString contains invalid characters
     */
    public static NibblePath fromHexString(String hexString) {
        Objects.requireNonNull(hexString, "Hex string cannot be null");

        String cleanHex = hexString.toLowerCase();
        if (cleanHex.startsWith("0x")) {
            cleanHex = cleanHex.substring(2);
        }

        // Handle odd length by padding with leading zero
        if (cleanHex.length() % 2 == 1) {
            cleanHex = "0" + cleanHex;
        }

        int[] nibbles = new int[cleanHex.length()];
        for (int i = 0; i < cleanHex.length(); i++) {
            int nibble = Character.digit(cleanHex.charAt(i), 16);
            if (nibble == -1) {
                throw new IllegalArgumentException("Invalid hex character at position " + i + ": " + cleanHex.charAt(i));
            }
            nibbles[i] = nibble;
        }

        return new NibblePath(nibbles);
    }

    /**
     * Returns the nibbles as an array.
     *
     * <p>The returned array is a defensive copy to maintain immutability.</p>
     *
     * <p><b>Performance Note:</b> This method allocates a new array on every call.
     * For performance-critical code that only needs read access, prefer using
     * {@link #length()}, {@link #get(int)}, or {@link #copyNibbles(int[], int)} instead.
     *
     * @return a copy of the nibbles array
     */
    public int[] getNibbles() {
        return nibbles.clone();
    }

    /**
     * Zero-allocation bulk copy of nibbles to a pre-allocated destination array.
     *
     * <p>This method is optimized for performance-critical paths where avoiding
     * allocations is important. Instead of creating a new array (like {@link #getNibbles()}),
     * this copies nibbles to a caller-provided buffer.
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * // Reuse buffer across multiple reads
     * int[] buffer = new int[maxPathLength];
     * for (NibblePath path : paths) {
     *     path.copyNibbles(buffer, 0);
     *     processNibbles(buffer, 0, path.length());
     * }
     * }</pre>
     *
     * @param dest the destination array
     * @param destPos starting position in the destination array
     * @throws NullPointerException if dest is null
     * @throws IndexOutOfBoundsException if copying would cause access outside array bounds
     * @since 0.6.1
     */
    public void copyNibbles(int[] dest, int destPos) {
        Objects.requireNonNull(dest, "Destination array cannot be null");
        System.arraycopy(nibbles, 0, dest, destPos, nibbles.length);
    }

    /**
     * Returns the length of this nibble path.
     *
     * @return the number of nibbles
     */
    public int length() {
        return nibbles.length;
    }

    /**
     * Checks if this path is empty.
     *
     * @return true if the path contains no nibbles
     */
    public boolean isEmpty() {
        return nibbles.length == 0;
    }

    /**
     * Returns the nibble at the specified index.
     *
     * @param index the index (0-based)
     * @return the nibble value (0-15)
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public int get(int index) {
        return nibbles[index];
    }

    /**
     * Returns a slice of this path.
     *
     * @param start the start index (inclusive)
     * @param end   the end index (exclusive)
     * @return a new NibblePath containing the specified range
     * @throws IndexOutOfBoundsException if indices are out of range
     */
    public NibblePath slice(int start, int end) {
        if (start < 0 || end > nibbles.length || start > end) {
            throw new IndexOutOfBoundsException(
                    "Invalid slice range [" + start + ", " + end + ") for length " + nibbles.length);
        }
        return new NibblePath(Arrays.copyOfRange(nibbles, start, end));
    }

    /**
     * Returns a suffix starting from the specified index.
     *
     * @param start the start index (inclusive)
     * @return a new NibblePath containing the suffix
     * @throws IndexOutOfBoundsException if start is out of range
     */
    public NibblePath suffix(int start) {
        return slice(start, nibbles.length);
    }

    /**
     * Returns a prefix of the specified length.
     *
     * @param length the desired prefix length
     * @return a new NibblePath containing the prefix
     * @throws IndexOutOfBoundsException if length is out of range
     */
    public NibblePath prefix(int length) {
        return slice(0, length);
    }

    /**
     * Checks if this path starts with the specified prefix.
     *
     * @param prefix the prefix to check
     * @return true if this path starts with the prefix
     */
    public boolean startsWith(NibblePath prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        if (prefix.length() > nibbles.length) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (nibbles[i] != prefix.nibbles[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the length of the common prefix with another path.
     *
     * @param other the other path to compare
     * @return the length of the common prefix
     */
    public int commonPrefixLength(NibblePath other) {
        Objects.requireNonNull(other, "Other path cannot be null");
        int minLength = Math.min(nibbles.length, other.nibbles.length);
        int commonLength = 0;
        for (int i = 0; i < minLength; i++) {
            if (nibbles[i] == other.nibbles[i]) {
                commonLength++;
            } else {
                break;
            }
        }
        return commonLength;
    }

    /**
     * Concatenates this path with another path.
     *
     * @param other the path to append
     * @return a new NibblePath containing the concatenated paths
     */
    public NibblePath concat(NibblePath other) {
        Objects.requireNonNull(other, "Other path cannot be null");
        int[] result = new int[nibbles.length + other.nibbles.length];
        System.arraycopy(nibbles, 0, result, 0, nibbles.length);
        System.arraycopy(other.nibbles, 0, result, nibbles.length, other.nibbles.length);
        return new NibblePath(result);
    }

    /**
     * Converts this nibble path to a byte array.
     *
     * <p>If the path has odd length, a zero nibble is prepended.</p>
     *
     * @return the byte array representation
     */
    public byte[] toBytes() {
        // If odd length, prepend a zero nibble to make it even
        if (nibbles.length % 2 == 1) {
            int[] paddedNibbles = new int[nibbles.length + 1];
            paddedNibbles[0] = 0;
            System.arraycopy(nibbles, 0, paddedNibbles, 1, nibbles.length);
            return Nibbles.fromNibbles(paddedNibbles);
        }
        return Nibbles.fromNibbles(nibbles);
    }

    /**
     * Returns a hexadecimal string representation.
     *
     * @return lowercase hex string
     */
    public String toHexString() {
        StringBuilder sb = new StringBuilder(nibbles.length);
        for (int nibble : nibbles) {
            sb.append(Character.forDigit(nibble, 16));
        }
        return sb.toString();
    }

    /**
     * Validates that all values in the array are valid nibbles (0-15).
     *
     * @param nibbles the array to validate
     * @throws IllegalArgumentException if any value is not a valid nibble
     */
    private static void validateNibbles(int[] nibbles) {
        for (int i = 0; i < nibbles.length; i++) {
            if (nibbles[i] < 0 || nibbles[i] > 15) {
                throw new IllegalArgumentException(
                        "Invalid nibble at index " + i + ": " + nibbles[i] + " (must be 0-15)");
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NibblePath that = (NibblePath) obj;
        return Arrays.equals(nibbles, that.nibbles);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(nibbles);
    }

    @Override
    public String toString() {
        return "NibblePath{" + toHexString() + "}";
    }

    @Override
    public int compareTo(NibblePath other) {
        Objects.requireNonNull(other, "other");
        int min = Math.min(this.nibbles.length, other.nibbles.length);
        for (int i = 0; i < min; i++) {
            int diff = Integer.compare(this.nibbles[i], other.nibbles[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return Integer.compare(this.nibbles.length, other.nibbles.length);
    }
}
