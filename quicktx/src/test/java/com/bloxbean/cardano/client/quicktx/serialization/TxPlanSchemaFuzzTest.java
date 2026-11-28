package com.bloxbean.cardano.client.quicktx.serialization;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "The schema is never stricter than the runtime" (ADR I10): mutated corpus documents that the runtime accepts must
 * validate. The seeded property runs in every build; the {@code @FuzzTest} replays its committed seeds and explores
 * new inputs with {@code JAZZER_FUZZ=1}.
 */
class TxPlanSchemaFuzzTest {

    private static final List<JsonNode> CORPUS = new ArrayList<>();
    private static final TxPlanMutator MUTATOR;

    static {
        for (String yaml : TxPlanSchemaSupport.corpus().values()) {
            if (TxPlanSchemaSupport.acceptedByRuntime(yaml)) CORPUS.add(TxPlanSchemaSupport.tree(yaml));
        }
        List<String> types = new ArrayList<>();
        TxPlanSchemaGenerator.intentDefinitionNames()
                .forEach(name -> types.add(name.substring(TxPlanSchemaGenerator.INTENT_PREFIX.length())));
        MUTATOR = new TxPlanMutator(types, vocabulary());
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {20261025L, 1L, 42L, 7919L})
    void seededMutationsTheRuntimeAcceptsValidate(long seed) {
        Random random = new Random(seed);
        TxPlanMutator.Choices choices = TxPlanMutator.Choices.of(random);
        int accepted = 0;
        for (int i = 0; i < 1500; i++) {
            if (checkAgreement(CORPUS.get(random.nextInt(CORPUS.size())), choices)) accepted++;
        }
        // The mutants must still exercise the acceptance path, or the property is vacuous.
        assertThat(accepted).isGreaterThan(150);
    }

    @FuzzTest(maxDuration = "60s")
    void fuzzedMutationsTheRuntimeAcceptsValidate(FuzzedDataProvider data) {
        JsonNode document = CORPUS.get(data.consumeInt(0, CORPUS.size() - 1));
        checkAgreement(document, new TxPlanMutator.Choices() {
            @Override
            public int pick(int bound) {
                return data.consumeInt(0, bound - 1);
            }

            @Override
            public String text() {
                return data.consumeString(8);
            }
        });
    }

    /** @return true when the runtime accepted the mutant (and the schema therefore had to accept it) */
    private static boolean checkAgreement(JsonNode document, TxPlanMutator.Choices choices) {
        String yaml = yaml(MUTATOR.mutate(document, choices));
        if (!TxPlanSchemaSupport.acceptedByRuntime(yaml)) return false;
        // Validate the text the runtime read, re-parsed: the writer may turn a text node "737" into the number 737.
        assertThat(TxPlanSchemaSupport.validate(yaml))
                .as("the runtime accepts this document but the schema rejects it:%n%s", yaml)
                .isEmpty();
        return true;
    }

    private static String yaml(JsonNode node) {
        try {
            return TxPlanSchemaSupport.YAML.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every property name the schema declares, so renames often hit a real field of another intent. */
    private static List<String> vocabulary() {
        try {
            JsonNode defs = new ObjectMapper().readTree(TxPlanSchemaSupport.schemaResource()).get("$defs");
            TreeSet<String> names = new TreeSet<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = defs.fields(); it.hasNext(); ) {
                it.next().getValue().path("properties").fieldNames().forEachRemaining(names::add);
            }
            return new ArrayList<>(names);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
