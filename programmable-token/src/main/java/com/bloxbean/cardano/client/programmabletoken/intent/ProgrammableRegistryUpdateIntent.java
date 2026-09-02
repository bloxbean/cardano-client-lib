package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistryUpdate;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Declarative update of a programmable-token registry entry. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableRegistryUpdateIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "update_registry";

    private String policyId;
    private ProgrammableTokenRegistryUpdate update;
    private PlutusData authorization;

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableTokenPolicyRef.policyId(policyId);
        if (update == null) throw new IllegalStateException("update is required");
        update.validate();
    }
}
