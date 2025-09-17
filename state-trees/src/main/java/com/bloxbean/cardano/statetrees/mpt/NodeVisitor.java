package com.bloxbean.cardano.statetrees.mpt;

/**
 * Visitor pattern interface for type-safe node operations.
 *
 * <p>The visitor pattern allows performing operations on different node types
 * without casting and provides compile-time safety. Each node type has its own
 * visit method, enabling type-specific handling.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * NodeHash result = node.accept(new NodeVisitor&lt;NodeHash&gt;() {
 *   &#64;Override
 *   public NodeHash visitLeaf(LeafNode leaf) {
 *     // Handle leaf node specifically
 *     return persistence.persist(leaf);
 *   }
 *
 *   &#64;Override
 *   public NodeHash visitBranch(BranchNode branch) {
 *     // Handle branch node specifically
 *     return persistence.persist(branch);
 *   }
 *
 *   &#64;Override
 *   public NodeHash visitExtension(ExtensionNode extension) {
 *     // Handle extension node specifically
 *     return persistence.persist(extension);
 *   }
 * });
 * </pre>
 *
 * @param <T> the return type of the visit operations
 */
public interface NodeVisitor<T> {

  /**
   * Visits a leaf node and performs the specific operation.
   *
   * @param node the leaf node to visit
   * @return the result of the operation
   */
  T visitLeaf(LeafNode node);

  /**
   * Visits a branch node and performs the specific operation.
   *
   * @param node the branch node to visit
   * @return the result of the operation
   */
  T visitBranch(BranchNode node);

  /**
   * Visits an extension node and performs the specific operation.
   *
   * @param node the extension node to visit
   * @return the result of the operation
   */
  T visitExtension(ExtensionNode node);
}