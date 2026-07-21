package com.bloxbean.cardano.client.txflow.stream;

import java.util.Optional;

/**
 * Outcome of a {@link LanePolicy#partitioned(PartitionedLanes) partitioned}
 * stream's one-time fan-out bootstrap (ADR 0004 Decision 2; Open Question 3).
 * <p>
 * When the partitioning enables the bootstrap, {@link TxFlowStream#start()} runs
 * a single idempotent engine execution — before opening for work — that splits
 * the funding source into the N lane UTXOs. The execution's idempotency claim is
 * derived from the lane addresses and seed, so it runs at most once: a restart
 * or a second stream instance <em>matches</em> the existing execution and never
 * re-splits. The report says which happened:
 * <ul>
 *   <li>{@link Outcome#RAN} — the split executed and confirmed this time;</li>
 *   <li>{@link Outcome#MATCHED} — a prior bootstrap already split the wallet;
 *       the claim matched and nothing was re-submitted;</li>
 *   <li>{@link Outcome#DISABLED} — the partitioning opted the bootstrap out
 *       (the lanes are assumed pre-funded);</li>
 *   <li>{@link Outcome#NOT_APPLICABLE} — the stream is not partitioned;</li>
 *   <li>{@link Outcome#FAILED} — the split could not complete;
 *       {@code start()} fails typed ({@code TXSTREAM_BOOTSTRAP_FAILED}) rather
 *       than dispatching items against unfunded lanes, and {@link #error()}
 *       carries the cause. On a durable stream this outcome also covers a
 *       detected configuration <em>drift</em>
 *       ({@code TXSTREAM_BOOTSTRAP_CONFIG_DRIFT}) — the funding source, seed,
 *       lane count, or lane-address list/order changed since the last run — in
 *       which case no split is submitted (see {@link PartitionedLanes}).</li>
 * </ul>
 *
 * <p>The bootstrap is durable and crash-safe only against a durable engine
 * store: with an in-memory engine it still runs exactly once per process but its
 * idempotency does not survive a real crash — the same honesty the rest of the
 * durable-vs-not surface keeps.</p>
 *
 * <p><b>Operability of a mid-flight crash.</b> The split is a normal engine
 * execution. A crash after it was submitted but before it confirmed leaves the
 * bootstrap execution non-terminal; the next {@code start()} sees that and
 * reports {@link Outcome#FAILED}, so an operator must reconcile the bootstrap
 * execution (for example {@code engine.recover(...)}) to a terminal state before
 * the stream can open for work.</p>
 */
public final class BootstrapReport {
    /** What the fan-out bootstrap did. */
    public enum Outcome {
        /** Not a partitioned stream — no bootstrap applies. */
        NOT_APPLICABLE,
        /** Partitioned, but the bootstrap was opted out; lanes assumed pre-funded. */
        DISABLED,
        /** The split executed and confirmed on this start. */
        RAN,
        /** A prior bootstrap already split the wallet; the claim matched. */
        MATCHED,
        /** The split could not complete; {@code start()} fails typed. */
        FAILED
    }

    private final Outcome outcome;
    private final String executionId;
    private final int laneCount;
    private final TxStreamException error;

    private BootstrapReport(Outcome outcome, String executionId, int laneCount,
                           TxStreamException error) {
        this.outcome = outcome;
        this.executionId = executionId;
        this.laneCount = laneCount;
        this.error = error;
    }

    /**
     * Returns what the bootstrap did.
     *
     * @return bootstrap outcome
     */
    public Outcome outcome() {
        return outcome;
    }

    /**
     * Returns the deterministic bootstrap execution identity, when a bootstrap
     * was attempted (ran, matched, or failed).
     *
     * @return bootstrap execution id, or empty when not applicable/disabled
     */
    public Optional<String> executionId() {
        return Optional.ofNullable(executionId);
    }

    /**
     * Returns the number of lanes the funding source is (or would be) split
     * into.
     *
     * @return lane count ({@code 0} when not applicable)
     */
    public int laneCount() {
        return laneCount;
    }

    /**
     * Returns the failure cause when the bootstrap {@link Outcome#FAILED}.
     *
     * @return typed bootstrap failure, or empty otherwise
     */
    public Optional<TxStreamException> error() {
        return Optional.ofNullable(error);
    }

    static BootstrapReport notApplicable() {
        return new BootstrapReport(Outcome.NOT_APPLICABLE, null, 0, null);
    }

    static BootstrapReport disabled(int laneCount) {
        return new BootstrapReport(Outcome.DISABLED, null, laneCount, null);
    }

    static BootstrapReport ran(String executionId, int laneCount) {
        return new BootstrapReport(Outcome.RAN, executionId, laneCount, null);
    }

    static BootstrapReport matched(String executionId, int laneCount) {
        return new BootstrapReport(Outcome.MATCHED, executionId, laneCount, null);
    }

    static BootstrapReport failed(String executionId, int laneCount, TxStreamException error) {
        return new BootstrapReport(Outcome.FAILED, executionId, laneCount, error);
    }
}
