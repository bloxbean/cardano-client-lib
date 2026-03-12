package com.bloxbean.cardano.client.ledger.slice;

import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple in-memory implementation of {@link UtxoSlice} backed by a HashMap.
 * <p>
 * Suitable for single-transaction validation in CCL where the full UTxO set
 * is provided upfront. For block-level validation with intra-block chaining,
 * Yaci provides a storage-backed implementation.
 */
public class SimpleUtxoSlice implements UtxoSlice {
    private final Map<TransactionInput, TransactionOutput> utxos;

    public SimpleUtxoSlice(Map<TransactionInput, TransactionOutput> utxos) {
        this.utxos = new HashMap<>(utxos);
    }

    @Override
    public Optional<TransactionOutput> lookup(TransactionInput input) {
        return Optional.ofNullable(utxos.get(input));
    }

    @Override
    public void consume(TransactionInput input) {
        utxos.remove(input);
    }

    @Override
    public void produce(TransactionInput input, TransactionOutput output) {
        utxos.put(input, output);
    }
}
