package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlutusDataFieldCodeGenTest {

    private final PlutusDataFieldCodeGen gen = new PlutusDataFieldCodeGen();
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void addsDirectly() {
            Field field = field("pd", 0, plutusDataFieldType());
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("constr.getData().add(obj.pd)");
            assertThat(code).doesNotContain("toPlutusData");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void directReference() {
            Field field = field("pd", 1, plutusDataFieldType());
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("var pd = constrData.getPlutusDataList().get(1)");
            assertThat(code).doesNotContain("cast");
        }
    }

    @Nested
    class Expressions {
        @Test
        void toPlutusDataReturnsNull() {
            assertThat(gen.toPlutusDataExpression(plutusDataFieldType(), "x")).isNull();
        }

        @Test
        void fromPlutusDataPassthrough() {
            assertThat(gen.fromPlutusDataExpression(plutusDataFieldType(), "item")).isEqualTo("item");
        }
    }
}
