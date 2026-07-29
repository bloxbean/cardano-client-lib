package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Result of {@link TxFlowStream#abort(String)}.
 * <p>
 * Abort is forced but honest about cooperativeness: buffered items are failed
 * {@link TxStreamItemStatus#CANCELLED} immediately, while in-flight executions
 * only receive a cooperative cancellation <em>signal</em> — an engine
 * execution may keep running after it. The stream releases its dispatch
 * resources at abort time but retains the completion and projection machinery
 * until every signalled execution reaches its terminal state, so signalled
 * items' receipts still settle with their real outcome (which may be
 * {@code CONFIRMED} when the execution wins the race against the signal).
 * <p>
 * One boundary case is invisible to the report: a window batch already
 * dequeued by the planning worker at abort time — no longer in the planning
 * queue, not yet enqueued on a lane — is cancelled by the dispatch path when
 * planning observes the abort. Its items settle {@code CANCELLED} typed
 * {@code TXSTREAM_ABORTED}, but they appear in neither
 * {@link #cancelledItemIds()} nor (having no signalled execution) in
 * {@link #quiescence()}. Await {@link TxFlowStream#drain()} — or the item
 * receipts themselves — when settlement of every accepted item must be
 * observed.
 */
public final class AbortReport {
    private final List<String> cancelledItemIds;
    private final List<String> signalledExecutionIds;
    private final CompletionStage<Void> quiescence;

    AbortReport(List<String> cancelledItemIds, List<String> signalledExecutionIds,
                CompletionStage<Void> quiescence) {
        this.cancelledItemIds = List.copyOf(cancelledItemIds);
        this.signalledExecutionIds = List.copyOf(signalledExecutionIds);
        this.quiescence = quiescence;
    }

    /**
     * Returns the ids of buffered items that were cancelled before dispatch.
     * Their receipts are already settled {@code CANCELLED} when the abort
     * call returns.
     *
     * @return immutable list of cancelled item ids
     */
    public List<String> cancelledItemIds() {
        return cancelledItemIds;
    }

    /**
     * Returns the execution ids that were in flight at abort time and received
     * the cooperative cancellation signal. An execution claimed for dispatch
     * but not yet started at the engine is included: it is either cancelled
     * before the engine is invoked or signalled the moment its start
     * completes — in both cases its item settles and counts toward
     * {@link #quiescence()}.
     *
     * @return immutable list of signalled execution ids
     */
    public List<String> signalledExecutionIds() {
        return signalledExecutionIds;
    }

    /**
     * Returns a stage that completes when every signalled execution has
     * reached its terminal state and its item promise has settled — full
     * stream quiescence. Completes immediately when nothing was in flight.
     * The stage is a non-cancelling view.
     *
     * @return quiescence stage
     */
    public CompletionStage<Void> quiescence() {
        return quiescence;
    }
}
