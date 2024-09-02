package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.List;

/**
 * Committee Info
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CommitteeInfo {

    /**
     * Proposal Action ID in accordance with CIP-129 format
     */
    private String proposalId;

    /**
     * Hash identifier of the transaction
     */
    private String proposalTxHash;

    /**
     * Index of governance proposal in transaction
     */
    private Integer proposalIndex;

    /**
     * Quorum numerator for governance proposal
     */
    private Integer quorumNumerator;

    /**
     * Quorum denominator for governance proposal
     */
    private Integer quorumDenominator;

    /**
     * Array of all members part of active governance committee
     */
    private List<CommitteeMember> members;
}
