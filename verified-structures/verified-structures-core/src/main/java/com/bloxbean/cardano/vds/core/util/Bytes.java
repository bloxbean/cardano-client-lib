package com.bloxbean.cardano.vds.core.util;

import com.bloxbean.cardano.client.util.HexUtil;

import java.util.Arrays;

/**
 * Utility class for byte array operations.
 *
 * <p>This class provides common operations for working with byte arrays in the context
 * of Merkle Patricia Trie operations, including concatenation, comparison, and
 * hexadecimal conversion utilities.</p>
 */
public final class Bytes {

    /**
     * Shared zero-length array constant to avoid repeated allocations.
     */
    public static final byte[] EMPTY = new byte[0];

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Bytes() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    /**
     * Concatenates multiple byte arrays into a single array.
     *
     * <p>Null arrays are ignored. This method efficiently combines arrays using
     * System.arraycopy for optimal performance.</p>
     *
     * @param arrays the byte arrays to concatenate (null arrays are ignored)
     * @return a new byte array containing all input arrays concatenated in order
     */
    public static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array == null ? 0 : array.length;
        }

        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            if (array == null) continue;
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    /**
     * Checks if a byte array is null or empty.
     *
     * @param bytes the byte array to check
     * @return true if the array is null or has length 0
     */
    public static boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    /**
     * Creates a copy of a portion of a byte array.
     *
     * @param source    the source array to copy from
     * @param fromIndex the start index (inclusive)
     * @param toIndex   the end index (exclusive)
     * @return a new array containing the specified range, or null if source is null
     */
    public static byte[] copyOfRange(byte[] source, int fromIndex, int toIndex) {
        if (source == null) return null;
        return Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    /**
     * Tests two byte arrays for equality.
     *
     * @param first  the first array
     * @param second the second array
     * @return true if both arrays contain the same elements in the same order
     */
    public static boolean equals(byte[] first, byte[] second) {
        return Arrays.equals(first, second);
    }

    /**
     * Converts a byte array to its hexadecimal string representation.
     *
     * @param bytes the byte array to convert
     * @return the hexadecimal string representation
     */
    public static String toHex(byte[] bytes) {
        return HexUtil.encodeHexString(bytes);
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * @param hexString the hexadecimal string to convert
     * @return the corresponding byte array
     */
    public static byte[] fromHex(String hexString) {
        return HexUtil.decodeHexString(hexString);
    }
}
