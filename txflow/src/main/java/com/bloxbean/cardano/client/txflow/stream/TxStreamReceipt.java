package com.bloxbean.cardano.client.txflow.stream;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-item receipt returned when work is accepted by a stream.
 * <p>
 * The receipt exposes the latest projected result and a non-cancelling
 * completion stage that settles when the item reaches a terminal status or
 * {@link TxStreamItemStatus#RECOVERY_REQUIRED}. A settled
 * {@code RECOVERY_REQUIRED} outcome is a point-in-time answer: the live
 * projection returned by {@link #current()} and
 * {@link TxFlowStream#getItemStatus(String)} continues to reflect later
 * read-through repairs.
 */
public final class TxStreamReceipt {
    private final String streamId;
    private final String itemId;
    private final ItemProjection projection;
    private final AtomicLong eventCursor;

    TxStreamReceipt(String streamId, String itemId,
                    ItemProjection projection, AtomicLong eventCursor) {
        this.streamId = streamId;
        this.itemId = itemId;
        this.projection = projection;
        this.eventCursor = eventCursor;
    }

    /**
     * Returns the caller-visible item identity.
     *
     * @return item id
     */
    public String itemId() {
        return itemId;
    }

    /**
     * Returns the stream that accepted the item.
     *
     * @return stream id
     */
    public String streamId() {
        return streamId;
    }

    /**
     * Returns the deterministic engine execution identity bound to this item.
     * <p>
     * Under the default {@link TxStreamPlanner#perItem()} planner the
     * identity is derived from the item's own idempotency claim, so it is
     * available from acceptance and stable across redeliveries and restarts.
     * Under multi-item planners the flow claim — and therefore the execution
     * identity — depends on the window's member set, so the identity becomes
     * available once the item's window has been planned. Items that fail
     * validation before binding have no execution identity.
     *
     * @return execution id once the item is bound to an execution
     */
    public Optional<String> executionId() {
        return Optional.ofNullable(projection.current().getExecutionId());
    }

    /**
     * Returns a read-only stage that settles with the item's outcome.
     * <p>
     * The stage is a view, not the work: completing, cancelling, or
     * obstructing it never affects the item or other observers. Use
     * {@link TxFlowStream#cancel(String, String)} to cancel the item itself.
     *
     * @return non-cancelling completion stage for the settled result
     */
    public CompletionStage<TxStreamItemResult> completion() {
        return projection.promise().minimalCompletionStage();
    }

    /**
     * Returns the latest projected result for this item.
     *
     * @return current result snapshot
     */
    public TxStreamItemResult current() {
        return projection.current();
    }

    /**
     * Returns the item's projection event cursor: the highest engine event
     * sequence this item's projection has consumed.
     * <p>
     * The cursor is {@code 0} until an engine event has been observed, and
     * advances when the live {@code SUBMITTED} read-through
     * ({@link TxFlowStream#getItemStatus(String)} /
     * {@link TxFlowStream#reconcile(String)}) or the terminal projection pass
     * consumes the execution's events. It is an observability cursor over the
     * engine's event stream for {@link #executionId()} — suitable as the
     * {@code afterSequence} input to the engine's event reads — not a count of
     * stream-level status changes; an authoritative fast-forward repair that
     * consulted only the execution snapshot leaves it unchanged.
     *
     * @return highest consumed engine event sequence, {@code 0} before any
     */
    public long eventCursor() {
        return eventCursor.get();
    }
}
