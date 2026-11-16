package com.bloxbean.cardano.client.ledger.conformance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0005's gate (D6): every Conway sample of cardano-cbor-dataset, valid and invalid, through CCL's Haskell-strict
 * reads, models and writers. The outcomes are exact, per sample: any change to any sample's line fails the gate until
 * the pinned file is updated after review (J4), and no stage may be an error (J11).
 *
 * <p>Run with {@code -PcborDataset=<checkout>/dataset}; the checkout must be {@link CborConformance#DATASET_REVISION},
 * clean (D8). Each run writes its outcomes to {@code build/cbor-conformance/}, the file to review and pin when an
 * outcome changes on purpose.</p>
 */
@EnabledIfSystemProperty(named = "cbor.dataset", matches = ".+")
class ConwayOutcomesGateTest {

    private static List<SampleOutcome> outcomes;
    private static List<String> lines;

    private static synchronized List<SampleOutcome> outcomes() throws IOException {
        if (outcomes == null) {
            outcomes = CborConformance.run(Path.of(System.getProperty("cbor.dataset")));
            lines = CborConformance.outcomesFile(outcomes);
            Path out = Path.of("build", "cbor-conformance", "outcomes-"
                    + CborConformance.DATASET_REVISION.substring(0, 8) + ".txt");
            Files.createDirectories(out.getParent());
            Files.write(out, lines);
        }
        return outcomes;
    }

    @Test
    void theNormalizationMatchesTheDatasetsVectors() {
        assertThat(CborConformance.normalizationDifferences(Path.of(System.getProperty("cbor.dataset")))).isEmpty();
    }

    @Test
    void theOutcomesAreExactlyThePinnedFile() throws IOException {
        outcomes();
        List<String> differences = CborConformance.differences(CborConformance.expectedOutcomes(), lines);
        assertThat(differences).as("outcomes that differ from the pinned file (the run's file is in "
                + "build/cbor-conformance/)").isEmpty();
    }

    @Test
    void noStageIsAnError() throws IOException {
        assertThat(outcomes().stream().filter(SampleOutcome::hasError)
                .map(o -> o.line() + " " + o.detail()).collect(Collectors.toList())).isEmpty();
    }

    /**
     * The migration baseline (ADR 0005 section 1.2, r5): what Yano reported at {@code de892d95} on CCL 0.8.0-pre8,
     * changed only by J1's corrections. The pinned file holds it sample by sample; this says it in numbers.
     */
    @Test
    void theBaselineHolds() throws IOException {
        List<SampleOutcome> all = outcomes();
        assertThat(all).hasSize(43_434);
        assertThat(all.stream().filter(o -> !o.valid())).hasSize(31_842).allMatch(SampleOutcome::passes);
        List<String> misses = all.stream().filter(o -> !o.passes()).map(SampleOutcome::path)
                .collect(Collectors.toList());
        assertThat(misses).hasSize(65);
        assertThat(misses.stream().filter(p -> p.startsWith("auxiliary_data/valid/")
                || p.startsWith("auxiliary_data_array/valid/"))).hasSize(64);
        assertThat(misses).contains("address/manual-valid/byron-payload-array-with-a-wide-head.input.cbor");
        assertThat(all.stream().filter(o -> o.model() == SampleOutcome.Model.FAILED)).isEmpty();
    }
}
