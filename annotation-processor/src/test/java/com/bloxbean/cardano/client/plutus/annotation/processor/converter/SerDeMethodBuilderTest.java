package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SerDeMethodBuilderTest {

    private final SerDeMethodBuilder builder = new SerDeMethodBuilder();

    private ClassDefinition classDef(String objType) {
        ClassDefinition cd = new ClassDefinition();
        cd.setObjType(objType);
        cd.setConverterClassName("TestConverter");
        return cd;
    }

    @Nested
    class Serialize {
        @Test
        void generatesSerializeMethod() {
            MethodSpec method = builder.serialize(classDef("com.example.MyModel"));
            assertThat(method.name).isEqualTo("serialize");
            assertThat(method.returnType).isEqualTo(TypeName.get(byte[].class));
            String code = method.toString();
            assertThat(code).contains("CborSerializationUtil.serialize");
            assertThat(code).contains("toPlutusData(obj)");
        }
    }

    @Nested
    class SerializeToHex {
        @Test
        void generatesSerializeToHexMethod() {
            MethodSpec method = builder.serializeToHex(classDef("com.example.MyModel"));
            assertThat(method.name).isEqualTo("serializeToHex");
            String code = method.toString();
            assertThat(code).contains("toPlutusData(obj)");
            assertThat(code).contains("serializeToHex()");
        }
    }

    @Nested
    class Deserialize {
        @Test
        void generatesDeserializeMethod() {
            MethodSpec method = builder.deserialize(classDef("com.example.MyModel"));
            assertThat(method.name).isEqualTo("deserialize");
            String code = method.toString();
            assertThat(code).contains("CborSerializationUtil.deserialize");
            assertThat(code).contains("fromPlutusData(constr)");
        }
    }

    @Nested
    class DeserializeFromHex {
        @Test
        void generatesHexDeserializeMethod() {
            MethodSpec method = builder.deserializeFromHex(classDef("com.example.MyModel"));
            assertThat(method.name).isEqualTo("deserialize");
            String code = method.toString();
            assertThat(code).contains("HexUtil.decodeHexString");
            assertThat(code).contains("deserialize(bytes)");
        }
    }

    @Nested
    class BestGuess {
        @Test
        void intMapsToInteger() {
            assertThat(SerDeMethodBuilder.bestGuess("int")).isEqualTo(ClassName.get(Integer.class));
        }

        @Test
        void longMapsToLong() {
            assertThat(SerDeMethodBuilder.bestGuess("long")).isEqualTo(ClassName.get(Long.class));
        }

        @Test
        void byteArrayMapsToByteArray() {
            assertThat(SerDeMethodBuilder.bestGuess("byte[]")).isEqualTo(ArrayTypeName.of(TypeName.BYTE));
        }

        @Test
        void fqnMapsToClassName() {
            assertThat(SerDeMethodBuilder.bestGuess("com.example.MyModel"))
                    .isEqualTo(ClassName.bestGuess("com.example.MyModel"));
        }
    }
}
