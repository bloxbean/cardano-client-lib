package com.bloxbean.cardano.statetrees.jmt.mpf;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.util.Bytes;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MpfProof {

  private final List<Step> steps;

  MpfProof(List<Step> steps) {
    this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
  }

  public List<Step> steps() {
    return steps;
  }

  public byte[] computeRoot(byte[] key, byte[] value, boolean including, HashFunction hashFn, CommitmentScheme commitments) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(hashFn, "hashFn");
    Objects.requireNonNull(commitments, "commitments");

    String pathHex = Bytes.toHex(hashFn.digest(key));
    if (steps.isEmpty()) {
      if (!including) {
        return null;
      }
      if (value == null) {
        throw new IllegalArgumentException("Value required for inclusion proof");
      }
      return commitments.commitLeaf(pathToNibblePath(pathHex), hashFn.digest(value));
    }

    return loop(0, 0, pathHex, value, including, hashFn, commitments, commitments.nullHash());
  }

  private byte[] loop(int cursor, int ix, String pathHex, byte[] value, boolean including,
                      HashFunction hashFn, CommitmentScheme commitments, byte[] nullHash) {
    if (ix >= steps.size()) {
      if (!including) {
        return null;
      }
      if (value == null) {
        throw new IllegalArgumentException("Value required for inclusion proof");
      }
      String suffixHex = pathHex.substring(cursor);
      return commitments.commitLeaf(nibblesFromHex(suffixHex), hashFn.digest(value));
    }

    Step step = steps.get(ix);
    int nextCursor = cursor + 1 + step.skip();
    byte[] childHash = loop(nextCursor, ix + 1, pathHex, value, including, hashFn, commitments, nullHash);
    int nibble = hexCharToNibble(pathHex.charAt(nextCursor - 1));
    boolean isLastStep = ix + 1 == steps.size();

    if (childHash == null) {
      childHash = nullHash;
    }

    if (step instanceof BranchStep) {
      BranchStep br = (BranchStep) step;
      byte[] merkle = aggregateSiblingHashes(nibble, childHash, br.neighbors(), hashFn, nullHash);
      byte[] prefixBytes = nibbleBytes(pathHex.substring(cursor, nextCursor - 1));
      return hashFn.digest(Bytes.concat(prefixBytes, merkle));
    } else if (step instanceof ForkStep) {
      ForkStep fork = (ForkStep) step;
      if (!including && isLastStep) {
        byte[] prefixBytes;
        if (fork.skip() == 0) {
          prefixBytes = Bytes.concat(new byte[] {(byte) fork.nibble()}, fork.prefix());
        } else {
          byte[] skipped = nibbleBytes(pathHex.substring(cursor, cursor + fork.skip()));
          prefixBytes = Bytes.concat(skipped, new byte[] {(byte) fork.nibble()}, fork.prefix());
        }
        return hashFn.digest(Bytes.concat(prefixBytes, fork.root()));
      }

      byte[] neighborHash = hashFn.digest(Bytes.concat(fork.prefix(), fork.root()));
      if (fork.nibble() == nibble) {
        throw new IllegalStateException("Fork neighbor nibble equals path nibble");
      }
      return branchFromSparse(pathHex.substring(cursor, nextCursor - 1), nibble, childHash,
          fork.nibble(), neighborHash, hashFn, nullHash);
    } else if (step instanceof LeafStep) {
      LeafStep leaf = (LeafStep) step;
      String neighborPath = Bytes.toHex(leaf.keyHash());
      if (!neighborPath.startsWith(pathHex.substring(0, cursor))) {
        throw new IllegalStateException("Leaf neighbor path mismatch");
      }
      int neighborNibble = hexCharToNibble(neighborPath.charAt(nextCursor - 1));
      if (neighborNibble == nibble) {
        throw new IllegalStateException("Leaf neighbor nibble equals path nibble");
      }
      if (!including && isLastStep) {
        String suffix = neighborPath.substring(cursor);
        return commitments.commitLeaf(nibblesFromHex(suffix), leaf.valueHash());
      }
      String suffix = neighborPath.substring(nextCursor);
      byte[] neighborHash = commitments.commitLeaf(nibblesFromHex(suffix), leaf.valueHash());
      return branchFromSparse(pathHex.substring(cursor, nextCursor - 1), nibble, childHash,
          neighborNibble, neighborHash, hashFn, nullHash);
    }
    throw new IllegalStateException("Unknown step type " + step.getClass());
  }

  private static byte[] branchFromSparse(String prefixHex, int meNibble, byte[] meHash,
                                         int neighborNibble, byte[] neighborHash,
                                         HashFunction hashFn, byte[] nullHash) {
    byte[][] nodes = new byte[16][];
    for (int i = 0; i < 16; i++) {
      nodes[i] = Arrays.copyOf(nullHash, nullHash.length);
    }
    nodes[meNibble] = meHash;
    nodes[neighborNibble] = neighborHash;

    int length = nodes.length;
    byte[][] layer = nodes;
    while (length > 1) {
      byte[][] next = new byte[length / 2][];
      for (int i = 0; i < length; i += 2) {
        next[i / 2] = hashFn.digest(Bytes.concat(layer[i], layer[i + 1]));
      }
      layer = next;
      length = layer.length;
    }
    byte[] merkle = layer[0];
    byte[] prefixBytes = nibbleBytes(prefixHex);
    return hashFn.digest(Bytes.concat(prefixBytes, merkle));
  }

  private static byte[] aggregateSiblingHashes(int nibble, byte[] me, byte[][] neighbors,
                                               HashFunction hashFn, byte[] nullHash) {
    byte[] lvl1 = neighbors[0];
    byte[] lvl2 = neighbors[1];
    byte[] lvl3 = neighbors[2];
    byte[] lvl4 = neighbors[3];

    switch (nibble) {
      case 0:
        return hashPair(hashPair(hashPair(hashPair(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
      case 1:
        return hashPair(hashPair(hashPair(hashPair(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
      case 2:
        return hashPair(hashPair(hashPair(lvl3, hashPair(me, lvl4, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
      case 3:
        return hashPair(hashPair(hashPair(lvl3, hashPair(lvl4, me, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), lvl1, hashFn, nullHash);
      case 4:
        return hashPair(hashPair(lvl2, hashPair(hashPair(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
      case 5:
        return hashPair(hashPair(lvl2, hashPair(hashPair(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
      case 6:
        return hashPair(hashPair(lvl2, hashPair(lvl3, hashPair(me, lvl4, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
      case 7:
        return hashPair(hashPair(lvl2, hashPair(lvl3, hashPair(lvl4, me, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), lvl1, hashFn, nullHash);
      case 8:
        return hashPair(lvl1, hashPair(hashPair(hashPair(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
      case 9:
        return hashPair(lvl1, hashPair(hashPair(hashPair(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
      case 10:
        return hashPair(lvl1, hashPair(hashPair(lvl3, hashPair(me, lvl4, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
      case 11:
        return hashPair(lvl1, hashPair(hashPair(lvl3, hashPair(lvl4, me, hashFn, nullHash), hashFn, nullHash), lvl2, hashFn, nullHash), hashFn, nullHash);
      case 12:
        return hashPair(lvl1, hashPair(lvl2, hashPair(hashPair(me, lvl4, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
      case 13:
        return hashPair(lvl1, hashPair(lvl2, hashPair(hashPair(lvl4, me, hashFn, nullHash), lvl3, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
      case 14:
        return hashPair(lvl1, hashPair(lvl2, hashPair(lvl3, hashPair(me, lvl4, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
      case 15:
        return hashPair(lvl1, hashPair(lvl2, hashPair(lvl3, hashPair(lvl4, me, hashFn, nullHash), hashFn, nullHash), hashFn, nullHash), hashFn, nullHash);
      default:
        throw new IllegalArgumentException("Invalid nibble: " + nibble);
    }
  }

  private static byte[] hashPair(byte[] left, byte[] right, HashFunction hashFn, byte[] nullHash) {
    byte[] l = left == null ? nullHash : left;
    byte[] r = right == null ? nullHash : right;
    return hashFn.digest(Bytes.concat(l, r));
  }

  interface Step {
    int skip();
  }

  static final class BranchStep implements Step {
    private final int skip;
    private final byte[][] neighbors;

    BranchStep(int skip, byte[][] neighbors) {
      this.skip = skip;
      this.neighbors = neighbors;
    }

    @Override
    public int skip() {
      return skip;
    }

    public byte[][] neighbors() {
      return neighbors;
    }
  }

  static final class ForkStep implements Step {
    private final int skip;
    private final int nibble;
    private final byte[] prefix;
    private final byte[] root;

    ForkStep(int skip, int nibble, byte[] prefix, byte[] root) {
      this.skip = skip;
      this.nibble = nibble;
      this.prefix = prefix;
      this.root = root;
    }

    @Override
    public int skip() {
      return skip;
    }

    public int nibble() {
      return nibble;
    }

    public byte[] prefix() {
      return prefix;
    }

    public byte[] root() {
      return root;
    }
  }

  static final class LeafStep implements Step {
    private final int skip;
    private final byte[] keyHash;
    private final byte[] valueHash;

    LeafStep(int skip, byte[] keyHash, byte[] valueHash) {
      this.skip = skip;
      this.keyHash = keyHash;
      this.valueHash = valueHash;
    }

    @Override
    public int skip() {
      return skip;
    }

    public byte[] keyHash() {
      return keyHash;
    }

    public byte[] valueHash() {
      return valueHash;
    }
  }

  private static NibblePath nibblesFromHex(String hex) {
    if (hex.isEmpty()) {
      return NibblePath.EMPTY;
    }
    int[] nibbles = new int[hex.length()];
    for (int i = 0; i < hex.length(); i++) {
      nibbles[i] = hexCharToNibble(hex.charAt(i));
    }
    return NibblePath.of(nibbles);
  }

  private static NibblePath pathToNibblePath(String hex) {
    int[] nibbles = new int[hex.length()];
    for (int i = 0; i < hex.length(); i++) {
      nibbles[i] = hexCharToNibble(hex.charAt(i));
    }
    return NibblePath.of(nibbles);
  }

  private static int hexCharToNibble(char ch) {
    int nibble = Character.digit(ch, 16);
    if (nibble < 0) throw new IllegalArgumentException("Invalid hex char: " + ch);
    return nibble;
  }

  private static byte[] nibbleBytes(String hex) {
    if (hex.isEmpty()) return new byte[0];
    byte[] out = new byte[hex.length()];
    for (int i = 0; i < hex.length(); i++) {
      out[i] = (byte) hexCharToNibble(hex.charAt(i));
    }
    return out;
  }
}
