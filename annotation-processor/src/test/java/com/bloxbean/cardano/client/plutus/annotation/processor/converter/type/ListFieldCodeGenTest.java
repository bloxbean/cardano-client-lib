package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class ListFieldCodeGenTest {

    private final FieldCodeGeneratorRegistry registry = new FieldCodeGeneratorRegistry();
    private final ListFieldCodeGen gen = (ListFieldCodeGen) registry.get(com.bloxbean.cardano.client.plutus.annotation.processor.model.Type.LIST);
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void generatesListLoop() {
            FieldType listType = listFieldType(intFieldType());
            Field field = field("numbers", 0, listType);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("//Field numbers");
            assertThat(code).contains("requireNonNull");
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("for(var");
            assertThat(code).contains("constr.getData().add(");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void generatesListDeser() {
            FieldType listType = listFieldType(intFieldType());
            Field field = field("numbers", 0, listType);
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("//Field numbers");
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("ArrayList<>()");
            assertThat(code).contains("for(var");
        }
    }

    @Nested
    class NestedList {
        @Test
        void listOfLists() {
            FieldType innerList = listFieldType(stringFieldType());
            FieldType outerList = listFieldType(innerList);
            Field field = field("nested", 0, outerList);
            String code = gen.generateSerialization(field, accessor).toString();
            // Should produce nested for-loops
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("for(var");
        }
    }

    @Nested
    class NestedSerDe {
        @Test
        void nestedSerializationProducesListPlutusData() {
            FieldType listType = listFieldType(bytesFieldType());
            String code = gen.generateNestedSerialization(listType, "item", "itemList", "item").toString();
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("for(var");
        }

        @Test
        void nestedDeserializationProducesArrayList() {
            FieldType listType = listFieldType(bytesFieldType());
            String code = gen.generateNestedDeserialization(listType, "item", "itemList", "pdItem").toString();
            assertThat(code).contains("ListPlutusData");
            assertThat(code).contains("ArrayList<>()");
        }
    }
}
