package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for delegating voting power to a DRep.
 * Captures the stake address and the DRep to delegate to.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("voting_delegation")
public class VotingDelegationIntention implements TxIntention {
    
    @JsonProperty("stake_address")
    private String stakeAddress;
    
    @JsonProperty("drep")
    private DRep drep;
    
    @Override
    public String getType() {
        return "voting_delegation";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve stake address from variables if it's a variable
        String resolvedStakeAddress = IntentionHelper.resolveVariable(stakeAddress, variables);
        
        // Apply voting delegation directly to Tx
        tx.delegateVotingPowerTo(resolvedStakeAddress, drep);
    }
    
    /**
     * Factory method for creating voting delegation intention.
     */
    public static VotingDelegationIntention delegateTo(String stakeAddress, DRep drep) {
        return new VotingDelegationIntention(stakeAddress, drep);
    }
}