package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.annotation.processing.ProcessingEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FieldSpecProcessor}.
 */
class FieldSpecProcessorTest {

    private FieldSpecProcessor fieldSpecProcessor;

    @BeforeEach
    void setUp() {
        // Create a mock Blueprint annotation
        Blueprint mockAnnotation = Mockito.mock(Blueprint.class);
        Mockito.when(mockAnnotation.packageName()).thenReturn("com.test");

        // Create minimal mocks for dependencies
        ProcessingEnvironment mockProcessingEnv = Mockito.mock(ProcessingEnvironment.class);
        GeneratedTypesRegistry mockRegistry = Mockito.mock(GeneratedTypesRegistry.class);
        SharedTypeLookup mockSharedTypeLookup = Mockito.mock(SharedTypeLookup.class);

        fieldSpecProcessor = new FieldSpecProcessor(
                mockAnnotation,
                mockProcessingEnv,
                mockRegistry,
                mockSharedTypeLookup
        );
    }

    /**
     * Tests for resolveTitleFromDefinitionKey() method.
     *
     * <p>This method is critical for CIP-57 compliance - it handles optional "title" fields
     * by falling back to extracting class names from definition keys.</p>
     */
    @Nested
    class ResolveTitleFromDefinitionKeyTests {

        @Test
        void shouldReturnNull_whenSchemaIsNull() {
            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", null);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_whenDefinitionKeyIsNull_andSchemaHasNoTitle() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey(null, schema);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_whenDefinitionKeyIsEmpty_andSchemaHasNoTitle() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("", schema);

            assertThat(result).isNull();
        }

        @Test
        void shouldPreferDefinitionKey_evenWhenTitleIsPresent() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("Action");

            // NEW BEHAVIOR: Definition key is preferred over title
            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/SomethingElse", schema);

            assertThat(result).isEqualTo("SomethingElse");  // From key, not title
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_simpleKey() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("Int", schema);

            assertThat(result).isEqualTo("Int");
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_pathWithSlashes() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", schema);

            assertThat(result).isEqualTo("Data");
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_multiLevelPath() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("cardano/transaction/OutputReference", schema);

            assertThat(result).isEqualTo("OutputReference");
        }

        @Test
        void shouldExtractClassName_whenSchemaHasNoTitle_withJsonPointerEscaping() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types~1custom~1Data", schema);

            assertThat(result).isEqualTo("Data");
        }

        @Test
        void shouldReturnEmptyString_whenSchemaHasEmptyTitle_andDefinitionKeyIsNull() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("");  // Empty title

            // NEW BEHAVIOR: Definition key is preferred but null/empty, so falls back to title ("")
            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey(null, schema);

            assertThat(result).isEmpty();  // Returns empty string, not null
        }

        @Test
        void shouldExtractClassName_whenSchemaHasEmptyTitle_andDefinitionKeyIsValid() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("");  // Empty title

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/order/Action", schema);

            assertThat(result).isEqualTo("Action");
        }

        @Test
        void shouldPreferDefinitionKey_overSchemaTitle() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("CustomName");

            // NEW BEHAVIOR: Definition key takes precedence over schema title
            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", schema);

            assertThat(result).isEqualTo("Data");  // From key, not title
        }

        @Test
        void shouldPreferDefinitionKey_evenWithWhitespaceTitle() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("   ");  // Whitespace title

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("types/custom/Data", schema);

            // NEW BEHAVIOR: Definition key is preferred over whitespace title
            assertThat(result).isEqualTo("Data");  // From key, not whitespace title
        }

        @Test
        void shouldExtractClassName_forTwoSegmentPath() {
            BlueprintSchema schema = new BlueprintSchema();
            // No title set

            String result = fieldSpecProcessor.resolveTitleFromDefinitionKey("cardano/Credential", schema);

            assertThat(result).isEqualTo("Credential");
        }
    }

    /**
     * Tests for resolveClassNameFromRef() method.
     *
     * <p><b>What this method does:</b> Determines what Java class name to use when generating
     * a class for a blueprint definition. Returns null if no class should be generated
     * (e.g., for built-in containers).</p>
     *
     * <p><b>IMPORTANT:</b> This is about <b>class generation from definitions</b>, NOT field typing!
     * Returning null means "don't generate a class", it does NOT mean "use PlutusData for fields".</p>
     *
     * <p><b>Why this changed:</b> The previous implementation incorrectly used variant titles
     * instead of definition keys, violating CIP-57 specification. This caused compilation failures
     * when the definition key (e.g., "ValidityRange") differed from the variant title (e.g., "Interval").</p>
     *
     * <p><b>What changed:</b> Now extracts base types from generic instantiations and distinguishes
     * between built-in containers (→ don't generate class) and domain-specific types (→ generate typed class).</p>
     *
     * <p><b>Real-world impact:</b></p>
     * <ul>
     *   <li>Aiken v1.0.26: "Interval$Int" generates typed Interval class (was skipped)</li>
     *   <li>Aiken v1.1.21+: "ValidityRange" with anyOf generates ValidityRange class (was failing with "cannot find symbol: Interval")</li>
     *   <li>Built-in containers like "Option&lt;T&gt;" return null (no class generated), but fields use Optional&lt;T&gt; via OptionDataTypeProcessor</li>
     * </ul>
     */
    @Nested
    class ResolveClassNameFromRefTests {

        // ========================================
        // NON-GENERIC TYPES (existing behavior)
        // ========================================

        @Test
        void shouldReturnTitle_whenRefIsNull_andSchemaHasTitle() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("Action");

            String result = fieldSpecProcessor.resolveClassNameFromRef(null, schema);

            assertThat(result).isEqualTo("Action");
        }

        @Test
        void shouldReturnNull_whenRefIsNull_andSchemaIsNull() {
            String result = fieldSpecProcessor.resolveClassNameFromRef(null, null);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_whenRefIsEmpty_andSchemaHasNoTitle() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef("", schema);

            assertThat(result).isNull();
        }

        @Test
        void shouldExtractClassNameFromRef_simpleType() {
            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("SomeOtherTitle");

            // CIP-57: Definition key is source of truth, not title
            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/types~1order~1Action",
                    schema
            );

            assertThat(result).isEqualTo("Action");  // From ref, not title
        }

        @Test
        void shouldExtractClassNameFromRef_multiLevelPath() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/cardano~1transaction~1OutputReference",
                    schema
            );

            assertThat(result).isEqualTo("OutputReference");
        }

        @Test
        void shouldExtractClassNameFromRef_twoSegmentPath() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/cardano~1Credential",
                    schema
            );

            assertThat(result).isEqualTo("Credential");
        }

        // ========================================
        // DOMAIN-SPECIFIC GENERIC TYPES (NEW: type-safe classes)
        // ========================================
        // These are the key changes - domain-specific types now extract base type
        // and generate typed classes instead of using PlutusData

        @Test
        void shouldExtractBaseType_aikenV1_0_26_intervalWithDollarSyntax() {
            // WHY: Aiken v1.0.26 uses "Interval$Int" for generic instantiations
            // BEFORE: Would return null → PlutusData (type-unsafe)
            // AFTER: Extracts "Interval" → typed Interval class (type-safe)

            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/aiken~1interval~1Interval$Int",
                    schema
            );

            assertThat(result).isEqualTo("Interval");  // Base type extracted for type safety
        }

        @Test
        void shouldExtractBaseType_aikenV1_0_26_intervalBoundWithDollarSyntax() {
            // WHY: IntervalBound is a domain-specific type, not a built-in container
            // RESULT: Generates typed IntervalBound class for type-safe field access

            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/aiken~1interval~1IntervalBound$Int",
                    schema
            );

            assertThat(result).isEqualTo("IntervalBound");  // Base type extracted
        }

        @Test
        void shouldExtractBaseType_aikenV1_0_26_intervalBoundTypeWithDollarSyntax() {
            // EXAMPLE: "IntervalBoundType$Int" from SundaeSwap V2 blueprint
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/aiken~1interval~1IntervalBoundType$Int",
                    schema
            );

            assertThat(result).isEqualTo("IntervalBoundType");
        }

        @Test
        void shouldExtractBaseType_aikenV1_1_21_intervalWithAngleBrackets() {
            // WHY: Newer Aiken versions can use <> syntax for generics
            // RESULT: Same behavior - extract base type for type safety

            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/aiken~1interval~1Interval<Int>",
                    schema
            );

            assertThat(result).isEqualTo("Interval");
        }

        @Test
        void shouldExtractBaseType_nestedGenerics() {
            // EXAMPLE: "Interval<Option<Int>>" → base type "Interval"
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/aiken~1interval~1Interval<Option<Int>>",
                    schema
            );

            assertThat(result).isEqualTo("Interval");
        }

        @Test
        void shouldExtractBaseType_withNamespacePath() {
            // EXAMPLE: Domain-specific type with full namespace path
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/custom~1types~1MyContainer$String",
                    schema
            );

            assertThat(result).isEqualTo("MyContainer");
        }

        // ========================================
        // BUILT-IN CONTAINERS (NEW: return null - no class generated)
        // ========================================
        // These tests demonstrate why we need to distinguish built-in containers
        // from domain-specific types.
        //
        // CRITICAL: Returning null means "don't generate a class", NOT "use PlutusData for fields"!
        // After our PlutusBlueprintLoader fix, Option<T> fields use Optional<T> via OptionDataTypeProcessor.

        @Test
        void shouldReturnNull_forBuiltInContainer_option_dollarSyntax() {
            // WHY: Option is a built-in container - we don't generate an "Option.java" class
            // BEFORE: Would try to generate "Option" class → compilation error (conflicts with built-in)
            // AFTER: Returns null → signals "don't generate class"
            //
            // NOTE: This does NOT mean Option<T> fields use PlutusData!
            // Fields use Optional<T> via OptionDataTypeProcessor (after PlutusBlueprintLoader sets dataType=option)

            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Option$ByteArray",
                    schema
            );

            assertThat(result).isNull();  // Signals "skip class generation", NOT "use PlutusData"
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_option_angleBrackets() {
            // EXAMPLE: "Option<types/order/Action>" from modern Aiken
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Option<types~1order~1Action>",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_list_dollarSyntax() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/List$Int",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_list_angleBrackets() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/List<Int>",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_tuple() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Tuple$Int_String",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_pair() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Pair<Int_String>",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_map() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Map$String_Int",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_dict() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Dict<String_Int>",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_data() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Data",
                    schema
            );

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNull_forBuiltInContainer_redeemer() {
            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Redeemer",
                    schema
            );

            assertThat(result).isNull();
        }

        // ========================================
        // REAL-WORLD EXAMPLES from SundaeSwap blueprints
        // ========================================
        // These demonstrate the actual failures and fixes

        @Test
        void realWorld_sundaeSwapV2_intervalInt_shouldGenerateTypedClass() {
            // REAL EXAMPLE from sundaeswap_aiken_v1_0_26_alpha_075668b.json
            // Definition: "aiken/interval/Interval$Int"
            //
            // BEFORE: Skipped entirely → fields use PlutusData (type-unsafe casting)
            // AFTER: Generates typed Interval class → fields use Interval type (type-safe)

            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/aiken~1interval~1Interval$Int",
                    schema
            );

            assertThat(result).isEqualTo("Interval");
        }

        @Test
        void realWorld_sundaeSwapV3_validityRange_shouldUseDefinitionKey() {
            // REAL EXAMPLE from sundaeswap_aiken_v1_1_21_42babe5.json
            // Definition key: "cardano/transaction/ValidityRange"
            // Single anyOf variant with title: "Interval"
            //
            // BEFORE: Used variant title "Interval" → generated aiken.interval.model.Interval
            //         Then field tried to use cardano.transaction.model.ValidityRange
            //         RESULT: "cannot find symbol: class ValidityRange" compilation error
            //
            // AFTER: Uses definition key "ValidityRange" → generates cardano.transaction.model.ValidityRange
            //        Fields correctly reference ValidityRange
            //        RESULT: Compiles successfully

            BlueprintSchema schema = new BlueprintSchema();
            schema.setTitle("Interval");  // Variant title (wrong!)

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/cardano~1transaction~1ValidityRange",
                    schema
            );

            // Returns "ValidityRange" from definition key, NOT "Interval" from title
            assertThat(result).isEqualTo("ValidityRange");
        }

        @Test
        void realWorld_sundaeSwapV3_optionCredential_shouldSkipClassGeneration() {
            // REAL EXAMPLE: SundaeSwap V3 has "Option<cardano/address/Credential>" definition
            //
            // WHY: Option is a built-in container, not a domain-specific type
            // RESULT: Returns null → no "Option" class generated (avoids conflicts with built-in Optional)
            //
            // IMPORTANT: Fields referencing this definition do NOT use PlutusData!
            // After PlutusBlueprintLoader fix, they use Optional<Credential> via OptionDataTypeProcessor.
            // See PlutusBlueprintLoaderTest.OptionTypeResolution for the full flow.

            BlueprintSchema schema = new BlueprintSchema();

            String result = fieldSpecProcessor.resolveClassNameFromRef(
                    "#/definitions/Option<cardano~1address~1Credential>",
                    schema
            );

            assertThat(result).isNull();  // Skip class generation, fields handled by OptionDataTypeProcessor
        }
    }
}
