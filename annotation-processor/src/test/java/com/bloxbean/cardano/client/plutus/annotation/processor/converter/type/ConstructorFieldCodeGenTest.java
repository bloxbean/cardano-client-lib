package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructorFieldCodeGenTest {

    private final ConstructorFieldCodeGen gen = new ConstructorFieldCodeGen();
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void sharedType_callsToPlutusDataDirectly() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", true, false);
            Field field = TestFixtures.field("myObj", 0, ft);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("obj.myObj.toPlutusData()");
            assertThat(code).contains("if(obj.myObj != null)");
        }

        @Test
        void nonSharedType_usesConverterClass() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", false, false);
            Field field = TestFixtures.field("myObj", 0, ft);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("new");
            assertThat(code).contains("toPlutusData(obj.myObj)");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void sharedConstrType_usesStaticFromPlutusDataWithCast() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", true, false);
            Field field = TestFixtures.field("myObj", 0, ft);
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("MyType.fromPlutusData");
            assertThat(code).contains("ConstrPlutusData");
        }

        @Test
        void sharedRawType_usesStaticFromPlutusDataWithoutCast() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", true, true);
            Field field = TestFixtures.field("myObj", 0, ft);
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("MyType.fromPlutusData");
            assertThat(code).doesNotContain("ConstrPlutusData");
        }

        @Test
        void nonSharedConstrType_usesConverterWithCast() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", false, false);
            Field field = TestFixtures.field("myObj", 0, ft);
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("new");
            assertThat(code).contains("fromPlutusData");
            assertThat(code).contains("ConstrPlutusData");
        }
    }

    @Nested
    class Expressions {
        @Test
        void sharedTypeToPlutusData() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", true, false);
            assertThat(gen.toPlutusDataExpression(ft, "obj")).isEqualTo("obj.toPlutusData()");
        }

        @Test
        void sharedRawTypeFromPlutusData() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", true, true);
            assertThat(gen.fromPlutusDataExpression(ft, "pd")).contains("MyType.fromPlutusData(pd)");
            assertThat(gen.fromPlutusDataExpression(ft, "pd")).doesNotContain("ConstrPlutusData");
        }

        @Test
        void sharedConstrTypeFromPlutusData() {
            FieldType ft = TestFixtures.constructorFieldType("com.example.MyType", true, false);
            assertThat(gen.fromPlutusDataExpression(ft, "pd")).contains("ConstrPlutusData");
        }
    }
}
