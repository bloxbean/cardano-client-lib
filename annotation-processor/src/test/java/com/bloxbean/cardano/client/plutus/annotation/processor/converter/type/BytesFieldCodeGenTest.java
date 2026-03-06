package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class BytesFieldCodeGenTest {

    private final BytesFieldCodeGen gen = new BytesFieldCodeGen();
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void generatesToPlutusData() {
            Field field = field("data", 0, bytesFieldType());
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("toPlutusData(obj.data)");
            assertThat(code).contains("requireNonNull");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void castsToBytesPlutusData() {
            Field field = field("data", 3, bytesFieldType());
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("BytesPlutusData");
            assertThat(code).contains(".getValue()");
            assertThat(code).contains(".get(3)");
        }
    }

    @Nested
    class Expressions {
        @Test
        void toPlutusData() {
            assertThat(gen.toPlutusDataExpression(bytesFieldType(), "b")).isEqualTo("toPlutusData(b)");
        }

        @Test
        void fromPlutusData() {
            assertThat(gen.fromPlutusDataExpression(bytesFieldType(), "pd")).isEqualTo("plutusDataToBytes(pd)");
        }
    }
}
