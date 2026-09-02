package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.*;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.coinselection.impl.ExcludeUtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.ExcludeUtxoSelector;
import com.bloxbean.cardano.client.coinselection.impl.LargestFirstUtxoSelectionStrategy;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.*;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.intent.MintingIntent;
import com.bloxbean.cardano.client.quicktx.intent.NativeScriptAttachmentIntent;
import com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntent;
import com.bloxbean.cardano.client.quicktx.extension.BalanceFinalization;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionBuildContext;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.script.DefaultScriptRegistry;
import com.bloxbean.cardano.client.quicktx.script.ScriptRegistry;
import com.bloxbean.cardano.client.quicktx.signing.SignerBinding;
import com.bloxbean.cardano.client.quicktx.signing.SignerRegistry;
import com.bloxbean.cardano.client.quicktx.signing.SignerScopes;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.hdwallet.Wallet;
import com.bloxbean.cardano.hdwallet.util.HDWalletAddressIterator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * QuickTxBuilder is a utility class to build and submit transactions quickly. It provides high level APIs to build
 * transactions with minimal configuration. Internally it uses composable functions to build transactions. Same instance of
 * QuickBuilder can be reused to build multiple transactions.
 * <br>
 * <br>
 * Example:
 * <pre>
 *   {@code QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
 *    Tx tx = new Tx()
 *             .payToAddress(receiver1, Amount.ada(1.5))
 *             .payToAddress(receiver2, Amount.ada(2.5))
 *             .attachMetadata(MessageMetadata.create().add("This is a test message"))
 *             .attachMetadata(metadata)
 *             .from(senderAddr);
 *
 *     Result<String> result = quickTxBuilder.compose(tx)
 *             .withSigner(SignerProviders.signerFrom(sender))
 *             .complete();
 *    }
 * </pre>
 */
@Slf4j
public class QuickTxBuilder {
    private static final int MAX_COLLATERAL_INPUTS = 3;
    private static final Amount DEFAULT_COLLATERAL_AMT = Amount.ada(5.0);
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    private TransactionProcessor transactionProcessor;
    private Consumer<Transaction> txInspector;

    private ScriptSupplier backendScriptSupplier;
    private final Map<String, QuickTxExtension> extensions = new LinkedHashMap<>();
    private static final int MAX_EXTENSION_STABILIZATION_PASSES = 3;

    /** Register an extension for this builder instance. */
    public QuickTxBuilder withExtension(QuickTxExtension extension) {
        if (extension == null) throw new IllegalArgumentException("extension is required");
        if (extension.id() == null || extension.id().isBlank())
            throw new IllegalArgumentException("extension id is required");
        if (extension.schemaVersion() == null || extension.schemaVersion().isBlank())
            throw new IllegalArgumentException("extension schema version is required");
        if (extension.operations() == null)
            throw new IllegalArgumentException("extension operations are required");
        QuickTxExtension existing = extensions.putIfAbsent(extension.id(), extension);
        if (existing != null && existing != extension)
            throw new IllegalArgumentException("Extension already registered: " + extension.id());
        return this;
    }

    /**
     * Create QuickTxBuilder
     *
     * @param utxoSupplier           utxo supplier
     * @param protocolParamsSupplier protocol params supplier
     * @param transactionProcessor   transaction processor
     */
    public QuickTxBuilder(UtxoSupplier utxoSupplier,
                          ProtocolParamsSupplier protocolParamsSupplier,
                          TransactionProcessor transactionProcessor) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.transactionProcessor = transactionProcessor;
    }

    /**
     * Create QuickTxBuilder
     * @param utxoSupplier - utxo supplier to get utxos
     * @param protocolParamsSupplier - protocol params supplier to get protocol parameters
     * @param scriptSupplier - script supplier to get scripts
     * @param transactionProcessor - transaction processor to submit transaction
     */
    public QuickTxBuilder(UtxoSupplier utxoSupplier,
                          ProtocolParamsSupplier protocolParamsSupplier,
                          ScriptSupplier scriptSupplier,
                          TransactionProcessor transactionProcessor) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.backendScriptSupplier = scriptSupplier;
        this.transactionProcessor = transactionProcessor;
    }

    /**
     * Create QuickTxBuilder from BackendService
     *
     * @param backendService
     */
    public QuickTxBuilder(BackendService backendService) {
        this.utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        this.protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        this.transactionProcessor = new DefaultTransactionProcessor(backendService.getTransactionService());

        try {
            this.backendScriptSupplier = new DefaultScriptSupplier(backendService.getScriptService());
        } catch (UnsupportedOperationException e) {
            //Not supported
        }
    }

    /**
     * Get the UtxoSupplier used by this builder.
     * Useful for creating TransactionEvaluators with the same supplier.
     *
     * @return the UtxoSupplier
     */
    public UtxoSupplier getUtxoSupplier() {
        return utxoSupplier;
    }

    /**
     * Get the ProtocolParamsSupplier used by this builder.
     * Useful for creating TransactionEvaluators with the same supplier.
     *
     * @return the ProtocolParamsSupplier
     */
    public ProtocolParamsSupplier getProtocolParamsSupplier() {
        return protocolParamsSupplier;
    }

    /**
     * Create a QuickTxBuilder instance with specified BackendService and UtxoSupplier.
     *
     * @param backendService backend service to get protocol params and submit transactions
     * @param utxoSupplier utxo supplier to get utxos
     */
    public QuickTxBuilder(BackendService backendService, UtxoSupplier utxoSupplier) {
        this(utxoSupplier,
                new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                new DefaultTransactionProcessor(backendService.getTransactionService()));
    }

    /**
     * Create TxContext for the given txs
     *
     * @param txs list of txs
     * @return TxContext which can be used to build and submit transaction
     */
    public TxContext compose(AbstractTx... txs) {
        if (txs == null || txs.length == 0)
            throw new TxBuildException("No txs provided to build transaction");
        return new TxContext(txs);
    }

    /**
     * Create TxContext from a TxPlan with automatic property mapping.
     * This method maps TxPlan context properties to the corresponding TxContext methods.
     *
     * <p>The context retains the plan's transaction objects. Registry resolution during a build
     * may populate resolved policy or script material on their intents, so callers must not build
     * the same mutable plan concurrently. Create an execution-local plan copy when a definition
     * is reused across threads.</p>
     *
     * @param plan the transaction plan with transactions and context properties
     * @return TxContext with all properties applied
     */
    public TxContext compose(TxPlan plan) {
        if (plan == null)
            throw new TxBuildException("TxPlan cannot be null");

        List<AbstractTx<?>> transactions = plan.getTxs();
        if (transactions == null || transactions.isEmpty())
            throw new TxBuildException("TxPlan must contain at least one transaction");

        plan.getExtensions().forEach((namespace, metadata) -> {
            QuickTxExtension extension = extensions.get(metadata.getExtension());
            if (extension == null)
                throw new TxBuildException("TxPlan namespace '" + namespace
                        + "' requires unregistered extension " + metadata.getExtension());
            try {
                extension.validateMetadata(metadata);
            } catch (IllegalArgumentException e) {
                throw new TxBuildException("Invalid extension metadata for namespace '"
                        + namespace + "': " + e.getMessage(), e);
            }
        });

        // Create TxContext with transactions
        TxContext context = new TxContext(transactions.toArray(new AbstractTx[0]));
        plan.getExtensions().values().forEach(metadata ->
                context.extensionMetadata.put(metadata.getExtension(), metadata));

        // Apply context properties from TxPlan
        if (plan.getFeePayer() != null) {
            context.feePayer(plan.getFeePayer());
        }

        if (plan.getCollateralPayer() != null) {
            context.collateralPayer(plan.getCollateralPayer());
        }

        if (plan.getRequiredSigners() != null && !plan.getRequiredSigners().isEmpty()) {
            // Convert hex strings back to byte arrays for TxContext
            byte[][] signerCredentials = plan.getRequiredSigners().stream()
                .map(HexUtil::decodeHexString)
                .toArray(byte[][]::new);
            context.withRequiredSigners(signerCredentials);
        }

        if (plan.getValidFromSlot() != null) {
            context.validFrom(plan.getValidFromSlot());
        }

        if (plan.getValidToSlot() != null) {
            context.validTo(plan.getValidToSlot());
        }

        // Map reference-based context
        if (plan.getFeePayerRef() != null && !plan.getFeePayerRef().isEmpty()) {
            context.feePayerRef(plan.getFeePayerRef());
        }
        if (plan.getCollateralPayerRef() != null && !plan.getCollateralPayerRef().isEmpty()) {
            context.collateralPayerRef(plan.getCollateralPayerRef());
        }
        if (plan.getSignerRefs() != null && !plan.getSignerRefs().isEmpty()) {
            for (com.bloxbean.cardano.client.quicktx.serialization.TransactionDocument.SignerRef sr : plan.getSignerRefs()) {
                if (sr.getRef() != null) {
                    // A portable signer ref may omit the scope (the schema makes it optional);
                    // default to payment, matching TxPlan.withSigner(ref).
                    String scope = (sr.getScope() != null && !sr.getScope().isBlank())
                            ? sr.getScope() : SignerScopes.PAYMENT;
                    context.withSignerRef(sr.getRef(), scope);
                }
            }
        }

        if (plan.getDepositPayer() != null) {
            context.depositPayer(plan.getDepositPayer());
        }
        if (plan.getDepositMode() != null) {
            context.depositMode(DepositMode.valueOf(plan.getDepositMode()));
        }

        return context;
    }

    /**
     * Compose from a TxPlan and configure a SignerRegistry for ref resolution.
     */
    public TxContext compose(TxPlan plan, SignerRegistry registry) {
        TxContext ctx = compose(plan);
        if (registry != null) ctx.withSignerRegistry(registry);
        return ctx;
    }

    /**
     * Compose from a TxPlan and configure registries for ref resolution.
     * The returned context has the same execution-local ownership requirement documented by
     * {@link #compose(TxPlan)}.
     */
    public TxContext compose(TxPlan plan, SignerRegistry signerRegistry, ScriptRegistry scriptRegistry) {
        TxContext ctx = compose(plan);
        if (signerRegistry != null) ctx.withSignerRegistry(signerRegistry);
        if (scriptRegistry != null) ctx.withScriptRegistry(scriptRegistry);
        return ctx;
    }

    /**
     * TxContext is created for group of transactions which are to be submitted as a single transaction.
     */
    public class TxContext {
        private AbstractTx[] txList;
        private String feePayer;
        private Wallet feePayerWallet;
        private String feePayerRef;
        private String collateralPayer;
        private Wallet collateralPayerWallet;
        private String collateralPayerRef;
        private Set<byte[]> requiredSigners;
        private Set<TransactionInput> collateralInputs;

        private TxBuilder preBalanceTrasformer;
        private TxBuilder postBalanceTrasformer;

        private int additionalSignerCount = 0;
        private int signersCount = 0;
        private TxSigner signers;

        private long validFrom;
        private long validTo;

        private boolean mergeOutputs = false;

        private TransactionEvaluator txnEvaluator;
        private UtxoSelectionStrategy utxoSelectionStrategy;
        private ScriptSupplier scriptSupplier;
        private Verifier txVerifier;

        private List<PlutusScript> referenceScripts;

        private boolean ignoreScriptCostEvaluationError = false;
        private Era serializationEra;
        private boolean removeDuplicateScriptWitnesses = false;
        private boolean searchUtxoByAddressVkh = false;

        // Deposit resolution configuration
        private String depositPayer;
        private DepositMode depositMode;

        // Optional per-context override for registry
        private SignerRegistry contextSignerRegistry;
        private ScriptRegistry contextScriptRegistry;
        private final Map<String, ExtensionMetadata> extensionMetadata = new LinkedHashMap<>();

        // Additional signer refs (ref + scope)
        private class SignerRef {
            final String ref;
            final String scope;
            SignerRef(String ref, String scope) {
                this.ref = ref; this.scope = scope;
            }
        }
        private List<SignerRef> signerRefs;
        private Set<String> resolvedSignerRefKeys;

        TxContext(AbstractTx... txs) {
            this.txList = txs;
        }

        /**
         * Set fee payer address. When there is only one tx, sender's address is used as fee payer address.
         * When there are more than one txs, fee payer address is mandatory.
         *
         * @param address fee payer address
         * @return TxContext - the updated transaction context with the fee payer set
         * @throws TxBuildException if the fee payer has already been set
         */
        public TxContext feePayer(String address) {
            if (this.feePayerWallet != null || this.feePayer != null)
                throw new TxBuildException("The fee payer has already been set. It can only be set once.");

            this.feePayer = address;
            return this;
        }

        /**
         * Sets the fee payer wallet for the transaction. When there is only one tx, sender's address is used as fee payer address.
         * When there are more than one txs, fee payer address/wallet is mandatory.
         *
         * @param feePayerWallet the wallet that will act as the fee payer for the transaction
         * @return TxContext - the updated transaction context with the fee payer set
         * @throws TxBuildException if the fee payer has already been set
         */
        public TxContext feePayer(Wallet feePayerWallet) {
            if (this.feePayerWallet != null || this.feePayer != null)
                throw new TxBuildException("The fee payer has already been set. It can only be set once.");

            this.feePayerWallet = feePayerWallet;
            // TODO feePayer is not used in this scenarios, but it must be set to avoid breaking other things.
            this.feePayer = this.feePayerWallet.getBaseAddress(0).getAddress();

            return this;
        }

        /**
         * Set an explicit deposit payer address. When not set, deposits are paid by the
         * transaction's from() address (or feePayer as fallback).
         *
         * @param address deposit payer address
         * @return TxContext
         */
        public TxContext depositPayer(String address) {
            this.depositPayer = address;
            return this;
        }

        /**
         * Set the deposit resolution mode. Controls how Phase 4 finds funds to cover
         * protocol deposits. Default is {@link DepositMode#AUTO}.
         *
         * @param mode the deposit mode
         * @return TxContext
         */
        public TxContext depositMode(DepositMode mode) {
            this.depositMode = mode;
            return this;
        }

        /**
         * Sets the provided collateral payer address. This method ensures that the collateral payer can only be set once.
         *
         * @param address the address of the collateral payer to be set
         * @return TxContext
         * @throws TxBuildException if the collateral payer has already been set
         */
        public TxContext collateralPayer(String address) {
            if (this.collateralPayerWallet != null || this.collateralPayer != null)
                throw new TxBuildException("The collateral payer has already been set. It can only be set once.");

            this.collateralPayer = address;
            return this;
        }

        /**
         * Sets the collateral payer using the provided wallet. This method ensures that the collateral payer
         * is set only once.
         *
         * @param wallet the wallet from which the collateral payer address will be derived
         * @return TxContext
         * @throws TxBuildException if the collateral payer has already been set
         */
        public TxContext collateralPayer(Wallet wallet) {
            if (this.collateralPayerWallet != null || this.collateralPayer != null)
                throw new TxBuildException("The collateral payer has already been set. It can only be set once.");

            this.collateralPayerWallet = wallet;
            // TODO collateralPayer is not used in this scenarios, but it must be set to avoid breaking other things.
            this.collateralPayer = this.collateralPayerWallet.getBaseAddress(0).getAddress();

            return this;
        }

        /**
         * Override the builder-level signer registry for this composition.
         */
        public TxContext withSignerRegistry(SignerRegistry registry) {
            this.contextSignerRegistry = registry;
            return this;
        }

        /**
         * Override the builder-level script registry for this composition.
         * Logical script_ref values require a registry. Hash-based Plutus script_hash
         * values can also be resolved through a configured ScriptSupplier.
         */
        public TxContext withScriptRegistry(ScriptRegistry registry) {
            this.contextScriptRegistry = registry;
            return this;
        }

        /**
         * Set the fee payer using a reference (e.g., account://..., wallet://...).
         */
        public TxContext feePayerRef(String ref) {
            if (this.feePayerWallet != null || this.feePayer != null)
                throw new TxBuildException("The fee payer has already been set. Cannot set feePayerRef.");
            if (ref == null || ref.isBlank())
                throw new TxBuildException("feePayerRef cannot be null or blank");
            this.feePayerRef = ref;
            return this;
        }

        /**
         * Set the collateral payer using a reference (e.g., account://..., wallet://...).
         */
        public TxContext collateralPayerRef(String ref) {
            if (this.collateralPayerWallet != null || this.collateralPayer != null)
                throw new TxBuildException("The collateral payer has already been set. Cannot set collateralPayerRef.");
            if (ref == null || ref.isBlank())
                throw new TxBuildException("collateralPayerRef cannot be null or blank");
            this.collateralPayerRef = ref;
            return this;
        }

        /**
         * Add an additional signer reference for the given scope (payment|stake|drep|committeeCold|committeeHot|policy|custom).
         */
        public TxContext withSignerRef(String ref, String scope) {
            if (ref == null || ref.isBlank())
                throw new TxBuildException("signer ref cannot be null or blank");
            if (scope == null || scope.isBlank())
                throw new TxBuildException("signer scope cannot be null or blank");
            if (this.signerRefs == null)
                this.signerRefs = new ArrayList<>();
            this.signerRefs.add(new SignerRef(ref, scope));
            return this;
        }

        /**
         * Set a TxBuilder function to transform the transaction before balance calculation.
         * This is useful when additional transformation logic is required before balance calculation.
         *
         * @param txBuilder TxBuilder function
         * @return TxContext
         */
        public TxContext preBalanceTx(TxBuilder txBuilder) {
            this.preBalanceTrasformer = txBuilder;
            return this;
        }

        /**
         * Set a TxBuilder function to transform the transaction after balance calculation.
         * As this TxBuilder is called after fee calculation and transaction balancing, don't add any transformation which
         * can change the fee or balance of the transaction.
         *
         * @param txBuilder TxBuilder function
         * @return TxContext
         */
        public TxContext postBalanceTx(TxBuilder txBuilder) {
            this.postBalanceTrasformer = txBuilder;
            return this;
        }

        /**
         * This is an optional method to set additional signers count. This is useful when you have multiple additional composite signers and calculating
         * total additional signers count is not possible automatically by the builder.
         * <br>
         * For example, if you have added 1 additional signer with two TxSigner instance composed together,
         * you can set the additional signers count to 2.
         *
         * @return Tx
         */
        public TxContext additionalSignersCount(int additionalSigners) {
            this.additionalSignerCount = additionalSigners;
            return this;
        }

        /**
         * Build unsigned transaction
         *
         * @return Transaction
         */
        public Transaction build() {
            Tuple<TxBuilderContext, TxBuilder> tuple = _build();
            return tuple._1.build(tuple._2);
        }

        /**
         * Build and sign transaction
         *
         * @return Transaction
         */
        public Transaction buildAndSign() {
            Tuple<TxBuilderContext, TxBuilder> tuple = _build();

            if (signers != null)
                return tuple._1.buildAndSign(tuple._2, signers);
            else
                throw new IllegalStateException("No signers found");
        }

        private Tuple<TxBuilderContext, TxBuilder> _build() {
            ExtensionBuildContext extensionContext = new ExtensionBuildContext(
                    txList, utxoSupplier, protocolParamsSupplier);
            List<TxBuildExtension> buildExtensions = extensions.values().stream()
                    .sorted(Comparator.comparingInt(QuickTxExtension::order)
                            .thenComparing(QuickTxExtension::id))
                    .map(extension -> extension.newBuildExtension(
                            extensionMetadata.getOrDefault(extension.id(), extension.metadata())))
                    .collect(Collectors.toList());

            validateExtensionIntents();

            // Resolve references (fromRef, payer refs, additional signer refs) before building
            SignerRegistry effectiveRegistry = this.contextSignerRegistry;
            boolean hasScriptReferences = hasScriptReferences();
            ScriptRegistry effectiveScriptRegistry = hasScriptReferences ? effectiveScriptRegistry() : null;
            if (this.resolvedSignerRefKeys == null)
                this.resolvedSignerRefKeys = new HashSet<>();
            Set<String> resolvedSignerRefs = this.resolvedSignerRefKeys;

            if (effectiveRegistry != null) {
                // Resolve tx-level fromRef for regular Txs and add payment signer
                for (AbstractTx tx : txList) {
                    if (tx instanceof Tx) {
                        Tx regularTx = (Tx) tx;
                        String fromRef = regularTx.getFromRef();
                        if (fromRef != null && !fromRef.isBlank()) {
                            var bindingOpt = effectiveRegistry.resolve(fromRef);
                            if (bindingOpt.isEmpty())
                                throw new TxBuildException("Unable to resolve fromRef: " + fromRef);
                            var binding = bindingOpt.get();
                            // Prefer wallet when available; else preferred address
                            if (binding.asWallet().isPresent()) {
                                regularTx.from(binding.asWallet().get());
                            } else if (binding.preferredAddress().isPresent()) {
                                regularTx.from(binding.preferredAddress().get());
                            } else {
                                throw new TxBuildException("Resolved fromRef has neither wallet nor preferred address: " + fromRef);
                            }
                        }
                    }
                }

                // Resolve fee/collateral payer refs if provided
                if (this.feePayerRef != null && (this.feePayer == null && this.feePayerWallet == null)) {
                    var bindingOpt = effectiveRegistry.resolve(this.feePayerRef);
                    if (bindingOpt.isEmpty())
                        throw new TxBuildException("Unable to resolve feePayerRef: " + this.feePayerRef);
                    var binding = bindingOpt.get();
                    if (binding.asWallet().isPresent()) {
                        this.feePayerWallet = binding.asWallet().get();
                        this.feePayer = this.feePayerWallet.getBaseAddress(0).getAddress();
                    } else if (binding.preferredAddress().isPresent()) {
                        this.feePayer = binding.preferredAddress().get();
                    } else {
                        throw new TxBuildException("Resolved feePayerRef has neither wallet nor preferred address: " + this.feePayerRef);
                    }
                }

                if (this.collateralPayerRef != null && (this.collateralPayer == null && this.collateralPayerWallet == null)) {
                    var bindingOpt = effectiveRegistry.resolve(this.collateralPayerRef);
                    if (bindingOpt.isEmpty())
                        throw new TxBuildException("Unable to resolve collateralPayerRef: " + this.collateralPayerRef);
                    var binding = bindingOpt.get();
                    if (binding.asWallet().isPresent()) {
                        this.collateralPayerWallet = binding.asWallet().get();
                        this.collateralPayer = this.collateralPayerWallet.getBaseAddress(0).getAddress();
                    } else if (binding.preferredAddress().isPresent()) {
                        this.collateralPayer = binding.preferredAddress().get();
                    } else {
                        throw new TxBuildException("Resolved collateralPayerRef has neither wallet nor preferred address: " + this.collateralPayerRef);
                    }
                }

                resolvePolicyRefs(effectiveRegistry, resolvedSignerRefs);

                // Resolve additional signer refs
                if (this.signerRefs != null && !this.signerRefs.isEmpty()) {
                    for (SignerRef sr : this.signerRefs) {
                        resolveSignerRef(effectiveRegistry, sr.ref, sr.scope, resolvedSignerRefs);
                    }
                }
            } else {
                // No registry configured; ensure no refs are present
                for (AbstractTx tx : txList) {
                    if (tx instanceof Tx) {
                        String ref = ((Tx) tx).getFromRef();
                        if (ref != null && !ref.isBlank())
                            throw new TxBuildException("fromRef set but no SignerRegistry configured");
                    }
                    if (hasPolicyRefs(tx))
                        throw new TxBuildException("policy_ref set but no SignerRegistry configured");
                }
                if (this.feePayerRef != null)
                    throw new TxBuildException("feePayerRef set but no SignerRegistry configured");
                if (this.collateralPayerRef != null)
                    throw new TxBuildException("collateralPayerRef set but no SignerRegistry configured");
                if (this.signerRefs != null && !this.signerRefs.isEmpty())
                    throw new TxBuildException("signer refs set but no SignerRegistry configured");
            }

            if (hasScriptReferences && effectiveScriptRegistry != null) {
                resolveScriptReferences(effectiveScriptRegistry);
            } else if (hasScriptReferences) {
                throw new TxBuildException("script_ref/script_hash set but no ScriptRegistry or ScriptSupplier configured");
            }

            // Extensions see fully resolved authoring references but still run before any Tx is
            // completed, so they can aggregate semantic declarations into ordinary core intents.
            buildExtensions.forEach(extension -> extension.prepare(extensionContext));

            TxBuilder txBuilder = (context, txn) -> {
            };
            boolean containsScriptTx = false;
            boolean hasMultiAssetMint = false;

            Set<String> fromAddresses = new HashSet<>();
            for (AbstractTx tx : txList) {
                tx.verifyData();

                //Check for duplicate from addresses in Txs
                if (tx.getFromAddress() != null && fromAddresses.contains(tx.getFromAddress())) {
                    throw new TxBuildException("Duplicate from address found in Txs. Please use unique from addresses for each Tx.");
                } else {
                    if (tx.getFromAddress() != null)
                        fromAddresses.add(tx.getFromAddress());
                }

                //For scriptTx or Tx with script intents, set fee payer as change address and from address by default.
                if (tx.getChangeAddress() == null && (tx instanceof ScriptTx || tx.hasScriptIntents())) {
                    tx.withChangeAddress(feePayer);
                }
                if (tx.getFromAddress() == null && (tx instanceof ScriptTx || tx.hasScriptIntents())) {
                    if (feePayerWallet != null)
                        tx.setDefaultFrom(feePayerWallet);
                    else
                        tx.setDefaultFrom(feePayer);
                }

                // Propagate deposit resolution configuration
                if (depositPayer != null) {
                    tx.setDepositPayer(depositPayer);
                }
                if (depositMode != null) {
                    tx.setDepositMode(depositMode);
                }

                txBuilder = txBuilder.andThen(tx.complete());

                if (tx instanceof ScriptTx || tx.hasScriptIntents())
                    containsScriptTx = true;

                hasMultiAssetMint = hasMultiAssetMint || tx.hasMultiAssetMinting();
            }

            int totalSigners = getTotalSigners();

            TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);
            if (backendScriptSupplier != null)
                txBuilderContext.setScriptSupplier(backendScriptSupplier);

            //Set merge outputs flag
            txBuilderContext.mergeOutputs(mergeOutputs);

            //Enable/Disable search by address vkh
            txBuilderContext.withSearchUtxoByAddressVkh(searchUtxoByAddressVkh);

            //Set tx evaluator for script cost calculation
            if (txnEvaluator != null)
                txBuilderContext.withTxnEvaluator(txnEvaluator);
            else
                txBuilderContext.withTxnEvaluator(transactionProcessor);

            //Set utxo selection strategy
            if (utxoSelectionStrategy != null)
                txBuilderContext.setUtxoSelectionStrategy(utxoSelectionStrategy);

            //override default script supplier
            if (scriptSupplier != null)
                txBuilderContext.setScriptSupplier(scriptSupplier);

            if (serializationEra != null)
                txBuilderContext.withSerializationEra(serializationEra);

            //If collateral inputs are set, exclude them from utxo selection
            if (collateralInputs != null && !collateralInputs.isEmpty()) {
                txBuilderContext.setUtxoSelectionStrategy(
                        new ExcludeUtxoSelectionStrategy(txBuilderContext.getUtxoSelectionStrategy(), collateralInputs));
                txBuilderContext.setUtxoSelector(new ExcludeUtxoSelector(txBuilderContext.getUtxoSelector(), collateralInputs));
            }

            //requiredSigners
            if (requiredSigners != null && !requiredSigners.isEmpty()) {
                txBuilder = txBuilder.andThen(addRequiredSignersBuilder());
            }

            //set reference scripts if set
            if (referenceScripts != null && !referenceScripts.isEmpty()) {
                referenceScripts.forEach(script -> txBuilderContext.addRefScripts(script));
            }

            if (preBalanceTrasformer != null)
                txBuilder = txBuilder.andThen(preBalanceTrasformer);

            if (feePayer == null && feePayerWallet == null) {
                if (txList.length == 1) {
                    feePayer = txList[0].getFeePayer();
                    if (feePayer == null)
                        throw new TxBuildException("No fee payer set. Please set fee payer address using feePayer() method");
                } else
                    throw new TxBuildException("Fee Payer address is not set. " +
                            "It's mandatory when there are more than one txs");
            }

            //Set validity interval
            txBuilder = buildValidityIntervalTxBuilder(txBuilder);

            if (containsScriptTx) {
                if (collateralPayer == null && collateralPayerWallet == null) {
                    if (feePayerWallet != null)
                        collateralPayerWallet = feePayerWallet;
                    else
                        collateralPayer = feePayer;
                }

                if (collateralPayerWallet != null) {
                    txBuilder = txBuilder.andThen(buildCollateralOutput(collateralPayerWallet));
                } else {
                    txBuilder = txBuilder.andThen(buildCollateralOutput(collateralPayer));
                }
            }

            if (containsScriptTx) {
                //Resolve any reference scripts if any
                if (referenceScripts == null || referenceScripts.isEmpty()) { //Resolve only if not set explicitly
                    txBuilder = txBuilder.andThen(ReferenceScriptResolver.resolveReferenceScript());
                }

                txBuilder = txBuilder.andThen(((context, transaction) -> {
                    boolean negativeAmt = transaction.getBody().getOutputs()
                            .stream()
                            .filter(output -> output.getValue().getCoin().compareTo(BigInteger.ZERO) < 0)
                            .collect(Collectors.toList()).size() > 0;
                    if (negativeAmt) {
                        log.debug("Negative amount found in transaction output. " +
                                "Script cost evaluation will be done after balancing the transaction.");
                        return;
                    }

                    //This is only applicable for ScriptTx for now, as default impl is empty for this method.
                    for (AbstractTx tx: txList) {
                        tx.preTxEvaluation(transaction);
                    }
                    for (TxBuildExtension extension : buildExtensions) {
                        extension.beforeScriptEvaluation(extensionContext, transaction);
                    }

                    try {
                        ScriptCostEvaluators.evaluateScriptCost().apply(context, transaction);
                    } catch (Exception e) {
                        //Ignore as it could happen due to insufficient ada in utxo
                        log.warn("Error while evaluating script cost", e);
                        if (log.isDebugEnabled())
                            log.debug("Transaction : " + JsonUtil.getPrettyJson(transaction));
                        if (!ignoreScriptCostEvaluationError)
                            throw new TxBuildException("Error while evaluating script cost", e);
                    }
                }));
            }

            // Balance outputs. Keep this builder separate so an extension can request a bounded
            // re-finalize/evaluate/balance pass when balancing changes index-sensitive content.
            TxBuilder balanceTxBuilder;
            if (feePayerWallet != null) {
                var walletAddrIterator = new HDWalletAddressIterator(feePayerWallet, utxoSupplier);
                balanceTxBuilder = ScriptBalanceTxProviders.balanceTx(walletAddrIterator, totalSigners, containsScriptTx);
            } else
                balanceTxBuilder = ScriptBalanceTxProviders.balanceTx(feePayer, totalSigners, containsScriptTx);
            txBuilder = txBuilder.andThen(balanceTxBuilder);

            final boolean scriptTxPresent = containsScriptTx;
            txBuilder = txBuilder.andThen((context, transaction) -> {
                for (int pass = 0; pass <= MAX_EXTENSION_STABILIZATION_PASSES; pass++) {
                    boolean refinalized = false;
                    for (TxBuildExtension extension : buildExtensions) {
                        refinalized |= extension.afterBalance(extensionContext, transaction)
                                == BalanceFinalization.REFINALIZED;
                    }
                    if (!refinalized) {
                        return;
                    }
                    if (pass == MAX_EXTENSION_STABILIZATION_PASSES) {
                        throw new TxBuildException("QuickTx extensions did not converge after "
                                + MAX_EXTENSION_STABILIZATION_PASSES + " stabilization passes");
                    }

                    for (AbstractTx tx : txList) tx.preTxEvaluation(transaction);
                    for (TxBuildExtension extension : buildExtensions)
                        extension.beforeScriptEvaluation(extensionContext, transaction);
                    if (scriptTxPresent) ScriptCostEvaluators.evaluateScriptCost().apply(context, transaction);
                    balanceTxBuilder.apply(context, transaction);
                }
            });

            boolean extensionRequestsWitnessDeduplication = buildExtensions.stream()
                    .anyMatch(TxBuildExtension::removeDuplicateScriptWitnesses);
            if ((containsScriptTx || hasMultiAssetMint)
                    && (removeDuplicateScriptWitnesses || extensionRequestsWitnessDeduplication)) {
                txBuilder = txBuilder.andThen(DuplicateScriptWitnessChecker.removeDuplicateScriptWitnesses());
            }

            if (postBalanceTrasformer != null)
                txBuilder = txBuilder.andThen(postBalanceTrasformer);

            //Call post balance function of each tx
            for (AbstractTx tx : txList) {
                txBuilder = txBuilder.andThen(((context, transaction) -> {
                    tx.postBalanceTx(transaction);
                }));
            }

            // This is deliberately last. User post-balance transforms and core post-balance
            // hooks are allowed to mutate the transaction, so index-sensitive extensions must
            // verify the actual final body rather than an intermediate balanced snapshot.
            txBuilder = txBuilder.andThen((context, transaction) -> {
                for (TxBuildExtension extension : buildExtensions)
                    extension.verify(extensionContext, transaction);
            });

            return new Tuple<>(txBuilderContext, txBuilder);
        }

        private void validateExtensionIntents() {
            for (AbstractTx tx : txList) {
                for (var intent : tx.getIntentions()) {
                    if (!(intent instanceof ExtensionIntent)) continue;
                    ExtensionIntent extensionIntent = (ExtensionIntent) intent;
                    extensionIntent.validate();
                    QuickTxExtension extension = extensions.get(extensionIntent.getExtensionId());
                    if (extension == null)
                        throw new TxBuildException("No runtime extension registered for intent "
                                + extensionIntent.getExtensionId() + ":" + extensionIntent.getOperation());
                    Class<? extends ExtensionIntent> expectedType =
                            extension.intentTypes().get(extensionIntent.getOperation());
                    if (!extension.operations().contains(extensionIntent.getOperation())
                            || expectedType == null)
                        throw new TxBuildException("Unsupported operation " + extensionIntent.getOperation()
                                + " for extension " + extensionIntent.getExtensionId());
                    if (!expectedType.isInstance(extensionIntent))
                        throw new TxBuildException("Intent " + extensionIntent.getClass().getName()
                                + " cannot claim operation " + extensionIntent.getExtensionId() + ":"
                                + extensionIntent.getOperation() + "; expected "
                                + expectedType.getName());
                }
            }
        }

        private void resolvePolicyRefs(SignerRegistry registry, Set<String> resolvedSignerRefs) {
            for (AbstractTx tx : txList) {
                for (var intent : tx.getIntentions()) {
                    if (intent instanceof MintingIntent) {
                        resolvePolicyRef((MintingIntent) intent, registry, resolvedSignerRefs);
                    }
                }
            }
        }

        private void resolvePolicyRef(MintingIntent intent, SignerRegistry registry, Set<String> resolvedSignerRefs) {
            String policyRef = intent.getPolicyRef();
            if (policyRef == null) {
                return;
            }

            policyRef = policyRef.trim();
            if (policyRef.isEmpty()) {
                throw new TxBuildException("policy_ref cannot be blank");
            }
            intent.setPolicyRef(policyRef);

            if (intent.hasSerializedScriptFields()) {
                throw new TxBuildException("policy_ref cannot be combined with script_hex or script_type");
            }
            if (intent.getScript() != null && !intent.isPolicyRefResolved()) {
                throw new TxBuildException("policy_ref cannot be combined with a runtime script");
            }

            var bindingOpt = registry.resolve(policyRef);
            if (bindingOpt.isEmpty()) {
                throw new TxBuildException("Unable to resolve policy_ref: " + policyRef);
            }

            SignerBinding binding = bindingOpt.get();
            var policyOpt = binding.asPolicy();
            if (policyOpt.isEmpty()) {
                throw new TxBuildException("Resolved policy_ref does not expose a policy script: " + policyRef);
            }

            intent.resolvePolicy(policyOpt.get());
            addSignerForBinding(policyRef, SignerScopes.POLICY, binding, resolvedSignerRefs);
        }

        private void resolveScriptReferences(ScriptRegistry registry) {
            for (AbstractTx tx : txList) {
                for (var intent : tx.getIntentions()) {
                    if (intent instanceof ScriptValidatorAttachmentIntent) {
                        resolveValidatorScriptReference((ScriptValidatorAttachmentIntent) intent, registry);
                    } else if (intent instanceof NativeScriptAttachmentIntent) {
                        resolveNativeScriptReference((NativeScriptAttachmentIntent) intent, registry);
                    }
                }
            }
        }

        private ScriptRegistry effectiveScriptRegistry() {
            if (this.contextScriptRegistry != null) {
                return this.contextScriptRegistry;
            }

            ScriptSupplier effectiveScriptSupplier = this.scriptSupplier != null ? this.scriptSupplier : backendScriptSupplier;
            if (effectiveScriptSupplier != null) {
                // Hash-based Plutus lookups can reuse the existing ScriptSupplier. Logical script_ref
                // values still need an explicit ScriptRegistry because ScriptSupplier is hash-only.
                return new DefaultScriptRegistry().withScriptSupplier(effectiveScriptSupplier);
            }

            return null;
        }

        private void resolveValidatorScriptReference(ScriptValidatorAttachmentIntent intent, ScriptRegistry registry) {
            if (!intent.hasScriptReference()) {
                return;
            }
            if (intent.hasScriptRef() && intent.hasScriptHash()) {
                throw new TxBuildException("ValidatorAttachment requires only one of script_ref or script_hash");
            }

            Script script = resolveScriptReference(intent.getScriptRef(), intent.getScriptHash(), registry);
            if (!(script instanceof PlutusScript)) {
                throw new TxBuildException("Resolved validator script reference is not a PlutusScript");
            }

            intent.resolveScript((PlutusScript) script);
        }

        private void resolveNativeScriptReference(NativeScriptAttachmentIntent intent, ScriptRegistry registry) {
            if (!intent.hasScriptReference()) {
                return;
            }
            if (intent.hasScriptRef() && intent.hasScriptHash()) {
                throw new TxBuildException("NativeScriptAttachment requires only one of script_ref or script_hash");
            }

            Script script = resolveScriptReference(intent.getScriptRef(), intent.getScriptHash(), registry);
            if (!(script instanceof NativeScript)) {
                throw new TxBuildException("Resolved native script reference is not a NativeScript");
            }

            intent.resolveScript((NativeScript) script);
        }

        private Script resolveScriptReference(String scriptRef, String scriptHash, ScriptRegistry registry) {
            if (scriptRef != null) {
                String resolvedRef = scriptRef.trim();
                if (resolvedRef.isEmpty()) {
                    throw new TxBuildException("script_ref cannot be blank");
                }

                return registry.resolve(resolvedRef)
                        .orElseThrow(() -> new TxBuildException("Unable to resolve script_ref: " + resolvedRef));
            }

            String resolvedHash = normalizeScriptHash(scriptHash);
            Script script = registry.resolveByHash(resolvedHash)
                    .orElseThrow(() -> new TxBuildException("Unable to resolve script_hash: " + resolvedHash));
            verifyScriptHash(resolvedHash, script);
            return script;
        }

        private String normalizeScriptHash(String scriptHash) {
            if (scriptHash == null || scriptHash.isBlank()) {
                throw new TxBuildException("script_hash cannot be blank");
            }

            String normalized = scriptHash.trim().toLowerCase(Locale.ROOT);
            try {
                byte[] hashBytes = HexUtil.decodeHexString(normalized);
                if (hashBytes.length != 28) {
                    throw new TxBuildException("script_hash must be 28 bytes");
                }
            } catch (Exception e) {
                if (e instanceof TxBuildException) {
                    throw (TxBuildException) e;
                }
                throw new TxBuildException("script_hash must be hex encoded", e);
            }

            return normalized;
        }

        private void verifyScriptHash(String expectedHash, Script script) {
            try {
                String actualHash = script.getPolicyId();
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    throw new TxBuildException("Resolved script hash mismatch. Expected: " + expectedHash + ", actual: " + actualHash);
                }
            } catch (TxBuildException e) {
                throw e;
            } catch (Exception e) {
                throw new TxBuildException("Unable to calculate resolved script hash", e);
            }
        }

        private boolean hasPolicyRefs(AbstractTx tx) {
            for (var intent : tx.getIntentions()) {
                if (intent instanceof MintingIntent && ((MintingIntent) intent).getPolicyRef() != null) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasScriptReferences(AbstractTx tx) {
            for (var intent : tx.getIntentions()) {
                if (intent instanceof ScriptValidatorAttachmentIntent) {
                    ScriptValidatorAttachmentIntent validatorIntent = (ScriptValidatorAttachmentIntent) intent;
                    if (validatorIntent.getScriptRef() != null || validatorIntent.getScriptHash() != null) {
                        return true;
                    }
                } else if (intent instanceof NativeScriptAttachmentIntent) {
                    NativeScriptAttachmentIntent nativeIntent = (NativeScriptAttachmentIntent) intent;
                    if (nativeIntent.getScriptRef() != null || nativeIntent.getScriptHash() != null) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean hasScriptReferences() {
            for (AbstractTx tx : txList) {
                if (hasScriptReferences(tx)) {
                    return true;
                }
            }
            return false;
        }

        private void resolveSignerRef(SignerRegistry registry, String ref, String scope, Set<String> resolvedSignerRefs) {
            var bindingOpt = registry.resolve(ref);
            if (bindingOpt.isEmpty())
                throw new TxBuildException("Unable to resolve signer ref: " + ref);

            addSignerForBinding(ref, scope, bindingOpt.get(), resolvedSignerRefs);
        }

        private void addSignerForBinding(String ref, String scope, SignerBinding binding, Set<String> resolvedSignerRefs) {
            String signerKey = signerKey(ref, scope);
            if (!resolvedSignerRefs.add(signerKey)) {
                return;
            }

            try {
                this.withSigner(binding.signerFor(scope));
            } catch (Exception e) {
                throw new TxBuildException("Failed to create signer for ref: " + ref + ", scope: " + scope, e);
            }
        }

        private String signerKey(String ref, String scope) {
            String normalizedRef = ref == null ? "" : ref.trim();
            String normalizedScope = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
            return normalizedRef + "|" + normalizedScope;
        }

        private int getTotalSigners() {
            int totalSigners = signersCount;
            if (additionalSignerCount != 0)
                totalSigners += additionalSignerCount;

            return totalSigners;
        }

        private TxBuilder buildCollateralOutput(String payingAddress) {
            if (collateralInputs != null && !collateralInputs.isEmpty()) {
                List<Utxo> collateralUtxos = collateralInputs.stream()
                        .map(input -> utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex()))
                        .map(optionalUtxo -> optionalUtxo.get())
                        .collect(Collectors.toList());
                return CollateralBuilders.collateralOutputs(payingAddress, List.copyOf(collateralUtxos));
            } else {
                UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
                Set<Utxo> collateralUtxos = utxoSelectionStrategy.select(payingAddress, DEFAULT_COLLATERAL_AMT, null);
                if (collateralUtxos.size() > MAX_COLLATERAL_INPUTS) {
                    utxoSelectionStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
                    collateralUtxos = utxoSelectionStrategy.select(payingAddress, DEFAULT_COLLATERAL_AMT, null);
                }

                return CollateralBuilders.collateralOutputs(payingAddress, List.copyOf(collateralUtxos));
            }
        }

        private TxBuilder buildCollateralOutput(Wallet payingWallet) {
            String collateralPayerAddress = payingWallet.getBaseAddressString(0); //TODO: first addr as collateral output addr

            if (collateralInputs != null && !collateralInputs.isEmpty()) {
                List<Utxo> collateralUtxos = collateralInputs.stream()
                        .map(input -> utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex()))
                        .map(optionalUtxo -> optionalUtxo.get())
                        .collect(Collectors.toList());
                return CollateralBuilders.collateralOutputs(collateralPayerAddress, List.copyOf(collateralUtxos));
            } else {
                UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
                var hdWalletAddressIterator = new HDWalletAddressIterator(payingWallet, utxoSupplier);

                List<Utxo> collateralUtxos = utxoSelectionStrategy.selectUtxos(hdWalletAddressIterator, List.of(DEFAULT_COLLATERAL_AMT), null);
                if (collateralUtxos.size() > MAX_COLLATERAL_INPUTS) {
                    utxoSelectionStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
                    collateralUtxos = utxoSelectionStrategy.selectUtxos(hdWalletAddressIterator, List.of(DEFAULT_COLLATERAL_AMT), null);
                }

                return CollateralBuilders.collateralOutputs(collateralPayerAddress, List.copyOf(collateralUtxos));
            }
        }

        private TxBuilder addRequiredSignersBuilder() {
            return ((context, txn) -> {
                List<byte[]> txRequiredSigners = txn.getBody().getRequiredSigners();
                if (txRequiredSigners == null) {
                    txRequiredSigners = new ArrayList<>();
                    txn.getBody().setRequiredSigners(txRequiredSigners);
                }
                txRequiredSigners.addAll(requiredSigners);
            });
        }

        /**
         * Build, sign and submit transaction
         *
         * @return Result of transaction submission
         */
        public TxResult complete() {
            if (txList.length == 0)
                throw new TxBuildException("At least one tx is required");

//            boolean txListContainsWallet = Arrays.stream(txList).anyMatch(abstractTx -> abstractTx.getFromWallet() != null);
//            if(txListContainsWallet && !(utxoSupplier instanceof WalletUtxoSupplier))
//                throw new TxBuildException("Provide a WalletUtxoSupplier when using a sender wallet");

            Transaction transaction = buildAndSign();

            if (txInspector != null)
                txInspector.accept(transaction);

            if (txVerifier != null)
                txVerifier.verify(transaction);

            try {
                Result<String> result = transactionProcessor.submitTransaction(transaction.serialize());
                if (!result.isSuccessful()) {
                    log.error("Transaction : " + transaction);
                }
                return TxResult.fromResult(result).withTxStatus(TxStatus.SUBMITTED);
            } catch (Exception e) {
                throw new ApiRuntimeException(e);
            }
        }

        /**
         * Build, sign and submit transaction and wait for the transaction to be included in the block.
         * Default timeout is 60 seconds.
         *
         * @return Result of transaction submission
         */
        public TxResult completeAndWait() {
            return completeAndWait(Duration.ofSeconds(60), (msg) -> log.info(msg));
        }

        /**
         * Build, sign and submit transaction and wait for the transaction to be included in the block.
         * Default timeout is 60 seconds.
         *
         * @param logConsumer consumer to get log messages
         * @return Result of transaction submission
         */
        public TxResult completeAndWait(Consumer<String> logConsumer) {
            return completeAndWait(Duration.ofSeconds(60), logConsumer);
        }

        /**
         * Build, sign and submit transaction and wait for the transaction to be included in the block.
         *
         * @param timeout Timeout to wait for transaction to be included in the block
         * @return Result of transaction submission
         */
        public TxResult completeAndWait(Duration timeout) {
            return completeAndWait(timeout, Duration.ofSeconds(2), (msg) -> log.info(msg));
        }

        /**
         * Build, sign and submit transaction and wait for the transaction to be included in the block.
         * @param timeout Timeout to wait for transaction to be included in the block
         * @param logConsumer consumer to get log messages
         * @return Result of transaction submission
         */
        public TxResult completeAndWait(Duration timeout, Consumer<String> logConsumer) {
            return completeAndWait(timeout, Duration.ofSeconds(2), logConsumer);
        }

        /**
         * Build, sign and submit transaction and wait for the transaction to be included in the block.
         * @param timeout Timeout to wait for transaction to be included in the block
         * @param checkInterval Interval sec to check if transaction is included in the block
         * @param logConsumer consumer to get log messages
         * @return Result of transaction submission
         */
        public TxResult completeAndWait(@NonNull Duration timeout, @NonNull Duration checkInterval,
                                              @NonNull Consumer<String> logConsumer) {
            Result<String> result = complete();
            var txResult = TxResult.fromResult(result);
            if (!result.isSuccessful())
                return txResult.withTxStatus(TxStatus.FAILED);

            Instant startInstant = Instant.now();
            long millisToTimeout = timeout.toMillis();

            logConsumer.accept(showStatus(TxStatus.SUBMITTED, result.getValue()));
            String txHash = result.getValue();
            try {
                if (result.isSuccessful()) { //Wait for transaction to be included in the block
                    int count = 0;
                    while (count < 60) {
                        Optional<Utxo> utxoOptional = utxoSupplier.getTxOutput(txHash, 0);
                        if (utxoOptional.isPresent()) {
                            logConsumer.accept(showStatus(TxStatus.CONFIRMED, txHash));
                            return txResult.withTxStatus(TxStatus.CONFIRMED);
                        }

                        logConsumer.accept(showStatus(TxStatus.PENDING, txHash));
                        Instant now = Instant.now();
                        if (now.isAfter(startInstant.plusMillis(millisToTimeout))) {
                            logConsumer.accept(showStatus(TxStatus.TIMEOUT, txHash));
                            return txResult.withTxStatus(TxStatus.TIMEOUT);
                        }

                        Thread.sleep(checkInterval.toMillis());
                    }
                }
            } catch (Exception e) {
                log.error("Error while waiting for transaction to be included in the block. TxHash : " + txHash, e);
                logConsumer.accept("Error while waiting for transaction to be included in the block. TxHash : " + txHash);
            }

            logConsumer.accept(showStatus(TxStatus.PENDING, txHash));
            return txResult.withTxStatus(TxStatus.PENDING);
        }

        /**
         * Completes the task and waits asynchronously with a specified timeout duration and a logging function.
         *
         * @return a CompletableFuture containing the result of the completion task.
         */
        public CompletableFuture<TxResult> completeAndWaitAsync() {
            return completeAndWaitAsync(Duration.ofSeconds(2), (msg) -> log.info(msg));
        }

        /**
         * Submit the transaction and return a CompletableFuture containing a Result that wraps a txHash if the operation is successful.
         *
         * @param logConsumer a consumer that processes log messages. It must not be null.
         * @return a CompletableFuture containing a Result that wraps txHash if the operation is successful.
         */
        public CompletableFuture<TxResult> completeAndWaitAsync(@NonNull Consumer<String> logConsumer) {
            return completeAndWaitAsync(Duration.ofSeconds(2), logConsumer);
        }

        /**
         * Submit the transaction and return a CompletableFuture containing a Result that wraps a txHash if the operation is successful.
         *
         * @param logConsumer a consumer that processes log messages. It must not be null.
         * @param executor the executor to use for asynchronous execution. It must not be null.
         * @return a CompletableFuture containing a Result that wraps txHash if the operation is successful.
         */
        public CompletableFuture<TxResult> completeAndWaitAsync(@NonNull Consumer<String> logConsumer, @NonNull Executor executor) {
            return completeAndWaitAsync(Duration.ofSeconds(2), logConsumer, executor);
        }

        /**
         * Submit the transaction and return a CompletableFuture containing a Result that wraps a txHash if the operation is successful.
         *
         * @param checkInterval the interval to check if the transaction is included in the block. It must not be null.
         * @param logConsumer a consumer that processes log messages. It must not be null.
         * @return a CompletableFuture containing a Result that wraps txHash if the operation is successful.
         */
        public CompletableFuture<TxResult> completeAndWaitAsync(@NonNull Duration checkInterval,
                                                                      @NonNull Consumer<String> logConsumer) {
            return completeAndWaitAsync(checkInterval, logConsumer, null);
        }

        /**
         * Submit the transaction and return a CompletableFuture containing a Result that wraps a txHash if the operation is successful.
         *
         * @param checkInterval the interval to check if the transaction is included in the block. It must not be null.
         * @param logConsumer a consumer that processes log messages. It must not be null.
         * @param executor the executor to use for asynchronous execution. It must not be null.
         * @return a CompletableFuture containing a Result that wraps txHash if the operation is successful.
         */
        public CompletableFuture<TxResult> completeAndWaitAsync(@NonNull Duration checkInterval,
                                                                      @NonNull Consumer<String> logConsumer, Executor executor) {
            if (executor != null) {
                return CompletableFuture.supplyAsync(() -> waitForTxResult(checkInterval, logConsumer), executor);
            } else {
                return CompletableFuture.supplyAsync(() -> waitForTxResult(checkInterval, logConsumer));
            }
        }

        private TxResult waitForTxResult(Duration checkInterval, Consumer<String> logConsumer) {
            Result<String> result = complete();
            var txResult = TxResult.fromResult(result);
            if (!result.isSuccessful()) {
                return txResult.withTxStatus(TxStatus.FAILED);
            }

            logConsumer.accept(showStatus(TxStatus.SUBMITTED, result.getValue()));
            String txHash = result.getValue();
            try {
                if (result.isSuccessful()) { //Wait for transaction to be included in the block
                    while (true) {
                        Optional<Utxo> utxoOptional = utxoSupplier.getTxOutput(txHash, 0);
                        if (utxoOptional.isPresent()) {
                            logConsumer.accept(showStatus(TxStatus.CONFIRMED, txHash));
                            return txResult.withTxStatus(TxStatus.CONFIRMED);
                        }

                        Thread.sleep(checkInterval.toMillis());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Error while waiting for transaction to be included in the block. TxHash : " + txHash, e);
                logConsumer.accept("Error while waiting for transaction to be included in the block. TxHash : " + txHash);
            }

            logConsumer.accept(showStatus(TxStatus.PENDING, txHash));
            return txResult.withTxStatus(TxStatus.PENDING);
        }


        private String showStatus(TxStatus status, String txHash) {
            return String.format("[%s] Tx: %s", status, txHash);
        }

        /**
         * Sign transaction with the given signer
         * @param signer
         * @return TxContext
         */
        public TxContext withSigner(@NonNull TxSigner signer) {
            signersCount++;

            if (this.signers == null)
                this.signers = signer;
            else
                this.signers = this.signers.andThen(signer);
            return this;
        }

        /**
         * Add validity start slot to the transaction. This value is set in "validity start from" field of the transaction.
         * @param slot validity start slot
         * @return TxContext
         */
        public TxContext validFrom(long slot) {
            this.validFrom = slot;
            return this;
        }

        /**
         * Add validity end slot to the transaction. This value is set in ttl field of the transaction.
         * @param slot validity end slot
         * @return TxContext
         */
        public TxContext validTo(long slot) {
            this.validTo = slot;
            return this;
        }

        /**
         * Define if outputs with the same address should be merged into one output.
         * Default is false
         *
         * @param merge
         * @return TxContext
         */
        public TxContext mergeOutputs(boolean merge) {
            this.mergeOutputs = merge;
            return this;
        }

        /**
         * Evaluate script cost for the transaction with the given evaluator
         * @param txEvaluator
         * @return TxContext
         */
        public TxContext withTxEvaluator(TransactionEvaluator txEvaluator) {
            this.txnEvaluator = txEvaluator;
            return this;
        }

        /**
         * Inspect transaction before submitting
         * @param txInspector
         * @return TxContext
         */
        public TxContext withTxInspector(Consumer<Transaction> txInspector) {
            QuickTxBuilder.this.txInspector = txInspector;
            return this;
        }

        /**
         * Use the given {@link UtxoSelectionStrategy} for selecting utxos
         * @param utxoSelectionStrategy UtxoSelectionStrategy
         * @return TxContext
         */
        public TxContext withUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy) {
            this.utxoSelectionStrategy = utxoSelectionStrategy;
            return this;
        }

        /**
         * Use the given {@link ScriptSupplier} to get script for the transaction
         * For example: To calculate tier reference script fee when reference scripts are used in the transaction, script supplier can be used
         * to get the reference scripts through the UtxoSupplier.
         * <p>
         * QuickTx also uses this supplier as a hash-only fallback for script attachment
         * intents that use script_hash. Logical script_ref values still require
         * {@link #withScriptRegistry(ScriptRegistry)}.
         *
         * @param scriptSupplier
         * @return
         */
        public TxContext withScriptSupplier(ScriptSupplier scriptSupplier) {
           this.scriptSupplier = scriptSupplier;
           return this;
        }

        /**
         * Verify the transaction with the given verifier before submitting
         * @param txVerifier TxVerifier
         * @return TxContext
         */
        public TxContext withVerifier(Verifier txVerifier) {
            if (this.txVerifier == null)
                this.txVerifier = txVerifier;
            else
                this.txVerifier = this.txVerifier.andThen(txVerifier);
            return this;
        }

        /**
         * Add address's payment or stake credential hash to the required signer list.
         * Add payment credential hash if address has a payment part (Base address, Enterprise address etc.),
         * otherwise add stake credential hash if exists (Stake address).
         *
         * @param addresses Address or list of address to add to the required signer list
         * @return TxContext
         */
        public TxContext withRequiredSigners(Address... addresses) {
           if (addresses == null || addresses.length == 0)
                throw new TxBuildException("Address is required");

            if (this.requiredSigners == null)
                this.requiredSigners = new HashSet<>();

            for (Address address : addresses) {
                if (address.getPaymentCredential().isPresent()) {
                    address.getPaymentCredential()
                            .map(credential -> this.requiredSigners.add(credential.getBytes()))
                            .orElseThrow(() -> new TxBuildException("Address is not a payment address : " + address));
                } else if (address.getDelegationCredential().isPresent()) {
                    address.getDelegationCredential()
                            .map(credential -> this.requiredSigners.add(credential.getBytes()))
                            .orElseThrow(() -> new TxBuildException("Address is not a stake address : " + address));
                } else
                    throw new TxBuildException("Address is not a payment or stake address");
            }

            return this;
        }

        /**
         * Add credential hash to the required signer list
         * @param credentials
         * @return TxContext
         */
        public TxContext withRequiredSigners(byte[]... credentials) {
            if (credentials == null || credentials.length == 0)
                throw new TxBuildException("Credential is required");

            if (this.requiredSigners == null)
                this.requiredSigners = new HashSet<>();

            for (byte[] credential : credentials) {
                this.requiredSigners.add(credential);
            }
            return this;
        }

        /**
         * Add specific inputs as collateral to the transaction. If set, the builder will not select collateral inputs.
         * The given inputs will be used as collateral inputs and not be included during coin selection.
         * @param inputs
         * @return TxContext
         */
        public TxContext withCollateralInputs(TransactionInput... inputs) {
            if (inputs == null || inputs.length == 0)
                throw new TxBuildException("Collateral inputs can't be null or empty");

            if (this.collateralInputs == null)
                this.collateralInputs = new HashSet<>();

            for (TransactionInput collateralInput : inputs) {
                collateralInputs.add(collateralInput);
            }

            return this;
        }

        /**
         * Set this flag to decide if the builder should throw an exception if the script cost evaluation fails during transaction building.
         *
         * <p>
         * If this flag is true, the builder will not throw an exception if the script cost evaluation fails and continue
         * building the transaction or submit the transaction.
         * </p>
         *
         * <p>
         * If set to false, the builder will throw an exception if the script cost evaluation fails and stop building the transaction.
         * </p>
         *
         * Default is false
         *
         * @param flag
         * @return TxContext
         */
        public TxContext ignoreScriptCostEvaluationError(boolean flag) {
            this.ignoreScriptCostEvaluationError = flag;
            return this;
        }

        /**
         * Set the serialization era for the transaction.
         *
         * @param era The serialization era to set. By default, Conway Era format is used for serialization.
         * @return The TxContext object.
         */
        public TxContext withSerializationEra(Era era) {
            this.serializationEra = era;
            return this;
        }

        /**
         * Set scripts used in reference inputs. From Conway era, reference script's size is also used during fee calculation.
         * These scripts are not part of the transaction but used only in fee calculation.
         *
         * <p>
         * If reference scripts are not set, the fee calculation may not be accurate.
         * </p>
         * @param scripts
         * @return TxContext
         */
        public TxContext withReferenceScripts(PlutusScript... scripts) {
            if (scripts == null || scripts.length == 0)
                throw new TxBuildException("Reference scripts can't be null or empty");

            if (referenceScripts == null)
                referenceScripts = new ArrayList<>();

            referenceScripts.addAll(Arrays.asList(scripts));
            return this;
        }

        /**
         * Set whether to remove duplicate script witnesses from the transaction. Default is false.
         * If set to true, the builder will remove duplicate script witnesses from the transaction if the same script ref is there
         * in inputs or reference inputs. This is to avoid ExtraneousScriptWitnessesUTXOW error.
         *
         * @param remove boolean flag indicating whether to remove duplicate script witnesses
         * @return TxContext the current TxContext instance.
         */
        public TxContext removeDuplicateScriptWitnesses(boolean remove) {
            this.removeDuplicateScriptWitnesses = remove;
            return this;
        }

        /**
         * Enables UTXO search by address verification hash (addr_vkh).
         * Configures the internal {@link UtxoSupplier} to search using the address verification hash.
         *
         * By default, searching by address verification hash is disabled.
         * <p></p>
         * If the {@link UtxoSupplier} relies on {@link UtxoService} to provide UTXOs
         * and the {@link UtxoService} does not support searching by address verification hash,
         * the search will fail.
         *
         * @param flag a boolean indicating whether to enable or disable searching UTXOs by address vkh.
         *
         * @return TxContext the current TxContext instance
         */
        public TxContext withSearchUtxoByAddressVkh(boolean flag) {
            this.searchUtxoByAddressVkh = flag;
            return this;
        }

        /**
         * TxBuilder to set start validity interval and ttl for the transaction
         * @param txBuilder TxBuilder
         * @return TxBuilder
         */
        private TxBuilder buildValidityIntervalTxBuilder(TxBuilder txBuilder) {
            //Add validity interval
            if (validFrom != 0 || validTo != 0) {
                return txBuilder.andThen((context, txn) -> {
                    if (validFrom != 0)
                        txn.getBody().setValidityStartInterval(validFrom);
                    if (validTo != 0)
                        txn.getBody().setTtl(validTo);
                });
            } else
                return txBuilder;
        }
    }

}
