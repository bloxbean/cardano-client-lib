package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for handling blueprint schemas without "title" fields (CIP-57 compliance).
 *
 * <p><b>CIP-57 Specification:</b></p>
 * <p>Per CIP-57, the "title" field is OPTIONAL for schema definitions.
 * Validators must have titles, but datum/redeemer/parameter schemas may omit them.
 * The spec states: "title's value must be a string. This keyword can be used to
 * decorate a user interface and qualify an instance with some short title."</p>
 *
 * <p><b>Real-World Scenarios:</b></p>
 * <p>Some production blueprints contain definitions without titles:</p>
 * <ul>
 *   <li>Primitive types: ByteArray, Int (dataType only, no title)</li>
 *   <li>Generic instantiations: List$ByteArray, List$Int, List$Tuple$..., etc.</li>
 *   <li>Concrete types may or may not have titles</li>
 * </ul>
 *
 * <p><b>The Issue:</b></p>
 * <p>Before fix: When schema.getTitle() returned null, JavaPoet's TypeSpec.classBuilder(null)
 * threw NullPointerException during class generation.</p>
 *
 * <p><b>The Solution:</b></p>
 * <ol>
 *   <li>Pass definition key (e.g., "types/custom/Data") as parameter to createDatumClass()</li>
 *   <li>Extract class name from definition key (e.g., "Data") as fallback when title is missing</li>
 *   <li>Set schema.title to extracted class name before creating TypeSpec</li>
 * </ol>
 *
 * <p><b>Note on Generic Instantiations:</b></p>
 * <p>Generic type instantiations (List$Int, Option&lt;ByteArray&gt;, etc.) without titles
 * should be SKIPPED by the generic type skip logic. These tests focus on primitive and
 * concrete types without titles that should generate classes.</p>
 *
 * @see <a href="https://cips.cardano.org/cip/CIP-57">CIP-57 Plutus Contract Blueprints</a>
 */
class MissingTitleTest {

    /**
     * Tests that schemas without titles can be processed without NPE.
     *
     * <p>This blueprint contains:</p>
     * <ul>
     *   <li>Primitive type without title: "ByteArray" (dataType: "bytes")</li>
     *   <li>Concrete type without title: "types/custom/Data"</li>
     *   <li>Concrete type WITH title: "types/custom/Action" (for comparison)</li>
     * </ul>
     *
     * <p><b>Expected Behavior:</b></p>
     * <ul>
     *   <li>✅ Compilation succeeds (no NPE)</li>
     *   <li>✅ Classes generated use definition key as name when title missing</li>
     *   <li>✅ Classes generated use title when present</li>
     * </ul>
     */
    @Test
    void shouldHandleSchemasWithoutTitles() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/MissingTitleBlueprint.java"));

        // CRITICAL: Must compile successfully (before fix: NullPointerException)
        assertThat(compilation).succeeded();

        // Verify classes were generated (using definition keys as fallback names)
        // Data class should exist (even though definition has no title, uses key as fallback)
        compilation.generatedSourceFile("com.test.missingtitle.types.custom.model.Data")
                .orElseThrow(() -> new AssertionError("Expected Data class to be generated"));

        // Action class should exist (has title in definition)
        compilation.generatedSourceFile("com.test.missingtitle.types.custom.model.Action")
                .orElseThrow(() -> new AssertionError("Expected Action class to be generated"));
    }

    /**
     * Tests that primitive types without titles are handled correctly.
     *
     * <p>Some blueprints define primitive types (ByteArray, Int) without titles,
     * only specifying dataType. These should ideally be mapped to Java primitives
     * rather than generating wrapper classes.</p>
     *
     * <p><b>Current Behavior:</b> The fix prevents NPE by using the definition key,
     * but may still attempt to generate classes for primitives (which likely fails
     * later in the pipeline or gets skipped by other logic).</p>
     *
     * <p><b>Future Improvement:</b> Add explicit check to skip primitive type
     * definitions (dataType: "bytes", "integer", "boolean") early in the pipeline.</p>
     */
    @Test
    void shouldHandlePrimitiveTypesWithoutTitles() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/PrimitiveNoTitleBlueprint.java"));

        // Should not throw NPE even when processing primitives without titles
        assertThat(compilation).succeeded();
    }

    /**
     * Tests handling of complex blueprints with mixed titled and untitled definitions.
     *
     * <p>This test validates CIP-57 compliance with blueprints containing:</p>
     * <ul>
     *   <li>Primitives without titles (ByteArray, Int) - should use definition key</li>
     *   <li>Generic instantiations without titles (List$ByteArray) - MUST BE SKIPPED per spec</li>
     *   <li>Concrete types with titles - should use provided title</li>
     * </ul>
     *
     * <p><b>Real-world context:</b> Some production blueprints (e.g., SundaeSwap V2) exposed
     * gaps in handling optional titles combined with generic type instantiations.</p>
     */
    @Test
    void shouldHandleMixedTitledAndUntitledDefinitionsWithGenerics() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/MixedTitlesWithGenerics.java"));

        // Must compile successfully - validates CIP-57 optional title handling
        assertThat(compilation).succeeded();

        // Verify generic instantiations without titles were SKIPPED (not generated)
        // List$ByteArray should NOT exist - dollar sign generics should be skipped
        assertThat(compilation.generatedSourceFile("com.test.mixedtitles.ListByteArray").isPresent())
                .isFalse();

        // Verify concrete types with titles WERE generated
        compilation.generatedSourceFile("com.test.mixedtitles.types.order.model.OrderDatum")
                .orElseThrow(() -> new AssertionError("Expected OrderDatum class to be generated"));
    }
}
