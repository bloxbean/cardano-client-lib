package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class FieldAccessorTest {

    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class FieldOrGetter {
        @Test
        void directFieldAccess() {
            Field field = field("myField", 0, intFieldType());
            assertThat(accessor.fieldOrGetter(field)).isEqualTo("myField");
        }

        @Test
        void getterAccess() {
            Field field = fieldWithGetter("myField", 0, intFieldType(), "getMyField");
            assertThat(accessor.fieldOrGetter(field)).isEqualTo("getMyField()");
        }
    }

    @Nested
    class Setter {
        @Test
        void generatesSetter() {
            assertThat(accessor.setter("myField")).isEqualTo("setMyField");
        }

        @Test
        void capitalizesFirstLetter() {
            assertThat(accessor.setter("x")).isEqualTo("setX");
        }
    }

    @Nested
    class NullCheck {
        @Test
        void generatesNullCheckForField() {
            Field field = field("amount", 0, intFieldType());
            String code = accessor.nullCheck(field).toString();
            assertThat(code).contains("Objects.requireNonNull(obj.amount");
            assertThat(code).contains("amount cannot be null");
        }

        @Test
        void generatesNullCheckForGetter() {
            Field field = fieldWithGetter("amount", 0, intFieldType(), "getAmount");
            String code = accessor.nullCheck(field).toString();
            assertThat(code).contains("obj.getAmount()");
        }
    }
}
