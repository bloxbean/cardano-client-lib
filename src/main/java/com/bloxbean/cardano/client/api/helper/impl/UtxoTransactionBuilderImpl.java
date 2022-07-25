package com.bloxbean.cardano.client.api.helper.impl;

import com.bloxbean.cardano.client.api.helper.UtxoTransactionBuilder;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
public class UtxoTransactionBuilderImpl implements UtxoTransactionBuilder {

    private UtxoSelectionStrategy utxoSelectionStrategy;

    /**
     * Create a {@link UtxoTransactionBuilder} with {@link DefaultUtxoSelectionStrategyImpl}
     *
     * @param utxoSupplier
     */
    public UtxoTransactionBuilderImpl(UtxoSupplier utxoSupplier) {
        this.utxoSelectionStrategy = new DefaultUtxoSelectionStrategyImpl(utxoSupplier);
    }

    /**
     * Create a {@link UtxoTransactionBuilder} with custom {@link UtxoSelectionStrategy}
     *
     * @param utxoSelectionStrategy
     */
    public UtxoTransactionBuilderImpl(UtxoSelectionStrategy utxoSelectionStrategy) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
    }

    /**
     * Set a custom UtxoSelectionStrategy
     *
     * @param utxoSelectionStrategy
     */
    @Override
    public void setUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
    }

    /**
     * Get current {@link UtxoSelectionStrategy}
     *
     * @return
     */
    public UtxoSelectionStrategy getUtxoSelectionStrategy() {
        return this.utxoSelectionStrategy;
    }

    /**
     * Build Transaction
     *
     * @param transactions
     * @param detailsParams
     * @return
     * @throws ApiException
     */
    @Override
    public Transaction buildTransaction(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams,
                                        Metadata metadata, ProtocolParams protocolParams) throws ApiException {
        TransactionBody transactionBody = UtxoTransactionBodyBuilder.buildTransferBody(transactions,
                detailsParams, protocolParams, this.utxoSelectionStrategy);

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        return Transaction.builder()
                .body(transactionBody)
                .auxiliaryData(auxiliaryData)
                .build();
    }

    @Override
    public Transaction buildMintTokenTransaction(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata, ProtocolParams protocolParams) throws ApiException {

        TransactionBody transactionBody = UtxoTransactionBodyBuilder.buildMintBody(mintTransaction, detailsParams, protocolParams, this.utxoSelectionStrategy);

        if (log.isDebugEnabled())
            log.debug(JsonUtil.getPrettyJson(transactionBody));

        AuxiliaryData auxiliaryData = AuxiliaryData.builder()
                .metadata(metadata)
                .build();

        Transaction transaction = Transaction.builder()
                .body(transactionBody)
                .auxiliaryData(auxiliaryData)
                .build();

        return transaction;
    }

    @Override
    public List<Utxo> getUtxos(String address, String unit, BigInteger amount) throws ApiException {
        Set<Utxo> selected = this.utxoSelectionStrategy.select(address, new Amount(unit, amount), Collections.emptySet());
        return selected != null ? new ArrayList<>(selected) : Collections.emptyList();
    }
}
