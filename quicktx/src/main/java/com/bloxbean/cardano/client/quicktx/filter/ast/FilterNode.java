package com.bloxbean.cardano.client.quicktx.filter.ast;

/**
 * Base interface for all nodes in the UTxO filter AST (Abstract Syntax Tree).
 * Implements the Visitor pattern to allow different backends to compile
 * the same AST into their native representations.
 *
 * <p>The filter AST supports:
 * <ul>
 *   <li>Logical operations: {@link And}, {@link Or}, {@link Not}</li>
 *   <li>Comparisons: {@link Comparison} with field, operator, and value</li>
 * </ul>
 *
 * @see FilterVisitor
 * @see Comparison
 * @see And
 * @see Or
 * @see Not
 */
public interface FilterNode {
    /**
     * Accepts a visitor that processes this node according to its type.
     *
     * @param visitor the visitor that will process this node
     * @param <T> the return type of the visitor's processing
     * @return the result of the visitor's processing of this node
     */
    <T> T accept(FilterVisitor<T> visitor);
}

