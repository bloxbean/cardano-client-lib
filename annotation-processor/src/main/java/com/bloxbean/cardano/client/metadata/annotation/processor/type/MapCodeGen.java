package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGenRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;

import java.math.BigInteger;
import java.util.*;

/**
 * Code generation for {@code Map<K, V>} fields where K is String, Integer, Long, BigInteger, or byte[].
 * Generates MetadataMap serialization/deserialization with delegation to
 * the appropriate value type strategy (scalar, enum, nested, or composite).
 */
@SuppressWarnings("java:S1192") // JavaPoet format strings are intentionally repeated across similar codegen methods
public class MapCodeGen {

    private final MetadataTypeCodeGenRegistry registry;
    private final MetadataFieldAccessor accessor;
    private final EnumCodeGen enumCodeGen;
    private final NestedTypeCodeGen nestedCodeGen;

    public MapCodeGen(MetadataTypeCodeGenRegistry registry, MetadataFieldAccessor accessor,
                      EnumCodeGen enumCodeGen, NestedTypeCodeGen nestedCodeGen) {
        this.registry = registry;
        this.accessor = accessor;
        this.enumCodeGen = enumCodeGen;
        this.nestedCodeGen = nestedCodeGen;
    }

    // --- Key type helpers ---

    static TypeName resolveKeyTypeName(String keyTypeName) {
        if (keyTypeName == null) return TypeName.get(String.class);
        return switch (keyTypeName) {
            case INTEGER, PRIM_INT -> TypeName.get(Integer.class);
            case LONG, PRIM_LONG -> TypeName.get(Long.class);
            case BIG_INTEGER -> TypeName.get(BigInteger.class);
            case BYTE_ARRAY -> TypeName.get(byte[].class);
            default -> TypeName.get(String.class);
        };
    }

    static Class<?> keyOnChainClass(String keyTypeName) {
        if (keyTypeName == null) return String.class;
        return switch (keyTypeName) {
            case INTEGER, PRIM_INT, LONG, PRIM_LONG, BIG_INTEGER -> BigInteger.class;
            case BYTE_ARRAY -> byte[].class;
            default -> String.class;
        };
    }

    /**
     * Returns the serialization expression for the key in a for-each entry.
     * String keys: {@code <entryVar>.getKey()}, Integer/Long: {@code BigInteger.valueOf(<entryVar>.getKey())},
     * BigInteger: {@code <entryVar>.getKey()}.
     *
     * @param keyTypeName the key type name (null treated as String)
     * @param entryVar    the loop variable name (e.g. "_entry" or "_innerEntry")
     */
    static String serKeyExpr(String keyTypeName, String entryVar) {
        String getter = entryVar + ".getKey()";
        if (keyTypeName == null) return getter;
        return switch (keyTypeName) {
            case INTEGER, PRIM_INT, LONG, PRIM_LONG -> "java.math.BigInteger.valueOf(" + getter + ")";
            default -> getter; // String, BigInteger, byte[] pass through directly
        };
    }

    private static String serKeyExpr(String keyTypeName) {
        return serKeyExpr(keyTypeName, "_entry");
    }

    /**
     * Returns the deserialization expression that narrows the on-chain key back to Java type.
     */
    static String deserKeyExpr(String keyTypeName, String keyVar) {
        if (keyTypeName == null) return "(String) " + keyVar;
        return switch (keyTypeName) {
            case INTEGER, PRIM_INT -> "((java.math.BigInteger) " + keyVar + ").intValue()";
            case LONG, PRIM_LONG -> "((java.math.BigInteger) " + keyVar + ").longValue()";
            case BIG_INTEGER -> "(java.math.BigInteger) " + keyVar;
            case BYTE_ARRAY -> "(byte[]) " + keyVar;
            default -> "(String) " + keyVar;
        };
    }

    /**
     * Emit an {@code instanceof} check for the given on-chain key class.
     * byte[] requires a literal check since JavaPoet's {@code $T} doesn't work with array types.
     */
    static void emitKeyInstanceofCheck(MethodSpec.Builder builder, String var, Class<?> keyChain) {
        if (keyChain == byte[].class) {
            builder.beginControlFlow("if ($L instanceof byte[])", var);
        } else {
            builder.beginControlFlow("if ($L instanceof $T)", var, keyChain);
        }
    }

    /**
     * Emit {@code <resultVar> = <rawMapVar>.get((<cast>) keyVar)} with the correct cast for the key type.
     */
    static void emitMapGet(MethodSpec.Builder builder, String rawMapVar, String keyVar,
                            Class<?> keyChain, String resultVar) {
        if (keyChain == byte[].class) {
            builder.addStatement("$T $L = $L.get((byte[]) $L)", Object.class, resultVar, rawMapVar, keyVar);
        } else {
            builder.addStatement("$T $L = $L.get(($T) $L)", Object.class, resultVar, rawMapVar, keyChain, keyVar);
        }
    }

    // --- Serialization ---

    public void emitSerializeToMap(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        String key = field.getMetadataKey();
        String keyTypeName = field.getMapKeyTypeName();

        if (field.isMapValueCollectionType()) {
            emitSerializeMapWithCollectionValue(builder, field, getExpr, key);
            return;
        }

        if (field.isMapValueMapType()) {
            emitSerializeMapWithMapValue(builder, field, getExpr, key);
            return;
        }

        TypeName keyTN = resolveKeyTypeName(keyTypeName);
        TypeName valueTypeName = resolveValueTypeName(field);

        builder.addStatement("$T _map$L = $T.createMap()", MetadataMap.class, key, MetadataBuilder.class);
        builder.beginControlFlow("for ($T<$T, $T> _entry : $L.entrySet())",
                Map.Entry.class, keyTN, valueTypeName, getExpr);
        builder.beginControlFlow("if (_entry.getValue() != null)");

        String serKey = serKeyExpr(keyTypeName);
        if (field.isMapValueNestedType()) {
            ClassName converterClass = ClassName.bestGuess(field.getMapValueConverterFqn());
            builder.addStatement("_map$L.put($L, new $T().toMetadataMap(_entry.getValue()))",
                    key, serKey, converterClass);
        } else if (field.isMapValueEnumType()) {
            builder.addStatement("_map$L.put($L, _entry.getValue().name())", key, serKey);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getMapValueTypeName());
            codeGen.emitSerializeMapValue(builder, key, field.getMapValueTypeName(), serKey);
        }

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // for loop
        builder.addStatement("map.put($S, _map$L)", key, key);
    }

    private void emitSerializeMapWithCollectionValue(MethodSpec.Builder builder, MetadataFieldInfo field,
                                                      String getExpr, String key) {
        String keyTypeName = field.getMapKeyTypeName();
        TypeName keyTN = resolveKeyTypeName(keyTypeName);
        TypeName innerElemTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getMapValueElementTypeName(),
                field.isMapValueElementEnumType(), field.isMapValueElementNestedType());
        TypeName collectionTypeName = ParameterizedTypeName.get(
                ClassName.bestGuess(field.getMapValueCollectionKind()), innerElemTypeName);

        builder.addStatement("$T _map$L = $T.createMap()", MetadataMap.class, key, MetadataBuilder.class);
        builder.beginControlFlow("for ($T<$T, $T> _entry : $L.entrySet())",
                Map.Entry.class, keyTN, collectionTypeName, getExpr);
        builder.beginControlFlow("if (_entry.getValue() != null)");

        builder.addStatement("$T _innerList$L = $T.createList()", MetadataList.class, key, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _innerEl : _entry.getValue())", innerElemTypeName);
        builder.beginControlFlow("if (_innerEl != null)");

        CompositeCodeGenHelper.emitAddToList(builder, registry, "_innerList" + key, "_innerEl",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getMapValueElementTypeName(),
                        field.isMapValueElementEnumType(), field.isMapValueElementNestedType(),
                        field.getMapValueElementConverterFqn()));

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // inner for
        builder.addStatement("_map$L.put($L, _innerList$L)", key, serKeyExpr(keyTypeName), key);

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // outer for
        builder.addStatement("map.put($S, _map$L)", key, key);
    }

    private void emitSerializeMapWithMapValue(MethodSpec.Builder builder, MetadataFieldInfo field,
                                               String getExpr, String key) {
        String outerKeyTypeName = field.getMapKeyTypeName();
        String innerKeyTypeName = field.getMapValueMapKeyTypeName();
        TypeName outerKeyTN = resolveKeyTypeName(outerKeyTypeName);
        TypeName innerKeyTN = resolveKeyTypeName(innerKeyTypeName);
        TypeName innerValTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getMapValueMapValueTypeName(),
                field.isMapValueMapValueEnumType(), field.isMapValueMapValueNestedType());
        ParameterizedTypeName innerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), innerKeyTN, innerValTypeName);

        builder.addStatement("$T _map$L = $T.createMap()", MetadataMap.class, key, MetadataBuilder.class);
        builder.beginControlFlow("for ($T<$T, $T> _entry : $L.entrySet())",
                Map.Entry.class, outerKeyTN, innerMapType, getExpr);
        builder.beginControlFlow("if (_entry.getValue() != null)");

        builder.addStatement("$T _innerMap$L = $T.createMap()", MetadataMap.class, key, MetadataBuilder.class);
        builder.beginControlFlow("for ($T<$T, $T> _innerEntry : _entry.getValue().entrySet())",
                Map.Entry.class, innerKeyTN, innerValTypeName);
        builder.beginControlFlow("if (_innerEntry.getValue() != null)");

        String innerSerKey = serKeyExpr(innerKeyTypeName, "_innerEntry");

        CompositeCodeGenHelper.emitPutToMap(builder, registry, "_innerMap" + key, innerSerKey, "_innerEntry.getValue()",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getMapValueMapValueTypeName(),
                        field.isMapValueMapValueEnumType(), field.isMapValueMapValueNestedType(),
                        field.getMapValueMapValueConverterFqn()));

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // inner for
        builder.addStatement("_map$L.put($L, _innerMap$L)", key, serKeyExpr(outerKeyTypeName), key);

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // outer for
        builder.addStatement("map.put($S, _map$L)", key, key);
    }

    // --- Deserialization ---

    public void emitDeserializeFromMap(MethodSpec.Builder builder, MetadataFieldInfo field) {
        if (field.isMapValueCollectionType()) {
            emitDeserializeMapWithCollectionValue(builder, field);
            return;
        }

        if (field.isMapValueMapType()) {
            emitDeserializeMapWithMapValue(builder, field);
            return;
        }

        String keyTypeName = field.getMapKeyTypeName();
        TypeName keyTN = resolveKeyTypeName(keyTypeName);
        Class<?> keyChain = keyOnChainClass(keyTypeName);
        TypeName valueTypeName = resolveValueTypeName(field);
        ParameterizedTypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), keyTN, valueTypeName);

        builder.beginControlFlow("if (v instanceof $T)", MetadataMap.class);
        builder.addStatement("$T _rawMap = ($T) v", MetadataMap.class, MetadataMap.class);
        builder.addStatement("$T _result = new $T<>()", mapType, LinkedHashMap.class);
        builder.beginControlFlow("for ($T _k : _rawMap.keys())", Object.class);
        emitKeyInstanceofCheck(builder, "_k", keyChain);
        emitMapGet(builder, "_rawMap", "_k", keyChain, "_val");

        String dkExpr = deserKeyExpr(keyTypeName, "_k");
        if (field.isMapValueNestedType()) {
            ClassName converterClass = ClassName.bestGuess(field.getMapValueConverterFqn());
            builder.beginControlFlow("if (_val instanceof $T)", MetadataMap.class);
            builder.addStatement("_result.put($L, new $T().fromMetadataMap(($T) _val))",
                    dkExpr, converterClass, MetadataMap.class);
            builder.endControlFlow();
        } else if (field.isMapValueEnumType()) {
            ClassName enumClass = ClassName.bestGuess(field.getMapValueTypeName());
            builder.beginControlFlow("if (_val instanceof $T)", String.class);
            builder.addStatement("_result.put($L, $T.valueOf(($T) _val))",
                    dkExpr, enumClass, String.class);
            builder.endControlFlow();
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getMapValueTypeName());
            codeGen.emitDeserializeMapValue(builder, field.getMapValueTypeName(), dkExpr);
        }

        builder.endControlFlow(); // if key instanceof
        builder.endControlFlow(); // for loop
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataMap
    }

    private void emitDeserializeMapWithCollectionValue(MethodSpec.Builder builder, MetadataFieldInfo field) {
        String keyTypeName = field.getMapKeyTypeName();
        TypeName keyTN = resolveKeyTypeName(keyTypeName);
        Class<?> keyChain = keyOnChainClass(keyTypeName);
        TypeName innerElemTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getMapValueElementTypeName(),
                field.isMapValueElementEnumType(), field.isMapValueElementNestedType());

        String collKind = field.getMapValueCollectionKind();
        ClassName collInterface = ClassName.bestGuess(collKind);
        ClassName collImpl = CompositeCodeGenHelper.collectionImplClass(collKind);

        ParameterizedTypeName innerCollType = ParameterizedTypeName.get(collInterface, innerElemTypeName);
        ParameterizedTypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), keyTN, innerCollType);

        builder.beginControlFlow("if (v instanceof $T)", MetadataMap.class);
        builder.addStatement("$T _rawMap = ($T) v", MetadataMap.class, MetadataMap.class);
        builder.addStatement("$T _result = new $T<>()", mapType, LinkedHashMap.class);
        builder.beginControlFlow("for ($T _k : _rawMap.keys())", Object.class);
        emitKeyInstanceofCheck(builder, "_k", keyChain);
        emitMapGet(builder, "_rawMap", "_k", keyChain, "_val");

        builder.beginControlFlow("if (_val instanceof $T)", MetadataList.class);
        builder.addStatement("$T _innerRawList = ($T) _val", MetadataList.class, MetadataList.class);
        builder.addStatement("$T _innerResult = new $T<>()", innerCollType, collImpl);
        builder.beginControlFlow("for (int _j = 0; _j < _innerRawList.size(); _j++)");
        builder.addStatement("$T _innerEl = _innerRawList.getValueAt(_j)", Object.class);

        CompositeCodeGenHelper.emitDeserializeLeafFromRaw(builder, registry, "_innerResult", "_innerEl",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getMapValueElementTypeName(),
                        field.isMapValueElementEnumType(), field.isMapValueElementNestedType(),
                        field.getMapValueElementConverterFqn()));

        builder.endControlFlow(); // for _j
        String dkExpr = deserKeyExpr(keyTypeName, "_k");
        builder.addStatement("_result.put($L, _innerResult)", dkExpr);
        builder.endControlFlow(); // if MetadataList

        builder.endControlFlow(); // if key instanceof
        builder.endControlFlow(); // for _k
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataMap
    }

    private void emitDeserializeMapWithMapValue(MethodSpec.Builder builder, MetadataFieldInfo field) {
        String outerKeyTypeName = field.getMapKeyTypeName();
        String innerKeyTypeName = field.getMapValueMapKeyTypeName();
        TypeName outerKeyTN = resolveKeyTypeName(outerKeyTypeName);
        TypeName innerKeyTN = resolveKeyTypeName(innerKeyTypeName);
        Class<?> outerKeyChain = keyOnChainClass(outerKeyTypeName);
        Class<?> innerKeyChain = keyOnChainClass(innerKeyTypeName);
        TypeName innerValTypeName = CompositeCodeGenHelper.resolveLeafTypeName(field.getMapValueMapValueTypeName(),
                field.isMapValueMapValueEnumType(), field.isMapValueMapValueNestedType());
        ParameterizedTypeName innerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), innerKeyTN, innerValTypeName);
        ParameterizedTypeName outerMapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), outerKeyTN, innerMapType);

        builder.beginControlFlow("if (v instanceof $T)", MetadataMap.class);
        builder.addStatement("$T _rawMap = ($T) v", MetadataMap.class, MetadataMap.class);
        builder.addStatement("$T _result = new $T<>()", outerMapType, LinkedHashMap.class);
        builder.beginControlFlow("for ($T _k : _rawMap.keys())", Object.class);
        emitKeyInstanceofCheck(builder, "_k", outerKeyChain);
        emitMapGet(builder, "_rawMap", "_k", outerKeyChain, "_val");

        builder.beginControlFlow("if (_val instanceof $T)", MetadataMap.class);
        builder.addStatement("$T _innerRawMap = ($T) _val", MetadataMap.class, MetadataMap.class);
        builder.addStatement("$T _innerResult = new $T<>()", innerMapType, LinkedHashMap.class);
        builder.beginControlFlow("for ($T _innerK : _innerRawMap.keys())", Object.class);
        emitKeyInstanceofCheck(builder, "_innerK", innerKeyChain);
        emitMapGet(builder, "_innerRawMap", "_innerK", innerKeyChain, "_innerVal");

        String innerDkExpr = deserKeyExpr(innerKeyTypeName, "_innerK");
        CompositeCodeGenHelper.emitDeserializeLeafFromRawToMap(builder, registry, "_innerResult", innerDkExpr, "_innerVal",
                new CompositeCodeGenHelper.LeafTypeInfo(field.getMapValueMapValueTypeName(),
                        field.isMapValueMapValueEnumType(), field.isMapValueMapValueNestedType(),
                        field.getMapValueMapValueConverterFqn()));

        builder.endControlFlow(); // if _innerK instanceof
        builder.endControlFlow(); // for _innerK
        String outerDkExpr = deserKeyExpr(outerKeyTypeName, "_k");
        builder.addStatement("_result.put($L, _innerResult)", outerDkExpr);
        builder.endControlFlow(); // if MetadataMap

        builder.endControlFlow(); // if key instanceof
        builder.endControlFlow(); // for _k
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataMap
    }

    // --- Type name resolution ---

    private TypeName resolveValueTypeName(MetadataFieldInfo field) {
        if (field.isMapValueEnumType() || field.isMapValueNestedType()) {
            return ClassName.bestGuess(field.getMapValueTypeName());
        }
        return CompositeCodeGenHelper.scalarTypeName(field.getMapValueTypeName());
    }
}
