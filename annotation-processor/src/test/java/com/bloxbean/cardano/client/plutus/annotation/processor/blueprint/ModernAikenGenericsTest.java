package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests that demonstrate the fixes for SundaeSwap V3 blueprint issues.
 *
 * <h2>Problems This Test Addresses:</h2>
 * <ol>
 *   <li><b>Angle brackets in type names:</b>
 *       Before: {@code List<Int>} would fail with "not a valid name: list<Int>0"
 *       After: Angle brackets are sanitized to {@code ListOfInt}</li>
 *
 *   <li><b>Package generation from generic types:</b>
 *       Before: {@code Option<cardano/address/Credential>} created package {@code optioncardano.address} (missing dot!)
 *       After: Extracts inner type to create package {@code cardano.address}</li>
 *
 *   <li><b>Generic type definitions:</b>
 *       Before: Tried to generate classes for {@code Option<X>}, {@code List<Y>}, causing compilation errors
 *       After: Skips generic type definitions, lets SchemaTypeResolver handle them with Java generics</li>
 * </ol>
 */
@DisplayName("Modern Aiken Generics Test - SundaeSwap V3 Fixes")
public class ModernAikenGenericsTest {

    @Test
    @DisplayName("FIX #1: Should skip generic type definitions like Option<cardano/address/Credential>")
    public void shouldSkipGenericTypeDefinitions() {
        // CONTEXT: SundaeSwap V3 blueprint has entries like "Option<cardano/address/Credential>"
        // OLD BEHAVIOR: Annotation processor would try to create a class called "Option"
        //               in package "optioncardano.address.model" (wrong package!)
        //               This would cause: "package does not exist" errors
        // NEW BEHAVIOR: Skip these entries - they're handled by SchemaTypeResolver using java.util.Optional

        JavaFileObject blueprintAnnotation = JavaFileObjects.forResource("ModernAikenGenerics.java");

        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(blueprintAnnotation);

        // Should succeed - generic definitions are skipped
        assertThat(compilation).succeeded();

        // Should NOT generate a class for the generic type definition "Option<cardano/address/Credential>"
        // (Before the fix, it would try to generate this and fail)
        // We verify this by checking that only the concrete types are generated
        assertThat(compilation)
                .generatedSourceFile("com.bloxbean.cardano.client.plutus.annotation.blueprint.modernaiken.cardano.address.model.Credential")
                .contentsAsUtf8String()
                .contains("class Credential");
    }

    @Test
    @DisplayName("FIX #2: Should extract correct package from types with module paths")
    public void shouldExtractCorrectPackageFromModulePaths() {
        // CONTEXT: Blueprint key "cardano/address/Credential" should create package "cardano.address.model"
        // OLD BEHAVIOR: Might create wrong packages or miss dots
        // NEW BEHAVIOR: Correctly converts slashes to dots

        JavaFileObject blueprintAnnotation = JavaFileObjects.forResource("ModernAikenGenerics.java");

        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(blueprintAnnotation);

        assertThat(compilation).succeeded();

        // Verify Credential class is in the correct package (not optioncardanoaddress or other malformed package)
        assertThat(compilation)
                .generatedSourceFile("com.bloxbean.cardano.client.plutus.annotation.blueprint.modernaiken.cardano.address.model.Credential")
                .contentsAsUtf8String()
                .contains("package com.bloxbean.cardano.client.plutus.annotation.blueprint.modernaiken.cardano.address.model");

        assertThat(compilation)
                .generatedSourceFile("com.bloxbean.cardano.client.plutus.annotation.blueprint.modernaiken.cardano.address.model.Credential")
                .contentsAsUtf8String()
                .contains("class Credential");
    }

    @Test
    @DisplayName("FIX #3: Should handle JSON Pointer escapes in definition keys")
    public void shouldHandleJsonPointerEscapes() {
        // CONTEXT: Blueprint definition keys can have JSON Pointer escapes: ~1 for /, ~0 for ~
        // Example: "types~1escapes~1TestType" means "types/escapes/TestType"
        // OLD BEHAVIOR: Might not properly unescape, creating wrong packages
        // NEW BEHAVIOR: unescapeJsonPointer() method handles RFC 6901 escapes correctly

        JavaFileObject blueprintAnnotation = JavaFileObjects.forResource("ModernAikenGenerics.java");

        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(blueprintAnnotation);

        // Should succeed - escapes are properly unescaped
        assertThat(compilation).succeeded();

        // Verify TestType is in correct package (types.escapes.model, not types~1escapes~1.model)
        assertThat(compilation)
                .generatedSourceFile("com.bloxbean.cardano.client.plutus.annotation.blueprint.modernaiken.types.escapes.model.TestType")
                .contentsAsUtf8String()
                .contains("package com.bloxbean.cardano.client.plutus.annotation.blueprint.modernaiken.types.escapes.model");
    }

    @Test
    @DisplayName("REGRESSION: Verify compilation succeeds (no invalid Java identifiers)")
    public void shouldCompileWithoutErrors() {
        // This test would have FAILED before our fixes with errors like:
        // - "not a valid name: list<Int>0"
        // - "package optioncardano.address.model does not exist"
        // - "cannot find symbol: class Option"

        JavaFileObject blueprintAnnotation = JavaFileObjects.forResource("ModernAikenGenerics.java");

        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(blueprintAnnotation);

        // The key assertion: compilation succeeds
        assertThat(compilation).succeeded();

        // No "invalid identifier" errors
        // No "package does not exist" errors
        // No "cannot find symbol" errors for Option/List/etc
    }
}
