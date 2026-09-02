package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Declarative third-party programmable-token transfer. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableThirdPartyTransferIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "third_party_transfer";

    private String holder;
    private String receiver;

    @JsonUnwrapped
    private Amount amount;

    private PlutusData thirdPartyRedeemer;

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableIntentValidation.required(holder, "holder");
        ProgrammableIntentValidation.required(receiver, "receiver");
        ProgrammableIntentValidation.amount(amount, "third-party transfer");
    }
}
