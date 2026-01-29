package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for SundaeSwap DEX blueprint annotation processing (Plutus v3).
 *
 * NOTE: Currently disabled due to StackOverflowError in PlutusBlueprintLoader when
 * resolving circular type references. This exposes a bug in the blueprint loader's
 * circular reference handling that needs to be fixed.
 *
 * The v3 blueprint compiled with Aiken v1.1.21 still has complex circular type
 * definitions that the loader cannot handle properly.
 */
public class SundaeSwapV3Test {

    @Test
    @Disabled("StackOverflowError in PlutusBlueprintLoader due to circular type references in v3 blueprint")
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
    }
}
