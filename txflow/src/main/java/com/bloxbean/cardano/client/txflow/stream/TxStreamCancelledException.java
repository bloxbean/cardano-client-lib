package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/** Cancelled outcome returned by {@link TxStreamReceipt#awaitConfirmed()}. */
public final class TxStreamCancelledException extends TxStreamException {
    /** Complete cancelled projection. */
    private final TxStreamItemResult result;

    /**
     * Creates an exception for a cancelled item projection.
     *
     * @param result complete cancelled item result
     */
    public TxStreamCancelledException(TxStreamItemResult result) {
        super("TXSTREAM_ITEM_CANCELLED", TxStreamOutcomes.cancelledMessage(result),
                Objects.requireNonNull(result, "result").getError());
        this.result = result;
    }

    /** @return complete cancelled item result */
    public TxStreamItemResult result() {
        return result;
    }
}
