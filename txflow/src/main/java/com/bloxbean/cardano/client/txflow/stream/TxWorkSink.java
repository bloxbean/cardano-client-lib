package com.bloxbean.cardano.client.txflow.stream;

/**
 * Sink exposed to source adapters.
 */
@FunctionalInterface
public interface TxWorkSink {
    /**
     * Submit one normalized work item to the stream.
     *
     * @param item work item
     * @return receipt for tracking the item
     */
    TxStreamReceipt submit(TxWorkItem item);
}
