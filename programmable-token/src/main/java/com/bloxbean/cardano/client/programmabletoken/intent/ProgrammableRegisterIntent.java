package com.bloxbean.cardano.client.programmabletoken.intent;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.ProgrammableTokenRegistration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Declarative programmable-token registration publishing a named policy reference. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableRegisterIntent implements ProgrammableTokenIntent {
    public static final String OPERATION = "register";

    private String name;
    private ProgrammableTokenRegistration registration;
    private PlutusData registrationRedeemer;

    @Override public String getOperation() { return OPERATION; }

    @Override
    public void validate() {
        ProgrammableIntentValidation.required(name, "registration name");
        if (registration == null) throw new IllegalStateException("registration is required");
        registration.validate();
    }
}
