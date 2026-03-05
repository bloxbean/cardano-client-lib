package com.bloxbean.cardano.client.plutus.annotation.processor.converter.type;

import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldAccessor;
import com.bloxbean.cardano.client.plutus.annotation.processor.converter.FieldCodeGeneratorRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.FieldType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.bloxbean.cardano.client.plutus.annotation.processor.converter.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class OptionalFieldCodeGenTest {

    private final FieldCodeGeneratorRegistry registry = new FieldCodeGeneratorRegistry();
    private final OptionalFieldCodeGen gen = (OptionalFieldCodeGen) registry.get(
            com.bloxbean.cardano.client.plutus.annotation.processor.model.Type.OPTIONAL);
    private final FieldAccessor accessor = new FieldAccessor();

    @Nested
    class TopLevelSerialization {
        @Test
        void generatesEmptyAndPresentBranches() {
            FieldType optType = optionalFieldType(intFieldType());
            Field field = field("maybe", 0, optType);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("//Field maybe");
            assertThat(code).contains("isEmpty()");
            assertThat(code).contains("alternative(1)");
            assertThat(code).contains("alternative(0)");
            assertThat(code).contains("constr.getData().add(");
        }
    }

    @Nested
    class TopLevelDeserialization {
        @Test
        void generatesAlternativeCheck() {
            FieldType optType = optionalFieldType(intFieldType());
            Field field = field("maybe", 0, optType);
            String code = gen.generateDeserialization(field).toString();
            assertThat(code).contains("//Field maybe");
            assertThat(code).contains("ConstrPlutusData");
            assertThat(code).contains("getAlternative() == 1");
            assertThat(code).contains("Optional.empty()");
            assertThat(code).contains("Optional.ofNullable(");
        }
    }

    @Nested
    class NestedSerialization {
        @Test
        void optionalInsideCollection() {
            FieldType optType = optionalFieldType(stringFieldType());
            String code = gen.generateNestedSerialization(optType, "item", "itemOpt", "item").toString();
            assertThat(code).contains("isEmpty()");
            assertThat(code).contains("alternative(1)");
            assertThat(code).contains("alternative(0)");
        }
    }

    @Nested
    class NestedDeserialization {
        @Test
        void optionalInsideCollectionDeser() {
            FieldType optType = optionalFieldType(stringFieldType());
            String code = gen.generateNestedDeserialization(optType, "item", "itemOpt", "pdItem").toString();
            assertThat(code).contains("ConstrPlutusData");
            assertThat(code).contains("getAlternative() == 1");
            assertThat(code).contains("Optional.empty()");
            assertThat(code).contains("Optional.ofNullable(");
        }
    }

    @Nested
    class OptionalOfList {
        @Test
        void optionalContainingList() {
            FieldType innerList = listFieldType(intFieldType());
            FieldType optType = optionalFieldType(innerList);
            Field field = field("maybeList", 0, optType);
            String code = gen.generateSerialization(field, accessor).toString();
            assertThat(code).contains("isEmpty()");
            assertThat(code).contains("ListPlutusData");
        }
    }
}
