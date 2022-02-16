package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides necessary services which are required to build the transaction
 * It also stores some temporary information like multiAsset info for minting transaction.
 */
@Data
public class TxBuilderContext {
    private BackendService backendService;
    private ProtocolParams protocolParams;
    private UtxoSelectionStrategy utxoSelectionStrategy;
    private UtxoSelector utxoSelector;

    //Needed to check if the output is for minting
    //This list is cleared after Input Builder
    private List<MultiAsset> mintMultiAssets = new ArrayList<>();

    public TxBuilderContext(BackendService backendService) {
        this.backendService = backendService;
        this.utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(backendService.getUtxoService());
        this.utxoSelector = new DefaultUtxoSelector(backendService.getUtxoService());
    }

    public void setUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
    }

    public UtxoSelectionStrategy getUtxoSelectionStrategy() {
        return utxoSelectionStrategy;
    }

    public ProtocolParams getProtocolParams() {
        if (protocolParams == null) {
            try {
                this.protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
            } catch (ApiException apiException) {
                throw new ApiRuntimeException("Unable to get protocol parameters", apiException);
            }
        }

        return this.protocolParams;
    }

    public void addMintMultiAssets(MultiAsset multiAsset) {
        mintMultiAssets.add(multiAsset);
    }

    public List<MultiAsset> getMintMultiAssets() {
        return mintMultiAssets;
    }

    public void clearMintMultiAssets() {
        mintMultiAssets.clear();
    }

    public static TxBuilderContext init(BackendService backendService) {
        return new TxBuilderContext(backendService);
    }

    public Transaction build(TxBuilder txBuilder) throws ApiException {
        Transaction transaction = new Transaction();
        txBuilder.accept(this, transaction);

        return transaction;
    }

    public void build(Transaction transaction, TxBuilder txBuilder) throws ApiException {
        txBuilder.accept(this, transaction);
    }

}
