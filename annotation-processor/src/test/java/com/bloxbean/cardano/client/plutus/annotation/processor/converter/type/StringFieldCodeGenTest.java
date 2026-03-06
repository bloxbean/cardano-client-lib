package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class StringFieldCodeGenTest {

    private final StringFieldCodeGen gen = new StringFieldCodeGen();
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void generatesToPlutusData() {
            Field field = field("name", 0, stringFieldType());
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("toPlutusData(obj.name)");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void deserializesWithNullEncoding() {
            Field field = field("name", 0, stringFieldType());
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("deserializeBytesToString");
            assertThat(code).contains("BytesPlutusData");
        }

        @Test
        void deserializesWithEncoding() {
            Field field = field("name", 0, stringFieldType("UTF-8"));
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("deserializeBytesToString");
            assertThat(code).contains("\"UTF-8\"");
        }
    }

    @Nested
    class Expressions {
        @Test
        void fromPlutusDataWithNullEncoding() {
            assertThat(gen.fromPlutusDataExpression(stringFieldType(), "pd"))
                    .isEqualTo("plutusDataToString(pd, null)");
        }

        @Test
        void fromPlutusDataWithEncoding() {
            assertThat(gen.fromPlutusDataExpression(stringFieldType("UTF-8"), "pd"))
                    .isEqualTo("plutusDataToString(pd, \"UTF-8\")");
        }
    }
}
