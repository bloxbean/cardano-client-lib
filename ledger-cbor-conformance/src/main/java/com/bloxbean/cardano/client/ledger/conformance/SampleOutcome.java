package com.bloxbean.cardano.client.ledger.conformance;

import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.util.Objects;

/**
 * One dataset sample's outcome, stage by stage (ADR 0005 D10).
 *
 * <ul>
 *   <li>{@link #strict}: accepted, rejected with a {@link #reason}, or an error. A {@code CborRuntimeException} from a
 *       strict read is a rejection; anything else thrown is an error.</li>
 *   <li>{@link #model}: run only on strict-accepted samples of a rule with a CCL model: decoded, failed (a
 *       {@code CborRuntimeException} or {@code CborDeserializationException}), an error (anything else), or not
 *       applicable.</li>
 *   <li>{@link #encoding}: for a valid, strict-accepted sample whose model did not fail: the re-encoding matches the
 *       expected bytes (normalized as the dataset says), differs, is an error, or is not applicable.</li>
 * </ul>
 *
 * A valid sample passes when it is accepted, decoded or not applicable, and matches or not applicable; an invalid one
 * when it is rejected. Every other sample is a miss.
 *
 * @param path     the sample's path under the dataset's {@code conway} directory, {@code /}-separated
 * @param valid    whether the dataset says the sample decodes
 * @param strict   the strict read's outcome
 * @param reason   the rejection's reason, when {@link #strict} is {@link Strict#REJECTED}
 * @param model    the model's outcome
 * @param encoding the re-encoding's outcome
 * @param detail   what went wrong, for people, or null; never compared
 */
public record SampleOutcome(String path, boolean valid, Strict strict, Reason reason, Model model, Encoding encoding,
                            String detail) {

    /** The strict read's outcome. */
    public enum Strict {
        ACCEPTED("A"), REJECTED("R"), ERROR("E");

        final String code;

        Strict(String code) {
            this.code = code;
        }
    }

    /** The model's outcome. */
    public enum Model {
        DECODED("D"), FAILED("F"), ERROR("E"), NOT_APPLICABLE("-");

        final String code;

        Model(String code) {
            this.code = code;
        }
    }

    /** The re-encoding's outcome. */
    public enum Encoding {
        MATCHES("M"), DIFFERS("X"), ERROR("E"), NOT_APPLICABLE("-");

        final String code;

        Encoding(String code) {
            this.code = code;
        }
    }

    public SampleOutcome {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(strict, "strict");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(encoding, "encoding");
        if ((strict == Strict.REJECTED) != (reason != null)) {
            throw new IllegalArgumentException("a reason goes with a rejection, and only with one");
        }
    }

    /** @return the dataset rule: the first part of the path */
    public String rule() {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    /** @return whether the sample meets the dataset's expectation */
    public boolean passes() {
        if (!valid) {
            return strict == Strict.REJECTED;
        }
        return strict == Strict.ACCEPTED && (model == Model.DECODED || model == Model.NOT_APPLICABLE)
                && (encoding == Encoding.MATCHES || encoding == Encoding.NOT_APPLICABLE);
    }

    /** @return whether any stage is an error, which fails the gate (J11) */
    public boolean hasError() {
        return strict == Strict.ERROR || model == Model.ERROR || encoding == Encoding.ERROR;
    }

    /**
     * @return the sample's line in the outcomes file: its path, then the strict outcome ({@code A}, {@code R:REASON} or
     *         {@code E}), the model's ({@code D}, {@code F}, {@code E} or {@code -}) and the re-encoding's ({@code M},
     *         {@code X}, {@code E} or {@code -})
     */
    public String line() {
        return path + " " + (strict == Strict.REJECTED ? "R:" + reason.name() : strict.code) + " " + model.code + " "
                + encoding.code;
    }

    /** @return why the sample misses, as Yano's report has written it ({@code decode: …} or {@code encode: …}) */
    public String failure() {
        if (passes()) {
            return null;
        }
        if (!valid) {
            return strict == Strict.ERROR ? "decode: crash: " + detail : "decode: succeeded when expected to fail";
        }
        if (strict == Strict.REJECTED) {
            return "decode: rejected (" + reason.name() + ")" + (detail != null ? ": " + detail : "");
        }
        if (strict == Strict.ERROR) {
            return "decode: crash: " + detail;
        }
        if (model != Model.DECODED && model != Model.NOT_APPLICABLE) {
            return "encode: the model " + (model == Model.FAILED ? "does not decode" : "crashes") + ": " + detail;
        }
        return encoding == Encoding.DIFFERS ? "encode: re-encoding differs from the reference"
                : "encode: crash: " + detail;
    }
}
