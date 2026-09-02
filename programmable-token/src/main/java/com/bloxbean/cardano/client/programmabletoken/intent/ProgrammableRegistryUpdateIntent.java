package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenPolicyRef;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistryUpdate;
import com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Declarative update of a programmable-token registry entry. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableRegistryUpdateIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "update_registry";

    private String policyId;
    private ProgrammableTokenRegistryUpdate update;
    @JsonIgnore
    private PlutusDataValue authorization;

    @JsonGetter("authorization")
    public JsonNode authorizationStructured() {
        return authorization == null ? null : authorization.structuredForYaml();
    }

    @JsonSetter("authorization")
    public void authorizationStructured(JsonNode value) {
        authorization = PlutusDataValue.readStructured(authorization, value, "authorization");
    }

    @JsonGetter("authorization_hex")
    public String authorizationHex() {
        return authorization == null ? null : authorization.cborHexForYaml();
    }

    @JsonSetter("authorization_hex")
    public void authorizationHex(String value) {
        authorization = PlutusDataValue.readCborHex(authorization, value, "authorization");
    }

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableTokenPolicyRef.policyId(policyId);
        if (update == null) throw new IllegalStateException("update is required");
        update.validate();
        if (authorization == null)
            throw new IllegalStateException("authorization is required");
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolved = authorization == null ? null
                : authorization.resolve(variables, "authorization");
        return resolved == authorization ? this : toBuilder().authorization(resolved).build();
    }
}
