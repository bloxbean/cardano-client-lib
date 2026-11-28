/**
 * Persistence SPI, codec, and value records for
 * {@link com.bloxbean.cardano.client.txflow.stream.TxFlowStream} planning
 * metadata, item projections, and single-owner dispatch leases.
 *
 * <p>{@link com.bloxbean.cardano.client.txflow.stream.store.TxStreamStateStore}
 * is the store adapter entry point. Authority is split with the engine's
 * {@link com.bloxbean.cardano.client.txflow.store.FlowExecutionStore}: this
 * store owns authoritative item registrations, write-ahead bindings, durable
 * planned execution records, and epoch-fenced ownership leases, while
 * denormalized item and batch projections are updated with strict per-item
 * compare-and-swap watermarks.</p>
 *
 * <p>Durable database adapters can use
 * {@link com.bloxbean.cardano.client.txflow.stream.store.TxStreamStoreCodec}
 * for versioned JSON serialization of non-columnar metadata payloads. Process-local
 * in-memory reference stores are exposed through
 * {@link com.bloxbean.cardano.client.txflow.stream.store.TxStreamStateStore#inMemory()}
 * and
 * {@link com.bloxbean.cardano.client.txflow.stream.store.TxStreamStateStore#inMemoryDurable()}.</p>
 */
package com.bloxbean.cardano.client.txflow.stream.store;
