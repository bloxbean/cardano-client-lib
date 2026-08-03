package com.bloxbean.cardano.client.txflow.exec;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Lossless internal result of waiting for transaction confirmation.
 *
 * <p>The type deliberately keeps rollback, cancellation, timeout, operational
 * failure, and inconclusive reconciliation separate. Callers must pass every
 * non-confirmed outcome through terminal handling; in particular,
 * {@link Type#RECOVERY_REQUIRED} must never be interpreted as a safe rebuild or
 * ordinary terminal failure.</p>
 */
final class ConfirmationOutcome {
    /** Mutually exclusive dispositions produced by confirmation tracking. */
    enum Type {
        CONFIRMED,
        ROLLED_BACK,
        TIMEOUT,
        CANCELLED,
        RECOVERY_REQUIRED,
        FAILED
    }

    private final Type type;
    private final ConfirmationResult result;
    private final Throwable error;

    private ConfirmationOutcome(Type type, ConfirmationResult result, Throwable error) {
        this.type = Objects.requireNonNull(type, "type");
        this.result = result;
        this.error = error;
    }

    static ConfirmationOutcome confirmed(ConfirmationResult result) {
        return new ConfirmationOutcome(Type.CONFIRMED, Objects.requireNonNull(result, "result"), null);
    }

    static ConfirmationOutcome rolledBack(ConfirmationResult result) {
        Throwable error = result.getError() != null
                ? result.getError()
                : new FlowExecutionException("Transaction rolled back: " + result.getTxHash());
        return new ConfirmationOutcome(Type.ROLLED_BACK, result, error);
    }

    static ConfirmationOutcome timeout(String txHash, ConfirmationResult result) {
        return new ConfirmationOutcome(Type.TIMEOUT, result, new ConfirmationTimeoutException(txHash));
    }

    static ConfirmationOutcome cancelled(ConfirmationResult result) {
        return new ConfirmationOutcome(Type.CANCELLED, result,
                new CancellationException("Flow cancelled while waiting for confirmation"));
    }

    static ConfirmationOutcome recoveryRequired(String txHash, ConfirmationResult result) {
        Throwable error = result != null && result.getError() != null
                ? result.getError()
                : new ReconciliationUncertainException(txHash);
        return new ConfirmationOutcome(Type.RECOVERY_REQUIRED, result,
                error);
    }

    static ConfirmationOutcome failed(ConfirmationResult result, Throwable error) {
        return new ConfirmationOutcome(Type.FAILED, result, Objects.requireNonNull(error, "error"));
    }

    Type getType() {
        return type;
    }

    ConfirmationResult getResult() {
        return result;
    }

    Throwable getError() {
        return error;
    }

    boolean isConfirmed() {
        return type == Type.CONFIRMED;
    }

    boolean isRolledBack() {
        return type == Type.ROLLED_BACK;
    }
}
