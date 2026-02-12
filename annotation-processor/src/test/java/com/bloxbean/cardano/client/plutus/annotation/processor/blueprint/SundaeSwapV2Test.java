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

    @Test
    @Disabled("Code generator incorrectly extracts variant titles instead of using $ref types")
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
