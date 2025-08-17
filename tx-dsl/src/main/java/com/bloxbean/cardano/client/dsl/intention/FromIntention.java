package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.dsl.TxDsl;
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
    public void apply(TxDsl txDsl) {
        txDsl.from(address);
    }
    
    public String getAddress() {
        return address;
    }
}