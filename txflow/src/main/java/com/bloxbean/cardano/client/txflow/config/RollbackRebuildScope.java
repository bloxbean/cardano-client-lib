package com.bloxbean.cardano.client.txflow.config;

/** Selects the minimum transaction-graph scope considered for rollback rebuild. */
public enum RollbackRebuildScope {
    /** Start with the rolled-back step; consumers may still be included when safety requires it. */
    AFFECTED_STEP,
    /** Include the rolled-back step and every transaction that consumes its invalidated outputs. */
    INVALIDATED_CLOSURE
}
