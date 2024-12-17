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
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.hdwallet.supplier.WalletUtxoSupplier;
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
     * TxContext is created for group of transactions which are to be submitted as a single transaction.
     */
    public class TxContext {
        private AbstractTx[] txList;
        private String feePayer;
        private String collateralPayer;
        private Set<byte[]> requiredSigners;
        private Set<TransactionInput> collateralInputs;

        private TxBuilder preBalanceTrasformer;
        private TxBuilder postBalanceTrasformer;

        private int additionalSignerCount = 0;
        private int signersCount = 0;
        private TxSigner signers;

        private long validFrom;
        private long validTo;

        private boolean mergeOutputs = true;

        private TransactionEvaluator txnEvaluator;
        private UtxoSelectionStrategy utxoSelectionStrategy;
        private ScriptSupplier scriptSupplier;
        private Verifier txVerifier;

        private List<PlutusScript> referenceScripts;

        private boolean ignoreScriptCostEvaluationError = true;
        private Era serializationEra;
        private boolean removeDuplicateScriptWitnesses = false;

        TxContext(AbstractTx... txs) {
            this.txList = txs;
        }

        /**
         * Set fee payer address. When there is only one tx, sender's address is used as fee payer address.
         * When there are more than one txs, fee payer address is mandatory.
         *
         * @param address fee payer address
         * @return TxContext
         */
        public TxContext feePayer(String address) {
            this.feePayer = address;
            return this;
        }

        public TxContext collateralPayer(String address) {
            this.collateralPayer = address;
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

                //For scriptTx, set fee payer as change address and from address by default.
                if (tx.getChangeAddress() == null && tx instanceof ScriptTx) {
                    ((ScriptTx) tx).withChangeAddress(feePayer);
                }
                if (tx.getFromAddress() == null && tx instanceof ScriptTx) {
                    ((ScriptTx) tx).from(feePayer);
                }

                txBuilder = txBuilder.andThen(tx.complete());

                if (tx instanceof ScriptTx)
                    containsScriptTx = true;

                hasMultiAssetMint = hasMultiAssetMint || tx.hasMultiAssetMinting();
            }

            int totalSigners = getTotalSigners();

            TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);
            if (backendScriptSupplier != null)
                txBuilderContext.setScriptSupplier(backendScriptSupplier);

            //Set merge outputs flag
            txBuilderContext.mergeOutputs(mergeOutputs);

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

            if (feePayer == null) {
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
                if (collateralPayer == null)
                    collateralPayer = feePayer;
                txBuilder = txBuilder.andThen(buildCollateralOutput(collateralPayer));
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

            //Balance outputs
            txBuilder = txBuilder.andThen(ScriptBalanceTxProviders.balanceTx(feePayer, totalSigners, containsScriptTx));

            if ((containsScriptTx || hasMultiAssetMint) && removeDuplicateScriptWitnesses) {
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

            return new Tuple<>(txBuilderContext, txBuilder);
        }

        private int getTotalSigners() {
            int totalSigners = signersCount;
            if (additionalSignerCount != 0)
                totalSigners += additionalSignerCount;

            return totalSigners;
        }

        private TxBuilder buildCollateralOutput(String feePayer) {
            if (collateralInputs != null && !collateralInputs.isEmpty()) {
                List<Utxo> collateralUtxos = collateralInputs.stream()
                        .map(input -> utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex()))
                        .map(optionalUtxo -> optionalUtxo.get())
                        .collect(Collectors.toList());
                return CollateralBuilders.collateralOutputs(feePayer, List.copyOf(collateralUtxos));
            } else {
                UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
                Set<Utxo> collateralUtxos = utxoSelectionStrategy.select(feePayer, DEFAULT_COLLATERAL_AMT, null);
                if (collateralUtxos.size() > MAX_COLLATERAL_INPUTS) {
                    utxoSelectionStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
                    collateralUtxos = utxoSelectionStrategy.select(feePayer, DEFAULT_COLLATERAL_AMT, null);
                }

                return CollateralBuilders.collateralOutputs(feePayer, List.copyOf(collateralUtxos));
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

            boolean txListContainsWallet = Arrays.stream(txList).anyMatch(abstractTx -> abstractTx.getFromWallet() != null);
            if(txListContainsWallet && !(utxoSupplier instanceof WalletUtxoSupplier))
                throw new TxBuildException("Provide a WalletUtxoSupplier when using a sender wallet");

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
         * Default is true
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
         * Default is true
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
