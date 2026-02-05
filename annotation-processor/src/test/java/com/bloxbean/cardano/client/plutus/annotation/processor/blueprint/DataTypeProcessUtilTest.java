package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.DefaultNamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.PackageResolver;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataTypeProcessUtilTest {

    private FieldSpecProcessor fieldSpecProcessor;
    private DataTypeProcessUtil dataTypeProcessUtil;
    private SharedTypeLookup sharedTypeLookup;

    @BeforeEach
    void setup() {
        fieldSpecProcessor = mock(FieldSpecProcessor.class);
        NamingStrategy nameStrategy = new DefaultNamingStrategy();
        PackageResolver packageResolver = new PackageResolver();
        sharedTypeLookup = SharedTypeLookup.disabled();
        dataTypeProcessUtil = new DataTypeProcessUtil(fieldSpecProcessor, blueprint("com.test.blueprint"), nameStrategy, packageResolver, sharedTypeLookup);
    }

    @Test
    void generateFieldSpecs_shouldUseSharedTypeWhenAvailable() {
        sharedTypeLookup = (namespace, schema) -> Optional.of(ClassName.get("com.example", "Shared"));
        NamingStrategy nameStrategy = new DefaultNamingStrategy();
        PackageResolver packageResolver = new PackageResolver();
        dataTypeProcessUtil = new DataTypeProcessUtil(fieldSpecProcessor, blueprint("com.test.blueprint"), nameStrategy, packageResolver, sharedTypeLookup);

        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("shared_value");

        List<FieldSpec> specs = dataTypeProcessUtil.generateFieldSpecs("ns", "", schema, "", "shared_value");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).type).isEqualTo(ClassName.get("com.example", "Shared"));
        assertThat(specs.get(0).name).isEqualTo("sharedValue");
    }

    @Test
    void generateFieldSpecs_shouldHandlePrimitiveTypes() {
        BlueprintSchema bytesSchema = schema(BlueprintDatatype.bytes, "payload");
        List<FieldSpec> specs = dataTypeProcessUtil.generateFieldSpecs("validators", "payload", bytesSchema, "", "payload");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).type).isEqualTo(TypeName.get(byte[].class));
        assertThat(specs.get(0).name).isEqualTo("payload");
    }

    @Test
    void generateFieldSpecs_shouldHandleListTypes() {
        BlueprintSchema listSchema = schema(BlueprintDatatype.list, "items");
        BlueprintSchema itemSchema = schema(BlueprintDatatype.string, "name");
        listSchema.setItems(List.of(itemSchema));

        List<FieldSpec> specs = dataTypeProcessUtil.generateFieldSpecs("validators", "items", listSchema, "", "items");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).type.toString()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(specs.get(0).name).isEqualTo("items");
    }

    @Test
    void generateFieldSpecs_shouldHandleOptionTypes() {
        BlueprintSchema optionSchema = schema(BlueprintDatatype.option, "maybeAmount");
        BlueprintSchema someField = schema(BlueprintDatatype.integer, "amount");
        BlueprintSchema someSchema = schema(BlueprintDatatype.constructor, "Some");
        someSchema.setFields(List.of(someField));
        BlueprintSchema noneSchema = schema(BlueprintDatatype.constructor, "None");
        noneSchema.setFields(List.of());
        optionSchema.setAnyOf(List.of(someSchema, noneSchema));

        List<FieldSpec> specs = dataTypeProcessUtil.generateFieldSpecs("validators", "maybeAmount", optionSchema, "", "maybeAmount");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).type.toString()).isEqualTo("java.util.Optional<java.math.BigInteger>");
        assertThat(specs.get(0).name).isEqualTo("maybeAmount");
    }

    @Test
    void generateFieldSpecs_shouldHandlePairTypes() {
        BlueprintSchema pairSchema = schema(BlueprintDatatype.pair, "coordinates");
        pairSchema.setLeft(schema(BlueprintDatatype.integer, "x"));
        pairSchema.setRight(schema(BlueprintDatatype.integer, "y"));

        List<FieldSpec> specs = dataTypeProcessUtil.generateFieldSpecs("validators", "coordinates", pairSchema, "", "coordinates");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).type.toString()).isEqualTo("com.bloxbean.cardano.client.plutus.blueprint.type.Pair<java.math.BigInteger, java.math.BigInteger>");
    }

    @Test
    void generateFieldSpecs_shouldHandleConstructorTypes() {
        BlueprintSchema constructorSchema = schema(BlueprintDatatype.constructor, "Action");
        BlueprintSchema field = schema(BlueprintDatatype.integer, "amount");
        constructorSchema.setFields(List.of(field));

        FieldSpec mockedField = FieldSpec.builder(BigInteger.class, "amount").build();
        when(fieldSpecProcessor.createFieldSpecForDataTypes(anyString(), anyString(), eq(field), anyString(), anyString()))
                .thenReturn(List.of(mockedField));

        List<FieldSpec> specs = dataTypeProcessUtil.generateFieldSpecs("validators", "Action", constructorSchema, "Action", "Action");

        assertThat(specs).containsExactly(mockedField);
    }

    @Test
    void generateFieldSpecs_shouldHandlePlutusDataWhenDatatypeNull() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("customData");

        List<FieldSpec> specs = dataTypeProcessUtil.generateFieldSpecs("validators", "customData", schema, "", "customData");

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).type.toString()).isEqualTo("com.bloxbean.cardano.client.plutus.spec.PlutusData");
    }

    private BlueprintSchema schema(BlueprintDatatype datatype, String title) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(datatype);
        schema.setTitle(title);
        return schema;
    }

    private Blueprint blueprint(String packageName) {
        return new Blueprint() {
            @Override
            public String file() {
                return "";
            }

            @Override
            public String fileInResources() {
                return "";
            }

            @Override
            public String packageName() {
                return packageName;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Blueprint.class;
            }
        };
    }
}
