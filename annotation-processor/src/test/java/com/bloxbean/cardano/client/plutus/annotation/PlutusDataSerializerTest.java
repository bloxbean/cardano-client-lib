package com.bloxbean.cardano.client.plutus.annotation;

import com.bloxbean.cardano.client.plutus.annotation.processor.SerializerProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class PlutusDataSerializerTest {

    @Test
    void testCompile() {
        Compilation compilation =
                javac()
                        .withProcessors(new SerializerProcessor())
                        .compile(JavaFileObjects.forResource("Model1.java"),
                                JavaFileObjects.forResource("Model2.java"));

        System.out.println(compilation.diagnostics());
        assertThat(compilation).succeeded();
    }

}

