package com.bloxbean.cardano.client.txflow.codec;

/** Handling of unrecognized fields in the TxFlow document envelope. */
public enum UnknownFieldPolicy {
    /** Treat an unknown field as a parse error. */
    REJECT,
    /** Preserve parsing but emit a warning diagnostic for the field. */
    WARN,
    /** Ignore the field without a diagnostic. */
    IGNORE
}
