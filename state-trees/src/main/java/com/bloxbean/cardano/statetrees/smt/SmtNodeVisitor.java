package com.bloxbean.cardano.statetrees.smt;

/**
 * Visitor interface for type-safe operations on SMT nodes.
 *
 * <p>This interface implements the Visitor pattern for SMT nodes, allowing
 * operations to be performed on different node types without explicit casting.
 * It provides compile-time type safety and is the recommended way to process
 * nodes of unknown type.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * SmtNodeVisitor<String> describer = new SmtNodeVisitor<String>() {
 *     @Override
 *     public String visitInternal(SmtInternalNode node) {
 *         return "Internal node with " +
 *                (node.getLeft() != null ? "left" : "no left") + " and " +
 *                (node.getRight() != null ? "right" : "no right") + " children";
 *     }
 *
 *     @Override
 *     public String visitLeaf(SmtLeafNode node) {
 *         return "Leaf node with " + node.getValue().length + " bytes of data";
 *     }
 * };
 *
 * String description = node.accept(describer);
 * }</pre>
 *
 * @param <T> the return type of the visitor operations
 * @since 0.8.0
 */
interface SmtNodeVisitor<T> {

    /**
     * Visits an internal node.
     *
     * @param node the internal node to visit
     * @return the result of the visit operation
     */
    T visitInternal(SmtInternalNode node);

    /**
     * Visits a leaf node.
     *
     * @param node the leaf node to visit
     * @return the result of the visit operation
     */
    T visitLeaf(SmtLeafNode node);
}
