package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for pool relay configurations.
 * Supports polymorphic JSON/YAML serialization.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "relay_type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SingleHostAddr.class, name = "single_host_addr"),
    @JsonSubTypes.Type(value = SingleHostName.class, name = "single_host_name"),
    @JsonSubTypes.Type(value = MultiHostName.class, name = "multi_host_name")
})
public interface Relay {
    Array serialize() throws CborSerializationException;
}
