package com.bloxbean.cardano.client.metadata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares polymorphic dispatch for a sealed interface or abstract class.
 * When a field's type carries this annotation, the generated converter uses
 * the discriminator key to determine which {@link MetadataSubtype} to
 * serialize/deserialize.
 *
 * <p>The annotated type itself does not need {@link MetadataType @MetadataType}
 * — each concrete subtype listed in {@link #subtypes()} must have it instead.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MetadataDiscriminator {

    /**
     * The metadata map key used as the discriminator (e.g. {@code "type"}).
     */
    String key();

    /**
     * The set of concrete subtypes and their discriminator values.
     */
    MetadataSubtype[] subtypes();
}
