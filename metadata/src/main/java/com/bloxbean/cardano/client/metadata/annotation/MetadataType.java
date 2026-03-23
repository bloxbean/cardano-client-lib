package com.bloxbean.cardano.client.metadata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class for Cardano metadata serialization/deserialization code generation.
 * The annotation processor will generate a {@code {ClassName}MetadataConverter} class
 * with {@code toMetadataMap} and {@code fromMetadataMap} methods.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MetadataType {

    /**
     * Optional metadata label (top-level key in the transaction metadata map).
     * When set to a non-negative value, the generated converter will include
     * {@code toMetadata(T)} and {@code fromMetadata(Metadata)} convenience methods
     * that wrap/unwrap the MetadataMap under this label.
     *
     * <p>Default is {@code -1} (no label — only {@code toMetadataMap}/{@code fromMetadataMap}
     * are generated).
     */
    long label() default -1;
}
