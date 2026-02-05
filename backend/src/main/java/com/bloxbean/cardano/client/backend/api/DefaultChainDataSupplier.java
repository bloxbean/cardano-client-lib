package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * Default implementation of ChainDataSupplier using BackendService.
 * <p>
 * This implementation wraps BlockService and TransactionService to provide
 * the chain data needed for transaction tracking and confirmation monitoring.
 */
@RequiredArgsConstructor
public class DefaultChainDataSupplier implements ChainDataSupplier {

    private final BlockService blockService;
    private final TransactionService transactionService;

    /**
     * Create a DefaultChainDataSupplier from a BackendService.
     *
     * @param backendService the backend service
     */
    public DefaultChainDataSupplier(BackendService backendService) {
        this(backendService.getBlockService(), backendService.getTransactionService());
    }

    @Override
    public long getChainTipHeight() throws ApiException {
        Result<Block> result = blockService.getLatestBlock();
        if (!result.isSuccessful() || result.getValue() == null) {
            throw new ApiException("Failed to get latest block: " + result.getResponse());
        }
        return result.getValue().getHeight();
    }

    @Override
    public Optional<TransactionInfo> getTransactionInfo(String txHash) throws ApiException {
        Result<TransactionContent> result = transactionService.getTransaction(txHash);
        if (!result.isSuccessful() || result.getValue() == null) {
            return Optional.empty();
        }

        TransactionContent tx = result.getValue();
        return Optional.of(TransactionInfo.builder()
                .txHash(txHash)
                .blockHeight(tx.getBlockHeight())
                .blockHash(tx.getBlock())
                .blockTime(tx.getBlockTime())
                .slot(tx.getSlot())
                .build());
    }
}
