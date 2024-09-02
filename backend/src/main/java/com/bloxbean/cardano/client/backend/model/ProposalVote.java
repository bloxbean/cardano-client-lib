package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * Proposal Vote
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProposalVote {

    /**
     * UNIX timestamp of the block
     */
    private Long blockTime;

    /**
     * The role of the voter (ConstitutionalCommittee, DRep, SPO)
     */
    private String voterRole;

    /**
     * Voter's DRep ID (CIP-129 bech32 format), pool ID (bech32 format) or committee hot ID (CIP-129 bech32 format)
     */
    private String voterId;

    /**
     * Voter's DRep ID, pool ID, or committee hash in hex format
     */
    private String voterHex;

    /**
     * Flag which shows if this credential is a script hash
     */
    private Boolean voterHasScript;

    /**
     * Actual Vote casted (Yes, No, Abstain)
     */
    private String vote;

    /**
     * A URL to a JSON payload of metadata (null if not applicable)
     */
    private String metaUrl;

    /**
     * A hash of the contents of the metadata URL (null if not applicable)
     */
    private String metaHash;
}
