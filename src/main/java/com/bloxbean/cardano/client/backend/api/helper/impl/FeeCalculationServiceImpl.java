package com.bloxbean.cardano.client.backend.api.helper.impl;

import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.helper.FeeCalculationService;
import com.bloxbean.cardano.client.backend.api.helper.TransactionHelperService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.InsufficientBalanceException;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.ExUnits;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public class FeeCalculationServiceImpl implements FeeCalculationService {
    //A dummy fee which is used during regular fee calculation
    private final static BigInteger DUMMY_FEE = BigInteger.valueOf(170000);

    //A very minimum fee which is used during retry after InsufficientBalanceException.
    //Here the goal is to make the fee calculation successful by reducing cost during transaction building.
    private final static BigInteger MIN_DUMMY_FEE = BigInteger.valueOf(100000);

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

        PaymentTransaction clonePaymentTransaction = paymentTransaction.toBuilder().build();
        if(clonePaymentTransaction.getFee() == null || clonePaymentTransaction.getFee().compareTo(DUMMY_FEE) == -1) //Just a dummy fee
            clonePaymentTransaction.setFee(DUMMY_FEE); //Set a min fee just for calcuation purpose if not set

        String txnCBORHash;
        try {
            //Build transaction
            txnCBORHash = transactionHelperService.createSignedTransaction(Arrays.asList(clonePaymentTransaction), detailsParams, metadata);
        } catch (InsufficientBalanceException e) {
            clonePaymentTransaction.setFee(MIN_DUMMY_FEE);
            clonePaymentTransaction.setAmount(clonePaymentTransaction.getAmount().subtract(MIN_DUMMY_FEE));
            txnCBORHash = transactionHelperService.createSignedTransaction(Arrays.asList(clonePaymentTransaction), detailsParams, metadata);
        }

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
        if(mintTransaction.getFee() == null || mintTransaction.getFee().compareTo(DUMMY_FEE) == -1) //Just a dummy fee
            mintTransaction.setFee(DUMMY_FEE); //Set a min fee just for calcuation purpose if not set
        //Build transaction
        String txnCBORHash = transactionHelperService.createSignedMintTransaction(mintTransaction, detailsParams, metadata);

        //Calculate fee
        return doFeeCalculationFromTxnSize(HexUtil.decodeHexString(txnCBORHash), protocolParams);
    }

    @Override
    public BigInteger calculateFee(Transaction transaction) throws ApiException, CborSerializationException {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        return calculateFee(transaction, protocolParamsResult.getValue());
    }

    @Override
    public BigInteger calculateFee(Transaction transaction, ProtocolParams protocolParams) throws CborSerializationException {
        if(transaction.getBody().getFee() == null) //Just a dummy fee
            transaction.getBody().setFee(DUMMY_FEE); //Set a min fee just for calcuation purpose if not set

        byte[] serializedBytes = transaction.serialize();
        return doFeeCalculationFromTxnSize(serializedBytes, protocolParams);
    }

    @Override
    public BigInteger calculateFee(byte[] transaction) throws ApiException {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        return calculateFee(transaction, protocolParamsResult.getValue());
    }

    @Override
    public BigInteger calculateFee(byte[] transaction, ProtocolParams protocolParams) {
        return doFeeCalculationFromTxnSize(transaction, protocolParams);
    }

    @Override
    public BigInteger calculateFee(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, CborSerializationException, AddressExcepion {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        return calculateFee(paymentTransactions, detailsParams, metadata, protocolParamsResult.getValue());
    }

    @Override
    public BigInteger calculateFee(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams,
                                   Metadata metadata, ProtocolParams protocolParams) throws CborSerializationException, AddressExcepion, ApiException {
        //Clone
        List<PaymentTransaction> clonePaymentTransactions = paymentTransactions.stream()
                .map(paymentTransaction -> paymentTransaction.toBuilder().build())
                .collect(Collectors.toList());

        //Set fee only for the first payment transaction. NULL for others
        clonePaymentTransactions.get(0).setFee(DUMMY_FEE);
        clonePaymentTransactions.stream().skip(1).forEach(paymentTransaction -> {
            paymentTransaction.setFee(null);
        });

        //Build transaction
        String txnCBORHash;
        try {
            txnCBORHash = transactionHelperService.createSignedTransaction(clonePaymentTransactions, detailsParams, metadata);
        } catch (InsufficientBalanceException exception) {
            //Update fee in the first payment transaction
            clonePaymentTransactions.get(0).setFee(MIN_DUMMY_FEE);
            //substract DUMMY_FEE from lovelace txn
            clonePaymentTransactions.stream().filter(paymentTransaction -> paymentTransaction.getUnit().equals(LOVELACE))
                    .findFirst()
                    .ifPresent(paymentTransaction -> {
                        paymentTransaction.setAmount(paymentTransaction.getAmount().subtract(MIN_DUMMY_FEE));
                    });

            txnCBORHash = transactionHelperService.createSignedTransaction(clonePaymentTransactions, detailsParams, metadata);
        }

        //Calculate fee
        return doFeeCalculationFromTxnSize(HexUtil.decodeHexString(txnCBORHash), protocolParams);
    }

    @Override
    public BigInteger calculateScriptFee(List<ExUnits> exUnitsList) throws ApiException {
        Result<ProtocolParams> protocolParamsResult = epochService.getProtocolParameters();
        if(!protocolParamsResult.isSuccessful())
            throw new ApiException("Unable to fetch protocol parameters to calculate the fee");

        return calculateScriptFee(exUnitsList, protocolParamsResult.getValue());
    }

    @Override
    public BigInteger calculateScriptFee(List<ExUnits> exUnitsList, ProtocolParams protocolParams) {
        BigDecimal priceMem = protocolParams.getPriceMem();
        BigDecimal priceSteps = protocolParams.getPriceStep();

        BigDecimal scriptFee = BigDecimal.ZERO;
        for (ExUnits exUnits: exUnitsList) {
            BigDecimal memCost = new BigDecimal(exUnits.getMem()).multiply(priceMem);
            BigDecimal stepCost = new BigDecimal(exUnits.getSteps()).multiply(priceSteps);
            scriptFee = scriptFee.add(memCost.add(stepCost));
        }

        //round
        scriptFee = scriptFee.setScale(0, RoundingMode.CEILING);

        return scriptFee.toBigInteger();
    }

    private BigInteger doFeeCalculationFromTxnSize(byte[] bytes, ProtocolParams protocolParams) {
        //a + b x size
        return BigInteger.valueOf((protocolParams.getMinFeeA() * bytes.length) + protocolParams.getMinFeeB());
    }
}
