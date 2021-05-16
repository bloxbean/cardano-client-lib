package com.bloxbean.cardano.client.backend.api;

public interface BackendFactory {
    /**
     *
     * @return NetworkInfoService for the configured backend
     */
    public NetworkInfoService getNetworkInfoService();

    /**
     *
     * @return TransactionService for the configured backend
     */
    public TransactionService getTransactionService();
}
