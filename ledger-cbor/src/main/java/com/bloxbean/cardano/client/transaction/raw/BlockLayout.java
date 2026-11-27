package com.bloxbean.cardano.client.transaction.raw;

/**
 * The layouts of a Shelley-family block, which {@link EraSpec} names per era. The parallel layouts hold the
 * transactions in parallel arrays, {@code [header, transaction_bodies, transaction_witness_sets, auxiliary_data_set
 * (, invalid_transactions)]}; the unified layout of Dijkstra holds each transaction whole, {@code [header, [[* [body,
 * witnesses, aux / null, isValid]], leios_certificate / null, peras_certificate / null]]} ({@code Dijkstra/Block.hs},
 * {@code BlockBody/Internal.hs}). {@link RawBlock} reads either.
 */
enum BlockLayout {
    PARALLEL_4(4),
    PARALLEL_5(5),
    /** Experimental: Dijkstra is not final; this may change in any release. */
    UNIFIED(2);

    final int items;

    BlockLayout(int items) {
        this.items = items;
    }

    /**
     * @return whether a block has {@code invalid_transactions}, which is then the fourth part of the block body hash
     * and gives its transactions an {@code isValid} flag (shelley and alonzo {@code BlockBody/Internal.hs})
     */
    boolean invalidTransactions() {
        return this == PARALLEL_5;
    }
}
