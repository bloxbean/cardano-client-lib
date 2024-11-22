package com.bloxbean.cardano.client.function;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
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
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.plutus.spec.Language;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides necessary services which are required to build the transaction
 * It also stores some temporary information like multiAsset info for minting transaction.
 */
@Data
public class TxBuilderContext {
    private UtxoSupplier utxoSupplier;
    private ProtocolParams protocolParams;
    private ScriptSupplier scriptSupplier;
    private UtxoSelectionStrategy utxoSelectionStrategy;
    private UtxoSelector utxoSelector;
    private FeeCalculationService feeCalculationService;
    private TransactionEvaluator transactionEvaluator;
    private CostMdls costMdls;

    //Needed to check if the output is for minting
    //This list is cleared after each Input Builder
    private List<MultiAsset> mintMultiAssets = new ArrayList<>();
    //Stores utxos used in the transaction.
    //This list is cleared after each build() call.
    private Set<Utxo> utxos = new HashSet<>();

    @Setter(AccessLevel.NONE)
    private Map<String, Tuple<PlutusScript, byte[]>> refScripts = new HashMap<>();

    @Setter(AccessLevel.NONE)
    private boolean mergeOutputs = true;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Era serializationEra;

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

    /**
     * Set ScriptSupplier
     * @param scriptSupplier
     * @return TxBuilderContext
     */
    public TxBuilderContext setScriptSupplier(ScriptSupplier scriptSupplier) {
        this.scriptSupplier = scriptSupplier;
        return this;
    }

    /**
     * Get ScriptSupplier
     * @return ScriptSupplier
     */
    public ScriptSupplier getScriptSupplier() {
        return scriptSupplier;
    }

    /**
     * Set UtxoSelectionStrategy
     * @param utxoSelectionStrategy
     * @return TxBuilderContext
     */
    public TxBuilderContext setUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
        return this;
    }

    /**
     * Get UtxoSelectionStrategy
     * @return UtxoSelectionStrategy
     */
    public UtxoSelectionStrategy getUtxoSelectionStrategy() {
        return utxoSelectionStrategy;
    }

    /**
     * Set UtxoSelector
     * @param utxoSelector
     * @return TxBuilderContext
     */
    public TxBuilderContext setUtxoSelector(UtxoSelector utxoSelector) {
        this.utxoSelector = utxoSelector;
        return this;
    }

    /**
     * Get UtxoSelector
     * @return UtxoSelector
     */
    public UtxoSelector getUtxoSelector() {
        return utxoSelector;
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

    public TxBuilderContext withTxnEvaluator(TransactionEvaluator transactionEvaluator) {
        this.transactionEvaluator = transactionEvaluator;
        return this;
    }

    public TransactionEvaluator getTxnEvaluator() {
        return transactionEvaluator;
    }

    public TxBuilderContext withCostMdls(CostMdls costMdls) {
        this.costMdls = costMdls;
        return this;
    }

    /**
     * If true, the outputs will be merged if there are multiple outputs with same address. Default is true.
     * @param mergeOutputs
     */
    public TxBuilderContext mergeOutputs(boolean mergeOutputs) {
        this.mergeOutputs = mergeOutputs;
        return this;
    }

    /**
     * Set the serialization era for the transaction
     * @param era
     * @return TxBuilderContext
     */
    public TxBuilderContext withSerializationEra(Era era) {
        this.serializationEra = era;
        return this;
    }

    /**
     * Get the serialization era for the transaction
     * @return Era or null if not set
     */
    public Era getSerializationEra() {
        return serializationEra;
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

    @SneakyThrows
    public void addRefScripts(PlutusScript plutusScript) {
        refScripts.put(HexUtil.encodeHexString(plutusScript.getScriptHash()), new Tuple<>(plutusScript, plutusScript.scriptRefBytes()));
    }

    public Optional<byte[]> getRefScript(String scriptHash) {
        var tuple = refScripts.get(scriptHash);
        return tuple != null ? Optional.of(tuple._2) : Optional.empty();
    }

    public List<byte[]> getRefScripts() {
        return refScripts.values().stream().map(tuple -> tuple._2)
                .collect(Collectors.toList());
    }

    public List<PlutusScript> getRefPlutusScripts() {
        return refScripts.values().stream().map(tuple -> tuple._1)
                .collect(Collectors.toList());
    }

    public Set<String> getRefScriptHashes() {
        var refScriptInUtxos = utxos.stream()
                .filter(utxo -> utxo.getReferenceScriptHash() != null)
                .map(utxo -> utxo.getReferenceScriptHash())
                .collect(Collectors.toSet());

        var hashes = new HashSet<String>();
        hashes.addAll(refScripts.keySet());
        hashes.addAll(refScriptInUtxos);

        return hashes;
    }

    public List<Language> getRefScriptLanguages() {
        if (refScripts.size() == 0)
            return Collections.emptyList();

        return refScripts.values().stream().map(tuple -> tuple._1.getLanguage())
                .collect(Collectors.toSet())
                .stream().sorted(Comparator.comparing(Language::getKey))
                .collect(Collectors.toList());
    }

    public void clearRefScripts() {
        refScripts.clear();
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
        transaction.setEra(getSerializationEra());
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
        clearRefScripts();
    }
}
