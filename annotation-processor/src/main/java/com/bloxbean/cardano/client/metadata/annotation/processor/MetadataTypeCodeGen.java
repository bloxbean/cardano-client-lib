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

    // --- Map value support ---

    /**
     * Emit {@code _mapSuffix.put(_entry.getKey(), ...)} for a Map value serialization.
     *
     * @param builder   method builder
     * @param mapVarSuffix the suffix for the map variable (e.g. "settings" → "_mapsettings")
     * @param javaType  value type
     */
    void emitSerializeMapValue(MethodSpec.Builder builder, String mapVarSuffix, String javaType);

    /**
     * Emit deserialization for a Map value from {@code _val} into {@code _result}.
     * Should check {@code _val instanceof OnChainType} and put into {@code _result}.
     *
     * @param builder  method builder
     * @param javaType value type
     */
    void emitDeserializeMapValue(MethodSpec.Builder builder, String javaType);

    // --- Composite support: variable-name-parameterized methods ---

    /**
     * Emit {@code listVar.add(serializeExpr)} for a single element.
     * Used by composite codegen where the list variable is not necessarily {@code _list}.
     */
    default void emitSerializeToListVar(MethodSpec.Builder builder, String listVar, String javaType) {
        throw new UnsupportedOperationException(
                "emitSerializeToListVar must be overridden for composite support (type: " + javaType + ")");
    }

    /**
     * Emit {@code mapVar.put(keyExpr, serializeExpr)} for a map entry.
     * Used by composite codegen where the map variable and key expression are parameterized.
     */
    default void emitSerializeMapValueVar(MethodSpec.Builder builder, String mapVar,
                                           String keyExpr, String javaType) {
        throw new UnsupportedOperationException(
                "emitSerializeMapValueVar must be overridden for composite support (type: " + javaType + ")");
    }

    /**
     * Emit deserialization of a raw value into a collection variable.
     * Checks {@code rawVar instanceof OnChainType} and calls {@code resultVar.add(...)}.
     */
    default void emitDeserializeToCollectionVar(MethodSpec.Builder builder, String resultVar,
                                                 String rawVar, String javaType) {
        throw new UnsupportedOperationException(
                "emitDeserializeToCollectionVar must be overridden for composite support (type: " + javaType + ")");
    }

    /**
     * Emit deserialization of a raw value into a map variable.
     * Checks {@code rawVar instanceof OnChainType} and calls {@code resultVar.put(keyExpr, ...)}.
     */
    default void emitDeserializeToMapVar(MethodSpec.Builder builder, String resultVar,
                                          String keyExpr, String rawVar, String javaType) {
        throw new UnsupportedOperationException(
                "emitDeserializeToMapVar must be overridden for composite support (type: " + javaType + ")");
    }
}
