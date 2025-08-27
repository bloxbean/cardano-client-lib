package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for setting a custom change address for the transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeAddressIntention implements TxIntention {
    
    @JsonProperty("change_address")
    private String changeAddress;
    
    @Override
    public String getType() {
        return "change_address";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve address from variables if it's a variable
        String resolvedAddress = IntentionHelper.resolveVariable(changeAddress, variables);
        
        // Apply change address directly to Tx
        tx.withChangeAddress(resolvedAddress);
    }
}