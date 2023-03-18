package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.api.helper.TransactionBuilder;
import com.bloxbean.cardano.client.api.helper.impl.FeeCalculationServiceImpl;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelector;
import com.bloxbean.cardano.client.transaction.spec.CostMdls;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides necessary services which are required to build the transaction
 * It also stores some temporary information like multiAsset info for minting transaction.
 */
@Data
public class TxBuilderContext {
    private UtxoSupplier utxoSupplier;
    private ProtocolParams protocolParams;
    private UtxoSelectionStrategy utxoSelectionStrategy;
    private UtxoSelector utxoSelector;
    private FeeCalculationService feeCalculationService;
    private CostMdls costMdls;

    //Needed to check if the output is for minting
    //This list is cleared after each Input Builder
    private List<MultiAsset> mintMultiAssets = new ArrayList<>();
    //Stores utxos used in the transaction.
    //This list is cleared after each build() call.
    private Set<Utxo> utxos = new HashSet<>();

    public TxBuilderContext(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier) {
        this(utxoSupplier, protocolParamsSupplier.getProtocolParams());
    }

    public TxBuilderContext(UtxoSupplier utxoSupplier, ProtocolParams protocolParams) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParams = protocolParams;
        this.utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
        this.utxoSelector = new DefaultUtxoSelector(utxoSupplier);

        this.feeCalculationService = new FeeCalculationServiceImpl(
                new TransactionBuilder(utxoSupplier, () -> protocolParams));
    }

    public TxBuilderContext setUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
        return this;
    }

    public UtxoSelectionStrategy getUtxoSelectionStrategy() {
        return utxoSelectionStrategy;
    }

    public ProtocolParams getProtocolParams() {
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

    public TxBuilderContext withCostMdls(CostMdls costMdls) {
        this.costMdls = costMdls;
        return this;
    }

    /**
     * @deprecated
     * Use {@link #withCostMdls(CostMdls)} instead
     * @param costMdls
     */
    @Deprecated(since = "0.4.3", forRemoval = true)
    public void setCostMdls(CostMdls costMdls) {
        withCostMdls(costMdls);
    }

    public void addUtxo(Utxo utxo) {
        utxos.add(utxo);
    }

    public Set<Utxo> getUtxos() {
        return utxos;
    }

    public void clearUtxos() {
        utxos.clear();
    }

    public static TxBuilderContext init(UtxoSupplier utxoSupplier, ProtocolParams protocolParams) {
        return new TxBuilderContext(utxoSupplier, protocolParams);
    }

    public static TxBuilderContext init(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier) {
        return new TxBuilderContext(utxoSupplier, protocolParamsSupplier);
    }

    /**
     * Build a <code>{@link Transaction}</code> using given <code>{@link TxBuilder}</code> function
     * @param txBuilder function to build the transaction
     * @return <code>Transaction</code>
     * @throws com.bloxbean.cardano.client.function.exception.TxBuildException if exception during transaction build
     */
    public Transaction build(TxBuilder txBuilder) {
        Transaction transaction = new Transaction();
        txBuilder.apply(this, transaction);
        clearTempStates();
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
        txBuilder.apply(this, transaction);
        clearTempStates();
    }

    private void clearTempStates() {
        clearMintMultiAssets();
        clearUtxos();
    }
}
