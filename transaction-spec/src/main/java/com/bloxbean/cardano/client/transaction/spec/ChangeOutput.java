package com.bloxbean.cardano.client.transaction.spec;

/**
 * Marker subclass of {@link TransactionOutput} used to identify system-generated change outputs.
 * <p>
 * During transaction building, UTXO selection creates change outputs to return excess funds
 * to the sender. This subclass allows Phase 4 deposit resolution to distinguish change outputs
 * (safe to deduct deposits from) from user-declared outputs (e.g., payToAddress).
 * <p>
 * Serialization is inherited from {@link TransactionOutput} — no CBOR impact.
 * Type information is only meaningful during transaction building, not after serialization.
 */
public class ChangeOutput extends TransactionOutput {

    public ChangeOutput(String address, Value value) {
        super(address, value);
    }
}
