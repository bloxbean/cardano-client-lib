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
}
