package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Functional test for circular nested list reference pattern (CIP-57).
 *
 * <p>Verifies that the annotation processor can generate valid Java classes
 * for blueprints with circular references via nested lists:</p>
 * <pre>Script → Composite → List&lt;Script&gt;</pre>
 *
 * <p>This pattern is common in:</p>
 * <ul>
 *   <li>Multisig scripts (AllOf/AnyOf/AtLeast containing lists of scripts)</li>
 *   <li>State machines (states containing lists of possible next states)</li>
 *   <li>Tree structures (nodes containing lists of child nodes)</li>
 * </ul>
 *
 * <p><strong>What This Tests:</strong></p>
 * <ul>
 *   <li>Blueprint loader handles circular references without StackOverflowError</li>
 *   <li>Code generation produces valid Java classes with self-referencing types</li>
 *   <li>Generated classes compile successfully</li>
 * </ul>
 */
public class CircularNestedListTest {

    @Test
    void circularNestedList() {
        Compilation compilation =
                javac()
                        .withProcessors(new BlueprintAnnotationProcessor())
                        .compile(
                                JavaFileObjects.forResource("blueprint/CircularNestedList.java")
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
