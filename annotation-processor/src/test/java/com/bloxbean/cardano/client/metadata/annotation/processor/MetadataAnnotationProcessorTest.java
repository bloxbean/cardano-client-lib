package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        String src = compilation.generatedSourceFiles().stream()
                .filter(f -> f.getName().contains("SampleOrderMetadataConverter"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SampleOrderMetadataConverter was not generated"))
                .getCharContent(true)
                .toString();

        // basic structure
        assertTrue(src.contains("toMetadataMap"));
        assertTrue(src.contains("fromMetadataMap"));

        // long-string round-trip: MetadataList chunk handling
        assertTrue(src.contains("MetadataList"));
        assertTrue(src.contains("_sb.toString()"));

        // as = STRING: Integer statusCode stored/read as String
        assertTrue(src.contains("String.valueOf(sampleOrder.getStatusCode())"));
        assertTrue(src.contains("Integer.parseInt((String) v)"));

        // as = STRING_HEX: payloadBytes stored/read as hex
        assertTrue(src.contains("HexUtil.encodeHexString(sampleOrder.getPayloadBytes())"));
        assertTrue(src.contains("HexUtil.decodeHexString((String) v)"));

        // as = STRING_BASE64: signatureBytes stored/read as Base64
        assertTrue(src.contains("Base64.getEncoder().encodeToString(sampleOrder.getSignatureBytes())"));
        assertTrue(src.contains("Base64.getDecoder().decode((String) v)"));
    }
}
