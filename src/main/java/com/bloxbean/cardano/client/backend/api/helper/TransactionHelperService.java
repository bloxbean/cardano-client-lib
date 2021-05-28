package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
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
    private UtxoTransactionBuilder utxoTransactionBuilder;

    public TransactionHelperService(UtxoService utxoService, TransactionService transactionService) {
        this.utxoService = utxoService;
        this.transactionService = transactionService;
        this.utxoTransactionBuilder = new UtxoTransactionBuilder(utxoService, transactionService);
    }

    /**
     * Get UtxoTransactionBuilder set in this TransactionHelperService
     * @return
     */
    public UtxoTransactionBuilder getUtxoTransactionBuilder() {
        return this.utxoTransactionBuilder;
    }

    /**
     *
     * @param paymentTransaction
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result transfer(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams)
            throws ApiException, AddressExcepion, CborSerializationException {
        return transfer(Arrays.asList(paymentTransaction), detailsParams, null);
    }

    /**
     *
     * @param paymentTransaction
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result transfer(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, AddressExcepion, CborSerializationException {
        return transfer(Arrays.asList(paymentTransaction), detailsParams, metadata);
    }

    /**
     *
     * @param paymentTransactions
     * @param detailsParams
     * @return
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result<String> transfer(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams)
            throws ApiException, AddressExcepion, CborSerializationException {
        return transfer(paymentTransactions, detailsParams, null);
    }

    /**
     * Transfer fund
     * @param paymentTransactions
     * @param detailsParams
     * @return Result object with transaction id
     * @throws ApiException
     * @throws AddressExcepion
     * @throws CborSerializationException
     */
    public Result<String> transfer(List<PaymentTransaction> paymentTransactions, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiException, AddressExcepion, CborSerializationException {
        String signedTxn = createSignedTransaction(paymentTransactions, detailsParams, metadata);

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);

        Result<String> result = transactionService.submitTransaction(signedTxnBytes);

        if(!result.isSuccessful()) {
            LOG.error("Trasaction submission failed");
        }

        return result;
    }

    /**
     * Get cbor serialized signed transaction in Hex
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
        if(LOG.isDebugEnabled())
            LOG.debug("Requests: \n" + JsonUtil.getPrettyJson(paymentTransactions));

        Transaction transaction = utxoTransactionBuilder.buildTransaction(paymentTransactions, detailsParams, metadata);

        if(LOG.isDebugEnabled())
            LOG.debug(JsonUtil.getPrettyJson(transaction));

        String txnHex = transaction.serializeToHex();
        String signedTxn = txnHex;
        for(PaymentTransaction txn: paymentTransactions) {
            signedTxn = txn.getSender().sign(signedTxn);
        }
        return signedTxn;
    }

    /**
     * Mint tranaction
     * @param mintTransaction
     * @param detailsParams
     * @return
     * @throws AddressExcepion
     * @throws ApiException
     * @throws CborSerializationException
     */
    public Result mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams)
            throws AddressExcepion, ApiException, CborSerializationException {
        return mintToken(mintTransaction, detailsParams, null);
    }

    /**
     * Create a token mint transaction, sign and submit to the network
     * @param mintTransaction
     * @param detailsParams
     * @return Result object with transaction id
     * @throws AddressExcepion
     * @throws ApiException
     * @throws CborSerializationException
     */
    public Result mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws AddressExcepion, ApiException, CborSerializationException {
        String signedTxn = createSignedMintTransaction(mintTransaction, detailsParams, metadata);

        byte[] signedTxnBytes = HexUtil.decodeHexString(signedTxn);
        Result<String> result = transactionService.submitTransaction(signedTxnBytes);

        return result;
    }

    /**
     * Create a mint transaction, sign and return cbor value as hex string
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
        if(LOG.isDebugEnabled())
            LOG.debug("Requests: \n" + JsonUtil.getPrettyJson(mintTransaction));

        Transaction transaction = utxoTransactionBuilder.buildMintTokenTransaction(mintTransaction, detailsParams, metadata);

        TransactionWitnessSet transactionWitnessSet = new TransactionWitnessSet();
        transactionWitnessSet.getNativeScripts().add(mintTransaction.getPolicyScript());
        transaction.setWitnessSet(transactionWitnessSet);
        transaction.setMetadata(metadata);

        if(LOG.isDebugEnabled())
            LOG.debug(JsonUtil.getPrettyJson(transaction));

        String signedTxn = mintTransaction.getSender().sign(transaction);

        if(mintTransaction.getPolicyKeys() == null || mintTransaction.getPolicyKeys().size() == 0){
            throw new ApiException("No policy key (secret key) found to sign the mint transaction");
        }

        for(SecretKey key: mintTransaction.getPolicyKeys()) {
            signedTxn = CardanoJNAUtil.signWithSecretKey(signedTxn, HexUtil.encodeHexString(key.getBytes()));
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug("Signed Txn : " + signedTxn);
        }
        return signedTxn;
    }
}
