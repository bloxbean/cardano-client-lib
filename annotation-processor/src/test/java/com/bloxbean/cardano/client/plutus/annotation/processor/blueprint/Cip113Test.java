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

public class Cip113Test {

    private static Compilation compilation;

    @BeforeAll
    static void setUp() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(JavaFileObjects.forResource("blueprint/CIP113Token.java"));
    }

    @Test
    void cip113Token() {
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
        @DisplayName("GlobalStateSpendAction should be interface with variants as top-level classes")
        void globalStateSpendActionVariantsAreTopLevel() throws Exception {
            assertThat(compilation).succeeded();

            // GlobalStateSpendAction has 3 variants: MintSecurity, PauseTransfers, ModifySecurityInfo
            // Namespace: types/global_state → package segment: types.globalstate
            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.globalstate.model";

            JavaFileObject file = compilation.generatedSourceFile(basePkg + ".GlobalStateSpendAction")
                    .orElseThrow(() -> new AssertionError("GlobalStateSpendAction.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("GlobalStateSpendAction should be an interface")
                    .contains("public interface GlobalStateSpendAction");

            for (String variant : new String[]{"MintSecurity", "PauseTransfers", "ModifySecurityInfo"}) {
                JavaFileObject variantFile = compilation.generatedSourceFile(basePkg + ".GlobalStateSpendAction" + variant)
                        .orElseThrow(() -> new AssertionError("GlobalStateSpendAction" + variant + ".java not generated as top-level"));
                String variantSource = variantFile.getCharContent(true).toString();
                assertThat(variantSource)
                        .as("GlobalStateSpendAction" + variant + " should implement Data and GlobalStateSpendAction")
                        .contains("abstract class GlobalStateSpendAction" + variant + " implements Data<GlobalStateSpendAction" + variant + ">, GlobalStateSpendAction");
            }
        }

        @Test
        @DisplayName("MintRedeemer should be interface with variants as top-level classes")
        void mintRedeemerVariantsAreTopLevel() throws Exception {
            assertThat(compilation).succeeded();

            // power_users/MintRedeemer has 4 variants: Init, Deinit, AddPowerUser, RemovePowerUser
            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.powerusers.model";

            JavaFileObject file = compilation.generatedSourceFile(basePkg + ".MintRedeemer")
                    .orElseThrow(() -> new AssertionError("MintRedeemer.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("MintRedeemer should be an interface")
                    .contains("public interface MintRedeemer");

            for (String variant : new String[]{"Init", "Deinit"}) {
                JavaFileObject variantFile = compilation.generatedSourceFile(basePkg + ".MintRedeemer" + variant)
                        .orElseThrow(() -> new AssertionError("MintRedeemer" + variant + ".java not generated as top-level"));
                String variantSource = variantFile.getCharContent(true).toString();
                assertThat(variantSource)
                        .as("MintRedeemer" + variant + " should implement Data and MintRedeemer")
                        .contains("abstract class MintRedeemer" + variant + " implements Data<MintRedeemer" + variant + ">, MintRedeemer");
            }
        }

        @Test
        @DisplayName("MintRedeemer variants should be top-level with prefixed names")
        void mintRedeemerVariantsArePrefixed() {
            assertThat(compilation).succeeded();

            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.powerusers.model";

            // Variants should exist as prefixed top-level files
            assertThat(compilation.generatedSourceFile(basePkg + ".MintRedeemerInit"))
                    .as("MintRedeemerInit should be a top-level file")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(basePkg + ".MintRedeemerDeinit"))
                    .as("MintRedeemerDeinit should be a top-level file")
                    .isPresent();
        }
    }
}
