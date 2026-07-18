package com.bloxbean.cardano.client.txflow.stream;

/**
 * Source SPI for producers that feed work into a stream.
 * <p>
 * Sources adapt external producers, files, queues, or in-memory collections
 * into the common {@link TxWorkItem} ingestion contract.
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
}
