package com.bloxbean.cardano.client.txflow.config;

/**
 * Stage of flow processing in which an error occurred.
 *
 * <p>The phase complements the error category: the category describes what
 * failed, while the phase identifies how far the transaction may have
 * progressed. Retry policy uses this distinction to avoid rebuilding a
 * transaction whose submission outcome might already be uncertain.</p>
 */
public enum FlowErrorPhase {
    /** Portable definition parsing, validation, or compilation. */
    COMPILE,
    /** Transaction body construction. */
    BUILD,
    /** Transaction signing. */
    SIGN,
    /** Submission to a backend or node. */
    SUBMIT,
    /** Inclusion and confirmation observation. */
    CONFIRM,
    /** Durable snapshot or event persistence. */
    PERSIST,
    /** Reconciliation or recovery of previously persisted work. */
    RECOVER
}
