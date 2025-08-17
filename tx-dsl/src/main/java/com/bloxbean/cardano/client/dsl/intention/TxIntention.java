package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.dsl.TxDsl;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for capturing transaction intentions.
 * Each intention represents a method call on TxDsl that needs to be serialized.
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
     * Apply this intention to a TxDsl instance during deserialization.
     * This reconstructs the state by replaying the operation.
     * 
     * @param txDsl the TxDsl to apply the intention to
     */
    void apply(TxDsl txDsl);
}