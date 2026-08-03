package com.bloxbean.cardano.client.txflow.codec;

/**
 * Machine-readable problem reported while decoding, validating, or compiling a flow.
 *
 * @param code stable diagnostic code suitable for programmatic handling
 * @param severity effect of the problem on the operation
 * @param message human-readable explanation
 * @param documentPath path to the affected value, using a JSONPath-like notation
 * @param line source line when available, otherwise {@code null}
 * @param column source column when available, otherwise {@code null}
 * @param stepId affected flow step when known, otherwise {@code null}
 */
public record FlowDiagnostic(String code, DiagnosticSeverity severity, String message,
                             String documentPath, Integer line, Integer column, String stepId) {
    /**
     * Creates an error without source coordinates or a resolved step identifier.
     *
     * @param code stable diagnostic code
     * @param message human-readable explanation
     * @param path document path associated with the error
     * @return the error diagnostic
     */
    public static FlowDiagnostic error(String code, String message, String path) {
        return new FlowDiagnostic(code, DiagnosticSeverity.ERROR, message, path, null, null, null);
    }
}
