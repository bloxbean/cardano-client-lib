package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Intention for attaching metadata to the transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataIntention implements TxIntention {
    
    @JsonProperty("metadata")
    private Metadata metadata;
    
    @Override
    public String getType() {
        return "metadata";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // For now, we use metadata as-is
        // Future enhancement: support variable resolution for metadata
        
        // Apply metadata directly to Tx
        tx.attachMetadata(metadata);
    }
}