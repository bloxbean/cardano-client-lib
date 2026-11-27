package com.bloxbean.cardano.client.programmabletoken.intent;

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

/**
 * Reserved holder-driven programmable-token UTxO restructuring request.
 * Decoding is retained so persisted plans fail validation with an explicit unsupported error.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableUnfrackIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "unfrack";
    public static final String UNSUPPORTED_MESSAGE = "Unfracking is temporarily unsupported in this release";

    private String policyId;
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
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolved = authorization == null ? null
                : authorization.resolve(variables, "authorization");
        return resolved == authorization ? this : toBuilder().authorization(resolved).build();
    }
}
