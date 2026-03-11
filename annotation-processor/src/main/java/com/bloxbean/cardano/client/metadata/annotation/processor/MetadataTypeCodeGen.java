package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.squareup.javapoet.MethodSpec;

import java.util.Set;

/**
 * Strategy interface for generating type-specific serialization / deserialization
 * code within a metadata converter.
 *
 * <p>Each implementation handles one or more Java types and knows how to emit
 * JavaPoet statements for the 7 contexts where type dispatch occurs:
 * <ol>
 *   <li>Scalar → MetadataMap (DEFAULT enc)</li>
 *   <li>Scalar → MetadataMap (STRING enc)</li>
 *   <li>MetadataMap → Scalar (DEFAULT enc)</li>
 *   <li>MetadataMap → Scalar (STRING enc)</li>
 *   <li>Element → MetadataList (collection serialization)</li>
 *   <li>MetadataList element → collection (collection deserialization)</li>
 *   <li>Optional deserialization</li>
 * </ol>
 */
public interface MetadataTypeCodeGen {

    /** The set of fully-qualified Java type names this strategy handles. */
    Set<String> supportedJavaTypes();

    /** Whether this type needs a null-check before serialization (false for primitives). */
    boolean needsNullCheck(String javaType);

    // --- Serialization ---

    /**
     * Emit {@code map.put(key, ...)} for the DEFAULT encoding.
     *
     * @param builder the method builder to emit into
     * @param key     the metadata map key literal
     * @param getExpr the expression that reads the value (e.g. "obj.getName()")
     * @param javaType fully-qualified Java type name
     */
    void emitSerializeToMapDefault(MethodSpec.Builder builder, String key, String getExpr,
                                   String javaType);

    /**
     * Emit {@code map.put(key, ...)} for the STRING encoding.
     * Default implementation delegates to {@link #emitSerializeToMapDefault}.
     */
    default void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                          String javaType) {
        emitSerializeToMapDefault(builder, key, getExpr, javaType);
    }

    /**
     * Emit {@code _list.add(...)} for a single collection element.
     *
     * @param builder   method builder
     * @param javaType  element type (boxed only, never primitive)
     */
    void emitSerializeToList(MethodSpec.Builder builder, String javaType);

    // --- Deserialization ---

    /**
     * Emit scalar deserialization from a MetadataMap value {@code v} with DEFAULT encoding.
     */
    void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                      MetadataFieldAccessor accessor);

    /**
     * Emit scalar deserialization from a MetadataMap value {@code v} with STRING encoding.
     * Default implementation delegates to {@link #emitDeserializeScalarDefault}.
     */
    default void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             MetadataFieldAccessor accessor) {
        emitDeserializeScalarDefault(builder, field, accessor);
    }

    /**
     * Emit deserialization for a collection element from {@code _el} into {@code _result}.
     */
    void emitDeserializeElement(MethodSpec.Builder builder, String javaType);

    /**
     * Emit deserialization for {@code Optional<T>}: the present branch (with {@code v}).
     * Must also emit the else branch with {@code Optional.empty()}.
     */
    void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                 MetadataFieldAccessor accessor);
}
