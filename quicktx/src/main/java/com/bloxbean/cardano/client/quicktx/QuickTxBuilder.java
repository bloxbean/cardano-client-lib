package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.BalanceTxBuilders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import lombok.extern.slf4j.Slf4j;

/**
 * QuickTxBuilder is a utility class to build and submit transactions quickly. It provides high level APIs to build
 * transactions with minimal configuration. Internally it uses composable functions to build transactions. Same instance of
 * QuickBuilder can be resued to build multiple transactions.
 * <br/>
 * <br/>
 * Example:
 * <pre>
 *   {@code QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
 *   Tx tx = new Tx()
 *              .payToAddress(receiver1, Amount.ada(1.5))
 *              .payToAddress(receiver2, Amount.ada(2.5))
 *              .attachMetadata(MessageMetadata.create().add("Hello"))
 *              .from(sender);
 *
 *    Result<String> result = quickTxBuilder.create(tx).complete();
 *    Result<String> result = txContext.complete();
 *    }
 * </pre>
 */
@Slf4j
public class QuickTxBuilder {
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    private TransactionProcessor transactionProcessor;

    /**
     * Create QuickTxBuilder
     * @param utxoSupplier utxo supplier
     * @param protocolParamsSupplier protocol params supplier
     * @param transactionProcessor transaction processor
     */
    public QuickTxBuilder(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier,
                           TransactionProcessor transactionProcessor) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.transactionProcessor = transactionProcessor;
    }

    /**
     * Create QuickTxBuilder with backend service
     * @param backendService backend service
     */
    public QuickTxBuilder(BackendService backendService) {
        this(new DefaultUtxoSupplier(backendService.getUtxoService()),
                new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                new DefaultTransactionProcessor(backendService.getTransactionService()));
    }

    /**
     * Create TxContext for the given txs
     * @param txs list of txs
     * @return TxContext which can be used to build and submit transaction
     */
    public TxContext create(Tx... txs) {
        if (txs == null || txs.length == 0)
            throw new TxBuildException("No txs provided to build transaction");
        return new TxContext(txs);
    }

    /**
     * TxContext is created for group of transactions which are to be submitted as a single transaction.
     */
    class TxContext {
        private Tx[] txList;
        private String feePayer;

        private TxBuilder preBalanceTrasformer;
        private TxBuilder postBalanceTrasformer;

        private int additionalSignerCount;
        private TxSigner additionalTxSigner;

        TxContext(Tx... txs) {
            this.txList = txs;
        }

        /**
         * Set fee payer address. When there is only one tx, sender's address is used as fee payer address.
         * When there are more than one txs, fee payer address is mandatory.
         * @param address fee payer address
         * @return TxContext
         */
        public TxContext feePayer(String address) {
            this.feePayer = address;
            return this;
        }

        /**
         * Set a TxBuilder function to transform the transaction before balance calculation.
         * This is useful when additional transformation logic is required before balance calculation.
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
         * @param txBuilder TxBuilder function
         * @return TxContext
         */
        public TxContext postBalanceTx(TxBuilder txBuilder) {
            this.postBalanceTrasformer = txBuilder;
            return this;
        }

        /**
         * Set additional signers count. This is useful when there are additional signers other than the default signers and
         * signer's set in individual Tx level. This helps to calculate correct size of the transaction and hence the fee.
         * @param additionalSigners
         * @return TxContext
         */
        public TxContext additionalSignersCount(int additionalSigners) {
            this.additionalSignerCount = additionalSigners;
            return this;
        }

        /**
         * Set additional signer. This is useful when there are additional signers other than the default signers and signer's
         * set in individual Tx level.
         * @param txSigner TxSigner
         * @return TxContext
         */
        public TxContext additionalSigner(TxSigner txSigner) {
            if (this.additionalTxSigner != null)
                this.additionalTxSigner = this.additionalTxSigner.andThen(txSigner);
            else
                this.additionalTxSigner = txSigner;
            return this;
        }

        /**
         * Build unsigned transaction
         * @return Transaction
         */
        public Transaction build() {
            TxBuilder txBuilder = (context, txn) -> {};
            for(Tx tx: txList) {
                if (tx.sender() == null)
                    throw new TxBuildException("Sender is not set for the tx : " + tx);

                txBuilder = txBuilder.andThen(tx.txBuilder());
            }

            int totalSigners = getTotalSigners();

            TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);

            if (preBalanceTrasformer != null)
                txBuilder = txBuilder.andThen(preBalanceTrasformer);

            if (feePayer == null) {
                if (txList.length == 1)
                    feePayer = txList[0].sender();
                else
                    throw new TxBuildException("Fee Payer address is not set. " +
                            "It's mandatory when there are more than one txs");
            }

            txBuilder = txBuilder.andThen(BalanceTxBuilders.balanceTxWithAdditionalSigners(feePayer, totalSigners));
            if (postBalanceTrasformer != null)
                txBuilder = txBuilder.andThen(postBalanceTrasformer);

            return txBuilderContext.build(txBuilder);
        }

        private int getTotalSigners() {
            int totalSigners = 0;
            for(Tx tx: txList) {
                totalSigners += tx.additionalSignersCount();
            }

            if(additionalSignerCount != 0)
                totalSigners += additionalSignerCount;

            return totalSigners;
        }

        /**
         * Build and sign transaction
         * @return Transaction
         */
        public Transaction buildAndSign() {
            Transaction transaction = build();
            for (Tx tx : txList) {
                if (tx.txSigner() != null) {
                    transaction = tx.txSigner().sign(transaction);
                }
            }

            return transaction;
        }

        /**
         * Build, sign and submit transaction
         * @return Result of transaction submission
         */
        public Result<String> complete() {
            if (txList.length == 0)
                throw new TxBuildException("At least one tx is required");

            Transaction transaction = buildAndSign();
            try {
                return transactionProcessor.submitTransaction(transaction.serialize());
            } catch (Exception e) {
                throw new ApiRuntimeException(e);
            }
        }
    }
}
