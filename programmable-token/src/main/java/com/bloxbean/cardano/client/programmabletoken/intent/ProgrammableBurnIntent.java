package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
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

import java.util.List;
import java.util.Map;

/** Declarative programmable-token burn with role-specific authorizations. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableBurnIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "burn";

    @JsonUnwrapped
    private ProgrammableTokenPolicyRef policy;

    private List<ProgrammableTokenAsset> assets;
    @JsonIgnore
    private PlutusDataValue transferRedeemer;
    @JsonIgnore
    private PlutusDataValue issuanceRedeemer;

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

    @JsonGetter("issuance_redeemer")
    public JsonNode issuanceRedeemerStructured() {
        return issuanceRedeemer == null ? null : issuanceRedeemer.structuredForYaml();
    }

    @JsonSetter("issuance_redeemer")
    public void issuanceRedeemerStructured(JsonNode value) {
        issuanceRedeemer = PlutusDataValue.readStructured(
                issuanceRedeemer, value, "issuance_redeemer");
    }

    @JsonGetter("issuance_redeemer_hex")
    public String issuanceRedeemerHex() {
        return issuanceRedeemer == null ? null : issuanceRedeemer.cborHexForYaml();
    }

    @JsonSetter("issuance_redeemer_hex")
    public void issuanceRedeemerHex(String value) {
        issuanceRedeemer = PlutusDataValue.readCborHex(
                issuanceRedeemer, value, "issuance_redeemer");
    }

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        if (policy == null) throw new IllegalStateException("policy is required");
        policy.validate();
        ProgrammableIntentValidation.assets(assets, OPERATION);
        if (transferRedeemer == null)
            throw new IllegalStateException("transfer_redeemer is required");
        if (issuanceRedeemer == null)
            throw new IllegalStateException("issuance_redeemer is required");
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolvedTransfer = transferRedeemer == null ? null
                : transferRedeemer.resolve(variables, "transfer_redeemer");
        PlutusDataValue resolvedIssuance = issuanceRedeemer == null ? null
                : issuanceRedeemer.resolve(variables, "issuance_redeemer");
        if (resolvedTransfer == transferRedeemer && resolvedIssuance == issuanceRedeemer) return this;
        return toBuilder().transferRedeemer(resolvedTransfer)
                .issuanceRedeemer(resolvedIssuance).build();
    }
}
