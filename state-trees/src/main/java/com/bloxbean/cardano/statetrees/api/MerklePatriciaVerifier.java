package com.bloxbean.cardano.statetrees.api;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;

/**
 * Verification helpers for Merkle Patricia Trie proofs.
 *
 * @since 0.9.0
 */
public final class MerklePatriciaVerifier {

  private MerklePatriciaVerifier() {
  }

  /**
   * Verifies an inclusion proof for a key/value pair.
   *
   * @param root trie root hash (nullable for empty trie)
   * @param hashFn hash function used for node hashes
   * @param key the queried key
   * @param value the expected value
   * @param proofNodes ordered list of CBOR-encoded nodes from root to leaf
   * @return true if the proof is valid for the supplied root/key/value
   */
  public static boolean verifyInclusion(
      byte[] root,
      HashFunction hashFn,
      byte[] key,
      byte[] value,
      List<byte[]> proofNodes) {
    Objects.requireNonNull(hashFn, "hashFn");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(proofNodes, "proofNodes");

    VerificationResult result = evaluate(root, hashFn, key, proofNodes);
    if (!result.valid || result.type != MerklePatriciaProof.Type.INCLUSION) {
      return false;
    }
    return eq(result.value, value);
  }

  /**
   * Verifies a non-inclusion proof for a key.
   *
   * @param root trie root hash (nullable for empty trie)
   * @param hashFn hash function used for node hashes
   * @param key the queried key
   * @param proofNodes ordered list of CBOR-encoded nodes from root to terminal node
   * @return true if the proof is a valid non-inclusion witness
   */
  public static boolean verifyNonInclusion(
      byte[] root,
      HashFunction hashFn,
      byte[] key,
      List<byte[]> proofNodes) {
    Objects.requireNonNull(hashFn, "hashFn");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(proofNodes, "proofNodes");

    VerificationResult result = evaluate(root, hashFn, key, proofNodes);
    if (!result.valid) {
      return false;
    }
    return result.type == MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH
        || result.type == MerklePatriciaProof.Type.NON_INCLUSION_DIFFERENT_LEAF;
  }

  private static VerificationResult evaluate(
      byte[] root,
      HashFunction hashFn,
      byte[] key,
      List<byte[]> proofNodes) {

    byte[] normalizedRoot = normalizeRoot(root);
    if (normalizedRoot == null) {
      return proofNodes.isEmpty()
          ? VerificationResult.success(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, null)
          : VerificationResult.invalid();
    }

    if (proofNodes.isEmpty()) {
      return VerificationResult.invalid();
    }

    int[] keyNibbles = Nibbles.toNibbles(key);
    int position = 0;
    byte[] expectedHash = normalizedRoot;

    for (int idx = 0; idx < proofNodes.size(); idx++) {
      byte[] nodeBytes = proofNodes.get(idx);
      if (nodeBytes == null) {
        return VerificationResult.invalid();
      }

      byte[] nodeHash = hashFn.digest(nodeBytes);
      if (nodeHash == null || !eq(nodeHash, expectedHash)) {
        return VerificationResult.invalid();
      }

      Array array = decode(nodeBytes);
      if (array == null) {
        return VerificationResult.invalid();
      }

      int size = array.getDataItems().size();
      if (size == 17) {
        ByteString valueItem = expectByteString(array.getDataItems().get(16));
        if (valueItem == null) {
          return VerificationResult.invalid();
        }
        byte[] branchValue = valueItem.getBytes();
        byte[] actualValue = branchValue.length == 0 ? null : branchValue.clone();

        if (position == keyNibbles.length) {
          if (idx != proofNodes.size() - 1) {
            return VerificationResult.invalid();
          }
          if (actualValue != null) {
            return VerificationResult.success(MerklePatriciaProof.Type.INCLUSION, actualValue);
          }
          return VerificationResult.success(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, null);
        }

        int nibble = keyNibbles[position];
        ByteString childItem = expectByteString(array.getDataItems().get(nibble));
        if (childItem == null) {
          return VerificationResult.invalid();
        }
        byte[] childDigest = childItem.getBytes();
        if (childDigest.length == 0) {
          if (idx != proofNodes.size() - 1) {
            return VerificationResult.invalid();
          }
          return VerificationResult.success(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, null);
        }

        expectedHash = childDigest;
        position++;
        if (idx == proofNodes.size() - 1) {
          return VerificationResult.invalid();
        }
        continue;
      }

      if (size == 2) {
        ByteString hpItem = expectByteString(array.getDataItems().get(0));
        ByteString second = expectByteString(array.getDataItems().get(1));
        if (hpItem == null || second == null) {
          return VerificationResult.invalid();
        }

        byte[] hpBytes = hpItem.getBytes();
        Nibbles.HP hp = Nibbles.unpackHP(hpBytes);
        if (hp.isLeaf) {
          if (idx != proofNodes.size() - 1) {
            return VerificationResult.invalid();
          }
          int[] leafNibbles = hp.nibbles;
          if (position + leafNibbles.length == keyNibbles.length) {
            boolean matches = true;
            for (int i = 0; i < leafNibbles.length; i++) {
              if (keyNibbles[position + i] != leafNibbles[i]) {
                matches = false;
                break;
              }
            }
            if (matches) {
              byte[] valueBytes = second.getBytes();
              return VerificationResult.success(
                  MerklePatriciaProof.Type.INCLUSION,
                  valueBytes.clone());
            }
          }
          return VerificationResult.success(
              MerklePatriciaProof.Type.NON_INCLUSION_DIFFERENT_LEAF,
              null);
        }

        int[] extNibbles = hp.nibbles;
        if (position + extNibbles.length > keyNibbles.length) {
          if (idx != proofNodes.size() - 1) {
            return VerificationResult.invalid();
          }
          return VerificationResult.success(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, null);
        }

        for (int i = 0; i < extNibbles.length; i++) {
          if (keyNibbles[position + i] != extNibbles[i]) {
            if (idx != proofNodes.size() - 1) {
              return VerificationResult.invalid();
            }
            return VerificationResult.success(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, null);
          }
        }

        position += extNibbles.length;
        byte[] childDigest = second.getBytes();
        if (childDigest.length == 0) {
          if (idx != proofNodes.size() - 1) {
            return VerificationResult.invalid();
          }
          return VerificationResult.success(MerklePatriciaProof.Type.NON_INCLUSION_MISSING_BRANCH, null);
        }

        expectedHash = childDigest;
        if (idx == proofNodes.size() - 1) {
          return VerificationResult.invalid();
        }
        continue;
      }

      return VerificationResult.invalid();
    }

    return VerificationResult.invalid();
  }

  private static Array decode(byte[] nodeBytes) {
    try {
      List<DataItem> items = new CborDecoder(new ByteArrayInputStream(nodeBytes)).decode();
      if (items.isEmpty()) {
        return null;
      }
      DataItem root = items.get(0);
      if (!(root instanceof Array)) {
        return null;
      }
      return (Array) root;
    } catch (Exception e) {
      return null;
    }
  }

  private static ByteString expectByteString(DataItem item) {
    if (item instanceof ByteString) {
      return (ByteString) item;
    }
    return null;
  }

  private static byte[] normalizeRoot(byte[] root) {
    if (root == null || root.length == 0) {
      return null;
    }
    return root;
  }

  private static boolean eq(byte[] a, byte[] b) {
    if (a == null || b == null || a.length != b.length) {
      return false;
    }
    int diff = 0;
    for (int i = 0; i < a.length; i++) {
      diff |= a[i] ^ b[i];
    }
    return diff == 0;
  }

  private static final class VerificationResult {
    final boolean valid;
    final MerklePatriciaProof.Type type;
    final byte[] value;

    private VerificationResult(boolean valid, MerklePatriciaProof.Type type, byte[] value) {
      this.valid = valid;
      this.type = type;
      this.value = value;
    }

    static VerificationResult invalid() {
      return new VerificationResult(false, null, null);
    }

    static VerificationResult success(MerklePatriciaProof.Type type, byte[] value) {
      return new VerificationResult(true, type, value);
    }
  }
}
