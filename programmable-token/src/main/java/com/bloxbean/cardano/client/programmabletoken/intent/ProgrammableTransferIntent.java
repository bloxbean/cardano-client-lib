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

/** Declarative owner-authorized programmable-token transfer. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableTransferIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "transfer";

    private String receiver;

    @JsonUnwrapped
    private Amount amount;

    @JsonIgnore
    private PlutusDataValue transferRedeemer;

    @JsonGetter("transfer_redeemer")
    public JsonNode transferRedeemerStructured() {
        return transferRedeemer == null ? null : transferRedeemer.structuredForYaml();
    }

    @JsonSetter("transfer_redeemer")
    public void transferRedeemerStructured(JsonNode value) {
        transferRedeemer = PlutusDataValue.readStructured(
                transferRedeemer, value, "transfer_redeemer");
    }

    @JsonGetter("transfer_redeemer_hex")
    public String transferRedeemerHex() {
        return transferRedeemer == null ? null : transferRedeemer.cborHexForYaml();
    }

    @JsonSetter("transfer_redeemer_hex")
    public void transferRedeemerHex(String value) {
        transferRedeemer = PlutusDataValue.readCborHex(
                transferRedeemer, value, "transfer_redeemer");
    }

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableIntentValidation.required(receiver, "receiver");
        ProgrammableIntentValidation.amount(amount, OPERATION);
        if (transferRedeemer == null)
            throw new IllegalStateException("transfer_redeemer is required");
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolved = transferRedeemer == null ? null
                : transferRedeemer.resolve(variables, "transfer_redeemer");
        return resolved == transferRedeemer ? this : toBuilder().transferRedeemer(resolved).build();
    }
}
