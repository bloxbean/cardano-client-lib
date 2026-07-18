package com.bloxbean.cardano.client.txflow.codec;

import java.util.Objects;

/**
 * Public validation entry point backed by the portable/legacy decoder.
 *
 * <p>Validation covers document decoding and the structural or graph checks
 * performed by the selected compatibility decoder. It does not bind parameters,
 * resolve resources, or perform the policy preflight supplied by
 * {@code TxFlowCompiler}.</p>
 */
public final class TxFlowValidator {
    private final TxFlowCodec codec;
    private final FlowParseOptions options;

    private TxFlowValidator(TxFlowCodec codec, FlowParseOptions options) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Creates a validator using the standard codec and bounded server defaults.
     *
     * @return reusable validator
     */
    public static TxFlowValidator standard() {
        return new TxFlowValidator(TxFlowCodec.standard(), FlowParseOptions.serverDefaults());
    }

    /**
     * Parses and validates a portable or legacy TxFlow document.
     *
     * @param source YAML or JSON source
     * @return immutable diagnostics; malformed documents are reported as errors
     */
    public FlowValidationResult validate(String source) {
        return new FlowValidationResult(codec.parse(source, options).getDiagnostics());
    }
}
