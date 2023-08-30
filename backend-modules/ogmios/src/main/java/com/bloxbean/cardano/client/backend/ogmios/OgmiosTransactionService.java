package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.adabox.client.OgmiosWSClient;
import io.adabox.model.tx.response.EvaluateTxResponse;
import io.adabox.model.tx.response.SubmitTxResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class OgmiosTransactionService implements TransactionService {

    private final OgmiosWSClient client;

    public OgmiosTransactionService(OgmiosWSClient client) {
        this.client = client;
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) {
        SubmitTxResponse submitTxResponse = client.submitTx(cborData);

        if (submitTxResponse.getSubmitFail() == null) {
            //Calculate transaction hash
            String txHash = calculateTxHash(cborData);
            return Result.success("OK").withValue(txHash).code(200);
        } else {
            return Result.error("FAILED").withValue(submitTxResponse.getSubmitFail()).code(500);
        }
    }

    @Override
    public Result<TransactionContent> getTransaction(String txnHash) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<List<TransactionContent>> getTransactions(List<String> txnHashCollection) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<TxContentUtxo> getTransactionUtxos(String txnHash) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<List<TxContentRedeemers>> getTransactionRedeemers(String txnHash)
        throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException {
        EvaluateTxResponse evaluateTxResponse = client.evaluateTx(cborData);

        if (evaluateTxResponse.getEvaluationFailure() == null) {
            List<io.adabox.model.tx.response.EvaluationResult> adaBoxEvaluationResults
                    = evaluateTxResponse.getEvaluationResults();

            if (adaBoxEvaluationResults == null)
                return Result.error("Empty evaluation result").withValue(Collections.emptyList()).code(200);

            //convert adabox EvaluationResult to cardano-client EvaluationResult
            List<EvaluationResult> evaluationResults = adaBoxEvaluationResults.stream()
                    .map(adaBoxEvaluationResult -> {
                        EvaluationResult evaluationResult = new EvaluationResult();
                        RedeemerTag cclRedeemerTag = RedeemerTag.valueOf(adaBoxEvaluationResult.getRedeemerTag().name());
                        evaluationResult.setRedeemerTag(cclRedeemerTag);
                        evaluationResult.setIndex(adaBoxEvaluationResult.getIndex());
                        if (adaBoxEvaluationResult.getExUnits() != null) {
                            evaluationResult.setExUnits(new ExUnits(adaBoxEvaluationResult.getExUnits().getMem(), adaBoxEvaluationResult.getExUnits().getSteps()));
                        }
                        return evaluationResult;
                    }).collect(Collectors.toList());

            return Result.success("OK").withValue(evaluationResults).code(200);
        } else {
            return Result.error(evaluateTxResponse.getEvaluationFailure().getErrorMsg())
                    .withValue(Collections.emptyList()).code(500);
        }
    }

    //TODO -- Add this method to core module later
    private String calculateTxHash(byte[] cbor) {
        try {
            Transaction transaction = Transaction.deserialize(cbor);
            return HexUtil.encodeHexString(
                    Blake2bUtil.blake2bHash256(CborSerializationUtil.serialize(transaction.getBody().serialize())));
        } catch (Exception e) {
            log.error("Unable to calculate transaction hash", e);
            return null;
        }
    }
}
