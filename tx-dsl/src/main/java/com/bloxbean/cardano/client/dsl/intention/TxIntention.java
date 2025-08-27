package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Base interface for capturing transaction intentions.
 * Each intention represents a method call that needs to be applied to build a Tx.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = FromIntention.class, name = "from"),
    @JsonSubTypes.Type(value = PaymentIntention.class, name = "payment")
})
public interface TxIntention {
    
    /**
     * Get the type of this intention for serialization.
     * 
     * @return the intention type
     */
    String getType();
    
    /**
     * Apply this intention to a Tx instance with variable resolution.
     * This builds the transaction by directly calling Tx methods.
     * 
     * @param tx the Tx to apply the intention to
     * @param variables the variables to use for resolution (may be null)
     */
    void apply(Tx tx, Map<String, Object> variables);
}