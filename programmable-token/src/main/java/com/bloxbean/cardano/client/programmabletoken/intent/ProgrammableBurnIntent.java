package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Declarative programmable-token burn with role-specific authorizations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableBurnIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "burn";

    @JsonUnwrapped
    private ProgrammableTokenPolicyRef policy;

    private List<ProgrammableTokenAsset> assets;
    private PlutusData transferRedeemer;
    private PlutusData issuanceRedeemer;

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        if (policy == null) throw new IllegalStateException("policy is required");
        policy.validate();
        ProgrammableIntentValidation.assets(assets, OPERATION);
    }
}
