package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for deregistering a stake address.
 * Captures the stake address to deregister and optional refund address.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("stake_deregistration")
public class StakeDeregistrationIntention implements TxIntention {
    
    @JsonProperty("stake_address")
    private String stakeAddress;
    
    @JsonProperty("refund_address")
    private String refundAddress; // Optional
    
    public StakeDeregistrationIntention(String stakeAddress) {
        this.stakeAddress = stakeAddress;
        this.refundAddress = null;
    }
    
    @Override
    public String getType() {
        return "stake_deregistration";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve addresses from variables if they're variables
        String resolvedStakeAddress = IntentionHelper.resolveVariable(stakeAddress, variables);
        String resolvedRefundAddress = refundAddress != null ? 
            IntentionHelper.resolveVariable(refundAddress, variables) : null;
        
        // Apply stake deregistration directly to Tx
        if (resolvedRefundAddress != null) {
            tx.deregisterStakeAddress(resolvedStakeAddress, resolvedRefundAddress);
        } else {
            tx.deregisterStakeAddress(resolvedStakeAddress);
        }
    }
    
    /**
     * Factory method for creating stake deregistration intention.
     */
    public static StakeDeregistrationIntention deregister(String stakeAddress) {
        return new StakeDeregistrationIntention(stakeAddress);
    }
    
    /**
     * Factory method for creating stake deregistration intention with refund address.
     */
    public static StakeDeregistrationIntention deregister(String stakeAddress, String refundAddress) {
        return new StakeDeregistrationIntention(stakeAddress, refundAddress);
    }
}