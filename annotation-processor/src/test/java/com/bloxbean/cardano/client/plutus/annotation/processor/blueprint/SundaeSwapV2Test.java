package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SundaeSwap DEX blueprint annotation processing (Plutus v2).
 *
 * <p>This blueprint was compiled with Aiken v1.0.26-alpha and uses <b>stdlib V1</b> schemas.
 * The {@code @AikenStdlib(V1)} annotation tells the processor to resolve matching schemas
 * against V1 registry entries, producing shared stdlib types instead of blueprint-specific ones.</p>
 *
 * <p><b>Current Issue:</b> The code generator incorrectly extracts variant titles instead of
 * using the actual type reference. For example:</p>
 * <ul>
 *   <li>Definition key: "cardano/transaction/ValidityRange" (title: "ValidityRange")</li>
 *   <li>Contains variant with title: "Interval"</li>
 *   <li>Correct: Generate as ValidityRange in package cardano.transaction.model</li>
 *   <li>Actual: Tries to find "Interval" in package aiken.interval.model (from variant's field refs)</li>
 * </ul>
 *
 * <p>This is a separate issue from the namespace extraction fix (commit 8305b8f2).
 * The namespace extraction now correctly uses base types, but there's a separate bug
 * in how type references are resolved during code generation.</p>
 */
public class SundaeSwapV2Test {

    private static Compilation compilation;

    @BeforeAll
    static void setUp() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .compile(JavaFileObjects.forResource("blueprint/SundaeSwapV2.java"));
    }

    @Test
    void sundaeSwap() {
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

    @Nested
    @DisplayName("V1 stdlib shared type resolution")
    class SharedTypeResolutionTests {

        @Test
        @DisplayName("Credential should be resolved as shared V1 stdlib type, not generated")
        void credentialResolvedAsSharedType() {
            assertThat(compilation).succeeded();

            // V1 Credential schema (VerificationKeyCredential/ScriptCredential with ByteArray refs)
            // matches the V1 registry entry → resolved as shared std.Credential
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap.aiken.transaction.credential.model.Credential"))
                    .as("Credential should be resolved as shared V1 stdlib type, not generated")
                    .isEmpty();
        }

        @Test
        @DisplayName("Address should be resolved as shared V1 stdlib type, not generated")
        void addressResolvedAsSharedType() {
            assertThat(compilation).succeeded();

            // V1 Address schema (refs Credential + Option$Referenced$Credential)
            // matches the V1 registry entry → resolved as shared std.Address
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap.aiken.transaction.credential.model.Address"))
                    .as("Address should be resolved as shared V1 stdlib type, not generated")
                    .isEmpty();
        }

        @Test
        @DisplayName("Referenced (StakeCredential) should be resolved as shared V1 stdlib type, not generated")
        void referencedCredentialResolvedAsSharedType() {
            assertThat(compilation).succeeded();

            // V1 Referenced$Credential schema (Inline refs Credential, Pointer with 3 Int fields)
            // matches the V1 registry entry → resolved as shared std.ReferencedCredential
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap.aiken.transaction.credential.model.Referenced"))
                    .as("Referenced (StakeCredential) should be resolved as shared V1 stdlib type, not generated")
                    .isEmpty();
        }

        @Test
        @DisplayName("OutputReference should be resolved as shared V1 stdlib type, not generated")
        void outputReferenceResolvedAsSharedType() {
            assertThat(compilation).succeeded();

            // V1 OutputReference schema (nested TransactionId wrapper + Int)
            // matches the V1 registry entry → resolved as shared std.OutputReferenceV1
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap.aiken.transaction.model.OutputReference"))
                    .as("OutputReference should be resolved as shared V1 stdlib type, not generated")
                    .isEmpty();
        }

        @Test
        @DisplayName("Shared stdlib converters should be generated")
        void sharedStdlibConvertersGenerated() {
            assertThat(compilation).succeeded();

            // When types are resolved as shared stdlib types, their converters are generated
            // in the std converter package
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.aiken.blueprint.std.converter.CredentialConverter"))
                    .as("CredentialConverter should be generated for shared V1 stdlib type")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.aiken.blueprint.std.converter.AddressConverter"))
                    .as("AddressConverter should be generated for shared V1 stdlib type")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.aiken.blueprint.std.converter.ReferencedCredentialConverter"))
                    .as("ReferencedCredentialConverter should be generated for shared V1 stdlib type")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.aiken.blueprint.std.converter.OutputReferenceV1Converter"))
                    .as("OutputReferenceV1Converter should be generated for shared V1 stdlib type")
                    .isPresent();
        }

        @Test
        @DisplayName("SundaeSwap-specific types should still be generated")
        void sundaeSwapSpecificTypesGenerated() {
            assertThat(compilation).succeeded();

            // Blueprint-specific types that don't match any stdlib schema should still be generated
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap.types.order.model.Order"))
                    .as("Order (SundaeSwap-specific) should still be generated")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswap.types.pool.model.PoolDatum"))
                    .as("PoolDatum (SundaeSwap-specific) should still be generated")
                    .isPresent();
        }
    }

    /**
     * Verifies that typed containers (Option, List) generate proper Java types instead of PlutusData.
     *
     * <p><b>Why this matters:</b> Before our fix, Option&lt;T&gt; fields would fall through to PlutusData
     * fallback because PlutusBlueprintLoader only checked "Option$" prefix (Aiken v1.0.26) and missed
     * "Option&lt;" syntax. This test verifies typed containers work correctly.</p>
     *
     * <p><b>Expected PlutusData usage (per CIP-57):</b></p>
     * <ul>
     *   <li><b>CORRECT:</b> Fields referencing #/definitions/Data (abstract "Any Plutus data" type)</li>
     *   <li><b>INCORRECT:</b> Option&lt;T&gt; → PlutusData (should be Optional&lt;T&gt;)</li>
     *   <li><b>INCORRECT:</b> List&lt;T&gt; → PlutusData (should be List&lt;T&gt;)</li>
     * </ul>
     *
     * <p><b>SundaeSwap V2 legitimate PlutusData fields (intentionally opaque per protocol design):</b></p>
     * <ul>
     *   <li>extension/extensions fields - arbitrary extensibility data</li>
     *   <li>InlineDatum.data - raw Plutus data in transaction format</li>
     *   <li>RedeemerWrapper.data - wrapped arbitrary redeemer data</li>
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

        // Filter out legitimate PlutusData fields:
        // - extension/extensions: arbitrary extensibility data per protocol design
        // - data: CIP-57 abstract Data type (InlineDatum.data, RedeemerWrapper.data)
        // Note: 3+ item tuples now generate ListPlutusData, so they won't match the PlutusData regex
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

        assertThat(illegitimateFields.size())
                .as("Generated sources should not have untyped PlutusData for containers. " +
                    "Option<T> should be Optional<T>, List<T> should be List<T>. " +
                    "PlutusData is ONLY allowed for: extension/extensions fields and 'data' fields " +
                    "(CIP-57 abstract 'Data' type). 3+ item tuples should be ListPlutusData.\n" +
                    "Found illegitimate fields: " + illegitimateFields)
                .isZero();
    }

}
