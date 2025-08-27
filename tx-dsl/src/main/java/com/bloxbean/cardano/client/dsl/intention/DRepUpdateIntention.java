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
 * Intention for updating a DRep (Delegated Representative).
 * Captures the DRep credential and optional metadata anchor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("drep_update")
public class DRepUpdateIntention implements TxIntention {
    
    @JsonProperty("drep_credential")
    private Credential drepCredential;
    
    @JsonProperty("anchor")
    private Anchor anchor; // Optional metadata anchor
    
    public DRepUpdateIntention(Credential drepCredential) {
        this.drepCredential = drepCredential;
        this.anchor = null;
    }
    
    @Override
    public String getType() {
        return "drep_update";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Note: Credential objects typically don't need variable resolution,
        // but anchor could potentially contain variable references in the future
        
        // Apply DRep update directly to Tx
        if (anchor != null) {
            tx.updateDRep(drepCredential, anchor);
        } else {
            tx.updateDRep(drepCredential);
        }
    }
    
    /**
     * Factory method for creating DRep update intention.
     */
    public static DRepUpdateIntention update(Credential drepCredential) {
        return new DRepUpdateIntention(drepCredential);
    }
    
    /**
     * Factory method for creating DRep update intention with anchor.
     */
    public static DRepUpdateIntention update(Credential drepCredential, Anchor anchor) {
        return new DRepUpdateIntention(drepCredential, anchor);
    }
}