package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;

public interface TransactionService {

    /**
     *
     * @param cborData
     * @return Transaction hash
     * @throws ApiException
     */
    public Result<String> submitTransaction(byte[] cborData) throws ApiException;

    /**
     *
     * @param txnHash
     * @return Transaction content
     * @throws ApiException
     */
    public Result<TransactionContent> getTransaction(String txnHash) throws ApiException;

    /**
     *
     * @param txnHash
     * @return Transaction Utxos
     * @throws ApiException
     */
    public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException;

}
