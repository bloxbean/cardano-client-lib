package com.bloxbean.cardano.client.quicktx.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified PlanAttributes for the maintainable approach.
 * Only contains essential configuration attributes actually used in recording.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanAttributes {

    /**
     * Sender address.
     * Maps to Tx.from(String) or ScriptTx.from(String)
     */
    @JsonProperty("from")
    private String from;

    /**
     * Custom change address.
     * Maps to AbstractTx.withChangeAddress()
     */
    @JsonProperty("change_address")
    private String changeAddress;

    // Simplified approach - removed unused inner classes and enums

    /**
     * Creates a deep copy of these attributes.
     */
    public PlanAttributes deepCopy() {
        return PlanAttributes.builder()
            .from(this.from)
            .changeAddress(this.changeAddress)
            .build();
    }
}
