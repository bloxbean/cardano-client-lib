package com.bloxbean.cardano.client.watcher.api;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.UtxoSupplier;

public interface WatchTxEvaluator {
    TransactionEvaluator createTxEvaluator(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier);
}
