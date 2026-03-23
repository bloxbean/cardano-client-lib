package com.bloxbean.cardano.client.metadata.annotation;

import com.bloxbean.cardano.client.metadata.Metadata;

/**
 * Extended converter interface for {@code @MetadataType(label=N)} classes that support
 * label-based serialization into full {@link Metadata} objects.
 * <p>
 * Only generated when the annotation specifies a non-negative label value.
 *
 * @param <T> the Java type this converter handles
 */
public interface LabeledMetadataConverter<T> extends MetadataConverter<T> {

    /**
     * Converts a Java object to a {@link Metadata} instance with the configured label.
     *
     * @param obj the Java object to serialize
     * @return the labeled metadata
     */
    Metadata toMetadata(T obj);

    /**
     * Reconstructs a Java object from a {@link Metadata} instance by extracting
     * the map at the configured label.
     *
     * @param metadata the labeled metadata
     * @return the reconstructed Java object
     */
    T fromMetadata(Metadata metadata);
}
