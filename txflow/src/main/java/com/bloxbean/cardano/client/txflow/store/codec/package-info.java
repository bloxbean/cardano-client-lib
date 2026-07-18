/**
 * Closed, versioned persistence encoding for TxFlow execution snapshots and events.
 *
 * <p>The codec in this package is intended for {@code FlowExecutionStore} adapters. It restores
 * the durable TxFlow records and portable scalar types explicitly and never enables Java object
 * serialization or polymorphic default typing.</p>
 */
package com.bloxbean.cardano.client.txflow.store.codec;
