package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.JavaType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quartet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quintet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Triple;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detects {@link FieldType} from a JavaPoet {@link TypeName}.
 * Handles primitives, collections, optionals, tuples, and PlutusData.
 * <p>
 * Returns {@code null} for unrecognized types — the caller handles
 * CONSTRUCTOR fallback (which may require TypeMirror or registry-based
 * interface checks).
 * <p>
 * Shared by {@code ClassDefinitionGenerator} (Phase 2, TypeMirror available)
 * and {@code FieldSpecProcessor} (Phase 1, TypeName only).
 */
public final class FieldTypeDetector {

    private FieldTypeDetector() {
    }

    /**
     * Detects the {@link FieldType} from a JavaPoet {@link TypeName}.
     *
     * @param typeName the type to detect
     * @return the detected FieldType, or {@code null} if the type is not a
     *         recognized primitive, collection, optional, tuple, or PlutusData
     */
    public static FieldType fromTypeName(TypeName typeName) {
        FieldType fieldType = new FieldType();
        fieldType.setFqTypeName(typeName.toString());

        // Simple (non-generic) types
        if (typeName.equals(TypeName.get(Long.class))) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.LONG_OBJECT);
        } else if (typeName.equals(TypeName.LONG)) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.LONG);
        } else if (typeName.equals(TypeName.get(BigInteger.class))) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.BIGINTEGER);
        } else if (typeName.equals(TypeName.get(Integer.class))) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.INTEGER);
        } else if (typeName.equals(TypeName.INT)) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.INT);
        } else if (typeName.equals(TypeName.get(String.class))) {
            fieldType.setType(Type.STRING);
            fieldType.setJavaType(JavaType.STRING);
        } else if (typeName.equals(TypeName.get(byte[].class))) {
            fieldType.setType(Type.BYTES);
            fieldType.setJavaType(JavaType.BYTES);
        } else if (typeName.equals(TypeName.get(Boolean.class))) {
            fieldType.setType(Type.BOOL);
            fieldType.setJavaType(JavaType.BOOLEAN_OBJ);
        } else if (typeName.equals(TypeName.BOOLEAN)) {
            fieldType.setType(Type.BOOL);
            fieldType.setJavaType(JavaType.BOOLEAN);
        } else if (typeName.equals(TypeName.get(PlutusData.class))) {
            fieldType.setType(Type.PLUTUSDATA);
            fieldType.setJavaType(JavaType.PLUTUSDATA);

        // Parameterized collection/optional types
        } else if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(ClassName.get(List.class))) {
            fieldType.setType(Type.LIST);
            fieldType.setJavaType(JavaType.LIST);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(fromTypeNameOrConstructor(ptn.typeArguments.get(0)));
        } else if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(ClassName.get(Map.class))) {
            fieldType.setType(Type.MAP);
            fieldType.setJavaType(JavaType.MAP);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(fromTypeNameOrConstructor(ptn.typeArguments.get(0)));
            fieldType.getGenericTypes().add(fromTypeNameOrConstructor(ptn.typeArguments.get(1)));
        } else if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(ClassName.get(Optional.class))) {
            fieldType.setType(Type.OPTIONAL);
            fieldType.setJavaType(JavaType.OPTIONAL);
            fieldType.getGenericTypes().add(fromTypeNameOrConstructor(ptn.typeArguments.get(0)));

        // Parameterized tuple types
        } else if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(ClassName.get(Pair.class))) {
            fieldType.setType(Type.PAIR);
            fieldType.setJavaType(JavaType.PAIR);
            for (TypeName arg : ptn.typeArguments) {
                fieldType.getGenericTypes().add(fromTypeNameOrConstructor(arg));
            }
        } else if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(ClassName.get(Triple.class))) {
            fieldType.setType(Type.TRIPLE);
            fieldType.setJavaType(JavaType.TRIPLE);
            for (TypeName arg : ptn.typeArguments) {
                fieldType.getGenericTypes().add(fromTypeNameOrConstructor(arg));
            }
        } else if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(ClassName.get(Quartet.class))) {
            fieldType.setType(Type.QUARTET);
            fieldType.setJavaType(JavaType.QUARTET);
            for (TypeName arg : ptn.typeArguments) {
                fieldType.getGenericTypes().add(fromTypeNameOrConstructor(arg));
            }
        } else if (typeName instanceof ParameterizedTypeName ptn && ptn.rawType.equals(ClassName.get(Quintet.class))) {
            fieldType.setType(Type.QUINTET);
            fieldType.setJavaType(JavaType.QUINTET);
            for (TypeName arg : ptn.typeArguments) {
                fieldType.getGenericTypes().add(fromTypeNameOrConstructor(arg));
            }

        // Raw (unparameterized) tuple types — treated as opaque PlutusData
        } else if (typeName.equals(ClassName.get(Pair.class))
                || typeName.equals(ClassName.get(Triple.class))
                || typeName.equals(ClassName.get(Quartet.class))
                || typeName.equals(ClassName.get(Quintet.class))) {
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(true);
        } else {
            // Not a recognized type — caller must handle CONSTRUCTOR fallback
            return null;
        }

        return fieldType;
    }

    /**
     * Like {@link #fromTypeName(TypeName)} but falls back to a default CONSTRUCTOR
     * FieldType for unrecognized types. Used for recursive generic-type detection
     * (e.g., List&lt;SomeClass&gt;) where we cannot access TypeMirror.
     */
    private static FieldType fromTypeNameOrConstructor(TypeName typeName) {
        FieldType ft = fromTypeName(typeName);
        if (ft != null) return ft;

        FieldType fieldType = new FieldType();
        fieldType.setFqTypeName(typeName.toString());
        fieldType.setType(Type.CONSTRUCTOR);
        fieldType.setJavaType(new JavaType(typeName.toString(), true));
        return fieldType;
    }
}
