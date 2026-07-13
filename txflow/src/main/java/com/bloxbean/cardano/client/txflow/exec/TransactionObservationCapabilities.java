package com.bloxbean.cardano.client.txflow.exec;

/**
 * Backend-adapter capabilities relevant to transaction reconciliation.
 *
 * <p>These are properties of the adapter and its indexing guarantees. They
 * cannot be declared by flow YAML or execution configuration. Implementations
 * must advertise only guarantees provided by the deployed backend, because
 * TxFlow uses them to distinguish an authoritative rollback observation from
 * an inconclusive lookup.</p>
 */
public interface TransactionObservationCapabilities {

    /**
     * Whether an empty transaction lookup is authoritative at a sufficiently
     * advanced chain point.
     *
     * <p>Returning {@code true} asserts that the transaction index is consistent
     * with the reported chain tip. TxFlow still applies its chain-point and
     * consecutive-observation checks before declaring rollback.</p>
     *
     * @return {@code true} only when transaction absence is authoritative
     */
    default boolean supportsAuthoritativeAbsence() {
        return false;
    }

    /**
     * Whether transaction observation includes the backend's mempool view.
     *
     * @return {@code true} when submitted, not-yet-included transactions can be
     *         observed
     */
    default boolean supportsMempoolObservation() {
        return false;
    }
}
