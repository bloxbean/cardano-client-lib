package com.bloxbean.cardano.client.backend.ogmios.model.tx.response;

import com.bloxbean.cardano.client.backend.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.ogmios.model.base.Response;
import com.bloxbean.cardano.client.transaction.spec.ExUnits;
import com.bloxbean.cardano.client.transaction.spec.RedeemerTag;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
public class EvaluateTxResponse extends Response {

    private EvaluationFailure evaluationFailure;
    private List<EvaluationResult> evaluationResults;

    public EvaluateTxResponse(long msgId) {
        super(msgId);
    }

    public static EvaluateTxResponse deserialize(long msgId, JsonNode result) {
        EvaluateTxResponse evaluateTxResponse = new EvaluateTxResponse(msgId);
        if (result.has("EvaluationFailure")) {
            evaluateTxResponse.setEvaluationFailure(new EvaluationFailure(result.get("EvaluationFailure").toString()));
        }

        List<EvaluationResult> evaluationResults = new ArrayList<>();
        if (result.has("EvaluationResult")) {
            JsonNode evaluationResultsObj = result.get("EvaluationResult");

            Iterator<String> keys = evaluationResultsObj.fieldNames();

            while (keys.hasNext()) {
                String redeemerPointer = keys.next();

                JsonNode evalRes = evaluationResultsObj.get(redeemerPointer);
                BigInteger memory = evalRes.get("memory").bigIntegerValue();
                BigInteger steps = evalRes.get("steps").bigIntegerValue();

                String[] splits = redeemerPointer.split(":");
                if (splits.length != 2)
                    throw new RuntimeException("Invalid redeemer pointer : " + evaluationResultsObj.toString());

                RedeemerTag redeemerTag;
                if ("spend".equals(splits[0]))
                    redeemerTag = RedeemerTag.Spend;
                else if ("mint".equals(splits[0]))
                    redeemerTag = RedeemerTag.Mint;
                else if ("certificate".equals(splits[0]))
                    redeemerTag = RedeemerTag.Cert;
                else if ("withdrawal".equals(splits[0]))
                    redeemerTag = RedeemerTag.Reward;
                else
                    throw new RuntimeException("Invalid RedeemerTag in evaluateTx reponse: " + splits[0]);

                EvaluationResult evaluationResult = new EvaluationResult();
                evaluationResult.setRedeemerTag(redeemerTag);
                evaluationResult.setIndex(Integer.parseInt(splits[1]));

                ExUnits exUnits = ExUnits.builder()
                        .mem(memory)
                        .steps(steps)
                        .build();
                evaluationResult.setExUnits(exUnits);

                evaluationResults.add(evaluationResult);
            }
        }

        evaluateTxResponse.setEvaluationResults(evaluationResults);

        return evaluateTxResponse;
    }
}
