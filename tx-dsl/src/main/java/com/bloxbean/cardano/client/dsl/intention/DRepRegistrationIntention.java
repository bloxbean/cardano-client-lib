package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for registering a DRep (Delegated Representative).
 * Captures the DRep credential and optional metadata anchor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("drep_registration")
public class DRepRegistrationIntention implements TxIntention {
    
    @JsonProperty("drep_credential")
    private Credential drepCredential;
    
    @JsonProperty("anchor")
    private Anchor anchor; // Optional metadata anchor
    
    public DRepRegistrationIntention(Credential drepCredential) {
        this.drepCredential = drepCredential;
        this.anchor = null;
    }
    
    @Override
    public String getType() {
        return "drep_registration";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Note: Credential objects typically don't need variable resolution,
        // but anchor could potentially contain variable references in the future
        
        // Apply DRep registration directly to Tx
        if (anchor != null) {
            tx.registerDRep(drepCredential, anchor);
        } else {
            tx.registerDRep(drepCredential);
        }
    }
    
    /**
     * Factory method for creating DRep registration intention.
     */
    public static DRepRegistrationIntention register(Credential drepCredential) {
        return new DRepRegistrationIntention(drepCredential);
    }
    
    /**
     * Factory method for creating DRep registration intention with anchor.
     */
    public static DRepRegistrationIntention register(Credential drepCredential, Anchor anchor) {
        return new DRepRegistrationIntention(drepCredential, anchor);
    }
}