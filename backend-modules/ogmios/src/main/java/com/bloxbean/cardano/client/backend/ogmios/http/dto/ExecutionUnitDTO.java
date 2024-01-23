package com.bloxbean.cardano.client.backend.ogmios.http.dto;

import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import io.adabox.model.query.response.models.ExecutionUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigInteger;

@Getter
@Setter
@NoArgsConstructor
public class ExecutionUnitDTO {
    private String memory;
    private String cpu;

    public ExUnits toExecutionUnit() {
        ExUnits executionUnit = new ExUnits();
        executionUnit.setMem(BigInteger.valueOf(Long.valueOf(memory)));
        executionUnit.setSteps(BigInteger.valueOf(Long.valueOf(cpu)));
        return executionUnit;
    }
}
