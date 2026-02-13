package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for UVerify contract blueprint annotation processing (Plutus v3).
 *
 * <p>This test verifies that the UVerify blueprint (Aiken v1.1.21+42babe5) compiles
 * successfully and generates validator classes for connected goods and social hub
 * functionality.</p>
 *
 * <p><b>Validators tested:</b></p>
 * <ul>
 *   <li>extensions/connected_goods/connected_goods.connected_goods.mint</li>
 *   <li>extensions/connected_goods/connected_goods.connected_goods.spend</li>
 *   <li>extensions/connected_goods/social_hub.social_hub.mint</li>
 *   <li>extensions/connected_goods/social_hub.social_hub.spend</li>
 *   <li>library.library.spend</li>
 * </ul>
 */
public class UVerifyTest {

    @Test
    void uverify() {
        Compilation compilation =
                javac()
                        .withProcessors(new BlueprintAnnotationProcessor())
                        .compile(
                                JavaFileObjects.forResource("blueprint/UVerify.java")
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
        // PlutusData should only be used when explicitly specified in CIP-57 schema as "Data" type
        // All other types (Option, List, domain-specific types) should be properly typed
        verifyNoOpaqueTypes(compilation);
    }

    /**
     * Verifies that typed containers (Option, List) generate proper Java types instead of PlutusData.
     *
     * <p><b>Why this matters:</b> UVerify uses Aiken v1.1.21+ which may have Option&lt;T&gt; syntax.
     * Our fix ensures these generate Optional&lt;T&gt;, not PlutusData.</p>
     *
     * <p><b>Expected PlutusData usage (per CIP-57):</b></p>
     * <ul>
     *   <li><b>CORRECT:</b> Fields referencing #/definitions/Data (abstract "Any Plutus data" type)</li>
     *   <li><b>INCORRECT:</b> Option&lt;T&gt; → PlutusData (should be Optional&lt;T&gt;)</li>
     *   <li><b>INCORRECT:</b> List&lt;T&gt; → PlutusData (should be List&lt;T&gt;)</li>
     * </ul>
     *
     * <p><b>UVerify legitimate PlutusData fields (intentionally opaque per protocol design):</b></p>
     * <ul>
     *   <li>extension/extensions fields - arbitrary extensibility data</li>
     *   <li>data fields - raw Plutus data in transaction format</li>
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
                    "Option<T> should be Optional<T>, List<T> should be List<T>. " +
                    "PlutusData is ONLY allowed for: extension/extensions fields, and 'data' fields " +
                    "(these reference CIP-57 abstract 'Data' type).\n" +
                    "Found illegitimate fields: " + illegitimateFields)
                .isZero();
    }
}
