package com.bloxbean.cardano.client.txflow;

/**
 * Strategy for selecting UTXOs from a previous step's outputs.
 * <p>
 * This enum defines how outputs from a dependent step are selected
 * to be used as inputs in the current step.
 */
public enum SelectionStrategy {
    /**
     * Select all outputs from the previous step.
     */
    ALL,

    /**
     * Select a specific output by index.
     */
    INDEX,

    /**
     * Select outputs matching a filter predicate.
     */
    FILTER
}
