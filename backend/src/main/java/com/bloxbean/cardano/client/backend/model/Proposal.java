package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * Proposal
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Proposal {

    /**
     * UNIX timestamp of the block
     */
    private Long blockTime;

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
     * Proposal Action Type (ParameterChange, HardForkInitiation, TreasuryWithdrawals, NoConfidence, NewCommittee, NewConstitution, InfoAction)
     */
    private String proposalType;

    /**
     * Description for Proposal Action
     */
    private JsonNode proposalDescription;

    /**
     * DRep's registration deposit in number (null if not applicable)
     */
    private String deposit;

    /**
     * The StakeAddress index of the reward address to receive the deposit when it is repaid
     */
    private String returnAddress;

    /**
     * Shows the epoch at which this governance action was proposed
     */
    private Integer proposedEpoch;

    /**
     * If not null, then this proposal has been ratified at the specified epoch
     */
    private Integer ratifiedEpoch;

    /**
     * If not null, then this proposal has been enacted at the specified epoch
     */
    private Integer enactedEpoch;

    /**
     * If not null, then this proposal has been dropped (expired/enacted) at the specified epoch
     */
    private Integer droppedEpoch;

    /**
     * If not null, then this proposal has been expired at the specified epoch
     */
    private Integer expiredEpoch;

    /**
     * Shows the epoch at which this governance action is expected to expire
     */
    private Integer expiration;

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

    /**
     * Comment attached to the metadata (null if not applicable)
     */
    private String metaComment;

    /**
     * The language described in the context of the metadata as per CIP-100 (null if not applicable)
     */
    private String metaLanguage;

    /**
     * Indicate whether data is invalid
     */
    private Boolean metaIsValid;

    /**
     * Object containing the withdrawal details or null if not applicable
     */
    private TxWithdrawal withdrawal;

    /**
     * Object containing parameter proposal details
     */
    private JsonNode paramProposal;
}
