package com.bloxbean.cardano.client.txflow.codec;

/** Severity assigned to a parse, validation, or compilation diagnostic. */
public enum DiagnosticSeverity {
    /** Informational guidance that does not affect validity. */
    INFO,
    /** A recoverable compatibility or authoring concern. */
    WARNING,
    /** A problem that prevents a valid flow or compiled execution plan. */
    ERROR
}
