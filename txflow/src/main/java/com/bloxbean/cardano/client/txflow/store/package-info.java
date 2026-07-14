/**
 * Durable execution state, journaling, fencing, and recovery payload contracts.
 *
 * <p>{@link com.bloxbean.cardano.client.txflow.store.FlowExecutionStore} is the adapter entry
 * point. Its snapshots and event cursor expose durable progress, while execution and resource
 * leases fence stale workers. Attempt snapshots retain the signed transaction identity and
 * inclusion history required for safe recovery. Applications provide external payload and secure
 * binding resolvers when those values are not stored inline.</p>
 *
 * <p>Database adapters can use
 * {@link com.bloxbean.cardano.client.txflow.store.codec.FlowStoreCodec} for the closed,
 * versioned snapshot and event persistence representation.</p>
 *
 * <p>{@link com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore} demonstrates the
 * required coordination semantics but is intentionally process-local and non-durable.</p>
 */
package com.bloxbean.cardano.client.txflow.store;
