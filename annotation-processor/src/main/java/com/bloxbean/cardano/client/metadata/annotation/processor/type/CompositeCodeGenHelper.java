package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
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
            case COLLECTION_LIST -> ClassName.get("java.util", "ArrayList");
            case COLLECTION_SET -> ClassName.get("java.util", "LinkedHashSet");
            case COLLECTION_SORTED_SET -> ClassName.get("java.util", "TreeSet");
            default -> ClassName.get("java.util", "ArrayList");
        };
    }

    static TypeName scalarTypeName(String typeName) {
        return switch (typeName) {
            case STRING         -> TypeName.get(String.class);
            case BIG_INTEGER    -> TypeName.get(BigInteger.class);
            case BIG_DECIMAL    -> TypeName.get(BigDecimal.class);
            case LONG           -> TypeName.get(Long.class);
            case INTEGER        -> TypeName.get(Integer.class);
            case SHORT          -> TypeName.get(Short.class);
            case BYTE           -> TypeName.get(Byte.class);
            case BOOLEAN        -> TypeName.get(Boolean.class);
            case DOUBLE         -> TypeName.get(Double.class);
            case FLOAT          -> TypeName.get(Float.class);
            case CHARACTER      -> TypeName.get(Character.class);
            case BYTE_ARRAY     -> TypeName.get(byte[].class);
            case URI            -> TypeName.get(URI.class);
            case URL            -> TypeName.get(URL.class);
            case UUID           -> TypeName.get(UUID.class);
            case CURRENCY       -> TypeName.get(Currency.class);
            case LOCALE         -> TypeName.get(Locale.class);
            case INSTANT        -> TypeName.get(Instant.class);
            case LOCAL_DATE     -> TypeName.get(LocalDate.class);
            case LOCAL_DATETIME -> TypeName.get(LocalDateTime.class);
            case DATE           -> TypeName.get(Date.class);
            default -> throw new IllegalArgumentException("Unsupported type: " + typeName);
        };
    }
}
