package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Intention for setting the sender address (from operation).
 */
public class FromIntention implements TxIntention {
    
    @JsonProperty("address")
    private String address;
    
    // Default constructor for Jackson
    public FromIntention() {
    }
    
    public FromIntention(String address) {
        this.address = address;
    }
    
    @Override
    public String getType() {
        return "from";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve address from variables if it's a variable
        String resolvedAddress = IntentionHelper.resolveVariable(address, variables);
        // Apply from directly to Tx
        tx.from(resolvedAddress);
    }
    
    public String getAddress() {
        return address;
    }
}