package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.contract.AdjustableClock;
import com.bloxbean.cardano.client.txflow.store.contract.FlowExecutionStoreContract;

import java.util.UUID;

/** Runs the reusable TxFlow store contract against the certified embedded H2 profile. */
class H2FlowExecutionStoreContractTest extends FlowExecutionStoreContract {
    @Override
    protected FlowExecutionStore createStore(AdjustableClock clock) {
        return RdbmsFlowExecutionStore.builder()
                .jdbcUrl("jdbc:h2:mem:contract-" + UUID.randomUUID())
                .clock(clock)
                .build();
    }
}
