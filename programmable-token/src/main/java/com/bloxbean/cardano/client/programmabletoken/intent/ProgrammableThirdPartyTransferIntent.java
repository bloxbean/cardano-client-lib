package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Declarative third-party programmable-token transfer. */
@Data
@Builder(toBuilder = true)
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

    @JsonIgnore
    private PlutusDataValue thirdPartyRedeemer;

    @JsonGetter("third_party_redeemer")
    public JsonNode thirdPartyRedeemerStructured() {
        return thirdPartyRedeemer == null ? null : thirdPartyRedeemer.structuredForYaml();
    }

    @JsonSetter("third_party_redeemer")
    public void thirdPartyRedeemerStructured(JsonNode value) {
        thirdPartyRedeemer = PlutusDataValue.readStructured(
                thirdPartyRedeemer, value, "third_party_redeemer");
    }

    @JsonGetter("third_party_redeemer_hex")
    public String thirdPartyRedeemerHex() {
        return thirdPartyRedeemer == null ? null : thirdPartyRedeemer.cborHexForYaml();
    }

    @JsonSetter("third_party_redeemer_hex")
    public void thirdPartyRedeemerHex(String value) {
        thirdPartyRedeemer = PlutusDataValue.readCborHex(
                thirdPartyRedeemer, value, "third_party_redeemer");
    }

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableIntentValidation.required(holder, "holder");
        ProgrammableIntentValidation.required(receiver, "receiver");
        ProgrammableIntentValidation.amount(amount, "third-party transfer");
        if (thirdPartyRedeemer == null)
            throw new IllegalStateException("third_party_redeemer is required");
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolved = thirdPartyRedeemer == null ? null
                : thirdPartyRedeemer.resolve(variables, "third_party_redeemer");
        return resolved == thirdPartyRedeemer ? this
                : toBuilder().thirdPartyRedeemer(resolved).build();
    }
}
