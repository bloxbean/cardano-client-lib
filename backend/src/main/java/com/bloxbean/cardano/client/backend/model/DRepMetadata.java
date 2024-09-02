package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * DRep Metadata
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DRepMetadata {

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
     * A URL to a JSON payload of metadata (null if not applicable)
     */
    private String url;

    /**
     * A hash of the contents of the metadata URL (null if not applicable)
     */
    private String hash;

    /**
     * The raw bytes of the payload (null if not applicable)
     */
    private String json;

    /**
     * A warning that occurred while validating the metadata (null if not applicable)
     */
    private String warning;

    /**
     * The language described in the context of the metadata as per CIP-100 (null if not applicable)
     */
    private String language;

    /**
     * Comment attached to the metadata (null if not applicable)
     */
    private String comment;

    /**
     * Indicate whether data is invalid
     */
    private Boolean isValid;
}
