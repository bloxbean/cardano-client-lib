package com.bloxbean.cardano.client.function.walletshape;

import com.bloxbean.cardano.client.function.TxBuilder;

/**
 * Marker for pre-balance {@link TxBuilder}s that shape the wallet by reshaping the transaction's change outputs.
 * <p>
 * When one is passed to QuickTx's {@code preBalanceTx(...)}, the change is kept as its own
 * {@link com.bloxbean.cardano.client.transaction.spec.ChangeOutput}, also with {@code mergeOutputs(true)}, which then
 * merges payment outputs only.
 */
public interface WalletShaper extends TxBuilder {
}
