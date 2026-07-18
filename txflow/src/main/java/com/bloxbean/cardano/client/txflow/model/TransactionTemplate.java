package com.bloxbean.cardano.client.txflow.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Immutable syntax tree for one embedded portable QuickTx transaction.
 *
 * <p>The tree is defensively copied both on construction and access. This wrapper
 * preserves expressions until compilation; it does not itself validate the
 * transaction shape or bind parameters.</p>
 */
public final class TransactionTemplate {
    private final JsonNode node;

    /**
     * Captures a defensive copy of an embedded transaction tree.
     *
     * @param node transaction syntax tree
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public TransactionTemplate(JsonNode node) {
        this.node = Objects.requireNonNull(node, "node").deepCopy();
    }

    /**
     * Returns a deep copy safe for binding or other transformations.
     *
     * @return copied transaction tree
     */
    public JsonNode toJsonNode() {
        return node.deepCopy();
    }
}
