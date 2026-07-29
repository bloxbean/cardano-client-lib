package com.bloxbean.cardano.client.quicktx.intent;

/**
 * Type-safe root for declarative transaction-input references in the QuickTx
 * serialization contract.
 *
 * <p>The permitted {@link UtxoRef} hierarchy includes both concrete chain UTXOs
 * and flow-aware references that must be materialized by a host such as TxFlow.</p>
 */
public sealed interface TxInputRef permits UtxoRef {
}
