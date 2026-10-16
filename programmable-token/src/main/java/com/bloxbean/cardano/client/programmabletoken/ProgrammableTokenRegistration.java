package com.bloxbean.cardano.client.programmabletoken;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Typed programmable-token registration declaration. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProgrammableTokenRegistration {
    private ProgrammableTokenCredential mintingLogicScript;
    private ProgrammableTokenCredential transferLogicScript;
    private ProgrammableTokenCredential thirdPartyTransferLogicScript;
    private ProgrammableTokenCredential unfrackingLogicScript;
    private String globalStateCs;

    public void validate() {
        required(mintingLogicScript, "minting logic script");
        required(transferLogicScript, "transfer logic script");
        required(thirdPartyTransferLogicScript, "third-party transfer logic script");
        required(unfrackingLogicScript, "unfracking logic script");
    }

    private static void required(ProgrammableTokenCredential credential, String field) {
        if (credential == null) throw new IllegalStateException(field + " is required");
        credential.validate();
    }
}
