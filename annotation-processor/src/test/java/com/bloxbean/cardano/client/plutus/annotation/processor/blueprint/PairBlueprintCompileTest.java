package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compile smoke for Pair codegen against a synthetic V3-style blueprint
 * (modern angle-bracket syntax, stdlib v3+). A blueprint field with
 * {@code dataType: "pair"} should generate a {@code Pair<L, R>}-typed Java field,
 * and no {@code Pair.java} class should be emitted (Pair is a built-in container).
 */
class PairBlueprintCompileTest {

    @Test
    void pairFieldGeneratesAsPairTypedJava() throws Exception {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/PairBlueprint.java"));

        assertThat(compilation).succeeded();

        // No Pair.java class generated — Pair is a built-in container
        assertThat(compilation.generatedSourceFiles().stream().map(JavaFileObject::getName).toList())
                .noneMatch(name -> name.matches(".*/Pair\\.java"));

        // PairDatum should be generated with a Pair-typed field
        JavaFileObject datum = compilation.generatedSourceFile("com.test.pair.model.PairDatum")
                .orElseThrow(() -> new AssertionError("PairDatum.java not generated"));
        String source = datum.getCharContent(true).toString();
        assertThat(source)
                .as("PairDatum should declare a Pair-typed field")
                .contains("Pair<");
    }
}
