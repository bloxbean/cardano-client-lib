package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Compaction-safe read of one stored execution: an authoritative snapshot
 * baseline plus the ordered event tail visible after a caller cursor.
 *
 * <p>The store may compact terminal-prefix events, so a caller cursor can
 * predate the retained journal. Instead of failing, the read clamps the cursor
 * to the snapshot's compaction watermark and reports {@link #rebaselined()};
 * a rebaselined view means the events before {@link FlowExecutionSnapshot
 * #compactedThroughSequence()} are gone and the caller must project from the
 * {@link #baseline()} snapshot, treating {@link #events()} as post-baseline
 * detail only.</p>
 *
 * @param baseline snapshot read in the same operation as the events
 * @param events immutable events strictly after the effective cursor, ascending
 * @param nextSequence exclusive cursor for the next read
 * @param rebaselined whether the caller cursor predated the compaction
 *                    watermark and was clamped to it
 */
public record ExecutionEventView(FlowExecutionSnapshot baseline, List<FlowEvent> events,
                                 long nextSequence, boolean rebaselined) {
    /**
     * Creates a validated view with an immutable event list.
     *
     * @param baseline snapshot read in the same operation as the events
     * @param events events strictly after the effective cursor; copied on construction
     * @param nextSequence exclusive cursor for the next read
     * @param rebaselined whether the caller cursor was clamped to the compaction watermark
     */
    public ExecutionEventView {
        Objects.requireNonNull(baseline, "baseline cannot be null");
        events = List.copyOf(Objects.requireNonNull(events, "events cannot be null"));
        if (nextSequence < 0) throw new IllegalArgumentException("nextSequence cannot be negative");
    }
}
