package com.bloxbean.cardano.client.metadata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Customizes how a field is serialized to/from Cardano metadata.
 * When {@code key} is specified, it overrides the field name used as the metadata map key.
 * When {@code enc} is specified, it overrides the Cardano metadata type used for storage.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface MetadataField {

    /**
     * Override the metadata map key for this field.
     * Defaults to the field name if not specified.
     */
    String key() default "";

    /**
     * Override the Cardano metadata type used to store this field.
     * Defaults to {@link MetadataFieldType#DEFAULT} which applies the natural mapping
     * for the Java type. See {@link MetadataFieldType} for valid combinations.
     */
    MetadataFieldType enc() default MetadataFieldType.DEFAULT;

}
