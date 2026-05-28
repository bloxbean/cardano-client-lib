package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.util.List;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BlueprintAnnotationProcessor}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>Generic type handling: distinguishes built-in containers (List, Option) from
 *       domain-specific types (Interval, IntervalBound)</li>
 *   <li>Definition key resolution: extracts base types from angle-bracket generic instantiations
 *       while preserving namespace paths</li>
 *   <li>Code generation: validator and datum classes are generated correctly from kept fixtures</li>
 * </ul>
 */
class BlueprintAnnotationProcessorTest {

    private BlueprintAnnotationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BlueprintAnnotationProcessor();
    }

    /**
     * Tests for {@link BlueprintAnnotationProcessor#resolveDefinitionKeyForClassGeneration(String)}.
     *
     * <p>Distinguishes:</p>
     * <ul>
     *   <li>Built-in containers (List, Option, Tuple, Pair, Map, Dict, Data, Redeemer) → {@code null}</li>
     *   <li>Domain-specific generics (Interval, IntervalBound) → base type for typed class</li>
     *   <li>Non-generic types → as-is</li>
     * </ul>
     */
    @Nested
    @DisplayName("resolveDefinitionKeyForClassGeneration() tests")
    class ResolveDefinitionKeyForClassGenerationTests {

        @Nested
        @DisplayName("Built-in containers should return null")
        class BuiltInContainerTests {

            @Test
            @DisplayName("List<Int> → null")
            void listOfInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("List<Int>")).isNull();
            }

            @Test
            @DisplayName("Option<Credential> → null")
            void optionOfCredential() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Option<Credential>")).isNull();
            }

            @Test
            @DisplayName("Option<types/order/Action> → null")
            void optionOfPathedType() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Option<types/order/Action>")).isNull();
            }

            @Test
            @DisplayName("Tuple<Int,String> → null")
            void tupleOfTwo() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Tuple<Int,String>")).isNull();
            }

            @Test
            @DisplayName("Pair<Int,String> → null")
            void pairOfTwo() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Pair<Int,String>")).isNull();
            }

            @Test
            @DisplayName("Abstract Data type → \"Data\" (kept; no generics to strip)")
            void abstractData() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Data")).isEqualTo("Data");
            }

            @Test
            @DisplayName("Nested generics: List<Option<Int>> → null")
            void nestedGenerics() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("List<Option<Int>>")).isNull();
            }
        }

        @Nested
        @DisplayName("Domain-specific generics should return base type")
        class DomainSpecificGenericTests {

            @Test
            @DisplayName("Interval<Int> → Interval")
            void intervalOfInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Interval<Int>"))
                        .isEqualTo("Interval");
            }

            @Test
            @DisplayName("IntervalBound<Int> → IntervalBound")
            void intervalBoundOfInt() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("IntervalBound<Int>"))
                        .isEqualTo("IntervalBound");
            }

            @Test
            @DisplayName("aiken/interval/IntervalBound<Int> → aiken/interval/IntervalBound (namespace preserved)")
            void namespacedGeneric() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("aiken/interval/IntervalBound<Int>"))
                        .isEqualTo("aiken/interval/IntervalBound");
            }

            @Test
            @DisplayName("Custom domain type: custom/types/Container<String> → custom/types/Container")
            void customDomainType() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("custom/types/Container<String>"))
                        .isEqualTo("custom/types/Container");
            }
        }

        @Nested
        @DisplayName("Non-generic types should return as-is")
        class NonGenericTypeTests {

            @Test
            @DisplayName("Simple type: ValidityRange → ValidityRange")
            void validityRange() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("ValidityRange"))
                        .isEqualTo("ValidityRange");
            }

            @Test
            @DisplayName("With namespace: types/order/Action → types/order/Action")
            void typeWithNamespace() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("types/order/Action"))
                        .isEqualTo("types/order/Action");
            }

            @Test
            @DisplayName("cardano/transaction/ValidityRange (semantic alias) → cardano/transaction/ValidityRange")
            void cardanoType() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("cardano/transaction/ValidityRange"))
                        .isEqualTo("cardano/transaction/ValidityRange");
            }

            @Test
            @DisplayName("Cardano type: cardano/address/Credential → cardano/address/Credential")
            void cardanoCredential() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("cardano/address/Credential"))
                        .isEqualTo("cardano/address/Credential");
            }

            @Test
            @DisplayName("Custom type: MyCustomType → MyCustomType")
            void customType() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("MyCustomType"))
                        .isEqualTo("MyCustomType");
            }
        }

        @Nested
        @DisplayName("Edge cases")
        class EdgeCaseTests {

            @Test
            @DisplayName("Empty string → empty string")
            void shouldHandleEmptyString() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("")).isEqualTo("");
            }

            @Test
            @DisplayName("Multiple angle brackets: Foo<Bar<Baz>> → Foo")
            void shouldHandleNestedAngleBrackets() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Foo<Bar<Baz>>"))
                        .isEqualTo("Foo");
            }

            @Test
            @DisplayName("Trailing <: Foo< → Foo")
            void shouldHandleTrailingAngleBracket() {
                assertThat(processor.resolveDefinitionKeyForClassGeneration("Foo<"))
                        .isEqualTo("Foo");
            }
        }
    }

    /**
     * Integration tests that compile real blueprint fixtures with generic instantiations to
     * confirm built-in containers are skipped (no {@code List.java}/{@code Option.java} generated).
     */
    @Nested
    @DisplayName("Generic type skip integration tests")
    class GenericTypeSkipTests {

        @Test
        @DisplayName("blueprint with simple generic instantiations compiles")
        void simpleGenericInstantiations() {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/GenericOptionTypes.java"));

            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(JavaFileObject::getName)
                    .toList();

            assertThat(generatedSources).isNotEmpty();
            assertThat(generatedSources)
                    .noneMatch(name -> name.matches(".*/Option\\.java") || name.matches(".*/List\\.java"));
        }

        @Test
        @DisplayName("blueprint with nested generic instantiations compiles")
        void nestedGenericInstantiations() {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/GenericNestedTypes.java"));

            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(JavaFileObject::getName)
                    .toList();

            assertThat(generatedSources).isNotEmpty();
            assertThat(generatedSources)
                    .noneMatch(name -> name.matches(".*/List\\.java") ||
                                       name.matches(".*/Option\\.java") ||
                                       name.matches(".*/Tuple\\.java"));
        }

        @Test
        @DisplayName("blueprint with Cardano built-in generics compiles")
        void cardanoBuiltinGenerics() {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/GenericCardanoBuiltins.java"));

            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(JavaFileObject::getName)
                    .toList();

            assertThat(generatedSources).isNotEmpty();
            assertThat(generatedSources)
                    .noneMatch(name -> name.matches(".*/Option\\.java") || name.matches(".*/List\\.java"));
        }
    }

    /**
     * Compilation tests for interface variant sub-package generation.
     *
     * <p>Verifies that when a blueprint defines interface types (anyOf > 1) like Credential and
     * PaymentCredential, each generates its variants in a sub-package named after the interface.</p>
     */
    @Nested
    @DisplayName("Interface variant sub-package compilation tests")
    class InterfaceVariantSubpackageCompilationTests {

        @Test
        @DisplayName("compiles with variants in sub-packages")
        void shouldCompileWithVariantsInSubPackages() {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/InterfaceVariantSubpackageTest.java"));

            assertThat(compilation).succeeded();
        }

        @Test
        @DisplayName("Credential variants in credential sub-package")
        void credentialVariantsShouldBeInSubPackage() throws Exception {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/InterfaceVariantSubpackageTest.java"));

            assertThat(compilation).succeeded();

            JavaFileObject credFile = compilation.generatedSourceFile("com.test.variantsubpackage.test.model.Credential")
                    .orElseThrow(() -> new AssertionError("Credential.java not generated"));
            assertThat(credFile.getCharContent(true).toString())
                    .contains("public interface Credential");

            JavaFileObject vkFile = compilation.generatedSourceFile("com.test.variantsubpackage.test.model.credential.VerificationKey")
                    .orElseThrow(() -> new AssertionError("credential/VerificationKey.java not generated"));
            assertThat(vkFile.getCharContent(true).toString()).contains("abstract class VerificationKey");

            JavaFileObject scriptFile = compilation.generatedSourceFile("com.test.variantsubpackage.test.model.credential.Script")
                    .orElseThrow(() -> new AssertionError("credential/Script.java not generated"));
            assertThat(scriptFile.getCharContent(true).toString()).contains("abstract class Script");
        }

        @Test
        @DisplayName("PaymentCredential variants in paymentcredential sub-package")
        void paymentCredentialVariantsShouldBeInSubPackage() throws Exception {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/InterfaceVariantSubpackageTest.java"));

            assertThat(compilation).succeeded();

            JavaFileObject pcFile = compilation.generatedSourceFile("com.test.variantsubpackage.test.model.PaymentCredential")
                    .orElseThrow(() -> new AssertionError("PaymentCredential.java not generated"));
            assertThat(pcFile.getCharContent(true).toString())
                    .contains("public interface PaymentCredential");

            JavaFileObject vkFile = compilation.generatedSourceFile("com.test.variantsubpackage.test.model.paymentcredential.VerificationKey")
                    .orElseThrow(() -> new AssertionError("paymentcredential/VerificationKey.java not generated"));
            assertThat(vkFile.getCharContent(true).toString()).contains("abstract class VerificationKey");

            JavaFileObject scriptFile = compilation.generatedSourceFile("com.test.variantsubpackage.test.model.paymentcredential.Script")
                    .orElseThrow(() -> new AssertionError("paymentcredential/Script.java not generated"));
            assertThat(scriptFile.getCharContent(true).toString()).contains("abstract class Script");
        }

        @Test
        @DisplayName("converters land in correct packages")
        void convertersShouldBeInCorrectPackages() {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/InterfaceVariantSubpackageTest.java"));

            assertThat(compilation).succeeded();

            List<String> generatedSources = compilation.generatedSourceFiles().stream()
                    .map(JavaFileObject::getName)
                    .toList();

            assertThat(generatedSources).anyMatch(name -> name.contains("CredentialConverter"));
            assertThat(generatedSources).anyMatch(name -> name.contains("PaymentCredentialConverter"));
            assertThat(generatedSources)
                    .anyMatch(name -> name.contains("/credential/converter/VerificationKeyConverter"));
            assertThat(generatedSources)
                    .anyMatch(name -> name.contains("/paymentcredential/converter/VerificationKeyConverter"));
        }

        @Test
        @DisplayName("Address type using both Credential and PaymentCredential refs compiles")
        void addressTypeUsingBothRefsShouldCompile() throws Exception {
            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(JavaFileObjects.forResource("blueprint/InterfaceVariantSubpackageTest.java"));

            assertThat(compilation).succeeded();

            JavaFileObject addressFile = compilation.generatedSourceFile("com.test.variantsubpackage.test.model.Address")
                    .orElseThrow(() -> new AssertionError("Address.java not generated"));
            assertThat(addressFile.getCharContent(true).toString())
                    .contains("package com.test.variantsubpackage.test.model;");
        }
    }
}
