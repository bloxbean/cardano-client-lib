package com.bloxbean.cardano.client.backend.ogmios.http.dto;

import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ValidateTransactionDTO {
    private String validator;
    private ExecutionUnitDTO budget;

    public EvaluationResult toEvaluationResult() {
        EvaluationResult evaluationResult = new EvaluationResult();
        evaluationResult.setRedeemerTag(RedeemerTag.valueOf(validator));
        evaluationResult.setExUnits(budget.toExecutionUnit());
        return evaluationResult;
    }
}
