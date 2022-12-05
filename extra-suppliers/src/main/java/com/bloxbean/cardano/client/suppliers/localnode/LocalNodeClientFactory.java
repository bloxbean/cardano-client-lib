package com.bloxbean.cardano.client.suppliers.localnode;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.yaci.core.common.TxBodyType;
import com.bloxbean.cardano.yaci.core.protocol.localtx.model.TxSubmissionRequest;
import com.bloxbean.cardano.yaci.helper.LocalClientProvider;
import com.bloxbean.cardano.yaci.helper.LocalStateQueryClient;
import com.bloxbean.cardano.yaci.helper.LocalTxMonitorClient;
import com.bloxbean.cardano.yaci.helper.LocalTxSubmissionClient;

public class LocalNodeClientFactory {
    private LocalClientProvider localClientProvider;
    private LocalStateQueryClient localStateQueryClient;
    private LocalTxSubmissionClient txSubmissionClient;
    private LocalTxMonitorClient txMonitorClient;

    public LocalNodeClientFactory(String nodeSocketFile, int protocolMagic) {
        this.localClientProvider = new LocalClientProvider(nodeSocketFile, protocolMagic);
        this.localClientProvider.start();

        this.localStateQueryClient = localClientProvider.getLocalStateQueryClient();
        this.txSubmissionClient = localClientProvider.getTxSubmissionClient();
        this.txMonitorClient = localClientProvider.getTxMonitorClient();
    }

    public LocalStateQueryClient getLocalStateQueryClient() {
        return localStateQueryClient;
    }

    public LocalTxSubmissionClient getTxSubmissionClient() {
        return txSubmissionClient;
    }

    public LocalTxMonitorClient getTxMonitorClient() {
        return txMonitorClient;
    }

    public void submitTx(Transaction transaction) throws Exception {
        txSubmissionClient.submitTx(new TxSubmissionRequest(TxBodyType.BABBAGE, transaction.serialize()));
    }

    public UtxoSupplier getUtxoSupplier() {
        return new LocalNodeUtxoSupplier(localStateQueryClient);
    }

    public ProtocolParamsSupplier getProtocolParamsSupplier() {
        return new LocalNodeProtocolSupplier(localStateQueryClient);
    }

    public void shutdown() {
        localClientProvider.shutdown();
    }
}
