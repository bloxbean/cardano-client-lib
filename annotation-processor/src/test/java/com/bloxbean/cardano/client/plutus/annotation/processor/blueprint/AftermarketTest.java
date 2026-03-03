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
    @DisplayName("Inner class structure for interface types")
    class InnerClassStructureTests {

        @Test
        @DisplayName("MarketDatum should contain 5 variants as inner classes")
        void marketDatumInnerClasses() throws Exception {
            assertThat(compilation).succeeded();

            // MarketDatum has 5 variants: SpotDatum, AuctionDatum, SpotBidDatum, ClaimBidDatum, AcceptedBidDatum
            // Namespace: cardano_aftermarket/data/datums → package segment: cardanoaftermarket.data.datums
            JavaFileObject file = compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.model.aftermarket.cardanoaftermarket.data.datums.model.MarketDatum")
                    .orElseThrow(() -> new AssertionError("MarketDatum.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("MarketDatum should be an interface")
                    .contains("public interface MarketDatum");

            assertThat(source)
                    .as("Should contain SpotDatum inner class")
                    .contains("abstract class SpotDatum implements Data<SpotDatum>, MarketDatum");
            assertThat(source)
                    .as("Should contain AuctionDatum inner class")
                    .contains("abstract class AuctionDatum implements Data<AuctionDatum>, MarketDatum");
            assertThat(source)
                    .as("Should contain SpotBidDatum inner class")
                    .contains("abstract class SpotBidDatum implements Data<SpotBidDatum>, MarketDatum");
            assertThat(source)
                    .as("Should contain ClaimBidDatum inner class")
                    .contains("abstract class ClaimBidDatum implements Data<ClaimBidDatum>, MarketDatum");
            assertThat(source)
                    .as("Should contain AcceptedBidDatum inner class")
                    .contains("abstract class AcceptedBidDatum implements Data<AcceptedBidDatum>, MarketDatum");
        }

        @Test
        @DisplayName("MarketRedeemer should contain variants as inner classes")
        void marketRedeemerInnerClasses() throws Exception {
            assertThat(compilation).succeeded();

            JavaFileObject file = compilation.generatedSourceFile(
                    "com.bloxbean.cardano.client.plutus.annotation.blueprint.model.aftermarket.cardanoaftermarket.data.redeemers.model.MarketRedeemer")
                    .orElseThrow(() -> new AssertionError("MarketRedeemer.java not generated"));
            String source = file.getCharContent(true).toString();

            assertThat(source)
                    .as("MarketRedeemer should be an interface")
                    .contains("public interface MarketRedeemer");
            assertThat(source)
                    .as("Should contain CloseOrUpdateSellerUTxO inner class")
                    .contains("abstract class CloseOrUpdateSellerUTxO implements Data<CloseOrUpdateSellerUTxO>, MarketRedeemer");
        }
    }
}
