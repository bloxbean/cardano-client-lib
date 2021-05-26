package com.bloxbean.cardano.client.backend.api.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;

import java.math.BigInteger;

public interface FeeCalculationService {

    /**
     * Calculate estimated fee for a payment transaction
     * @param paymentTransaction
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws TransactionSerializationException
     * @throws CborException
     * @throws AddressExcepion
     */
    BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                            Metadata metadata) throws ApiException, TransactionSerializationException, CborException, AddressExcepion;

    /**
     * Calculate estimated fee for a payment transaction
     * @param paymentTransaction
     * @param detailsParams
     * @param metadata
     * @param protocolParams
     * @return
     * @throws TransactionSerializationException
     * @throws CborException
     * @throws AddressExcepion
     * @throws ApiException
     */
    BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                            Metadata metadata, ProtocolParams protocolParams) throws TransactionSerializationException, CborException,
            AddressExcepion, ApiException;

    /**
     * Calculate estimated fee for a token mint transaction
     * @param mintTransaction
     * @param detailsParams
     * @param metadata
     * @return
     * @throws ApiException
     * @throws TransactionSerializationException
     * @throws CborException
     * @throws AddressExcepion
     */
    BigInteger calculateFee(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, TransactionSerializationException, CborException, AddressExcepion;

    /**
     * Calculate estimated fee for a token mint transaction
     * @param mintTransaction
     * @param detailsParams
     * @param metadata
     * @param protocolParams
     * @return
     * @throws ApiException
     * @throws TransactionSerializationException
     * @throws CborException
     * @throws AddressExcepion
     */
    BigInteger calculateFee(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata, ProtocolParams protocolParams)
            throws ApiException, TransactionSerializationException, CborException, AddressExcepion;

}
