package com.bloxbean.cardano.statetrees.mpt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.NodeStore;
import com.bloxbean.cardano.statetrees.common.NodeHash;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Core implementation of the Merkle Patricia Trie data structure.
 *
 * <p>This class implements a hexary (16-way) Merkle Patricia Trie following the Ethereum
 * specification with the following characteristics:</p>
 * <ul>
 *   <li>Three node types: Branch (16-way), Leaf, and Extension</li>
 *   <li>CBOR serialization for deterministic encoding</li>
 *   <li>Blake2b-256 hashing (configurable via HashFunction)</li>
 *   <li>Hex-Prefix (HP) encoding for path compression</li>
 *   <li>Automatic node compression and extension merging</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is NOT thread-safe. External synchronization
 * is required for concurrent access. Consider wrapping with a synchronized decorator
 * for multi-threaded environments.</p>
 *
 * <p><b>Performance Characteristics:</b></p>
 * <ul>
 *   <li>Put/Get/Delete: O(log n) where n is the number of keys</li>
 *   <li>ScanByPrefix: O(k) where k is the number of matching keys</li>
 *   <li>Space complexity: O(n) with path compression</li>
 * </ul>
 *
 */
public final class MerklePatriciaTrieImpl {
  private final NodeStore store;
  @SuppressWarnings("unused")
  private final HashFunction hashFn; // reserved for SecureTrie use
  private final NodePersistence persistence;
  private byte[] root; // nullable => empty trie

  /**
   * Creates a new Merkle Patricia Trie implementation.
   *
   * @param store the node storage backend (must not be null)
   * @param hashFn the hash function for node hashing (must not be null)
   * @param root the initial root hash, or null for an empty trie
   * @throws NullPointerException if store or hashFn is null
   */
  public MerklePatriciaTrieImpl(NodeStore store, HashFunction hashFn, byte[] root) {
    this.store = Objects.requireNonNull(store, "NodeStore");
    this.hashFn = Objects.requireNonNull(hashFn, "HashFunction");
    this.persistence = new NodePersistence(store);
    this.root = root == null || root.length == 0 ? null : root;
  }

  /**
   * Sets the root hash of the trie.
   *
   * @param root the new root hash, or null/empty for an empty trie
   */
  public void setRootHash(byte[] root) {
    this.root = root == null || root.length == 0 ? null : root;
  }

  /**
   * Gets the current root hash of the trie.
   *
   * @return the root hash, or null if the trie is empty
   */
  public byte[] getRootHash() {
    return root;
  }

  /**
   * Inserts or updates a key-value pair in the trie.
   *
   * @param key the key to insert
   * @param value the value to associate with the key
   * @throws IllegalArgumentException if value is null
   */
  public void put(byte[] key, byte[] value) {
    if (value == null) throw new IllegalArgumentException("value cannot be null; use delete");
    int[] nibblePath = Nibbles.toNibbles(key);
    NodeHash rootHash = putAtNew(this.root, nibblePath, 0, value);
    this.root = rootHash != null ? rootHash.toBytes() : null;
  }

  /**
   * Retrieves a value by its key.
   *
   * @param key the key to look up
   * @return the associated value, or null if not found
   */
  public byte[] get(byte[] key) {
    int[] nibblePath = Nibbles.toNibbles(key);
    return getAtNew(this.root, nibblePath, 0);
  }

  /**
   * Deletes a key-value pair from the trie.
   *
   * @param key the key to delete
   */
  public void delete(byte[] key) {
    int[] nibblePath = Nibbles.toNibbles(key);
    NodeHash rootHash = deleteAtNew(this.root, nibblePath, 0);
    this.root = rootHash != null ? rootHash.toBytes() : null;
  }

  /**
   * Scans the trie for all entries with keys matching the given prefix.
   *
   * @param prefix the prefix to match
   * @param limit the maximum number of results (0 or negative for unlimited)
   * @return a list of matching entries
   */
  public List<com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie.Entry> scanByPrefix(byte[] prefix, int limit) {
    int[] prefixNibbles = Nibbles.toNibbles(prefix);
    // If the caller provided an odd-length hex prefix padded as 0x0? (e.g., "a" -> 0x0a),
    // drop the leading zero nibble so we match nibble-level intent.
    if (prefixNibbles.length > 0 && prefixNibbles[0] == 0) {
      prefixNibbles = slice(prefixNibbles, 1, prefixNibbles.length);
    }
    List<com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie.Entry> results = new ArrayList<>();
    Deque<Integer> currentPath = new ArrayDeque<>();
    scan(this.root, prefixNibbles, 0, limit <= 0 ? Integer.MAX_VALUE : limit, currentPath, results);
    return results;
  }

  /**
   * DEPRECATED: Use putAtNew() with visitor pattern instead.
   * This method delegates to the new implementation for backward compatibility.
   *
   * @param nodeHash the hash of the current node (null for non-existent)
   * @param keyNibbles the full key as nibbles
   * @param position the current position in the key
   * @param value the value to insert
   * @return the hash of the modified subtree
   */
  private byte[] putAt(byte[] nodeHash, int[] keyNibbles, int position, byte[] value) {
    NodeHash result = putAtNew(nodeHash, keyNibbles, position, value);
    return result != null ? result.toBytes() : null;
  }

  /*
   * OLD IMPLEMENTATION - COMMENTED OUT FOR REFACTORING
   * TODO: Remove after validation of new implementation
   *
  private byte[] putAtOld(byte[] nodeHash, int[] keyNibbles, int position, byte[] value) {
    if (nodeHash == null) {
      LeafNode leaf = new LeafNode();
      int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
      leaf.hp = Nibbles.packHP(true, remainingNibbles);
      leaf.value = value;
      byte[] enc = leaf.encode();
      byte[] h = leaf.hash();
      store.put(h, enc);
      return h;
    }
    byte[] nodeData = store.get(nodeHash);
    if (nodeData == null) {
      // Treat as missing node (store inconsistency or in-flight batch); create a leaf at this position
      LeafNode leaf = new LeafNode();
      int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
      leaf.hp = Nibbles.packHP(true, remainingNibbles);
      leaf.value = value;
      byte[] enc = leaf.encode();
      byte[] h = leaf.hash();
      store.put(h, enc);
      return h;
    }
    Node node = TrieEncoding.decode(nodeData);
    if (node instanceof LeafNode) {
      LeafNode ln = (LeafNode) node;
      int[] nibbles = Nibbles.unpackHP(ln.hp).nibbles;
      int common = Nibbles.commonPrefixLen(slice(keyNibbles, position, keyNibbles.length), nibbles);
      if (common == nibbles.length && position + common == keyNibbles.length) {
        // overwrite leaf value
        ln.value = value;
        byte[] enc = ln.encode();
        byte[] h = ln.hash();
        store.put(h, enc);
        return h;
      }
      // need to split
      return splitAndInsert(ln, nodeHash, keyNibbles, position, value, common);
    } else if (node instanceof ExtensionNode) {
      ExtensionNode en = (ExtensionNode) node;
      int[] enNibbles = Nibbles.unpackHP(en.hp).nibbles;
      int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);
      int common = Nibbles.commonPrefixLen(targetNibbles, enNibbles);
      if (common == enNibbles.length) {
        byte[] newChild = putAt(en.child, keyNibbles, position + common, value);
        en.child = newChild;
        byte[] enc = en.encode();
        byte[] h = en.hash();
        store.put(h, enc);
        return h;
      }
      // split extension
      return splitExtensionAndInsert(en, nodeHash, keyNibbles, position, value, common);
    } else {
      BranchNode bn = (BranchNode) node;
      if (position == keyNibbles.length) {
        bn.value = value;
      } else {
        int childIndex = keyNibbles[position];
        bn.children[childIndex] = putAt(bn.children[childIndex], keyNibbles, position + 1, value);
      }
      byte[] enc = bn.encode();
      byte[] h = bn.hash();
      store.put(h, enc);
      return h;
    }
  }
  */

  /**
   * DEPRECATED: Use NodeSplitter.splitLeafNode() with visitor pattern instead.
   * This method delegates to the new implementation for backward compatibility.
   *
   * @param existingLeaf the existing leaf node to split
   * @param existingHash the hash of the existing leaf
   * @param keyNibbles the new key being inserted
   * @param position the current position in the key
   * @param value the new value to insert
   * @param commonPrefixLength the length of the common prefix
   * @return the hash of the new subtree structure
   */
  private byte[] splitAndInsert(LeafNode existingLeaf, byte[] existingHash, int[] keyNibbles, int position, byte[] value, int commonPrefixLength) {
    int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);
    NodeHash result = NodeSplitter.splitLeafNode(persistence, existingLeaf, remainingKey, value, commonPrefixLength);
    return result.toBytes();
  }

  /*
   * ALL OLD METHODS COMMENTED OUT FOR REFACTORING - WILL BE REMOVED
   * The new visitor-based implementation is now active.
   */

  // All old methods from here are temporarily commented out
  /*
    int[] leafRest = slice(leafNibs, commonPrefixLength, leafNibs.length);
    int[] keyRest = slice(keyNibbles, position + commonPrefixLength, keyNibbles.length);

    BranchNode branch = new BranchNode();

    if (leafRest.length == 0) {
      branch.value = existingLeaf.value;
    } else {
      LeafNode newLeafFromOld = new LeafNode();
      newLeafFromOld.hp = Nibbles.packHP(true, slice(leafRest, 1, leafRest.length));
      newLeafFromOld.value = existingLeaf.value;
      byte[] enc = newLeafFromOld.encode();
      byte[] h = newLeafFromOld.hash();
      store.put(h, enc);
      branch.children[leafRest[0]] = h;
    }

    if (keyRest.length == 0) {
      branch.value = value;
    } else {
      LeafNode newLeaf = new LeafNode();
      newLeaf.hp = Nibbles.packHP(true, slice(keyRest, 1, keyRest.length));
      newLeaf.value = value;
      byte[] enc = newLeaf.encode();
      byte[] h = newLeaf.hash();
      store.put(h, enc);
      branch.children[keyRest[0]] = h;
    }

    byte[] branchHash;
    if (commonPrefixLength > 0) {
      ExtensionNode en = new ExtensionNode();
      // Use the actual key segment from current position as the extension prefix
      en.hp = Nibbles.packHP(false, slice(keyNibbles, position, position + commonPrefixLength));
      byte[] bEnc = branch.encode();
      byte[] bHash = branch.hash();
      store.put(bHash, bEnc);
      en.child = bHash;
      byte[] eEnc = en.encode();
      byte[] eHash = en.hash();
      store.put(eHash, eEnc);
      branchHash = eHash;
    } else {
      byte[] bEnc = branch.encode();
      byte[] bHash = branch.hash();
      store.put(bHash, bEnc);
      branchHash = bHash;
    }
    return branchHash;
  }

  /**
   * Splits an extension node when a partial path match occurs.
   *
   * @param existingExt the existing extension node to split
   * @param existingHash the hash of the existing extension
   * @param keyNibbles the new key being inserted
   * @param position the current position in the key
   * @param value the new value to insert
   * @param commonPrefixLength the length of the common prefix
   * @return the hash of the new subtree structure
   */
  // DEPRECATED: Old method temporarily preserved but will be removed
  // This method has been replaced by NodeSplitter and visitor pattern
  // Keeping for reference during migration - NOT USED IN PRODUCTION
  /*
  private byte[] splitExtensionAndInsert(ExtensionNode existingExt, byte[] existingHash, int[] keyNibbles, int position, byte[] value, int commonPrefixLength) {
    int[] extNibs = Nibbles.unpackHP(existingExt.getHp()).nibbles;
    int[] extRest = slice(extNibs, commonPrefixLength, extNibs.length);
    int[] keyRest = slice(keyNibbles, position + commonPrefixLength, keyNibbles.length);

    BranchNode.Builder branchBuilder = BranchNode.builder();
    if (extRest.length == 0) {
      // Defensive: treat as full match of extension; insert below existing child
      byte[] newChild = putAt(existingExt.getChild(), keyNibbles, position + commonPrefixLength, value);
      ExtensionNode updated = existingExt.withChild(newChild);
      NodeHash hash = persistence.persist(updated);
      return hash.toBytes();
    } else {
      // child along extRest[0]
      if (extRest.length == 1) {
        // directly to child
        branchBuilder.child(extRest[0], existingExt.getChild());
      } else {
        byte[] hp = Nibbles.packHP(false, slice(extRest, 1, extRest.length));
        ExtensionNode en2 = ExtensionNode.of(hp, existingExt.getChild());
        NodeHash hash2 = persistence.persist(en2);
        branchBuilder.child(extRest[0], hash2.toBytes());
      }
    }

    if (keyRest.length == 0) {
      branchBuilder.value(value);
    } else {
      byte[] hp = Nibbles.packHP(true, slice(keyRest, 1, keyRest.length));
      LeafNode newLeaf = LeafNode.of(hp, value);
      NodeHash hash = persistence.persist(newLeaf);
      branchBuilder.child(keyRest[0], hash.toBytes());
    }

    BranchNode branch = branchBuilder.build();
    
    if (commonPrefixLength > 0) {
      byte[] hp = Nibbles.packHP(false, slice(keyNibbles, position, position + commonPrefixLength));
      NodeHash branchHash = persistence.persist(branch);
      ExtensionNode en = ExtensionNode.of(hp, branchHash.toBytes());
      NodeHash extensionHash = persistence.persist(en);
      return extensionHash.toBytes();
    } else {
      NodeHash branchHash = persistence.persist(branch);
      return branchHash.toBytes();
    }
  }
  */

  /**
   * Recursively deletes a value at the given position in the trie.
   *
   * @param nodeHash the hash of the current node
   * @param keyNibbles the full key as nibbles
   * @param position the current position in the key
   * @return the hash of the modified subtree (null if deleted)
   */
  // DEPRECATED: Old method temporarily preserved but will be removed
  // This method uses direct field access and needs migration to visitor pattern
  // Keeping for reference during migration - NOT USED IN PRODUCTION
  /*
  private byte[] deleteAt(byte[] nodeHash, int[] keyNibbles, int position) {
    if (nodeHash == null) return null;
    byte[] nodeData = store.get(nodeHash);
    if (nodeData == null) {
      return nodeHash; // node not found, nothing to delete
    }
    Node node = TrieEncoding.decode(nodeData);
    if (node instanceof LeafNode) {
      LeafNode ln = (LeafNode) node;
      int[] nibbles = Nibbles.unpackHP(ln.getHp()).nibbles;
      int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);
      if (nibbles.length == targetNibbles.length && Nibbles.commonPrefixLen(nibbles, targetNibbles) == nibbles.length) {
        return null; // delete this leaf
      }
      return nodeHash; // not found
    } else if (node instanceof ExtensionNode) {
      ExtensionNode en = (ExtensionNode) node;
      int[] enNibs = Nibbles.unpackHP(en.getHp()).nibbles;
      int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);
      int common = Nibbles.commonPrefixLen(targetNibbles, enNibs);
      if (common < enNibs.length) return nodeHash; // not found
      byte[] newChild = deleteAt(en.getChild(), keyNibbles, position + enNibs.length);
      if (newChild == null) return null; // child removed, compress away this ext
      // check if child can be merged
      byte[] childData = store.get(newChild);
      if (childData == null) {
        return nodeHash; // child not found
      }
      Node child = TrieEncoding.decode(childData);
      if (child instanceof ExtensionNode) {
        // merge extensions
        ExtensionNode c = (ExtensionNode) child;
        int[] mergedNibs = concat(Nibbles.unpackHP(en.getHp()).nibbles, Nibbles.unpackHP(c.getHp()).nibbles);
        byte[] hp = Nibbles.packHP(false, mergedNibs);
        ExtensionNode merged = ExtensionNode.of(hp, c.getChild());
        NodeHash hash = persistence.persist(merged);
        return hash.toBytes();
      } else if (child instanceof LeafNode) {
        // merge ext + leaf into leaf
        LeafNode c = (LeafNode) child;
        int[] mergedNibs = concat(Nibbles.unpackHP(en.getHp()).nibbles, Nibbles.unpackHP(c.getHp()).nibbles);
        byte[] hp = Nibbles.packHP(true, mergedNibs);
        LeafNode merged = LeafNode.of(hp, c.getValue());
        NodeHash hash = persistence.persist(merged);
        return hash.toBytes();
      } else {
        ExtensionNode updated = en.withChild(newChild);
        NodeHash hash = persistence.persist(updated);
        return hash.toBytes();
      }
    } else {
      BranchNode bn = (BranchNode) node;
      BranchNode updated;
      if (position == keyNibbles.length) {
        updated = bn.withValue(null);
      } else {
        int childIndex = keyNibbles[position];
        byte[] newChild = deleteAt(bn.getChild(childIndex), keyNibbles, position + 1);
        updated = bn.withChild(childIndex, newChild);
      }
      // compress if needed
      int childCnt = updated.childCountNonNull();
      if (childCnt == 0 && updated.getValue() == null) {
        return null;
      } else if (childCnt == 0 && updated.getValue() != null) {
        byte[] hp = Nibbles.packHP(true, new int[0]);
        LeafNode ln = LeafNode.of(hp, updated.getValue());
        NodeHash hash = persistence.persist(ln);
        return hash.toBytes();
      } else if (childCnt == 1 && updated.getValue() == null) {
        int firstChildIdx = updated.firstChildIndex();
        byte[] childHash = updated.getChild(firstChildIdx);
        byte[] cData = store.get(childHash);
        if (cData == null) {
          // child not found, return as-is
          NodeHash hash = persistence.persist(updated);
          return hash.toBytes();
        }
        Node c = TrieEncoding.decode(cData);
        if (c instanceof ExtensionNode) {
          ExtensionNode ce = (ExtensionNode) c;
          int[] merged = concat(new int[]{firstChildIdx}, Nibbles.unpackHP(ce.getHp()).nibbles);
          byte[] hp = Nibbles.packHP(false, merged);
          ExtensionNode en = ExtensionNode.of(hp, ce.getChild());
          NodeHash hash = persistence.persist(en);
          return hash.toBytes();
        } else if (c instanceof LeafNode) {
          LeafNode cl = (LeafNode) c;
          int[] merged = concat(new int[]{firstChildIdx}, Nibbles.unpackHP(cl.getHp()).nibbles);
          byte[] hp = Nibbles.packHP(true, merged);
          LeafNode ln = LeafNode.of(hp, cl.getValue());
          NodeHash hash = persistence.persist(ln);
          return hash.toBytes();
        } else {
          // child branch; create extension of single nibble
          byte[] hp = Nibbles.packHP(false, new int[]{firstChildIdx});
          ExtensionNode en = ExtensionNode.of(hp, childHash);
          NodeHash hash = persistence.persist(en);
          return hash.toBytes();
        }
      } else {
        NodeHash hash = persistence.persist(updated);
        return hash.toBytes();
      }
    }
  }
  */

  /**
   * Recursively retrieves a value at the given position in the trie.
   *
   * @param nodeHash the hash of the current node
   * @param keyNibbles the full key as nibbles
   * @param position the current position in the key
   * @return the value if found, null otherwise
   */
  // DEPRECATED: Old method temporarily preserved but will be removed
  // This method uses direct field access and needs migration to visitor pattern
  // Keeping for reference during migration - NOT USED IN PRODUCTION
  /*
  private byte[] getAt(byte[] nodeHash, int[] keyNibbles, int position) {
    if (nodeHash == null) return null;
    byte[] nodeData = store.get(nodeHash);
    if (nodeData == null) {
      return null; // node not found
    }
    Node node = TrieEncoding.decode(nodeData);
    if (node instanceof LeafNode) {
      LeafNode ln = (LeafNode) node;
      int[] nibbles = Nibbles.unpackHP(ln.getHp()).nibbles;
      int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);
      if (nibbles.length == targetNibbles.length && Nibbles.commonPrefixLen(nibbles, targetNibbles) == nibbles.length) {
        return ln.getValue();
      }
      return null;
    } else if (node instanceof ExtensionNode) {
      ExtensionNode en = (ExtensionNode) node;
      int[] enNibs = Nibbles.unpackHP(en.getHp()).nibbles;
      int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);
      int common = Nibbles.commonPrefixLen(targetNibbles, enNibs);
      if (common < enNibs.length) return null;
      return getAt(en.getChild(), keyNibbles, position + enNibs.length);
    } else {
      BranchNode bn = (BranchNode) node;
      if (position == keyNibbles.length) return bn.getValue();
      int childIndex = keyNibbles[position];
      return getAt(bn.getChild(childIndex), keyNibbles, position + 1);
    }
  }
  */

  /**
   * Recursively scans the trie for keys matching a prefix.
   *
   * @param nodeHash the hash of the current node
   * @param prefixNibbles the prefix to match as nibbles
   * @param position the current position in the traversal
   * @param limit the maximum number of results
   * @param accumulator the accumulated path nibbles
   * @param output the list to add matching entries to
   */
  private void scan(byte[] nodeHash, int[] prefixNibbles, int position, int limit, Deque<Integer> accumulator, List<com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie.Entry> output) {
    if (nodeHash == null || output.size() >= limit) return;
    byte[] nodeData = store.get(nodeHash);
    if (nodeData == null) {
      return; // node not found, skip
    }
    Node node = TrieEncoding.decode(nodeData);
    if (node instanceof LeafNode) {
      LeafNode ln = (LeafNode) node;
      int[] nibbles = Nibbles.unpackHP(ln.getHp()).nibbles;
      for (int nib : nibbles) accumulator.addLast(nib);
      // check prefix match
      int[] current = toArray(accumulator);
      if (startsWith(current, prefixNibbles)) {
        output.add(new com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie.Entry(toBytes(current), ln.getValue()));
      }
      for (int i = 0; i < nibbles.length; i++) accumulator.removeLast();
    } else if (node instanceof ExtensionNode) {
      ExtensionNode en = (ExtensionNode) node;
      int[] enNibs = Nibbles.unpackHP(en.getHp()).nibbles;
      for (int nib : enNibs) accumulator.addLast(nib);
      scan(en.getChild(), prefixNibbles, position + enNibs.length, limit, accumulator, output);
      for (int i = 0; i < enNibs.length; i++) accumulator.removeLast();
    } else {
      BranchNode bn = (BranchNode) node;
      byte[] branchValue = bn.getValue();
      if (branchValue != null) {
        int[] current = toArray(accumulator);
        if (startsWith(current, prefixNibbles)) output.add(new com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie.Entry(toBytes(current), branchValue));
        if (output.size() >= limit) return;
      }
      for (int i = 0; i < 16 && output.size() < limit; i++) {
        accumulator.addLast(i);
        byte[] childHash = bn.getChild(i);
        scan(childHash, prefixNibbles, position + 1, limit, accumulator, output);
        accumulator.removeLast();
      }
    }
  }

  /**
   * Utility methods for array operations.
   */

  /**
   * Creates a slice of an integer array.
   *
   * @param array the source array
   * @param fromIndex the starting index (inclusive)
   * @param toIndex the ending index (exclusive)
   * @return a new array containing the specified slice
   */
  private static int[] slice(int[] array, int fromIndex, int toIndex) {
    int length = Math.max(0, toIndex - fromIndex);
    int[] result = new int[length];
    for (int i = 0; i < length; i++) result[i] = array[fromIndex + i];
    return result;
  }

  /**
   * Concatenates two integer arrays.
   *
   * @param first the first array
   * @param second the second array
   * @return a new array containing elements from both arrays
   */
  private static int[] concat(int[] first, int[] second) {
    int[] result = new int[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  /**
   * Checks if an array starts with a given prefix.
   *
   * @param fullArray the full array to check
   * @param prefix the prefix to match
   * @return true if fullArray starts with prefix
   */
  private static boolean startsWith(int[] fullArray, int[] prefix) {
    if (prefix.length > fullArray.length) return false;
    for (int i = 0; i < prefix.length; i++) if (fullArray[i] != prefix[i]) return false;
    return true;
  }

  /**
   * Converts a deque of integers to an array.
   *
   * @param deque the deque to convert
   * @return an array containing the deque elements in order
   */
  private static int[] toArray(Deque<Integer> deque) {
    int[] result = new int[deque.size()];
    int index = 0;
    for (int value : deque) result[index++] = value;
    return result;
  }

  /**
   * Converts nibbles array to bytes.
   *
   * @param nibbles the nibbles to convert
   * @return the corresponding byte array
   */
  private static byte[] toBytes(int[] nibbles) {
    return Nibbles.fromNibbles(nibbles);
  }
  // END OF COMMENTED OUT METHODS

  // Note: getAt, splitExtensionAndInsert, and deleteAt methods are defined 
  // in the commented section above. These delegation methods were removed 
  // to avoid duplicate method definitions during the refactoring transition.

  // ======================================
  // New Visitor-Based Implementation Methods
  // ======================================

  /**
   * New visitor-based implementation for inserting values using immutable nodes.
   *
   * @param nodeHash the hash of the current node (null for non-existent)
   * @param keyNibbles the full key as nibbles
   * @param position the current position in the key
   * @param value the value to insert
   * @return the hash of the modified subtree
   */
  private NodeHash putAtNew(byte[] nodeHash, int[] keyNibbles, int position, byte[] value) {
    if (nodeHash == null) {
      // Create new leaf node
      int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
      byte[] hp = Nibbles.packHP(true, remainingNibbles);
      LeafNode leaf = LeafNode.of(hp, value);
      return persistence.persist(leaf);
    }

    Node node = persistence.load(NodeHash.of(nodeHash));
    if (node == null) {
      // Missing node - create new leaf
      int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
      byte[] hp = Nibbles.packHP(true, remainingNibbles);
      LeafNode leaf = LeafNode.of(hp, value);
      return persistence.persist(leaf);
    }

    PutOperationVisitor putVisitor = new PutOperationVisitor(persistence, keyNibbles, position, value);
    return node.accept(putVisitor);
  }

  /**
   * New visitor-based implementation for retrieving values using immutable nodes.
   *
   * @param nodeHash the hash of the current node (null for non-existent)
   * @param keyNibbles the full key as nibbles
   * @param position the current position in the key
   * @return the value if found, null otherwise
   */
  private byte[] getAtNew(byte[] nodeHash, int[] keyNibbles, int position) {
    if (nodeHash == null) {
      return null; // No node at this path
    }

    Node node = persistence.load(NodeHash.of(nodeHash));
    if (node == null) {
      return null; // Missing node
    }

    GetOperationVisitor getVisitor = new GetOperationVisitor(persistence, keyNibbles, position);
    return node.accept(getVisitor);
  }

  /**
   * New visitor-based implementation for deleting values using immutable nodes.
   *
   * @param nodeHash the hash of the current node (null for non-existent)
   * @param keyNibbles the full key as nibbles
   * @param position the current position in the key
   * @return the hash of the modified subtree (null if deleted)
   */
  private NodeHash deleteAtNew(byte[] nodeHash, int[] keyNibbles, int position) {
    if (nodeHash == null) {
      return null; // No node at this path
    }

    Node node = persistence.load(NodeHash.of(nodeHash));
    if (node == null) {
      return null; // Missing node - nothing to delete
    }

    DeleteOperationVisitor deleteVisitor = new DeleteOperationVisitor(persistence, keyNibbles, position);
    return node.accept(deleteVisitor);
  }
}
