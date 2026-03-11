package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class IntegerFieldCodeGenTest {

    private final IntegerFieldCodeGen gen = new IntegerFieldCodeGen();
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class Serialization {
        @Test
        void generatesToPlutusDataWithNullCheck() {
            Field field = field("amount", 0, intFieldType());
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("//Field amount");
            assertThat(code).contains("requireNonNull");
            assertThat(code).contains("constr.getData().add(toPlutusData(obj.amount))");
        }
    }

    @Nested
    class Deserialization {
        @Test
        void intTypeUsesIntValue() {
            Field field = field("count", 0, intFieldType());
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("BigIntPlutusData");
            assertThat(code).contains("getValue().intValue()");
        }

        @Test
        void longTypeUsesLongValue() {
            Field field = field("timestamp", 1, longFieldType());
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("getValue().longValue()");
            assertThat(code).contains(".get(1)");
        }

        @Test
        void bigIntegerTypeUsesGetValue() {
            Field field = field("bigNum", 2, bigIntFieldType());
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains(".getValue()");
            assertThat(code).doesNotContain("intValue").doesNotContain("longValue");
        }
    }

    @Nested
    class Expressions {
        @Test
        void toPlutusDataExpression() {
            assertThat(gen.toPlutusDataExpression(intFieldType(), "x")).isEqualTo("toPlutusData(x)");
        }

        @Test
        void fromPlutusDataExpressionInt() {
            assertThat(gen.fromPlutusDataExpression(intFieldType(), "pd")).isEqualTo("plutusDataToInteger(pd)");
        }

        @Test
        void fromPlutusDataExpressionLong() {
            assertThat(gen.fromPlutusDataExpression(longFieldType(), "pd")).isEqualTo("plutusDataToLong(pd)");
        }

        @Test
        void fromPlutusDataExpressionBigInteger() {
            assertThat(gen.fromPlutusDataExpression(bigIntFieldType(), "pd")).isEqualTo("plutusDataToBigInteger(pd)");
        }
    }
}
