package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for delegating a stake address to a stake pool.
 * Captures the stake address and the pool ID for delegation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("stake_delegation")
public class StakeDelegationIntention implements TxIntention {
    
    @JsonProperty("stake_address")
    private String stakeAddress;
    
    @JsonProperty("pool_id")
    private String poolId;
    
    @Override
    public String getType() {
        return "stake_delegation";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve addresses and pool ID from variables if they're variables
        String resolvedStakeAddress = IntentionHelper.resolveVariable(stakeAddress, variables);
        String resolvedPoolId = IntentionHelper.resolveVariable(poolId, variables);
        
        // Apply stake delegation directly to Tx
        tx.delegateTo(resolvedStakeAddress, resolvedPoolId);
    }
    
    /**
     * Factory method for creating stake delegation intention.
     */
    public static StakeDelegationIntention delegateTo(String stakeAddress, String poolId) {
        return new StakeDelegationIntention(stakeAddress, poolId);
    }
}