package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.txflow.codec.DiagnosticSeverity;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;

import java.util.List;

/**
 * Immutable outcome of binding and preflighting a flow definition.
 *
 * <p>Compilation failures are represented as diagnostics so server and build-tool
 * callers can report all known authoring problems together. The compiled flow is
 * accessible only through {@link #requireCompiledFlow()}.</p>
 */
public final class FlowCompilationResult {
    private final CompiledTxFlow compiledFlow;
    private final List<FlowDiagnostic> diagnostics;

    FlowCompilationResult(CompiledTxFlow compiledFlow, List<FlowDiagnostic> diagnostics) {
        this.compiledFlow = compiledFlow;
        this.diagnostics = List.copyOf(diagnostics);
    }

    /**
     * Indicates whether any diagnostic prevents execution.
     *
     * @return {@code true} when at least one error was reported
     */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR);
    }

    /**
     * Returns immutable diagnostics in compilation order.
     *
     * @return diagnostics, including non-fatal warnings
     */
    public List<FlowDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    /**
     * Returns the compiled plan when compilation succeeded.
     *
     * @return compiled flow
     * @throws IllegalStateException if compilation produced no plan or reported errors
     */
    public CompiledTxFlow requireCompiledFlow() {
        if (compiledFlow == null || hasErrors()) {
            throw new IllegalStateException("TxFlow compilation failed: " + diagnostics);
        }
        return compiledFlow;
    }
}
