package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;

import java.util.List;

public interface TransactionService {

    /**
     *
     * @param cborData
     * @return Transaction hash
     * @throws ApiException
     */
    Result<String> submitTransaction(byte[] cborData) throws ApiException;

    /**
     *
     * @param txnHash
     * @return Transaction content
     * @throws ApiException
     */
    Result<TransactionContent> getTransaction(String txnHash) throws ApiException;

    /**
     *
     * @param txnHash
     * @return Transaction Utxos
     * @throws ApiException
     */
    Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException;

    /**
     * Evaluate ExUnits for the scripts in the input transaction
     * @param cborData Serialized cbor bytes
     * @return List of {@link EvaluationResult}
     * @throws ApiException
     */
    default Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException {
        throw new UnsupportedOperationException("Not yet supported");
    }

}
