package com.bloxbean.cardano.client.backend.api.helper.impl;

import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.util.Arrays;

public class FeeCalculationServiceImpl implements FeeCalculationService {

    private TransactionHelperService transactionHelperService;
    private EpochService epochService;

    public FeeCalculationServiceImpl(TransactionHelperService transactionHelperService, EpochService epochService) {
        this.transactionHelperService = transactionHelperService;
        this.epochService = epochService;
    }

    @Override
    public BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                                   Metadata metadata) throws ApiException, CborSerializationException, AddressExcepion {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        return calculateFee(paymentTransaction, detailsParams, metadata, protocolParamsResult.getValue());
    }

    @Override
    public BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                                   Metadata metadata, ProtocolParams protocolParams) throws CborSerializationException, AddressExcepion, ApiException {

        if(paymentTransaction.getFee() == null || paymentTransaction.getFee().compareTo(BigInteger.valueOf(170000)) == -1) //Just a dummy fee
            paymentTransaction.setFee(new BigInteger("170000")); //Set a min fee just for calcuation purpose if not set
        //Build transaction
        String txnCBORHash = transactionHelperService.createSignedTransaction(Arrays.asList(paymentTransaction), detailsParams, metadata);

        //Calculate fee
        return doFeeCalculationFromTxnSize(HexUtil.decodeHexString(txnCBORHash), protocolParams);
    }

    @Override
    public BigInteger calculateFee(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, CborSerializationException, AddressExcepion {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        return calculateFee(mintTransaction, detailsParams, metadata, protocolParamsResult.getValue());
    }

    @Override
    public BigInteger calculateFee(MintTransaction mintTransaction, TransactionDetailsParams detailsParams,
                                   Metadata metadata, ProtocolParams protocolParams) throws ApiException,
            CborSerializationException, AddressExcepion {
        if(mintTransaction.getFee() == null || mintTransaction.getFee().compareTo(BigInteger.valueOf(170000)) == -1) //Just a dummy fee
            mintTransaction.setFee(new BigInteger("170000")); //Set a min fee just for calcuation purpose if not set
        //Build transaction
        String txnCBORHash = transactionHelperService.createSignedMintTransaction(mintTransaction, detailsParams, metadata);

        //Calculate fee
        return doFeeCalculationFromTxnSize(HexUtil.decodeHexString(txnCBORHash), protocolParams);
    }

    private BigInteger doFeeCalculationFromTxnSize(byte[] bytes, ProtocolParams protocolParams) {
        //a + b x size
        return BigInteger.valueOf((protocolParams.getMinFeeA() * bytes.length) + protocolParams.getMinFeeB());
    }
}
