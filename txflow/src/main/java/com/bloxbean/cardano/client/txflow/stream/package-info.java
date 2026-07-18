/**
 * Streaming transaction workflow support built on top of bounded TxFlow executions.
 * <p>
 * The stream package accepts {@link com.bloxbean.cardano.client.txflow.stream.TxWorkItem}
 * instances, groups them into count/time windows, plans bounded
 * {@link com.bloxbean.cardano.client.txflow.TxFlow} executions, and reports
 * item-level status through receipts and a stream state store.
 * <p>
 * The MVP keeps execution deliberately conservative: generated flows are run
 * serially by default, and transaction-count reduction is left to custom
 * planners that explicitly merge compatible work.
 */
package com.bloxbean.cardano.client.txflow.stream;
