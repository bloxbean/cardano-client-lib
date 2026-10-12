package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/** Conclusive failed outcome returned by {@link TxStreamReceipt#awaitConfirmed()}. */
public final class TxStreamFailedException extends TxStreamException {
    /** Complete failed projection. */
    private final TxStreamItemResult result;

    /**
     * Creates an exception for a failed item projection.
     *
     * @param result complete failed item result
     */
    public TxStreamFailedException(TxStreamItemResult result) {
        super(TxStreamOutcomes.failureCode(result), TxStreamOutcomes.failureMessage(result),
                Objects.requireNonNull(result, "result").getError());
        this.result = result;
    }

    /** @return complete failed item result */
    public TxStreamItemResult result() {
        return result;
    }
}
