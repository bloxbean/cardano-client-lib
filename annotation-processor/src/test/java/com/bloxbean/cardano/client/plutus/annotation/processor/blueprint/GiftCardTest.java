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
    @DisplayName("Sub-package variant structure for interface types")
    class SubPackageVariantTests {

        @Test
        @DisplayName("Action interface should have Mint and Burn in action sub-package")
        void actionVariantsInSubPackage() throws Exception {
            assertThat(compilation).succeeded();

            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard.multi.model";

            // Action is an anyOf > 1 interface with variants: Mint (1 field), Burn (0 fields)
            JavaFileObject actionFile = compilation.generatedSourceFile(basePkg + ".Action")
                    .orElseThrow(() -> new AssertionError("Action.java not generated"));
            String actionSource = actionFile.getCharContent(true).toString();

            assertThat(actionSource)
                    .as("Action should be an interface")
                    .contains("public interface Action");

            // Variants should be in action sub-package
            String variantPkg = basePkg + ".action";
            JavaFileObject mintFile = compilation.generatedSourceFile(variantPkg + ".Mint")
                    .orElseThrow(() -> new AssertionError("Mint.java not generated in action sub-package"));
            String mintSource = mintFile.getCharContent(true).toString();
            assertThat(mintSource)
                    .as("Mint should implement Data and Action")
                    .contains("abstract class Mint implements Data<Mint>, Action");
            assertThat(mintSource)
                    .as("Mint variant should have @Constr(alternative = 0)")
                    .contains("@Constr(\n    alternative = 0\n)\npublic abstract class Mint");

            JavaFileObject burnFile = compilation.generatedSourceFile(variantPkg + ".Burn")
                    .orElseThrow(() -> new AssertionError("Burn.java not generated in action sub-package"));
            String burnSource = burnFile.getCharContent(true).toString();
            assertThat(burnSource)
                    .as("Burn should implement Data and Action")
                    .contains("abstract class Burn implements Data<Burn>, Action");
            assertThat(burnSource)
                    .as("Burn variant should have @Constr(alternative = 1)")
                    .contains("@Constr(\n    alternative = 1\n)\npublic abstract class Burn");
        }

        @Test
        @DisplayName("Action variants should exist in action sub-package")
        void actionVariantsExistInSubPackage() {
            assertThat(compilation).succeeded();

            String variantPkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.giftcard.multi.model.action";

            assertThat(compilation.generatedSourceFile(variantPkg + ".Mint"))
                    .as("Mint should be in action sub-package")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(variantPkg + ".Burn"))
                    .as("Burn should be in action sub-package")
                    .isPresent();
        }
    }
}
