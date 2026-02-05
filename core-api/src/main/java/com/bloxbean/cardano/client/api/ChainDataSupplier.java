package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.TransactionInfo;

import java.util.Optional;

/**
 * Provides blockchain data needed for transaction tracking and monitoring.
 * <p>
 * This interface abstracts the minimal data access required for chain queries,
 * confirmation monitoring, and rollback detection, allowing integration with
 * any data provider without implementing full BackendService.
 */
public interface ChainDataSupplier {

    /**
     * Get the current chain tip height.
     *
     * @return the latest block height on the chain
     * @throws ApiException if the data cannot be retrieved
     */
    long getChainTipHeight() throws ApiException;

    /**
     * Get transaction information by hash.
     *
     * @param txHash the transaction hash to look up
     * @return Optional containing TransactionInfo if found on chain, empty otherwise
     * @throws ApiException if the lookup fails
     */
    Optional<TransactionInfo> getTransactionInfo(String txHash) throws ApiException;
}
