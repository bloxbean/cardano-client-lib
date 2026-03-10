package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SundaeSwap DEX blueprint annotation processing (Plutus v3).
 *
 * NOTE: Currently disabled. While circular reference handling is fixed
 * (see PlutusBlueprintLoaderTest.testSundaeSwapMultisigCircular), there are
 * other annotation processor issues with complex SundaeSwap types that need
 * to be addressed separately.
 *
 * The blueprint loads successfully but code generation has issues with:
 * - Generic type extraction and naming
 * - Complex nested type hierarchies
 * - Module path resolution for namespaced types
 */
public class SundaeSwapV3Test {

    private static Compilation compilation;

    @BeforeAll
    static void setUp() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(JavaFileObjects.forResource("blueprint/SundaeSwapV3.java"));
    }

    @Test
    void sundaeSwapV3() {
        System.out.println(compilation.diagnostics());

        compilation.generatedFiles().forEach(javaFileObject -> {
            if (javaFileObject.getName().endsWith("class"))
                return;
            System.out.println(javaFileObject.getName());
            try {
                System.out.println(javaFileObject.getCharContent(true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(compilation).succeeded();

        // Verify no PlutusData fields in generated sources
        // This is critical for SundaeSwap V3 which uses Option<T> syntax (Aiken v1.1.21+)
        // Our fix ensures Option<Credential> generates Optional<Credential>, not PlutusData
        verifyNoOpaqueTypes(compilation);
    }

    @Nested
    @DisplayName("Sub-package variant structure for interface types")
    class SubPackageVariantTests {

        @Test
        @DisplayName("Credential should be resolved as shared stdlib type, not generated")
        void credentialResolvedAsSharedType() {
            assertThat(compilation).succeeded();

            // SundaeSwap V3 blueprint uses Aiken stdlib v3 Credential schema, which matches
            // the V3 entry in AikenBlueprintTypeRegistry. The processor correctly resolves it
            // as a shared stdlib type instead of generating a blueprint-specific class.
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3.cardano.address.model.Credential"))
                    .as("Credential should be resolved as shared stdlib type, not generated")
                    .isEmpty();
        }

        @Test
        @DisplayName("PaymentCredential should be resolved as shared stdlib type, not generated")
        void paymentCredentialResolvedAsSharedType() {
            assertThat(compilation).succeeded();

            // Same as Credential: the V3 PaymentCredential schema matches the registry entry,
            // so it's resolved as a shared stdlib type.
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3.cardano.address.model.PaymentCredential"))
                    .as("PaymentCredential should be resolved as shared stdlib type, not generated")
                    .isEmpty();
        }

        @Test
        @DisplayName("Order should be an interface with 6 variants in order sub-package")
        void orderVariantsInSubPackage() throws Exception {
            assertThat(compilation).succeeded();

            JavaFileObject file = compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3.types.order.model.Order")
                    .orElseThrow(() -> new AssertionError("Order.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("Order should be an interface")
                    .contains("public interface Order");

            // Variants should be in order sub-package
            String variantPkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3.types.order.model.order";
            for (String variant : new String[]{"Strategy", "Swap", "Deposit", "Withdrawal", "Donation", "Record"}) {
                JavaFileObject variantFile = compilation.generatedSourceFile(variantPkg + "." + variant)
                        .orElseThrow(() -> new AssertionError(variant + ".java not generated in order sub-package"));
                String variantSource = variantFile.getCharContent(true).toString();
                assertThat(variantSource)
                        .as(variant + " should implement Data and Order")
                        .contains("abstract class " + variant + " implements Data<" + variant + ">, Order");
            }
        }

        @Test
        @DisplayName("Credential variants should NOT be generated (shared stdlib type)")
        void credentialVariantsShouldNotBeGenerated() {
            assertThat(compilation).succeeded();

            // Credential is resolved as a shared stdlib type, so no variants are generated at all
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3.cardano.address.model.credential.VerificationKey"))
                    .as("credential/VerificationKey should not be generated (shared type)")
                    .isEmpty();
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3.cardano.address.model.credential.Script"))
                    .as("credential/Script should not be generated (shared type)")
                    .isEmpty();
        }
    }

    /**
     * Verifies that typed containers (Option, List) generate proper Java types instead of PlutusData.
     *
     * <p><b>Why this matters for SundaeSwap V3:</b> This blueprint uses Aiken v1.1.21+ syntax
     * with angle brackets (Option&lt;T&gt;) instead of dollar signs (Option$T). Before our fix,
     * PlutusBlueprintLoader only checked for Option$ prefix, causing Option&lt;T&gt; fields to
     * fall through to PlutusData fallback.</p>
     *
     * <p>This assertion verifies the fix works: Option&lt;Credential&gt; → Optional&lt;Credential&gt;.</p>
     *
     * <p><b>Legitimate PlutusData fields (per CIP-57 abstract Data type):</b></p>
     * <ul>
     *   <li>extension/extensions fields - arbitrary extensibility data</li>
     *   <li>data fields - wrapped Plutus data (InlineDatum, RedeemerWrapper, etc.)</li>
     * </ul>
     */
    private void verifyNoOpaqueTypes(Compilation compilation) {
        String allGeneratedSources = compilation.generatedFiles().stream()
                .filter(file -> file.getName().endsWith(".java"))
                .map(file -> {
                    try {
                        return file.getCharContent(true).toString();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining("\n"));

        // Find PlutusData fields
        List<String> plutusDataFields = allGeneratedSources.lines()
                .filter(line -> line.trim().matches("private\\s+PlutusData\\s+\\w+;"))
                .toList();

        // Filter out legitimate PlutusData fields:
        // - extension/extensions: arbitrary extensibility data per protocol design
        // - data: CIP-57 abstract Data type (InlineDatum.data, RedeemerWrapper.data)
        // Note: 3+ item tuples now generate ListPlutusData, so they won't match the PlutusData regex
        List<String> illegitimateFields = plutusDataFields.stream()
                .filter(line -> !line.contains("extensions")
                        && !line.contains("extension")
                        && !line.contains(" data;"))
                .toList();

        if (!illegitimateFields.isEmpty()) {
            System.out.println("\n=== Found ILLEGITIMATE PlutusData fields (should be typed) ===");
            illegitimateFields.forEach(System.out::println);
            System.out.println("==============================================================\n");
        }

        assertThat(illegitimateFields.size())
                .as("Generated sources should not have untyped PlutusData for containers. " +
                    "SundaeSwap V3 uses Option<T> syntax (Aiken v1.1.21+). " +
                    "Option<Credential> should be Optional<Credential>, not PlutusData. " +
                    "PlutusData is ONLY allowed for: extension/extensions fields and 'data' fields " +
                    "(CIP-57 abstract 'Data' type). 3+ item tuples should be ListPlutusData.\n" +
                    "Found illegitimate fields: " + illegitimateFields)
                .isZero();
    }

}
