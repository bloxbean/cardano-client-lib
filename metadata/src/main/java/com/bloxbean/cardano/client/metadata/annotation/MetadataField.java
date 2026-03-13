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

    /**
     * When {@code true}, deserialization throws {@link IllegalArgumentException} if this
     * key is missing from the metadata map. Only affects {@code fromMetadataMap};
     * serialization is unchanged.
     * <p>Mutually exclusive with {@link #defaultValue()}.
     */
    boolean required() default false;

    /**
     * Fallback value (as a string) to use when this key is absent from the metadata map
     * during deserialization. The string is parsed into the field's on-chain type.
     * Only supported on scalar and enum fields — not on collections, maps, Optional, byte[], or nested types.
     * <p>Mutually exclusive with {@link #required()}.
     */
    String defaultValue() default "";

    /**
     * Custom adapter for serializing/deserializing this field.
     * When specified, the adapter's {@code toMetadata}/{@code fromMetadata} methods are
     * used instead of the processor's built-in type handling.
     * <p>Mutually exclusive with {@link #defaultValue()}.
     * The {@link #enc()} attribute is ignored when an adapter is specified.
     */
    Class<? extends MetadataTypeAdapter<?>> adapter() default NoAdapter.class;

    /**
     * Sentinel class indicating no adapter is specified. Used as the default for {@link #adapter()}.
     */
    final class NoAdapter implements MetadataTypeAdapter<Void> {
        private NoAdapter() {}

        @Override
        public Object toMetadata(Void value) {
            throw new UnsupportedOperationException("NoAdapter sentinel");
        }

        @Override
        public Void fromMetadata(Object metadata) {
            throw new UnsupportedOperationException("NoAdapter sentinel");
        }
    }

}
