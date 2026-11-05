package com.bloxbean.cardano.client.transaction.raw;

/**
 * The layouts of a Shelley-family block, which {@link EraSpec} names per era. Both hold the transactions in parallel
 * arrays, {@code [header, transaction_bodies, transaction_witness_sets, auxiliary_data_set (, invalid_transactions)]},
 * so {@link RawBlock} reads them once, by position.
 */
enum BlockLayout {
    PARALLEL_4(4),
    PARALLEL_5(5);

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
