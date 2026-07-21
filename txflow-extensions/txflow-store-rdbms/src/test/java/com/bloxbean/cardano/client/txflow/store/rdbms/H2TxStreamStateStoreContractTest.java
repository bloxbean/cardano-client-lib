package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.stream.TxStreamStateStore;
import com.bloxbean.cardano.client.txflow.stream.contract.TxStreamStateStoreContract;

import java.util.UUID;

/** Runs the reusable durable stream-store contract against the certified embedded H2 profile. */
class H2TxStreamStateStoreContractTest extends TxStreamStateStoreContract {
    @Override
    protected TxStreamStateStore createStore() {
        return RdbmsTxStreamStateStore.builder()
                .jdbcUrl("jdbc:h2:mem:stream-contract-" + UUID.randomUUID())
                .build();
    }
}
