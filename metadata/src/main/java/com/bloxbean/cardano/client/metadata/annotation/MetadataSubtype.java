package com.bloxbean.cardano.client.metadata.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a discriminator value to a concrete {@link MetadataType @MetadataType} subtype.
 * Used inside {@link MetadataDiscriminator#subtypes()}.
 */
@Target({})
@Retention(RetentionPolicy.SOURCE)
public @interface MetadataSubtype {

    /**
     * The discriminator value that identifies this subtype in the metadata map.
     */
    String value();

    /**
     * The concrete {@link MetadataType @MetadataType} class for this subtype.
     */
    Class<?> type();
}
