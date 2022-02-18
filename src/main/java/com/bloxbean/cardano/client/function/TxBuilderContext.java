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
    //This list is cleared after each Input Builder
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

    public void addMintMultiAsset(MultiAsset multiAsset) {
        mintMultiAssets = MultiAsset.mergeMultiAssetLists(mintMultiAssets, List.of(multiAsset));
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

    /**
     * Build a <code>{@link Transaction}</code> using given <code>{@link TxBuilder}</code> function
     * @param txBuilder function to build the transaction
     * @return <code>Transaction</code>
     * @throws com.bloxbean.cardano.client.function.exception.TxBuildException if exception during transaction build
     */
    public Transaction build(TxBuilder txBuilder) {
        Transaction transaction = new Transaction();
        txBuilder.build(this, transaction);

        return transaction;
    }

    /**
     * Build and sign a <code>{@link Transaction}</code> using given <code>{@link TxBuilder}</code> and <code>Signer</code>
     * @param txBuilder function to build the transaction
     * @param signer function to sign the transaction
     * @return signed <code>Transaction</code>
     * @throws com.bloxbean.cardano.client.function.exception.TxBuildException if exception during transaction build
     */
    public Transaction buildAndSign(TxBuilder txBuilder, TxSigner signer) {
        Transaction transaction = build(txBuilder);
        return signer.sign(transaction);
    }

    /**
     * Transform the given <code>{@link Transaction}</code> using the <code>{@link TxBuilder}</code>
     * @param transaction transaction to transform
     * @param txBuilder function to transform the given transaction
     * @throws com.bloxbean.cardano.client.function.exception.TxBuildException if exception during transaction build
     */
    public void build(Transaction transaction, TxBuilder txBuilder) {
        txBuilder.build(this, transaction);
    }

}
