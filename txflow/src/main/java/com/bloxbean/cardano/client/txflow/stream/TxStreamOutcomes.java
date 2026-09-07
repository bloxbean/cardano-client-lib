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
        switch (result.getStatus()) {
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
                        "Item '" + result.getItemId() + "' did not reach a settled outcome; latest"
                                + " status is " + result.getStatus());
        }
    }

    static String failureCode(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        Throwable cursor = result.getError();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (cursor != null && seen.add(cursor)) {
            if (cursor instanceof TxStreamException) {
                return ((TxStreamException) cursor).getCode();
            }
            cursor = cursor.getCause();
        }
        return "TXSTREAM_ITEM_FAILED";
    }

    static String failureMessage(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        String detail = result.getError() != null && result.getError().getMessage() != null
                ? ": " + result.getError().getMessage() : "";
        return "TxStream item '" + result.getItemId() + "' failed" + detail;
    }

    static String cancelledMessage(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        return "TxStream item '" + result.getItemId() + "' was cancelled";
    }

    static String uncertainMessage(TxStreamItemResult result) {
        Objects.requireNonNull(result, "result");
        String hash = result.getTransactionHash() != null
                ? " Known transaction hash: " + result.getTransactionHash() + "." : "";
        return "DO NOT RESUBMIT: TxStream item '" + result.getItemId()
                + "' has an uncertain on-chain outcome and requires reconciliation."
                + hash;
    }
}
