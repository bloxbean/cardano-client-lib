package com.bloxbean.cardano.client.txflow.codec;

import com.bloxbean.cardano.client.txflow.TxFlow;

import java.util.List;

/**
 * Immutable outcome of decoding a TxFlow document.
 *
 * <p>Callers can inspect every diagnostic without relying on exceptions for
 * malformed input. A flow is available through {@link #requireFlow()} only when
 * decoding produced a value and no error-severity diagnostics were reported.</p>
 */
public final class FlowParseResult {
    private final TxFlow flow;
    private final List<FlowDiagnostic> diagnostics;

    FlowParseResult(TxFlow flow, List<FlowDiagnostic> diagnostics) {
        this.flow = flow;
        this.diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Indicates whether any diagnostic prevents use of the decoded flow.
     *
     * @return {@code true} when at least one error was reported
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    }

    /**
     * Returns the immutable ordered diagnostics emitted during parsing.
     *
     * @return diagnostics, including non-fatal warnings
     */
    public List<FlowDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    /**
     * Returns the decoded flow when parsing succeeded.
     *
     * @return decoded flow
     * @throws IllegalStateException if no flow was produced or errors were reported
     */
    public TxFlow requireFlow() {
        if (flow == null || hasErrors()) {
            throw new IllegalStateException("TxFlow parse failed: " + diagnostics);
        }
        return flow;
    }
}
