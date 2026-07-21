package com.bloxbean.cardano.client.txflow.stream;

/**
 * Sink exposed to source adapters.
 * <p>
 * A sink is the narrow ingestion surface a {@link TxWorkSource} sees: it can
 * submit work either blocking ({@link #submit}) or without blocking for buffer
 * capacity ({@link #trySubmit}). The stream is the only implementor; sources
 * such as {@link TxWorkSource#fromPublisher(java.util.concurrent.Flow.Publisher)}
 * use {@link #trySubmit} so a full stream buffer is a backpressure signal
 * ({@link EmitResult.Status#FULL}) rather than a blocked producer thread.
 */
@FunctionalInterface
public interface TxWorkSink {
    /**
     * Submit one normalized work item to the stream, blocking while the bounded
     * buffer is full.
     *
     * @param item work item
     * @return receipt for tracking the item
     */
    TxStreamReceipt submit(TxWorkItem item);

    /**
     * Attempt to submit one work item without blocking for buffer capacity.
     * <p>
     * The default implementation delegates to the blocking {@link #submit} and
     * always reports {@link EmitResult.Status#OK}; it exists only so the
     * functional-interface shape is preserved for trivial sinks. The stream
     * overrides it with a genuinely non-blocking path that reports
     * {@link EmitResult.Status#FULL} when the bounded buffer is full,
     * {@link EmitResult.Status#CLOSED} when the stream is no longer accepting,
     * {@link EmitResult.Status#PAUSED} when the stream is temporarily not
     * accepting (an ownership standby — park and retry, do not tear down),
     * and the typed content outcomes ({@code DUPLICATE_ATTACHED},
     * {@code CONFLICT}, {@code REJECTED}) — the outcomes a backpressure-aware
     * source needs to apply demand instead of blocking or dropping.
     *
     * @param item work item
     * @return non-blocking emit outcome
     */
    default EmitResult trySubmit(TxWorkItem item) {
        return EmitResult.ok(submit(item));
    }
}
