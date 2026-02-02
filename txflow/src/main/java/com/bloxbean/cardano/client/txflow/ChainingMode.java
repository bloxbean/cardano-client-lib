package com.bloxbean.cardano.client.txflow;

/**
 * Defines how transactions are submitted and confirmed during flow execution.
 * <p>
 * This mode determines whether transactions can potentially land in the same block
 * or are guaranteed to be in separate blocks.
 */
public enum ChainingMode {

    /**
     * Sequential execution mode (default).
     * <p>
     * Each step waits for its transaction to be confirmed on-chain before
     * the next step begins. This guarantees that each transaction is in a
     * separate block.
     * <p>
     * Use this mode when:
     * <ul>
     *     <li>You need confirmation of each step before proceeding</li>
     *     <li>Steps have complex dependencies that require on-chain state</li>
     *     <li>You want more predictable, safer execution</li>
     * </ul>
     */
    SEQUENTIAL,

    /**
     * Pipelined execution mode for true transaction chaining.
     * <p>
     * All transactions are built and submitted without waiting for confirmations
     * between steps. Each step uses the <em>expected</em> outputs from previous
     * steps (captured from the built transaction before submission).
     * <p>
     * This enables multiple transactions to potentially land in the same block,
     * providing faster overall execution.
     * <p>
     * Use this mode when:
     * <ul>
     *     <li>You want faster execution (transactions can be in same block)</li>
     *     <li>You're doing simple UTXO chaining without complex on-chain queries</li>
     *     <li>You understand that if any transaction fails, subsequent ones may also fail</li>
     * </ul>
     * <p>
     * <b>Note:</b> In this mode, if an early transaction fails, later transactions
     * that depend on its outputs will also fail since they reference non-existent UTXOs.
     */
    PIPELINED
}
