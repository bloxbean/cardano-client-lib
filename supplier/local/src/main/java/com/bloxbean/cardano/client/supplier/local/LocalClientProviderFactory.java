package com.bloxbean.cardano.client.supplier.local;

import com.bloxbean.cardano.yaci.helper.LocalClientProvider;
import com.bloxbean.cardano.yaci.helper.LocalStateQueryClient;
import com.bloxbean.cardano.yaci.helper.LocalTxSubmissionClient;

public class LocalClientProviderFactory {
    private LocalClientProvider localClientProvider;
    private LocalStateQueryClient localStateQueryClient;
    private LocalTxSubmissionClient txSubmissionClient;

    public LocalClientProviderFactory(String nodeSocketFile, long protocolMagicId) {
        localClientProvider = new LocalClientProvider(nodeSocketFile, protocolMagicId);

        localStateQueryClient = localClientProvider.getLocalStateQueryClient();
        txSubmissionClient = localClientProvider.getTxSubmissionClient();
    }

    public void start() {
        localClientProvider.start();
    }

    public LocalStateQueryClient getLocalStateQueryClient() {
        return localStateQueryClient;
    }

    public LocalTxSubmissionClient getTxSubmissionClient() {
        return txSubmissionClient;
    }

    public void shutdown() {
        if (localClientProvider != null)
            localClientProvider.shutdown();
    }
}
