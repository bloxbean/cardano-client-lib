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
 * Compile smoke for Map codegen against a synthetic V3-style blueprint
 * (modern angle-bracket syntax, stdlib v3+). A blueprint field with
 * {@code dataType: "map"} should generate a {@code Map<K, V>}-typed Java field,
 * and no {@code Map.java} class should be emitted (Map is a built-in container).
 */
class MapBlueprintCompileTest {

    @Test
    void mapFieldGeneratesAsMapTypedJava() throws Exception {
        Compilation compilation = Compiler.javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .withClasspathFrom(ClassLoader.getSystemClassLoader())
                .compile(JavaFileObjects.forResource("blueprint/MapBlueprint.java"));

        assertThat(compilation).succeeded();

        // No Map.java class generated — Map is a built-in container
        assertThat(compilation.generatedSourceFiles().stream().map(JavaFileObject::getName).toList())
                .noneMatch(name -> name.matches(".*/Map\\.java"));

        // MapDatum should be generated with a Map-typed field
        JavaFileObject datum = compilation.generatedSourceFile("com.test.map.model.MapDatum")
                .orElseThrow(() -> new AssertionError("MapDatum.java not generated"));
        String source = datum.getCharContent(true).toString();
        assertThat(source)
                .as("MapDatum should declare a Map-typed field")
                .contains("Map<");
    }
}
