package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultTransactionProcessor;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.function.*;
import com.bloxbean.cardano.client.function.helper.AuxDataProviders;
import com.bloxbean.cardano.client.function.helper.BalanceTxBuilders;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * QuickTxBuilder is a utility class to build and submit transactions quickly. It provides high level APIs to build
 * transactions with minimal configuration. Internally it uses composable functions to build transactions.
 */
@Slf4j
public class QuickTxBuilder {
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    private TransactionProcessor transactionProcessor;

    private TxBuilder txBuilder;
    private TxOutputBuilder txOutputBuilder;
    private TxSigner txSigner;
    private String sender;
    private Account senderAccount;

    private TxBuilder preBalanceTrasformer;
    private TxBuilder postBalanceTrasformer;

    private QuickTxBuilder(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier,
                           TransactionProcessor transactionProcessor) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.transactionProcessor = transactionProcessor;

        txBuilder = (context, txn) -> {};
        txOutputBuilder = (context, txn) -> {};
    }

    public static QuickTxBuilder newTx(BackendService backendService) {
        return QuickTxBuilder.newTx(new DefaultUtxoSupplier(backendService.getUtxoService()),
                new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                new DefaultTransactionProcessor(backendService.getTransactionService()));
    }

    public static QuickTxBuilder newTx(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier,
                                       TransactionProcessor transactionProcessor) {
        return new QuickTxBuilder(utxoSupplier, protocolParamsSupplier, transactionProcessor);
    }

    public QuickTxBuilder withSender(String sender) {
        if (senderAccount != null)
            throw new IllegalStateException("Sender and senderAccount cannot be set at the same time");

        this.txBuilder = txOutputBuilder.buildInputs(InputBuilders
                .createFromSender(sender, sender));

        this.sender = sender;
        return this;
    }

    public QuickTxBuilder withSender(Account account) {
        if (sender != null)
            throw new IllegalStateException("Sender and senderAccount cannot be set at the same time");

        this.txBuilder = txBuilder.andThen(txOutputBuilder.buildInputs(InputBuilders
                .createFromSender(account.baseAddress(), account.baseAddress())));
        this.senderAccount = account;
        return this;
    }

    public QuickTxBuilder withSigner(TxSigner signer) {
        this.txSigner = signer;
        return this;
    }

    public QuickTxBuilder payToAddress(String address, Amount amount) {
        return payToAddress(address, List.of(amount));
    }

    public QuickTxBuilder payToAddress(String address, List<Amount> amounts) {
        for (Amount amount: amounts) {
            String unit = amount.getUnit();
            Output output;
            if (unit.equals(LOVELACE)) {
                output = Output.builder()
                        .address(address)
                        .assetName(LOVELACE)
                        .qty(amount.getQuantity())
                        .build();
            } else {
                Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(unit);
                output = Output.builder()
                        .address(address)
                        .policyId(policyAssetName._1)
                        .assetName(policyAssetName._2)
                        .qty(amount.getQuantity())
                        .build();
            }

            txOutputBuilder = txOutputBuilder.and(output.outputBuilder());
        }

        return this;
    }

    public QuickTxBuilder attachMetadata(Metadata metadata) {
        this.txBuilder = this.txBuilder.andThen(AuxDataProviders.metadataProvider(metadata));
        return this;
    }

    public QuickTxBuilder preBalance(TxBuilder txBuilder) {
        this.preBalanceTrasformer = txBuilder;
        return this;
    }

    public QuickTxBuilder postBalance(TxBuilder txBuilder) {
        this.postBalanceTrasformer = txBuilder;
        return this;
    }

    public Transaction build() {
        String senderAddress = sender;
        if (senderAccount != null)
            senderAddress = senderAccount.baseAddress();

        TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);

        if (preBalanceTrasformer != null)
            txBuilder = txBuilder.andThen(preBalanceTrasformer);

        txBuilder = txBuilder.andThen(BalanceTxBuilders.balanceTx(senderAddress));

        if (postBalanceTrasformer != null)
            txBuilder = txBuilder.andThen(postBalanceTrasformer);

        return txBuilderContext.build(txBuilder);
    }

    public Result<String> complete() {
        Transaction transaction = build();
        if (senderAccount != null)
            transaction = SignerProviders.signerFrom(senderAccount).sign(transaction);
        else if (txSigner != null)
            transaction = txSigner.sign(transaction);

        try {
            return transactionProcessor.submitTransaction(transaction.serialize());
        } catch (Exception e) {
            throw new ApiRuntimeException(e);
        }
    }
}
