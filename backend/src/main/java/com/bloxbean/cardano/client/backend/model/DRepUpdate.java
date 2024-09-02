package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * DRep Update
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DRepUpdate {

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
     * Hash identifier of the transaction
     */
    private String updateTxHash;

    /**
     * The index of this certificate within the transaction
     */
    private Integer certIndex;

    /**
     * UNIX timestamp of the block
     */
    private Long blockTime;

    /**
     * Effective action for this DRep Update certificate (updated, registered, deregistered)
     */
    private String action;

    /**
     * DRep's registration deposit in number (null if not applicable)
     */
    private String deposit;

    /**
     * A URL to a JSON payload of metadata (null if not applicable)
     */
    private String metaUrl;

    /**
     * A hash of the contents of the metadata URL (null if not applicable)
     */
    private String metaHash;

    /**
     * JSON object containing the metadata payload
     */
    private JsonNode metaJson;
}
