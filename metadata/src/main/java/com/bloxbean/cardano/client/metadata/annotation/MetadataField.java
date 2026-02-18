package com.bloxbean.cardano.client.metadata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Customizes how a field is serialized to/from Cardano metadata.
 * When {@code key} is specified, it overrides the field name used as the metadata map key.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface MetadataField {

    /**
     * Override the metadata map key for this field.
     * Defaults to the field name if not specified.
     */
    String key() default "";

}
