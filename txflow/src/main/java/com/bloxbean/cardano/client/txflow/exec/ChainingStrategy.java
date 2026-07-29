package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.ChainingMode;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Selects one execution-mode implementation without owning tasks or threads.
 *
 * <p>The facade supplies the mode implementations as run-scoped operations. This keeps
 * scheduling policy separate from executor ownership and lets callers use platform or
 * virtual-thread executors without changing TxFlow internals.</p>
 */
final class ChainingStrategy {
    private final ChainingMode mode;

    private ChainingStrategy(ChainingMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    static ChainingStrategy forMode(ChainingMode mode) {
        return new ChainingStrategy(mode != null ? mode : ChainingMode.SEQUENTIAL);
    }

    <T> T execute(Supplier<T> sequential, Supplier<T> pipelined, Supplier<T> batch) {
        Objects.requireNonNull(sequential, "sequential");
        Objects.requireNonNull(pipelined, "pipelined");
        Objects.requireNonNull(batch, "batch");
        return switch (mode) {
            case SEQUENTIAL -> sequential.get();
            case PIPELINED -> pipelined.get();
            case BATCH -> batch.get();
        };
    }
}
