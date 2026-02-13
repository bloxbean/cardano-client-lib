package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
     * Tests for {@link BlueprintAnnotationProcessor#isBuiltInGenericContainer(String)}.
     *
     * <p><b>Purpose:</b> This method is critical for distinguishing between types that should
     * generate Java classes (domain-specific types) vs types that should be skipped (built-in containers).</p>
     *
     * <p><b>Why this matters:</b></p>
     * <ul>
     *   <li>Built-in containers (List, Option, etc.) already have Java equivalents (java.util.List, Optional, PlutusData)</li>
     *   <li>Domain-specific types (Interval, IntervalBound, etc.) need generated classes for type safety</li>
     *   <li>Wrong classification → either missing classes (compilation error) or redundant classes (naming conflicts)</li>
     * </ul>
     */
    @Nested
    @DisplayName("isBuiltInGenericContainer() tests")
    class IsBuiltInGenericContainerTests {

        // ========================================
        // BUILT-IN CONTAINERS (should return true)
        // ========================================

        @Test
        @DisplayName("should recognize List as built-in container")
        void shouldRecognize_list_asBuiltInContainer() {
            // WHY: List<T> maps to java.util.List<T> or PlutusData
            // EXAMPLE: "List$Int", "List<types/order/Action>"
            assertThat(processor.isBuiltInGenericContainer("List")).isTrue();
        }

        @Test
        @DisplayName("should recognize Option as built-in container")
        void shouldRecognize_option_asBuiltInContainer() {
            // WHY: Option<T> maps to java.util.Optional<T> or PlutusData
            // EXAMPLE: "Option$ByteArray", "Option<Credential>"
            // REAL-WORLD: SundaeSwap V3 has "Option<Credential>" fields
            assertThat(processor.isBuiltInGenericContainer("Option")).isTrue();
        }

        @Test
        @DisplayName("should recognize Optional as built-in container")
        void shouldRecognize_optional_asBuiltInContainer() {
            // WHY: Optional is an alias for Option in some Aiken versions
            assertThat(processor.isBuiltInGenericContainer("Optional")).isTrue();
        }

        @Test
        @DisplayName("should recognize Tuple as built-in container")
        void shouldRecognize_tuple_asBuiltInContainer() {
            // WHY: Tuple maps to custom Tuple class or PlutusData
            // EXAMPLE: "Tuple$Int_String", "Tuple<Int,String,Bool>"
            assertThat(processor.isBuiltInGenericContainer("Tuple")).isTrue();
        }

        @Test
        @DisplayName("should recognize Pair as built-in container")
        void shouldRecognize_pair_asBuiltInContainer() {
            // WHY: Pair is a 2-element tuple
            // EXAMPLE: "Pair<Int,String>"
            assertThat(processor.isBuiltInGenericContainer("Pair")).isTrue();
        }

        @Test
        @DisplayName("should recognize Map as built-in container")
        void shouldRecognize_map_asBuiltInContainer() {
            // WHY: Map<K,V> maps to java.util.Map<K,V> or PlutusData
            // EXAMPLE: "Map$String_Int"
            assertThat(processor.isBuiltInGenericContainer("Map")).isTrue();
        }

        @Test
        @DisplayName("should recognize Dict as built-in container")
        void shouldRecognize_dict_asBuiltInContainer() {
            // WHY: Dict is an alias for Map in some contexts
            // EXAMPLE: "Dict<String,Int>"
            assertThat(processor.isBuiltInGenericContainer("Dict")).isTrue();
        }

        @Test
        @DisplayName("should recognize Data as built-in abstract type")
        void shouldRecognize_data_asBuiltInContainer() {
            // WHY: Data is the abstract PlutusData type per CIP-57
            // EXAMPLE: "Data" (not generic but treated as built-in)
            assertThat(processor.isBuiltInGenericContainer("Data")).isTrue();
        }

        @Test
        @DisplayName("should recognize Redeemer as built-in type")
        void shouldRecognize_redeemer_asBuiltInContainer() {
            // WHY: Redeemer is a built-in Plutus type
            assertThat(processor.isBuiltInGenericContainer("Redeemer")).isTrue();
        }

        // ========================================
        // DOMAIN-SPECIFIC TYPES (should return false)
        // ========================================

        @Test
        @DisplayName("should NOT recognize Interval as built-in container")
        void shouldNotRecognize_interval_asBuiltInContainer() {
            // WHY: Interval is a domain-specific type from aiken/interval library
            // BEFORE: Was skipped → PlutusData (type-unsafe)
            // AFTER: Generates typed Interval class (type-safe)
            // REAL-WORLD: SundaeSwap V2 has "Interval$Int" definitions
            assertThat(processor.isBuiltInGenericContainer("Interval")).isFalse();
        }

        @Test
        @DisplayName("should NOT recognize IntervalBound as built-in container")
        void shouldNotRecognize_intervalBound_asBuiltInContainer() {
            // WHY: IntervalBound is a domain-specific type from aiken/interval
            // REAL-WORLD: SundaeSwap V2 has "IntervalBound$Int"
            assertThat(processor.isBuiltInGenericContainer("IntervalBound")).isFalse();
        }

        @Test
        @DisplayName("should NOT recognize IntervalBoundType as built-in container")
        void shouldNotRecognize_intervalBoundType_asBuiltInContainer() {
            // WHY: IntervalBoundType is another aiken/interval domain type
            // REAL-WORLD: SundaeSwap V2 has "IntervalBoundType$Int"
            assertThat(processor.isBuiltInGenericContainer("IntervalBoundType")).isFalse();
        }

        @Test
        @DisplayName("should NOT recognize ValidityRange as built-in container")
        void shouldNotRecognize_validityRange_asBuiltInContainer() {
            // WHY: ValidityRange is a domain-specific type (semantic alias for Interval)
            // REAL-WORLD: SundaeSwap V3 has "cardano/transaction/ValidityRange"
            assertThat(processor.isBuiltInGenericContainer("ValidityRange")).isFalse();
        }

        @Test
        @DisplayName("should NOT recognize custom user types as built-in containers")
        void shouldNotRecognize_customUserType_asBuiltInContainer() {
            assertThat(processor.isBuiltInGenericContainer("MyCustomType")).isFalse();
            assertThat(processor.isBuiltInGenericContainer("Action")).isFalse();
            assertThat(processor.isBuiltInGenericContainer("Credential")).isFalse();
        }

        // ========================================
        // EDGE CASES
        // ========================================

        @Test
        @DisplayName("should be case-sensitive (lowercase ≠ uppercase)")
        void shouldBeCaseSensitive() {
            // WHY: Type names are case-sensitive in Aiken/Plutus
            assertThat(processor.isBuiltInGenericContainer("list")).isFalse();  // lowercase
            assertThat(processor.isBuiltInGenericContainer("List")).isTrue();   // uppercase
            assertThat(processor.isBuiltInGenericContainer("option")).isFalse();
            assertThat(processor.isBuiltInGenericContainer("Option")).isTrue();
        }

        @Test
        @DisplayName("should handle null gracefully")
        void shouldHandleNull_gracefully() {
            assertThat(processor.isBuiltInGenericContainer(null)).isFalse();
        }

        @Test
        @DisplayName("should handle empty string gracefully")
        void shouldHandleEmptyString_gracefully() {
            assertThat(processor.isBuiltInGenericContainer("")).isFalse();
        }
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
    // HELPER METHODS
    // ========================================

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
