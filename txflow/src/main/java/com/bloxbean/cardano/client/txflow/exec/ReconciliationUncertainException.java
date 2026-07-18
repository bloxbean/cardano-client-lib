package com.bloxbean.cardano.client.txflow.exec;

/**
 * Signals that backend observations proved neither transaction presence nor
 * authoritative absence within the reconciliation budget.
 *
 * <p>This condition maps to recovery-required. It must not be collapsed into an
 * ordinary failure, inferred rollback, or permission to build a replacement
 * transaction, because the original signed transaction may still have been
 * accepted.</p>
 */
final class ReconciliationUncertainException extends FlowExecutionException {
    ReconciliationUncertainException(String txHash) {
        super("Transaction state remains uncertain after reconciliation: " + txHash);
    }

    ReconciliationUncertainException(String txHash, Throwable cause) {
        super("Transaction state remains uncertain after reconciliation: " + txHash, cause);
    }
}
