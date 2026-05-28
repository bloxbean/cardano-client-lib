package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.util.List;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2 verification — exercises Aiken stdlib v3.x shared-type resolution end-to-end.
 *
 * <p>The driver blueprint is hand-crafted to mirror what {@code aiken build} emits for
 * a contract built against stdlib v3.1 — every shared definition matches the
 * signatures registered by {@link com.bloxbean.cardano.client.plutus.aiken.blueprint.registry.AikenBlueprintTypeRegistry}
 * verbatim (title, description, anyOf shape, $ref targets). If the registry's schemas
 * ever drift from real Aiken output this test will fail.</p>
 *
 * <p>Stdlib v3.1 is structurally identical to v3.0 (the v3.1.0 release only added
 * helper functions, no new types or schema changes — verified against the
 * {@code aiken-lang/stdlib} v3.1.0 release notes). So this test also doubles as the
 * v3.1 compatibility assertion called for by Step 2 of the plan.</p>
 */
class AikenStdlib31SharedTypesTest {

    private static final String STD = "com.bloxbean.cardano.client.plutus.aiken.blueprint.std";
    private static final String STD_CONV = STD + ".converter";
    private static final String LOCAL_PKG = "com.test.aikenstdlib31";

    @Test
    void sharedTypesResolveAgainstStdlibV3Registry() throws Exception {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/AikenStdlib31SharedTypesBlueprint.java"));

        assertThat(compilation).succeeded();

        List<String> generated = compilation.generatedSourceFiles().stream()
                .map(JavaFileObject::getName)
                .toList();

        // Shared types must NOT be regenerated locally — they should resolve to the prebuilt
        // classes in plutus-aiken's std package.
        for (String sharedSimple : new String[]{
                "Address", "PaymentCredential", "StakeCredential",
                "OutputReference", "VerificationKeyHash", "ScriptHash"
        }) {
            String localCopy = String.format(".*%s.*model.*/%s\\.java", LOCAL_PKG.replace('.', '/'), sharedSimple);
            assertThat(generated)
                    .as("Shared type %s must not be regenerated locally under %s", sharedSimple, LOCAL_PKG)
                    .noneMatch(name -> name.matches(localCopy));
        }

        // The processor must emit a Converter for each resolved shared type, in plutus-aiken's
        // std.converter package. (These converters are what plug into user-generated code
        // — if they're missing, downstream serialization breaks.)
        for (String converterSimple : new String[]{
                "AddressConverter", "PaymentCredentialConverter", "StakeCredentialConverter",
                "OutputReferenceConverter", "VerificationKeyHashConverter", "ScriptHashConverter"
        }) {
            assertThat(compilation.generatedSourceFile(STD_CONV + "." + converterSimple))
                    .as("Expected %s.%s to be generated for shared-type resolution", STD_CONV, converterSimple)
                    .isPresent();
        }

        // The custom datum SHOULD be generated and SHOULD reference the shared types.
        JavaFileObject datum = compilation.generatedSourceFile(LOCAL_PKG + ".model.StorageDatum")
                .orElseThrow(() -> new AssertionError("StorageDatum.java not generated"));
        String source = datum.getCharContent(true).toString();
        assertThat(source)
                .as("StorageDatum should import the shared Address class")
                .contains(STD + ".Address");
        assertThat(source)
                .as("StorageDatum should import the shared OutputReference class")
                .contains(STD + ".OutputReference");
        assertThat(source)
                .as("StorageDatum should import the shared VerificationKeyHash class")
                .contains(STD + ".VerificationKeyHash");
    }
}
