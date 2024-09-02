package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.*;

import java.util.List;

public interface GovernanceService {

    /**
     * DReps Epoch Summary
     * Summary of voting power and DRep count for each epoch
     *
     * @param epochNo   Epoch Number to fetch details for
     * @return Summary of voting power and DRep count for each epoch
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<DRepEpochSummary>> getDRepsEpochSummary(Integer epochNo) throws ApiException;

    /**
     * DReps List
     * List of all active delegated representatives (DReps)
     *
     * @return List of all active delegated representatives (DReps)
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<DRep>> getDRepsList() throws ApiException;

    /**
     * DReps Info
     * Get detailed information about requested delegated representatives (DReps)
     *
     * @param drepIds   List of DRep Ids
     * @return Detailed information about requested delegated representatives (DReps)
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<DRepInfo>> getDRepsInfo(List<String> drepIds) throws ApiException;

    /**
     * DReps Metadata
     * List metadata for requested delegated representatives (DReps)
     *
     * @param drepIds   List of DRep Ids
     * @return Metadata for requested delegated representatives (DReps)
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<DRepMetadata>> getDRepsMetadata(List<String> drepIds) throws ApiException;

    /**
     * DReps Updates
     * List of updates for requested (or all) delegated representatives (DReps)
     *
     * @param drepId   DRep ID in bech32 format
     * @return List of updates for requested (or all) delegated representatives (DReps)
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<DRepUpdate>> getDRepsUpdates(String drepId) throws ApiException;

    /**
     * DReps Votes
     * List of all votes casted by requested delegated representative (DRep)
     *
     * @param drepId   DRep ID in bech32 format
     * @return List of all votes casted by requested delegated representative (DRep)
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<DRepVote>> getDRepsVotes(String drepId) throws ApiException;

    /**
     * DReps Delegators
     * List of all delegators to requested delegated representative (DRep)
     *
     * @param drepId   DRep ID in bech32 format
     * @return List of all delegators to requested delegated representative (DRep)
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<DRepDelegator>> getDRepsDelegators(String drepId) throws ApiException;

    /**
     * Committee Information
     * Information about active committee and its members
     *
     * @return Current governance committee information
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<CommitteeInfo>> getCommitteeInformation() throws ApiException;

    /**
     * Committee Votes
     * List of all votes casted by a given committee member or collective
     *
     * @param ccHotId  Committee member hot key ID in Bech32 format (CIP-5 | CIP-129)

     * @return List of all votes casted by a given committee member or collective
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<CommitteeVote>> getCommitteeVotes(String ccHotId) throws ApiException;

    /**
     * Proposals List
     * List of all governance proposals
     *
     * @return List of all governance action proposals
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<Proposal>> getProposalList() throws ApiException;

    /**
     * Voter's Proposal List
     * List of all governance proposals for specified DRep, SPO or Committee credential
     *
     * @param voterId   Voter ID (DRep, SPO, Committee Member) in Bech32 format (CIP-5 | CIP-129)
     * @return List of all governance action proposals for the specified voter
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<Proposal>> getVoterProposals(String voterId) throws ApiException;

    /**
     * Proposal Voting Summary
     * Summary of votes for a given proposal
     *
     * @param proposalId Government proposal ID in CIP-129 Bech32 format
     * @return Summary of votes for the given proposal
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<ProposalVotingSummary>> getProposalVotingSummary(String proposalId) throws ApiException;

    /**
     * Proposal Votes
     * List of all votes cast on a specified governance action
     *
     * @param proposalId Government proposal ID in CIP-129 Bech32 format
     * @return List of all votes cast on the specified governance action
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<ProposalVote>> getProposalVotes(String proposalId) throws ApiException;

    /**
     * Pool Votes
     * List of all votes casted by a pool
     *
     * @param poolBech32 Pool ID in bech32 format
     * @return List of all votes casted by the requested pool
     * @throws ApiException if an error occurs while attempting to invoke the API
     */
    Result<List<PoolVote>> getPoolVotes(String poolBech32) throws ApiException;
}
