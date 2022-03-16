package com.bloxbean.cardano.client.api.helper;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.impl.UtxoTransactionBuilderImpl;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.api.UtxoSupplier;
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
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * Helper service to build transaction request from high level transaction request apis and submit to the network.
 * To build the final transaction request, this class uses {@link UtxoTransactionBuilder}
 */
@Slf4j
public class TransactionHelperService {

    private TransactionProcessor transactionService;
    private UtxoTransactionBuilder utxoTransactionBuilder;
    private ProtocolParamsSupplier protocolParamsSupplier;

    private ProtocolParams protocolParams;

    /**
     * Create a {@link TransactionHelperService} from {@link TransactionProcessor} and {@link UtxoSupplier}
     *
     * @param transactionProcessor
     * @param utxoSupplier
     */
    public TransactionHelperService(TransactionProcessor transactionProcessor, ProtocolParamsSupplier protocolParamsSupplier, UtxoSupplier utxoSupplier) {
        this.transactionService = transactionProcessor;
        this.utxoTransactionBuilder = new UtxoTransactionBuilderImpl(utxoSupplier);
        this.protocolParamsSupplier = protocolParamsSupplier;
    }

    /**
     * Create a {@link TransactionHelperService} from {@link TransactionProcessor} and custom {@link UtxoTransactionBuilder} implementation
     *
     * @param transactionProcessor
     * @param utxoTransactionBuilder
     */
    public TransactionHelperService(TransactionProcessor transactionProcessor, ProtocolParamsSupplier protocolParamsSupplier, UtxoTransactionBuilder utxoTransactionBuilder) {
        this.transactionService = transactionProcessor;
        this.utxoTransactionBuilder = utxoTransactionBuilder;
        this.protocolParamsSupplier = protocolParamsSupplier;
    }


    /**
     * Create a {@link TransactionHelperService} from {@link TransactionProcessor} and custom {@link UtxoSelectionStrategy}
     * This uses the default implementation of {@link UtxoTransactionBuilder} and set the custom {@link UtxoSelectionStrategy}
     *
     * @param transactionProcessor
     * @param utxoSelectionStrategy
     */
    public TransactionHelperService(TransactionProcessor transactionProcessor, UtxoSelectionStrategy utxoSelectionStrategy) {
        this.transactionService = transactionProcessor;
        this.utxoTransactionBuilder = new UtxoTransactionBuilderImpl(utxoSelectionStrategy);
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

    /**
     * @param paymentTransaction
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result<TransactionResult> transfer(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams)
            throws ApiException, AddressExcepion, CborSerializationException {
        return transfer(Arrays.asList(paymentTransaction), detailsParams, null);
    }

    /**
     * @param paymentTransaction
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result<TransactionResult> transfer(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, AddressExcepion, CborSerializationException {
        return transfer(Arrays.asList(paymentTransaction), detailsParams, metadata);
    }

    /**
     * @param paymentTransactions
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result<TransactionResult> transfer(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams)
            throws ApiException, AddressExcepion, CborSerializationException {
        return transfer(paymentTransactions, detailsParams, null);
    }

    /**
     * Transfer fund
     *
     * @param paymentTransactions
     * @param detailsParams
     * @return Result object with transaction id
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result<TransactionResult> transfer(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, AddressExcepion, CborSerializationException {
        String signedTxn = createSignedTransaction(paymentTransactions, detailsParams, metadata);

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);

        Result<String> result = transactionService.submitTransaction(signedTxnBytes);

        if (!result.isSuccessful()) {
            log.error("Trasaction submission failed");
        }

        //Let's build TransactionResult object
        return processTransactionResult(signedTxnBytes, result);
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

        Transaction transaction = utxoTransactionBuilder.buildTransaction(paymentTransactions, detailsParams, metadata, getProtocolParams());
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
     * Mint tranaction
     *
     * @param mintTransaction
     * @param detailsParams
     * @return
     * @throws AddressExcepion
     * @throws ApiException
     * @throws CborSerializationException
     */
    public Result<TransactionResult> mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams)
            throws AddressExcepion, ApiException, CborSerializationException {
        return mintToken(mintTransaction, detailsParams, null);
    }

    /**
     * Create a token mint transaction, sign and submit to the network
     *
     * @param mintTransaction
     * @param detailsParams
     * @return Result object with transaction id
     * @throws AddressExcepion
     * @throws ApiException
     * @throws CborSerializationException
     */
    public Result<TransactionResult> mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws AddressExcepion, ApiException, CborSerializationException {
        String signedTxn = createSignedMintTransaction(mintTransaction, detailsParams, metadata);

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);
        Result<String> result = transactionService.submitTransaction(signedTxnBytes);

        return processTransactionResult(signedTxnBytes, result);
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

        Transaction transaction = utxoTransactionBuilder.buildMintTokenTransaction(mintTransaction, detailsParams, metadata, getProtocolParams());
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

    /**
     * Set protocolparams. Caller invokes this method to set custom protocol parameters.
     *
     * @param protocolParams
     */
    public void setProtocolParams(ProtocolParams protocolParams) {
        this.protocolParams = protocolParams;
    }

    private Result<TransactionResult> processTransactionResult(byte[] signedTxn, Result<String> result) {
        TransactionResult transactionResult = new TransactionResult();
        transactionResult.setSignedTxn(signedTxn);

        if (result.isSuccessful()) {
            transactionResult.setTransactionId(result.getValue());
            return Result.success(result.getResponse()).withValue(transactionResult).code(result.code());
        } else {
            transactionResult.setTransactionId(null);
            return Result.error(result.getResponse()).withValue(transactionResult).code(result.code());
        }
    }

    private ProtocolParams getProtocolParams() throws ApiException {
        if (protocolParams == null) {
            protocolParams = protocolParamsSupplier.getProtocolParams();
        }

        if (protocolParams == null)
            throw new ApiException("Unable to fetch protocol parameters to build transaction");

        return protocolParams;
    }

}
