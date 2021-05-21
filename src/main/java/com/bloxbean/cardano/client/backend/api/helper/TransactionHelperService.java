package com.bloxbean.cardano.client.backend.api.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import com.bloxbean.cardano.client.jna.CardanoJNAUtil;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class TransactionHelperService {
    private Logger LOG = LoggerFactory.getLogger(TransactionHelperService.class);

    private UtxoService utxoService;
    private TransactionService transactionService;

    public TransactionHelperService(UtxoService utxoService, TransactionService transactionService) {
        this.utxoService = utxoService;
        this.transactionService = transactionService;
    }

    /**
     *
     * @param paymentTransaction
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws TransactionSerializationException
     * @throws CborException
     */
    public Result transfer(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams)
            throws ApiException, AddressExcepion, TransactionSerializationException, CborException {
        return transfer(Arrays.asList(paymentTransaction), detailsParams);
    }

    /**
     * Transfer fund
     * @param paymentTransactions
     * @param detailsParams
     * @return Result object with transaction id
     * @throws ApiException
     * @throws AddressExcepion
     * @throws TransactionSerializationException
     * @throws CborException
     */
    public Result<String> transfer(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams)
            throws ApiException, AddressExcepion, TransactionSerializationException, CborException {
        UtxoTransactionBuilder utxoTransactionBuilder = new UtxoTransactionBuilder(this.utxoService, this.transactionService);

        if(LOG.isDebugEnabled())
            LOG.debug("Requests: \n" + JsonUtil.getPrettyJson(paymentTransactions));

        Transaction transaction = utxoTransactionBuilder.buildTransaction(paymentTransactions, detailsParams);

        if(LOG.isDebugEnabled())
            LOG.debug(JsonUtil.getPrettyJson(transaction));

        String txnHex = transaction.serializeToHex();
        String signedTxn = txnHex;
        for(PaymentTransaction txn: paymentTransactions) {
            signedTxn = txn.getSender().sign(signedTxn);
        }

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);

        Result<String> result = transactionService.submitTransaction(signedTxnBytes);

        if(!result.isSuccessful()) {
            LOG.error("Trasaction submission failed");
        }

        return result;
    }

    /**
     *
     * @param mintTransaction
     * @param detailsParams
     * @return Result object with transaction id
     * @throws AddressExcepion
     * @throws ApiException
     * @throws CborException
     * @throws TransactionSerializationException
     */
    public Result mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams)
            throws AddressExcepion, ApiException, CborException, TransactionSerializationException {
        UtxoTransactionBuilder utxoTransactionBuilder = new UtxoTransactionBuilder(this.utxoService, this.transactionService);

        if(LOG.isDebugEnabled())
            LOG.debug("Requests: \n" + JsonUtil.getPrettyJson(mintTransaction));

        Transaction transaction = utxoTransactionBuilder.mintToken(mintTransaction, detailsParams);

        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();
        transactionWitnessSet.getNativeScripts().add(mintTransaction.getPolicyScript());
        transaction.setWitnessSet(transactionWitnessSet);

        if(LOG.isDebugEnabled())
            LOG.debug(JsonUtil.getPrettyJson(transaction));

        String signedTxn = mintTransaction.getSender().sign(transaction);

        signedTxn = CardanoJNAUtil.signWithSecretKey(signedTxn, HexUtil.encodeHexString(mintTransaction.getPolicyKey().getBytes()));

        if(LOG.isDebugEnabled()) {
            LOG.debug("Signed Txn : " + signedTxn);
        }

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);
        Result<String> result = transactionService.submitTransaction(signedTxnBytes);

        return result;
    }
}
