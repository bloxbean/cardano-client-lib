package com.bloxbean.cardano.client.metadata;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class MetadataAnnotationProcessorTest {

    @Test
    void testCompile_sampleOrder() throws IOException {
        Compilation compilation = javac()
                .withProcessors(new MetadataAnnotationProcessor())
                .compile(JavaFileObjects.forResource("SampleOrder.java"));

        System.out.println("--- Diagnostics ---");
        compilation.diagnostics().forEach(System.out::println);

        System.out.println("--- Generated source files ---");
        compilation.generatedSourceFiles().forEach(javaFileObject -> {
            System.out.println(javaFileObject.getName());
            try {
                System.out.println(javaFileObject.getCharContent(true));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("com.test.SampleOrderMetadataConverter")
                .contentsAsUtf8String().contains("toMetadataMap");
        assertThat(compilation).generatedSourceFile("com.test.SampleOrderMetadataConverter")
                .contentsAsUtf8String().contains("fromMetadataMap");

    }

}
