package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.txflow.FlowStep;

/**
 * Internal transaction-attempt transition boundary shared by the execution
 * facade and durable engine.
 *
 * <p>The executor calls {@link #onPrepared(FlowStep, Transaction)} after signing
 * but before submission. Durable implementations must complete that callback
 * synchronously and throw on failure, thereby enforcing write-before-submit
 * ordering. Later callbacks describe observation state; they are not permission
 * to resubmit or rebuild a transaction.</p>
 *
 * <p>The no-op implementation preserves non-durable behavior. Implementations
 * do not own execution threads and are invoked on caller-managed tasks.</p>
 */
interface PersistencePort {
    PersistencePort NOOP = new PersistencePort() { };

    default void onPrepared(FlowStep step, Transaction transaction) { }
    default void onSubmitting(FlowStep step, Transaction transaction) { }
    default void onSubmitted(FlowStep step, String transactionHash) { }
    default void onInBlock(FlowStep step, String transactionHash, long blockHeight) { }
    default void onConfirmationDepth(FlowStep step, String transactionHash, int depth) { }
    default void onConfirmed(FlowStep step, String transactionHash) { }
    default void onRolledBack(FlowStep step, String transactionHash, long previousBlockHeight) { }
}
