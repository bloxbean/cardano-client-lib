package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;

import java.util.List;

/**
 * Immutable page returned by an exclusive sequence-cursor journal read.
 *
 * <p>{@code nextSequence} is the last event sequence in this page, or the caller's unchanged
 * cursor when the page is empty. Pass it as the next {@code afterSequence} value to continue
 * without duplicates. A compaction gap is reported by the store instead of being represented as
 * an apparently empty page.</p>
 *
 * @param events events in ascending sequence order
 * @param nextSequence exclusive cursor for the next read
 */
public record EventReadResult(List<FlowEvent> events, long nextSequence) {
    /**
     * Creates an immutable event page.
     *
     * @param events events in ascending sequence order; copied on construction
     * @param nextSequence exclusive cursor for the next read
     */
    public EventReadResult {
        events = List.copyOf(events);
    }
}
