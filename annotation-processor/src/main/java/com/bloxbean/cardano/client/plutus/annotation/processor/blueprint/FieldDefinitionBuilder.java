package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quartet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quintet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Triple;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.squareup.javapoet.*;

import java.math.BigInteger;
import java.util.*;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.CONVERTER;
import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.IMPL;

/**
 * Maps JavaPoet {@link FieldSpec}/{@link TypeName} into the annotation processor's
 * {@link Field}/{@link FieldType}/{@link ClassDefinition} model.
 * <p>
 * Used in Phase 1 ({@link FieldSpecProcessor}) to build {@link ClassDefinition}s
 * from blueprint-generated JavaPoet types, enabling converter generation via
 * {@link com.bloxbean.cardano.client.plutus.annotation.processor.ConverterCodeGenerator}
 * without needing compiled {@code TypeElement}s (Phase 2 data).
 */
public class FieldDefinitionBuilder {

    private final GeneratedTypesRegistry registry;

    public FieldDefinitionBuilder(GeneratedTypesRegistry registry) {
        this.registry = registry;
    }

    /**
     * Builds a {@link ClassDefinition} for a variant inner class.
     *
     * @param pkg            model package
     * @param interfaceName  enclosing interface name (e.g., "Credential")
     * @param variantName    variant class name (e.g., "VerificationKey")
     * @param alternative    Plutus constructor alternative index
     * @param fieldSpecs     JavaPoet field specs for this variant
     * @return fully populated ClassDefinition
     */
    public ClassDefinition buildVariantClassDefinition(String pkg,
                                                        String interfaceName,
                                                        String variantName,
                                                        int alternative,
                                                        List<FieldSpec> fieldSpecs) {
        ClassDefinition def = new ClassDefinition();
        def.setPackageName(pkg);
        def.setDataClassName(variantName);
        def.setObjType(pkg + "." + interfaceName + "." + variantName);
        def.setAlternative(alternative);
        def.setAbstract(true);
        def.setHasLombokAnnotation(false);

        // Converter is nested inside the interface
        def.setConverterClassName(variantName + CONVERTER);
        def.setConverterPackageName(pkg);
        def.setEnclosingInterfaceName(interfaceName);

        // Impl stays prefixed and in impl sub-package
        def.setImplClassName(interfaceName + variantName + IMPL);
        def.setImplPackageName(pkg + ".impl");

        // Convert fields
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < fieldSpecs.size(); i++) {
            fields.add(fromFieldSpec(fieldSpecs.get(i), i));
        }
        def.setFields(fields);

        return def;
    }

    /**
     * Builds a {@link ClassDefinition} for an interface (dispatch converter).
     */
    public ClassDefinition buildInterfaceClassDefinition(String pkg, String interfaceName) {
        ClassDefinition def = new ClassDefinition();
        def.setPackageName(pkg);
        def.setDataClassName(interfaceName);
        def.setObjType(pkg + "." + interfaceName);
        def.setAlternative(0);
        def.setAbstract(false);
        def.setHasLombokAnnotation(false);

        // Converter is nested inside the interface itself
        def.setConverterClassName(interfaceName + CONVERTER);
        def.setConverterPackageName(pkg);
        def.setEnclosingInterfaceName(interfaceName);

        // No impl for interfaces
        def.setImplClassName(interfaceName + IMPL);
        def.setImplPackageName(pkg + ".impl");

        return def;
    }

    /**
     * Converts a JavaPoet {@link FieldSpec} to a model {@link Field}.
     */
    Field fromFieldSpec(FieldSpec fieldSpec, int index) {
        TypeName typeName = fieldSpec.type;
        FieldType fieldType = detectFieldType(typeName);

        String fieldName = fieldSpec.name;
        String getterName;
        if (Type.BOOL.equals(fieldType.getType())
                && JavaType.BOOLEAN.equals(fieldType.getJavaType())) {
            getterName = "is" + capitalize(fieldName);
        } else {
            getterName = "get" + capitalize(fieldName);
        }

        return Field.builder()
                .name(fieldName)
                .index(index)
                .fieldType(fieldType)
                .hashGetter(true)
                .getterName(getterName)
                .build();
    }

    /**
     * Detects the {@link FieldType} from a JavaPoet {@link TypeName}.
     * Simplified version of {@code ClassDefinitionGenerator.detectFieldType()}
     * that works without {@code TypeMirror} (no compiled types needed).
     */
    FieldType detectFieldType(TypeName typeName) {
        FieldType fieldType = new FieldType();
        fieldType.setFqTypeName(typeName.toString());

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
        } else if (isParameterizedType(typeName, List.class)) {
            ParameterizedTypeName ptn = (ParameterizedTypeName) typeName;
            fieldType.setType(Type.LIST);
            fieldType.setJavaType(JavaType.LIST);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0)));
        } else if (isParameterizedType(typeName, Map.class)) {
            ParameterizedTypeName ptn = (ParameterizedTypeName) typeName;
            fieldType.setType(Type.MAP);
            fieldType.setJavaType(JavaType.MAP);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0)));
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(1)));
        } else if (isParameterizedType(typeName, Optional.class)) {
            ParameterizedTypeName ptn = (ParameterizedTypeName) typeName;
            fieldType.setType(Type.OPTIONAL);
            fieldType.setJavaType(JavaType.OPTIONAL);
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0)));
        } else if (isParameterizedType(typeName, Pair.class)) {
            ParameterizedTypeName ptn = (ParameterizedTypeName) typeName;
            fieldType.setType(Type.PAIR);
            fieldType.setJavaType(JavaType.PAIR);
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0)));
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(1)));
        } else if (typeName.equals(ClassName.get(Pair.class))) {
            // Raw Pair type
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(true);
        } else if (isParameterizedType(typeName, Triple.class)) {
            ParameterizedTypeName ptn = (ParameterizedTypeName) typeName;
            fieldType.setType(Type.TRIPLE);
            fieldType.setJavaType(JavaType.TRIPLE);
            for (TypeName arg : ptn.typeArguments) {
                fieldType.getGenericTypes().add(detectFieldType(arg));
            }
        } else if (typeName.equals(ClassName.get(Triple.class))) {
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(true);
        } else if (isParameterizedType(typeName, Quartet.class)) {
            ParameterizedTypeName ptn = (ParameterizedTypeName) typeName;
            fieldType.setType(Type.QUARTET);
            fieldType.setJavaType(JavaType.QUARTET);
            for (TypeName arg : ptn.typeArguments) {
                fieldType.getGenericTypes().add(detectFieldType(arg));
            }
        } else if (typeName.equals(ClassName.get(Quartet.class))) {
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(true);
        } else if (isParameterizedType(typeName, Quintet.class)) {
            ParameterizedTypeName ptn = (ParameterizedTypeName) typeName;
            fieldType.setType(Type.QUINTET);
            fieldType.setJavaType(JavaType.QUINTET);
            for (TypeName arg : ptn.typeArguments) {
                fieldType.getGenericTypes().add(detectFieldType(arg));
            }
        } else if (typeName.equals(ClassName.get(Quintet.class))) {
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(true);
        } else {
            // Default: CONSTRUCTOR type (user-defined class)
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));

            // Check if the type is a known interface with nested converters
            if (typeName instanceof ClassName) {
                ClassName cn = (ClassName) typeName;
                if (registry != null && registry.isInterface(cn.packageName(), cn.simpleName())) {
                    fieldType.setInterfaceType(true);
                }
            }
        }

        return fieldType;
    }

    private static boolean isParameterizedType(TypeName typeName, Class<?> rawType) {
        return typeName instanceof ParameterizedTypeName
                && ((ParameterizedTypeName) typeName).rawType.equals(ClassName.get(rawType));
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
