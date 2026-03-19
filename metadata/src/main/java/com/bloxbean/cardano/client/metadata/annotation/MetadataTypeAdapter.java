package com.bloxbean.cardano.client.metadata.annotation;

/**
 * Pluggable adapter for serializing/deserializing a Java type to/from Cardano metadata.
 * <p>
 * Apply via {@code @MetadataEncoder(MyAdapter.class)} and/or {@code @MetadataDecoder(MyAdapter.class)}
 * to let the processor delegate serialization/deserialization to the adapter instead of
 * using built-in type handling.
 *
 * @param <T> the Java type this adapter converts
 */
public interface MetadataTypeAdapter<T> {

    /**
     * Converts a Java value to its metadata representation.
     *
     * @param value the Java value (never null — null checks are handled by the generated code)
     * @return the metadata-compatible value (String, BigInteger, byte[], MetadataMap, MetadataList, etc.)
     */
    Object toMetadata(T value);

    /**
     * Converts a metadata value back to the Java type.
     *
     * @param metadata the raw metadata value
     * @return the Java value
     */
    T fromMetadata(Object metadata);
}
