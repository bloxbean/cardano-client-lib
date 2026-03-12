package com.bloxbean.cardano.client.ledger.rule;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.ValidationError;
import com.bloxbean.cardano.client.ledger.LedgerContext;
import com.bloxbean.cardano.client.ledger.slice.CommitteeSlice;
import com.bloxbean.cardano.client.ledger.slice.DRepsSlice;
import com.bloxbean.cardano.client.ledger.slice.PoolsSlice;
import com.bloxbean.cardano.client.ledger.slice.ProposalsSlice;
import com.bloxbean.cardano.client.spec.NetworkId;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.Withdrawal;
import com.bloxbean.cardano.client.transaction.spec.governance.*;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.*;
import com.bloxbean.cardano.client.util.HexUtil;

import com.bloxbean.cardano.client.ledger.slice.AccountsSlice;

import java.math.BigInteger;
import java.util.*;

/**
 * Category G: Governance Validation Rule.
 * <p>
 * Validates governance proposals and voting procedures: deposits, network consistency,
 * previous action references, treasury withdrawals, committee expiration, voter eligibility.
 * Stateful checks gracefully skip when the required state slice is null.
 */
public class GovernanceValidationRule implements LedgerRule {

    private static final String RULE_NAME = "GovernanceValidation";

    @Override
    public List<ValidationError> validate(LedgerContext context, Transaction transaction) {
        List<ValidationError> errors = new ArrayList<>();
        TransactionBody body = transaction.getBody();

        // G.A: Validate proposal procedures
        List<ProposalProcedure> proposals = body.getProposalProcedures();
        if (proposals != null && !proposals.isEmpty()) {
            for (int i = 0; i < proposals.size(); i++) {
                validateProposal(context, proposals.get(i), i, errors);
            }
        }

        // G.B: Validate voting procedures
        VotingProcedures votingProcedures = body.getVotingProcedures();
        if (votingProcedures != null && votingProcedures.getVoting() != null) {
            validateVoting(context, votingProcedures, errors);
        }

        return errors;
    }

    // ---- Proposal Validation ----

    private void validateProposal(LedgerContext context, ProposalProcedure proposal,
                                  int index, List<ValidationError> errors) {
        ProtocolParams pp = context.getProtocolParams();

        // G-1: deposit == pp.govActionDeposit
        if (pp != null && pp.getGovActionDeposit() != null && proposal.getDeposit() != null) {
            if (proposal.getDeposit().compareTo(pp.getGovActionDeposit()) != 0) {
                errors.add(error("Proposal[" + index + "]: deposit " + proposal.getDeposit()
                        + " does not match pp.govActionDeposit " + pp.getGovActionDeposit()));
            }
        }

        // G-2: reward account network matches context.networkId
        if (context.getNetworkId() != null && proposal.getRewardAccount() != null) {
            validateRewardAccountNetwork(proposal.getRewardAccount(), context, index, errors);
        }

        // ProposalReturnAccountDoesNotExist: reward account credential must be registered
        AccountsSlice accounts = context.getAccountsSlice();
        if (accounts != null && proposal.getRewardAccount() != null) {
            try {
                Address addr = new Address(proposal.getRewardAccount());
                byte[] delegationHash = addr.getDelegationCredentialHash().orElse(null);
                if (delegationHash != null) {
                    String hash = HexUtil.encodeHexString(delegationHash);
                    if (!accounts.isRegistered(hash)) {
                        errors.add(error("Proposal[" + index + "]: return account credential "
                                + hash + " is not registered"));
                    }
                }
            } catch (Exception e) {
                // Skip unparseable addresses
            }
        }

        // Action-specific validation
        GovAction action = proposal.getGovAction();
        if (action != null) {
            validateGovAction(context, action, index, errors);
        }
    }

    private void validateGovAction(LedgerContext context, GovAction action, int proposalIndex,
                                   List<ValidationError> errors) {
        // G-3 + G-4: TreasuryWithdrawalsAction
        if (action instanceof TreasuryWithdrawalsAction twa) {
            validateTreasuryWithdrawals(context, twa, proposalIndex, errors);
        }

        // G-5: UpdateCommittee member expiration > currentEpoch
        if (action instanceof UpdateCommittee uc) {
            validateUpdateCommittee(context, uc, proposalIndex, errors);
        }

        // G-6: prevGovActionId references exist
        GovActionId prevId = getPrevGovActionId(action);
        if (prevId != null) {
            ProposalsSlice proposals = context.getProposalsSlice();
            if (proposals != null) {
                if (!proposals.exists(prevId.getTransactionId(), prevId.getGovActionIndex())) {
                    errors.add(error("Proposal[" + proposalIndex + "]: prevGovActionId "
                            + prevId.getTransactionId() + "#" + prevId.getGovActionIndex()
                            + " does not exist"));
                }
            }
        }
    }

    private void validateTreasuryWithdrawals(LedgerContext context, TreasuryWithdrawalsAction twa,
                                             int proposalIndex, List<ValidationError> errors) {
        List<Withdrawal> withdrawals = twa.getWithdrawals();
        if (withdrawals == null || withdrawals.isEmpty()) return;

        int expectedNetworkInt = context.getNetworkId() != null
                ? (context.getNetworkId() == NetworkId.MAINNET ? 1 : 0)
                : -1;
        AccountsSlice accounts = context.getAccountsSlice();

        BigInteger totalSum = BigInteger.ZERO;

        for (int i = 0; i < withdrawals.size(); i++) {
            Withdrawal w = withdrawals.get(i);

            // G-4: amount > 0
            if (w.getCoin() != null && w.getCoin().signum() <= 0) {
                errors.add(error("Proposal[" + proposalIndex + "] TreasuryWithdrawal[" + i
                        + "]: amount must be > 0, got " + w.getCoin()));
            }

            if (w.getCoin() != null) {
                totalSum = totalSum.add(w.getCoin());
            }

            // G-3: network consistency
            if (expectedNetworkInt >= 0 && w.getRewardAddress() != null) {
                try {
                    Address addr = new Address(w.getRewardAddress());
                    if (addr.getNetwork() != null && addr.getNetwork().getNetworkId() != expectedNetworkInt) {
                        errors.add(error("Proposal[" + proposalIndex + "] TreasuryWithdrawal[" + i
                                + "]: address network " + addr.getNetwork().getNetworkId()
                                + " does not match expected " + expectedNetworkInt));
                    }
                } catch (Exception e) {
                    // Skip unparseable addresses
                }
            }

            // TreasuryWithdrawalReturnAccountsDoNotExist: destination account must exist
            if (accounts != null && w.getRewardAddress() != null) {
                try {
                    Address addr = new Address(w.getRewardAddress());
                    byte[] delegationHash = addr.getDelegationCredentialHash().orElse(null);
                    if (delegationHash != null) {
                        String hash = HexUtil.encodeHexString(delegationHash);
                        if (!accounts.isRegistered(hash)) {
                            errors.add(error("Proposal[" + proposalIndex + "] TreasuryWithdrawal[" + i
                                    + "]: destination account credential " + hash + " is not registered"));
                        }
                    }
                } catch (Exception e) {
                    // Skip unparseable addresses
                }
            }
        }

        // ZeroTreasuryWithdrawals (aggregate): total sum must be > 0
        if (totalSum.signum() <= 0) {
            errors.add(error("Proposal[" + proposalIndex
                    + "] TreasuryWithdrawals: aggregate withdrawal sum must be > 0, got " + totalSum));
        }
    }

    private void validateUpdateCommittee(LedgerContext context, UpdateCommittee uc,
                                         int proposalIndex, List<ValidationError> errors) {
        // G-5: new member expiration > currentEpoch
        long currentEpoch = context.getCurrentEpoch();
        if (currentEpoch > 0 && uc.getNewMembersAndTerms() != null) {
            for (Map.Entry<Credential, Integer> entry : uc.getNewMembersAndTerms().entrySet()) {
                int expiration = entry.getValue();
                if (expiration <= currentEpoch) {
                    String credHash = credHash(entry.getKey());
                    errors.add(error("Proposal[" + proposalIndex + "] UpdateCommittee: member "
                            + credHash + " expiration epoch " + expiration
                            + " is not greater than currentEpoch " + currentEpoch));
                }
            }
        }

        // ConflictingCommitteeUpdate: membersForRemoval ∩ newMembersAndTerms.keys == ∅
        Set<Credential> toRemove = uc.getMembersForRemoval();
        Map<Credential, Integer> toAdd = uc.getNewMembersAndTerms();
        if (toRemove != null && !toRemove.isEmpty() && toAdd != null && !toAdd.isEmpty()) {
            for (Credential removeCred : toRemove) {
                if (toAdd.containsKey(removeCred)) {
                    errors.add(error("Proposal[" + proposalIndex
                            + "] UpdateCommittee: credential " + credHash(removeCred)
                            + " is in both membersForRemoval and newMembersAndTerms"));
                }
            }
        }
    }

    /**
     * Extract prevGovActionId from governance actions that reference a previous action.
     */
    private GovActionId getPrevGovActionId(GovAction action) {
        if (action instanceof ParameterChangeAction a) return a.getPrevGovActionId();
        if (action instanceof HardForkInitiationAction a) return a.getPrevGovActionId();
        if (action instanceof NoConfidence a) return a.getPrevGovActionId();
        if (action instanceof UpdateCommittee a) return a.getPrevGovActionId();
        if (action instanceof NewConstitution a) return a.getPrevGovActionId();
        return null;
    }

    // ---- Voting Validation ----

    private void validateVoting(LedgerContext context, VotingProcedures votingProcedures,
                                List<ValidationError> errors) {
        Map<Voter, Map<GovActionId, VotingProcedure>> voting = votingProcedures.getVoting();

        for (Map.Entry<Voter, Map<GovActionId, VotingProcedure>> entry : voting.entrySet()) {
            Voter voter = entry.getKey();

            // Validate voter eligibility
            validateVoterEligibility(context, voter, errors);

            // G-7: vote targets (GovActionId) exist
            // DisallowedVoters: check voter type is allowed for the action type
            ProposalsSlice proposals = context.getProposalsSlice();
            if (proposals != null && entry.getValue() != null) {
                for (GovActionId actionId : entry.getValue().keySet()) {
                    if (!proposals.exists(actionId.getTransactionId(), actionId.getGovActionIndex())) {
                        errors.add(error("Vote: target governance action "
                                + actionId.getTransactionId() + "#" + actionId.getGovActionIndex()
                                + " does not exist"));
                    } else {
                        // DisallowedVoters: voter type must be permitted for this action type
                        validateVoterForActionType(voter, actionId, proposals, errors);
                    }
                }
            }
        }
    }

    private void validateVoterEligibility(LedgerContext context, Voter voter,
                                          List<ValidationError> errors) {
        if (voter == null || voter.getType() == null) return;
        String hash = credHash(voter.getCredential());
        if (hash == null) return;

        switch (voter.getType()) {
            // G-8: DRep voter IS registered
            case DREP_KEY_HASH:
            case DREP_SCRIPT_HASH: {
                DRepsSlice dreps = context.getDrepsSlice();
                if (dreps != null && !dreps.isRegistered(hash)) {
                    errors.add(error("Vote: DRep voter " + hash + " is not registered"));
                }
                break;
            }
            // G-9: Pool voter IS registered
            case STAKING_POOL_KEY_HASH: {
                PoolsSlice pools = context.getPoolsSlice();
                if (pools != null && !pools.isRegistered(hash)) {
                    errors.add(error("Vote: pool voter " + hash + " is not registered"));
                }
                break;
            }
            // G-10: Committee hot voter IS authorized
            case CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH:
            case CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH: {
                CommitteeSlice committee = context.getCommitteeSlice();
                if (committee != null) {
                    // Check if any cold credential has this hot credential authorized
                    // The voter's credential is the HOT credential
                    boolean isAuthorized = false;
                    // CommitteeSlice checks by cold credential, but voter uses hot credential
                    // We need to check if any member has this hot credential
                    // For now, we check that the hot credential is associated with a member
                    // This requires iterating or a reverse lookup — we check isMember as a proxy
                    // Note: CommitteeSlice.getHotCredential takes a cold credential hash
                    // The voter presents a hot credential — we can't directly validate without
                    // a reverse lookup. Skip for now if no reverse lookup is available.
                    // TODO: Consider adding a reverse lookup to CommitteeSlice
                }
                break;
            }
        }
    }

    /**
     * DisallowedVoters: Validates that a voter type is permitted to vote on the given action type.
     *
     * <pre>
     * Action Type            | CC  | DRep | SPO
     * -----------------------|-----|------|----
     * NoConfidence           | —   | Yes  | Yes
     * UpdateCommittee        | —   | Yes  | Yes
     * NewConstitution        | Yes | Yes  | —
     * HardForkInitiation     | Yes | Yes  | Yes
     * ParameterChange        | Yes | Yes  | —
     * TreasuryWithdrawal     | Yes | Yes  | —
     * InfoAction             | Yes | Yes  | Yes
     * </pre>
     */
    private void validateVoterForActionType(Voter voter, GovActionId actionId,
                                            ProposalsSlice proposals, List<ValidationError> errors) {
        if (voter == null || voter.getType() == null) return;

        String actionType = proposals.getActionType(actionId.getTransactionId(), actionId.getGovActionIndex());
        if (actionType == null) return;

        boolean isCC = voter.getType() == VoterType.CONSTITUTIONAL_COMMITTEE_HOT_KEY_HASH
                || voter.getType() == VoterType.CONSTITUTIONAL_COMMITTEE_HOT_SCRIPT_HASH;
        boolean isDRep = voter.getType() == VoterType.DREP_KEY_HASH
                || voter.getType() == VoterType.DREP_SCRIPT_HASH;
        boolean isSPO = voter.getType() == VoterType.STAKING_POOL_KEY_HASH;

        boolean allowed = switch (actionType.toUpperCase()) {
            case "NO_CONFIDENCE", "NOCONFIDENCE" -> isDRep || isSPO;
            case "UPDATE_COMMITTEE", "UPDATECOMMITTEE" -> isDRep || isSPO;
            case "NEW_CONSTITUTION", "NEWCONSTITUTION" -> isCC || isDRep;
            case "HARD_FORK_INITIATION", "HARDFORKINITIATION", "HARD_FORK_INITIATION_ACTION" -> isCC || isDRep || isSPO;
            case "PARAMETER_CHANGE", "PARAMETERCHANGE", "PARAMETER_CHANGE_ACTION" -> isCC || isDRep;
            case "TREASURY_WITHDRAWAL", "TREASURYWITHDRAWAL", "TREASURY_WITHDRAWALS_ACTION" -> isCC || isDRep;
            case "INFO_ACTION", "INFOACTION", "INFO" -> isCC || isDRep || isSPO;
            default -> true; // Unknown action type — don't block
        };

        if (!allowed) {
            String voterTypeStr = isCC ? "CC" : (isDRep ? "DRep" : "SPO");
            errors.add(error("Vote: " + voterTypeStr + " voter is not allowed to vote on action type "
                    + actionType + " (" + actionId.getTransactionId() + "#" + actionId.getGovActionIndex() + ")"));
        }
    }

    // ---- Helper methods ----

    private void validateRewardAccountNetwork(String rewardAddress, LedgerContext context,
                                              int proposalIndex, List<ValidationError> errors) {
        try {
            Address addr = new Address(rewardAddress);
            int expectedNetworkInt = context.getNetworkId() == NetworkId.MAINNET ? 1 : 0;
            if (addr.getNetwork() != null && addr.getNetwork().getNetworkId() != expectedNetworkInt) {
                errors.add(error("Proposal[" + proposalIndex + "]: reward account network "
                        + addr.getNetwork().getNetworkId()
                        + " does not match expected " + expectedNetworkInt));
            }
        } catch (Exception e) {
            // Skip unparseable addresses
        }
    }

    private String credHash(Credential cred) {
        if (cred == null || cred.getBytes() == null) return null;
        return HexUtil.encodeHexString(cred.getBytes());
    }

    private ValidationError error(String message) {
        return ValidationError.builder()
                .rule(RULE_NAME)
                .message(message)
                .phase(ValidationError.Phase.PHASE_1)
                .build();
    }
}
