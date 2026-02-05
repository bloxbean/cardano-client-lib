package com.bloxbean.cardano.client.txflow.exec;

/**
 * Status of transaction confirmation tracking.
 * <p>
 * Tracks the progression of a transaction from submission through finality:
 * <pre>
 * SUBMITTED → IN_BLOCK → CONFIRMED → FINALIZED
 *                 ↓
 *            ROLLED_BACK (if reorg detected)
 * </pre>
 *
 * <table border="1">
 *   <caption>Confirmation status progression and criteria</caption>
 *   <tr><th>Status</th><th>Meaning</th><th>Depth Criteria</th></tr>
 *   <tr><td>SUBMITTED</td><td>Transaction in mempool, not yet in any block</td><td>depth &lt; 0 (not found in block)</td></tr>
 *   <tr><td>IN_BLOCK</td><td>Transaction included in a block, waiting for confirmations</td><td>0 ≤ depth &lt; minConfirmations</td></tr>
 *   <tr><td>CONFIRMED</td><td>Reached practical confirmation depth (safe for most use cases)</td><td>minConfirmations ≤ depth &lt; safeConfirmations</td></tr>
 *   <tr><td>FINALIZED</td><td>Reached actual finality (essentially immutable)</td><td>depth ≥ safeConfirmations</td></tr>
 *   <tr><td>ROLLED_BACK</td><td>Transaction was in chain but disappeared (reorg detected)</td><td>Previously tracked, now not found</td></tr>
 * </table>
 */
public enum ConfirmationStatus {
    /**
     * Transaction submitted to mempool, not yet included in any block.
     */
    SUBMITTED,

    /**
     * Transaction is included in a block but has not yet reached the minimum
     * confirmation depth. Still vulnerable to shallow reorgs.
     */
    IN_BLOCK,

    /**
     * Transaction has reached the practical confirmation depth (configurable,
     * default ~10-20 blocks). Safe for most use cases.
     */
    CONFIRMED,

    /**
     * Transaction has reached actual finality (configurable, default 2160 blocks).
     * Essentially impossible to reverse under normal conditions.
     */
    FINALIZED,

    /**
     * Transaction was previously tracked in the chain but has disappeared,
     * indicating a chain reorganization (rollback).
     */
    ROLLED_BACK
}
