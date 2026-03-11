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
    @DisplayName("Sub-package variant structure for interface types")
    class SubPackageVariantTests {

        @Test
        @DisplayName("GlobalStateSpendAction should be interface with variants in sub-package")
        void globalStateSpendActionVariantsInSubPackage() throws Exception {
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

            String variantPkg = basePkg + ".globalstatespendaction";
            for (String variant : new String[]{"MintSecurity", "PauseTransfers", "ModifySecurityInfo"}) {
                JavaFileObject variantFile = compilation.generatedSourceFile(variantPkg + "." + variant)
                        .orElseThrow(() -> new AssertionError(variant + ".java not generated in globalstatespendaction sub-package"));
                String variantSource = variantFile.getCharContent(true).toString();
                assertThat(variantSource)
                        .as(variant + " should implement Data and GlobalStateSpendAction")
                        .contains("abstract class " + variant + " implements Data<" + variant + ">, GlobalStateSpendAction");
            }
        }

        @Test
        @DisplayName("MintRedeemer should be interface with variants in sub-package")
        void mintRedeemerVariantsInSubPackage() throws Exception {
            assertThat(compilation).succeeded();

            // power_users/MintRedeemer has 4 variants: Init, Deinit, AddPowerUser, RemovePowerUser
            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.powerusers.model";

            JavaFileObject file = compilation.generatedSourceFile(basePkg + ".MintRedeemer")
                    .orElseThrow(() -> new AssertionError("MintRedeemer.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("MintRedeemer should be an interface")
                    .contains("public interface MintRedeemer");

            String variantPkg = basePkg + ".mintredeemer";
            for (String variant : new String[]{"Init", "Deinit"}) {
                JavaFileObject variantFile = compilation.generatedSourceFile(variantPkg + "." + variant)
                        .orElseThrow(() -> new AssertionError(variant + ".java not generated in mintredeemer sub-package"));
                String variantSource = variantFile.getCharContent(true).toString();
                assertThat(variantSource)
                        .as(variant + " should implement Data and MintRedeemer")
                        .contains("abstract class " + variant + " implements Data<" + variant + ">, MintRedeemer");
            }
        }

        @Test
        @DisplayName("MintRedeemer variants should be in mintredeemer sub-package")
        void mintRedeemerVariantsInCorrectSubPackage() {
            assertThat(compilation).succeeded();

            String variantPkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.powerusers.model.mintredeemer";

            assertThat(compilation.generatedSourceFile(variantPkg + ".Init"))
                    .as("Init should be in mintredeemer sub-package")
                    .isPresent();
            assertThat(compilation.generatedSourceFile(variantPkg + ".Deinit"))
                    .as("Deinit should be in mintredeemer sub-package")
                    .isPresent();
        }
    }
}
