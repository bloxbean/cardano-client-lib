package com.bloxbean.cardano.client.txflow.config;

/**
 * Best-known outcome of submitting a particular signed transaction.
 *
 * <p>This is deliberately not a confirmation status. {@link #ACCEPTED} means
 * only that the submitter reported success, while {@link #UNKNOWN} means the
 * transaction may have reached the network and therefore must be reconciled by
 * hash before rebuilding.</p>
 */
public enum SubmissionOutcome {
    /** No submission call was made. */
    NOT_ATTEMPTED,
    /** The submitter reported that it accepted the transaction. */
    ACCEPTED,
    /** The submitter definitively rejected the transaction. */
    REJECTED,
    /** The call did not establish whether the transaction was accepted. */
    UNKNOWN
}
