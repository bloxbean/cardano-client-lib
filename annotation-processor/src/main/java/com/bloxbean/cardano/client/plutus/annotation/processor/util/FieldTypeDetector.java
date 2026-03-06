package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.ClassDefinitionGenerator;
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
import java.util.Set;
import java.util.function.BiPredicate;

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

    private record SimpleMapping(Type type, JavaType javaType) {}

    private record ParameterizedMapping(Type type, JavaType javaType, boolean collection) {}

    private static final Map<TypeName, SimpleMapping> SIMPLE_TYPES = Map.ofEntries(
            Map.entry(TypeName.get(Long.class),       new SimpleMapping(Type.INTEGER, JavaType.LONG_OBJECT)),
            Map.entry(TypeName.LONG,                  new SimpleMapping(Type.INTEGER, JavaType.LONG)),
            Map.entry(TypeName.get(BigInteger.class),  new SimpleMapping(Type.INTEGER, JavaType.BIGINTEGER)),
            Map.entry(TypeName.get(Integer.class),     new SimpleMapping(Type.INTEGER, JavaType.INTEGER)),
            Map.entry(TypeName.INT,                   new SimpleMapping(Type.INTEGER, JavaType.INT)),
            Map.entry(TypeName.get(String.class),      new SimpleMapping(Type.STRING, JavaType.STRING)),
            Map.entry(TypeName.get(byte[].class),      new SimpleMapping(Type.BYTES, JavaType.BYTES)),
            Map.entry(TypeName.get(Boolean.class),     new SimpleMapping(Type.BOOL, JavaType.BOOLEAN_OBJ)),
            Map.entry(TypeName.BOOLEAN,               new SimpleMapping(Type.BOOL, JavaType.BOOLEAN)),
            Map.entry(TypeName.get(PlutusData.class),  new SimpleMapping(Type.PLUTUSDATA, JavaType.PLUTUSDATA))
    );

    private static final Map<ClassName, ParameterizedMapping> PARAMETERIZED_TYPES = Map.of(
            ClassName.get(List.class),     new ParameterizedMapping(Type.LIST, JavaType.LIST, true),
            ClassName.get(Map.class),      new ParameterizedMapping(Type.MAP, JavaType.MAP, true),
            ClassName.get(Optional.class), new ParameterizedMapping(Type.OPTIONAL, JavaType.OPTIONAL, false),
            ClassName.get(Pair.class),     new ParameterizedMapping(Type.PAIR, JavaType.PAIR, false),
            ClassName.get(Triple.class),   new ParameterizedMapping(Type.TRIPLE, JavaType.TRIPLE, false),
            ClassName.get(Quartet.class),  new ParameterizedMapping(Type.QUARTET, JavaType.QUARTET, false),
            ClassName.get(Quintet.class),  new ParameterizedMapping(Type.QUINTET, JavaType.QUINTET, false)
    );

    private static final Set<ClassName> RAW_TUPLE_TYPES = Set.of(
            ClassName.get(Pair.class),
            ClassName.get(Triple.class),
            ClassName.get(Quartet.class),
            ClassName.get(Quintet.class)
    );

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
        SimpleMapping simple = SIMPLE_TYPES.get(typeName);
        if (simple != null) {
            fieldType.setType(simple.type());
            fieldType.setJavaType(simple.javaType());
            return fieldType;
        }

        // Parameterized types (collections, optionals, tuples)
        if (typeName instanceof ParameterizedTypeName ptn) {
            ParameterizedMapping mapping = PARAMETERIZED_TYPES.get(ptn.rawType);
            if (mapping != null) {
                fieldType.setType(mapping.type());
                fieldType.setJavaType(mapping.javaType());
                fieldType.setCollection(mapping.collection());
                for (TypeName arg : ptn.typeArguments) {
                    fieldType.getGenericTypes().add(fromTypeNameOrConstructor(arg));
                }
                return fieldType;
            }
        }

        // Raw (unparameterized) tuple types — treated as opaque PlutusData
        if (typeName instanceof ClassName cn && RAW_TUPLE_TYPES.contains(cn)) {
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(true);
            return fieldType;
        }

        // Not a recognized type — caller must handle CONSTRUCTOR fallback
        return null;
    }

    /**
     * Recursively walks generic type arguments and sets {@code converterClassFqn}
     * for CONSTRUCTOR-typed fields. Shared by both Phase 1 (blueprint/FieldSpecProcessor)
     * and Phase 2 ({@code @Constr}/ClassDefinitionGenerator).
     *
     * @param fieldType   the field type whose generic arguments to process
     * @param isInterface predicate that checks (packageName, simpleName) → true if the type is an interface
     */
    public static void resolveConverterFqns(FieldType fieldType,
                                             BiPredicate<String, String> isInterface) {
        for (FieldType genericType : fieldType.getGenericTypes()) {
            if (genericType.getType() == Type.CONSTRUCTOR) {
                String typeFqn = genericType.getJavaType().getName();
                try {
                    ClassName cn = ClassName.bestGuess(typeFqn);
                    boolean isIface = isInterface.test(cn.packageName(), cn.simpleName());
                    genericType.setConverterClassFqn(
                            ClassDefinitionGenerator.resolveConverterFqn(cn, isIface));
                } catch (IllegalArgumentException ignored) {
                    // bestGuess can fail for parameterized types — skip
                }
            }
            resolveConverterFqns(genericType, isInterface);
        }
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
