package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class BlueprintCompileTest {

    @Test
    void nestedListMapCompile() {
        Compilation compilation =
                javac()
                        .withProcessors(new BlueprintAnnotationProcessor())
                        .compile(
                                JavaFileObjects.forResource("blueprint/BasicTypesBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/ComplexTypesBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/HelloWorldBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/HelloWorldNoNSBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/ListBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/MapBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/AnyPlutusDataBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/SpendMintBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/MultipleValidatorsBlueprint.java"),
                                JavaFileObjects.forResource("blueprint/ParameterizedValidatorBlueprint.java")
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
