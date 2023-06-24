package com.bloxbean.cardano.client.supplier.local;

import com.bloxbean.cardano.yaci.helper.LocalClientProvider;
import com.bloxbean.cardano.yaci.helper.LocalStateQueryClient;
import com.bloxbean.cardano.yaci.helper.LocalTxSubmissionClient;

/**
 * Factory class to create LocalClientProvider.
 */
public class LocalClientProviderFactory {
    private LocalClientProvider localClientProvider;
    private LocalStateQueryClient localStateQueryClient;
    private LocalTxSubmissionClient txSubmissionClient;

    /**
     * Constructor
     * @param nodeSocketFile Node socket file
     * @param protocolMagicId Protocol magic id
     */
    public LocalClientProviderFactory(String nodeSocketFile, long protocolMagicId) {
        localClientProvider = new LocalClientProvider(nodeSocketFile, protocolMagicId);

        localStateQueryClient = localClientProvider.getLocalStateQueryClient();
        txSubmissionClient = localClientProvider.getTxSubmissionClient();
    }

    /**
     * Start the LocalClientProvider instance
     */
    public void start() {
        localClientProvider.start();
    }

    /**
     * Get LocalStateQueryClient instance
     * @return LocalStateQueryClient
     */
    public LocalStateQueryClient getLocalStateQueryClient() {
        return localStateQueryClient;
    }

    /**
     * Get LocalTxSubmissionClient instance
     * @return LocalTxSubmissionClient
     */
    public LocalTxSubmissionClient getTxSubmissionClient() {
        return txSubmissionClient;
    }

    /**
     * Shutdown the LocalClientProvider instance
     */
    public void shutdown() {
        if (localClientProvider != null)
            localClientProvider.shutdown();
    }
}
