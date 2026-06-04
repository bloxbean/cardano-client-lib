package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Tests for handling blueprint schemas without "title" fields (CIP-57 compliance).
 *
 * <p>Per CIP-57, the "title" field is OPTIONAL for schema definitions. Validators must
 * have titles, but datum/redeemer/parameter schemas may omit them. When schema.getTitle()
 * is null, the processor falls back to the definition key (e.g., "types/custom/Data" →
 * "Data") for class name derivation. Without that fallback, code generation would NPE.</p>
 *
 * @see <a href="https://cips.cardano.org/cip/CIP-57">CIP-57 Plutus Contract Blueprints</a>
 */
class MissingTitleTest {

    /**
     * Tests that schemas without titles can be processed without NPE.
     *
     * <p>Blueprint contains:</p>
     * <ul>
     *   <li>Primitive type without title: "ByteArray" (dataType: "bytes")</li>
     *   <li>Concrete type without title: "types/custom/Data" — falls back to "Data"</li>
     *   <li>Concrete type WITH title: "types/custom/Action" — uses "Action"</li>
     * </ul>
     */
    @Test
    void shouldHandleSchemasWithoutTitles() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/MissingTitleBlueprint.java"));

        assertThat(compilation).succeeded();

        compilation.generatedSourceFile("com.test.missingtitle.types.custom.model.Data")
                .orElseThrow(() -> new AssertionError("Expected Data class to be generated"));
        compilation.generatedSourceFile("com.test.missingtitle.types.custom.model.Action")
                .orElseThrow(() -> new AssertionError("Expected Action class to be generated"));
    }

    /**
     * Tests that primitive types without titles (ByteArray, Int, Bool — dataType only) don't NPE
     * during processing.
     */
    @Test
    void shouldHandlePrimitiveTypesWithoutTitles() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/PrimitiveNoTitleBlueprint.java"));

        assertThat(compilation).succeeded();
    }
}
