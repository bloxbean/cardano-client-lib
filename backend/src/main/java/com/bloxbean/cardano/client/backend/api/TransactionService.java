package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.util.List;

public interface TransactionService {

    /**
     * submitTransaction
     *
     * @param cborData cborData
     * @return Transaction hash
     * @throws ApiException
     */
    Result<String> submitTransaction(byte[] cborData) throws ApiException;

    /**
     * getTransaction
     *
     * @param txnHash txnHash
     * @return Transaction content
     * @throws ApiException
     */
    Result<TransactionContent> getTransaction(String txnHash) throws ApiException;

    /**
     * getTransactions
     *
     * @param txnHashCollection Collection of TX Ids
     * @return Transaction content
     * @throws ApiException
     */
    Result<List<TransactionContent>> getTransactions(List<String> txnHashCollection) throws ApiException;

    /**
     * getTransactionUtxos
     *
     * @param txnHash txnHash
     * @return Transaction Utxos
     * @throws ApiException
     */
    Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException;

    /**
     * getTransactionRedeemers
     *
     * @param txnHash txnHash
     * @return Transaction Redeemers
     * @throws ApiException
     */
    Result<List<TxContentRedeemers>> getTransactionRedeemers(String txnHash) throws ApiException;

    /**
     * Get transaction output at given index for a transaction hash
     * @param txnHash Transaction hash
     * @param outputIndex Output index
     * @return Utxo
     * @throws ApiException If any error occurs
     */
    default Result<Utxo> getTransactionOutput(String txnHash, int outputIndex) throws ApiException {
        Result<TxContentUtxo> result = this.getTransactionUtxos(txnHash);
        if (!result.isSuccessful())
            return Result.error(result.getResponse()).code(result.code());

        TxContentUtxo txContentUtxo = result.getValue();
        if (txContentUtxo == null)
            return Result.error("No UTXO found for txHash: " + txnHash).code(404);

        List<TxContentUtxoOutputs> outputs = txContentUtxo.getOutputs();
        if (outputs == null) {
            return Result.error("No UTXO found for txHash: " + txnHash).code(404);
        } else {
            return outputs.stream().filter(output -> output.getOutputIndex() == outputIndex)
                    .findFirst()
                    .map(output -> {
                        Utxo utxo = output.toUtxos(txnHash);
                        return Result.success(JsonUtil.getPrettyJson(utxo)).withValue(utxo).code(result.code());
                    }).orElseGet(() -> Result.error("No UTXO found for txHash: " + txnHash + ", outputIndex: " + outputIndex).code(404));
        }
    }

    /**
     * Evaluate ExUnits for the scripts in the input transaction
     *
     * @param cborData Serialized cbor bytes
     * @return List of {@link EvaluationResult}
     * @throws ApiException
     */
    default Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException {
        throw new UnsupportedOperationException("Not yet supported");
    }

}
