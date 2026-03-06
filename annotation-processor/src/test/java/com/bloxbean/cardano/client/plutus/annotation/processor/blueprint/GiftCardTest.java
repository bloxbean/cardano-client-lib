package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.processor.ConstrAnnotationProcessor;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Gift Card (CIP-57) blueprint annotation processing.
 *
 * Verifies that the gift card contract blueprint compiles successfully
 * and generates valid Java classes for all validators and data types.
 */
public class GiftCardTest {

    private static Compilation compilation;

    @BeforeAll
    static void setUp() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
                .compile(JavaFileObjects.forResource("blueprint/GiftCard.java"));
    }

    @Test
    void giftCard() {
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

    @Nested
    @DisplayName("Inner class structure for interface types")
    class InnerClassStructureTests {

        @Test
        @DisplayName("Action interface should contain Mint and Burn as inner classes")
        void actionShouldContainInnerClasses() throws Exception {
            assertThat(compilation).succeeded();

            // Action is an anyOf > 1 interface with variants: Mint (1 field), Burn (0 fields)
            JavaFileObject actionFile = compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard.multi.model.Action")
                    .orElseThrow(() -> new AssertionError("Action.java not generated"));
            String actionSource = actionFile.getCharContent(true).toString();

            assertThat(actionSource)
                    .as("Action should be an interface")
                    .contains("public interface Action");

            // In Java interfaces, nested classes are implicitly public and static,
            // so JavaPoet omits those modifiers
            assertThat(actionSource)
                    .as("Action should contain Mint inner class")
                    .contains("abstract class Mint implements Data<Mint>, Action");
            assertThat(actionSource)
                    .as("Action should contain Burn inner class")
                    .contains("abstract class Burn implements Data<Burn>, Action");

            // Mint variant has a field, Burn does not
            assertThat(actionSource)
                    .as("Mint variant should have @Constr(alternative = 0)")
                    .contains("@Constr(\n      alternative = 0\n  )\n  abstract class Mint");
            assertThat(actionSource)
                    .as("Burn variant should have @Constr(alternative = 1)")
                    .contains("@Constr(\n      alternative = 1\n  )\n  abstract class Burn");
        }

        @Test
        @DisplayName("Action variants should NOT be generated as separate top-level classes")
        void actionVariantsShouldNotBeTopLevel() {
            assertThat(compilation).succeeded();

            // Verify Mint and Burn are NOT generated as separate files — they should only exist inside Action
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard.multi.model.Mint"))
                    .as("Mint should not be a separate top-level file")
                    .isEmpty();
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard.multi.model.Burn"))
                    .as("Burn should not be a separate top-level file")
                    .isEmpty();
        }
    }
}
