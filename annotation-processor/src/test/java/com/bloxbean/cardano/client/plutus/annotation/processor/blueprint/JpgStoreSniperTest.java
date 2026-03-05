package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Tests for JpgStore Sniper blueprint annotation processing.
 *
 * <p>Verifies that the jpgstore/sniper-onchain blueprint (Aiken v1.1.21, Plutus V3)
 * compiles successfully <b>without</b> an explicit {@code @AikenStdlib} annotation,
 * testing that V3 is the implicit default.</p>
 */
class JpgStoreSniperTest {

    private static Compilation compilation;

    @BeforeAll
    static void setUp() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(JavaFileObjects.forResource("blueprint/JpgStoreSniper.java"));
    }

    @Test
    void compilationSucceeds() {
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
