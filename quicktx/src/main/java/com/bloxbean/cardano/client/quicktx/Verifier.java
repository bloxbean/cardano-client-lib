package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Objects;

/**
 * Verifier interface to verify a transaction before submitting to the network.
 * Implement this interface to add verification logic. You can chain multiple verifiers using andThen method.
 * If any of the verifier fails, the transaction submission will fail.
 */
@FunctionalInterface
public interface Verifier {

    /**
     * Verify the transaction
     * @param txn transaction to verify
     * @throws VerifierException if verification fails
     */
    void verify(Transaction txn) throws VerifierException;

    /**
     * Chain multiple verifiers
     * @param after verifier to chain
     * @return chained verifier
     */
    default Verifier andThen(Verifier after) {
        Objects.requireNonNull(after);

        return (txn) -> {
            verify(txn);
            after.verify(txn);
        };
    }
}
