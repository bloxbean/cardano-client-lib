package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Map;

/**
 * Intention for deregistering a DRep (Delegated Representative).
 * Captures the DRep credential, optional refund address and amount.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("drep_deregistration")
public class DRepDeregistrationIntention implements TxIntention {
    
    @JsonProperty("drep_credential")
    private Credential drepCredential;
    
    @JsonProperty("refund_address")
    private String refundAddress; // Optional refund address
    
    @JsonProperty("refund_amount")
    private BigInteger refundAmount; // Optional refund amount
    
    public DRepDeregistrationIntention(Credential drepCredential) {
        this.drepCredential = drepCredential;
        this.refundAddress = null;
        this.refundAmount = null;
    }
    
    public DRepDeregistrationIntention(Credential drepCredential, String refundAddress) {
        this.drepCredential = drepCredential;
        this.refundAddress = refundAddress;
        this.refundAmount = null;
    }
    
    @Override
    public String getType() {
        return "drep_deregistration";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve refund address from variables if it's a variable
        String resolvedRefundAddress = refundAddress != null ? 
            IntentionHelper.resolveVariable(refundAddress, variables) : null;
        
        // Apply DRep deregistration directly to Tx
        if (resolvedRefundAddress != null && refundAmount != null) {
            tx.unregisterDRep(drepCredential, resolvedRefundAddress, refundAmount);
        } else if (resolvedRefundAddress != null) {
            tx.unregisterDRep(drepCredential, resolvedRefundAddress);
        } else {
            tx.unregisterDRep(drepCredential);
        }
    }
    
    /**
     * Factory method for creating DRep deregistration intention.
     */
    public static DRepDeregistrationIntention unregister(Credential drepCredential) {
        return new DRepDeregistrationIntention(drepCredential);
    }
    
    /**
     * Factory method for creating DRep deregistration intention with refund address.
     */
    public static DRepDeregistrationIntention unregister(Credential drepCredential, String refundAddress) {
        return new DRepDeregistrationIntention(drepCredential, refundAddress);
    }
    
    /**
     * Factory method for creating DRep deregistration intention with refund address and amount.
     */
    public static DRepDeregistrationIntention unregister(Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        return new DRepDeregistrationIntention(drepCredential, refundAddress, refundAmount);
    }
}