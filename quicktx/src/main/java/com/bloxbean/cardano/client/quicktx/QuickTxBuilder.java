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
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.BalanceTxBuilders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import lombok.extern.slf4j.Slf4j;

/**
 * QuickTxBuilder is a utility class to build and submit transactions quickly. It provides high level APIs to build
 * transactions with minimal configuration. Internally it uses composable functions to build transactions.
 */
@Slf4j
public class QuickTxBuilder {
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    private TransactionProcessor transactionProcessor;

    private TxBuilder txBuilder = (context, txn) -> {};

    private String balanceChangeAddress;
    private TxBuilder preBalanceTrasformer;
    private TxBuilder postBalanceTrasformer;

    public QuickTxBuilder(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier,
                           TransactionProcessor transactionProcessor) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.transactionProcessor = transactionProcessor;
    }

    public static  QuickTxBuilder create(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier,
                          TransactionProcessor transactionProcessor) {
        return new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
    }

    public static QuickTxBuilder create(BackendService backendService) {
        return new QuickTxBuilder(new DefaultUtxoSupplier(backendService.getUtxoService()),
                new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                new DefaultTransactionProcessor(backendService.getTransactionService()));
    }

    public Tx newTx() {
        return new Tx();
    }

    public QuickTxBuilder balanceChangeAddress(String address) {
        this.balanceChangeAddress = address;
        return this;
    }

    public QuickTxBuilder preBalanceTx(TxBuilder txBuilder) {
        this.preBalanceTrasformer = txBuilder;
        return this;
    }

    public QuickTxBuilder postBalanceTx(TxBuilder txBuilder) {
        this.postBalanceTrasformer = txBuilder;
        return this;
    }

    public Transaction build(Tx... txList) {
        int totalSigners = 0;
        for(Tx tx: txList) {
            this.txBuilder = this.txBuilder.andThen(tx.txBuilder());
            totalSigners += tx.additionalSigner();
        }

        TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);

        if (preBalanceTrasformer != null)
            txBuilder = txBuilder.andThen(preBalanceTrasformer);

        if (balanceChangeAddress == null) {
            if (txList.length == 1)
                balanceChangeAddress = txList[0].sender();
            else
                throw new TxBuildException("Balance change address is not set. " +
                        "It's mandatory when there are more than one txs");
        }

        txBuilder = txBuilder.andThen(BalanceTxBuilders.balanceTxWithAdditionalSigners(balanceChangeAddress, totalSigners));
        if (postBalanceTrasformer != null)
            txBuilder = txBuilder.andThen(postBalanceTrasformer);

        return txBuilderContext.build(txBuilder);
    }

    public Transaction buildAndSign(Tx... txList) {
        Transaction transaction = build(txList);
        for (Tx tx : txList) {
            if (tx.txSigner() != null) {
                transaction = tx.txSigner().sign(transaction);
            }
        }

        return transaction;
    }

    public Result<String> complete(Tx... txList) {
        if (txList.length == 0)
            throw new TxBuildException("At least one tx is required");

        Transaction transaction = buildAndSign(txList);
        try {
            return transactionProcessor.submitTransaction(transaction.serialize());
        } catch (Exception e) {
            throw new ApiRuntimeException(e);
        }
    }
}
