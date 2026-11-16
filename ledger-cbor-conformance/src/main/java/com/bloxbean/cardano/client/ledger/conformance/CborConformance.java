package com.bloxbean.cardano.client.ledger.conformance;

import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.ledger.conformance.SampleOutcome.Encoding;
import com.bloxbean.cardano.client.ledger.conformance.SampleOutcome.Model;
import com.bloxbean.cardano.client.ledger.conformance.SampleOutcome.Strict;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The cardano-cbor-dataset conformance run of CCL's Haskell-strict reads and models (ADR 0005 D5, D10), one copy for
 * CCL's release gate and Yano's node-diversity report: every Conway sample, valid and invalid, gets a typed outcome per
 * stage ({@link SampleOutcome}), and the run is compared, whole, with the outcomes file pinned for its dataset revision
 * ({@link #expectedOutcomes}).
 *
 * <p>It reads the dataset from a path it is given, and checks that the path is exactly the pinned revision
 * ({@link DatasetCheckout}); it holds no test framework.</p>
 */
public final class CborConformance {

    /** The cardano-cbor-dataset revision the outcomes file was made for: CCL CI's {@code CBOR_DATASET_REVISION}. */
    public static final String DATASET_REVISION = "4fd977b201f7426a2983161a702bb127c87973f7";

    private static final String OUTCOMES_RESOURCE = "outcomes-" + DATASET_REVISION.substring(0, 8) + ".txt";
    private static final String INPUT = ".input.cbor";
    private static final String EXPECTED = ".expected.cbor";

    private CborConformance() {
    }

    /**
     * Runs every Conway sample of the dataset with {@link CborRule#conway()}, after checking that the directory is the
     * pinned revision.
     *
     * @param dataset the dataset's {@code dataset} directory
     * @return each sample's outcome, sorted by path
     * @throws IllegalStateException if the directory is not the pinned revision, or a sample's rule has no rule here
     */
    public static List<SampleOutcome> run(Path dataset) {
        DatasetCheckout.verify(dataset, DATASET_REVISION);
        return run(dataset.resolve("conway"), CborRule.conway());
    }

    /**
     * Runs the samples of a directory with the given rules. It does not check what the directory is: the gate and
     * Yano's report run {@link #run(Path)}, which does ({@link DatasetCheckout}).
     *
     * @param conway the dataset's {@code conway} directory
     * @param rules  the rules, by name
     * @return each sample's outcome, sorted by path
     */
    public static List<SampleOutcome> run(Path conway, Map<String, CborRule> rules) {
        List<SampleOutcome> outcomes = new ArrayList<>();
        for (Path sample : samples(conway)) {
            String path = conway.relativize(sample).toString().replace('\\', '/');
            String[] parts = path.split("/");
            CborRule rule = rules.get(parts[0]);
            if (rule == null) {
                throw new IllegalStateException("the harness has no rule " + parts[0]);
            }
            boolean valid = isValid(parts[1]);
            outcomes.add(evaluate(rule, path, valid, read(sample), valid ? read(expectedOf(sample)) : null));
        }
        return outcomes;
    }

    /**
     * @param category a sample's directory under its rule, such as {@code valid} or {@code invalid-zap-1}
     * @return whether a sample in this category must decode; the dataset's other categories must be rejected
     */
    public static boolean isValid(String category) {
        return category.equals("valid") || category.equals("manual-valid");
    }

    /**
     * One sample through the three stages.
     *
     * @param rule     the sample's rule
     * @param path     the sample's path under {@code conway}
     * @param valid    whether the dataset says it decodes
     * @param input    the sample
     * @param expected the expected re-encoding of a valid sample, or null for an invalid one
     * @return its outcome
     */
    public static SampleOutcome evaluate(CborRule rule, String path, boolean valid, byte[] input, byte[] expected) {
        try {
            rule.strict().decode(input);
        } catch (LedgerDecodingException e) {
            return rejected(path, valid, e.reason(), e);
        } catch (RuleRestriction e) {
            return new SampleOutcome(path, valid, Strict.RESTRICTED, null, Model.NOT_APPLICABLE,
                    Encoding.NOT_APPLICABLE, describe(e));
        } catch (Exception | Error e) {
            return new SampleOutcome(path, valid, Strict.ERROR, null, Model.NOT_APPLICABLE, Encoding.NOT_APPLICABLE,
                    describe(e));
        }
        Object model = null;
        Model modelOutcome = Model.NOT_APPLICABLE;
        String detail = null;
        if (rule.model() != null) {
            try {
                model = rule.model().decode(input);
                modelOutcome = Model.DECODED;
            } catch (CborRuntimeException | CborDeserializationException e) {
                modelOutcome = crashInside(e) ? Model.ERROR : Model.FAILED;
                detail = describe(e);
            } catch (Exception | Error e) {
                modelOutcome = Model.ERROR;
                detail = describe(e);
            }
        }
        if (!valid || modelOutcome == Model.FAILED || modelOutcome == Model.ERROR) {
            return new SampleOutcome(path, valid, Strict.ACCEPTED, null, modelOutcome, Encoding.NOT_APPLICABLE, detail);
        }
        Encoding encoding;
        try {
            byte[] encoded = rule.encode(model, input);
            boolean same = rule.exact() ? Arrays.equals(encoded, expected)
                    : Arrays.equals(CborNormalizer.normalize(encoded), CborNormalizer.normalize(expected));
            encoding = same ? Encoding.MATCHES : Encoding.DIFFERS;
        } catch (Exception | Error e) {
            encoding = Encoding.ERROR;
            detail = describe(e);
        }
        return new SampleOutcome(path, valid, Strict.ACCEPTED, null, modelOutcome, encoding, detail);
    }

    private static SampleOutcome rejected(String path, boolean valid, Reason reason, Exception e) {
        return new SampleOutcome(path, valid, Strict.REJECTED, reason, Model.NOT_APPLICABLE, Encoding.NOT_APPLICABLE,
                describe(e));
    }

    /**
     * Whether a model's decoding exception wraps a crash: CCL's model decoders wrap whatever they catch, so a
     * {@code NullPointerException} or {@code ClassCastException} inside would otherwise read as a gap in the model.
     */
    private static boolean crashInside(Throwable e) {
        for (Throwable cause = e.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            boolean cbor = cause instanceof CborRuntimeException || cause instanceof CborDeserializationException;
            if (cause instanceof Error || (cause instanceof RuntimeException && !cbor)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * Checks the normalization against the dataset's own vectors ({@code dataset/normalization}).
     *
     * @param dataset the dataset's {@code dataset} directory
     * @return the vectors whose normalization differs; empty when all match
     */
    public static List<String> normalizationDifferences(Path dataset) {
        List<String> differences = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(dataset.resolve("normalization"))) {
            for (Path input : files.filter(p -> p.toString().endsWith(INPUT)).sorted().collect(Collectors.toList())) {
                checked++;
                if (!Arrays.equals(CborNormalizer.normalize(read(input)), read(expectedOf(input)))) {
                    differences.add(input.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (checked == 0) {
            throw new IllegalStateException("no normalization vectors in " + dataset.resolve("normalization"));
        }
        return differences;
    }

    /**
     * @param outcomes a run
     * @return the outcomes file of the run: a header naming the dataset revision and the counts, then one line per
     *         sample ({@link SampleOutcome#line()}), sorted by path
     */
    public static List<String> outcomesFile(List<SampleOutcome> outcomes) {
        long passed = outcomes.stream().filter(SampleOutcome::passes).count();
        long invalid = outcomes.stream().filter(o -> !o.valid()).count();
        long rejected = outcomes.stream().filter(o -> !o.valid() && o.passes()).count();
        List<String> lines = new ArrayList<>();
        lines.add("# ADR 0005: CCL's Haskell-strict reads and models over cardano-cbor-dataset " + DATASET_REVISION
                + ", conway");
        lines.add("# " + passed + " of " + outcomes.size() + " samples pass; " + rejected + " of " + invalid
                + " invalid samples rejected");
        lines.add("# <path> <strict: A | R:REASON | R:RULE | E> <model: D | F | E | -> <re-encoding: M | X | E | ->");
        outcomes.stream().map(SampleOutcome::line).sorted().forEach(lines::add);
        return lines;
    }

    /** @return the pinned outcomes file for {@link #DATASET_REVISION}, line by line */
    public static List<String> expectedOutcomes() {
        try (InputStream in = CborConformance.class.getResourceAsStream(OUTCOMES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("no pinned outcomes file " + OUTCOMES_RESOURCE);
            }
            return Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param expected the pinned outcomes file
     * @param actual   a run's
     * @return the lines that differ between the pinned outcomes file and a run's: {@code -} a pinned line the run does
     *         not have, {@code +} a run's line the pinned file does not have. Empty when the two are equal (J3, J4).
     */
    public static List<String> differences(List<String> expected, List<String> actual) {
        Set<String> expectedLines = new HashSet<>(expected);
        Set<String> actualLines = new HashSet<>(actual);
        List<String> out = new ArrayList<>();
        expected.stream().filter(line -> !actualLines.contains(line)).forEach(line -> out.add("- " + line));
        actual.stream().filter(line -> !expectedLines.contains(line)).forEach(line -> out.add("+ " + line));
        if (out.isEmpty() && !expected.equals(actual)) {
            out.add("the same lines, in another order or repeated");
        }
        return out;
    }

    private static List<Path> samples(Path conway) {
        try (Stream<Path> files = Files.walk(conway)) {
            return files.filter(p -> p.getFileName().toString().endsWith(INPUT)).sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path expectedOf(Path input) {
        return input.resolveSibling(input.getFileName().toString().replace(INPUT, EXPECTED));
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
