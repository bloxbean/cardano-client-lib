package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/**
 * Uncertain on-chain outcome returned by
 * {@link TxStreamReceipt#awaitConfirmed()}.
 * <p>
 * The item must be reconciled; blindly rebuilding or resubmitting may create a
 * duplicate payment.
 */
public final class TxStreamUncertainException extends TxStreamException {
    /** Complete uncertain projection, including any known transaction hash. */
    private final TxStreamItemResult result;

    /**
     * Creates an exception for a recovery-required item projection.
     *
     * @param result complete uncertain item result, including any known hash
     */
    public TxStreamUncertainException(TxStreamItemResult result) {
        super("TXSTREAM_RECOVERY_REQUIRED", TxStreamOutcomes.uncertainMessage(result),
                Objects.requireNonNull(result, "result").getError());
        this.result = result;
    }

    /** @return complete uncertain item result, including any known hash */
    public TxStreamItemResult result() {
        return result;
    }

    /** @return caller-visible item id to pass to explicit reconciliation */
    public String itemId() {
        return result.getItemId();
    }
}
