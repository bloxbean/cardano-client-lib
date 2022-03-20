package com.bloxbean.cardano.client.api.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.impl.UtxoTransactionBuilderImpl;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class TransactionBuilder {

    private UtxoTransactionBuilder utxoTransactionBuilder;
    private ProtocolParamsSupplier protocolParamsSupplier;

    /**
     * Create a {@link TransactionHelperService} from {@link UtxoSupplier} and {@link ProtocolParamsSupplier}
     *
     * @param utxoSupplier
     * @param protocolParamsSupplier
     */
    public TransactionBuilder(UtxoSupplier utxoSupplier, ProtocolParamsSupplier protocolParamsSupplier) {
        this.utxoTransactionBuilder = new UtxoTransactionBuilderImpl(utxoSupplier);
        this.protocolParamsSupplier = protocolParamsSupplier;
    }

    /**
     * Create a {@link TransactionHelperService} from a {@link UtxoTransactionBuilder} and {@link ProtocolParamsSupplier}
     *
     * @param utxoTransactionBuilder
     * @param protocolParamsSupplier
     */
    public TransactionBuilder(UtxoTransactionBuilder utxoTransactionBuilder, ProtocolParamsSupplier protocolParamsSupplier) {
        this.utxoTransactionBuilder = utxoTransactionBuilder;
        this.protocolParamsSupplier = protocolParamsSupplier;
    }

    /**
     * Create a {@link TransactionHelperService} from {@link UtxoSelectionStrategy} and {@link ProtocolParamsSupplier}
     * This uses the default implementation of {@link UtxoTransactionBuilder} and set the custom {@link UtxoSelectionStrategy}
     *
     * @param utxoSelectionStrategy
     * @param protocolParamsSupplier
     */
    public TransactionBuilder(UtxoSelectionStrategy utxoSelectionStrategy, ProtocolParamsSupplier protocolParamsSupplier) {
        this.utxoTransactionBuilder = new UtxoTransactionBuilderImpl(utxoSelectionStrategy);
        this.protocolParamsSupplier = protocolParamsSupplier;
    }

    /**
     * Get UtxoTransactionBuilder set in this TransactionHelperService
     *
     * @return
     */
    public UtxoTransactionBuilder getUtxoTransactionBuilder() {
        return this.utxoTransactionBuilder;
    }

    /**
     * Set a custom UtxoTransactionBuilder
     *
     * @param utxoTransactionBuilder
     */
    public void setUtxoTransactionBuilder(UtxoTransactionBuilder utxoTransactionBuilder) {
        this.utxoTransactionBuilder = utxoTransactionBuilder;
    }

    public ProtocolParamsSupplier getProtocolParamsSupplier() {
        return protocolParamsSupplier;
    }

    public void setProtocolParamsSupplier(ProtocolParamsSupplier protocolParamsSupplier) {
        this.protocolParamsSupplier = protocolParamsSupplier;
    }

    /**
     * Get cbor serialized signed transaction in Hex
     *
     * @param paymentTransactions
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public String createSignedTransaction(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, AddressExcepion, CborSerializationException {
        if (log.isDebugEnabled())
            log.debug("Requests: \n" + JsonUtil.getPrettyJson(paymentTransactions));

        Transaction transaction = utxoTransactionBuilder.buildTransaction(paymentTransactions, detailsParams, metadata,
                protocolParamsSupplier.getProtocolParams());
        transaction.setValid(true);

        if (log.isDebugEnabled())
            log.debug(JsonUtil.getPrettyJson(transaction));

        Transaction finalTxn = transaction;
        for (PaymentTransaction txn : paymentTransactions) {
            finalTxn = txn.getSender().sign(finalTxn);

            if (txn.getAdditionalWitnessAccounts() != null) { //Add additional witnesses
                for (Account additionalWitnessAcc : txn.getAdditionalWitnessAccounts()) {
                    finalTxn = additionalWitnessAcc.sign(finalTxn);
                }
            }
        }

        return finalTxn.serializeToHex();
    }

    /**
     * Create a mint transaction, sign and return cbor value as hex string
     *
     * @param mintTransaction
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public String createSignedMintTransaction(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, AddressExcepion, CborSerializationException {
        if (log.isDebugEnabled())
            log.debug("Requests: \n" + JsonUtil.getPrettyJson(mintTransaction));

        Transaction transaction = utxoTransactionBuilder.buildMintTokenTransaction(mintTransaction, detailsParams, metadata,
                protocolParamsSupplier.getProtocolParams());
        transaction.setValid(true);

        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();
        transactionWitnessSet.getNativeScripts().add(mintTransaction.getPolicy().getPolicyScript());
        transaction.setWitnessSet(transactionWitnessSet);

        //TODO - check probably not required here.
//        transaction.setAuxiliaryData(AuxiliaryData.builder()
//                .metadata(metadata)
//                .build());

        if (log.isDebugEnabled())
            log.debug(JsonUtil.getPrettyJson(transaction));

        Transaction signedTxn = mintTransaction.getSender().sign(transaction);

        if (mintTransaction.getPolicy().getPolicyKeys() != null) {
            for (SecretKey key : mintTransaction.getPolicy().getPolicyKeys()) {
                signedTxn = TransactionSigner.INSTANCE.sign(signedTxn, key);
            }
        }

        if (mintTransaction.getAdditionalWitnessAccounts() != null) {
            for (Account addWitnessAcc : mintTransaction.getAdditionalWitnessAccounts()) {
                signedTxn = addWitnessAcc.sign(signedTxn);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(signedTxn.toString());
            log.debug(signedTxn.serializeToHex());
        }
        return signedTxn.serializeToHex();
    }
}
