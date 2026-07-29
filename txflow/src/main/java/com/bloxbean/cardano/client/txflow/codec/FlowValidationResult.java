package com.bloxbean.cardano.client.txflow.codec;

import java.util.List;

/**
 * Immutable, side-effect-free validation result suitable for build tools and
 * servers.
 *
 * @param diagnostics ordered validation diagnostics; {@code null} is normalized
 *                    to an empty list
 */
public record FlowValidationResult(List<FlowDiagnostic> diagnostics) {
    /**
     * Creates a result with an immutable snapshot of its diagnostics.
     *
     * @param diagnostics ordered diagnostics; {@code null} is treated as an empty list
     */
    public FlowValidationResult {
        diagnostics = List.copyOf(diagnostics != null ? diagnostics : List.of());
    }

    /**
     * Reports whether validation completed without error-severity diagnostics.
     * Warnings do not make the result invalid.
     *
     * @return {@code true} when the document is valid
     */
    public boolean isValid() {
        return diagnostics.stream().noneMatch(
                diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }
}
