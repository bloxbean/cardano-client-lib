package com.bloxbean.cardano.statetrees.common.nibbles;

import java.util.Arrays;

/**
 * Utility class for nibble (4-bit value) operations and Hex-Prefix (HP) encoding.
 *
 * <p>Nibbles are fundamental to the Merkle Patricia Trie implementation, as the trie
 * uses hexary (16-way) branching based on nibble values. This class provides:</p>
 * <ul>
 *   <li>Conversion between bytes and nibble arrays</li>
 *   <li>Hex-Prefix encoding for path compression</li>
 *   <li>Common prefix length calculation</li>
 * </ul>
 *
 * <p><b>Hex-Prefix Encoding:</b> HP encoding compresses nibble paths by packing them
 * into bytes with a header nibble that indicates:</p>
 * <ul>
 *   <li>Bit 1 (value 2): Whether this is a leaf node (1) or extension node (0)</li>
 *   <li>Bit 0 (value 1): Whether the path has odd length (1) or even length (0)</li>
 * </ul>
 *
 * <p>Examples:</p>
 * <pre>
 * HP([1,2,3,4], leaf=true)     → [0x20, 0x12, 0x34] (even length leaf)
 * HP([1,2,3], leaf=true)       → [0x31, 0x23]       (odd length leaf)
 * HP([1,2,3,4], leaf=false)    → [0x00, 0x12, 0x34] (even length extension)
 * HP([1,2,3], leaf=false)      → [0x11, 0x23]       (odd length extension)
 * </pre>
 */
public final class Nibbles {
  private Nibbles() {}

  public static int[] toNibbles(byte[] key) {
    int[] n = new int[key.length * 2];
    for (int i = 0; i < key.length; i++) {
      n[2 * i] = (key[i] >> 4) & 0xF;
      n[2 * i + 1] = key[i] & 0xF;
    }
    return n;
  }

  public static int commonPrefixLen(int[] a, int[] b) {
    int L = Math.min(a.length, b.length);
    int i = 0;
    while (i < L && a[i] == b[i]) i++;
    return i;
  }

  public static byte[] packHP(boolean isLeaf, int[] nibbles) {
    boolean odd = (nibbles.length % 2) == 1;
    int prefix = (isLeaf ? 2 : 0) | (odd ? 1 : 0);
    // For odd, first nibble is embedded in header low nibble, leaving (n-1) nibbles => (n/2) bytes (floor)
    // For even, there are n/2 bytes. So unified: 1 + (nibbles.length / 2)
    int outLen = 1 + (nibbles.length / 2);
    byte[] out = new byte[outLen];
    int idx = 0;
    if (odd) {
      out[idx++] = (byte) ((prefix << 4) | (nibbles[0] & 0xF));
      for (int i = 1; i < nibbles.length; i += 2) {
        int hi = nibbles[i] & 0xF;
        int lo = (i + 1 < nibbles.length) ? nibbles[i + 1] & 0xF : 0;
        out[idx++] = (byte) ((hi << 4) | lo);
      }
    } else {
      out[idx++] = (byte) ((prefix << 4));
      for (int i = 0; i < nibbles.length; i += 2) {
        int hi = nibbles[i] & 0xF;
        int lo = (i + 1 < nibbles.length) ? nibbles[i + 1] & 0xF : 0;
        out[idx++] = (byte) ((hi << 4) | lo);
      }
    }
    return out;
  }

  public static HP unpackHP(byte[] hp) {
    if (hp.length == 0) return new HP(false, new int[0]);
    int header = (hp[0] >> 4) & 0xF;
    boolean isLeaf = (header & 0x2) != 0;
    boolean odd = (header & 0x1) != 0;
    int start = 1;
    int nibbleCount = (hp.length - 1) * 2 + (odd ? 1 : 0);
    int[] nibbles = new int[nibbleCount];
    int idx = 0;
    if (odd) {
      nibbles[idx++] = hp[0] & 0xF;
    }
    for (int i = start; i < hp.length; i++) {
      nibbles[idx++] = (hp[i] >> 4) & 0xF;
      if (idx < nibbles.length)
        nibbles[idx++] = hp[i] & 0xF;
    }
    return new HP(isLeaf, nibbles);
  }

  public static byte[] fromNibbles(int[] nibbles) {
    int len = (nibbles.length + 1) / 2;
    byte[] out = new byte[len];
    int idx = 0;
    for (int i = 0; i < nibbles.length; i += 2) {
      int hi = nibbles[i] & 0xF;
      int lo = (i + 1 < nibbles.length) ? nibbles[i + 1] & 0xF : 0;
      out[idx++] = (byte) ((hi << 4) | lo);
    }
    return out;
  }

  public static final class HP {
    public final boolean isLeaf;
    public final int[] nibbles;
    public HP(boolean isLeaf, int[] nibbles) {
      this.isLeaf = isLeaf;
      this.nibbles = nibbles == null ? new int[0] : Arrays.copyOf(nibbles, nibbles.length);
    }
  }
}
