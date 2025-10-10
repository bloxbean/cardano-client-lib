package com.bloxbean.cardano.client.quicktx.filter.ast;

/**
 * Visitor interface for processing filter AST nodes.
 * Implementations compile the AST into backend-specific representations.
 *
 * <p>Example implementations:
 * <ul>
 *   <li>InMemoryFilterVisitor - Compiles to {@code Predicate<Utxo>}</li>
 *   <li>SqlFilterVisitor (future) - Compiles to SQL WHERE clause</li>
 * </ul>
 *
 * <p>The visitor pattern allows adding new backends without modifying
 * the AST structure, maintaining clean separation between the DSL
 * and its various execution strategies.
 *
 * @param <T> the type produced by this visitor (e.g., Predicate, SqlFragment)
 * @see FilterNode
 */
public interface FilterVisitor<T> {
    /**
     * Visits a comparison node that compares a field with a value.
     *
     * @param node the comparison node containing field, operator, and value
     * @return the compiled representation of the comparison
     */
    T visit(Comparison node);

    /**
     * Visits an AND logical node that requires all terms to be true.
     *
     * @param node the AND node containing multiple filter terms
     * @return the compiled representation of the AND operation
     */
    T visit(And node);

    /**
     * Visits an OR logical node that requires at least one term to be true.
     *
     * @param node the OR node containing multiple filter terms
     * @return the compiled representation of the OR operation
     */
    T visit(Or node);

    /**
     * Visits a NOT logical node that negates its inner term.
     *
     * @param node the NOT node containing a single filter term
     * @return the compiled representation of the NOT operation
     */
    T visit(Not node);
}

