package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

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

    @Test
    void sundaeSwapV3() {
        Compilation compilation =
                javac()
                        .withProcessors(new BlueprintAnnotationProcessor())
                        .compile(
                                JavaFileObjects.forResource("blueprint/SundaeSwapV3.java")
                        );

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
                .collect(Collectors.toList());

        // Filter out legitimate PlutusData fields (extension/data fields per CIP-57 abstract Data type)
        List<String> illegitimateFields = plutusDataFields.stream()
                .filter(line -> !line.contains("extensions")
                        && !line.contains("extension")
                        && !line.contains(" data;"))
                .collect(Collectors.toList());

        if (!illegitimateFields.isEmpty()) {
            System.out.println("\n=== Found ILLEGITIMATE PlutusData fields (should be typed) ===");
            illegitimateFields.forEach(System.out::println);
            System.out.println("==============================================================\n");
        }

        org.assertj.core.api.Assertions.assertThat(illegitimateFields.size())
                .as("Generated sources should not have untyped PlutusData for containers. " +
                    "SundaeSwap V3 uses Option<T> syntax (Aiken v1.1.21+). " +
                    "Option<Credential> should be Optional<Credential>, not PlutusData. " +
                    "PlutusData is ONLY allowed for: extension/extensions fields, and 'data' fields " +
                    "(these reference CIP-57 abstract 'Data' type).\n" +
                    "Found illegitimate fields: " + illegitimateFields)
                .isZero();
    }

}
