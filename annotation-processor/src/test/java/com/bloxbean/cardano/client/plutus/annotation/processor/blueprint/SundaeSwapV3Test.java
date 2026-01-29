package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for SundaeSwap DEX blueprint annotation processing (Plutus v3).
 *
 * Verifies that the SundaeSwap contract blueprint compiled with Aiken v1.1.21
 * targeting Plutus v3 compiles successfully and generates valid Java classes
 * for all validators (pool, order, oracle) and data types.
 */
public class SundaeSwapV3Test {

    @Test
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
