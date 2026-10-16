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

/** Declarative programmable-token mint. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableMintIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "mint";

    @JsonUnwrapped
    private ProgrammableTokenPolicyRef policy;

    private String receiver;
    private List<ProgrammableTokenAsset> assets;
    @JsonIgnore
    private PlutusDataValue issuanceRedeemer;
    @JsonIgnore
    private PlutusDataValue inlineDatum;

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

    @JsonGetter("inline_datum")
    public JsonNode inlineDatumStructured() {
        return inlineDatum == null ? null : inlineDatum.structuredForYaml();
    }

    @JsonSetter("inline_datum")
    public void inlineDatumStructured(JsonNode value) {
        inlineDatum = PlutusDataValue.readStructured(inlineDatum, value, "inline_datum");
    }

    @JsonGetter("inline_datum_hex")
    public String inlineDatumHex() {
        return inlineDatum == null ? null : inlineDatum.cborHexForYaml();
    }

    @JsonSetter("inline_datum_hex")
    public void inlineDatumHex(String value) {
        inlineDatum = PlutusDataValue.readCborHex(inlineDatum, value, "inline_datum");
    }

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        if (policy == null) throw new IllegalStateException("policy is required");
        policy.validate();
        ProgrammableIntentValidation.required(receiver, "receiver");
        ProgrammableIntentValidation.assets(assets, OPERATION);
        if (issuanceRedeemer == null)
            throw new IllegalStateException("issuance_redeemer is required");
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolvedRedeemer = issuanceRedeemer == null ? null
                : issuanceRedeemer.resolve(variables, "issuance_redeemer");
        PlutusDataValue resolvedDatum = inlineDatum == null ? null
                : inlineDatum.resolve(variables, "inline_datum");
        if (resolvedRedeemer == issuanceRedeemer && resolvedDatum == inlineDatum) return this;
        return toBuilder().issuanceRedeemer(resolvedRedeemer).inlineDatum(resolvedDatum).build();
    }
}
