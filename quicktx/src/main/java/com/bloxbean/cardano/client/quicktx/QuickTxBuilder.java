package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.coinselection.impl.LargestFirstUtxoSelectionStrategy;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.CollateralBuilders;
import com.bloxbean.cardano.client.function.helper.OutputMergers;
import com.bloxbean.cardano.client.function.helper.ScriptCostEvaluators;
import com.bloxbean.cardano.client.quicktx.helpers.ScriptBalanceTxProviders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.JsonUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

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

    /**
     * Create QuickTxBuilder
     *
     * @param utxoSupplier           utxo supplier
     * @param protocolParamsSupplier protocol params supplier
     * @param transactionProcessor   transaction processor
     */
    public QuickTxBuilder(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier,
                          TransactionProcessor transactionProcessor) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.transactionProcessor = transactionProcessor;
    }

    /**
     * Create QuickTxBuilder with backend service
     *
     * @param backendService backend service
     */
    public QuickTxBuilder(BackendService backendService) {
        this(new DefaultUtxoSupplier(backendService.getUtxoService()),
                new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                new DefaultTransactionProcessor(backendService.getTransactionService())
        );
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
    class TxContext {
        private AbstractTx[] txList;
        private String feePayer;
        private String collateralPayer;

        private TxBuilder preBalanceTrasformer;
        private TxBuilder postBalanceTrasformer;

        private int additionalSignerCount = 0;
        private int signersCount = 0;
        private TxSigner signers;

        private boolean mergeChangeOutputs = true;

        private TransactionEvaluator txnEvaluator;

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
            TxBuilder txBuilder = (context, txn) -> {
            };
            boolean containsScriptTx = false;

            for (AbstractTx tx : txList) {
                tx.verifyData();
                if (tx.getChangeAddress() == null && tx instanceof ScriptTx)
                    ((ScriptTx)tx).withChangeAddress(feePayer);

                txBuilder = txBuilder.andThen(tx.complete());

                if (tx instanceof ScriptTx)
                    containsScriptTx = true;
            }

            int totalSigners = getTotalSigners();

            TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);
            //Set tx evaluator for script cost calculation
            if (txnEvaluator != null)
                txBuilderContext.withTxnEvaluator(txnEvaluator);
            else
                txBuilderContext.withTxnEvaluator(transactionProcessor);


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

            if (containsScriptTx) {
                if (collateralPayer == null)
                    collateralPayer = feePayer;
                txBuilder = txBuilder.andThen(buildCollateralOutput(collateralPayer));
            }

            if (containsScriptTx) {
                txBuilder = txBuilder.andThen(((context, transaction) -> {
                    try {
                        ScriptCostEvaluators.evaluateScriptCost().apply(context, transaction);
                    } catch (Exception e) {
                        //Ignore as it could happen due to insufficient ada in utxo
                    }
                }));
            }

            if (mergeChangeOutputs)
                txBuilder = txBuilder.andThen(OutputMergers.mergeOutputsForAddress(feePayer));

            //Balance outputs
            txBuilder = txBuilder.andThen(ScriptBalanceTxProviders.balanceTx(feePayer, totalSigners, containsScriptTx));

            if (postBalanceTrasformer != null)
                txBuilder = txBuilder.andThen(postBalanceTrasformer);


            //Call post balance function of each tx
            for (AbstractTx tx : txList) {
                txBuilder = txBuilder.andThen(((context, transaction) -> {
                    tx.postBalanceTx(transaction);
                }));
            }

            return txBuilderContext.build(txBuilder);
        }

        private int getTotalSigners() {
            int totalSigners = signersCount;
            if (additionalSignerCount != 0)
                totalSigners += additionalSignerCount;

            return totalSigners;
        }

        /**
         * Build and sign transaction
         *
         * @return Transaction
         */
        public Transaction buildAndSign() {
            Transaction transaction = build();
            if (signers != null)
                transaction = signers.sign(transaction);

            return transaction;
        }

        private TxBuilder buildCollateralOutput(String feePayer) {
            UtxoSelectionStrategy utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
            Set<Utxo> collateralUtxos = utxoSelectionStrategy.select(feePayer, DEFAULT_COLLATERAL_AMT, null);
            if (collateralUtxos.size() > MAX_COLLATERAL_INPUTS) {
                utxoSelectionStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);
                collateralUtxos = utxoSelectionStrategy.select(feePayer, DEFAULT_COLLATERAL_AMT, null);
            }

            return CollateralBuilders.collateralOutputs(feePayer, List.copyOf(collateralUtxos));
        }

        /**
         * Build, sign and submit transaction
         *
         * @return Result of transaction submission
         */
        public Result<String> complete() {
            if (txList.length == 0)
                throw new TxBuildException("At least one tx is required");

            Transaction transaction = buildAndSign();

            try {
                Result<String> result = transactionProcessor.submitTransaction(transaction.serialize());
                System.out.println("Transaction : " + JsonUtil.getPrettyJson(transaction));
                if (!result.isSuccessful()) {
                    log.error("Transaction : " + transaction);
                }
                return result;
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
        public Result<String> completeAndWait() {
            return completeAndWait(Duration.ofSeconds(60), (msg) -> log.info(msg));
        }

        public Result<String> completeAndWait(Consumer<String> logConsumer) {
            return completeAndWait(Duration.ofSeconds(60), logConsumer);
        }

        public Result<String> completeAndWait(Duration timeout) {
            return completeAndWait(timeout, (msg) -> log.info(msg));
        }

        /**
         * Build, sign and submit transaction and wait for the transaction to be included in the block.
         *
         * @param timeout timeout duration
         * @return Result of transaction submission
         */
        public Result<String> completeAndWait(Duration timeout, Consumer<String> logConsumer) {
            Result<String> result = complete();
            if (!result.isSuccessful())
                return result;

            logConsumer.accept("Transaction submitted. TxHash : " + result.getValue());
            String txHash = result.getValue();
            try {
                if (result.isSuccessful()) { //Wait for transaction to be included in the block
                    int count = 0;
                    while (count < 60) {
                        Optional<Utxo> utxoOptional = utxoSupplier.getTxOutput(txHash, 0);
                        if (utxoOptional.isPresent())
                            return result;

                        logConsumer.accept("Waiting for transaction to be included in the block. TxHash : " + txHash);
                        Thread.sleep(2000);
                    }
                }
            } catch (Exception e) {
                log.error("Error while waiting for transaction to be included in the block. TxHash : " + txHash, e);
                logConsumer.accept("Error while waiting for transaction to be included in the block. TxHash : " + txHash);
            }

            logConsumer.accept("Timeout while waiting for transaction to be included in the block. TxHash : " + txHash);
            return result;
        }

        public TxContext withSigner(@NonNull TxSigner signer) {
            signersCount++;

            if (this.signers == null)
                this.signers = signer;
            else
                this.signers = this.signers.andThen(signer);
            return this;
        }

        public TxContext mergeChangeOutputs(boolean merge) {
            this.mergeChangeOutputs = merge;
            return this;
        }

        public TxContext withTxEvaluator(TransactionEvaluator txEvaluator) {
            this.txnEvaluator = txEvaluator;
            return this;
        }
    }
}
