package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class TupleFieldCodeGenTest {

    private final FieldCodeGeneratorRegistry registry = new FieldCodeGeneratorRegistry();
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class PairSerialization {
        @Test
        void generates2ElementListPlutusData() {
            FieldType pairType = pairFieldType(intFieldType(), stringFieldType());
            Field field = field("myPair", 0, pairType);
            TupleFieldCodeGen gen = (TupleFieldCodeGen) registry.get(Type.PAIR);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("//Field myPair");
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("getFirst()");
            assertThat(code).contains("getSecond()");
            assertThat(code).contains("constr.getData().add(");
        }
    }

    @Nested
    class PairDeserialization {
        @Test
        void generatesNewPair() {
            FieldType pairType = pairFieldType(intFieldType(), stringFieldType());
            Field field = field("myPair", 0, pairType);
            TupleFieldCodeGen gen = (TupleFieldCodeGen) registry.get(Type.PAIR);
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("//Field myPair");
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("Pair(");
        }
    }

    @Nested
    class TripleSerialization {
        @Test
        void generates3ElementListPlutusData() {
            FieldType tripleType = tripleFieldType(intFieldType(), stringFieldType(), bytesFieldType());
            Field field = field("myTriple", 0, tripleType);
            TupleFieldCodeGen gen = (TupleFieldCodeGen) registry.get(Type.TRIPLE);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("getFirst()");
            assertThat(code).contains("getSecond()");
            assertThat(code).contains("getThird()");
        }
    }

    @Nested
    class NestedTuple {
        @Test
        void pairNestedSerialization() {
            FieldType pairType = pairFieldType(intFieldType(), bytesFieldType());
            TupleFieldCodeGen gen = (TupleFieldCodeGen) registry.get(Type.PAIR);
            String code = gen.generateNestedSerialization(pairType, "item", "itemPair", "myPair").toString();
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("getFirst()");
            assertThat(code).contains("getSecond()");
        }

        @Test
        void pairNestedDeserialization() {
            FieldType pairType = pairFieldType(intFieldType(), bytesFieldType());
            TupleFieldCodeGen gen = (TupleFieldCodeGen) registry.get(Type.PAIR);
            String code = gen.generateNestedDeserialization(pairType, "item", "itemPair", "pdItem").toString();
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("Pair(");
        }
    }
}
