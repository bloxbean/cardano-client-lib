package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.store.FlowStoreTextPolicy;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Streaming transaction workflow API executing through {@link FlowEngine}.
 * <p>
 * A stream accepts {@link TxWorkItem}s and plans them into idempotent engine
 * executions on lanes — a lane is a funding scope with at most one in-flight
 * execution at a time. By default ({@link TxStreamPlanner#perItem()}) every
 * item becomes its own single-step execution; a {@link WindowPolicy} combined
 * with a multi-item planner such as {@link TxStreamPlanner#perWindow()} groups
 * a window of accepted items into shared multi-step flows, each item riding
 * its own step of the shared execution. Item status is reported as an honest
 * projection of engine truth through receipts, {@link #getItemStatus(String)},
 * and the {@link TxStreamEventListener}; windows surface as batches through
 * {@link #getBatchStatus(String)}. Executions on different lanes run
 * concurrently (scheduled per canonical spending identity, bounded by
 * {@link Builder#maxInFlight(int)}); executions on the same lane serialize
 * FIFO.
 * <p>
 * Guarantees inherited from the engine and this design:
 * <ul>
 *   <li><b>Idempotent end to end</b> — the execution identity is derived from
 *       the item's idempotency key (for multi-item planners: from the sorted
 *       member keys, a flow-level claim), so redelivery attaches instead of
 *       double-spending; same-content redelivery returns the existing receipt
 *       and different content is a typed conflict.</li>
 *   <li><b>Honest states</b> — {@code SUBMITTED} is never asserted before the
 *       backend reports it, a known transaction hash is never dropped, and a
 *       submitted-but-unconfirmed transaction inside a terminal flow becomes
 *       {@link TxStreamItemStatus#RECOVERY_REQUIRED} rather than a false
 *       failure.</li>
 *   <li><b>Bounded settlement</b> — every accepted item's receipt settles:
 *       terminal projection, validation failure, binding failure, and
 *       cancellation are all completers of the item promise, which is also
 *       what {@link #drain()} awaits.</li>
 * </ul>
 * <p>
 * Example (single statically configured lane):
 * <pre>{@code
 * try (TxFlowStream stream = TxFlowStream.builder("payouts", engine)
 *         .lane(ResolvedLane.ofAddress("payouts", senderAddress))
 *         .executor(streamExecutor)              // optional caller-owned override
 *         .build()) {
 *     stream.start();
 *
 *     TxStreamReceipt receipt = stream.submit(
 *             TxWorkItem.builder("pay-0042")
 *                     .withTxPlan(plan)
 *                     .withIdempotencyKey("order-0042")
 *                     .build());
 *
 *     TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
 * }   // close() = graceful: drains accepted work, then releases; nothing cancelled
 * }</pre>
 * <p>
 * Multi-lane form: dynamically named lanes resolve through a
 * {@link LaneIdentityResolver} once per lane name, and items on different
 * canonical identities run concurrently:
 * <pre>{@code
 * try (TxFlowStream stream = TxFlowStream.builder("payouts", engine)
 *         .lanes(LanePolicy.explicit())
 *         .laneResolver(name -> ResolvedLane.ofAddress(name, addressForLane(name)))
 *         .maxInFlight(8)                        // global cap across lanes
 *         .executor(streamExecutor)
 *         .build()) {
 *     stream.start();
 *     stream.submit(TxWorkItem.builder("pay-0042")
 *             .withTxPlan(plan)
 *             .withLane("treasury")              // one in-flight execution per lane identity
 *             .build());
 *     stream.submit(TxWorkItem.builder("ops-0007")
 *             .withTxPlan(opsPlan)
 *             .withLane("operations")            // runs concurrently with "treasury"
 *             .build());
 * }
 * }</pre>
 */
public interface TxFlowStream extends AutoCloseable {
    /**
     * Starts the stream: begins dispatching accepted work and starts the
     * configured source. Calling this method more than once is a no-op.
     * <p>
     * For a durable stream (a {@link TxStreamStateStore} whose
     * {@link TxStreamStateStore#isDurable()} is {@code true}), {@code start()}
     * runs {@link #reattach()} <b>before</b> opening for new work, so a
     * restarted process resolves its in-flight executions against engine truth
     * — re-projecting present ones and re-dispatching absent ones — instead of
     * re-running or losing them. The report is available afterwards through
     * {@link #reattach()} (idempotent — it returns the same report).
     *
     * @throws IllegalStateException when the stream has already been closed;
     *         a closed stream cannot be restarted
     */
    void start();

    /**
     * Resolves this durable stream's persisted non-terminal item bindings
     * against engine truth and returns a report of what was recovered (ADR
     * 0004 Decision 5). Called automatically by {@link #start()} for a durable
     * stream before it opens for new work; may also be called explicitly.
     * Idempotent: the first invocation runs the recovery pass and every later
     * one returns the same report.
     * <p>
     * For each persisted non-terminal binding the stream asks the engine store
     * for the execution snapshot: a <em>present</em> snapshot means the start
     * happened, so the item is re-projected from that snapshot through the same
     * projection path as live work (transition table and authoritative
     * fast-forward apply identically — a crash-recovered item whose snapshot
     * says completed goes straight to confirmed, retaining its hash); an
     * <em>absent</em> snapshot means the start never happened, so the item is
     * re-dispatched from its persisted {@link TxStreamPlannedRecord} under the
     * same deterministic execution id (idempotent — it runs at most once). An
     * item whose execution is still running is surfaced
     * {@link TxStreamItemStatus#RECOVERY_REQUIRED} and refreshed by
     * read-through ({@link #getItemStatus(String)} / {@link #reconcile
     * (String)}); live push watching of a foreign-process execution is a later
     * iteration. Items accepted but not yet bound at the crash are lost
     * (bounded; idempotent redelivery is the answer).
     * <p>
     * {@link #start()} is the supported entry point for a full recover-then-run
     * cycle: it enables the dispatcher, runs this pass, and only then opens for
     * new work, so re-dispatched executions actually run. Calling {@code reattach()}
     * <b>before</b> {@code start()} still performs the recovery pass — present
     * executions re-project and abandoned rows are reaped immediately — but any
     * re-dispatched execution is queued and does not begin dispatching until
     * {@code start()} enables the dispatcher (it is not lost; {@code start()}
     * runs it). After {@code start()} this method is idempotent and returns the
     * same report.
     * <p>
     * A non-durable stream has nothing persisted to recover and returns an
     * empty report.
     *
     * @return report of re-attached, re-dispatched, and recovery-required items
     */
    ReattachReport reattach();

    /**
     * Runs (or returns the already-computed result of) a
     * {@link LanePolicy#partitioned(PartitionedLanes) partitioned} stream's
     * one-time fan-out bootstrap (ADR 0004 Decision 2). Called automatically by
     * {@link #start()} for a partitioned stream — before opening for work and
     * before {@link #reattach()} — so items never dispatch against unfunded
     * lanes. Idempotent: the first call runs the split (or matches an existing
     * one) and every later call returns the same report.
     * <p>
     * The bootstrap is a single engine execution that splits the funding source
     * into the N lane UTXOs; its idempotency claim makes it run at most once, so
     * a restart or a second stream instance matches and never re-splits. A
     * failed bootstrap fails {@link #start()} typed
     * ({@code TXSTREAM_BOOTSTRAP_FAILED}); {@link BootstrapReport#error()}
     * carries the cause.
     * <p>
     * For any non-partitioned stream this returns
     * {@link BootstrapReport.Outcome#NOT_APPLICABLE}.
     *
     * @return the fan-out bootstrap outcome
     */
    BootstrapReport bootstrap();

    /**
     * Returns this instance's current single-owner ownership state (ADR 0004
     * iteration 3d — multi-instance active/standby failover).
     * <p>
     * When ownership is opted in ({@link Builder#ownership(String, Duration)}),
     * two or more instances on one stream id share a durable store, exactly one
     * is {@link OwnershipStatus.State#ACTIVE} at a time (holds a currently-valid
     * epoch-fenced lease and dispatches), and the rest
     * {@link OwnershipStatus.State#STANDBY stand by} and take over on the owner's
     * crash or lease expiry. A stream with ownership disabled always reports
     * {@link OwnershipStatus.State#DISABLED} and dispatches unconditionally.
     *
     * @return this instance's ownership state snapshot
     */
    OwnershipStatus ownership();

    /**
     * Submits one work item, blocking while the bounded buffer is full.
     * <p>
     * Guard window: idempotency-key reuse and content-conflict detection hold
     * only while the item is retained — they lapse when a settled item is
     * evicted under {@link Builder#maxRetainedSettledItems(int)}. After
     * eviction the remaining guarantee is engine-request equality on the
     * claim-derived execution: an identical resubmit matches the stored
     * execution and projects the original outcome; a resubmit with different
     * transaction content fails typed at the engine; but a resubmit differing
     * only in metadata or in a lane label that resolves to the same identity
     * silently matches the stored execution — before eviction the same
     * resubmit would have been a typed content conflict.
     *
     * @param item work to accept; must not be {@code null} (a null item is a
     *        programming error and throws {@link NullPointerException}, never
     *        a typed stream exception)
     * @return receipt for tracking this item; for a live redelivery with an
     *         identical fingerprint, the existing item's receipt
     * @throws TxStreamDuplicateItemException when the item id was already
     *         accepted with different content
     * @throws TxStreamException when the stream is not accepting work, the
     *         item content is invalid ({@code TXSTREAM_INVALID_ITEM}, e.g. an
     *         idempotency key violating the store text policy), an
     *         authoritative registration write fails, or the item reuses an
     *         idempotency key already bound to a different item id
     *         ({@code TXSTREAM_IDEMPOTENCY_KEY_REUSE} — redelivery must reuse
     *         the original item id)
     */
    TxStreamReceipt submit(TxWorkItem item);

    /**
     * Submits one work item without blocking for buffer capacity.
     * <p>
     * Unlike {@link #submit(TxWorkItem)}, this method never throws for a
     * content or registration outcome: duplicate content is reported as
     * {@link EmitResult.Status#CONFLICT}, and invalid item content, an
     * authoritative registration failure, or idempotency-key reuse as
     * {@link EmitResult.Status#REJECTED} with the typed cause. A {@code null}
     * item remains a programming error and throws
     * {@link NullPointerException}.
     *
     * @param item work to accept
     * @return non-blocking emit result, including a receipt when accepted or
     *         attached, a typed conflict for duplicate content, and a typed
     *         rejection when registration fails
     */
    EmitResult trySubmit(TxWorkItem item);

    /**
     * Cancels one item with a typed outcome (ADR 0004 Decision 7.5).
     * <p>
     * A buffered item — window buffer, unplanned window, or undispatched
     * single-item execution — is cancelled immediately and never reaches the
     * engine ({@link CancelOutcome.Kind#CANCELLED_BUFFERED}). An in-flight
     * item that is the <em>only</em> member of its execution receives the
     * cooperative engine cancellation signal
     * ({@link CancelOutcome.Kind#SIGNALLED_SINGLE}) and settles from the
     * engine outcome. An item sharing a planned or in-flight multi-item flow
     * is <b>rejected</b> ({@link CancelOutcome.Kind#REJECTED_SHARED}) with
     * the execution id and the full member set — item cancellation is never
     * silently widened to flow neighbours; escalate explicitly with
     * {@link #cancelExecution(String, String)} when cancelling the whole flow
     * is intended.
     *
     * @param itemId item to cancel
     * @param reason diagnostic reason, or {@code null}
     * @return typed cancellation outcome
     */
    CancelOutcome cancelItem(String itemId, String reason);

    /**
     * Boolean convenience over {@link #cancelItem(String, String)}.
     *
     * @param itemId item to cancel
     * @param reason diagnostic reason, or {@code null}
     * @return {@code true} for {@link CancelOutcome.Kind#CANCELLED_BUFFERED}
     *         and {@link CancelOutcome.Kind#SIGNALLED_SINGLE}; {@code false}
     *         for unknown/settled items and for members of shared multi-item
     *         flows (which are never cancelled implicitly — use
     *         {@link #cancelItem(String, String)} to discover the shared
     *         execution and {@link #cancelExecution(String, String)} to
     *         escalate)
     */
    default boolean cancel(String itemId, String reason) {
        CancelOutcome.Kind kind = cancelItem(itemId, reason).kind();
        return kind == CancelOutcome.Kind.CANCELLED_BUFFERED
                || kind == CancelOutcome.Kind.SIGNALLED_SINGLE;
    }

    /**
     * Cancels one planned execution whole — the explicit escalation for
     * multi-item flows (ADR 0004 Decision 7.5).
     * <p>
     * A queued, not-yet-started execution is removed from its lane and every
     * member item settles {@link TxStreamItemStatus#CANCELLED} immediately
     * (buffered members of a dispatched flow cannot exist: flows dispatch
     * whole). An in-flight execution receives the cooperative cancellation
     * signal; its members settle from the engine outcome when the flow
     * terminates, which may be {@code CONFIRMED} for steps that won the race.
     *
     * @param executionId execution to cancel, as surfaced by
     *        {@link CancelOutcome#executionId()} or
     *        {@link TxStreamReceipt#executionId()}
     * @param reason diagnostic reason, or {@code null}
     * @return {@code true} when the execution was known and cancelled or
     *         signalled; {@code false} for unknown or already completed
     *         executions
     */
    boolean cancelExecution(String executionId, String reason);

    /**
     * Returns the latest item projection. For a
     * {@link TxStreamItemStatus#RECOVERY_REQUIRED} item this is a
     * read-through: the engine snapshot is consulted and, when it carries an
     * authoritative terminal answer, the projection is repaired before being
     * returned.
     * <p>
     * A settled item evicted under the retention cap
     * ({@link Builder#maxRetainedSettledItems(int)}) returns empty — the
     * engine's execution store remains the durable record of the execution
     * itself, and the durable stream store of iteration 2 lifts this limit.
     *
     * @param itemId caller-provided work item id
     * @return latest item result if the item is known and retained
     */
    Optional<TxStreamItemResult> getItemStatus(String itemId);

    /**
     * Forces the read-through reconciliation check for one item, typically
     * after an operator has run {@code engine.recover(...)}.
     *
     * @param itemId caller-provided work item id
     * @return post-reconciliation item result if the item is known
     */
    Optional<TxStreamItemResult> reconcile(String itemId);

    /**
     * Returns the latest batch projection for one planning batch. Batch
     * status derives from the member items (see {@link TxStreamBatchStatus});
     * the batch id is observability metadata, never engine identity.
     *
     * @param batchId stream-scoped batch id ({@code "batch-N"})
     * @return latest batch snapshot if the batch is known and retained
     */
    Optional<TxStreamBatchResult> getBatchStatus(String batchId);

    /**
     * Closes the current window immediately, handing its buffered items to
     * the planner without waiting for the count or age bound. No-op when the
     * window is empty or the stream is closed.
     */
    void flush();

    /**
     * Returns point-in-time counters derived from the item projections.
     *
     * @return stream stats snapshot
     */
    TxStreamStats getStats();

    /**
     * Stops accepting new work, flushes buffered items, and waits until every
     * accepted item's promise settles. The wait is interruptible: an
     * interrupt restores the thread's interrupt flag and throws a typed
     * {@code TXSTREAM_INTERRUPTED} {@link TxStreamException}.
     */
    void drain();

    /**
     * Drains with a deadline.
     *
     * @param timeout maximum time to wait for accepted items to settle
     * @throws TxStreamTimeoutException when the deadline elapses first
     */
    void awaitDrain(Duration timeout);

    /**
     * Reports whether the stream dispatcher is operational. A systemic
     * dispatch failure marks the stream unhealthy and fails pending items
     * typed; in-flight executions still deliver their outcomes. Health is
     * about the dispatcher, not lifecycle: a gracefully closed or aborted
     * stream remains healthy.
     *
     * @return {@code true} while the stream can dispatch work
     */
    boolean isHealthy();

    /**
     * Aborts the stream (ADR 0004 Decision 7.5). Stops accepting work, fails
     * every buffered item as {@link TxStreamItemStatus#CANCELLED}, signals
     * cooperative cancellation to every in-flight execution, and releases the
     * dispatch resources — lane queues and the work source — immediately.
     * <p>
     * Cancellation is a signal, not termination: an engine execution may keep
     * running after it and must still have somewhere to deliver its terminal
     * outcome. The stream therefore retains its completion and projection
     * machinery until every signalled execution terminates; signalled items'
     * receipts settle with their real outcome, and the report's
     * {@link AbortReport#quiescence()} completes exactly when the last one
     * does. Idempotent: repeated calls return the first abort's report, and a
     * subsequent {@link #close()} is a no-op. Reentrancy-safe: a listener or
     * completion callback that calls {@code abort} from inside the first
     * abort's own callbacks receives the same report — its contents are
     * already frozen, though the outer call may still be delivering
     * cancellations and signals when the reentrant call returns.
     *
     * @param reason diagnostic reason recorded on cancelled items and passed
     *        to the engine's cancellation signal; may be {@code null}
     * @return report of what was cancelled, what was signalled, and a
     *         quiescence stage
     */
    AbortReport abort(String reason);

    /**
     * Closes the stream gracefully: stops accepting work, drains accepted
     * items to settlement, then releases the source and stream resources.
     * Nothing is cancelled. Idempotent.
     */
    @Override
    void close();

    /**
     * Closes with a grace deadline: drains accepted work until the deadline,
     * then {@link #abort(String) aborts} the remainder and returns.
     * <p>
     * This method promises that by the time it returns no new work is
     * accepted and cancellation has been signalled to anything still in
     * flight — it cannot and does not promise execution termination at the
     * deadline. Call {@link #abort(String)} directly when the
     * {@link AbortReport#quiescence()} stage must be awaited.
     *
     * @param graceDeadline maximum time to wait for a graceful drain before
     *        aborting the remainder
     */
    void close(Duration graceDeadline);

    /**
     * Creates a stream builder executing through the supplied engine.
     *
     * @param streamId stable stream id; defines the idempotency namespace
     *        {@code stream:<streamId>}
     * @param engine caller-owned flow engine
     * @return stream builder
     */
    static Builder builder(String streamId, FlowEngine engine) {
        return new Builder(streamId, new FlowEngineGateway(engine));
    }

    /**
     * Builder for {@link TxFlowStream}. Instances are mutable and not
     * thread-safe.
     */
    final class Builder {
        final String streamId;
        final EngineGateway gateway;
        LanePolicy lanePolicy;
        LaneIdentityResolver laneResolver;
        TxWorkSource source = TxWorkSource.inMemory();
        TxStreamStateStore stateStore = TxStreamStateStore.inMemory();
        TxStreamEventListener eventListener = TxStreamEventListener.NOOP;
        TxStreamPlanner planner = TxStreamPlanner.perItem();
        WindowPolicy windowPolicy;
        ScheduledExecutorService maintenanceExecutor;
        int maxBufferSize = 1000;
        int maxInFlight = 16;
        int maxRetainedSettledItems = 10_000;
        Duration reconciliationInterval;      // null => reconciliation observer OFF (read-through only)
        int reconciliationBatchSize = 100;
        String ownerToken;                    // null => single-instance (ownership OFF)
        Duration ownershipLeaseDuration;
        Executor executor;
        Clock clock;
        final Map<String, TxFlow> templates = new LinkedHashMap<>();

        Builder(String streamId, EngineGateway gateway) {
            if (streamId == null || streamId.trim().isEmpty()) {
                throw new IllegalArgumentException("streamId cannot be null, empty, or whitespace");
            }
            this.streamId = streamId.trim();
            FlowStoreTextPolicy.requireIdentifier(
                    StreamIdentities.namespace(this.streamId), "stream idempotency namespace",
                    FlowStoreTextPolicy.MAX_NAMESPACE_BYTES);
            this.gateway = Objects.requireNonNull(gateway, "gateway");
        }

        /**
         * Configures the stream's single statically resolved lane; shorthand
         * for {@code lanes(LanePolicy.single(value))}.
         *
         * @param value resolved lane; validated at {@link #build()}
         * @return this builder
         */
        public Builder lane(ResolvedLane value) {
            this.lanePolicy = LanePolicy.single(Objects.requireNonNull(value, "lane"));
            return this;
        }

        /**
         * Configures the lane policy — one of the four
         * {@link LanePolicy} modes:
         * {@link LanePolicy#single(ResolvedLane)} (one statically configured
         * lane), {@link LanePolicy#explicit()} (dynamically named lanes,
         * requires {@link #laneResolver(LaneIdentityResolver)}),
         * {@link LanePolicy#byFundingAddress()} (lane derived from each item's
         * transaction funding source; no resolver), or
         * {@link LanePolicy#partitioned(PartitionedLanes)} (N application-owned
         * lanes with an optional one-time fan-out bootstrap).
         *
         * @param value lane policy
         * @return this builder
         */
        public Builder lanes(LanePolicy value) {
            this.lanePolicy = Objects.requireNonNull(value, "lanes");
            return this;
        }

        /**
         * Configures the resolver for dynamically named lanes. Required with
         * {@link LanePolicy#explicit()}; ignored by
         * {@link LanePolicy#single(ResolvedLane)}, which performs no
         * resolution.
         *
         * @param value lane identity resolver
         * @return this builder
         */
        public Builder laneResolver(LaneIdentityResolver value) {
            this.laneResolver = value;
            return this;
        }

        /**
         * Configures the source that feeds work into this stream.
         *
         * @param value source adapter; {@link TxWorkSource#inMemory()} when null
         * @return this builder
         */
        public Builder source(TxWorkSource value) {
            this.source = value != null ? value : TxWorkSource.inMemory();
            return this;
        }

        /**
         * Configures the stream-owned planning-metadata and projection store.
         *
         * @param value stream state store; in-memory store when null
         * @return this builder
         */
        public Builder stateStore(TxStreamStateStore value) {
            this.stateStore = value != null ? value : TxStreamStateStore.inMemory();
            return this;
        }

        /**
         * Configures structured stream event callbacks.
         *
         * @param value event listener; no-op listener when null
         * @return this builder
         */
        public Builder eventListener(TxStreamEventListener value) {
            this.eventListener = value != null ? value : TxStreamEventListener.NOOP;
            return this;
        }

        /**
         * Configures the planner converting closed windows into engine
         * executions. Defaults to {@link TxStreamPlanner#perItem()} — one
         * single-step flow per item with true per-item dedup; multi-item
         * planners trade that for grouping (flow-level dedup, ADR 0004
         * Decision 3 — see {@link TxStreamPlanner}).
         *
         * @param value planner; the per-item planner when null
         * @return this builder
         */
        public Builder planner(TxStreamPlanner value) {
            this.planner = value != null ? value : TxStreamPlanner.perItem();
            return this;
        }

        /**
         * Registers a parameterized portable {@link TxFlow} definition under a
         * template id, to be invoked by {@link TxWorkItem.Kind#TEMPLATE template
         * items} (ADR 0004, iteration 3). The definition is compiled, validated,
         * fingerprinted, and portable-encoded <b>once</b> at {@link #build()}
         * and reused for every invocation — a stream becomes a stream of
         * parameterized invocations of one compiled flow rather than one fresh
         * flow per item.
         * <p>
         * The definition should declare its parameters
         * ({@link TxFlow.Builder#addParameter(com.bloxbean.cardano.client.txflow.model.ParameterSpec)});
         * each template item supplies the values through
         * {@link TxWorkItem.Builder#withBinding(String, Object)},
         * {@link TxWorkItem.Builder#withSecureBindingReference(String, String)},
         * and {@link TxWorkItem.Builder#withSensitiveBinding(String, Object)}.
         * A <b>non-portable</b> definition is rejected here at build time (typed,
         * not per item). A durable stream must re-register the same definition
         * under the same id across restarts — it is load-bearing configuration
         * like the lane config: only the template <em>reference</em> (plus its
         * definition fingerprint) is persisted per item, and re-attach
         * re-resolves it against the re-registered template. Re-registering a
         * <b>different</b> definition under the same id fails a re-attached item
         * typed {@code TXSTREAM_TEMPLATE_DRIFT} rather than silently running the
         * wrong flow.
         * <p>
         * The {@code definition} is <b>held by reference</b>: it is validated,
         * fingerprinted, and portable-encoded at {@link #build()}, but the same
         * live object is used at every dispatch. Do not mutate it after
         * {@code build()} — doing so diverges the executed flow from the frozen
         * fingerprint/encoding and behaviour is undefined.
         * <p>
         * Template items require an explicit lane
         * ({@link LanePolicy#single(ResolvedLane)} or
         * {@link LanePolicy#explicit()}); deriving a lane from a template's bound
         * definition under {@link LanePolicy#byFundingAddress()} /
         * {@link LanePolicy#partitioned(PartitionedLanes)} is a later iteration —
         * a template item under those modes fails typed
         * {@code TXSTREAM_LANE_REQUIRED}.
         *
         * @param templateId stable, non-blank template id
         * @param definition parameterized portable flow definition
         * @return this builder
         * @throws IllegalStateException when the template id was already
         *         registered
         * @throws IllegalArgumentException when the id is blank or the definition
         *         is null
         */
        public Builder template(String templateId, TxFlow definition) {
            if (templateId == null || templateId.trim().isEmpty()) {
                throw new IllegalArgumentException("templateId cannot be null, empty, or whitespace");
            }
            Objects.requireNonNull(definition, "template definition cannot be null");
            String trimmed = templateId.trim();
            if (templates.putIfAbsent(trimmed, definition) != null) {
                throw new IllegalStateException(
                        "A template is already registered under id '" + trimmed + "'");
            }
            return this;
        }

        /**
         * Configures count/time windowing of accepted items. Without a
         * window policy every accepted item is planned immediately — a
         * window of one with no timer — which with the default per-item
         * planner is exactly the immediate-dispatch behavior of earlier
         * iterations. Time-based policies additionally require
         * {@link #maintenanceExecutor(ScheduledExecutorService)}.
         *
         * @param value window policy; {@code null} for immediate planning
         * @return this builder
         */
        public Builder window(WindowPolicy value) {
            this.windowPolicy = value;
            return this;
        }

        /**
         * Sets the caller-owned scheduler used for window-age wakeups,
         * mirroring {@code FlowEngine}'s maintenance-executor pattern: the
         * stream never constructs threads or timers. Required at
         * {@link #build()} when — and only when — a time-based
         * {@link WindowPolicy} is configured; the stream schedules one wakeup
         * per open window and cancels it when the window closes early. The
         * application retains ownership and must shut the scheduler down.
         *
         * @param value caller-owned scheduler for window-age wakeups
         * @return this builder
         */
        public Builder maintenanceExecutor(ScheduledExecutorService value) {
            this.maintenanceExecutor = value;
            return this;
        }

        /**
         * Bounds the number of accepted-but-not-yet-dispatched items.
         *
         * @param value positive buffer capacity
         * @return this builder
         */
        public Builder maxBufferSize(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("maxBufferSize must be positive");
            }
            this.maxBufferSize = value;
            return this;
        }

        /**
         * Bounds the number of concurrently in-flight executions across all
         * lanes. Defaults to {@code 16}. The per-lane rule — at most one
         * in-flight execution per canonical spending identity — always applies
         * in addition to this global cap, and lanes are scheduled fairly
         * (round-robin over ready lanes) so one lane's backlog cannot starve
         * the others.
         *
         * @param value positive global in-flight cap
         * @return this builder
         */
        public Builder maxInFlight(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("maxInFlight must be positive");
            }
            this.maxInFlight = value;
            return this;
        }

        /**
         * Bounds how many settled items the stream retains for status lookup.
         * Defaults to {@code 10_000}. Once an item settles with a final status
         * (confirmed, failed, or cancelled) it becomes evictable; when the cap
         * is exceeded the oldest-settled items are evicted FIFO from the live
         * item map and the stream state store, after which
         * {@link TxFlowStream#getItemStatus(String)} returns empty for them.
         * Unsettled items are never evicted, and
         * {@link TxFlowStream#getStats()} counters are cumulative and
         * unaffected by eviction. Terminal batches share this cap in a
         * separate FIFO: once a batch reaches a terminal status it is
         * retained under the same bound and evicted FIFO independently of the
         * item FIFO, after which
         * {@link TxFlowStream#getBatchStatus(String)} returns empty for it.
         * <p>
         * This cap is also the guard window for duplicate detection:
         * idempotency-key reuse and content-conflict checks lapse at
         * eviction. Post-eviction the guarantee is engine-request equality on
         * the claim-derived execution — an identical resubmit matches the
         * stored execution and projects the original outcome, different
         * transaction content fails typed at the engine, but a resubmit
         * differing only in metadata or in a lane label resolving to the same
         * identity silently matches where it would previously have been a
         * typed conflict. Size the cap to cover the longest realistic
         * redelivery horizon; a durable stream store (iteration 2) lifts the
         * limit.
         *
         * @param value positive retention cap for settled items
         * @return this builder
         */
        public Builder maxRetainedSettledItems(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("maxRetainedSettledItems must be positive");
            }
            this.maxRetainedSettledItems = value;
            return this;
        }

        /**
         * Enables the stream-owned reconciliation observer (ADR 0004,
         * iteration 3): a periodic background pass that <b>push-repairs</b>
         * {@link TxStreamItemStatus#RECOVERY_REQUIRED} items instead of relying
         * only on a caller poll. On each fire the stream enumerates its
         * {@code RECOVERY_REQUIRED} items — from the live projection and, for a
         * durable stream, additionally from the durable store's non-terminal set
         * (so items recovered on a prior/other instance are covered) — and runs
         * the same read-through reconcile as {@link TxFlowStream#reconcile(String)}
         * for each: an item whose engine snapshot is now terminal advances to
         * {@code CONFIRMED}/{@code FAILED}/{@code CANCELLED} with the repair
         * emitted to the {@link TxStreamEventListener}, while one still genuinely
         * recovery-required stays put for the next pass (idempotent). It is how a
         * durable {@code RECOVERY_REQUIRED} item gets repaired after an operator
         * runs {@code engine.recover(...)} with nobody polling.
         * <p>
         * <b>Opt-in and OFF by default</b>: without it, repair is read-through
         * only — {@link TxFlowStream#reconcile(String)} and
         * {@link TxFlowStream#getItemStatus(String)} remain available and are the
         * default. When set, the pass runs on the caller-owned
         * {@link #maintenanceExecutor(ScheduledExecutorService)} (the same
         * scheduler required for time-based windows); the stream owns no threads,
         * timers, or clock. A reconciliation interval therefore <b>requires</b>
         * {@code maintenanceExecutor} at {@link #build()}, exactly like a
         * time-based {@link WindowPolicy}. The observer starts with
         * {@link TxFlowStream#start()} (after re-attach for a durable stream) and
         * is cancelled on {@link TxFlowStream#close()} / {@link TxFlowStream#abort(String)}.
         *
         * @param value positive interval between reconciliation passes;
         *        {@code null} disables the observer (read-through only)
         * @return this builder
         */
        public Builder reconciliationInterval(Duration value) {
            this.reconciliationInterval = value;
            return this;
        }

        /**
         * Bounds how many {@code RECOVERY_REQUIRED} items the reconciliation
         * observer repairs per pass, so a large recovery-required set cannot
         * starve the caller-owned scheduler. Defaults to {@code 100}; items
         * beyond the cap are repaired on subsequent passes. Ignored unless
         * {@link #reconciliationInterval(Duration)} is set.
         *
         * @param value positive per-pass reconcile cap
         * @return this builder
         */
        public Builder reconciliationBatchSize(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("reconciliationBatchSize must be positive");
            }
            this.reconciliationBatchSize = value;
            return this;
        }

        /**
         * Opts this instance into single-owner active/standby dispatch ownership
         * (ADR 0004 iteration 3d — HA failover). OFF by default (single-instance:
         * the stream dispatches unconditionally, today's behaviour).
         * <p>
         * With ownership enabled, run two or more instances of the stream — same
         * {@code streamId}, distinct {@code ownerToken}s — sharing one durable
         * {@link TxStreamStateStore} and one durable {@link FlowEngine} store.
         * At {@link TxFlowStream#start() start} each instance tries to acquire an
         * epoch-fenced ownership lease: the one that wins becomes
         * {@link OwnershipStatus.State#ACTIVE} — it re-attaches to in-flight
         * executions, opens for dispatch, and renews the lease periodically
         * (every {@code leaseDuration/3}); the others become
         * {@link OwnershipStatus.State#STANDBY} and poll to take over. <b>Only the
         * current lease-holder dispatches</b> — the dispatcher refuses to submit
         * work unless this instance holds a currently-valid lease. If the ACTIVE
         * owner crashes, its lease expires, a standby's poll acquires ownership,
         * and that standby re-attaches and resumes the stream's durable
         * non-terminal items. If a stale owner resumes after being fenced (its
         * lease was taken over), its next renewal fails and it steps down
         * immediately, dispatching nothing further; in-flight engine executions it
         * already started continue and are reconciled by the new owner (never
         * aborted on step-down). Queued-but-unstarted work is settled
         * {@code CANCELLED} ({@code TXSTREAM_OWNERSHIP_LOST}) on step-down — a
         * bounded loss; because those item ids are terminally cancelled in the
         * shared durable store, recover the work by resubmitting it to the new
         * owner <b>under a new item id</b> (the same item id is rejected as a
         * duplicate). This settlement covers work in <em>any</em> pre-dispatch
         * position — window buffer, planning queue, a window mid-planning at
         * fence time, or a lane queue — never stranding an unsettled item on a
         * standby.
         * <p>
         * <b>A standby is paused, not closed:</b> while STANDBY, blocking
         * {@link TxFlowStream#submit} refuses typed {@code TXSTREAM_CLOSED}, but
         * the non-blocking {@link TxFlowStream#trySubmit} reports
         * {@link EmitResult.Status#PAUSED} — a temporary, retryable condition —
         * so a source adapter (for example
         * {@link TxWorkSource#fromPublisher(java.util.concurrent.Flow.Publisher)})
         * parks its items and resumes delivering them if this instance reclaims
         * ownership, instead of tearing down permanently. A stepped-down
         * instance also keeps its reconciliation observer running: a standby
         * continues <em>read-only</em> reconciliation passes (repair-only store
         * writes, CAS-arbitrated against the active owner's; a standby never
         * dispatches).
         * <p>
         * <b>Requirements (validated at {@link #build()}):</b> a durable
         * {@code TxStreamStateStore}, a durable {@code FlowEngine} store
         * ({@code engine.capabilities().durableExecution()}), and a
         * {@link #maintenanceExecutor(ScheduledExecutorService)} (the periodic
         * renewal/acquire-poll runs on the caller-owned scheduler — the stream
         * owns no threads/timers/clock). The {@code ownerToken} must be a stable
         * per-instance identity the caller supplies (e.g. hostname + PID); it is
         * required, not derived, so it is testable and never depends on
         * {@code Date}/random in stream code.
         * <p>
         * <b>Future extension (not this iteration):</b> active/active
         * lane-partitioned ownership — multiple instances each owning a disjoint
         * subset of lanes — which needs the engine's cross-process resource
         * contention path (P3) and per-lane leases.
         *
         * @param ownerToken stable, non-blank per-instance identity
         * @param leaseDuration positive ownership lease duration (renewed at a
         *        fraction of it while ACTIVE)
         * @return this builder
         */
        public Builder ownership(String ownerToken, Duration leaseDuration) {
            this.ownerToken = ownerToken;
            this.ownershipLeaseDuration = leaseDuration;
            return this;
        }

        /**
         * Sets the executor the dispatcher runs on. The application retains
         * ownership and must shut it down when appropriate; the stream never
         * creates or owns threads, mirroring {@link FlowEngine}. Dispatch for
         * different lanes is submitted as independent tasks, so a
         * multi-threaded executor lets lanes dispatch concurrently.
         * When omitted from a stream built with
         * {@link TxFlowStream#builder(String, FlowEngine)}, the stream inherits
         * the engine's caller-owned execution executor without taking
         * ownership. A custom engine gateway that cannot expose a dispatcher
         * still requires this method.
         *
         * @param value caller-owned executor
         * @return this builder
         */
        public Builder executor(Executor value) {
            this.executor = value;
            return this;
        }

        /**
         * Sets the wall clock used for projection timestamps.
         *
         * @param value wall clock; defaults to {@link Clock#systemUTC()}
         * @return this builder
         */
        public Builder clock(Clock value) {
            this.clock = value;
            return this;
        }

        /**
         * Validates the lane configuration and builds the stream.
         *
         * @return configured stream, not yet started
         */
        public TxFlowStream build() {
            if (lanePolicy == null) {
                throw new IllegalStateException(
                        "A lane policy is required: configure lane(ResolvedLane.ofAddress(...)), "
                                + "lanes(LanePolicy.single(...)), or lanes(LanePolicy.explicit()) "
                                + "with laneResolver(...)");
            }
            if (lanePolicy.isExplicit() && laneResolver == null) {
                throw new IllegalStateException(
                        "LanePolicy.explicit() requires laneResolver(LaneIdentityResolver)");
            }
            if (windowPolicy != null && windowPolicy.isTimeBased() && maintenanceExecutor == null) {
                throw new IllegalStateException(
                        "maintenanceExecutor(ScheduledExecutorService) must be supplied for a "
                                + "time-based WindowPolicy so window-age wakeups run on a "
                                + "caller-owned scheduler (the stream never owns threads)");
            }
            if (windowPolicy != null && !windowPolicy.isTimeBased()
                    && windowPolicy.getMaxItems() > maxBufferSize) {
                // Buffer permits are held until a window is planned, so a
                // count-only window larger than the buffer can never fill:
                // blocking submit would wedge forever with no age bound to
                // close the window.
                throw new IllegalStateException(
                        "A count-only WindowPolicy's maxItems (" + windowPolicy.getMaxItems()
                                + ") cannot exceed maxBufferSize (" + maxBufferSize
                                + "): the window could never fill and blocking submit would"
                                + " wedge; raise maxBufferSize or add a time bound"
                                + " (WindowPolicy.countOrTime)");
            }
            if (reconciliationInterval != null) {
                if (reconciliationInterval.isZero() || reconciliationInterval.isNegative()) {
                    throw new IllegalStateException(
                            "reconciliationInterval must be positive when set");
                }
                if (maintenanceExecutor == null) {
                    // Mirror the time-window rule: the periodic reconciliation
                    // pass runs on the caller-owned scheduler; the stream owns
                    // no threads or timers. Read-through reconcile(...) /
                    // getItemStatus(...) remain available without it.
                    throw new IllegalStateException(
                            "reconciliationInterval(...) requires maintenanceExecutor"
                                    + "(ScheduledExecutorService) so the periodic reconciliation"
                                    + " pass runs on a caller-owned scheduler (the stream never"
                                    + " owns threads); read-through reconcile(...) /"
                                    + " getItemStatus(...) remain available without it");
                }
            }
            if (stateStore.isDurable() && !gateway.durableExecution()) {
                // Restart re-attach reasons "no stored execution ⇒ it never
                // ran"; pairing durable stream state with an in-memory engine
                // would re-dispatch executions that already ran before the
                // crash — a transaction duplicator. A non-durable stream store
                // stays legal with any engine.
                throw new IllegalStateException(
                        "a durable stream store requires a durable FlowEngine store; pairing"
                                + " durable stream state with an in-memory engine would re-dispatch"
                                + " executions that already ran (configure the engine with a"
                                + " FlowExecutionStore, or use a non-durable stream store)");
            }
            if (ownerToken != null || ownershipLeaseDuration != null) {
                // Ownership is opt-in HA active/standby: it requires the same
                // durability family as durable mode (so a standby can re-attach to
                // the durable in-flight items on failover) plus a caller-owned
                // maintenance scheduler for the lease renewal / acquire-poll (the
                // stream owns no threads/timers/clock).
                if (ownerToken == null || ownerToken.trim().isEmpty()) {
                    throw new IllegalStateException(
                            "ownership(ownerToken, leaseDuration) requires a non-blank ownerToken"
                                    + " (a stable per-instance identity the caller supplies)");
                }
                if (ownershipLeaseDuration == null || ownershipLeaseDuration.isZero()
                        || ownershipLeaseDuration.isNegative()) {
                    throw new IllegalStateException(
                            "ownership(...) requires a positive leaseDuration");
                }
                if (!stateStore.isDurable()) {
                    throw new IllegalStateException(
                            "ownership(...) requires a durable TxStreamStateStore so a standby can"
                                    + " re-attach to the stream's durable in-flight items on"
                                    + " failover (use TxStreamStateStore.inMemoryDurable() or the"
                                    + " relational store)");
                }
                if (!stateStore.supportsOwnership()) {
                    // A durable store that does NOT implement the epoch-fenced
                    // ownership-lease trio hands out no lease, so every instance
                    // would stand by forever (accept nothing, dispatch nothing)
                    // with no error. Reject it here rather than wedge silently.
                    throw new IllegalStateException(
                            "ownership(...) requires a TxStreamStateStore that supports ownership"
                                    + " leases (supportsOwnership() == true); the configured durable"
                                    + " store does not implement the epoch-fenced acquire/renew/"
                                    + "release trio, so every instance would stay STANDBY and"
                                    + " dispatch nothing (use TxStreamStateStore.inMemoryDurable()"
                                    + " or the relational store)");
                }
                if (!gateway.durableExecution()) {
                    throw new IllegalStateException(
                            "ownership(...) requires a durable FlowEngine store"
                                    + " (engine.capabilities().durableExecution()); active/standby"
                                    + " failover re-attaches to durable executions");
                }
                if (maintenanceExecutor == null) {
                    throw new IllegalStateException(
                            "ownership(...) requires maintenanceExecutor(ScheduledExecutorService)"
                                    + " so the periodic lease renewal / acquire-poll runs on a"
                                    + " caller-owned scheduler (the stream never owns threads)");
                }
            }
            if (executor == null) {
                executor = gateway.executionExecutor().orElseThrow(() ->
                        new IllegalStateException(
                                "executor must be supplied because the configured engine gateway "
                                        + "does not expose one; configure executor(Executor), or "
                                        + "build the stream from a FlowEngine to inherit its "
                                        + "caller-owned execution executor"));
            }
            return new EngineTxFlowStream(this);
        }
    }
}
