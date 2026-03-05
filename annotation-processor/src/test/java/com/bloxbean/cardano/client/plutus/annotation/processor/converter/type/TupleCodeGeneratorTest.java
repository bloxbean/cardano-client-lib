package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.TupleInfo;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.squareup.javapoet.CodeBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class TupleCodeGeneratorTest {

    private final TupleCodeGenerator gen = new TupleCodeGenerator();

    // Simple element generator for testing: wraps expression in "toPlutusData(expr)"
    private final ElementCodeGenerator simpleSerGen = (type, baseName, outputVarName, expression) ->
            CodeBlock.builder().addStatement("var $L = toPlutusData($L)", outputVarName, expression).build();

    // Simple element deserializer for testing
    private final ElementCodeGenerator simpleDeserGen = (type, baseName, outputVarName, expression) ->
            CodeBlock.builder().addStatement("var $L = fromPlutusData($L)", outputVarName, expression).build();

    @Nested
    class TopLevelSerialization {
        @Test
        void pairProduces2ElementList() {
            FieldType pairType = pairFieldType(intFieldType(), stringFieldType());
            CodeBlock nullCheck = CodeBlock.builder()
                    .addStatement("$T.requireNonNull(obj.myPair)", Objects.class).build();
            String code = gen.generateTopLevelSerialization(TupleInfo.PAIR, pairType,
                    "myPair", "myPair", nullCheck, simpleSerGen).toString();
            assertThat(code).contains("//Field myPair");
            assertThat(code).contains("requireNonNull");
            assertThat(code).contains("getFirst()");
            assertThat(code).contains("getSecond()");
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("constr.getData().add(");
        }
    }

    @Nested
    class NestedSerialization {
        @Test
        void nestedPairProducesListPlutusData() {
            FieldType pairType = pairFieldType(intFieldType(), bytesFieldType());
            String code = gen.generateNestedSerialization(TupleInfo.PAIR, pairType,
                    "myVar", "outputPair", simpleSerGen).toString();
            assertThat(code).contains("requireNonNull");
            assertThat(code).contains("getFirst()");
            assertThat(code).contains("getSecond()");
            assertThat(code).contains("ListPlutusData");
        }
    }

    @Nested
    class SerializationFromExpression {
        @Test
        void assignsExpressionToVariable() {
            FieldType pairType = pairFieldType(intFieldType(), stringFieldType());
            String code = gen.generateSerializationFromExpression(TupleInfo.PAIR, pairType,
                    "pairVar", "outputPair", "myList.get(0)", simpleSerGen).toString();
            assertThat(code).contains("var pairVar = myList.get(0)");
            assertThat(code).contains("ListPlutusData");
        }
    }

    @Nested
    class DeserializationFromExpression {
        @Test
        void pairExtractsAndConstructs() {
            FieldType pairType = pairFieldType(intFieldType(), stringFieldType());
            String code = gen.generateDeserializationFromExpression(TupleInfo.PAIR, pairType,
                    "myBase", "outputPair", "pdItem", simpleDeserGen).toString();
            assertThat(code).contains("(ListPlutusData)pdItem");
            assertThat(code).contains("getPlutusDataList().get(0)");
            assertThat(code).contains("getPlutusDataList().get(1)");
            assertThat(code).contains("Pair(");
        }

        @Test
        void tripleExtractsAndConstructs() {
            FieldType tripleType = tripleFieldType(intFieldType(), stringFieldType(), bytesFieldType());
            String code = gen.generateDeserializationFromExpression(TupleInfo.TRIPLE, tripleType,
                    "myBase", "outputTriple", "pdItem", simpleDeserGen).toString();
            assertThat(code).contains("getPlutusDataList().get(0)");
            assertThat(code).contains("getPlutusDataList().get(1)");
            assertThat(code).contains("getPlutusDataList().get(2)");
            assertThat(code).contains("Triple(");
        }
    }
}
