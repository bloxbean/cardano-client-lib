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

/**
 * Declarative third-party burn: destroy tokens held in one named smart-wallet UTxO, authorised
 * by the token's third-party logic and minting logic rather than by the holder.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableThirdPartyBurnIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "third_party_burn";

    /** {@code txHash#outputIndex} of the holder's smart-wallet UTxO the tokens are burned from. */
    private String holderUtxoRef;

    @JsonUnwrapped
    private ProgrammableTokenPolicyRef policy;

    private List<ProgrammableTokenAsset> assets;
    @JsonIgnore
    private PlutusDataValue thirdPartyRedeemer;
    @JsonIgnore
    private PlutusDataValue issuanceRedeemer;

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
        validateUtxoRef(holderUtxoRef);
        if (policy == null) throw new IllegalStateException("policy is required");
        policy.validate();
        // Same reason as a burn: the policy's registry node and the held tokens must already be
        // on chain, so a policy registered by this same transaction cannot be named.
        if (policy.getName() != null)
            throw new IllegalStateException("third_party_burn requires a literal policy_id; policy_ref '"
                    + policy.getName() + "' names a registration in the same transaction, and a"
                    + " burn needs the policy's registry node and held tokens, which do not exist"
                    + " until that registration is on chain");
        ProgrammableIntentValidation.assets(assets, OPERATION);
        if (thirdPartyRedeemer == null)
            throw new IllegalStateException("third_party_redeemer is required");
        if (issuanceRedeemer == null)
            throw new IllegalStateException("issuance_redeemer is required");
    }

    /** A {@code txHash#outputIndex} reference: a 32-byte hex transaction id and a non-negative index. */
    private static void validateUtxoRef(String utxoRef) {
        if (utxoRef == null || utxoRef.isBlank())
            throw new IllegalStateException("holder_utxo_ref is required");
        if (!utxoRef.matches("(?i)[0-9a-f]{64}#[0-9]{1,9}"))
            throw new IllegalStateException("holder_utxo_ref must be <txHash>#<outputIndex> with a"
                    + " 64-character hex transaction id, but got '" + utxoRef + "'");
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolvedThirdParty = thirdPartyRedeemer == null ? null
                : thirdPartyRedeemer.resolve(variables, "third_party_redeemer");
        PlutusDataValue resolvedIssuance = issuanceRedeemer == null ? null
                : issuanceRedeemer.resolve(variables, "issuance_redeemer");
        if (resolvedThirdParty == thirdPartyRedeemer && resolvedIssuance == issuanceRedeemer) return this;
        return toBuilder().thirdPartyRedeemer(resolvedThirdParty)
                .issuanceRedeemer(resolvedIssuance).build();
    }
}
