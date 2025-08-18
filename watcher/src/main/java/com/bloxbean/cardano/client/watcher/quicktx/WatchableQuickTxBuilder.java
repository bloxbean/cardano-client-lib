package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import com.bloxbean.cardano.client.watcher.api.WatchTxEvaluator;
import com.bloxbean.cardano.client.watcher.chain.Watcher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Wrapper around QuickTxBuilder that produces watchable transaction contexts.
 *
 * This builder allows creating transaction contexts that can be watched for
 * completion and chained together with UTXO dependencies between steps.
 *
 * Usage:
 * <pre>
 * WatchableQuickTxBuilder builder = new WatchableQuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
 *
 * // Single transaction
 * WatchHandle handle = builder.compose(tx1, tx2)
 *     .withSigner(signer)
 *     .feePayer(address)
 *     .watch();
 *
 * // Chain with dependencies
 * var step1 = builder.compose(depositTx).withStepId("deposit").watchable();
 * var step2 = builder.compose(withdrawTx).fromStep("deposit").withStepId("withdraw").watchable();
 *
 * Watcher.build("chain")
 *     .step(step1)
 *     .step(step2)
 *     .watch();
 * </pre>
 */
public class WatchableQuickTxBuilder {

    private final QuickTxBuilder delegate;

    // Store references to services for creating new QuickTxBuilder instances with different UtxoSuppliers
    private final BackendService backendService;
    private final UtxoSupplier originalUtxoSupplier;
    private final ProtocolParamsSupplier protocolParamsSupplier;
    private final TransactionProcessor transactionProcessor;

    /**
     * Create WatchableQuickTxBuilder with supplier interfaces.
     *
     * @param utxoSupplier the UTXO supplier
     * @param protocolParamsSupplier the protocol parameters supplier
     * @param transactionProcessor the transaction processor
     */
    public WatchableQuickTxBuilder(UtxoSupplier utxoSupplier,
                                  ProtocolParamsSupplier protocolParamsSupplier,
                                  TransactionProcessor transactionProcessor) {
        this.backendService = null;
        this.originalUtxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.transactionProcessor = transactionProcessor;
        this.delegate = new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
    }

    /**
     * Create WatchableQuickTxBuilder from BackendService.
     *
     * @param backendService the backend service
     */
    public WatchableQuickTxBuilder(BackendService backendService) {
        this.backendService = backendService;
        this.originalUtxoSupplier = null;
        this.protocolParamsSupplier = null;
        this.transactionProcessor = null;
        this.delegate = new QuickTxBuilder(backendService);
    }

    /**
     * Create WatchableQuickTxBuilder with custom UTXO supplier.
     *
     * @param backendService the backend service
     * @param utxoSupplier custom UTXO supplier
     */
    public WatchableQuickTxBuilder(BackendService backendService, UtxoSupplier utxoSupplier) {
        this.backendService = backendService;
        this.originalUtxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = null;
        this.transactionProcessor = null;
        this.delegate = new QuickTxBuilder(backendService, utxoSupplier);
    }

    /**
     * Get the backend service if this builder was created with BackendService.
     *
     * @return the backend service or null if not created with BackendService
     */
    public BackendService getBackendService() {
        return backendService;
    }

    /**
     * Get the original UTXO supplier.
     *
     * @return the original UTXO supplier or null if created with BackendService only
     */
    public UtxoSupplier getOriginalUtxoSupplier() {
        return originalUtxoSupplier;
    }

    /**
     * Get the protocol parameters supplier.
     *
     * @return the protocol parameters supplier or null if created with BackendService
     */
    public ProtocolParamsSupplier getProtocolParamsSupplier() {
        return protocolParamsSupplier;
    }

    /**
     * Get the transaction processor.
     *
     * @return the transaction processor or null if created with BackendService
     */
    public TransactionProcessor getTransactionProcessor() {
        return transactionProcessor;
    }

    /**
     * Compose multiple AbstractTx objects into a watchable transaction context.
     *
     * @param txs the transactions to compose
     * @return watchable transaction context
     */
    public WatchableTxContext compose(AbstractTx... txs) {
        return new WatchableTxContext(txs);
    }

    /**
     * Watchable wrapper around TxContext that supports step identification
     * and UTXO dependencies between chain steps.
     */
    public class WatchableTxContext {
        private final QuickTxBuilder.TxContext delegate;
        private final AbstractTx[] originalTxs;
        private String stepId;
        private String description;
        private final List<StepOutputDependency> utxoDependencies;

        // Store configuration for recreating effective contexts
        private com.bloxbean.cardano.client.function.TxSigner storedSigner;
        private String storedFeePayer;
        private Consumer<Transaction> storedTxInspector;
        private WatchTxEvaluator watchTxEvaluator;

        /**
         * Create WatchableTxContext with the given transactions.
         *
         * @param txs the transactions to compose
         */
        WatchableTxContext(AbstractTx... txs) {
            this.delegate = WatchableQuickTxBuilder.this.delegate.compose(txs);
            this.originalTxs = txs.clone(); // Store original transactions
            this.utxoDependencies = new ArrayList<>();
        }

        /**
         * Set the step identifier for this transaction context.
         * This ID will be used to reference this step's outputs in chain dependencies.
         *
         * @param stepId the step identifier
         * @return this context for method chaining
         */
        public WatchableTxContext withStepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        /**
         * Set a human-readable description for this transaction context.
         *
         * @param description the description
         * @return this context for method chaining
         */
        public WatchableTxContext withDescription(String description) {
            this.description = description;
            return this;
        }

        // === UTXO Dependency Methods ===

        /**
         * Use all outputs from a previous step as inputs for this step.
         * Equivalent to calling collectFrom() with all UTXOs from the specified step.
         *
         * @param stepId the ID of the step whose outputs to use
         * @return this context for method chaining
         */
        public WatchableTxContext fromStep(String stepId) {
            this.addUtxoDependency(new StepOutputDependency(stepId, UtxoSelectionStrategy.ALL));
            return this;
        }

        /**
         * Use filtered outputs from a previous step as inputs.
         * Only UTXOs matching the condition will be used as inputs.
         *
         * @param stepId the ID of the step whose outputs to filter
         * @param condition the filtering condition
         * @return this context for method chaining
         */
        public WatchableTxContext fromStepWhere(String stepId, Predicate<Utxo> condition) {
            this.addUtxoDependency(new StepOutputDependency(stepId,
                new FilteredUtxoSelectionStrategy(condition)));
            return this;
        }

        /**
         * Use a specific UTXO from a previous step by index.
         * Index 0 is the first output of the transaction.
         *
         * @param stepId the ID of the step whose output to use
         * @param utxoIndex the index of the UTXO to use (0-based)
         * @return this context for method chaining
         */
        public WatchableTxContext fromStepUtxo(String stepId, int utxoIndex) {
            this.addUtxoDependency(new StepOutputDependency(stepId,
                new IndexedUtxoSelectionStrategy(utxoIndex)));
            return this;
        }

        /**
         * Add a UTXO dependency to this context.
         *
         * @param dependency the dependency to add
         */
        private void addUtxoDependency(StepOutputDependency dependency) {
            this.utxoDependencies.add(dependency);
        }

        /**
         * Get the step ID for this context.
         *
         * @return the step ID, or a generated UUID if not set
         */
        public String getStepId() {
            if (stepId == null) {
                stepId = UUID.randomUUID().toString();
            }
            return stepId;
        }

        /**
         * Get the description for this context.
         *
         * @return the description, or null if not set
         */
        public String getDescription() {
            return description;
        }

        /**
         * Get the UTXO dependencies for this context.
         *
         * @return the list of UTXO dependencies
         */
        public List<StepOutputDependency> getUtxoDependencies() {
            return new ArrayList<>(utxoDependencies);
        }

        // === Delegation methods to underlying TxContext ===

        /**
         * Get the underlying TxContext delegate.
         *
         * @return the TxContext delegate
         */
        public QuickTxBuilder.TxContext getDelegate() {
            return delegate;
        }

        /**
         * Get the original AbstractTx objects that were used to create this context.
         *
         * @return a copy of the original transactions
         */
        public AbstractTx[] getOriginalTxs() {
            return originalTxs.clone(); // Return defensive copy
        }

        /**
         * Get the parent WatchableQuickTxBuilder that created this context.
         *
         * @return the parent builder
         */
        public WatchableQuickTxBuilder getParentBuilder() {
            return WatchableQuickTxBuilder.this;
        }

        /**
         * Get the stored signer for recreating effective contexts.
         *
         * @return the stored signer
         */
        public com.bloxbean.cardano.client.function.TxSigner getStoredSigner() {
            return storedSigner;
        }

        /**
         * Get the stored fee payer for recreating effective contexts.
         *
         * @return the stored fee payer address
         */
        public String getStoredFeePayer() {
            return storedFeePayer;
        }

        /**
         * Set fee payer address. Delegates to underlying TxContext.
         *
         * @param address fee payer address
         * @return this context for method chaining
         */
        public WatchableTxContext feePayer(String address) {
            this.storedFeePayer = address;  // Store for later use
            delegate.feePayer(address);
            return this;
        }

        /**
         * Set fee payer wallet. Delegates to underlying TxContext.
         *
         * @param feePayerWallet the fee payer wallet
         * @return this context for method chaining
         */
        public WatchableTxContext feePayer(com.bloxbean.cardano.hdwallet.Wallet feePayerWallet) {
            delegate.feePayer(feePayerWallet);
            return this;
        }

        /**
         * Add signers. Delegates to underlying TxContext.
         *
         * @param signer the signer to add
         * @return this context for method chaining
         */
        public WatchableTxContext withSigner(com.bloxbean.cardano.client.function.TxSigner signer) {
            this.storedSigner = signer;  // Store for later use
            delegate.withSigner(signer);
            return this;
        }

        /**
         * Convert this context into a watchable step that can be used in chains.
         *
         * @return a watchable step
         */
        public WatchableStep watchable() {
            return new WatchableStep(this);
        }

        /**
         * Watch this transaction context as a single-step chain.
         * This is a convenience method for simple cases where you don't need
         * the full chain builder API.
         *
         * @return a watch handle for monitoring the transaction
         */
        public WatchHandle watch() {
            return Watcher.build("single-tx-" + System.currentTimeMillis())
                .step(this.watchable())
                .watch();
        }

        /**
         * Set transaction inspector callback. Delegates to underlying TxContext and stores for effective contexts.
         *
         * @param txInspector the transaction inspector callback
         * @return this context for method chaining
         */
        public WatchableTxContext withTxInspector(Consumer<Transaction> txInspector) {
            this.storedTxInspector = txInspector;  // Store for later use in effective contexts
            delegate.withTxInspector(txInspector);
            return this;
        }

        /**
         * Set transaction evaluator. Delegates to underlying TxContext.
         *
         * @param watchTxEvaluator watch transaction evaluator
         * @return this context for method chaining
         */
        public WatchableTxContext withTxEvaluator(WatchTxEvaluator watchTxEvaluator) {
            this.watchTxEvaluator = watchTxEvaluator;
            return this;
        }

        /**
         * Get the stored transaction inspector for recreating effective contexts.
         *
         * @return the stored transaction inspector
         */
        public Consumer<Transaction> getStoredTxInspector() {
            return storedTxInspector;
        }

        /**
         * Get the transaction evaluator for this context.
         *
         * @return the transaction evaluator, or null if not set
         */
        public WatchTxEvaluator getTxEvaluator() {
            return watchTxEvaluator;
        }
    }
}
