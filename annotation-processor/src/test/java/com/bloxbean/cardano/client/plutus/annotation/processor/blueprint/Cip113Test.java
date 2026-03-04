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

public class Cip113Test {

    private static Compilation compilation;

    @BeforeAll
    static void setUp() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor(), new ConstrAnnotationProcessor())
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
    @DisplayName("Inner class structure for interface types")
    class InnerClassStructureTests {

        @Test
        @DisplayName("GlobalStateSpendAction should contain variants as inner classes")
        void globalStateSpendActionInnerClasses() throws Exception {
            assertThat(compilation).succeeded();

            // GlobalStateSpendAction has 3 variants: MintSecurity, PauseTransfers, ModifySecurityInfo
            // Namespace: types/global_state → package segment: types.globalstate
            JavaFileObject file = compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.globalstate.model.GlobalStateSpendAction")
                    .orElseThrow(() -> new AssertionError("GlobalStateSpendAction.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("GlobalStateSpendAction should be an interface")
                    .contains("public interface GlobalStateSpendAction");

            assertThat(source)
                    .as("Should contain MintSecurity inner class")
                    .contains("abstract class MintSecurity implements Data<MintSecurity>, GlobalStateSpendAction");
            assertThat(source)
                    .as("Should contain PauseTransfers inner class")
                    .contains("abstract class PauseTransfers implements Data<PauseTransfers>, GlobalStateSpendAction");
            assertThat(source)
                    .as("Should contain ModifySecurityInfo inner class")
                    .contains("abstract class ModifySecurityInfo implements Data<ModifySecurityInfo>, GlobalStateSpendAction");
        }

        @Test
        @DisplayName("MintRedeemer should be an interface with variants as inner classes")
        void mintRedeemerInnerClasses() throws Exception {
            assertThat(compilation).succeeded();

            // power_users/MintRedeemer has 4 variants: Init, Deinit, AddPowerUser, RemovePowerUser
            JavaFileObject file = compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.powerusers.model.MintRedeemer")
                    .orElseThrow(() -> new AssertionError("MintRedeemer.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("MintRedeemer should be an interface")
                    .contains("public interface MintRedeemer");
            assertThat(source)
                    .as("Should contain Init inner class")
                    .contains("abstract class Init implements Data<Init>, MintRedeemer");
            assertThat(source)
                    .as("Should contain Deinit inner class")
                    .contains("abstract class Deinit implements Data<Deinit>, MintRedeemer");
        }

        @Test
        @DisplayName("MintRedeemer variants should NOT be separate top-level files")
        void mintRedeemerVariantsShouldNotBeTopLevel() {
            assertThat(compilation).succeeded();

            // MintRedeemer variants (Init, Deinit, AddPowerUser, RemovePowerUser) should be inner classes
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.powerusers.model.Init"))
                    .as("Init should not be a separate top-level file")
                    .isEmpty();
            assertThat(compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.cip113.types.powerusers.model.Deinit"))
                    .as("Deinit should not be a separate top-level file")
                    .isEmpty();
        }
    }
}
