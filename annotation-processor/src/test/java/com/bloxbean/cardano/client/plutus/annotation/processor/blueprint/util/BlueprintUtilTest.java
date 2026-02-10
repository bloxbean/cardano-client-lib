package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BlueprintUtil}
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Compliance with CIP-57 specification for opaque PlutusData detection</li>
 *   <li>Correct namespace extraction from reference keys including generic types</li>
 * </ul>
 */
public class BlueprintUtilTest {

    // ========== Null and Edge Cases ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenSchemaIsNull() {
        assertThat(BlueprintUtil.isAbstractPlutusDataType(null)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenEmptySchema() {
        // An empty schema has no dataType and no structure = opaque PlutusData per CIP-57
        BlueprintSchema schema = new BlueprintSchema();

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    // ========== Opaque PlutusData Cases (should return true) ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenNoDataTypeAndNoStructure() {
        // Per CIP-57: no dataType = opaque PlutusData
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Data");
        schema.setDescription("Any Plutus data.");
        // No dataType, no structure

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenOnlyTitleSet() {
        // Title doesn't matter - only dataType and structure matter
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("SomeArbitraryTitle");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_whenOnlyDescriptionSet() {
        // Description doesn't matter - only dataType and structure matter
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDescription("Some arbitrary description");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_forRealWorldDataType() {
        // Simulate the actual "Data" type from cip113Token.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Data");
        schema.setDescription("Any Plutus data.");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnTrue_forRealWorldRedeemerType() {
        // Simulate the actual "Redeemer" type from cip113Token.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Redeemer");
        schema.setDescription("Any Plutus data.");

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isTrue();
    }

    // ========== Concrete Types (should return false) ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataType() {
        // Any dataType makes it a concrete type, not opaque
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.bytes);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeInteger() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.integer);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeList() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.list);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeConstructor() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.constructor);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasAnyOf() {
        // anyOf indicates a sum type with defined alternatives
        BlueprintSchema schema = new BlueprintSchema();
        schema.setAnyOf(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasFields() {
        // fields indicates a constructor with defined structure
        BlueprintSchema schema = new BlueprintSchema();
        schema.setFields(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasItems() {
        // items indicates a list with defined element type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setItems(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasKeys() {
        // keys indicates a map type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setKeys(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasValues() {
        // values indicates a map type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setValues(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasLeft() {
        // left indicates a pair type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setLeft(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasRight() {
        // right indicates a pair type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setRight(new BlueprintSchema());

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    // ========== Real-World Concrete Type Examples ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forByteArrayType() {
        // ByteArray from aftermarket.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.bytes);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forConstructorType() {
        // GlobalStateDatum from cip113Token.json
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("GlobalStateDatum");
        schema.setDataType(BlueprintDatatype.constructor);
        schema.setIndex(0);

        BlueprintSchema field = new BlueprintSchema();
        field.setTitle("transfers_paused");
        schema.setFields(Collections.singletonList(field));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forSumType() {
        // Optional type with anyOf
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Optional");

        BlueprintSchema someVariant = new BlueprintSchema();
        someVariant.setTitle("Some");
        someVariant.setDataType(BlueprintDatatype.constructor);

        BlueprintSchema noneVariant = new BlueprintSchema();
        noneVariant.setTitle("None");
        noneVariant.setDataType(BlueprintDatatype.constructor);

        schema.setAnyOf(java.util.Arrays.asList(someVariant, noneVariant));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forListType() {
        // List$ByteArray
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.list);

        BlueprintSchema itemSchema = new BlueprintSchema();
        itemSchema.setDataType(BlueprintDatatype.bytes);
        schema.setItems(Collections.singletonList(itemSchema));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_forMapType() {
        // Map type with keys and values
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.map);

        BlueprintSchema keySchema = new BlueprintSchema();
        keySchema.setDataType(BlueprintDatatype.bytes);
        schema.setKeys(keySchema);

        BlueprintSchema valueSchema = new BlueprintSchema();
        valueSchema.setDataType(BlueprintDatatype.integer);
        schema.setValues(valueSchema);

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    // ========== Mixed Cases ==========

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenHasDataTypeButNoStructure() {
        // Even with no structure, if dataType is set, it's a concrete type
        BlueprintSchema schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.bytes);
        schema.setTitle("Data"); // Title doesn't matter

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    @Test
    void isAbstractPlutusDataType_shouldReturnFalse_whenNoDataTypeButHasStructure() {
        // Even without dataType, if there's structure, it's not opaque
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Data"); // Title doesn't matter
        schema.setAnyOf(Collections.singletonList(new BlueprintSchema()));

        assertThat(BlueprintUtil.isAbstractPlutusDataType(schema)).isFalse();
    }

    // ========== Namespace Extraction Tests ==========

    /**
     * Tests for getNamespaceFromReferenceKey() with non-generic types (regression tests)
     */
    @Nested
    class GetNamespaceFromReferenceKeyNonGenericTests {

        @Test
        void shouldReturnEmptyString_whenKeyIsNull() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey(null)).isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenKeyIsEmpty() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("")).isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenSingleLevelType() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Int")).isEmpty();
        }

        @Test
        void shouldExtractNamespace_whenStandardPath() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("types/order/Action"))
                    .isEqualTo("types.order");
        }

        @Test
        void shouldExtractNamespace_whenTwoLevelPath() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("cardano/transaction"))
                    .isEqualTo("cardano");
        }

        @Test
        void shouldExtractNamespace_whenThreeLevelPath() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("cardano/address/Credential"))
                    .isEqualTo("cardano.address");
        }

        @Test
        void shouldReturnLowercaseNamespace() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Types/Order/Action"))
                    .isEqualTo("types.order");
        }

        @Test
        void shouldHandleJsonPointerEscapes() {
            // JSON Pointer escapes: ~1 = /
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("types~1order~1Action"))
                    .isEqualTo("types.order");
        }
    }

    /**
     * Tests for getNamespaceFromReferenceKey() with generic types (angle bracket syntax)
     *
     * <p><b>IMPORTANT:</b> Per CIP-57, definition keys are the technical identifiers.
     * Namespace extraction uses the BASE TYPE, not the type parameter:</p>
     * <ul>
     *   <li>"Option&lt;cardano/address/StakeCredential&gt;" → base "Option" has NO module path → empty namespace</li>
     *   <li>"aiken/interval/IntervalBound&lt;Int&gt;" → base "aiken/interval/IntervalBound" HAS module path → "aiken.interval"</li>
     * </ul>
     */
    @Nested
    class GetNamespaceFromReferenceKeyGenericAngleBracketTests {

        // Base types WITHOUT module paths (generic instantiations like Option, List, etc.)
        // These should return empty namespace regardless of type parameter

        @Test
        void shouldReturnEmptyString_whenBaseTypeIsOption() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option<types/order/Action>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenGenericPrimitive() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option<Int>")).isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenListOfPrimitives() {
            // Base type "List" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("List<Int>")).isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenNestedGenericWithoutBasePath() {
            // Base type "List" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("List<Option<types/order/Action>>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenTripleNestedGeneric() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option<List<Option<types/order/Action>>>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenBaseOptionWithEscapedTypeParam() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option<types~1order~1Action>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenBaseListWithEscapedTypeParam() {
            // Base type "List" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("List<Option<types~1order~1Action>>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenBaseOptionWithCardanoTypeParam() {
            // Base type "Option" has no module path → empty namespace (type param doesn't matter)
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option<cardano/address/Credential>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenBaseListWithCardanoTypeParam() {
            // Base type "List" has no module path → empty namespace (type param doesn't matter)
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("List<cardano/transaction/OutputReference>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenBaseTupleWithTypeParam() {
            // Base type "Tuple" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Tuple<<types/order/Action,Int>>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenBasePairWithTypeParam() {
            // Base type "Pair" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Pair<types/order/Action,types/order/Status>"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenTupleOfPrimitives() {
            // Base type "Tuple" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Tuple<Int,ByteArray>")).isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenComplexNestedStructure() {
            // Base type "List" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("List<Option<Pair<types/order/Action,Int>>>"))
                    .isEmpty();
        }

        // Base types WITH module paths (e.g., aiken/interval/IntervalBound)
        // These should extract namespace from the base type

        @Test
        void shouldExtractNamespace_whenBaseTypeHasModulePath() {
            // Base type "aiken/interval/IntervalBound" HAS module path → "aiken.interval"
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("aiken/interval/IntervalBound<Int>"))
                    .isEqualTo("aiken.interval");
        }

        @Test
        void shouldExtractNamespace_whenBaseTypeHasModulePathWithCardanoTypeParam() {
            // Base type "cardano/wrapper/Container" HAS module path → "cardano.wrapper"
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("cardano/wrapper/Container<cardano/address/Credential>"))
                    .isEqualTo("cardano.wrapper");
        }

        @Test
        void shouldExtractNamespace_whenBaseTypeHasThreeLevelPath() {
            // Base type "types/order/Container" HAS module path → "types.order"
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("types/order/Container<types/order/Action>"))
                    .isEqualTo("types.order");
        }

        @Test
        void shouldExtractNamespace_whenBaseTypeWithEscapes() {
            // Base type "aiken~1interval~1IntervalBound" unescapes to "aiken/interval/IntervalBound" → "aiken.interval"
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("aiken~1interval~1IntervalBound<Int>"))
                    .isEqualTo("aiken.interval");
        }
    }

    /**
     * Tests for getNamespaceFromReferenceKey() with generic types (dollar sign syntax)
     *
     * <p>Dollar sign syntax used by older Aiken compiler versions (v1.0.26).</p>
     * <p>Same rules as angle bracket syntax: namespace extracted from BASE TYPE, not type parameter.</p>
     */
    @Nested
    class GetNamespaceFromReferenceKeyGenericDollarSignTests {

        // Base types WITHOUT module paths (generic instantiations)
        // These should return empty namespace regardless of type parameter

        @Test
        void shouldReturnEmptyString_whenDollarSignPrimitive() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option$Int")).isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenDollarSignWithPath() {
            // Base type "Option" has no module path → empty namespace (type param doesn't matter)
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option$types/order/Action"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenDollarSignWithEscapes() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option$types~1order~1Action"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenDollarSignCardanoType() {
            // Base type "Option" has no module path → empty namespace (type param doesn't matter)
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option$cardano/address/Credential"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenNestedDollarSign() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option$List$types/order/Action"))
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyString_whenListDollarPrimitive() {
            // Base type "List" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("List$ByteArray")).isEmpty();
        }

        // Base types WITH module paths
        // These should extract namespace from the base type

        @Test
        void shouldExtractNamespace_whenBaseTypeHasModulePathWithDollarSign() {
            // Base type "aiken/interval/IntervalBound" HAS module path → "aiken.interval"
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("aiken/interval/IntervalBound$Int"))
                    .isEqualTo("aiken.interval");
        }

        @Test
        void shouldExtractNamespace_whenBaseTypeWithDollarAndEscapes() {
            // Base type "aiken~1interval~1IntervalBound" unescapes to "aiken/interval/IntervalBound" → "aiken.interval"
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("aiken~1interval~1IntervalBound$Int"))
                    .isEqualTo("aiken.interval");
        }
    }

    /**
     * Tests for edge cases in generic type handling
     */
    @Nested
    class GetNamespaceFromReferenceKeyEdgeCasesTests {

        @Test
        void shouldHandleMalformedGeneric_missingClosingBracket() {
            // Malformed input - should not crash
            String result = BlueprintUtil.getNamespaceFromReferenceKey("Option<types/order/Action");
            // Best effort: should not throw exception
            assertThat(result).isNotNull();
        }

        @Test
        void shouldHandleMalformedGeneric_missingOpeningBracket() {
            String result = BlueprintUtil.getNamespaceFromReferenceKey("Option types/order/Action>");
            assertThat(result).isNotNull();
        }

        @Test
        void shouldHandleEmptyGeneric() {
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option<>")).isEmpty();
        }

        @Test
        void shouldHandleWhitespaceInGeneric() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option< types/order/Action >"))
                    .isEmpty();
        }

        @Test
        void shouldHandleMixedDollarAndBrackets() {
            // Base type "Option" has no module path → empty namespace
            assertThat(BlueprintUtil.getNamespaceFromReferenceKey("Option$List<types/order/Action>"))
                    .isEmpty();
        }
    }

    /**
     * Tests for getClassNameFromReferenceKey() - extracts class name (last segment) from definition keys.
     *
     * <p>This method is used as a fallback when blueprint schemas lack titles (CIP-57 compliance).
     * Real-world example: SundaeSwap V2 blueprint has definitions without titles for primitive types.</p>
     */
    @Nested
    class GetClassNameFromReferenceKeyTests {

        @Test
        void shouldReturnEmpty_whenKeyIsNull() {
            assertThat(BlueprintUtil.getClassNameFromReferenceKey(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_whenKeyIsEmpty() {
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("")).isEmpty();
        }

        @Test
        void shouldReturnKey_whenNoSlashes() {
            // Primitive type with no namespace
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("Int"))
                    .isEqualTo("Int");
        }

        @Test
        void shouldExtractLastSegment_whenSimplePath() {
            // types/custom/Data → Data
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("types/custom/Data"))
                    .isEqualTo("Data");
        }

        @Test
        void shouldExtractLastSegment_whenMultiLevelPath() {
            // cardano/transaction/OutputReference → OutputReference
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("cardano/transaction/OutputReference"))
                    .isEqualTo("OutputReference");
        }

        @Test
        void shouldExtractLastSegment_whenTwoSegments() {
            // types/order/Action → Action
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("types/order/Action"))
                    .isEqualTo("Action");
        }

        @Test
        void shouldUnescapeJsonPointer_beforeExtraction() {
            // types~1custom~1Data → types/custom/Data → Data
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("types~1custom~1Data"))
                    .isEqualTo("Data");
        }

        @Test
        void shouldUnescapeJsonPointer_twoSegments() {
            // types~1order~1Action → types/order/Action → Action
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("types~1order~1Action"))
                    .isEqualTo("Action");
        }

        @Test
        void shouldHandleSingleSlash() {
            // cardano/transaction → transaction
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("cardano/transaction"))
                    .isEqualTo("transaction");
        }

        @Test
        void shouldHandleTrailingSlash() {
            // Technically invalid, but should handle gracefully
            // Java's split() excludes trailing empty strings, so "types/custom/Data/" → ["types", "custom", "Data"]
            assertThat(BlueprintUtil.getClassNameFromReferenceKey("types/custom/Data/"))
                    .isEqualTo("Data");
        }
    }
}
