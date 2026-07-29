package com.bloxbean.cardano.client.txflow.stream;

import java.util.concurrent.Flow;

/**
 * Source SPI for producers that feed work into a stream.
 * <p>
 * Sources adapt external producers, files, queues, or in-memory collections
 * into the common {@link TxWorkItem} ingestion contract. The SPI stays tiny on
 * purpose (ADR 0004 Non-goals — the core is not a general reactive-streams
 * framework); {@link #fromPublisher(Flow.Publisher)} is the thin, contract-
 * correct bridge to {@link java.util.concurrent.Flow} for reactive-streams
 * interop, and a simple {@code submit}/{@code receipt} user never touches it.
 */
public interface TxWorkSource extends AutoCloseable {
    /**
     * Start producing work into the provided sink.
     *
     * @param sink stream sink used to submit normalized work
     */
    void start(TxWorkSink sink);

    /**
     * Pause production if the source supports pausing.
     */
    default void pause() {
    }

    /**
     * Resume production if the source supports resuming.
     */
    default void resume() {
    }

    /**
     * Release source resources.
     */
    @Override
    default void close() {
    }

    /**
     * Create a no-op in-memory source. Direct calls to {@link TxFlowStream#submit(TxWorkItem)}
     * or {@link TxFlowStream#trySubmit(TxWorkItem)} provide the work.
     *
     * @return in-memory source
     */
    static TxWorkSource inMemory() {
        return sink -> {
        };
    }

    /**
     * Create a finite source from an iterable of work items.
     *
     * @param items items to submit when the stream starts
     * @return iterable source
     */
    static TxWorkSource iterable(Iterable<TxWorkItem> items) {
        return sink -> {
            if (items != null) {
                for (TxWorkItem item : items) {
                    sink.submit(item);
                }
            }
        };
    }

    /**
     * Bridge a {@link java.util.concurrent.Flow.Publisher} of work items into a
     * demand-backpressured stream source, using the default prefetch of
     * {@value FlowWorkSource#DEFAULT_PREFETCH}.
     *
     * @param publisher reactive-streams publisher of work items
     * @return a Flow-backed source; observe its termination via
     *         {@link FlowWorkSource#terminated()}
     * @see #fromPublisher(Flow.Publisher, int)
     */
    static FlowWorkSource fromPublisher(Flow.Publisher<TxWorkItem> publisher) {
        return new FlowWorkSource(publisher, FlowWorkSource.DEFAULT_PREFETCH);
    }

    /**
     * Bridge a {@link java.util.concurrent.Flow.Publisher} of work items into a
     * demand-backpressured stream source with an explicit prefetch.
     * <p>
     * The adapter requests {@code prefetch} items up front and thereafter
     * requests one more each time an accepted item settles (or a redelivery is
     * resolved), so at most {@code prefetch} items are ever outstanding from the
     * publisher — never {@code Long.MAX_VALUE}. When the stream's bounded buffer
     * is full the adapter holds the received-but-unsubmitted items (bounded by
     * {@code prefetch}) and retries them as capacity frees, so items are never
     * dropped and memory never grows without bound. The publisher's
     * {@code onError}/{@code onComplete} are surfaced through
     * {@link FlowWorkSource#terminated()}; {@code onComplete} lets accepted work
     * drain rather than closing the stream (the caller owns close).
     *
     * @param publisher reactive-streams publisher of work items
     * @param prefetch  positive bound on outstanding, unsettled items pulled
     *                  from the publisher; keep it at or below the stream's
     *                  {@code maxBufferSize} to avoid blocking the dispatch path
     * @return a Flow-backed source
     */
    static FlowWorkSource fromPublisher(Flow.Publisher<TxWorkItem> publisher, int prefetch) {
        return new FlowWorkSource(publisher, prefetch);
    }
}
