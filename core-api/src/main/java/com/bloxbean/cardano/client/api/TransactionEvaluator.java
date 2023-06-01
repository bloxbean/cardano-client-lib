package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Implement this interface to provide transaction evaluation capability for transaction with script(s).
 */
public interface TransactionEvaluator {

    /**
     * Evaluate a transaction to get execution units
     * @param cbor - CBOR serialized transaction
     * @param inputUtxos - List of Utxos used in the transaction. This is optional and can be empty for online evaluator like
     *              Blockfrost which already has access to utxos. But for offline evaluator, this may be required to avoid
     *              querying the blockchain for utxos.
     *
     * @return
     * @throws ApiException
     */
    Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException;

    /**
     * Evaluate a transaction to get execution units
     * @param cbor - CBOR serialized transaction
     * @return List of EvaluationResult
     * @throws ApiException
     */
    default Result<List<EvaluationResult>> evaluateTx(byte[] cbor) throws ApiException {
        return evaluateTx(cbor, Collections.emptySet());
    }

    /**
     * Evaluate a transaction to get execution units
     * @param transaction - Transaction object
     * @param inputUtxos - List of Utxos used in the transaction. This is optional and can be empty for online evaluator like
     *              Blockfrost which already has access to utxos. But for offline evaluator, this may be required to avoid
     *              querying the blockchain for utxos.
     * @return List of EvaluationResult
     * @throws ApiException
     */
    default Result<List<EvaluationResult>> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos) throws ApiException {
        try {
            return evaluateTx(transaction.serialize(), inputUtxos);
        } catch (CborSerializationException e) {
            throw new CborRuntimeException("Unable to serialize transaction", e);
        }
    }
}
