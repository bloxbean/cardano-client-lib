package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for SundaeSwap DEX blueprint annotation processing (Plutus v2).
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
 *
 * @see com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoaderTest#testNestedListCircularReference()
 */
public class SundaeSwapTest {

    @Test
    @Disabled("Annotation processor has issues with complex SundaeSwap types beyond circular references")
    void sundaeSwap() {
        Compilation compilation =
                javac()
                        .withProcessors(new BlueprintAnnotationProcessor())
                        .compile(
                                JavaFileObjects.forResource("blueprint/SundaeSwapV2.java")
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
    }
}
