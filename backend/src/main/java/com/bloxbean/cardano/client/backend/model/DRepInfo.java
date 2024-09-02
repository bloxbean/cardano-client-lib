package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * DRep Information
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DRepInfo {

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

    /**
     * DRep's registration deposit in number (null if not applicable)
     */
    private String deposit;

    /**
     * Flag to show if the DRep is active (i.e., not expired)
     */
    private Boolean active;

    /**
     * After which epoch DRep is considered inactive (null if not applicable)
     */
    private Integer expiresEpochNo;

    /**
     * The total amount of voting power this DRep is delegated
     */
    private String amount;

    /**
     * A URL to a JSON payload of metadata (null if not applicable)
     */
    private String url;

    /**
     * A hash of the contents of the metadata URL (null if not applicable)
     */
    private String hash;
}
