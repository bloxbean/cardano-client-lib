package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class BoolFieldCodeGenTest {

    private final BoolFieldCodeGen gen = new BoolFieldCodeGen();
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void generatesToPlutusData() {
            Field field = field("active", 0, boolFieldType());
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("toPlutusData(obj.active)");
            assertThat(code).contains("requireNonNull");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void generatesAlternativeCheck() {
            Field field = field("active", 2, boolFieldType());
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("ConstrPlutusData");
            assertThat(code).contains("getAlternative() == 0");
            assertThat(code).contains("active = false");
            assertThat(code).contains("active = true");
            assertThat(code).contains(".get(2)");
        }
    }

    @Nested
    class Expressions {
        @Test
        void toPlutusData() {
            assertThat(gen.toPlutusDataExpression(boolFieldType(), "flag")).isEqualTo("toPlutusData(flag)");
        }

        @Test
        void fromPlutusData() {
            assertThat(gen.fromPlutusDataExpression(boolFieldType(), "pd")).isEqualTo("plutusDataToBoolean(pd)");
        }
    }
}
