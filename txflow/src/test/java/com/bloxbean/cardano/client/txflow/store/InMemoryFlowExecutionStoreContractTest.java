package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.txflow.store.contract.AdjustableClock;
import com.bloxbean.cardano.client.txflow.store.contract.FlowExecutionStoreContract;

class InMemoryFlowExecutionStoreContractTest extends FlowExecutionStoreContract {
    @Override
    protected FlowExecutionStore createStore(AdjustableClock clock) {
        return new InMemoryFlowExecutionStore(clock);
    }
}
