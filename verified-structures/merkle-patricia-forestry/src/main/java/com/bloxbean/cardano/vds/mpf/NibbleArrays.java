package com.bloxbean.cardano.vds.mpf;

/**
 * Utility methods for nibble array operations.
 *
 * <p>This class provides common operations on integer arrays representing nibble paths
 * in the Merkle Patricia Forestry trie. Nibbles are 4-bit values (0-15) representing
 * hex digits in trie paths.</p>
 *
 * @since 0.8.0
 */
final class NibbleArrays {

    /**
     * Private constructor to prevent instantiation.
     */
    private NibbleArrays() {
    }

    /**
     * Creates a slice of an integer array.
     *
     * <p>Returns a new array containing elements from the specified range.
     * If {@code fromIndex} >= {@code toIndex}, returns an empty array.</p>
     *
     * @param array     the source array
     * @param fromIndex the starting index (inclusive)
     * @param toIndex   the ending index (exclusive)
     * @return a new array containing the specified range of elements
     */
    static int[] slice(int[] array, int fromIndex, int toIndex) {
        int length = Math.max(0, toIndex - fromIndex);
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = array[fromIndex + i];
        }
        return result;
    }

    /**
     * Concatenates two integer arrays.
     *
     * @param first  the first array
     * @param second the second array
     * @return a new array containing all elements from both arrays
     */
    static int[] concat(int[] first, int[] second) {
        int[] result = new int[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
