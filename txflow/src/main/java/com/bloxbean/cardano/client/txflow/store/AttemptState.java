package com.bloxbean.cardano.client.txflow.store;

/**
 * Durable lifecycle of one transaction attempt.
 *
 * <p>States describe persisted facts rather than a mandatory linear in-memory state machine.
 * Recovery may reconstruct a later observation directly, and a previously confirmed inclusion
 * may subsequently be marked rolled back while it remains inside the monitoring horizon.</p>
 */
public enum AttemptState {
    /** Transaction materialization is in progress. */
    BUILDING,
    /** An unsigned transaction body has been built. */
    BUILT,
    /** Exact signed payload and its identity have been durably recorded. */
    SIGNED,
    /** The pre-submission boundary was persisted before calling the backend. */
    SUBMITTING,
    /** Submission was accepted or recovery otherwise established the transaction's existence. */
    SUBMITTED,
    /** At least one on-chain inclusion has been observed. */
    IN_BLOCK,
    /** The configured confirmation depth has been reached. */
    CONFIRMED,
    /** A previously observed inclusion was removed by a rollback. */
    ROLLED_BACK,
    /** A newer attempt replaced this attempt. */
    SUPERSEDED,
    /** The attempt ended in a classified failure. */
    FAILED,
    /** Execution was cancelled before this attempt completed. */
    CANCELLED,
    /** Automatic progress stopped because a safe outcome could not be established. */
    RECOVERY_REQUIRED
}
