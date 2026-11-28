package com.bloxbean.cardano.client.ledger.conformance;

import com.bloxbean.cardano.client.ledger.conformance.SampleOutcome.Encoding;
import com.bloxbean.cardano.client.ledger.conformance.SampleOutcome.Model;
import com.bloxbean.cardano.client.ledger.conformance.SampleOutcome.Strict;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The runner's stages (D10), the outcomes file's comparison (J4) and the dataset checkout's check (D8). */
class CborConformanceTest {

    private static final byte[] ZERO = {0};

    private static SampleOutcome evaluate(CborRule.Decoder strict, CborRule.Decoder model, boolean valid) {
        CborRule rule = new CborRule("rule", true, strict, model, null);
        return CborConformance.evaluate(rule, "rule/" + (valid ? "valid" : "invalid") + "/0.input.cbor", valid, ZERO,
                valid ? ZERO : null);
    }

    @Test
    void onlyACborRuntimeExceptionFromAStrictReadIsARejection() {
        SampleOutcome reason = evaluate(cbor -> {
            throw new LedgerDecodingException(Reason.UNKNOWN_FIELD, "unknown");
        }, null, false);
        assertThat(reason.strict()).isEqualTo(Strict.REJECTED);
        assertThat(reason.reason()).isEqualTo(Reason.UNKNOWN_FIELD);
        assertThat(reason.passes()).isTrue();
        assertThat(reason.line()).isEqualTo("rule/invalid/0.input.cbor R:UNKNOWN_FIELD - -");

        // a strict read rejects with a LedgerDecodingException only: a plain CborRuntimeException is a broken contract
        SampleOutcome plain = evaluate(cbor -> {
            throw new com.bloxbean.cardano.client.exception.CborRuntimeException("arity");
        }, null, false);
        assertThat(plain.strict()).isEqualTo(Strict.ERROR);
        assertThat(plain.hasError()).isTrue();
        // the harness's own restriction is recorded apart from the policy's rejections
        SampleOutcome restricted = evaluate(cbor -> {
            throw new RuleRestriction("not of this rule's form");
        }, null, false);
        assertThat(restricted.strict()).isEqualTo(Strict.RESTRICTED);
        assertThat(restricted.passes()).isTrue();
        assertThat(restricted.line()).isEqualTo("rule/invalid/0.input.cbor R:RULE - -");
        assertThat(evaluate(cbor -> {
            throw new RuleRestriction("not of this rule's form");
        }, null, true).passes()).isFalse();

        for (RuntimeException crash : List.of(new NullPointerException(), new IllegalArgumentException(),
                new ClassCastException(), new IllegalStateException())) {
            SampleOutcome error = evaluate(cbor -> {
                throw crash;
            }, null, false);
            assertThat(error.strict()).as(crash.toString()).isEqualTo(Strict.ERROR);
            assertThat(error.passes()).isFalse();
            assertThat(error.hasError()).isTrue();
            assertThat(error.line()).endsWith(" E - -");
        }
        SampleOutcome overflow = evaluate(cbor -> {
            throw new StackOverflowError();
        }, null, false);
        assertThat(overflow.strict()).isEqualTo(Strict.ERROR);
        // any Error is that sample's error, as the old harness recorded a crash
        assertThat(evaluate(cbor -> {
            throw new AssertionError("crash");
        }, null, false).strict()).isEqualTo(Strict.ERROR);
    }

    @Test
    void theModelRunsOnStrictAcceptedSamplesOnly() {
        SampleOutcome decoded = evaluate(cbor -> null, cbor -> "model", true);
        assertThat(decoded.line()).isEqualTo("rule/valid/0.input.cbor A D M");
        assertThat(decoded.passes()).isTrue();

        SampleOutcome failed = evaluate(cbor -> null, cbor -> {
            throw new com.bloxbean.cardano.client.exception.CborDeserializationException("gap");
        }, true);
        assertThat(failed.model()).isEqualTo(Model.FAILED);
        assertThat(failed.encoding()).isEqualTo(Encoding.NOT_APPLICABLE);
        assertThat(failed.passes()).isFalse();
        assertThat(failed.hasError()).isFalse();

        SampleOutcome crashed = evaluate(cbor -> null, cbor -> {
            throw new ClassCastException("cast");
        }, true);
        assertThat(crashed.model()).isEqualTo(Model.ERROR);
        assertThat(crashed.hasError()).isTrue();
        // a crash that a model decoder wraps is still a crash, not a gap in the model
        SampleOutcome wrapped = evaluate(cbor -> null, cbor -> {
            throw new com.bloxbean.cardano.client.exception.CborDeserializationException("wrapped",
                    new NullPointerException());
        }, true);
        assertThat(wrapped.model()).isEqualTo(Model.ERROR);

        SampleOutcome notRun = evaluate(cbor -> {
            throw new LedgerDecodingException(Reason.TAG, "tag");
        }, cbor -> {
            throw new AssertionError("not run");
        }, false);
        assertThat(notRun.model()).isEqualTo(Model.NOT_APPLICABLE);
    }

    @Test
    void aReEncodingIsComparedExactlyOrNormalized() {
        CborRule exact = new CborRule("rule", true, cbor -> null, null, model -> new byte[]{0x18, 0x00});
        CborRule normalized = new CborRule("rule", false, cbor -> null, null, model -> new byte[]{0x18, 0x00});
        assertThat(CborConformance.evaluate(exact, "rule/valid/0.input.cbor", true, ZERO, ZERO).encoding())
                .isEqualTo(Encoding.DIFFERS);
        assertThat(CborConformance.evaluate(normalized, "rule/valid/0.input.cbor", true, ZERO, ZERO).encoding())
                .isEqualTo(Encoding.MATCHES);
    }

    @Test
    void anyChangedLineIsADifferenceEvenWithEqualTotals() {
        List<String> pinned = List.of("# header", "a/invalid/1 R:TAG - -", "a/invalid/2 A - -");
        assertThat(CborConformance.differences(pinned, pinned)).isEmpty();
        // one rejection lost and another gained: the same totals, two differences
        List<String> swapped = List.of("# header", "a/invalid/1 A - -", "a/invalid/2 R:TAG - -");
        assertThat(CborConformance.differences(pinned, swapped)).hasSize(4);
        // another reason code
        assertThat(CborConformance.differences(pinned, List.of("# header", "a/invalid/1 R:STRUCTURE - -",
                "a/invalid/2 A - -"))).containsExactly("- a/invalid/1 R:TAG - -", "+ a/invalid/1 R:STRUCTURE - -");
        assertThat(CborConformance.differences(pinned, List.of("# header", "a/invalid/2 A - -",
                "a/invalid/1 R:TAG - -"))).containsExactly("the same lines, in another order or repeated");
    }

    @Test
    void aNormalizationCheckWithoutVectorsFails(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("normalization"));
        assertThatThrownBy(() -> CborConformance.normalizationDifferences(dir))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void thePinnedOutcomesFileIsForThePinnedRevision() {
        List<String> pinned = CborConformance.expectedOutcomes();
        assertThat(pinned.get(0)).contains(CborConformance.DATASET_REVISION);
        assertThat(pinned.stream().filter(l -> !l.startsWith("#"))).hasSize(43_434);
    }

    @Test
    void theDatasetIsExactlyTheRevisionClean(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("cardano-cbor-dataset");
        Path dataset = root.resolve("dataset");
        Files.createDirectories(dataset.resolve("conway"));
        Files.writeString(dataset.resolve("conway").resolve("a.input.cbor"), "x");
        git(root, "init", "-q");
        git(root, "-c", "user.name=t", "-c", "user.email=t@example.org", "add", "-A");
        git(root, "-c", "user.name=t", "-c", "user.email=t@example.org", "commit", "-q", "-m", "dataset");
        String head = git(root, "rev-parse", "HEAD").trim();

        assertThatCode(() -> DatasetCheckout.verify(dataset, head)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DatasetCheckout.verify(dataset, "0".repeat(40)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expected");
        assertThatThrownBy(() -> DatasetCheckout.verify(root, head))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not the dataset directory");

        Path a = dataset.resolve("conway").resolve("a.input.cbor");
        Files.writeString(a, "y");
        assertThatThrownBy(() -> DatasetCheckout.verify(dataset, head))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("has changed");
        git(root, "checkout", "-q", "--", ".");
        // Codex D2: an index flag, or an edit that keeps the size and time, hides a change from git status
        git(root, "update-index", "--assume-unchanged", "dataset/conway/a.input.cbor");
        java.nio.file.attribute.FileTime time = Files.getLastModifiedTime(a);
        Files.writeString(a, "q");
        Files.setLastModifiedTime(a, time);
        assertThat(git(root, "status", "--porcelain")).isEmpty();
        assertThatThrownBy(() -> DatasetCheckout.verify(dataset, head))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("has changed");
        git(root, "update-index", "--no-assume-unchanged", "dataset/conway/a.input.cbor");
        git(root, "checkout", "-q", "--", ".");
        assertThatCode(() -> DatasetCheckout.verify(dataset, head)).doesNotThrowAnyException();
        // an untracked file, and an ignored one
        Files.writeString(dataset.resolve("conway").resolve("b.input.cbor"), "z");
        assertThatThrownBy(() -> DatasetCheckout.verify(dataset, head))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not");
        Files.writeString(root.resolve(".git").resolve("info").resolve("exclude"), "*.input.cbor\n");
        assertThat(git(root, "status", "--porcelain")).isEmpty();
        assertThatThrownBy(() -> DatasetCheckout.verify(dataset, head))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not");
        Files.delete(dataset.resolve("conway").resolve("b.input.cbor"));
        Files.delete(a);
        assertThatThrownBy(() -> DatasetCheckout.verify(dataset, head))
                .isInstanceOf(IllegalStateException.class);

        Path plain = dir.resolve("plain").resolve("dataset");
        Files.createDirectories(plain);
        assertThatThrownBy(() -> DatasetCheckout.verify(plain, head)).isInstanceOf(IllegalStateException.class);
    }

    private static String git(Path directory, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(out).isZero();
        return out;
    }
}
