package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.stream.contract.TxStreamStateStoreContract;

/** Runs the durable stream-store contract against the in-memory reference. */
class InMemoryDurableTxStreamStoreContractTest extends TxStreamStateStoreContract {
    @Override
    protected TxStreamStateStore createStore() {
        return new InMemoryDurableTxStreamStore();
    }
}
