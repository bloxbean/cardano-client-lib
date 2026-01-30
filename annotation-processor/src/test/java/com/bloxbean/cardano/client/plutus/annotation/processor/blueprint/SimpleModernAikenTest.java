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
 * Tests basic modern Aiken naming conventions (v1.1.x+) with module paths.
 * Validates that the naming strategy correctly handles:
 * - Module paths with forward slashes (types/simple/Person)
 * - JSON Pointer escapes (types~1simple~1Person)
 * - Package generation from module paths
 */
@DisplayName("Simple Modern Aiken Test")
public class SimpleModernAikenTest {

    @Test
    @DisplayName("Should generate classes for modern Aiken types with module paths")
    public void shouldGenerateClassesForModernAikenTypes() {
        // Arrange
        JavaFileObject blueprintAnnotation = JavaFileObjects.forResource("SimpleModernAiken.java");

        // Act
        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(blueprintAnnotation);

        // Assert
        assertThat(compilation).succeeded();

        // Verify Person class was generated in correct package (tests module path handling)
        // The blueprint key "types/simple/Person" should become package "types.simple.model"
        assertThat(compilation)
                .generatedSourceFile("com.bloxbean.cardano.client.plutus.annotation.blueprint.simplemodernaikentest.types.simple.model.Person")
                .contentsAsUtf8String()
                .contains("class Person");

        assertThat(compilation)
                .generatedSourceFile("com.bloxbean.cardano.client.plutus.annotation.blueprint.simplemodernaikentest.types.simple.model.Person")
                .contentsAsUtf8String()
                .contains("package com.bloxbean.cardano.client.plutus.annotation.blueprint.simplemodernaikentest.types.simple.model");
    }

    @Test
    @DisplayName("Should handle JSON Pointer escapes in type references")
    public void shouldHandleJsonPointerEscapes() {
        // Arrange
        JavaFileObject blueprintAnnotation = JavaFileObjects.forResource("SimpleModernAiken.java");

        // Act
        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(blueprintAnnotation);

        // Assert - compilation should succeed despite JSON Pointer escapes (~1 for /)
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should generate valid Java identifiers from module paths")
    public void shouldGenerateValidJavaIdentifiersFromModulePaths() {
        // Arrange
        JavaFileObject blueprintAnnotation = JavaFileObjects.forResource("SimpleModernAiken.java");

        // Act
        Compilation compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(blueprintAnnotation);

        // Assert
        assertThat(compilation).succeeded();

        // succeeded() already verifies no compilation errors
    }
}
