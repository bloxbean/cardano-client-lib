package com.bloxbean.cardano.client.plutus.annotation;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class ConstrAnnotationProcessorTest {

    @Test
    void testCompile() throws Exception {
        Compilation compilation =
                javac()
                        .withProcessors(new ConstrAnnotationProcessor())
                        .compile(JavaFileObjects.forResource("Model1.java"),
                                JavaFileObjects.forResource("Model2.java"));


        compilation.generatedSourceFiles().forEach(javaFileObject -> {
            try {
                System.out.println(javaFileObject.getCharContent(true)); // Prints the generated source
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        compilation.generatedFiles().forEach(javaFileObject -> {
            if (javaFileObject.getName().endsWith("class"))
                return;;
            System.out.println(javaFileObject.getName());
            try {
                System.out.println(javaFileObject.getCharContent(true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        assertThat(compilation).succeeded();
    }

    @Test
    void testCompile2() throws Exception {
        Compilation compilation =
                javac()
                        .withProcessors(new ConstrAnnotationProcessor())
                        .compile(JavaFileObjects.forResource("TestData.java"),
                                JavaFileObjects.forResource("AnotherData.java"));

        System.out.println(compilation.diagnostics());
        compilation.generatedFiles().forEach(javaFileObject -> {
            if (javaFileObject.getName().endsWith("class"))
                return;;
            System.out.println(javaFileObject.getName());
            try {
                System.out.println(javaFileObject.getCharContent(true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        assertThat(compilation).succeeded();
    }

    @Test
    @Disabled
    void testCompile_netsedClass_optional() {
        Compilation compilation =
                javac()
                        .withProcessors(new ConstrAnnotationProcessor())
                        .compile(JavaFileObjects.forResource("A.java"));

        System.out.println(compilation.diagnostics());
        compilation.generatedFiles().forEach(javaFileObject -> {
            if (javaFileObject.getName().endsWith("class"))
                return;;
            System.out.println(javaFileObject.getName());
            try {
                System.out.println(javaFileObject.getCharContent(true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        assertThat(compilation).succeeded();
    }

    @Test
    void listOfListCompile() {
        Compilation compilation =
                javac()
                        .withProcessors(new ConstrAnnotationProcessor())
                        .compile(JavaFileObjects.forResource("NestedListModel.java"));

        System.out.println(compilation.diagnostics());
        compilation.generatedFiles().forEach(javaFileObject -> {
            if (javaFileObject.getName().endsWith("class"))
                return;;
            System.out.println(javaFileObject.getName());
            try {
                System.out.println(javaFileObject.getCharContent(true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        assertThat(compilation).succeeded();
    }

    @Test
    void mapOfMapCompile() throws Exception {
        Compilation compilation =
                javac()
                        .withProcessors(new ConstrAnnotationProcessor())
                        .compile(JavaFileObjects.forResource("NestedMapModel.java"));

        System.out.println(compilation.diagnostics());
        compilation.generatedFiles().forEach(javaFileObject -> {
            if (javaFileObject.getName().endsWith("class"))
                return;;
            System.out.println(javaFileObject.getName());
            try {
                System.out.println(javaFileObject.getCharContent(true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        assertThat(compilation).succeeded();
    }

    @Test
    void nestedListMapCompile() throws Exception {
        Compilation compilation =
                javac()
                        .withProcessors(new ConstrAnnotationProcessor())
                        .compile(JavaFileObjects.forResource("NestedListMapModel.java"));

        System.out.println(compilation.diagnostics());
        compilation.generatedFiles().forEach(javaFileObject -> {
            if (javaFileObject.getName().endsWith("class"))
                return;;
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

