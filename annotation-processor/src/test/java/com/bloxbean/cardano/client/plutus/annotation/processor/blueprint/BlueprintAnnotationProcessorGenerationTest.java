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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class BlueprintAnnotationProcessorGenerationTest {

    @Test
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

    /**
     * Tests for generic type instantiation skip logic.
     * <p>
     * Verifies that blueprints with generic type instantiations (e.g., "Option&lt;Int&gt;", "List&lt;ByteArray&gt;")
     * compile successfully without errors. The skip logic (lines 110-114 in BlueprintAnnotationProcessor)
     * prevents these generic instantiations from being processed as new class definitions.
     * </p>
     */
    @Nested
    class GenericTypeSkipTests {

        @Test
        void shouldSuccessfullyProcessBlueprintWith_simpleGenericInstantiations() {
            // Blueprint contains: "Option<Int>", "Option<types/order/Action>", "List<ByteArray>"
            // These should be skipped (not cause compilation errors)
            JavaFileObject source = JavaFileObjects.forSourceString(
                    "com.test.GenericOptionTypes",
                    "package com.test;\n" +
                            "import com.bloxbean.cardano.client.plutus.annotation.Blueprint;\n" +
                            "@Blueprint(fileInResources = \"blueprint/generic-option-types_aiken_v1_1_21_42babe5.json\", packageName = \"com.test.genericoption\")\n" +
                            "public interface GenericOptionTypes { }\n");

            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(source);

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
        void shouldSuccessfullyProcessBlueprintWith_nestedGenericInstantiations() {
            // Blueprint contains: "List<Option<types/order/Action>>", "Tuple<<types/order/Action,types/order/Status>>"
            // These should be skipped
            JavaFileObject source = JavaFileObjects.forSourceString(
                    "com.test.GenericNestedTypes",
                    "package com.test;\n" +
                            "import com.bloxbean.cardano.client.plutus.annotation.Blueprint;\n" +
                            "@Blueprint(fileInResources = \"blueprint/generic-nested-types_aiken_v1_1_21_42babe5.json\", packageName = \"com.test.genericnested\")\n" +
                            "public interface GenericNestedTypes { }\n");

            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(source);

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
        void shouldSuccessfullyProcessBlueprintWith_cardanoBuiltinGenerics() {
            // Blueprint contains: "Option<cardano/address/Credential>", "List<cardano/transaction/OutputReference>"
            // Real-world pattern from SundaeSwap V3 - must compile successfully
            JavaFileObject source = JavaFileObjects.forSourceString(
                    "com.test.GenericCardanoBuiltins",
                    "package com.test;\n" +
                            "import com.bloxbean.cardano.client.plutus.annotation.Blueprint;\n" +
                            "@Blueprint(fileInResources = \"blueprint/generic-cardano-builtins_aiken_v1_1_21_42babe5.json\", packageName = \"com.test.genericcardano\")\n" +
                            "public interface GenericCardanoBuiltins { }\n");

            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(source);

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
            JavaFileObject source = JavaFileObjects.forSourceString(
                    "com.test.GenericDollarSignSyntax",
                    "package com.test;\n" +
                            "import com.bloxbean.cardano.client.plutus.annotation.Blueprint;\n" +
                            "@Blueprint(fileInResources = \"blueprint/generic-dollar-sign-syntax_aiken_v1_1_21_42babe5.json\", packageName = \"com.test.genericdollar\")\n" +
                            "public interface GenericDollarSignSyntax { }\n");

            Compilation compilation = Compiler.javac()
                    .withProcessors(new BlueprintAnnotationProcessor())
                    .withClasspathFrom(ClassLoader.getSystemClassLoader())
                    .compile(source);

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
}
