package com.bloxbean.cardano.client.ledger.slice;

import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.util.Optional;

/**
 * Provides read/write access to the UTxO set during validation.
 * <p>
 * CCL provides a simple in-memory implementation for single-transaction validation.
 * Yaci provides a storage-backed implementation with intra-block chaining support.
 */
public interface UtxoSlice {

    /**
     * Look up a UTxO by its input reference.
     *
     * @param input the transaction input (tx hash + output index)
     * @return the resolved output, or empty if not found
     */
    Optional<TransactionOutput> lookup(TransactionInput input);

    /**
     * Mark a UTxO as consumed (spent). Used during intra-block processing.
     *
     * @param input the input being spent
     */
    void consume(TransactionInput input);

    /**
     * Add a newly produced UTxO. Used during intra-block processing.
     *
     * @param input  the input reference (tx hash + output index) for the new UTxO
     * @param output the output being produced
     */
    void produce(TransactionInput input, TransactionOutput output);
}
