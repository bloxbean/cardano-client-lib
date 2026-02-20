package com.bloxbean.cardano.client.txflow.exec;

/**
 * Strategy for handling transaction rollbacks during flow execution.
 * <p>
 * When a transaction that was previously confirmed is detected as rolled back
 * (due to a chain reorganization), this strategy determines how the flow
 * executor should respond.
 *
 * <h2>Strategy Comparison</h2>
 * <table border="1">
 *   <caption>Rollback handling strategies comparison</caption>
 *   <tr><th>Strategy</th><th>Behavior</th><th>Use Case</th></tr>
 *   <tr>
 *     <td>FAIL_IMMEDIATELY</td>
 *     <td>Fail the flow immediately when rollback is detected</td>
 *     <td>Default, safest option for most applications</td>
 *   </tr>
 *   <tr>
 *     <td>NOTIFY_ONLY</td>
 *     <td>Notify via listener but continue waiting</td>
 *     <td>When you want manual intervention or external handling</td>
 *   </tr>
 *   <tr>
 *     <td>REBUILD_FROM_FAILED</td>
 *     <td>Automatically rebuild and resubmit the failed step</td>
 *     <td>Independent steps or early steps without dependents</td>
 *   </tr>
 *   <tr>
 *     <td>REBUILD_ENTIRE_FLOW</td>
 *     <td>Rebuild all steps from the beginning</td>
 *     <td>Complex flows with UTXO dependencies (safest for chained transactions)</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FlowExecutor.create(backendService)
 *     .withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY)
 *     .executeSync(flow);
 * }</pre>
 */
public enum RollbackStrategy {

    /**
     * Fail the flow immediately when a rollback is detected.
     * <p>
     * This is the safest default option. When a transaction rolls back:
     * <ul>
     *     <li>The {@code onTransactionRolledBack} listener callback is invoked</li>
     *     <li>The current step fails with a rollback error</li>
     *     <li>The entire flow fails</li>
     * </ul>
     * <p>
     * The application can then decide how to handle the failure (e.g., retry the
     * entire flow, notify operators, etc.).
     */
    FAIL_IMMEDIATELY,

    /**
     * Notify via listener but continue waiting for the transaction.
     * <p>
     * When a rollback is detected:
     * <ul>
     *     <li>The {@code onTransactionRolledBack} listener callback is invoked</li>
     *     <li>The tracker continues monitoring the transaction</li>
     *     <li>If the transaction is re-included in a new block, monitoring continues</li>
     * </ul>
     * <p>
     * Use this strategy when:
     * <ul>
     *     <li>You want to implement custom rollback handling in a listener</li>
     *     <li>The transaction might be re-included after a shallow reorg</li>
     *     <li>You have external systems that need to coordinate the response</li>
     * </ul>
     * <p>
     * <b>Warning:</b> If the transaction is never re-included, this will eventually
     * timeout based on the confirmation configuration.
     */
    NOTIFY_ONLY,

    /**
     * Automatically rebuild and resubmit the failed step.
     * <p>
     * When a rollback is detected:
     * <ul>
     *     <li>The {@code onTransactionRolledBack} listener callback is invoked</li>
     *     <li>The {@code onStepRebuilding} listener callback is invoked</li>
     *     <li>The step is rebuilt with fresh UTXOs from the chain</li>
     *     <li>The rebuilt transaction is submitted and monitored</li>
     * </ul>
     * <p>
     * Use this strategy when:
     * <ul>
     *     <li>Steps are independent (no subsequent steps depend on this step's outputs)</li>
     *     <li>The step is early in the flow without dependent steps</li>
     *     <li>You want automatic recovery with minimal re-execution</li>
     * </ul>
     * <p>
     * <b>Warning:</b> If subsequent steps have already used outputs from this step,
     * those outputs will be invalid after rebuild. Use REBUILD_ENTIRE_FLOW for
     * complex flows with UTXO dependencies.
     * <p>
     * The number of rebuild attempts is controlled by {@code maxRollbackRetries}
     * in {@link ConfirmationConfig}.
     */
    REBUILD_FROM_FAILED,

    /**
     * Rebuild all steps from the beginning of the flow.
     * <p>
     * When a rollback is detected:
     * <ul>
     *     <li>The {@code onTransactionRolledBack} listener callback is invoked</li>
     *     <li>The {@code onFlowRestarting} listener callback is invoked</li>
     *     <li>All step results are cleared</li>
     *     <li>The entire flow is re-executed from step 1</li>
     * </ul>
     * <p>
     * This is the safest strategy for complex flows because:
     * <ul>
     *     <li>All UTXO dependencies are rebuilt from current chain state</li>
     *     <li>No orphaned outputs from previously confirmed but now invalid transactions</li>
     *     <li>Guarantees a consistent state across all steps</li>
     * </ul>
     * <p>
     * Use this strategy when:
     * <ul>
     *     <li>Steps have UTXO dependencies (step N uses outputs from step N-1)</li>
     *     <li>Transaction chaining is used and data integrity is critical</li>
     *     <li>The cost of re-executing all steps is acceptable</li>
     * </ul>
     * <p>
     * The number of flow restart attempts is controlled by {@code maxRollbackRetries}
     * in {@link ConfirmationConfig}.
     */
    REBUILD_ENTIRE_FLOW
}
