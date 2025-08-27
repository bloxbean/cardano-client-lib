package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for registering a stake address.
 * Captures the stake address that needs to be registered.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("stake_registration")
public class StakeRegistrationIntention implements TxIntention {
    
    @JsonProperty("stake_address")
    private String stakeAddress;
    
    @Override
    public String getType() {
        return "stake_registration";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve stake address from variables if it's a variable
        String resolvedStakeAddress = IntentionHelper.resolveVariable(stakeAddress, variables);
        
        // Apply stake registration directly to Tx
        tx.registerStakeAddress(resolvedStakeAddress);
    }
    
    /**
     * Factory method for creating stake registration intention.
     */
    public static StakeRegistrationIntention register(String stakeAddress) {
        return new StakeRegistrationIntention(stakeAddress);
    }
}