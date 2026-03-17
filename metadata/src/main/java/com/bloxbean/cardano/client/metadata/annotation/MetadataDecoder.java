package com.bloxbean.cardano.client.metadata.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a custom decoder for deserializing a field from Cardano metadata.
 * <p>
 * The referenced class must implement {@link MetadataTypeAdapter}. Only the
 * {@link MetadataTypeAdapter#fromMetadata(Object) fromMetadata} method is called during
 * deserialization; serialization falls back to built-in type handling unless
 * {@link MetadataEncoder @MetadataEncoder} is also present on the same field.
 *
 * <p>Can be combined with {@link MetadataEncoder @MetadataEncoder} on the same field for
 * separate encode/decode logic. <strong>Mutually exclusive</strong> with
 * {@link MetadataField#adapter()}.
 *
 * <p>When the decoder requires constructor arguments (e.g., configuration or services),
 * supply a {@link MetadataAdapterResolver} to the generated converter's constructor.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * @MetadataDecoder(EpochToSlotDecoder.class)
 * @MetadataField(key = "epoch")
 * private long slot;
 * }</pre>
 *
 * @see MetadataEncoder
 * @see MetadataAdapterResolver
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface MetadataDecoder {

    /**
     * The decoder class. Must implement {@link MetadataTypeAdapter}.
     * Only {@link MetadataTypeAdapter#fromMetadata(Object)} is called.
     */
    Class<? extends MetadataTypeAdapter<?>> value();
}
