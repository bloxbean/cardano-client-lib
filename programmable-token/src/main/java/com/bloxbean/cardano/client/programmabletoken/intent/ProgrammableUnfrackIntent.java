package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Declarative holder-driven programmable-token UTxO restructuring request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableUnfrackIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "unfrack";

    private String policyId;
    private PlutusData authorization;

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableTokenPolicyRef.policyId(policyId);
    }
}
