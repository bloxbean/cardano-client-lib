package com.bloxbean.cardano.client.metadata.annotation;

import com.bloxbean.cardano.client.metadata.MetadataMap;

/**
 * Common interface for all generated metadata converters.
 * <p>
 * Every {@code @MetadataType}-annotated class gets a generated converter that implements
 * this interface, enabling generic code that serializes/deserializes any model to/from
 * Cardano transaction metadata.
 *
 * @param <T> the Java type this converter handles
 */
public interface MetadataConverter<T> {

    /**
     * Converts a Java object to its {@link MetadataMap} representation.
     *
     * @param obj the Java object to serialize
     * @return the metadata map representation
     */
    MetadataMap toMetadataMap(T obj);

    /**
     * Reconstructs a Java object from its {@link MetadataMap} representation.
     *
     * @param map the metadata map to deserialize
     * @return the reconstructed Java object
     */
    T fromMetadataMap(MetadataMap map);
}
