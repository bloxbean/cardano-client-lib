package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

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
                .withProcessors(new BlueprintAnnotationProcessor())
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
    @DisplayName("Top-level variant structure for interface types")
    class TopLevelVariantTests {

        @Test
        @DisplayName("Action interface should have Mint and Burn as top-level classes")
        void actionVariantsAreTopLevel() throws Exception {
            assertThat(compilation).succeeded();

            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard.multi.model";

            // Action is an anyOf > 1 interface with variants: Mint (1 field), Burn (0 fields)
            JavaFileObject actionFile = compilation.generatedSourceFile(basePkg + ".Action")
                    .orElseThrow(() -> new AssertionError("Action.java not generated"));
            String actionSource = actionFile.getCharContent(true).toString();

            assertThat(actionSource)
                    .as("Action should be an interface")
                    .contains("public interface Action");

            // Variants should be separate top-level files with prefixed names
            JavaFileObject mintFile = compilation.generatedSourceFile(basePkg + ".ActionMint")
                    .orElseThrow(() -> new AssertionError("ActionMint.java not generated as top-level"));
            String mintSource = mintFile.getCharContent(true).toString();
            assertThat(mintSource)
                    .as("ActionMint should implement Data and Action")
                    .contains("abstract class ActionMint implements Data<ActionMint>, Action");
            assertThat(mintSource)
                    .as("ActionMint variant should have @Constr(alternative = 0)")
                    .contains("@Constr(\n    alternative = 0\n)\npublic abstract class ActionMint");

            JavaFileObject burnFile = compilation.generatedSourceFile(basePkg + ".ActionBurn")
                    .orElseThrow(() -> new AssertionError("ActionBurn.java not generated as top-level"));
            String burnSource = burnFile.getCharContent(true).toString();
            assertThat(burnSource)
                    .as("ActionBurn should implement Data and Action")
                    .contains("abstract class ActionBurn implements Data<ActionBurn>, Action");
            assertThat(burnSource)
                    .as("ActionBurn variant should have @Constr(alternative = 1)")
                    .contains("@Constr(\n    alternative = 1\n)\npublic abstract class ActionBurn");
        }

        @Test
        @DisplayName("Action variants should exist as prefixed top-level files")
        void actionVariantsExistAsPrefixedFiles() {
            assertThat(compilation).succeeded();

            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard.multi.model";

            assertThat(compilation.generatedSourceFile(basePkg + ".ActionMint"))
                    .as("ActionMint should be a top-level file")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(basePkg + ".ActionBurn"))
                    .as("ActionBurn should be a top-level file")
                    .isPresent();
        }
    }
}
