package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldCodeGeneratorRegistryTest {

    private final FieldCodeGeneratorRegistry registry = new FieldCodeGeneratorRegistry();

    @Nested
    class Registration {
        @Test
        void allTypesHaveRegisteredGenerator() {
            for (Type type : Type.values()) {
                assertThat(registry.get(type))
                        .as("Generator for type %s", type)
                        .isNotNull();
            }
        }

        @Test
        void eachGeneratorReportsCorrectSupportedType() {
            for (Type type : Type.values()) {
                FieldCodeGenerator gen = registry.get(type);
                assertThat(gen.supportedType()).isEqualTo(type);
            }
        }
    }

    @Nested
    class ExpressionDispatch {
        @Test
        void integerToPlutusDataExpression() {
            String expr = registry.toPlutusDataExpression(TestFixtures.intFieldType(), "myValue");
            assertThat(expr).isEqualTo("toPlutusData(myValue)");
        }

        @Test
        void bytesFromPlutusDataExpression() {
            String expr = registry.fromPlutusDataExpression(TestFixtures.bytesFieldType(), "item");
            assertThat(expr).isEqualTo("plutusDataToBytes(item)");
        }

        @Test
        void stringWithEncodingFromPlutusDataExpression() {
            String expr = registry.fromPlutusDataExpression(TestFixtures.stringFieldType("UTF-8"), "item");
            assertThat(expr).isEqualTo("plutusDataToString(item, \"UTF-8\")");
        }

        @Test
        void boolToPlutusDataExpression() {
            String expr = registry.toPlutusDataExpression(TestFixtures.boolFieldType(), "flag");
            assertThat(expr).isEqualTo("toPlutusData(flag)");
        }
    }
}
