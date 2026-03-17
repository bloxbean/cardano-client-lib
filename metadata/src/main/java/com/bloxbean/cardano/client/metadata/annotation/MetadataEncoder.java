package com.bloxbean.cardano.client.metadata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a custom encoder for serializing a field to Cardano metadata.
 * <p>
 * The referenced class must implement {@link MetadataTypeAdapter}. Only the
 * {@link MetadataTypeAdapter#toMetadata(Object) toMetadata} method is called during
 * serialization; deserialization falls back to built-in type handling unless
 * {@link MetadataDecoder @MetadataDecoder} is also present on the same field.
 *
 * <p>Can be combined with {@link MetadataDecoder @MetadataDecoder} on the same field for
 * separate encode/decode logic. <strong>Mutually exclusive</strong> with
 * {@link MetadataField#adapter()}.
 *
 * <p>When the encoder requires constructor arguments (e.g., configuration or services),
 * supply a {@link MetadataAdapterResolver} to the generated converter's constructor.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @MetadataEncoder(SlotToEpochEncoder.class)
 * @MetadataField(key = "epoch")
 * private long slot;
 * }</pre>
 *
 * @see MetadataDecoder
 * @see MetadataAdapterResolver
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface MetadataEncoder {

    /**
     * The encoder class. Must implement {@link MetadataTypeAdapter}.
     * Only {@link MetadataTypeAdapter#toMetadata(Object)} is called.
     */
    Class<? extends MetadataTypeAdapter<?>> value();
}
