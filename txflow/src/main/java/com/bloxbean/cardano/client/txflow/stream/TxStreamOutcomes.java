package com.bloxbean.cardano.client.txflow.stream;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Shared classification for receipt waits and explicit resolution. */
final class TxStreamOutcomes {
    private TxStreamOutcomes() {
    }

    static TxStreamItemResult requireConfirmed(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        switch (result.status()) {
            case CONFIRMED:
                return result;
            case FAILED:
                throw new TxStreamFailedException(result);
            case CANCELLED:
                throw new TxStreamCancelledException(result);
            case RECOVERY_REQUIRED:
                throw new TxStreamUncertainException(result);
            default:
                throw new TxStreamException("TXSTREAM_ITEM_FAILED",
                        "Item '" + result.itemId() + "' did not reach a settled outcome; latest"
                                + " status is " + result.status());
        }
    }

    static String failureCode(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        Throwable cursor = result.error();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cursor != null && seen.add(cursor)) {
            if (cursor instanceof TxStreamException) {
                return ((TxStreamException) cursor).code();
            }
            cursor = cursor.getCause();
        }
        return "TXSTREAM_ITEM_FAILED";
    }

    static String failureMessage(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        String detail = result.error() != null && result.error().getMessage() != null
                ? ": " + result.error().getMessage() : "";
        return "TxStream item '" + result.itemId() + "' failed" + detail;
    }

    static String cancelledMessage(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        return "TxStream item '" + result.itemId() + "' was cancelled";
    }

    static String uncertainMessage(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        String hash = result.transactionHash() != null
                ? " Known transaction hash: " + result.transactionHash() + "." : "";
        return "DO NOT RESUBMIT: TxStream item '" + result.itemId()
                + "' has an uncertain on-chain outcome and requires reconciliation."
                + hash;
    }
}
