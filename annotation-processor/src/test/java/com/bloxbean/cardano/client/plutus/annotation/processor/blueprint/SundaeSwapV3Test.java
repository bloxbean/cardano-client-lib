package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SundaeSwap V3 blueprint processing, with focus on type alias handling.
 *
 * <p>SundaeSwap V3 (Aiken v1.1.21) defines {@code PaymentCredential} as a type alias
 * for {@code Credential} — both have identical VerificationKey/Script variants.
 * The processor should generate:</p>
 * <ul>
 *   <li>{@code interface Credential} — canonical interface</li>
 *   <li>{@code interface PaymentCredential extends Credential} — alias interface</li>
 *   <li>{@code class VerificationKey implements Credential, PaymentCredential} — variant</li>
 *   <li>{@code class Script implements Credential, PaymentCredential} — variant</li>
 *   <li>{@code PaymentCredentialConverter} — auto-generated converter</li>
 * </ul>
 */
@DisplayName("SundaeSwap V3 blueprint processing")
class SundaeSwapV3Test {

    private static final String ADDRESS_PKG = "com.bloxbean.cardano.client.plutus.annotation.blueprint.sundaeswapv3.cardano.address.model";

    private static Compilation compilation;

    @BeforeAll
    static void compileBlueprint() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .compile(JavaFileObjects.forResource("blueprint/SundaeSwapV3.java"));
    }

    @Test
    @DisplayName("should compile successfully")
    void shouldCompileSuccessfully() {
        assertThat(compilation).succeeded();
    }

    // ==========================================
    // TYPE ALIAS INTERFACE GENERATION
    // ==========================================

    @Nested
    @DisplayName("Alias interface generation")
    class AliasInterfaceTests {

        @Test
        @DisplayName("should generate PaymentCredential interface")
        void shouldGeneratePaymentCredentialInterface() {
            Optional<JavaFileObject> file = compilation.generatedSourceFile(ADDRESS_PKG + ".PaymentCredential");
            assertThat(file).isPresent();
        }

        @Test
        @DisplayName("PaymentCredential should extend Credential")
        void paymentCredentialShouldExtendCredential() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".PaymentCredential");
            assertThat(source)
                    .contains("public interface PaymentCredential extends Credential");
        }

        @Test
        @DisplayName("PaymentCredential should have @Constr annotation")
        void paymentCredentialShouldHaveConstrAnnotation() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".PaymentCredential");
            assertThat(source).contains("@Constr");
        }

        @Test
        @DisplayName("Credential interface should still be generated")
        void credentialInterfaceShouldBeGenerated() {
            Optional<JavaFileObject> file = compilation.generatedSourceFile(ADDRESS_PKG + ".Credential");
            assertThat(file).isPresent();
        }

        @Test
        @DisplayName("Credential should be a plain interface (not extending PaymentCredential)")
        void credentialShouldBePlainInterface() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".Credential");
            assertThat(source)
                    .contains("public interface Credential")
                    .doesNotContain("extends PaymentCredential");
        }
    }

    // ==========================================
    // VARIANT CLASSES IMPLEMENT ALIAS INTERFACES
    // ==========================================

    @Nested
    @DisplayName("Variant classes implement alias interfaces")
    class VariantImplementationTests {

        @Test
        @DisplayName("VerificationKey should implement both Credential and PaymentCredential")
        void verificationKeyShouldImplementBoth() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".VerificationKey");
            assertThat(source)
                    .contains("implements Data<VerificationKey>, Credential, PaymentCredential");
        }

        @Test
        @DisplayName("Script should implement both Credential and PaymentCredential")
        void scriptShouldImplementBoth() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".Script");
            assertThat(source)
                    .contains("implements Data<Script>, Credential, PaymentCredential");
        }

        @Test
        @DisplayName("VerificationKey should be abstract class with @Constr(alternative = 0)")
        void verificationKeyShouldBeAbstractWithCorrectAlternative() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".VerificationKey");
            assertThat(source)
                    .contains("public abstract class VerificationKey")
                    .contains("alternative = 0");
        }

        @Test
        @DisplayName("Script should be abstract class with @Constr(alternative = 1)")
        void scriptShouldBeAbstractWithCorrectAlternative() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".Script");
            assertThat(source)
                    .contains("public abstract class Script")
                    .contains("alternative = 1");
        }
    }

    // ==========================================
    // CONVERTER AUTO-GENERATION
    // ==========================================

    @Nested
    @DisplayName("Converter generation for alias types")
    class ConverterTests {

        @Test
        @DisplayName("PaymentCredentialConverter should be auto-generated")
        void paymentCredentialConverterShouldBeGenerated() {
            Optional<JavaFileObject> file = compilation.generatedSourceFile(
                    ADDRESS_PKG + ".converter.PaymentCredentialConverter");
            assertThat(file).isPresent();
        }

        @Test
        @DisplayName("CredentialConverter should also be generated")
        void credentialConverterShouldBeGenerated() {
            Optional<JavaFileObject> file = compilation.generatedSourceFile(
                    ADDRESS_PKG + ".converter.CredentialConverter");
            assertThat(file).isPresent();
        }

        @Test
        @DisplayName("PaymentCredentialConverter should accept PaymentCredential type")
        void converterShouldAcceptPaymentCredentialType() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".converter.PaymentCredentialConverter");
            assertThat(source)
                    .contains("toPlutusData(PaymentCredential paymentCredential)")
                    .contains("PaymentCredential fromPlutusData(");
        }

        @Test
        @DisplayName("PaymentCredentialConverter should dispatch to VerificationKey and Script")
        void converterShouldDispatchToVariants() throws IOException {
            String source = readGeneratedSource(ADDRESS_PKG + ".converter.PaymentCredentialConverter");
            assertThat(source)
                    .contains("instanceof VerificationKey")
                    .contains("instanceof Script");
        }
    }

    // ==========================================
    // $REF PRESERVATION (alias refs NOT rewritten)
    // ==========================================

    @Nested
    @DisplayName("Field types preserve alias references")
    class RefPreservationTests {

        @Test
        @DisplayName("Address should reference PaymentCredential type (not rewritten to Credential)")
        void addressShouldReferencePaymentCredentialType() throws IOException {
            // The Address schema has a payment_credential field with $ref to PaymentCredential.
            // Previously, this was rewritten to Credential. Now it should stay as PaymentCredential.
            String source = readGeneratedSource(ADDRESS_PKG + ".Address");
            assertThat(source)
                    .as("Address.paymentCredential should be typed as PaymentCredential, not Credential")
                    .contains("PaymentCredential paymentCredential");
        }
    }

    // ==========================================
    // NO OPAQUE PLUTUSDATA FIELDS
    // ==========================================

    @Nested
    @DisplayName("No illegitimate opaque PlutusData fields")
    class NoOpaqueFieldsTests {

        @Test
        @DisplayName("should not have untyped PlutusData for containers")
        void shouldNotHaveIllegitimatePlutusDataFields() {
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
            List<String> illegitimateFields = plutusDataFields.stream()
                    .filter(line -> !line.contains("extensions")
                            && !line.contains("extension")
                            && !line.contains(" data;"))
                    .collect(Collectors.toList());

            assertThat(illegitimateFields)
                    .as("Generated sources should not have untyped PlutusData for containers. "
                            + "SundaeSwap V3 uses Option<T> syntax (Aiken v1.1.21+). "
                            + "Option<Credential> should be Optional<Credential>, not PlutusData.")
                    .isEmpty();
        }
    }

    // ========================================
    // HELPERS
    // ========================================

    private static String readGeneratedSource(String qualifiedName) throws IOException {
        JavaFileObject file = compilation.generatedSourceFile(qualifiedName)
                .orElseThrow(() -> new AssertionError("Generated source not found: " + qualifiedName));
        return file.getCharContent(true).toString();
    }

    /**
     * Custom AssertionError to avoid confusing with assert keyword.
     */
    private static class AssertionError extends RuntimeException {
        AssertionError(String message) {
            super(message);
        }
    }
}
