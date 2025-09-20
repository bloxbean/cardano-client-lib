package com.bloxbean.cardano.client.quicktx.filter;

import com.bloxbean.cardano.client.quicktx.filter.ast.FilterNode;

/**
 * Specification for filtering UTxOs with optional ordering and limiting.
 * Contains the filter AST, selection criteria, and backend hint.
 *
 * <p>This specification can be:
 * <ul>
 *   <li>Created programmatically using {@link ImmutableUtxoFilterSpec#builder(FilterNode)}</li>
 *   <li>Parsed from YAML using {@link com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml}</li>
 *   <li>Serialized back to YAML for persistence</li>
 * </ul>
 *
 * @see FilterNode
 * @see Selection
 * @see com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml
 */
public interface UtxoFilterSpec {
    /**
     * Gets the root node of the filter AST.
     *
     * @return the filter AST root node (never null)
     */
    FilterNode root();

    /**
     * Gets the selection criteria for ordering and limiting results.
     *
     * @return the selection criteria, or null for default behavior (all results, canonical order)
     */
    Selection selection(); // may be null to indicate default (all, canonical order)

    /**
     * Gets the backend hint for filter execution.
     *
     * @return the backend identifier (e.g., "memory", "sql"), or null for auto-selection
     */
    String backend(); // may be null (auto)
}

