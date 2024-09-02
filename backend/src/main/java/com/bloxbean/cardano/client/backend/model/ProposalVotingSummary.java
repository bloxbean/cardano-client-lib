package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * Proposal Voting Summary
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProposalVotingSummary {

    /**
     * Proposal Action Type (ParameterChange, HardForkInitiation, TreasuryWithdrawals, NoConfidence, NewCommittee, NewConstitution, InfoAction)
     */
    private String proposalType;

    /**
     * Epoch for which data was collated
     */
    private Integer epochNo;

    /**
     * Number of 'yes' votes casted by dreps
     */
    private Integer drepYesVotesCast;

    /**
     * Power of 'yes' votes from dreps
     */
    private Integer drepYesVotePower;

    /**
     * Percentage of 'yes' votes from dreps
     */
    private Double drepYesPct;

    /**
     * Number of 'no' votes casted by dreps
     */
    private Integer drepNoVotesCast;

    /**
     * Power of 'no' votes from dreps
     */
    private Integer drepNoVotePower;

    /**
     * Percentage of 'no' votes from dreps
     */
    private Double drepNoPct;

    /**
     * Number of 'yes' votes casted by pools
     */
    private Integer poolYesVotesCast;

    /**
     * Power of 'yes' votes from pools
     */
    private Integer poolYesVotePower;

    /**
     * Percentage of 'yes' votes from pools
     */
    private Double poolYesPct;

    /**
     * Number of 'no' votes casted by pools
     */
    private Integer poolNoVotesCast;

    /**
     * Power of 'no' votes from pools
     */
    private Integer poolNoVotePower;

    /**
     * Percentage of 'no' votes from pools
     */
    private Double poolNoPct;

    /**
     * Number of 'yes' votes casted by committee
     */
    private Integer committeeYesVotesCast;

    /**
     * Percentage of 'yes' votes from committee
     */
    private Double committeeYesPct;

    /**
     * Number of 'no' votes casted by committee
     */
    private Integer committeeNoVotesCast;

    /**
     * Percentage of 'no' votes from committee
     */
    private Double committeeNoPct;
}
