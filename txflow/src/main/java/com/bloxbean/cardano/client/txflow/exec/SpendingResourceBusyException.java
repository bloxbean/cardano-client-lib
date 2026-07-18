package com.bloxbean.cardano.client.txflow.exec;

/**
 * Raised when execution policy rejects or exhausts the wait for an in-process
 * spending-resource lock.
 *
 * <p>This is a resource-contention outcome, not evidence that a transaction was
 * submitted or that an on-chain input is spent.</p>
 */
final class SpendingResourceBusyException extends RuntimeException {
    SpendingResourceBusyException(String resourceId) {
        super("Spending resource is busy: " + resourceId);
    }
}
