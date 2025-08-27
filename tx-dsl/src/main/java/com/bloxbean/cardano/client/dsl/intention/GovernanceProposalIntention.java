package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.InfoAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.ParameterChangeAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.HardForkInitiationAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.TreasuryWithdrawalsAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.NoConfidence;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.UpdateCommittee;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.NewConstitution;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for creating a governance action proposal.
 * Captures the governance action, reward account, and optional metadata anchor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("governance_proposal")
public class GovernanceProposalIntention implements TxIntention {
    
    @JsonProperty("gov_action")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = InfoAction.class, name = "info"),
        @JsonSubTypes.Type(value = ParameterChangeAction.class, name = "parameter_change"),
        @JsonSubTypes.Type(value = HardForkInitiationAction.class, name = "hard_fork"),
        @JsonSubTypes.Type(value = TreasuryWithdrawalsAction.class, name = "treasury_withdrawals"),
        @JsonSubTypes.Type(value = NoConfidence.class, name = "no_confidence"),
        @JsonSubTypes.Type(value = UpdateCommittee.class, name = "update_committee"),
        @JsonSubTypes.Type(value = NewConstitution.class, name = "new_constitution")
    })
    private GovAction govAction;
    
    @JsonProperty("reward_account")
    private String rewardAccount;
    
    @JsonProperty("anchor")
    private Anchor anchor; // Optional metadata anchor
    
    public GovernanceProposalIntention(GovAction govAction, String rewardAccount) {
        this.govAction = govAction;
        this.rewardAccount = rewardAccount;
        this.anchor = null;
    }
    
    @Override
    public String getType() {
        return "governance_proposal";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve reward account from variables if it's a variable
        String resolvedRewardAccount = IntentionHelper.resolveVariable(rewardAccount, variables);
        
        // Apply governance proposal directly to Tx
        tx.createProposal(govAction, resolvedRewardAccount, anchor);
    }
    
    /**
     * Factory method for creating governance proposal intention.
     */
    public static GovernanceProposalIntention propose(GovAction govAction, String rewardAccount) {
        return new GovernanceProposalIntention(govAction, rewardAccount);
    }
    
    /**
     * Factory method for creating governance proposal intention with anchor.
     */
    public static GovernanceProposalIntention propose(GovAction govAction, String rewardAccount, Anchor anchor) {
        return new GovernanceProposalIntention(govAction, rewardAccount, anchor);
    }
}