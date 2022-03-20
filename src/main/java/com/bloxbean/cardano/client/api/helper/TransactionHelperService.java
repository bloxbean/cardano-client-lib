package com.bloxbean.cardano.client.api.helper;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * Helper service to build transaction request from high level transaction request apis and submit to the network.
 * To build the final transaction request, this class uses {@link TransactionBuilder}
 */
@Slf4j
public class TransactionHelperService {

    private TransactionProcessor transactionProcessor;
    private TransactionBuilder transactionBuilder;

    /**
     * Create a {@link TransactionHelperService} from {@link TransactionBuilder} and {@link TransactionProcessor}
     *
     * @param transactionBuilder
     * @param transactionProcessor
     */
    public TransactionHelperService(TransactionBuilder transactionBuilder, TransactionProcessor transactionProcessor) {
        this.transactionProcessor = transactionProcessor;
        this.transactionBuilder = transactionBuilder;
    }

    /**
     * Build and post a transfer transaction created from a {@link PaymentTransaction}
     *
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
     * Build and post a transaction created from {@link PaymentTransaction} and {@link Metadata}
     *
     * @param paymentTransaction
     * @param detailsParams
     * @param metadata
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
     * Build and post a transaction created from multiple {@link PaymentTransaction}
     *
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
     * Build and post a transaction created from multiple {@link PaymentTransaction} and {@link Metadata}
     *
     * @param paymentTransactions
     * @param detailsParams
     * @param metadata
     *
     * @return Result object with transaction id
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result<TransactionResult> transfer(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, AddressExcepion, CborSerializationException {
        String signedTxn = transactionBuilder.createSignedTransaction(paymentTransactions, detailsParams, metadata);

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);

        Result<String> result = transactionProcessor.submitTransaction(signedTxnBytes);

        if (!result.isSuccessful()) {
            log.error("Trasaction submission failed");
        }

        //Let's build TransactionResult object
        return processTransactionResult(signedTxnBytes, result);
    }

    /**
     * Create a token mint transaction, sign and submit to network
     *
     * @param mintTransaction
     * @param detailsParams
     * @return Result object with transaction id
     * @throws AddressExcepion
     * @throws ApiException
     * @throws CborSerializationException
     */
    public Result<TransactionResult> mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams)
            throws AddressExcepion, ApiException, CborSerializationException {
        return mintToken(mintTransaction, detailsParams, null);
    }

    /**
     * Create a token mint transaction with metadata, sign and submit to the network
     *
     * @param mintTransaction
     * @param detailsParams
     * @param metadata
     *
     * @return Result object with transaction id
     * @throws AddressExcepion
     * @throws ApiException
     * @throws CborSerializationException
     */
    public Result<TransactionResult> mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws AddressExcepion, ApiException, CborSerializationException {
        String signedTxn = transactionBuilder.createSignedMintTransaction(mintTransaction, detailsParams, metadata);

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);
        Result<String> result = transactionProcessor.submitTransaction(signedTxnBytes);

        return processTransactionResult(signedTxnBytes, result);
    }

    public TransactionBuilder getTransactionBuilder() {
        return transactionBuilder;
    }

    public void setTransactionBuilder(TransactionBuilder transactionBuilder) {
        this.transactionBuilder = transactionBuilder;
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

}
