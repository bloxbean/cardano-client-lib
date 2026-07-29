package com.bloxbean.cardano.client.txflow.stream;

/**
 * Planner SPI (ADR 0004 Decision 6): converts one closed window of accepted
 * items into the engine executions that will run them.
 * <p>
 * Every dispatch is routed through a planner — the stream has no other path
 * to the engine. The default {@link #perItem()} planner emits one single-step
 * flow per item; {@link #perWindow()} emits one flow per lane group within
 * the window; {@link #batching(BatchingOptions)} merges compatible
 * payment-shaped items in a lane into <em>fewer</em> transactions (item status
 * becomes transaction-granular); custom planners may group, split, or reshape
 * work as long as they honor the contract below.
 * <p>
 * <b>Determinism is an SPI obligation.</b> The same window items — in any
 * submission order — must produce a byte-identical plan: identical flow ids
 * and step ids (derived through {@link TxStreamPlanningContext#ids()}),
 * identical claim keys, identical transaction content and member mappings.
 * The engine fingerprints the whole compiled request under the flow's
 * idempotency claim, so a non-deterministic planner converts legitimate
 * redeliveries into {@code TXFLOW_IDEMPOTENCY_CONFLICT} failures instead of
 * idempotent matches. Planning must be pure: no I/O, no clocks, no
 * randomness.
 * <p>
 * <b>Idempotency scope (ADR 0004 Decision 3).</b> {@code perItem()} gives
 * true per-item dedup: one item, one flow, one claim keyed by the item's
 * idempotency key — a redelivered item matches its existing execution.
 * {@code perWindow()}, {@code batching(...)}, and other multi-item planners
 * give <em>flow-level</em> dedup only: the claim covers the exact member set,
 * so an identical window resubmission matches, but a single redelivered item
 * landing in a differently-composed window is a new claim and will run again.
 * Sources that redeliver individual items must use {@code perItem()} or dedup
 * upstream. Under {@code batching(...)} this is especially sharp — a re-batched
 * payment is a real second on-chain payment, not an independent retry; see
 * {@link BatchingOptions}.
 * <p>
 * <b>Validation and isolation.</b> The stream validates every plan before
 * dispatch — duplicate/foreign mappings, unmapped steps, and invalid flow
 * claim keys reject the plan typed {@code TXSTREAM_PLAN_INVALID}; a flow
 * whose members span lanes rejects it typed {@code TXSTREAM_PLAN_CROSS_LANE};
 * items omitted from the plan fail typed {@code TXSTREAM_PLAN_OMITTED} while
 * the rest proceeds. A planner that throws fails only that window's items
 * typed {@code TXSTREAM_PLANNER_FAILED}; it never kills the stream. Custom
 * planners are responsible for keeping each planned flow's transactions
 * inside its lane's funding scope; the built-in planners inherit the
 * mechanically enforced items.
 * <p>
 * <b>Step-sharing is a planner-owned obligation the stream CANNOT verify.</b>
 * A planner MAY map several items to one step (map several
 * {@link TxStreamPlannedItem}s at the same {@code stepId}) — that is
 * <em>transaction-granular batching</em>: those items ride one transaction and
 * share its fate. When a planner does this it OWNS the guarantee that the
 * shared step's transaction actually serves <em>every</em> item mapped to it
 * (pays each one what its item represents). The stream projects all items on a
 * shared step from that step's single outcome — same status, same transaction
 * hash — and has <em>no way to inspect an arbitrary transaction's semantics</em>
 * to confirm it pays each mapped item. A mis-mapped shared step therefore
 * reports items {@code CONFIRMED} (with the step's transaction hash) whose
 * payment the transaction never actually made. The built-in
 * {@link #batching(BatchingOptions)} planner builds the shared step's
 * transaction from exactly the members mapped to it, so it is correct by
 * construction; a custom planner that shares steps must uphold the same
 * invariant itself — the stream validates the <em>mapping</em>, never the
 * <em>payment</em>.
 */
public interface TxStreamPlanner {
    /**
     * Plans one closed window.
     *
     * @param context window items, in acceptance order, plus the stable
     *        identity factory
     * @return the executions to dispatch for this window
     */
    TxStreamPlan plan(TxStreamPlanningContext context);

    /**
     * Returns the default planner: one single-step flow per item, claimed
     * under the item's own idempotency key — maximum lane parallelism and
     * true per-item dedup.
     *
     * @return per-item planner
     */
    static TxStreamPlanner perItem() {
        return BuiltInPlanners.PER_ITEM;
    }

    /**
     * Returns the windowing planner: one flow per <em>lane group</em> within
     * the window, each member item riding its own step, claimed under a key
     * derived from the sorted member idempotency keys. Flow-level dedup only
     * — see the class javadoc.
     *
     * @return per-window planner
     */
    static TxStreamPlanner perWindow() {
        return BuiltInPlanners.PER_WINDOW;
    }

    /**
     * Returns the batching planner with the conservative default options
     * ({@link BatchingOptions#defaults()}).
     *
     * @return batching planner with default options
     */
    static TxStreamPlanner batching() {
        return batching(BatchingOptions.defaults());
    }

    /**
     * Returns the batching planner: within each lane group it merges compatible
     * payment-shaped items into <em>one</em> transaction (one flow, one merged
     * step, every member mapped to that step), capping group size by
     * {@link BatchingOptions#maxItemsPerTransaction()} and splitting overflow
     * into multiple merged flows. Items that are not payment-shaped run as their
     * own single-item flows (or fail the window typed, per the options) and are
     * never merged.
     * <p>
     * <b>Flow-level dedup only, and the sharpest footgun in the stream.</b>
     * A merged batch claims the engine under its exact member set, so an
     * identical batch redelivered whole matches; but a single item redelivered
     * into a differently-composed batch is a new claim, and — unlike
     * {@code perWindow()} where items are independent transactions — a re-batched
     * payment is a <b>real second on-chain payment</b>. Per-item exactly-once
     * requires {@code perItem()} or upstream dedup; per-item dedup inside a
     * merged flow awaits the multi-claim engine extension (Decision 6 / Open
     * Question 2). See {@link BatchingOptions} for the full contract.
     *
     * @param options batching configuration
     * @return batching planner
     */
    static TxStreamPlanner batching(BatchingOptions options) {
        return BuiltInPlanners.batching(options);
    }
}
