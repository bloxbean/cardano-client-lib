package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.math.BigInteger;

public interface FeeCalculationService {

    /**
     * Calculate estimated fee for a payment transaction
     * @param paymentTransaction
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws CborSerializationException
     * @throws AddressExcepion
     */
    BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                            Metadata metadata) throws ApiException, CborSerializationException, AddressExcepion;

    /**
     * Calculate estimated fee for a payment transaction
     * @param paymentTransaction
     * @param detailsParams
     * @param metadata
     * @param protocolParams
     * @return
     * @throws CborSerializationException
     * @throws AddressExcepion
     * @throws ApiException
     */
    BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                            Metadata metadata, ProtocolParams protocolParams)
            throws CborSerializationException, AddressExcepion, ApiException;

    /**
     * Calculate estimated fee for a token mint transaction
     * @param mintTransaction
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws CborSerializationException
     * @throws AddressExcepion
     */
    BigInteger calculateFee(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, CborSerializationException, AddressExcepion;

    /**
     * Calculate estimated fee for a token mint transaction
     * @param mintTransaction
     * @param detailsParams
     * @param metadata
     * @param protocolParams
     * @return
     * @throws ApiException
     * @throws CborSerializationException
     * @throws AddressExcepion
     */
    BigInteger calculateFee(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata, ProtocolParams protocolParams)
            throws ApiException, CborSerializationException, AddressExcepion;

    /**
     * Calculate estimated fee for a transaction. For correct fee estimation, sign the transaction and then calculate fee.
     * @param transaction Signed transaction
     * @return Estimated fee
     * @throws ApiException
     * @throws CborSerializationException
     */
    BigInteger calculateFee(Transaction transaction) throws ApiException, CborSerializationException;

    /**
     * Calculate estimated fee for a transaction. For correct fee estimation, sign the transaction and then calculate fee.
     * @param transaction Signed transaction
     * @param protocolParams
     * @return Estimated fee
     * @throws CborSerializationException
     */
    BigInteger calculateFee(Transaction transaction, ProtocolParams protocolParams) throws CborSerializationException;
}
