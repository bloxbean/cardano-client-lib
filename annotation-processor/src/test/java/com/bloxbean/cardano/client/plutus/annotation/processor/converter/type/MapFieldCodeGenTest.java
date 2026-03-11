package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class MapFieldCodeGenTest {

    private final FieldCodeGeneratorRegistry registry = new FieldCodeGeneratorRegistry();
    private final MapFieldCodeGen gen = (MapFieldCodeGen) registry.get(com.bloxbean.cardano.client.plutus.annotation.processor.model.Type.MAP);
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void generatesMapLoop() {
            FieldType mapType = mapFieldType(stringFieldType(), intFieldType());
            Field field = field("scores", 0, mapType);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("//Field scores");
            assertThat(code).contains("MapPlutusData");
            assertThat(code).contains("entrySet()");
            assertThat(code).contains(".put(");
            assertThat(code).contains("constr.getData().add(");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void generatesMapDeser() {
            FieldType mapType = mapFieldType(stringFieldType(), intFieldType());
            Field field = field("scores", 0, mapType);
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("//Field scores");
            assertThat(code).contains("MapPlutusData");
            assertThat(code).contains("LinkedHashMap");
            assertThat(code).contains("entrySet()");
        }
    }

    @Nested
    class NestedSerDe {
        @Test
        void nestedSerializationProducesMapPlutusData() {
            FieldType mapType = mapFieldType(bytesFieldType(), stringFieldType());
            String code = gen.generateNestedSerialization(mapType, "entry", "entryMap", "myMap").toString();
            assertThat(code).contains("MapPlutusData");
            assertThat(code).contains("entrySet()");
        }

        @Test
        void nestedDeserializationProducesLinkedHashMap() {
            FieldType mapType = mapFieldType(bytesFieldType(), stringFieldType());
            String code = gen.generateNestedDeserialization(mapType, "entry", "entryMap", "pdEntry").toString();
            assertThat(code).contains("MapPlutusData");
            assertThat(code).contains("LinkedHashMap");
        }
    }
}
