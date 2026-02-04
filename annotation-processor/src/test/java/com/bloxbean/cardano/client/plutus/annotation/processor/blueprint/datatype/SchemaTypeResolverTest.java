package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.FieldSpecProcessor;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaTypeResolverTest {

    private final FieldSpecProcessor fieldSpecProcessor = mock(FieldSpecProcessor.class);
    private final SchemaTypeResolver resolver = new SchemaTypeResolver(fieldSpecProcessor);

    @Test
    void resolveType_shouldReturnPrimitiveTypes() {
        assertThat(resolver.resolveType("ns", schema(BlueprintDatatype.bytes))).isEqualTo(TypeName.get(byte[].class));
        assertThat(resolver.resolveType("ns", schema(BlueprintDatatype.integer))).isEqualTo(TypeName.get(java.math.BigInteger.class));
        assertThat(resolver.resolveType("ns", schema(BlueprintDatatype.bool))).isEqualTo(TypeName.get(Boolean.class));
        assertThat(resolver.resolveType("ns", schema(BlueprintDatatype.string))).isEqualTo(TypeName.get(String.class));
    }

    @Test
    void resolveType_shouldHandleList() {
        BlueprintSchema item = schema(BlueprintDatatype.string);
        BlueprintSchema listSchema = schema(BlueprintDatatype.list);
        listSchema.setItems(List.of(item));

        TypeName typeName = resolver.resolveType("validators", listSchema);

        assertThat(typeName).isEqualTo(
                ParameterizedTypeName.get(ClassName.get("java.util", "List"), TypeName.get(String.class))
        );
    }

    @Test
    void resolveType_shouldHandleTupleDefinitionsAsPair() {
        BlueprintSchema first = schema(BlueprintDatatype.bytes);
        BlueprintSchema second = schema(BlueprintDatatype.string);

        BlueprintSchema tupleSchema = schema(BlueprintDatatype.list);
        tupleSchema.setTitle("Tuple");
        tupleSchema.setItems(List.of(first, second));

        TypeName typeName = resolver.resolveType("validators", tupleSchema);

        assertThat(typeName).isEqualTo(
                ParameterizedTypeName.get(
                        ClassName.get("com.bloxbean.cardano.client.plutus.blueprint.type", "Pair"),
                        TypeName.get(byte[].class),
                        TypeName.get(String.class)
                )
        );
    }

    @Test
    void resolveType_shouldHandleMap() {
        BlueprintSchema mapSchema = schema(BlueprintDatatype.map);
        mapSchema.setKeys(schema(BlueprintDatatype.string));
        mapSchema.setValues(schema(BlueprintDatatype.integer));

        TypeName typeName = resolver.resolveType("validators", mapSchema);

        assertThat(typeName).isEqualTo(
                ParameterizedTypeName.get(
                        ClassName.get("java.util", "Map"),
                        TypeName.get(String.class),
                        TypeName.get(java.math.BigInteger.class)
                ));
    }

    @Test
    void resolveType_shouldHandleOption() {
        BlueprintSchema someField = schema(BlueprintDatatype.integer);
        BlueprintSchema someSchema = schema(BlueprintDatatype.constructor);
        someSchema.setTitle("Some");
        someSchema.setFields(java.util.List.of(someField));

        BlueprintSchema noneSchema = schema(BlueprintDatatype.constructor);
        noneSchema.setTitle("None");
        noneSchema.setFields(java.util.List.of());

        BlueprintSchema optionSchema = schema(BlueprintDatatype.option);
        optionSchema.setAnyOf(java.util.List.of(someSchema, noneSchema));

        TypeName typeName = resolver.resolveType("validators", optionSchema);

        assertThat(typeName).isEqualTo(
                ParameterizedTypeName.get(
                        ClassName.get("java.util", "Optional"),
                        TypeName.get(java.math.BigInteger.class)
                ));
    }

    @Test
    void resolveType_shouldHandlePair() {
        BlueprintSchema pairSchema = schema(BlueprintDatatype.pair);
        pairSchema.setLeft(schema(BlueprintDatatype.string));
        pairSchema.setRight(schema(BlueprintDatatype.bool));

        TypeName typeName = resolver.resolveType("validators", pairSchema);

        assertThat(typeName).isEqualTo(
                ParameterizedTypeName.get(
                        ClassName.get("com.bloxbean.cardano.client.plutus.blueprint.type", "Pair"),
                        TypeName.get(String.class),
                        TypeName.get(Boolean.class)
                ));
    }

    @Test
    void resolveType_shouldDelegateToFieldSpecProcessorWhenDatatypeNull() {
        BlueprintSchema nestedSchema = new BlueprintSchema();
        ClassName innerClass = ClassName.get("com.test", "Inner");
        when(fieldSpecProcessor.getInnerDatumClass(eq("validators"), eq(nestedSchema))).thenReturn(innerClass);

        TypeName typeName = resolver.resolveType("validators", nestedSchema);

        assertThat(typeName).isEqualTo(innerClass);
    }

    @Test
    void resolveListType_withNestedSchemaWithoutDatatype_shouldUseFieldSpecProcessor() {
        BlueprintSchema itemSchema = new BlueprintSchema();
        ClassName innerClass = ClassName.get("com.test", "Inner");
        when(fieldSpecProcessor.getInnerDatumClass(anyString(), eq(itemSchema))).thenReturn(innerClass);

        BlueprintSchema listSchema = schema(BlueprintDatatype.list);
        listSchema.setItems(List.of(itemSchema));

        TypeName typeName = resolver.resolveType("validators", listSchema);

        assertThat(typeName).isEqualTo(
                ParameterizedTypeName.get(ClassName.get("java.util", "List"), innerClass)
        );
    }

    private BlueprintSchema schema(BlueprintDatatype datatype) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(datatype);
        schema.setTitle(datatype != null ? datatype.name() : "Custom");
        return schema;
    }
}
