package com.bloxbean.cardano.client.backend.api.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.Arrays;

public class FeeCalculationService {

    private UtxoTransactionBuilder utxoTransactionBuilder;
    private EpochService epochService;

    public FeeCalculationService(UtxoTransactionBuilder utxoTransactionBuilder, EpochService epochService) {
        this.utxoTransactionBuilder = utxoTransactionBuilder;
        this.epochService = epochService;
    }

    public BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                                   Metadata metadata) throws AddressExcepion, ApiException, CborException {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        return calculateFee(paymentTransaction, detailsParams, metadata, protocolParamsResult.getValue());
    }

    public BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                                   Metadata metadata, ProtocolParams protocolParams) throws AddressExcepion, ApiException, CborException {
        //Build transaction
        Transaction transaction = utxoTransactionBuilder.buildTransaction(Arrays.asList(paymentTransaction), detailsParams, metadata);

        String txnCBOR = null;
        try {
            txnCBOR = paymentTransaction.getSender().sign(transaction);
        } catch (TransactionSerializationException e) {
            e.printStackTrace();
        }

        //Calculate fee
        return doFeeCalculation(HexUtil.decodeHexString(txnCBOR), protocolParams);
    }

    private BigInteger doFeeCalculation(Transaction transaction, ProtocolParams protocolParams) throws CborException, AddressExcepion {
        //a + b x size
        byte[] bytes = transaction.serialize();
        return BigInteger.valueOf((protocolParams.getMinFeeA() * bytes.length) + protocolParams.getMinFeeB());

    }

    private BigInteger doFeeCalculation(byte[] bytes, ProtocolParams protocolParams) throws CborException, AddressExcepion {
        //a + b x size
        return BigInteger.valueOf((protocolParams.getMinFeeA() * bytes.length) + protocolParams.getMinFeeB());

    }

    public BigInteger calculateFeeFromHash(String txnHash) throws ApiException {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        byte[] bytes = HexUtil.decodeHexString(txnHash);
        ProtocolParams protocolParams = protocolParamsResult.getValue();
        return BigInteger.valueOf((protocolParams.getMinFeeA() * bytes.length) + protocolParams.getMinFeeB());
    }
}
