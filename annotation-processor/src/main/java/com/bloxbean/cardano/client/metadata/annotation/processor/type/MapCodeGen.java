package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGenRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Code generation for {@code Map<String, V>} fields.
 * Generates MetadataMap serialization/deserialization with delegation to
 * the appropriate value type strategy (scalar, enum, or nested).
 */
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

    // --- Serialization ---

    public void emitSerializeToMap(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        String key = field.getMetadataKey();
        TypeName valueTypeName = resolveValueTypeName(field);

        builder.addStatement("$T _map$L = $T.createMap()", MetadataMap.class, key, MetadataBuilder.class);
        builder.beginControlFlow("for ($T<$T, $T> _entry : $L.entrySet())",
                Map.Entry.class, String.class, valueTypeName, getExpr);
        builder.beginControlFlow("if (_entry.getValue() != null)");

        if (field.isMapValueNestedType()) {
            ClassName converterClass = ClassName.bestGuess(field.getMapValueConverterFqn());
            builder.addStatement("_map$L.put(_entry.getKey(), new $T().toMetadataMap(_entry.getValue()))",
                    key, converterClass);
        } else if (field.isMapValueEnumType()) {
            builder.addStatement("_map$L.put(_entry.getKey(), _entry.getValue().name())", key);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getMapValueTypeName());
            codeGen.emitSerializeMapValue(builder, key, field.getMapValueTypeName());
        }

        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // for loop
        builder.addStatement("map.put($S, _map$L)", key, key);
    }

    // --- Deserialization ---

    public void emitDeserializeFromMap(MethodSpec.Builder builder, MetadataFieldInfo field) {
        TypeName valueTypeName = resolveValueTypeName(field);
        ParameterizedTypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class), TypeName.get(String.class), valueTypeName);

        builder.beginControlFlow("if (v instanceof $T)", MetadataMap.class);
        builder.addStatement("$T _rawMap = ($T) v", MetadataMap.class, MetadataMap.class);
        builder.addStatement("$T _result = new $T<>()", mapType, LinkedHashMap.class);
        builder.beginControlFlow("for ($T _k : _rawMap.keys())", Object.class);
        builder.beginControlFlow("if (_k instanceof $T)", String.class);
        builder.addStatement("$T _val = _rawMap.get(($T) _k)", Object.class, String.class);

        if (field.isMapValueNestedType()) {
            ClassName converterClass = ClassName.bestGuess(field.getMapValueConverterFqn());
            builder.beginControlFlow("if (_val instanceof $T)", MetadataMap.class);
            builder.addStatement("_result.put(($T) _k, new $T().fromMetadataMap(($T) _val))",
                    String.class, converterClass, MetadataMap.class);
            builder.endControlFlow();
        } else if (field.isMapValueEnumType()) {
            ClassName enumClass = ClassName.bestGuess(field.getMapValueTypeName());
            builder.beginControlFlow("if (_val instanceof $T)", String.class);
            builder.addStatement("_result.put(($T) _k, $T.valueOf(($T) _val))",
                    String.class, enumClass, String.class);
            builder.endControlFlow();
        } else {
            MetadataTypeCodeGen codeGen = registry.get(field.getMapValueTypeName());
            codeGen.emitDeserializeMapValue(builder, field.getMapValueTypeName());
        }

        builder.endControlFlow(); // if key instanceof String
        builder.endControlFlow(); // for loop
        accessor.emitSetRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataMap
    }

    private TypeName resolveValueTypeName(MetadataFieldInfo field) {
        if (field.isMapValueEnumType() || field.isMapValueNestedType()) {
            return ClassName.bestGuess(field.getMapValueTypeName());
        }
        return scalarTypeName(field.getMapValueTypeName());
    }

    private TypeName scalarTypeName(String typeName) {
        return switch (typeName) {
            case "java.lang.String"         -> TypeName.get(String.class);
            case "java.math.BigInteger"     -> TypeName.get(BigInteger.class);
            case "java.math.BigDecimal"     -> TypeName.get(BigDecimal.class);
            case "java.lang.Long"           -> TypeName.get(Long.class);
            case "java.lang.Integer"        -> TypeName.get(Integer.class);
            case "java.lang.Short"          -> TypeName.get(Short.class);
            case "java.lang.Byte"           -> TypeName.get(Byte.class);
            case "java.lang.Boolean"        -> TypeName.get(Boolean.class);
            case "java.lang.Double"         -> TypeName.get(Double.class);
            case "java.lang.Float"          -> TypeName.get(Float.class);
            case "java.lang.Character"      -> TypeName.get(Character.class);
            case "byte[]"                   -> TypeName.get(byte[].class);
            case "java.net.URI"             -> TypeName.get(URI.class);
            case "java.net.URL"             -> TypeName.get(URL.class);
            case "java.util.UUID"           -> TypeName.get(UUID.class);
            case "java.util.Currency"       -> TypeName.get(Currency.class);
            case "java.util.Locale"         -> TypeName.get(Locale.class);
            case "java.time.Instant"        -> TypeName.get(Instant.class);
            case "java.time.LocalDate"      -> TypeName.get(LocalDate.class);
            case "java.time.LocalDateTime"  -> TypeName.get(LocalDateTime.class);
            case "java.util.Date"           -> TypeName.get(Date.class);
            default -> throw new IllegalArgumentException("Unsupported map value type: " + typeName);
        };
    }
}
