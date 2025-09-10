package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedure;
import com.bloxbean.cardano.client.transaction.spec.governance.VotingProcedures;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.util.HexUtil;
import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Intention for voting operations.
 * Maps to GovTx.createVote(Voter, GovActionId, Vote, Anchor, PlutusData) operations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VotingIntention implements TxIntention {

    // Runtime fields - original objects preserved

    /**
     * Voter for the vote (runtime object).
     */
    @JsonIgnore
    private Voter voter;

    /**
     * Governance action ID to vote on (runtime object).
     */
    @JsonIgnore
    private GovActionId govActionId;

    /**
     * Vote decision (runtime object).
     */
    @JsonIgnore
    private Vote vote;

    /**
     * Anchor information for the vote (runtime object).
     */
    @JsonIgnore
    private Anchor anchor;

    // Serialization fields - computed from runtime objects or set during deserialization

    /**
     * Voter as CBOR hex for serialization.
     */
    @JsonProperty("voter_hex")
    private String voterHex;

    /**
     * Transaction hash of the governance action.
     */
    @JsonProperty("gov_action_tx_hash")
    private String govActionTxHash;

    /**
     * Index of the governance action in the transaction.
     */
    @JsonProperty("gov_action_index")
    private Integer govActionIndex;

    /**
     * Vote decision as string (Yes, No, Abstain).
     */
    @JsonProperty("vote")
    private String voteDecision;

    /**
     * Anchor URL for serialization.
     */
    @JsonProperty("anchor_url")
    private String anchorUrl;

    /**
     * Anchor hash as hex for serialization.
     */
    @JsonProperty("anchor_hash")
    private String anchorHash;

    // Optional redeemer for voting
    @com.fasterxml.jackson.annotation.JsonIgnore
    private PlutusData redeemer;

    @JsonProperty("redeemer_hex")
    private String redeemerHex;

    @JsonProperty("redeemer_hex")
    public String getRedeemerHex() {
        if (redeemer != null) {
            try { return redeemer.serializeToHex(); } catch (Exception e) { /* ignore */ }
        }
        return redeemerHex;
    }

    /**
     * Get voter hex for serialization.
     */
    @JsonProperty("voter_hex")
    public String getVoterHex() {
        if (voter != null) {
            try {
                return HexUtil.encodeHexString(CborSerializationUtil.serialize(voter.serialize()));
            } catch (Exception e) {
                // Log error and return stored hex
            }
        }
        return voterHex;
    }

    /**
     * Get governance action transaction hash for serialization.
     */
    @JsonProperty("gov_action_tx_hash")
    public String getGovActionTxHash() {
        if (govActionId != null) {
            return govActionId.getTransactionId();
        }
        return govActionTxHash;
    }

    /**
     * Get governance action index for serialization.
     */
    @JsonProperty("gov_action_index")
    public Integer getGovActionIndex() {
        if (govActionId != null) {
            return govActionId.getGovActionIndex();
        }
        return govActionIndex;
    }

    /**
     * Get vote decision for serialization.
     */
    @JsonProperty("vote")
    public String getVoteDecision() {
        if (vote != null) {
            return vote.toString();
        }
        return voteDecision;
    }

    /**
     * Get anchor URL for serialization.
     */
    @JsonProperty("anchor_url")
    public String getAnchorUrl() {
        if (anchor != null) {
            return anchor.getAnchorUrl();
        }
        return anchorUrl;
    }

    /**
     * Get anchor hash for serialization.
     */
    @JsonProperty("anchor_hash")
    public String getAnchorHash() {
        if (anchor != null && anchor.getAnchorDataHash() != null) {
            return HexUtil.encodeHexString(anchor.getAnchorDataHash());
        }
        return anchorHash;
    }

    @Override
    public String getType() {
        return "voting";
    }

    @Override
    public void validate() {
        // Check voter
        if (voter == null && (voterHex == null || voterHex.isEmpty())) {
            throw new IllegalStateException("Voter is required for voting");
        }

        // Check governance action ID
        if (govActionId == null &&
            (govActionTxHash == null || govActionTxHash.isEmpty() || govActionIndex == null)) {
            throw new IllegalStateException("Governance action ID is required for voting");
        }

        // Check vote
        if (vote == null && (voteDecision == null || voteDecision.isEmpty())) {
            throw new IllegalStateException("Vote decision is required");
        }

        // Validate vote decision format
        if (voteDecision != null && !voteDecision.startsWith("${")) {
            if (!voteDecision.equalsIgnoreCase("Yes") &&
                !voteDecision.equalsIgnoreCase("No") &&
                !voteDecision.equalsIgnoreCase("Abstain")) {
                throw new IllegalStateException("Vote decision must be Yes, No, or Abstain");
            }
        }

        // Validate hex formats
        if (voterHex != null && !voterHex.isEmpty() && !voterHex.startsWith("${")) {
            try {
                HexUtil.decodeHexString(voterHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid voter hex format: " + voterHex);
            }
        }

        if (anchorHash != null && !anchorHash.isEmpty() && !anchorHash.startsWith("${")) {
            try {
                HexUtil.decodeHexString(anchorHash);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid anchor hash format: " + anchorHash);
            }
        }

        if (govActionIndex != null && govActionIndex < 0) {
            throw new IllegalStateException("Governance action index cannot be negative");
        }

        if (redeemerHex != null && !redeemerHex.isEmpty() && !redeemerHex.startsWith("${")) {
            try { HexUtil.decodeHexString(redeemerHex); } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    // Factory methods for different use cases

    /**
     * Create VotingIntention from runtime objects.
     */
    public static VotingIntention vote(Voter voter, GovActionId govActionId, Vote vote) {
        return VotingIntention.builder()
            .voter(voter)
            .govActionId(govActionId)
            .vote(vote)
            .build();
    }

    /**
     * Create VotingIntention with anchor.
     */
    public static VotingIntention vote(Voter voter, GovActionId govActionId, Vote vote, Anchor anchor) {
        return VotingIntention.builder()
            .voter(voter)
            .govActionId(govActionId)
            .vote(vote)
            .anchor(anchor)
            .build();
    }

    /**
     * Create VotingIntention from serializable values.
     */
    public static VotingIntention vote(String voterHex, String govActionTxHash, Integer govActionIndex, String voteDecision) {
        return VotingIntention.builder()
            .voterHex(voterHex)
            .govActionTxHash(govActionTxHash)
            .govActionIndex(govActionIndex)
            .voteDecision(voteDecision)
            .build();
    }

    // Utility methods

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return voter != null || govActionId != null || vote != null || anchor != null;
    }

    /**
     * Check if this intention needs deserialization from stored data.
     */
    @JsonIgnore
    public boolean needsDeserialization() {
        return !hasRuntimeObjects() &&
               (voterHex != null && !voterHex.isEmpty()) &&
               (govActionTxHash != null && !govActionTxHash.isEmpty());
    }

    /**
     * Check if anchor information is available.
     */
    @JsonIgnore
    public boolean hasAnchor() {
        return anchor != null ||
               (anchorUrl != null && !anchorUrl.isEmpty());
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank())
            throw new TxBuildException("From address is required for voting");
        
        // Use helper to create smart dummy output that merges with existing outputs
        return DepositHelper.createDummyOutputBuilder(from, ADAConversionUtil.adaToLovelace(1));
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank())
                throw new TxBuildException("From address is required for voting");
            if (voter == null && (voterHex == null || voterHex.isEmpty()))
                throw new TxBuildException("Voter is required for voting");
            if (govActionId == null && (govActionTxHash == null || govActionTxHash.isEmpty() || govActionIndex == null))
                throw new TxBuildException("Governance action id is required for voting");
            if (vote == null && (voteDecision == null || voteDecision.isEmpty()))
                throw new TxBuildException("Vote decision is required for voting");
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                Voter _voter = voter;
                if (_voter == null && voterHex != null && !voterHex.isEmpty()) {
                    Array voterArray = (Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(voterHex));
                    _voter = Voter.deserialize(voterArray);
                }

                GovActionId _gaid = govActionId;
                if (_gaid == null && govActionTxHash != null && govActionIndex != null) {
                    _gaid = new GovActionId(govActionTxHash, govActionIndex);
                }

                Vote _vote = vote;
                if (_vote == null && voteDecision != null) {
                    _vote = Vote.valueOf(voteDecision.toUpperCase());
                }

                Anchor anch = anchor;
                if (anch == null && anchorUrl != null) {
                    byte[] hash = (anchorHash != null && !anchorHash.isEmpty()) ? HexUtil.decodeHexString(anchorHash) : null;
                    anch = new Anchor(anchorUrl, hash);
                }

                if (txn.getBody().getVotingProcedures() == null)
                    txn.getBody().setVotingProcedures(new VotingProcedures());

                txn.getBody().getVotingProcedures().add(_voter, _gaid, new VotingProcedure(_vote, anch));

                // Add voting redeemer if provided
                PlutusData rdData = redeemer;
                if (rdData == null && redeemerHex != null && !redeemerHex.isEmpty()) {
                    rdData = PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));
                }
                if (rdData != null) {
                    if (txn.getWitnessSet() == null)
                        txn.setWitnessSet(new com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet());
                    int idx = 0; // approximation
                    Redeemer rd = Redeemer.builder()
                            .tag(RedeemerTag.Voting)
                            .data(rdData)
                            .index(java.math.BigInteger.valueOf(idx))
                            .exUnits(ExUnits.builder()
                                    .mem(java.math.BigInteger.valueOf(10000))
                                    .steps(java.math.BigInteger.valueOf(1000))
                                    .build())
                            .build();
                    txn.getWitnessSet().getRedeemers().add(rd);
                }
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply VotingIntention: " + e.getMessage(), e);
            }
        };
    }
}
