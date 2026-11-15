package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.VoterUtil;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
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

import java.math.BigInteger;
import java.util.Optional;

/**
 * Intention for voting operations.
 * Maps to GovTx.createVote(Voter, GovActionId, Vote, Anchor, PlutusData) operations.
 * <p>
 * The voting redeemer index is the voter's position in ledger order ({@link VoterUtil}), computed from the voters
 * in the same Tx. Limitation: all voting intents should be in one Tx. When several Txs are composed into one
 * transaction, a voter added by a later Tx (script or key) can shift the index already computed for an earlier Tx.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VotingIntent implements TxIntent {

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
        if (voteDecision != null) {
            if (!voteDecision.equalsIgnoreCase("Yes") &&
                !voteDecision.equalsIgnoreCase("No") &&
                !voteDecision.equalsIgnoreCase("Abstain")) {
                throw new IllegalStateException("Vote decision must be Yes, No, or Abstain");
            }
        }

        // Validate hex formats
        if (voterHex != null && !voterHex.isEmpty()) {
            try {
                HexUtil.decodeHexString(voterHex);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid voter hex format: " + voterHex);
            }
        }

        if (anchorHash != null && !anchorHash.isEmpty()) {
            try {
                HexUtil.decodeHexString(anchorHash);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid anchor hash format: " + anchorHash);
            }
        }

        if (govActionIndex != null && govActionIndex < 0) {
            throw new IllegalStateException("Governance action index cannot be negative");
        }

        if (redeemerHex != null && !redeemerHex.isEmpty()) {
            try { HexUtil.decodeHexString(redeemerHex); } catch (Exception e) {
                throw new IllegalStateException("Invalid redeemer hex format");
            }
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedVoterHex = VariableResolver.resolve(voterHex, variables);
        String resolvedGovActionTxHash = VariableResolver.resolve(govActionTxHash, variables);
        String resolvedVoteDecision = VariableResolver.resolve(voteDecision, variables);
        String resolvedAnchorUrl = VariableResolver.resolve(anchorUrl, variables);
        String resolvedAnchorHash = VariableResolver.resolve(anchorHash, variables);
        String resolvedRedeemerHex = VariableResolver.resolve(redeemerHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedVoterHex, voterHex) || !java.util.Objects.equals(resolvedGovActionTxHash, govActionTxHash) || !java.util.Objects.equals(resolvedVoteDecision, voteDecision) || !java.util.Objects.equals(resolvedAnchorUrl, anchorUrl) || !java.util.Objects.equals(resolvedAnchorHash, anchorHash) || !java.util.Objects.equals(resolvedRedeemerHex, redeemerHex)) {
            return this.toBuilder()
                .voterHex(resolvedVoterHex)
                .govActionTxHash(resolvedGovActionTxHash)
                .voteDecision(resolvedVoteDecision)
                .anchorUrl(resolvedAnchorUrl)
                .anchorHash(resolvedAnchorHash)
                .redeemerHex(resolvedRedeemerHex)
                .build();
        }

        return this;
    }

    // Factory methods for different use cases

    /**
     * Create VotingIntention from runtime objects.
     */
    public static VotingIntent vote(Voter voter, GovActionId govActionId, Vote vote) {
        return VotingIntent.builder()
            .voter(voter)
            .govActionId(govActionId)
            .vote(vote)
            .build();
    }

    /**
     * Create VotingIntention with anchor.
     */
    public static VotingIntent vote(Voter voter, GovActionId govActionId, Vote vote, Anchor anchor) {
        return VotingIntent.builder()
            .voter(voter)
            .govActionId(govActionId)
            .vote(vote)
            .anchor(anchor)
            .build();
    }

    /**
     * Create VotingIntention from serializable values.
     */
    public static VotingIntent vote(String voterHex, String govActionTxHash, Integer govActionIndex, String voteDecision) {
        return VotingIntent.builder()
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
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            // Context-specific check only
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank())
                throw new TxBuildException("From address is required for voting");

            // Add voting procedure here so that every voter is in the body before apply() computes redeemer indices
            try {
                if (txn.getBody().getVotingProcedures() == null)
                    txn.getBody().setVotingProcedures(new VotingProcedures());

                txn.getBody().getVotingProcedures().add(resolveVoter(), resolveGovActionId(),
                        new VotingProcedure(resolveVote(), resolveAnchor()));
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply VotingIntention: " + e.getMessage(), e);
            }
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            PlutusData rdData;
            Voter _voter;
            try {
                rdData = resolveRedeemer();
                if (rdData == null)
                    return;
                _voter = resolveVoter();
            } catch (Exception e) {
                throw new TxBuildException("Failed to apply VotingIntention: " + e.getMessage(), e);
            }

            // Voting redeemer index is the voter's position in ledger order, not insertion order
            int index = VoterUtil.getIndexByVoter(VoterUtil.getSortedVoters(txn.getBody().getVotingProcedures()), _voter);
            if (index < 0)
                throw new TxBuildException("Voter not found in voting procedures: " + _voter);

            if (txn.getWitnessSet() == null)
                txn.setWitnessSet(new TransactionWitnessSet());

            // A voter voting on several governance actions has one entry in voting procedures, so one redeemer
            BigInteger redeemerIndex = BigInteger.valueOf(index);
            Optional<Redeemer> existing = txn.getWitnessSet().getRedeemers().stream()
                    .filter(rd -> rd.getTag() == RedeemerTag.Voting && redeemerIndex.equals(rd.getIndex()))
                    .findFirst();
            if (existing.isPresent()) {
                if (!sameData(existing.get().getData(), rdData))
                    throw new TxBuildException("Conflicting voting redeemers for voter: " + _voter);
                return;
            }

            Redeemer rd = Redeemer.builder()
                    .tag(RedeemerTag.Voting)
                    .data(rdData)
                    .index(redeemerIndex)
                    .exUnits(ExUnits.builder()
                            .mem(BigInteger.valueOf(10000))
                            .steps(BigInteger.valueOf(1000))
                            .build())
                    .build();
            txn.getWitnessSet().getRedeemers().add(rd);
        };
    }

    // Resolve from runtime objects first, then from serialized fields. These never write back to the intent,
    // so a reused intent resolves fresh values.

    private Voter resolveVoter() throws Exception {
        if (voter != null)
            return voter;
        if (voterHex != null && !voterHex.isEmpty()) {
            Array voterArray = (Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(voterHex));
            return Voter.deserialize(voterArray);
        }
        return null;
    }

    private GovActionId resolveGovActionId() {
        if (govActionId != null)
            return govActionId;
        if (govActionTxHash != null && govActionIndex != null)
            return new GovActionId(govActionTxHash, govActionIndex);
        return null;
    }

    private Vote resolveVote() {
        if (vote != null)
            return vote;
        if (voteDecision != null)
            return Vote.valueOf(voteDecision.toUpperCase());
        return null;
    }

    private Anchor resolveAnchor() {
        if (anchor != null)
            return anchor;
        if (anchorUrl != null) {
            byte[] hash = (anchorHash != null && !anchorHash.isEmpty()) ? HexUtil.decodeHexString(anchorHash) : null;
            return new Anchor(anchorUrl, hash);
        }
        return null;
    }

    private PlutusData resolveRedeemer() throws Exception {
        if (redeemer != null)
            return redeemer;
        if (redeemerHex != null && !redeemerHex.isEmpty())
            return PlutusData.deserialize(HexUtil.decodeHexString(redeemerHex));
        return null;
    }

    private static boolean sameData(PlutusData a, PlutusData b) {
        try {
            return a.serializeToHex().equals(b.serializeToHex());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean hasRedeemer() {
        return redeemer != null || (redeemerHex != null && !redeemerHex.isEmpty());
    }
}
