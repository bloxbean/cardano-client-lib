package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class ClassConverterBuilderTest {

    private final FieldCodeGeneratorRegistry registry = new FieldCodeGeneratorRegistry();
    private final SerDeMethodBuilder serDeBuilder = new SerDeMethodBuilder();
    private final ClassConverterBuilder builder = new ClassConverterBuilder(registry, serDeBuilder);

    private ClassDefinition classDef(String className, String objType, int alternative, Field... fields) {
        ClassDefinition cd = new ClassDefinition();
        cd.setConverterClassName(className);
        cd.setObjType(objType);
        cd.setAlternative(alternative);
        cd.setFields(Arrays.asList(fields));
        return cd;
    }

    @Nested
    class Build {
        @Test
        void generatesTypeSpecWith6Methods() {
            ClassDefinition cd = classDef("MyModelConverter", "com.example.MyModel", 0,
                    field("name", 0, stringFieldType()),
                    field("age", 1, intFieldType()));

            TypeSpec typeSpec = builder.build(cd);
            assertThat(typeSpec.name).isEqualTo("MyModelConverter");
            assertThat(typeSpec.methodSpecs).hasSize(6);

            var methodNames = typeSpec.methodSpecs.stream().map(m -> m.name).toList();
            assertThat(methodNames).containsExactly(
                    "toPlutusData", "fromPlutusData", "serialize", "serializeToHex", "deserialize", "deserialize");
        }
    }

    @Nested
    class ToPlutusData {
        @Test
        void iteratesFieldsAndGeneratesInitConstr() {
            ClassDefinition cd = classDef("TestConverter", "com.example.Test", 1,
                    field("value", 0, intFieldType()));

            TypeSpec typeSpec = builder.build(cd);
            MethodSpec toPlutusData = typeSpec.methodSpecs.stream()
                    .filter(m -> m.name.equals("toPlutusData")).findFirst().orElseThrow();

            String code = toPlutusData.toString();
            assertThat(code).contains("initConstr(1)");
            assertThat(code).contains("//Field value");
            assertThat(code).contains("return constr");
        }
    }

    @Nested
    class FromPlutusData {
        @Test
        void generatesObjectInstantiationAndFieldAssignment() {
            ClassDefinition cd = classDef("TestConverter", "com.example.Test", 0,
                    field("name", 0, stringFieldType()));

            TypeSpec typeSpec = builder.build(cd);
            MethodSpec fromPlutusData = typeSpec.methodSpecs.stream()
                    .filter(m -> m.name.equals("fromPlutusData")).findFirst().orElseThrow();

            String code = fromPlutusData.toString();
            assertThat(code).contains("var obj = new");
            assertThat(code).contains("var constrData = constr.getData()");
            assertThat(code).contains("//Field name");
            assertThat(code).contains("obj.name = name");
            assertThat(code).contains("return obj");
        }

        @Test
        void usesSetterForGetterFields() {
            ClassDefinition cd = classDef("TestConverter", "com.example.Test", 0,
                    fieldWithGetter("amount", 0, intFieldType(), "getAmount"));

            TypeSpec typeSpec = builder.build(cd);
            MethodSpec fromPlutusData = typeSpec.methodSpecs.stream()
                    .filter(m -> m.name.equals("fromPlutusData")).findFirst().orElseThrow();

            String code = fromPlutusData.toString();
            assertThat(code).contains("obj.setAmount(amount)");
        }
    }
}
