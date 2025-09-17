package com.bloxbean.cardano.statetrees.mpt;

import com.bloxbean.cardano.statetrees.common.NodeHash;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;

/**
 * Utility class for splitting nodes when key conflicts occur during insertion operations.
 *
 * <p>This class contains the complex logic for splitting leaf and extension nodes when
 * a new key insertion creates a conflict with existing node paths. The splitting operations
 * maintain the trie's structural invariants while using the new immutable node infrastructure.</p>
 */
final class NodeSplitter {

  /**
   * Private constructor to prevent instantiation.
   */
  private NodeSplitter() {
  }

  /**
   * Splits a leaf node when a key conflict occurs and inserts both values.
   *
   * @param persistence the node persistence layer
   * @param existingLeaf the existing leaf node to split
   * @param newKeyRemainder the remaining portion of the new key (from current position)
   * @param newValue the new value to insert
   * @param commonPrefixLength the length of the common prefix between keys
   * @return the hash of the new subtree structure
   */
  public static NodeHash splitLeafNode(
      NodePersistence persistence,
      LeafNode existingLeaf,
      int[] newKeyRemainder,
      byte[] newValue,
      int commonPrefixLength) {

    int[] leafNibbles = Nibbles.unpackHP(existingLeaf.getHp()).nibbles;
    int[] leafRemainder = slice(leafNibbles, commonPrefixLength, leafNibbles.length);
    int[] keyRemainder = slice(newKeyRemainder, commonPrefixLength, newKeyRemainder.length);

    // Create the branch node that will contain both values
    BranchNode.Builder branchBuilder = BranchNode.builder();

    // Handle the existing leaf's remainder
    if (leafRemainder.length == 0) {
      // Existing key ends at this branch
      branchBuilder.value(existingLeaf.getValue());
    } else {
      // Create new leaf for the existing value
      byte[] newLeafHp = Nibbles.packHP(true, slice(leafRemainder, 1, leafRemainder.length));
      LeafNode newLeafFromExisting = LeafNode.of(newLeafHp, existingLeaf.getValue());
      NodeHash leafHash = persistence.persist(newLeafFromExisting);
      branchBuilder.child(leafRemainder[0], leafHash.toBytes());
    }

    // Handle the new key's remainder
    if (keyRemainder.length == 0) {
      // New key ends at this branch
      branchBuilder.value(newValue);
    } else {
      // Create new leaf for the new value
      byte[] newLeafHp = Nibbles.packHP(true, slice(keyRemainder, 1, keyRemainder.length));
      LeafNode newLeaf = LeafNode.of(newLeafHp, newValue);
      NodeHash leafHash = persistence.persist(newLeaf);
      branchBuilder.child(keyRemainder[0], leafHash.toBytes());
    }

    BranchNode branch = branchBuilder.build();
    NodeHash branchHash = persistence.persist(branch);

    // If there's a common prefix, wrap in an extension node
    if (commonPrefixLength > 0) {
      int[] commonPrefix = slice(newKeyRemainder, 0, commonPrefixLength);
      byte[] extensionHp = Nibbles.packHP(false, commonPrefix);
      ExtensionNode extension = ExtensionNode.of(extensionHp, branchHash.toBytes());
      return persistence.persist(extension);
    }

    return branchHash;
  }

  /**
   * Splits an extension node when a key conflict occurs and inserts the new value.
   *
   * @param persistence the node persistence layer
   * @param existingExtension the existing extension node to split
   * @param newKeyRemainder the remaining portion of the new key (from current position)
   * @param newValue the new value to insert
   * @param commonPrefixLength the length of the common prefix between paths
   * @return the hash of the new subtree structure
   */
  public static NodeHash splitExtensionNode(
      NodePersistence persistence,
      ExtensionNode existingExtension,
      int[] newKeyRemainder,
      byte[] newValue,
      int commonPrefixLength) {

    int[] extensionNibbles = Nibbles.unpackHP(existingExtension.getHp()).nibbles;
    int[] extensionRemainder = slice(extensionNibbles, commonPrefixLength, extensionNibbles.length);
    int[] keyRemainder = slice(newKeyRemainder, commonPrefixLength, newKeyRemainder.length);

    // Create the branch node that will split the paths
    BranchNode.Builder branchBuilder = BranchNode.builder();

    // Handle the extension's remainder
    if (extensionRemainder.length == 1) {
      // Extension remainder is a single nibble - becomes direct child
      branchBuilder.child(extensionRemainder[0], existingExtension.getChild());
    } else {
      // Extension remainder is multiple nibbles - create new extension
      byte[] newExtensionHp = Nibbles.packHP(false, slice(extensionRemainder, 1, extensionRemainder.length));
      ExtensionNode newExtension = ExtensionNode.of(newExtensionHp, existingExtension.getChild());
      NodeHash extHash = persistence.persist(newExtension);
      branchBuilder.child(extensionRemainder[0], extHash.toBytes());
    }

    // Handle the new key's remainder
    if (keyRemainder.length == 0) {
      // New key ends at this branch
      branchBuilder.value(newValue);
    } else {
      // Create new leaf for the new value
      byte[] newLeafHp = Nibbles.packHP(true, slice(keyRemainder, 1, keyRemainder.length));
      LeafNode newLeaf = LeafNode.of(newLeafHp, newValue);
      NodeHash leafHash = persistence.persist(newLeaf);
      branchBuilder.child(keyRemainder[0], leafHash.toBytes());
    }

    BranchNode branch = branchBuilder.build();
    NodeHash branchHash = persistence.persist(branch);

    // If there's a common prefix, wrap in an extension node
    if (commonPrefixLength > 0) {
      int[] commonPrefix = slice(newKeyRemainder, 0, commonPrefixLength);
      byte[] extensionHp = Nibbles.packHP(false, commonPrefix);
      ExtensionNode extension = ExtensionNode.of(extensionHp, branchHash.toBytes());
      return persistence.persist(extension);
    }

    return branchHash;
  }

  /**
   * Utility method to slice an array.
   */
  private static int[] slice(int[] array, int from, int to) {
    int len = Math.max(0, to - from);
    int[] out = new int[len];
    for (int i = 0; i < len; i++) {
      out[i] = array[from + i];
    }
    return out;
  }
}