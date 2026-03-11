package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper methods for building test fixtures for converter unit tests.
 */
public class TestFixtures {

    public static Field field(String name, int index, FieldType fieldType) {
        return Field.builder()
                .name(name)
                .index(index)
                .fieldType(fieldType)
                .hashGetter(false)
                .build();
    }

    public static Field fieldWithGetter(String name, int index, FieldType fieldType, String getterName) {
        return Field.builder()
                .name(name)
                .index(index)
                .fieldType(fieldType)
                .hashGetter(true)
                .getterName(getterName)
                .build();
    }

    public static FieldType intFieldType() {
        return simpleFieldType(Type.INTEGER, JavaType.INT);
    }

    public static FieldType longFieldType() {
        return simpleFieldType(Type.INTEGER, JavaType.LONG);
    }

    public static FieldType bigIntFieldType() {
        return simpleFieldType(Type.INTEGER, JavaType.BIGINTEGER);
    }

    public static FieldType bytesFieldType() {
        return simpleFieldType(Type.BYTES, JavaType.BYTES);
    }

    public static FieldType stringFieldType() {
        return stringFieldType(null);
    }

    public static FieldType stringFieldType(String encoding) {
        FieldType ft = simpleFieldType(Type.STRING, JavaType.STRING);
        ft.setEncoding(encoding);
        return ft;
    }

    public static FieldType boolFieldType() {
        return simpleFieldType(Type.BOOL, JavaType.BOOLEAN_OBJ);
    }

    public static FieldType plutusDataFieldType() {
        return simpleFieldType(Type.PLUTUSDATA, JavaType.PLUTUSDATA);
    }

    public static FieldType constructorFieldType(String className, boolean shared, boolean rawData) {
        FieldType ft = new FieldType();
        ft.setType(Type.CONSTRUCTOR);
        ft.setJavaType(new JavaType(className, true));
        ft.setDataType(shared && !rawData);
        ft.setRawDataType(rawData);
        ft.setFqTypeName(className);
        return ft;
    }

    public static FieldType listFieldType(FieldType elementType) {
        FieldType ft = new FieldType();
        ft.setType(Type.LIST);
        ft.setJavaType(JavaType.LIST);
        ft.setCollection(true);
        ft.setFqTypeName("java.util.List<" + elementType.getFqTypeName() + ">");
        ft.setGenericTypes(Collections.singletonList(elementType));
        return ft;
    }

    public static FieldType mapFieldType(FieldType keyType, FieldType valueType) {
        FieldType ft = new FieldType();
        ft.setType(Type.MAP);
        ft.setJavaType(JavaType.MAP);
        ft.setCollection(true);
        ft.setFqTypeName("java.util.Map<" + keyType.getFqTypeName() + ", " + valueType.getFqTypeName() + ">");
        ft.setGenericTypes(Arrays.asList(keyType, valueType));
        return ft;
    }

    public static FieldType optionalFieldType(FieldType innerType) {
        FieldType ft = new FieldType();
        ft.setType(Type.OPTIONAL);
        ft.setJavaType(JavaType.OPTIONAL);
        ft.setCollection(false);
        ft.setFqTypeName("java.util.Optional<" + innerType.getFqTypeName() + ">");
        ft.setGenericTypes(Collections.singletonList(innerType));
        return ft;
    }

    public static FieldType pairFieldType(FieldType first, FieldType second) {
        return tupleFieldType(Type.PAIR, JavaType.PAIR, Arrays.asList(first, second));
    }

    public static FieldType tripleFieldType(FieldType first, FieldType second, FieldType third) {
        return tupleFieldType(Type.TRIPLE, JavaType.TRIPLE, Arrays.asList(first, second, third));
    }

    private static FieldType tupleFieldType(Type type, JavaType javaType, List<FieldType> generics) {
        FieldType ft = new FieldType();
        ft.setType(type);
        ft.setJavaType(javaType);
        ft.setCollection(false);
        ft.setGenericTypes(generics);
        return ft;
    }

    private static FieldType simpleFieldType(Type type, JavaType javaType) {
        FieldType ft = new FieldType();
        ft.setType(type);
        ft.setJavaType(javaType);
        ft.setCollection(false);
        ft.setFqTypeName(javaType.getName());
        return ft;
    }
}
