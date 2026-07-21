package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;
import java.util.Optional;

/**
 * Typed outcome of {@link TxFlowStream#cancelItem(String, String)} (ADR 0004
 * Decision 7.5).
 * <p>
 * Item cancellation is honest about flow granularity: a shared engine
 * execution can only be cancelled whole, and an item-level cancel is
 * <b>never silently widened</b> to its flow neighbours. Cancelling an
 * in-flight member of a multi-item flow is therefore {@link Kind#REJECTED_SHARED
 * rejected}, naming the execution and the full member set so the caller can
 * decide explicitly whether to escalate to
 * {@link TxFlowStream#cancelExecution(String, String)}.
 */
public final class CancelOutcome {
    /** Disposition of an item-level cancellation request. */
    public enum Kind {
        /**
         * The item was still buffered — window buffer, a not-yet-planned
         * window, or an undispatched single-item execution — and was
         * cancelled immediately; it will never reach the engine.
         */
        CANCELLED_BUFFERED,
        /**
         * The item is the only member of an in-flight execution and the
         * cooperative cancellation signal was delivered (or recorded for
         * delivery the moment the execution's handle exists). The item
         * settles from the engine outcome.
         */
        SIGNALLED_SINGLE,
        /**
         * The item is a member of a multi-item flow that is planned or in
         * flight. Nothing was cancelled: cancelling would widen to the
         * executions' other members. {@link #executionId()} and
         * {@link #memberItemIds()} identify the shared execution; escalate
         * explicitly with {@link TxFlowStream#cancelExecution(String, String)}
         * to cancel the whole flow.
         */
        REJECTED_SHARED,
        /**
         * The item is unknown, already settled, or momentarily not in a
         * cancellable stage (mid-acceptance or mid-planning — retry applies).
         */
        UNKNOWN_OR_SETTLED
    }

    private static final CancelOutcome CANCELLED_BUFFERED_INSTANCE =
            new CancelOutcome(Kind.CANCELLED_BUFFERED, null, List.of());
    private static final CancelOutcome SIGNALLED_SINGLE_INSTANCE =
            new CancelOutcome(Kind.SIGNALLED_SINGLE, null, List.of());
    private static final CancelOutcome UNKNOWN_OR_SETTLED_INSTANCE =
            new CancelOutcome(Kind.UNKNOWN_OR_SETTLED, null, List.of());

    private final Kind kind;
    private final String executionId;
    private final List<String> memberItemIds;

    private CancelOutcome(Kind kind, String executionId, List<String> memberItemIds) {
        this.kind = kind;
        this.executionId = executionId;
        this.memberItemIds = List.copyOf(memberItemIds);
    }

    /**
     * Returns the cancellation disposition.
     *
     * @return outcome kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the shared execution's id for {@link Kind#REJECTED_SHARED}
     * outcomes.
     *
     * @return execution id of the shared flow, when the cancel was rejected
     *         as shared
     */
    public Optional<String> executionId() {
        return Optional.ofNullable(executionId);
    }

    /**
     * Returns the full member item set of the shared execution for
     * {@link Kind#REJECTED_SHARED} outcomes — every item that would be
     * affected by escalating to
     * {@link TxFlowStream#cancelExecution(String, String)}.
     *
     * @return immutable member item ids; empty for other kinds
     */
    public List<String> memberItemIds() {
        return memberItemIds;
    }

    static CancelOutcome cancelledBuffered() {
        return CANCELLED_BUFFERED_INSTANCE;
    }

    static CancelOutcome signalledSingle() {
        return SIGNALLED_SINGLE_INSTANCE;
    }

    static CancelOutcome rejectedShared(String executionId, List<String> memberItemIds) {
        return new CancelOutcome(Kind.REJECTED_SHARED, executionId, memberItemIds);
    }

    static CancelOutcome unknownOrSettled() {
        return UNKNOWN_OR_SETTLED_INSTANCE;
    }

    @Override
    public String toString() {
        return "CancelOutcome{" + kind
                + (executionId != null ? ", executionId='" + executionId + '\'' : "")
                + (!memberItemIds.isEmpty() ? ", members=" + memberItemIds : "")
                + '}';
    }
}
