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

public class AftermarketTest {

    private static Compilation compilation;

    @BeforeAll
    static void setUp() {
        compilation = javac()
                .withProcessors(new BlueprintAnnotationProcessor())
                .compile(JavaFileObjects.forResource("blueprint/Aftermarket.java"));
    }

    @Test
    void aftermarketCompile() {
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
        @DisplayName("MarketDatum should be interface with 5 variants in sub-package")
        void marketDatumVariantsInSubPackage() throws Exception {
            assertThat(compilation).succeeded();

            // MarketDatum has 5 variants: SpotDatum, AuctionDatum, SpotBidDatum, ClaimBidDatum, AcceptedBidDatum
            // Namespace: cardano_aftermarket/data/datums → package segment: cardanoaftermarket.data.datums
            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.model.aftermarket.cardanoaftermarket.data.datums.model";

            JavaFileObject file = compilation.generatedSourceFile(basePkg + ".MarketDatum")
                    .orElseThrow(() -> new AssertionError("MarketDatum.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("MarketDatum should be an interface")
                    .contains("public interface MarketDatum");

            String variantPkg = basePkg + ".marketdatum";
            for (String variant : new String[]{"SpotDatum", "AuctionDatum", "SpotBidDatum", "ClaimBidDatum", "AcceptedBidDatum"}) {
                JavaFileObject variantFile = compilation.generatedSourceFile(variantPkg + "." + variant)
                        .orElseThrow(() -> new AssertionError(variant + ".java not generated in marketdatum sub-package"));
                String variantSource = variantFile.getCharContent(true).toString();
                assertThat(variantSource)
                        .as(variant + " should implement Data and MarketDatum")
                        .contains("abstract class " + variant + " implements Data<" + variant + ">, MarketDatum");
            }
        }

        @Test
        @DisplayName("MarketRedeemer should be interface with variants in sub-package")
        void marketRedeemerVariantsInSubPackage() throws Exception {
            assertThat(compilation).succeeded();

            String basePkg = "com.bloxbean.cardano.client.plutus.annotation.blueprint.model.aftermarket.cardanoaftermarket.data.redeemers.model";

            JavaFileObject file = compilation.generatedSourceFile(basePkg + ".MarketRedeemer")
                    .orElseThrow(() -> new AssertionError("MarketRedeemer.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("MarketRedeemer should be an interface")
                    .contains("public interface MarketRedeemer");

            String variantPkg = basePkg + ".marketredeemer";
            JavaFileObject variantFile = compilation.generatedSourceFile(variantPkg + ".CloseOrUpdateSellerUTxO")
                    .orElseThrow(() -> new AssertionError("CloseOrUpdateSellerUTxO.java not generated in marketredeemer sub-package"));
            String variantSource = variantFile.getCharContent(true).toString();
            assertThat(variantSource)
                    .as("Should contain CloseOrUpdateSellerUTxO in sub-package")
                    .contains("abstract class CloseOrUpdateSellerUTxO implements Data<CloseOrUpdateSellerUTxO>, MarketRedeemer");
        }
    }
}
