package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TxPlan readers over untrusted text (ADR I11, I12):
 * <ul>
 *     <li>a reader returns a plan or rejects the input, and never fails with a programming error
 *     ({@code NullPointerException}, {@code ClassCastException}, an index error, {@code StackOverflowError} or any
 *     other {@link Error})</li>
 *     <li>an accepted plan is writable, its YAML reads back with the same reader (strictly, for a strict reader), and
 *     writing that again gives the same YAML</li>
 * </ul>
 * The seeded tests run in every build; the {@code @FuzzTest}s replay their committed seeds and explore new inputs with
 * {@code JAZZER_FUZZ=1}.
 */
class TxPlanReaderFuzzTest {

    private static final int MAX_TEXT = 16 * 1024;
    private static final TxPlanCodec STRICT = TxPlanCodec.builder().build();
    private static final TxPlanCodec LENIENT = TxPlanCodec.builder().strictIntentFields(false).build();
    private static final List<Function<String, TxPlan>> READERS = List.of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml);
    private static final List<Class<? extends Throwable>> PROGRAMMING_ERRORS = List.of(
            NullPointerException.class, ClassCastException.class, IndexOutOfBoundsException.class,
            ArithmeticException.class, UnsupportedOperationException.class, Error.class);

    private static final List<JsonNode> SEEDS = new ArrayList<>();
    private static final TxPlanMutator MUTATOR;

    static {
        IntentSamples.documents().values().forEach(yaml -> SEEDS.add(tree(yaml)));
        List<String> types = Arrays.stream(TxIntent.class.getAnnotation(JsonSubTypes.class).value())
                .map(JsonSubTypes.Type::name).toList();
        TreeSet<String> names = new TreeSet<>(List.of("variables", "refs", "utxo_refs", "flow_output", "step", "output",
                "tx_hash", "output_index", "unit", "quantity", "relay_type", "numerator", "denominator"));
        SEEDS.forEach(seed -> collectNames(seed, names));
        MUTATOR = new TxPlanMutator(types, new ArrayList<>(names));
    }

    @Test
    void seededMutantsAreReadTotallyAndAcceptedOnesReachAFixedPoint() {
        Random random = new Random(20261025L);
        TxPlanMutator.Choices choices = TxPlanMutator.Choices.of(random);
        int accepted = 0;
        for (int i = 0; i < 3000; i++) {
            JsonNode mutant = MUTATOR.mutate(SEEDS.get(random.nextInt(SEEDS.size())), choices);
            accepted += check(yaml(mutant));
        }
        // Mutants must still be accepted often enough to exercise the fixed-point check.
        assertThat(accepted).isGreaterThan(500);
    }

    @Test
    void seededArbitraryTextIsReadTotally() {
        Random random = new Random(42L);
        String alphabet = "abcdefxyz0123456789 :-[]{},'\"#&*!|>%@`\n\t$.?";
        for (int i = 0; i < 2000; i++) {
            StringBuilder text = new StringBuilder();
            if (random.nextBoolean()) text.append(yaml(SEEDS.get(random.nextInt(SEEDS.size()))));
            int extra = random.nextInt(64);
            int at = text.length() == 0 ? 0 : random.nextInt(text.length());
            StringBuilder noise = new StringBuilder();
            for (int j = 0; j < extra; j++) noise.append(alphabet.charAt(random.nextInt(alphabet.length())));
            text.insert(at, noise);
            check(text.toString());
        }
    }

    @FuzzTest(maxDuration = "60s")
    void fuzzedMutantsAreReadTotallyAndAcceptedOnesReachAFixedPoint(FuzzedDataProvider data) {
        JsonNode seed = SEEDS.get(data.consumeInt(0, SEEDS.size() - 1));
        check(yaml(MUTATOR.mutate(seed, new TxPlanMutator.Choices() {
            @Override
            public int pick(int bound) {
                return data.consumeInt(0, bound - 1);
            }

            @Override
            public String text() {
                return data.consumeString(8);
            }
        })));
    }

    @FuzzTest(maxDuration = "60s")
    void fuzzedTextIsReadTotally(byte[] input) {
        byte[] bytes = input == null ? new byte[0] : Arrays.copyOf(input, Math.min(input.length, MAX_TEXT));
        check(new String(bytes, StandardCharsets.UTF_8));
    }

    /** @return how many readers accepted the text */
    private static int check(String yaml) {
        int accepted = 0;
        for (Function<String, TxPlan> reader : READERS) {
            TxPlan plan;
            try {
                plan = reader.apply(yaml);
            } catch (Throwable rejected) {
                assertNotAProgrammingError(rejected, yaml);
                continue;
            }
            accepted++;
            assertFixedPoint(plan, reader, yaml);
        }
        return accepted;
    }

    /** The plan's YAML reads back with the reader that accepted the input (strictly, for a strict reader). */
    private static void assertFixedPoint(TxPlan plan, Function<String, TxPlan> reader, String input) {
        String first;
        String second;
        try {
            first = plan.toYaml();
            second = reader.apply(first).toYaml();
        } catch (Throwable t) {
            throw new AssertionError("an accepted plan must write and read back with the same reader; input:\n" + input, t);
        }
        assertThat(second).as("toYaml() fixed point; input:%n%s", input).isEqualTo(first);
    }

    private static void assertNotAProgrammingError(Throwable rejected, String yaml) {
        for (Throwable t = rejected; t != null; t = t.getCause() == t ? null : t.getCause()) {
            for (Class<? extends Throwable> error : PROGRAMMING_ERRORS) {
                if (error.isInstance(t)) {
                    throw new AssertionError("reader failed with " + t.getClass().getName()
                            + " instead of rejecting the input:\n" + yaml, rejected);
                }
            }
        }
    }

    private static JsonNode tree(String yaml) {
        try {
            return YamlSerializer.getYamlMapper().readTree(yaml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String yaml(JsonNode node) {
        try {
            return YamlSerializer.getYamlMapper().writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void collectNames(JsonNode node, TreeSet<String> names) {
        if (node.isObject()) {
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                String name = it.next();
                names.add(name);
                collectNames(node.get(name), names);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectNames(child, names));
        }
    }
}
