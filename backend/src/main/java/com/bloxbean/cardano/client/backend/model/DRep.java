package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * DRep
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DRep {

    /**
     * DRep ID in CIP-129 bech32 format
     */
    private String drepId;

    /**
     * DRep ID in hex format
     */
    private String hex;

    /**
     * Flag which shows if this credential is a script hash
     */
    private Boolean hasScript;

    /**
     * Flag to show if the DRep is currently registered
     */
    private Boolean registered;
}
