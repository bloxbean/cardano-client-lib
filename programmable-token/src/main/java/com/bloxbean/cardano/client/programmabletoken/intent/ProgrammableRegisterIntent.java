package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistration;
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

/** Declarative programmable-token registration publishing a named policy reference. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableRegisterIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "register";

    private String name;
    private ProgrammableTokenRegistration registration;
    @JsonIgnore
    private PlutusDataValue registrationRedeemer;

    @JsonGetter("registration_redeemer")
    public JsonNode registrationRedeemerStructured() {
        return registrationRedeemer == null ? null : registrationRedeemer.structuredForYaml();
    }

    @JsonSetter("registration_redeemer")
    public void registrationRedeemerStructured(JsonNode value) {
        registrationRedeemer = PlutusDataValue.readStructured(
                registrationRedeemer, value, "registration_redeemer");
    }

    @JsonGetter("registration_redeemer_hex")
    public String registrationRedeemerHex() {
        return registrationRedeemer == null ? null : registrationRedeemer.cborHexForYaml();
    }

    @JsonSetter("registration_redeemer_hex")
    public void registrationRedeemerHex(String value) {
        registrationRedeemer = PlutusDataValue.readCborHex(
                registrationRedeemer, value, "registration_redeemer");
    }

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableIntentValidation.required(name, "registration name");
        if (registration == null) throw new IllegalStateException("registration is required");
        registration.validate();
        if (registrationRedeemer == null)
            throw new IllegalStateException("registration_redeemer is required");
    }

    @Override
    public TxIntent resolveVariables(Map<String, Object> variables) {
        PlutusDataValue resolved = registrationRedeemer == null ? null
                : registrationRedeemer.resolve(variables, "registration_redeemer");
        return resolved == registrationRedeemer ? this
                : toBuilder().registrationRedeemer(resolved).build();
    }
}
