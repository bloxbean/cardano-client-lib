package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.stream.store.TxStreamStateStore;
import com.bloxbean.cardano.client.txflow.stream.store.contract.TxStreamStateStoreContract;

/** Runs the durable stream-store contract against the in-memory reference. */
class InMemoryDurableStoreContractTest extends TxStreamStateStoreContract {
    @Override
    protected TxStreamStateStore createStore() {
        return TxStreamStateStore.inMemoryDurable();
    }
}
