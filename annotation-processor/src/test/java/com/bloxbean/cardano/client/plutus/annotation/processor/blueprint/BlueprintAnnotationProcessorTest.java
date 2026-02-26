package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatum;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link BlueprintAnnotationProcessor}.
 *
 * <p><b>What this class tests:</b></p>
 * <ul>
 *   <li><b>Generic Type Handling:</b> Distinguishes between built-in containers (List, Option)
 *       and domain-specific types (Interval, IntervalBound)</li>
 *   <li><b>Definition Key Resolution:</b> Extracts base types from generic instantiations while
 *       preserving namespace paths</li>
 *   <li><b>Code Generation:</b> Verifies validator and datum classes are generated correctly</li>
 *   <li><b>Aiken Version Compatibility:</b> Supports both v1.0.x ($ syntax) and v1.1.x (&lt;&gt; syntax)</li>
 * </ul>
 *
 * <p><b>Why generic type handling matters:</b></p>
 * <ul>
 *   <li><b>Type Safety:</b> Domain types like Interval generate typed classes instead of PlutusData</li>
 *   <li><b>No Redundancy:</b> Built-in containers don't generate conflicting classes</li>
 *   <li><b>Real-World Impact:</b> SundaeSwap V2/V3 blueprints compile correctly with typed fields</li>
 * </ul>
 */
class BlueprintAnnotationProcessorTest {

    private BlueprintAnnotationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BlueprintAnnotationProcessor();
    }

    @Test
    @DisplayName("should generate expected validator and datum classes from blueprint")
    void blueprintProcessorShouldGenerateExpectedValidatorArtifacts() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "com.test.multiple.MultipleValidatorsBlueprint",
                "package com.test.multiple;\n" +
                        "import com.bloxbean.cardano.client.plutus.annotation.Blueprint;\n" +
                        "import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;\n" +
                        "import com.bloxbean.cardano.client.quicktx.blueprint.extender.LockUnlockValidatorExtender;\n" +
                        "@Blueprint(fileInResources = \"blueprint/multiple_validators_aiken_v1_0_29_alpha_16fb02e.json\", packageName = \"com.test.multiple\")\n" +
                        "@ExtendWith(LockUnlockValidatorExtender.class)\n" +
                        "public interface MultipleValidatorsBlueprint { }\n");

        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject validatorFile = compilation.generatedSourceFile("com.test.multiple.demo.HelloWorldValidator").orElseThrow();
        String validatorSource = validatorFile.getCharContent(true).toString();
        assertThat(validatorSource).contains("package com.test.multiple.demo;");
        assertThat(validatorSource).contains("public class HelloWorldValidator");
        assertThat(validatorSource).contains("public static final String COMPILED_CODE");
        assertThat(validatorSource).contains("private String scriptAddress;");

        JavaFileObject datumFile = compilation.generatedSourceFile("com.test.multiple.demo.model.MyDatum").orElseThrow();
        String datumSource = datumFile.getCharContent(true).toString();
        assertThat(datumSource).contains("package com.test.multiple.demo.model;");
        assertThat(datumSource).contains("public abstract class MyDatum");
        assertThat(datumSource).contains("private byte[] owner;");
        assertThat(datumSource).contains("@Constr");

        String expectedSnapshot = readResource("/snapshots/HelloWorldValidator.java");
        assertThat(normalize(validatorSource)).isEqualTo(normalize(expectedSnapshot));
    }

    /**
     * Tests for {@link BlueprintAnnotationProcessor#resolveDefinitionKeyForClassGeneration(String)}.
     *
     * <p><b>Purpose:</b> Verifies definition key resolution logic that determines:</p>
     * <ul>
     *   <li>Which definitions should be skipped (built-in containers → null)</li>
     *   <li>Which definitions should generate classes (domain types → base type or as-is)</li>
     *   <li>How to extract base types from generic instantiations</li>
     * </ul>
     *
     * <p><b>Aiken Compiler Syntax Support:</b></p>
     * <ul>
     *   <li><b>Aiken v1.0.x:</b> Dollar syntax - "Interval$Int", "Option$ByteArray"</li>
     *   <li><b>Aiken v1.1.x:</b> Angle bracket syntax - "Interval&lt;Int&gt;", "Option&lt;Credential&gt;"</li>
     * </ul>
     *
     * <p><b>Real-World Examples:</b></p>
     * <ul>
     *   <li>SundaeSwap V2 (Aiken v1.0.26): "Interval$Int" → "Interval" (generate typed class)</li>
     *   <li>SundaeSwap V3 (Aiken v1.1.21): "ValidityRange" → "ValidityRange" (as-is)</li>
     * </ul>
     */
    @Nested
    @DisplayName("resolveDefinitionKeyForClassGeneration() tests")
    class ResolveDefinitionKeyForClassGenerationTests {

        // ========================================
        // BUILT-IN CONTAINERS → null (skip generation)
        // ========================================

        @Nested
        @DisplayName("Built-in containers should return null (skip generation)")
        class BuiltInContainerTests {

            @Test
            @DisplayName("Dollar syntax: List$Int → null")
            void shouldSkip_listDollarInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("List$Int")).isNull();
            }

            @Test
            @DisplayName("Dollar syntax: Option$ByteArray → null")
            void shouldSkip_optionDollarByteArray() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Option$ByteArray")).isNull();
            }

            @Test
            @DisplayName("Dollar syntax: Tuple$Int_String → null")
            void shouldSkip_tupleDollarIntString() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Tuple$Int_String")).isNull();
            }

            @Test
            @DisplayName("Dollar syntax: Map$String_Int → null")
            void shouldSkip_mapDollarStringInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Map$String_Int")).isNull();
            }

            @Test
            @DisplayName("Angle bracket syntax: List<Int> → null")
            void shouldSkip_listAngleBracketInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("List<Int>")).isNull();
            }

            @Test
            @DisplayName("Angle bracket syntax: Option<Credential> → null")
            void shouldSkip_optionAngleBracketCredential() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Option<Credential>")).isNull();
            }

            @Test
            @DisplayName("Angle bracket syntax: Option<types/order/Action> → null")
            void shouldSkip_optionAngleBracketWithPath() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Option<types/order/Action>")).isNull();
            }

            @Test
            @DisplayName("Angle bracket syntax: Tuple<Int,String> → null")
            void shouldSkip_tupleAngleBracket() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Tuple<Int,String>")).isNull();
            }

            @Test
            @DisplayName("Angle bracket syntax: Pair<Int,String> → null")
            void shouldSkip_pairAngleBracket() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Pair<Int,String>")).isNull();
            }

            @Test
            @DisplayName("Abstract Data type → null")
            void shouldSkip_dataType() {
                // Data is abstract PlutusData type per CIP-57
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Data")).isEqualTo("Data");
            }

            @Test
            @DisplayName("Nested generics: List<Option<Int>> → null")
            void shouldSkip_nestedGenerics() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("List<Option<Int>>")).isNull();
            }
        }

        // ========================================
        // DOMAIN-SPECIFIC GENERICS → base type (generate typed class)
        // ========================================

        @Nested
        @DisplayName("Domain-specific generics should return base type")
        class DomainSpecificGenericTests {

            @Test
            @DisplayName("Dollar syntax: Interval$Int → Interval")
            void shouldExtractBaseType_intervalDollarInt() {
                // REAL-WORLD: SundaeSwap V2 "aiken/interval/Interval$Int"
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Interval$Int"))
                        .isEqualTo("Interval");
            }

            @Test
            @DisplayName("Dollar syntax: IntervalBound$Int → IntervalBound")
            void shouldExtractBaseType_intervalBoundDollarInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("IntervalBound$Int"))
                        .isEqualTo("IntervalBound");
            }

            @Test
            @DisplayName("Dollar syntax: IntervalBoundType$Int → IntervalBoundType")
            void shouldExtractBaseType_intervalBoundTypeDollarInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("IntervalBoundType$Int"))
                        .isEqualTo("IntervalBoundType");
            }

            @Test
            @DisplayName("Angle bracket syntax: Interval<Int> → Interval")
            void shouldExtractBaseType_intervalAngleBracketInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Interval<Int>"))
                        .isEqualTo("Interval");
            }

            @Test
            @DisplayName("Angle bracket syntax: IntervalBound<Int> → IntervalBound")
            void shouldExtractBaseType_intervalBoundAngleBracket() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("IntervalBound<Int>"))
                        .isEqualTo("IntervalBound");
            }

            @Test
            @DisplayName("With namespace path (dollar): aiken/interval/Interval$Int → aiken/interval/Interval")
            void shouldPreserveNamespacePath_dollarSyntax() {
                // CRITICAL: Namespace path must be preserved for package generation
                assertThat(processor.resolveDefinitionKeyForClassGeneration("aiken/interval/Interval$Int"))
                        .isEqualTo("aiken/interval/Interval");
            }

            @Test
            @DisplayName("With namespace path (angle): aiken/interval/IntervalBound<Int> → aiken/interval/IntervalBound")
            void shouldPreserveNamespacePath_angleBracketSyntax() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("aiken/interval/IntervalBound<Int>"))
                        .isEqualTo("aiken/interval/IntervalBound");
            }

            @Test
            @DisplayName("Custom domain type with path: custom/types/Container$String → custom/types/Container")
            void shouldHandleCustomDomainTypes() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("custom/types/Container$String"))
                        .isEqualTo("custom/types/Container");
            }
        }

        // ========================================
        // NON-GENERIC TYPES → as-is (generate class)
        // ========================================

        @Nested
        @DisplayName("Non-generic types should return as-is")
        class NonGenericTypeTests {

            @Test
            @DisplayName("Simple type: ValidityRange → ValidityRange")
            void shouldReturnAsIs_validityRange() {
                // REAL-WORLD: SundaeSwap V3 "cardano/transaction/ValidityRange"
                assertThat(processor.resolveDefinitionKeyForClassGeneration("ValidityRange"))
                        .isEqualTo("ValidityRange");
            }

            @Test
            @DisplayName("With namespace: types/order/Action → types/order/Action")
            void shouldReturnAsIs_typeWithNamespace() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("types/order/Action"))
                        .isEqualTo("types/order/Action");
            }

            @Test
            @DisplayName("With namespace: cardano/transaction/ValidityRange → cardano/transaction/ValidityRange")
            void shouldReturnAsIs_cardanoType() {
                // REAL-WORLD: SundaeSwap V3 semantic type alias (NOT a generic instantiation)
                assertThat(processor.resolveDefinitionKeyForClassGeneration("cardano/transaction/ValidityRange"))
                        .isEqualTo("cardano/transaction/ValidityRange");
            }

            @Test
            @DisplayName("Cardano type: cardano/address/Credential → cardano/address/Credential")
            void shouldReturnAsIs_credential() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("cardano/address/Credential"))
                        .isEqualTo("cardano/address/Credential");
            }

            @Test
            @DisplayName("Custom type: MyCustomType → MyCustomType")
            void shouldReturnAsIs_customType() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("MyCustomType"))
                        .isEqualTo("MyCustomType");
            }
        }

        // ========================================
        // REAL-WORLD SUNDAESWAP EXAMPLES
        // ========================================

        @Nested
        @DisplayName("Real-world SundaeSwap blueprint examples")
        class RealWorldExamplesTests {

            @Test
            @DisplayName("SundaeSwap V2: aiken/interval/Interval$Int → aiken/interval/Interval")
            void sundaeSwapV2_intervalInt() {
                // WHY: Generate typed Interval class, fields use Interval (not PlutusData)
                assertThat(processor.resolveDefinitionKeyForClassGeneration("aiken/interval/Interval$Int"))
                        .isEqualTo("aiken/interval/Interval");
            }

            @Test
            @DisplayName("SundaeSwap V2: aiken/interval/IntervalBound$Int → aiken/interval/IntervalBound")
            void sundaeSwapV2_intervalBoundInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("aiken/interval/IntervalBound$Int"))
                        .isEqualTo("aiken/interval/IntervalBound");
            }

            @Test
            @DisplayName("SundaeSwap V3: cardano/transaction/ValidityRange → cardano/transaction/ValidityRange")
            void sundaeSwapV3_validityRange() {
                // WHY: Semantic alias (NOT generic instantiation), uses definition key as class name
                assertThat(processor.resolveDefinitionKeyForClassGeneration("cardano/transaction/ValidityRange"))
                        .isEqualTo("cardano/transaction/ValidityRange");
            }

            @Test
            @DisplayName("SundaeSwap V3: Option<cardano/address/Credential> → null (skip)")
            void sundaeSwapV3_optionCredential() {
                // WHY: Built-in container, field uses Optional<Credential> via OptionDataTypeProcessor
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Option<cardano/address/Credential>"))
                        .isNull();
            }
        }

        // ========================================
        // EDGE CASES
        // ========================================

        @Nested
        @DisplayName("Edge cases and special syntax handling")
        class EdgeCaseTests {

            /**
             * <b>Note:</b> The method expects valid definition keys from blueprint JSON.
             * Null or malformed inputs (e.g., "$Int", empty string) are not real-world scenarios
             * and may throw exceptions or produce unexpected results. These are not tested as
             * they represent invalid blueprint data that would fail JSON parsing.
             */

            @Test
            @DisplayName("Empty string → empty string")
            void shouldHandleEmptyString() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("")).isEqualTo("");
            }

            @Test
            @DisplayName("Multiple $ signs: Foo$Bar$Baz → Foo")
            void shouldHandleMultipleDollarSigns() {
                // Extract first segment before first $
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Foo$Bar$Baz"))
                        .isEqualTo("Foo");
            }

            @Test
            @DisplayName("Multiple angle brackets: Foo<Bar<Baz>> → Foo")
            void shouldHandleNestedAngleBrackets() {
                // Extract base type before first <
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Foo<Bar<Baz>>"))
                        .isEqualTo("Foo");
            }

            @Test
            @DisplayName("Mixed syntax: Foo$Bar<Baz> → Foo ($ takes precedence)")
            void shouldHandleMixedSyntax() {
                // Both $ and < present - $ is processed first
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Foo$Bar<Baz>"))
                        .isEqualTo("Foo");
            }

            @Test
            @DisplayName("Trailing $: Foo$ → Foo")
            void shouldHandleTrailingDollar() {
                // Malformed but extracts base type before $
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Foo$"))
                        .isEqualTo("Foo");
            }

            @Test
            @DisplayName("Trailing <: Foo< → Foo")
            void shouldHandleTrailingAngleBracket() {
                // Malformed but extracts base type before <
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Foo<"))
                        .isEqualTo("Foo");
            }
        }
    }

    /**
     * Integration tests for generic type instantiation skip logic.
     *
     * <p><b>Purpose:</b> Verifies that blueprints with generic type instantiations
     * (e.g., "Option&lt;Int&gt;", "List&lt;ByteArray&gt;") compile successfully without errors.</p>
     *
     * <p><b>What these tests validate:</b></p>
     * <ul>
     *   <li>Generic instantiations don't cause compilation errors</li>
     *   <li>No generic container classes are generated (List.java, Option.java)</li>
     *   <li>Concrete types ARE still generated (validators, domain types)</li>
     *   <li>Both dollar ($) and angle bracket (&lt;&gt;) syntax work</li>
     * </ul>
     */
    @Nested
    @DisplayName("Generic type skip integration tests")
    class GenericTypeSkipTests {

        @Test
        @DisplayName("should successfully process blueprint with simple generic instantiations")
        void shouldSuccessfullyProcessBlueprintWith_simpleGenericInstantiations() {
            // Blueprint contains: "Option<Int>", "Option<types/order/Action>", "List<ByteArray>"
            // These should be skipped (not cause compilation errors)
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/GenericOptionTypes.java"));

            // CRITICAL: Compilation must succeed (before fix, it would fail with invalid class name errors)
            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(jfo -> jfo.getName())
                    .collect(Collectors.toList());

            // Verify that SOME classes were generated (concrete types)
            assertThat(generatedSources)
                    .as("Should generate validator and at least some datum classes")
                    .isNotEmpty();

            // Verify no generic container classes were generated
            assertThat(generatedSources)
                    .as("Should not generate Option or List generic container classes")
                    .noneMatch(name -> name.matches(".*/Option\\.java") || name.matches(".*/List\\.java"));
        }

        @Test
        @DisplayName("should successfully process blueprint with nested generic instantiations")
        void shouldSuccessfullyProcessBlueprintWith_nestedGenericInstantiations() {
            // Blueprint contains: "List<Option<types/order/Action>>", "Tuple<<types/order/Action,types/order/Status>>"
            // These should be skipped
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/GenericNestedTypes.java"));

            // CRITICAL: Nested generics must not break compilation
            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(jfo -> jfo.getName())
                    .collect(Collectors.toList());

            assertThat(generatedSources)
                    .as("Should generate classes despite nested generic definitions")
                    .isNotEmpty();

            // Verify no generic wrapper classes
            assertThat(generatedSources)
                    .noneMatch(name -> name.matches(".*/List\\.java") ||
                                       name.matches(".*/Option\\.java") ||
                                       name.matches(".*/Tuple\\.java"));
        }

        @Test
        @DisplayName("should successfully process blueprint with Cardano built-in generics")
        void shouldSuccessfullyProcessBlueprintWith_cardanoBuiltinGenerics() {
            // Blueprint contains: "Option<cardano/address/Credential>", "List<cardano/transaction/OutputReference>"
            // Real-world pattern from SundaeSwap V3 - must compile successfully
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/GenericCardanoBuiltins.java"));

            // CRITICAL: Real-world pattern must compile (this was failing before the fix)
            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(jfo -> jfo.getName())
                    .collect(Collectors.toList());

            assertThat(generatedSources)
                    .as("Should generate Cardano types and custom types")
                    .isNotEmpty();

            // Verify no generic wrappers
            assertThat(generatedSources)
                    .noneMatch(name -> name.matches(".*/Option\\.java") || name.matches(".*/List\\.java"));
        }

        @Test
        @DisplayName("should successfully process blueprint with dollar sign generic syntax (Aiken v1.0.x)")
        void shouldSuccessfullyProcessBlueprintWith_dollarSignGenericSyntax() {
            // Blueprint contains OLD Aiken v1.0.x dollar sign syntax:
            // "Option$Int", "List$ByteArray", "Option$types/storage/Item"
            // "Option$List$types/storage/Item" (nested dollar signs)
            //
            // This is the CRITICAL backward compatibility test - old Aiken versions
            // (v1.0.26 and earlier) used $ instead of <> for generic type parameters.
            // Real-world example: SundaeSwap V2 blueprint has "List$Tuple$Int_Option$types/order/SignedStrategyExecution_Int"
            //
            // Before fix: NullPointerException in FieldSpecProcessor.createDatumTypeSpec()
            // After fix: These should be skipped just like angle bracket generics
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/GenericDollarSignSyntax.java"));

            // CRITICAL: Must compile successfully (was throwing NPE before adding $ check)
            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(jfo -> jfo.getName())
                    .collect(Collectors.toList());

            assertThat(generatedSources)
                    .as("Should generate concrete types (Item, StorageData) but not generic wrappers")
                    .isNotEmpty();

            // Verify dollar sign generic instantiations were skipped (not generated as classes)
            assertThat(generatedSources)
                    .as("Should not generate Option$Int, List$ByteArray or other $ generic classes")
                    .noneMatch(name -> name.matches(".*/Option\\$.*\\.java") ||
                                       name.matches(".*/List\\$.*\\.java"));
        }
    }

    // ========================================
    // TYPE ALIAS DETECTION & RESOLUTION TESTS
    // ========================================

    /**
     * Tests for {@link BlueprintAnnotationProcessor#detectTypeAliases(Map)} and
     * {@link BlueprintAnnotationProcessor#resolveTypeAliases(Map, List, Map)}.
     *
     * <p>Type aliases occur when two blueprint definitions have structurally identical
     * {@code anyOf} variants in the same namespace. For example, SundaeSwap V3's
     * {@code PaymentCredential} is an alias for {@code Credential} — both have
     * identical VerificationKey/Script variants.</p>
     */
    @Nested
    @DisplayName("detectTypeAliases() tests")
    class DetectTypeAliasesTests {

        @Nested
        @DisplayName("Should return empty map when no aliases exist")
        class NoAliasesTests {

            @Test
            @DisplayName("empty definitions map")
            void emptyDefinitions() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }

            @Test
            @DisplayName("definitions without anyOf are ignored")
            void definitionsWithoutAnyOf() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Foo", schemaWithoutAnyOf());
                definitions.put("cardano/address/Bar", schemaWithoutAnyOf());
                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }

            @Test
            @DisplayName("anyOf with single variant is ignored")
            void singleVariantAnyOf() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Foo", schemaWithAnyOf(
                        variant("Only", 0, "#/definitions/SomeRef")));
                definitions.put("cardano/address/Bar", schemaWithAnyOf(
                        variant("Only", 0, "#/definitions/SomeRef")));
                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }

            @Test
            @DisplayName("same variants but different namespaces are not aliases")
            void differentNamespaces() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", credentialSchema());
                definitions.put("other/namespace/Credential", credentialSchema());
                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }

            @Test
            @DisplayName("same namespace but different variant titles")
            void differentVariantTitles() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", schemaWithAnyOf(
                        variant("VerificationKey", 0, "#/definitions/ref1"),
                        variant("Script", 1, "#/definitions/ref2")));
                definitions.put("cardano/address/Other", schemaWithAnyOf(
                        variant("PubKey", 0, "#/definitions/ref1"),
                        variant("Script", 1, "#/definitions/ref2")));
                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }

            @Test
            @DisplayName("same namespace and titles but different constructor indices")
            void differentConstructorIndices() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", schemaWithAnyOf(
                        variant("VerificationKey", 0, "#/definitions/ref1"),
                        variant("Script", 1, "#/definitions/ref2")));
                definitions.put("cardano/address/Other", schemaWithAnyOf(
                        variant("VerificationKey", 0, "#/definitions/ref1"),
                        variant("Script", 2, "#/definitions/ref2")));
                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }

            @Test
            @DisplayName("same namespace and titles but different field refs")
            void differentFieldRefs() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", schemaWithAnyOf(
                        variant("VerificationKey", 0, "#/definitions/ref1"),
                        variant("Script", 1, "#/definitions/ref2")));
                definitions.put("cardano/address/Other", schemaWithAnyOf(
                        variant("VerificationKey", 0, "#/definitions/ref1"),
                        variant("Script", 1, "#/definitions/ref3")));
                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }
        }

        @Nested
        @DisplayName("Should detect aliases correctly")
        class AliasDetectionTests {

            @Test
            @DisplayName("PaymentCredential is alias for Credential (real-world SundaeSwap V3)")
            void paymentCredentialAliasForCredential() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", credentialSchema());
                definitions.put("cardano/address/PaymentCredential", credentialSchema());

                Map<String, String> aliases = processor.detectTypeAliases(definitions);

                assertThat(aliases).hasSize(1);
                assertThat(aliases).containsEntry(
                        "cardano/address/PaymentCredential",
                        "cardano/address/Credential");
            }

            @Test
            @DisplayName("alphabetically first key becomes canonical")
            void alphabeticalCanonicalSelection() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                // Insert in reverse alphabetical order to verify sorting
                definitions.put("ns/Zebra", credentialSchema());
                definitions.put("ns/Alpha", credentialSchema());

                Map<String, String> aliases = processor.detectTypeAliases(definitions);

                assertThat(aliases).hasSize(1);
                // Alpha is first alphabetically → canonical; Zebra → alias
                assertThat(aliases).containsEntry("ns/Zebra", "ns/Alpha");
            }

            @Test
            @DisplayName("multiple aliases for same canonical type")
            void multipleAliases() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Alpha", credentialSchema());
                definitions.put("ns/Beta", credentialSchema());
                definitions.put("ns/Gamma", credentialSchema());

                Map<String, String> aliases = processor.detectTypeAliases(definitions);

                assertThat(aliases).hasSize(2);
                assertThat(aliases).containsEntry("ns/Beta", "ns/Alpha");
                assertThat(aliases).containsEntry("ns/Gamma", "ns/Alpha");
            }

            @Test
            @DisplayName("definitions without anyOf are not affected by alias detection")
            void nonAnyOfDefinitionsUntouched() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", credentialSchema());
                definitions.put("cardano/address/PaymentCredential", credentialSchema());
                definitions.put("cardano/address/SimpleType", schemaWithoutAnyOf());

                Map<String, String> aliases = processor.detectTypeAliases(definitions);

                assertThat(aliases).hasSize(1);
                assertThat(aliases).containsEntry(
                        "cardano/address/PaymentCredential",
                        "cardano/address/Credential");
            }

            @Test
            @DisplayName("variant order does not matter (signatures are sorted)")
            void variantOrderIndependent() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/TypeA", schemaWithAnyOf(
                        variant("Script", 1, "#/definitions/ref2"),
                        variant("VerificationKey", 0, "#/definitions/ref1")));
                definitions.put("ns/TypeB", schemaWithAnyOf(
                        variant("VerificationKey", 0, "#/definitions/ref1"),
                        variant("Script", 1, "#/definitions/ref2")));

                Map<String, String> aliases = processor.detectTypeAliases(definitions);

                assertThat(aliases).hasSize(1);
            }

            @Test
            @DisplayName("variants with multiple fields are compared correctly")
            void multipleFieldsPerVariant() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/TypeA", schemaWithAnyOf(
                        variant("Ctor", 0, "#/definitions/ref1", "#/definitions/ref2"),
                        variant("Other", 1)));
                definitions.put("ns/TypeB", schemaWithAnyOf(
                        variant("Ctor", 0, "#/definitions/ref1", "#/definitions/ref2"),
                        variant("Other", 1)));

                Map<String, String> aliases = processor.detectTypeAliases(definitions);

                assertThat(aliases).hasSize(1);
                assertThat(aliases).containsEntry("ns/TypeB", "ns/TypeA");
            }

            @Test
            @DisplayName("variants with different number of fields are not aliases")
            void differentFieldCountNotAlias() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/TypeA", schemaWithAnyOf(
                        variant("Ctor", 0, "#/definitions/ref1", "#/definitions/ref2"),
                        variant("Other", 1)));
                definitions.put("ns/TypeB", schemaWithAnyOf(
                        variant("Ctor", 0, "#/definitions/ref1"),
                        variant("Other", 1)));

                assertThat(processor.detectTypeAliases(definitions)).isEmpty();
            }

            @Test
            @DisplayName("two independent alias groups are detected separately")
            void twoIndependentAliasGroups() {
                BlueprintSchema groupA = schemaWithAnyOf(
                        variant("X", 0, "#/definitions/refX"),
                        variant("Y", 1, "#/definitions/refY"));
                BlueprintSchema groupB = schemaWithAnyOf(
                        variant("P", 0, "#/definitions/refP"),
                        variant("Q", 1, "#/definitions/refQ"));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/A1", groupA);
                definitions.put("ns/A2", cloneAnyOfSchema(groupA));
                definitions.put("ns/B1", groupB);
                definitions.put("ns/B2", cloneAnyOfSchema(groupB));

                Map<String, String> aliases = processor.detectTypeAliases(definitions);

                assertThat(aliases).hasSize(2);
                assertThat(aliases).containsEntry("ns/A2", "ns/A1");
                assertThat(aliases).containsEntry("ns/B2", "ns/B1");
            }
        }
    }

    @Nested
    @DisplayName("resolveTypeAliases() tests")
    class ResolveTypeAliasesTests {

        @Nested
        @DisplayName("Definition map modifications")
        class DefinitionMapTests {

            @Test
            @DisplayName("removes alias definitions from the map")
            void removesAliasDefinitions() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", credentialSchema());
                definitions.put("cardano/address/PaymentCredential", credentialSchema());

                Map<String, String> aliases = Map.of(
                        "cardano/address/PaymentCredential", "cardano/address/Credential");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(definitions).containsKey("cardano/address/Credential");
                assertThat(definitions).doesNotContainKey("cardano/address/PaymentCredential");
            }

            @Test
            @DisplayName("removes multiple aliases from the map")
            void removesMultipleAliases() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Alpha", credentialSchema());
                definitions.put("ns/Beta", credentialSchema());
                definitions.put("ns/Gamma", credentialSchema());

                Map<String, String> aliases = Map.of(
                        "ns/Beta", "ns/Alpha",
                        "ns/Gamma", "ns/Alpha");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(definitions).containsOnlyKeys("ns/Alpha");
            }
        }

        @Nested
        @DisplayName("$ref rewriting in definitions")
        class DefinitionRefRewriteTests {

            @Test
            @DisplayName("rewrites $ref in remaining definition fields")
            void rewritesRefInDefinitionFields() {
                BlueprintSchema addressSchema = schemaWithAnyOf(
                        variantWithFieldRef("Keyed", 0, "#/definitions/cardano~1address~1PaymentCredential"),
                        variant("Other", 1));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", credentialSchema());
                definitions.put("cardano/address/PaymentCredential", credentialSchema());
                definitions.put("cardano/address/Address", addressSchema);

                Map<String, String> aliases = Map.of(
                        "cardano/address/PaymentCredential", "cardano/address/Credential");

                processor.resolveTypeAliases(definitions, null, aliases);

                // The field ref should be rewritten to the canonical type
                BlueprintSchema field = definitions.get("cardano/address/Address")
                        .getAnyOf().get(0).getFields().get(0);
                assertThat(field.getRef()).isEqualTo("#/definitions/cardano~1address~1Credential");
            }

            @Test
            @DisplayName("rewrites $ref in nested anyOf > fields > $ref")
            void rewritesNestedRefs() {
                BlueprintSchema nestedField = new BlueprintSchema();
                nestedField.setRef("#/definitions/ns~1AliasType");

                BlueprintSchema innerVariant = new BlueprintSchema();
                innerVariant.setTitle("Inner");
                innerVariant.setIndex(0);
                innerVariant.setFields(List.of(nestedField));

                BlueprintSchema outerFieldSchema = new BlueprintSchema();
                outerFieldSchema.setAnyOf(List.of(innerVariant, variant("X", 1)));

                BlueprintSchema outerVariant = new BlueprintSchema();
                outerVariant.setTitle("Outer");
                outerVariant.setIndex(0);
                outerVariant.setFields(List.of(outerFieldSchema));

                BlueprintSchema rootSchema = new BlueprintSchema();
                rootSchema.setAnyOf(List.of(outerVariant, variant("Z", 1)));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/Root", rootSchema);

                Map<String, String> aliases = Map.of("ns/AliasType", "ns/Canonical");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(nestedField.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }

            @Test
            @DisplayName("rewrites $ref in items (list element type)")
            void rewritesRefInItems() {
                BlueprintSchema itemRef = new BlueprintSchema();
                itemRef.setRef("#/definitions/ns~1Alias");

                BlueprintSchema listSchema = new BlueprintSchema();
                listSchema.setItems(List.of(itemRef));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/ListHolder", listSchema);

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(itemRef.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }

            @Test
            @DisplayName("rewrites $ref in keys and values (map types)")
            void rewritesRefInKeysAndValues() {
                BlueprintSchema keyRef = new BlueprintSchema();
                keyRef.setRef("#/definitions/ns~1Alias");
                BlueprintSchema valueRef = new BlueprintSchema();
                valueRef.setRef("#/definitions/ns~1Alias");

                BlueprintSchema mapSchema = new BlueprintSchema();
                mapSchema.setKeys(keyRef);
                mapSchema.setValues(valueRef);

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/MapHolder", mapSchema);

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(keyRef.getRef()).isEqualTo("#/definitions/ns~1Canonical");
                assertThat(valueRef.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }

            @Test
            @DisplayName("rewrites $ref in left and right (pair types)")
            void rewritesRefInLeftAndRight() {
                BlueprintSchema leftRef = new BlueprintSchema();
                leftRef.setRef("#/definitions/ns~1Alias");
                BlueprintSchema rightRef = new BlueprintSchema();
                rightRef.setRef("#/definitions/ns~1Alias");

                BlueprintSchema pairSchema = new BlueprintSchema();
                pairSchema.setLeft(leftRef);
                pairSchema.setRight(rightRef);

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/PairHolder", pairSchema);

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(leftRef.getRef()).isEqualTo("#/definitions/ns~1Canonical");
                assertThat(rightRef.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }

            @Test
            @DisplayName("does not rewrite $ref that do not match aliases")
            void doesNotRewriteNonAliasRefs() {
                BlueprintSchema field = new BlueprintSchema();
                field.setRef("#/definitions/ns~1Unrelated");

                BlueprintSchema schema = schemaWithAnyOf(
                        variantWithFieldRef("V", 0, "#/definitions/ns~1Unrelated"),
                        variant("W", 1));
                // Replace the field to our tracked object
                schema.getAnyOf().get(0).getFields().set(0, field);

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/Holder", schema);

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(field.getRef()).isEqualTo("#/definitions/ns~1Unrelated");
            }
        }

        @Nested
        @DisplayName("$ref rewriting in validators")
        class ValidatorRefRewriteTests {

            @Test
            @DisplayName("rewrites $ref in validator datum schema")
            void rewritesDatumRef() {
                BlueprintSchema datumSchema = new BlueprintSchema();
                datumSchema.setRef("#/definitions/ns~1Alias");
                BlueprintDatum datum = new BlueprintDatum();
                datum.setSchema(datumSchema);

                Validator validator = new Validator();
                validator.setDatum(datum);

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                processor.resolveTypeAliases(definitions, List.of(validator), aliases);

                assertThat(datumSchema.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }

            @Test
            @DisplayName("rewrites $ref in validator redeemer schema")
            void rewritesRedeemerRef() {
                BlueprintSchema redeemerSchema = new BlueprintSchema();
                redeemerSchema.setRef("#/definitions/ns~1Alias");
                BlueprintDatum redeemer = new BlueprintDatum();
                redeemer.setSchema(redeemerSchema);

                Validator validator = new Validator();
                validator.setRedeemer(redeemer);

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                processor.resolveTypeAliases(definitions, List.of(validator), aliases);

                assertThat(redeemerSchema.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }

            @Test
            @DisplayName("rewrites $ref in validator parameter schemas")
            void rewritesParameterRef() {
                BlueprintSchema paramSchema = new BlueprintSchema();
                paramSchema.setRef("#/definitions/ns~1Alias");
                BlueprintDatum param = new BlueprintDatum();
                param.setSchema(paramSchema);

                Validator validator = new Validator();
                validator.setParameters(List.of(param));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                processor.resolveTypeAliases(definitions, List.of(validator), aliases);

                assertThat(paramSchema.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }

            @Test
            @DisplayName("handles null validators list")
            void handlesNullValidators() {
                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/Alias", credentialSchema());

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                // Should not throw
                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(definitions).containsOnlyKeys("ns/Canonical");
            }

            @Test
            @DisplayName("handles validators with null datum/redeemer/parameters")
            void handlesValidatorsWithNullSchemas() {
                Validator validator = new Validator();
                // datum, redeemer, parameters all null

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/Alias", credentialSchema());

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                // Should not throw
                processor.resolveTypeAliases(definitions, List.of(validator), aliases);

                assertThat(definitions).containsOnlyKeys("ns/Canonical");
            }
        }

        @Nested
        @DisplayName("Cycle handling")
        class CycleHandlingTests {

            @Test
            @DisplayName("handles circular schema references without StackOverflow")
            void handlesCircularReferences() {
                // Create a cycle: schema A → anyOf → variant → fields → schema B → anyOf → variant → fields → schema A
                BlueprintSchema schemaA = new BlueprintSchema();
                BlueprintSchema schemaB = new BlueprintSchema();

                BlueprintSchema fieldRefA = new BlueprintSchema();
                fieldRefA.setRef("#/definitions/ns~1Alias");

                BlueprintSchema variantA = new BlueprintSchema();
                variantA.setTitle("V");
                variantA.setIndex(0);
                variantA.setFields(List.of(schemaB));

                BlueprintSchema variantA2 = new BlueprintSchema();
                variantA2.setTitle("W");
                variantA2.setIndex(1);

                schemaA.setAnyOf(List.of(variantA, variantA2));

                BlueprintSchema variantB = new BlueprintSchema();
                variantB.setTitle("X");
                variantB.setIndex(0);
                variantB.setFields(List.of(schemaA, fieldRefA)); // cycle back to A + alias ref

                BlueprintSchema variantB2 = new BlueprintSchema();
                variantB2.setTitle("Y");
                variantB2.setIndex(1);

                schemaB.setAnyOf(List.of(variantB, variantB2));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("ns/Canonical", credentialSchema());
                definitions.put("ns/Root", schemaA);

                Map<String, String> aliases = Map.of("ns/Alias", "ns/Canonical");

                // Should not throw StackOverflowError
                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(fieldRefA.getRef()).isEqualTo("#/definitions/ns~1Canonical");
            }
        }

        @Nested
        @DisplayName("$ref encoding")
        class RefEncodingTests {

            @Test
            @DisplayName("correctly encodes slashes as ~1 in $ref rewrites")
            void encodesSlashesCorrectly() {
                BlueprintSchema field = new BlueprintSchema();
                field.setRef("#/definitions/cardano~1address~1PaymentCredential");

                BlueprintSchema schema = new BlueprintSchema();
                schema.setAnyOf(List.of(variantWithField("V", 0, field), variant("W", 1)));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("cardano/address/Credential", credentialSchema());
                definitions.put("some/other/Def", schema);

                Map<String, String> aliases = Map.of(
                        "cardano/address/PaymentCredential", "cardano/address/Credential");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(field.getRef()).isEqualTo("#/definitions/cardano~1address~1Credential");
            }

            @Test
            @DisplayName("handles deeply nested namespace paths")
            void deeplyNestedNamespace() {
                BlueprintSchema field = new BlueprintSchema();
                field.setRef("#/definitions/a~1b~1c~1Alias");

                BlueprintSchema schema = new BlueprintSchema();
                schema.setAnyOf(List.of(variantWithField("V", 0, field), variant("W", 1)));

                Map<String, BlueprintSchema> definitions = new LinkedHashMap<>();
                definitions.put("a/b/c/Canonical", credentialSchema());
                definitions.put("x/y/Holder", schema);

                Map<String, String> aliases = Map.of("a/b/c/Alias", "a/b/c/Canonical");

                processor.resolveTypeAliases(definitions, null, aliases);

                assertThat(field.getRef()).isEqualTo("#/definitions/a~1b~1c~1Canonical");
            }
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Creates a schema mimicking SundaeSwap V3's Credential/PaymentCredential structure:
     * anyOf with VerificationKey (index 0) and Script (index 1) variants.
     */
    private static BlueprintSchema credentialSchema() {
        return schemaWithAnyOf(
                variant("VerificationKey", 0, "#/definitions/aiken~1crypto~1VerificationKeyHash"),
                variant("Script", 1, "#/definitions/aiken~1crypto~1ScriptHash"));
    }

    private static BlueprintSchema schemaWithoutAnyOf() {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setTitle("Simple");
        return schema;
    }

    private static BlueprintSchema schemaWithAnyOf(BlueprintSchema... variants) {
        BlueprintSchema schema = new BlueprintSchema();
        schema.setAnyOf(new ArrayList<>(Arrays.asList(variants)));
        return schema;
    }

    private static BlueprintSchema variant(String title, int index, String... fieldRefs) {
        BlueprintSchema v = new BlueprintSchema();
        v.setTitle(title);
        v.setIndex(index);
        if (fieldRefs.length > 0) {
            List<BlueprintSchema> fields = new ArrayList<>();
            for (String ref : fieldRefs) {
                BlueprintSchema field = new BlueprintSchema();
                field.setRef(ref);
                fields.add(field);
            }
            v.setFields(fields);
        }
        return v;
    }

    private static BlueprintSchema variantWithFieldRef(String title, int index, String fieldRef) {
        return variant(title, index, fieldRef);
    }

    private static BlueprintSchema variantWithField(String title, int index, BlueprintSchema field) {
        BlueprintSchema v = new BlueprintSchema();
        v.setTitle(title);
        v.setIndex(index);
        v.setFields(new ArrayList<>(List.of(field)));
        return v;
    }

    /**
     * Creates a new schema with the same anyOf structure (fresh objects, same titles/indices/refs).
     */
    private static BlueprintSchema cloneAnyOfSchema(BlueprintSchema original) {
        List<BlueprintSchema> clonedVariants = new ArrayList<>();
        for (BlueprintSchema v : original.getAnyOf()) {
            String[] fieldRefs = v.getFields() != null
                    ? v.getFields().stream().map(BlueprintSchema::getRef).toArray(String[]::new)
                    : new String[0];
            clonedVariants.add(variant(v.getTitle(), v.getIndex(), fieldRefs));
        }
        BlueprintSchema clone = new BlueprintSchema();
        clone.setAnyOf(clonedVariants);
        return clone;
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }
}
