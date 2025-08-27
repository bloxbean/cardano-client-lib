package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for creating a governance vote.
 * Captures the voter, governance action ID, vote choice, and optional metadata anchor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("governance_vote")
public class GovernanceVoteIntention implements TxIntention {
    
    @JsonProperty("voter")
    private Voter voter;
    
    @JsonProperty("gov_action_id")
    private GovActionId govActionId;
    
    @JsonProperty("vote")
    private Vote vote;
    
    @JsonProperty("anchor")
    private Anchor anchor; // Optional metadata anchor
    
    public GovernanceVoteIntention(Voter voter, GovActionId govActionId, Vote vote) {
        this.voter = voter;
        this.govActionId = govActionId;
        this.vote = vote;
        this.anchor = null;
    }
    
    @Override
    public String getType() {
        return "governance_vote";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Note: Voter, GovActionId, and Vote objects typically don't need variable resolution,
        // but anchor could potentially contain variable references in the future
        
        // Apply governance vote directly to Tx
        if (anchor != null) {
            tx.createVote(voter, govActionId, vote, anchor);
        } else {
            tx.createVote(voter, govActionId, vote);
        }
    }
    
    /**
     * Factory method for creating governance vote intention.
     */
    public static GovernanceVoteIntention vote(Voter voter, GovActionId govActionId, Vote vote) {
        return new GovernanceVoteIntention(voter, govActionId, vote);
    }
    
    /**
     * Factory method for creating governance vote intention with anchor.
     */
    public static GovernanceVoteIntention vote(Voter voter, GovActionId govActionId, Vote vote, Anchor anchor) {
        return new GovernanceVoteIntention(voter, govActionId, vote, anchor);
    }
}