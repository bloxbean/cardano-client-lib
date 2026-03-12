package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGenRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
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
 * Shared helper methods for composite codegen used by both {@link MapCodeGen} and {@link CollectionCodeGen}.
 * Eliminates duplication of leaf-level serialize/deserialize dispatch and type resolution.
 */
final class CompositeCodeGenHelper {

    private CompositeCodeGenHelper() {}

    // --- Serialize dispatch ---

    static void emitAddToList(MethodSpec.Builder builder, MetadataTypeCodeGenRegistry registry,
                               String listVar, String elemVar,
                               String leafTypeName, boolean isEnum, boolean isNested, String converterFqn) {
        if (isNested) {
            ClassName converterClass = ClassName.bestGuess(converterFqn);
            builder.addStatement("$L.add(new $T().toMetadataMap($L))", listVar, converterClass, elemVar);
        } else if (isEnum) {
            builder.addStatement("$L.add($L.name())", listVar, elemVar);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(leafTypeName);
            codeGen.emitSerializeToListVar(builder, listVar, leafTypeName);
        }
    }

    static void emitPutToMap(MethodSpec.Builder builder, MetadataTypeCodeGenRegistry registry,
                              String mapVar, String keyExpr, String valExpr,
                              String leafTypeName, boolean isEnum, boolean isNested, String converterFqn) {
        if (isNested) {
            ClassName converterClass = ClassName.bestGuess(converterFqn);
            builder.addStatement("$L.put($L, new $T().toMetadataMap($L))", mapVar, keyExpr, converterClass, valExpr);
        } else if (isEnum) {
            builder.addStatement("$L.put($L, $L.name())", mapVar, keyExpr, valExpr);
        } else {
            MetadataTypeCodeGen codeGen = registry.get(leafTypeName);
            codeGen.emitSerializeMapValueVar(builder, mapVar, keyExpr, leafTypeName);
        }
    }

    // --- Deserialize dispatch ---

    static void emitDeserializeLeafFromRaw(MethodSpec.Builder builder, MetadataTypeCodeGenRegistry registry,
                                            String resultVar, String rawVar,
                                            String leafTypeName, boolean isEnum, boolean isNested,
                                            String converterFqn) {
        if (isNested) {
            ClassName converterClass = ClassName.bestGuess(converterFqn);
            builder.beginControlFlow("if ($L instanceof $T)", rawVar, MetadataMap.class);
            builder.addStatement("$L.add(new $T().fromMetadataMap(($T) $L))",
                    resultVar, converterClass, MetadataMap.class, rawVar);
            builder.endControlFlow();
        } else if (isEnum) {
            ClassName enumClass = ClassName.bestGuess(leafTypeName);
            builder.beginControlFlow("if ($L instanceof $T)", rawVar, String.class);
            builder.addStatement("$L.add($T.valueOf(($T) $L))",
                    resultVar, enumClass, String.class, rawVar);
            builder.endControlFlow();
        } else {
            MetadataTypeCodeGen codeGen = registry.get(leafTypeName);
            codeGen.emitDeserializeToCollectionVar(builder, resultVar, rawVar, leafTypeName);
        }
    }

    static void emitDeserializeLeafFromRawToMap(MethodSpec.Builder builder, MetadataTypeCodeGenRegistry registry,
                                                 String resultVar, String keyVar, String rawVar,
                                                 String leafTypeName, boolean isEnum, boolean isNested,
                                                 String converterFqn) {
        if (isNested) {
            ClassName converterClass = ClassName.bestGuess(converterFqn);
            builder.beginControlFlow("if ($L instanceof $T)", rawVar, MetadataMap.class);
            builder.addStatement("$L.put(($T) $L, new $T().fromMetadataMap(($T) $L))",
                    resultVar, String.class, keyVar, converterClass, MetadataMap.class, rawVar);
            builder.endControlFlow();
        } else if (isEnum) {
            ClassName enumClass = ClassName.bestGuess(leafTypeName);
            builder.beginControlFlow("if ($L instanceof $T)", rawVar, String.class);
            builder.addStatement("$L.put(($T) $L, $T.valueOf(($T) $L))",
                    resultVar, String.class, keyVar, enumClass, String.class, rawVar);
            builder.endControlFlow();
        } else {
            MetadataTypeCodeGen codeGen = registry.get(leafTypeName);
            codeGen.emitDeserializeToMapVar(builder, resultVar, keyVar, rawVar, leafTypeName);
        }
    }

    // --- Type resolution ---

    static TypeName resolveLeafTypeName(String typeName, boolean isEnum, boolean isNested) {
        if (isEnum || isNested) {
            return ClassName.bestGuess(typeName);
        }
        return scalarTypeName(typeName);
    }

    static ClassName collectionImplClass(String collectionKind) {
        return switch (collectionKind) {
            case "java.util.List" -> ClassName.get("java.util", "ArrayList");
            case "java.util.Set" -> ClassName.get("java.util", "LinkedHashSet");
            case "java.util.SortedSet" -> ClassName.get("java.util", "TreeSet");
            default -> ClassName.get("java.util", "ArrayList");
        };
    }

    static TypeName scalarTypeName(String typeName) {
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
            default -> throw new IllegalArgumentException("Unsupported type: " + typeName);
        };
    }
}
