package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Processor for governance-related intentions.
 * Handles variable resolution and applies governance operations to transactions.
 */
@Slf4j
public class GovernanceIntentProcessor {

    /**
     * Process a governance intention and apply it to the transaction.
     */
    public static void process(TxIntent intention, AbstractTx<?> tx, Map<String, Object> variables) {
        if (!(tx instanceof Tx)) {
            throw new UnsupportedOperationException("Governance operations are only supported on Tx instances");
        }

        String intentionType = intention.getType();
        switch (intentionType) {
            case "drep_registration":
                processDRepRegistrationIntention((DRepRegistrationIntent) intention, (Tx) tx, variables);
                break;
            case "drep_deregistration":
                processDRepDeregistrationIntention((DRepDeregistrationIntent) intention, (Tx) tx, variables);
                break;
            case "drep_update":
                processDRepUpdateIntention((DRepUpdateIntent) intention, (Tx) tx, variables);
                break;
            case "governance_proposal":
                processGovernanceProposalIntention((GovernanceProposalIntent) intention, (Tx) tx, variables);
                break;
            case "voting":
                processVotingIntention((VotingIntent) intention, (Tx) tx, variables);
                break;
            case "voting_delegation":
                processVotingDelegationIntention((VotingDelegationIntent) intention, (Tx) tx, variables);
                break;
            default:
                throw new TxBuildException("Unknown governance intention type: " + intentionType);
        }
    }

    /**
     * Process DRep registration intention.
     */
    private static void processDRepRegistrationIntention(DRepRegistrationIntent intention, Tx tx, Map<String, Object> variables) {
        try {
            // Get or deserialize credential
            Credential drepCredential = intention.getDrepCredential();
            if (drepCredential == null) {
                String credentialHex = resolveVariable(intention.getDrepCredentialHex(), variables);
                if (credentialHex != null && !credentialHex.isEmpty()) {
                    byte[] credentialBytes = HexUtil.decodeHexString(credentialHex);
                    // We'll use the bytes as-is since we don't know the type
                    // The GovTx methods will handle the credential properly
                    drepCredential = Credential.fromKey(credentialBytes);
                }
            }

            // Get or build anchor
            Anchor anchor = intention.getAnchor();
            if (anchor == null && intention.getAnchorUrl() != null) {
                String url = resolveVariable(intention.getAnchorUrl(), variables);
                String hashHex = resolveVariable(intention.getAnchorHash(), variables);
                if (url != null && !url.isEmpty()) {
                    byte[] hash = null;
                    if (hashHex != null && !hashHex.isEmpty()) {
                        hash = HexUtil.decodeHexString(hashHex);
                    }
                    anchor = new Anchor(url, hash);
                }
            }

            if (drepCredential == null) {
                throw new TxBuildException("DRep credential is required for registration");
            }

            // Use Tx governance methods to register DRep
            tx.registerDRep(drepCredential, anchor);

        } catch (Exception e) {
            throw new TxBuildException("Failed to process DRep registration intention", e);
        }
    }

    /**
     * Process DRep deregistration intention.
     */
    private static void processDRepDeregistrationIntention(DRepDeregistrationIntent intention, Tx tx, Map<String, Object> variables) {
        try {
            // Get or deserialize credential
            Credential drepCredential = intention.getDrepCredential();
            if (drepCredential == null) {
                String credentialHex = resolveVariable(intention.getDrepCredentialHex(), variables);
                if (credentialHex != null && !credentialHex.isEmpty()) {
                    byte[] credentialBytes = HexUtil.decodeHexString(credentialHex);
                    // We'll use the bytes as-is since we don't know the type
                    // The GovTx methods will handle the credential properly
                    drepCredential = Credential.fromKey(credentialBytes);
                }
            }

            if (drepCredential == null) {
                throw new TxBuildException("DRep credential is required for deregistration");
            }

            // Resolve variables
            String refundAddress = resolveVariable(intention.getRefundAddress(), variables);

            // Use Tx governance methods to unregister DRep
            tx.unregisterDRep(drepCredential, refundAddress);

        } catch (Exception e) {
            throw new TxBuildException("Failed to process DRep deregistration intention", e);
        }
    }

    /**
     * Process DRep update intention.
     */
    private static void processDRepUpdateIntention(DRepUpdateIntent intention, Tx tx, Map<String, Object> variables) {
        try {
            // Get or deserialize credential
            Credential drepCredential = intention.getDrepCredential();
            if (drepCredential == null) {
                String credentialHex = resolveVariable(intention.getDrepCredentialHex(), variables);
                if (credentialHex != null && !credentialHex.isEmpty()) {
                    byte[] credentialBytes = HexUtil.decodeHexString(credentialHex);
                    // We'll use the bytes as-is since we don't know the type
                    // The GovTx methods will handle the credential properly
                    drepCredential = Credential.fromKey(credentialBytes);
                }
            }

            // Get or build anchor
            Anchor anchor = intention.getAnchor();
            if (anchor == null && intention.getAnchorUrl() != null) {
                String url = resolveVariable(intention.getAnchorUrl(), variables);
                String hashHex = resolveVariable(intention.getAnchorHash(), variables);
                if (url != null && !url.isEmpty()) {
                    byte[] hash = null;
                    if (hashHex != null && !hashHex.isEmpty()) {
                        hash = HexUtil.decodeHexString(hashHex);
                    }
                    anchor = new Anchor(url, hash);
                }
            }

            if (drepCredential == null) {
                throw new TxBuildException("DRep credential is required for update");
            }

            // Use Tx governance methods to update DRep
            tx.updateDRep(drepCredential, anchor);

        } catch (Exception e) {
            throw new TxBuildException("Failed to process DRep update intention", e);
        }
    }

    /**
     * Process governance proposal intention.
     */
    private static void processGovernanceProposalIntention(GovernanceProposalIntent intention, Tx tx, Map<String, Object> variables) {
        try {
            // Get or deserialize governance action
            GovAction govAction = intention.getGovAction();
            if (govAction == null) {
                String govActionHex = resolveVariable(intention.getGovActionHex(), variables);
                if (govActionHex != null && !govActionHex.isEmpty()) {
                    byte[] govActionBytes = HexUtil.decodeHexString(govActionHex);
                    Array govActionArray = (Array) CborSerializationUtil.deserialize(govActionBytes);
                    govAction = GovAction.deserialize(govActionArray);
                }
            }

            // Get or build anchor
            Anchor anchor = intention.getAnchor();
            if (anchor == null && intention.getAnchorUrl() != null) {
                String url = resolveVariable(intention.getAnchorUrl(), variables);
                String hashHex = resolveVariable(intention.getAnchorHash(), variables);
                if (url != null && !url.isEmpty()) {
                    byte[] hash = null;
                    if (hashHex != null && !hashHex.isEmpty()) {
                        hash = HexUtil.decodeHexString(hashHex);
                    }
                    anchor = new Anchor(url, hash);
                }
            }

            if (govAction == null) {
                throw new TxBuildException("Governance action is required for proposal");
            }

            // Resolve return address
            String returnAddress = resolveVariable(intention.getReturnAddress(), variables);
            if (returnAddress == null || returnAddress.isEmpty()) {
                throw new TxBuildException("Return address is required for governance proposal");
            }

            // Use Tx governance methods to create proposal
            tx.createProposal(govAction, returnAddress, anchor);

        } catch (Exception e) {
            throw new TxBuildException("Failed to process governance proposal intention", e);
        }
    }

    /**
     * Process voting intention.
     */
    private static void processVotingIntention(VotingIntent intention, Tx tx, Map<String, Object> variables) {
        try {
            // Get or deserialize voter
            Voter voter = intention.getVoter();
            if (voter == null) {
                String voterHex = resolveVariable(intention.getVoterHex(), variables);
                if (voterHex != null && !voterHex.isEmpty()) {
                    byte[] voterBytes = HexUtil.decodeHexString(voterHex);
                    Array voterArray = (Array) CborSerializationUtil.deserialize(voterBytes);
                    voter = Voter.deserialize(voterArray);
                }
            }

            // Get or build governance action ID
            GovActionId govActionId = intention.getGovActionId();
            if (govActionId == null) {
                String txHash = resolveVariable(intention.getGovActionTxHash(), variables);
                Integer index = intention.getGovActionIndex();
                if (txHash != null && !txHash.isEmpty() && index != null) {
                    govActionId = new GovActionId(txHash, index);
                }
            }

            // Get or parse vote
            Vote vote = intention.getVote();
            if (vote == null) {
                String voteDecision = resolveVariable(intention.getVoteDecision(), variables);
                if (voteDecision != null && !voteDecision.isEmpty()) {
                    vote = parseVote(voteDecision);
                }
            }

            // Get or build anchor
            Anchor anchor = intention.getAnchor();
            if (anchor == null && intention.getAnchorUrl() != null) {
                String url = resolveVariable(intention.getAnchorUrl(), variables);
                String hashHex = resolveVariable(intention.getAnchorHash(), variables);
                if (url != null && !url.isEmpty()) {
                    byte[] hash = null;
                    if (hashHex != null && !hashHex.isEmpty()) {
                        hash = HexUtil.decodeHexString(hashHex);
                    }
                    anchor = new Anchor(url, hash);
                }
            }

            if (voter == null || govActionId == null || vote == null) {
                throw new TxBuildException("Voter, governance action ID, and vote are required");
            }

            // Use Tx governance methods to create vote
            tx.createVote(voter, govActionId, vote, anchor);

        } catch (Exception e) {
            throw new TxBuildException("Failed to process voting intention", e);
        }
    }

    /**
     * Process voting delegation intention.
     */
    private static void processVotingDelegationIntention(VotingDelegationIntent intention, Tx tx, Map<String, Object> variables) {
        try {
            // Get or parse address
            Address address = intention.getAddress();
            if (address == null) {
                String addressStr = resolveVariable(intention.getAddressStr(), variables);
                if (addressStr != null && !addressStr.isEmpty()) {
                    address = new Address(addressStr);
                }
            }

            // Get or build DRep
            DRep drep = intention.getDrep();
            if (drep == null) {
                String drepHex = resolveVariable(intention.getDrepHex(), variables);
                if (drepHex != null && !drepHex.isEmpty()) {
                    byte[] drepBytes = HexUtil.decodeHexString(drepHex);
                    DataItem drepDI = CborSerializationUtil.deserialize(drepBytes);
                    drep = DRep.deserialize(drepDI);
                } else {
                    // Build from type and hash
                    String drepType = resolveVariable(intention.getDrepType(), variables);
                    String drepHash = resolveVariable(intention.getDrepHash(), variables);
                    drep = buildDRepFromTypeAndHash(drepType, drepHash);
                }
            }

            if (address == null || drep == null) {
                throw new TxBuildException("Address and DRep are required for voting delegation");
            }

            // Use Tx governance methods to delegate voting power
            tx.delegateVotingPowerTo(address, drep);

        } catch (Exception e) {
            throw new TxBuildException("Failed to process voting delegation intention", e);
        }
    }

    /**
     * Parse vote decision string into Vote enum.
     */
    private static Vote parseVote(String voteDecision) {
        if (voteDecision == null) {
            return null;
        }

        switch (voteDecision.toLowerCase()) {
            case "yes":
                return Vote.YES;
            case "no":
                return Vote.NO;
            case "abstain":
                return Vote.ABSTAIN;
            default:
                throw new TxBuildException("Invalid vote decision: " + voteDecision + ". Must be Yes, No, or Abstain");
        }
    }

    /**
     * Build DRep from type and hash.
     */
    private static DRep buildDRepFromTypeAndHash(String drepType, String drepHash) {
        if (drepType == null) {
            return null;
        }

        switch (drepType.toLowerCase()) {
            case "key_hash":
            case "addr_keyhash":
                if (drepHash == null || drepHash.isEmpty()) {
                    throw new TxBuildException("DRep hash is required for key_hash type");
                }
                return DRep.addrKeyHash(HexUtil.decodeHexString(drepHash));
            case "script_hash":
            case "scripthash":
                if (drepHash == null || drepHash.isEmpty()) {
                    throw new TxBuildException("DRep hash is required for script_hash type");
                }
                return DRep.scriptHash(HexUtil.decodeHexString(drepHash));
            case "abstain":
                return DRep.abstain();
            case "no_confidence":
                return DRep.noConfidence();
            default:
                throw new TxBuildException("Invalid DRep type: " + drepType + ". Must be key_hash, script_hash, abstain, or no_confidence");
        }
    }

    /**
     * Helper method to resolve a variable value.
     */
    private static String resolveVariable(String value, Map<String, Object> variables) {
        if (value != null && value.startsWith("${") && value.endsWith("}") && variables != null) {
            String varName = value.substring(2, value.length() - 1);
            Object varValue = variables.get(varName);
            return varValue != null ? varValue.toString() : value;
        }
        return value;
    }
}
