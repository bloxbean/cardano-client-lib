package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.intent.*;
import java.util.Objects;
import java.util.HashMap;

import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractTx<T> {
    protected String changeAddress;

    //Required for script
    protected PlutusData changeData;
    protected String changeDatahash;

    protected boolean hasMultiAssetMinting;

    // Function-based lazy UTXO resolver for script transactions
    protected Function<UtxoSupplier, List<Utxo>> lazyUtxoResolver;

    // Intent-based architecture: Store intentions for deferred resolution
    protected List<TxIntent> intentions;

    // Variables for YAML parameterization and dynamic value resolution
    protected java.util.Map<String, Object> variables;

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address Address to send the output
     * @param amount  Amount to send
     * @return T
     */
    public T payToAddress(String address, Amount amount) {
        return payToAddress(address, List.of(amount), null, null, null, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address Address to send the output
     * @param amounts List of Amount to send
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts) {
        return payToAddress(address, amounts, null, null, null, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address address
     * @param amounts List of Amount to send
     * @param script  Reference Script
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts, Script script) {
        return payToAddress(address, amounts, null, null, script, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address address
     * @param amount  Amount to send
     * @param script  Reference Script
     * @return T
     */
    public T payToAddress(String address, Amount amount, Script script) {
        return payToAddress(address, List.of(amount), null, null, script, null);
    }

    /**
     * Add an output to the transaction. This method can be called multiple times to add multiple outputs.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param scriptRefBytes Reference Script bytes
     * @return T
     */
    public T payToAddress(String address, List<Amount> amounts, byte[] scriptRefBytes) {
        return payToAddress(address, amounts, null, null, null, scriptRefBytes);
    }

    /**
     * Add an output at contract address with amount and inline datum.
     *
     * @param address contract address
     * @param amount  amount
     * @param datum   inline datum
     * @return T
     */
    public T payToContract(String address, Amount amount, PlutusData datum) {
        return payToAddress(address, List.of(amount), null, datum, null, null);
    }

    /**
     * Add an output at contract address with amounts and inline datum.
     *
     * @param address contract address
     * @param amounts amounts
     * @param datum   inline datum
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum) {
        return payToAddress(address, amounts, null, datum, null, null);
    }

    /**
     * Add an output at contract address with amount and datum hash.
     *
     * @param address   contract address
     * @param amount    amount
     * @param datumHash datum hash
     * @return T
     */
    public T payToContract(String address, Amount amount, String datumHash) {
        return payToAddress(address, List.of(amount), HexUtil.decodeHexString(datumHash), null, null, null);
    }

    /**
     * Add an output at contract address with amount and datum hash.
     *
     * @param address   contract address
     * @param amounts   list of amounts
     * @param datumHash datum hash
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, String datumHash) {
        return payToAddress(address, amounts, HexUtil.decodeHexString(datumHash), null, null, null);
    }

    /**
     * Add an output at contract address with amounts, inline datum and reference script.
     *
     * @param address   address
     * @param amounts   List of Amount to send
     * @param datum     Plutus data
     * @param refScript Reference Script
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum, Script refScript) {
        return payToAddress(address, amounts, null, datum, refScript, null);
    }

    /**
     * Add an output at a contract address with specified amount, inline datum, and a reference script.
     *
     * @param address   the contract address to which the amount will be sent
     * @param amount    the amount to be sent to the contract address
     * @param datum     Plutus data
     * @param refScript Reference Script
     * @return T
     */
    public T payToContract(String address, Amount amount, PlutusData datum, Script refScript) {
        return payToAddress(address, List.of(amount), null, datum, refScript, null);
    }

    /**
     * Add an output at contract address with amounts, inline datum and reference script bytes.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param datum          Plutus data
     * @param scriptRefBytes Reference Script bytes
     * @return T
     */
    public T payToContract(String address, List<Amount> amounts, PlutusData datum, byte[] scriptRefBytes) {
        return payToAddress(address, amounts, null, datum, null, scriptRefBytes);
    }

    /**
     * Add an output to the transaction.
     *
     * @param address        address
     * @param amounts        List of Amount to send
     * @param datumHash      datum hash
     * @param datum          inline datum
     * @param scriptRef      Reference Script
     * @param scriptRefBytes Reference Script bytes
     * @return T
     */
    protected T payToAddress(String address, List<Amount> amounts, byte[] datumHash, PlutusData datum, Script scriptRef, byte[] scriptRefBytes) {
        if (scriptRef != null && scriptRefBytes != null && scriptRefBytes.length > 0)
            throw new TxBuildException("Both scriptRef and scriptRefBytes cannot be set. Only one of them can be set");

        if (datumHash != null && datumHash.length > 0 && datum != null)
            throw new TxBuildException("Both datumHash and datum cannot be set. Only one of them can be set");

        // Create and store payment intention with original objects
        PaymentIntent.PaymentIntentBuilder builder = PaymentIntent.builder()
            .address(address)
            .amounts(amounts);

        if (datum != null) {
            builder.datum(datum);
        } else if (datumHash != null) {
            builder.datumHashBytes(datumHash);
        }

        if (scriptRef != null) {
            builder.refScript(scriptRef);
        } else if (scriptRefBytes != null) {
            builder.scriptRefBytes(scriptRefBytes);
        }

        PaymentIntent intention = builder.build();

        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(intention);

        return (T) this;
    }

    /**
     * Donate to treasury
     * @param currentTreasuryValue current treasury value
     * @param donationAmount donation amount in lovelace
     * @return T
     */
    public T donateToTreasury(@NonNull BigInteger currentTreasuryValue, @NonNull BigInteger donationAmount) {
        // Create and store donation intention
        DonationIntent intention = DonationIntent.of(currentTreasuryValue, donationAmount);

        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(intention);

        return (T) this;
    }

    // ========== Intent-Based Recording ==========

    /**
     * Process all recorded intentions using three-phase architecture.
     * This is called automatically at the beginning of complete().
     *
     * Phase 1: Collect and compose all TxOutputBuilders from intentions
     * Phase 2: Build inputs (UTXO selection) - handled in complete()
     * Phase 3: Apply all transaction transformations - handled in complete()
     */
    protected TxOutputBuilder preComplete() {
        if (intentions == null || intentions.isEmpty()) {
            return null;
        }

        // Create IntentContext for intention processing
        IntentContext intentContext = IntentContext.builder()
            .variables(variables != null ? variables : new HashMap<>())
            .fromAddress(getFromAddress())
            .changeAddress(getChangeAddress())
            .build();

        // Phase 1: Collect and compose all TxOutputBuilders
        List<TxIntent> allIntentions = getIntentions();
        TxOutputBuilder composedOutputBuilder = allIntentions.stream()
            .map(intention -> intention.outputBuilder(intentContext))
            .filter(Objects::nonNull)
            .reduce(TxOutputBuilder::and)
            .orElse(null);

        return composedOutputBuilder;
    }

    /**
     * Apply all intention transformations (Phase 3).
     * This is called after UTXO selection in complete().
     */
    protected TxBuilder applyIntentions() {
        if (intentions == null || intentions.isEmpty()) {
            return (ctx, txn) -> { /* no-op */ };
        }

        // Create IntentContext for intention processing
        IntentContext intentContext = IntentContext.builder()
            .variables(variables != null ? variables : new HashMap<>())
            .fromAddress(getFromAddress())
            .changeAddress(getChangeAddress())
            .build();

        // Phase 3: Apply all transformations (validation + transaction changes)
        TxBuilder combinedBuilder = (ctx, txn) -> { /* no-op base */ };

        List<TxIntent> allIntentions = getIntentions();
        for (TxIntent intention : allIntentions) {
            combinedBuilder = combinedBuilder.andThen(intention.preApply(intentContext));
        }

        for (TxIntent intention : allIntentions) {
            combinedBuilder = combinedBuilder.andThen(intention.apply(intentContext));     // Transformations
        }

        return combinedBuilder;
    }

    /**
     * This is an optional method. By default, the change address is same as the sender address.<br>
     * This method is used to set a different change address.
     * <br><br>
     * By default, if there is a single Tx during a transaction with a custom change address, the default fee payer is set to the
     * custom change address in Tx. So that the fee is deducted from the change output.
     * <br><br>
     * But for a custom change address in Tx and a custom fee payer, make sure feePayer address (which is set through {@link QuickTxBuilder})
     * has enough balance to pay the fee after all outputs .
     *
     * @param changeAddress
     * @return T
     */
    public T withChangeAddress(String changeAddress) {
        this.changeAddress = changeAddress;
        return (T) this;
    }

    /**
     * Add metadata to the transaction.
     *
     * @param metadata
     * @return Tx
     */
    public T attachMetadata(Metadata metadata) {
        // Create and store metadata intention
        MetadataIntent intention = MetadataIntent.from(metadata);

        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(intention);

        return (T) this;
    }

    /**
     * Checks if the transaction has any multi-asset minting or burning.
     *
     * @return true if there are multi-assets to be minted; false otherwise
     */
    boolean hasMultiAssetMinting() {
        return hasMultiAssetMinting;
    }

    TxBuilder complete() {
        if (this.intentions != null && !this.intentions.isEmpty()) {
            java.util.List<com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy> strategies = this.intentions.stream()
                    .filter(i -> i instanceof TxInputIntent)
                    .map(i -> (TxInputIntent) i)
                    .map(TxInputIntent::utxoStrategy)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

            if (!strategies.isEmpty()) {
                this.lazyUtxoResolver = (supplier) -> {
                    java.util.List<com.bloxbean.cardano.client.api.model.Utxo> resolved = new java.util.ArrayList<>();
                    for (var s : strategies) {
                        resolved.addAll(s.resolve(supplier));
                    }
                    return resolved;
                };
            }
        }

        TxOutputBuilder txOutputBuilder = preComplete();

        // Phase 2: Build inputs (UTXO selection)
        TxBuilder txBuilder;
        if (txOutputBuilder == null) {
            txBuilder = (context, txn) -> {
            };
        } else {
            if (lazyUtxoResolver != null) {
                // Handle function-based lazy UTXO resolution for script transactions
                ContextAwareSupplier contextSupplier = new ContextAwareSupplier(lazyUtxoResolver);
                txBuilder = buildInputBuildersFromUtxoSupplier(txOutputBuilder, contextSupplier);
            } else {
                txBuilder = buildInputBuilders(txOutputBuilder);
            }
        }

        // Phase 3: Apply intention transformations
        txBuilder = txBuilder.andThen(applyIntentions());

        return txBuilder;
    }

    private TxBuilder buildInputBuilders(TxOutputBuilder txOutputBuilder) {
        String _changeAddress = getChangeAddress();
        String _fromAddress = getFromAddress();
        Wallet _fromWallet = getFromWallet();
        TxBuilder txBuilder = null;

        if (_fromWallet != null) {
            txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromSender(_fromWallet, _changeAddress));
        } else {
            txBuilder = txOutputBuilder.buildInputs(InputBuilders.createFromSender(_fromAddress, _changeAddress));
        }

        return txBuilder;
    }

    private TxBuilder buildInputBuildersFromUtxoSupplier(TxOutputBuilder txOutputBuilder, Supplier<List<Utxo>> utxoSupplier) {
        String _changeAddr = getChangeAddress();

        TxBuilder txBuilder;

        // Check if we need to inject context for ContextAwareSupplier
        if (utxoSupplier instanceof ContextAwareSupplier) {
            ContextAwareSupplier contextSupplier = (ContextAwareSupplier) utxoSupplier;

            // Create a TxBuilder that first injects context, then builds inputs
            txBuilder = (context, transaction) -> {
                // Inject the context's UtxoSupplier into our lazy supplier
                contextSupplier.setContextSupplier(context.getUtxoSupplier());

                // Now build inputs using the context-aware supplier
                TxBuilder inputBuilder = txOutputBuilder.buildInputs(
                    InputBuilders.createFromUtxos(utxoSupplier, _changeAddr, changeData, changeDatahash)
                );

                // Execute the input builder
                inputBuilder.apply(context, transaction);
            };
        } else {
            // Regular supplier - no context injection needed
            txBuilder = txOutputBuilder.buildInputs(
                InputBuilders.createFromUtxos(utxoSupplier, _changeAddr, changeData, changeDatahash)
            );
        }

        return txBuilder;
    }

    /**
     * Return change address
     *
     * @return String
     */
    protected abstract String getChangeAddress();

    /**
     * Return from address
     *
     * @return String
     */
    protected abstract String getFromAddress();

    protected abstract Wallet getFromWallet();

    /**
     * Perform pre Tx evaluation action. This is called before Script evaluation if any
     * @param transaction
     */
    protected void preTxEvaluation(Transaction transaction) {

    }

    /**
     * Perform post balanceTx action
     *
     * @param transaction
     */
    protected abstract void postBalanceTx(Transaction transaction);

    /**
     * Verify if the data is valid
     */
    protected abstract void verifyData();

    /**
     * Return fee payer
     *
     * @return String
     */
    protected abstract String getFeePayer();

    /**
     * Context-aware supplier that can resolve lazy UTXO strategies when context is available
     */
    private static class ContextAwareSupplier implements Supplier<List<Utxo>> {
        private final Function<UtxoSupplier, List<Utxo>> resolver;
        private UtxoSupplier contextSupplier;

        ContextAwareSupplier(Function<UtxoSupplier, List<Utxo>> resolver) {
            this.resolver = resolver;
        }

        void setContextSupplier(UtxoSupplier supplier) {
            this.contextSupplier = supplier;
        }

        @Override
        public List<Utxo> get() {
            if (contextSupplier == null) {
                throw new TxBuildException("ContextAwareSupplier called without injected UtxoSupplier");
            }
            return resolver.apply(contextSupplier);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class DonationContext {
        private BigInteger currentTreasuryValue;
        private BigInteger donationAmount;
    }

    /**
     * Add an intention to this transaction.
     * This is used internally by the YAML deserialization process.
     * @param intention the intention to add
     */
    public void addIntention(TxIntent intention) {
        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(intention);
    }

    /**
     * Get the list of intentions for this transaction.
     * @return list of intentions, or empty list if none
     */
    public java.util.List<TxIntent> getIntentions() {
        return intentions != null ? intentions : new ArrayList<>();
    }

    /**
     * Get the change address for this transaction.
     * @return change address or null if not set
     */
    public String getPublicChangeAddress() {
        return getChangeAddress();
    }

    /**
     * Get change inline datum as hex if present; null otherwise.
     */
    public String getChangeDatumHex() {
        if (changeData != null) {
            try {
                return changeData.serializeToHex();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get change datum hash if present; null otherwise.
     */
    public String getChangeDatumHash() {
        return changeDatahash;
    }

    /**
     * Set variables for this transaction.
     * Used during YAML deserialization to pass variables to intentions.
     * @param variables the variables map
     */
    public void setVariables(java.util.Map<String, Object> variables) {
        this.variables = variables;
    }

    /**
     * Get variables for this transaction.
     * @return variables map, or null if not set
     */
    public java.util.Map<String, Object> getVariables() {
        return variables;
    }
}
