package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.codec.FlowFormat;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion;
import com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions;
import com.bloxbean.cardano.client.txflow.codec.PortableFlowValidator;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.exec.FlowError;
import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreTextPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/**
 * {@link TxFlowStream} implementation on the {@code FlowEngine} durable
 * runtime.
 *
 * <p>Dispatch is a work-scheduling loop on the caller-owned executor — the
 * stream never constructs threads or timers. Accepted items accumulate in a
 * window (a window of one with no timer when no {@link WindowPolicy} is
 * configured); a closed window is handed to the configured
 * {@link TxStreamPlanner}, the plan is validated, and each
 * {@link PlannedExecution} queues FIFO on its lane, where a lane is keyed by
 * its {@link ResolvedLane#canonicalSpendingIdentity()} (never its label): at
 * most one execution is in flight per canonical identity, alias lane names
 * resolving to one identity share a single FIFO, and different identities
 * dispatch concurrently — each claimed execution is submitted as its own
 * executor task — bounded by the global {@code maxInFlight} cap with
 * round-robin fairness over ready lanes. Authoritative planning writes (item
 * registry, per-member execution bindings) fail closed; listener callbacks,
 * batch records, and denormalized projection writes are isolated so they can
 * never kill the dispatcher or wedge {@code drain()}.</p>
 */
final class EngineTxFlowStream implements TxFlowStream {
    private static final Logger log = LoggerFactory.getLogger(EngineTxFlowStream.class);

    /**
     * Sequence floor a re-attached projection is seeded at when the durable
     * store cannot report the last stored sequence
     * ({@link TxStreamStateStore#lastProjectionSequence} returned empty). An
     * authoritative re-attach write must DOMINATE any stored sequence so the
     * durable projection reaches its terminal status (BUG-1); a store that
     * reports the real stored sequence (the durable contract) gets the exact
     * dominating value instead. The floor is far above any realistic per-item
     * transition count, so live advances continuing from it never wrap.
     */
    private static final long REATTACH_SEQUENCE_FLOOR = 1L << 42;

    private final String streamId;
    private final String namespace;
    private final EngineGateway gateway;
    private final LanePolicy.Mode laneMode;             // which lane-assignment mode
    private final ResolvedLane staticLane;              // null unless LanePolicy.single(...)
    private final LaneIdentityResolver laneResolver;    // required under explicit()
    private final PartitionedLanes partitioning;        // non-null under partitioned()
    private final List<ResolvedLane> partitionLanes;    // precomputed N lanes; empty otherwise
    private final TxWorkSource source;
    private final TxStreamStateStore stateStore;
    private final TxStreamEventListener listener;
    private final TxStreamPlanner planner;
    private final WindowPolicy windowPolicy;            // null => immediate windows of one
    private final ScheduledExecutorService maintenanceExecutor; // null unless time-based window
    private final Executor executor;
    private final Clock clock;
    private final int maxInFlight;
    private final int maxRetainedSettledItems;
    /** Reconciliation observer interval; {@code null} unless opted in (read-through only). */
    private final Duration reconciliationInterval;
    private final int reconciliationBatchSize;
    private final TxFlowCodec codec = TxFlowCodec.standard();
    private final StableIdFactory idFactory;
    /**
     * Pre-registered parameterized templates by id (ADR 0004, iteration 3):
     * each is compiled, validated, fingerprinted, and portable-encoded once at
     * build time and reused by every {@link TxWorkItem.Kind#TEMPLATE} item.
     */
    private final Map<String, RegisteredTemplate> templates;
    /**
     * Fast path: the built-in per-item planner with no window configured is
     * planned inline on the accepting thread — a pure function over the
     * accept-time seeds — preserving the exact immediate-dispatch semantics
     * (and deterministic lane FIFO acceptance order) of the pre-window
     * iterations. Every other planner/window combination plans on the
     * dispatch executor through the serialized planning pump.
     */
    private final boolean inlinePlanning;

    private final ConcurrentMap<String, ItemState> items = new ConcurrentHashMap<>();
    /** Claim key -> owning item id; enforces TXSTREAM_IDEMPOTENCY_KEY_REUSE. */
    private final ConcurrentMap<String, String> itemIdByClaimKey = new ConcurrentHashMap<>();
    /** Live (queued or in-flight) executions by execution id. */
    private final ConcurrentMap<String, ExecutionState> executionsById = new ConcurrentHashMap<>();
    /** Batch projections by batch id. */
    private final ConcurrentMap<String, BatchState> batches = new ConcurrentHashMap<>();
    /**
     * Stream-scoped monotonic batch counter. Batch ids ("batch-N") are
     * observability metadata only and are NEVER part of engine identity:
     * execution/flow/step ids derive exclusively from item idempotency keys.
     */
    private final AtomicLong batchCounter = new AtomicLong();
    private final Semaphore capacity;
    private final AtomicBoolean pumpActive = new AtomicBoolean();
    private final AtomicBoolean planningActive = new AtomicBoolean();
    private final Object stateLock = new Object();

    /** Window buffer and timer state, all guarded by {@link #stateLock}. */
    private final ArrayDeque<ItemState> windowBuffer = new ArrayDeque<>();
    private Instant windowOpenedAt;
    private long windowEpoch;
    private ScheduledFuture<?> windowTimer;

    /**
     * Reconciliation-observer scheduling state, guarded by {@link #stateLock}.
     * The epoch invalidates a stale/late fire after close/abort (mirroring the
     * window-timer epoch): a fire whose epoch no longer matches is a no-op and
     * never re-arms.
     */
    private long reconciliationEpoch;
    private ScheduledFuture<?> reconciliationTimer;
    /**
     * Monotonic count of reconciliation passes run, used to alternate which
     * phase (live-map scan vs durable-absent discovery) gets the per-fire budget
     * first (F2 fairness — see {@link #runReconciliationPass()}). Only ever read
     * and incremented on the maintenance scheduler, one pass at a time.
     */
    private final AtomicLong reconciliationPassCount = new AtomicLong();

    /** Closed windows awaiting planning, guarded by {@link #stateLock}. */
    private final ArrayDeque<BatchState> planningQueue = new ArrayDeque<>();

    /** Lane dispatch state, all guarded by {@link #stateLock}. */
    private final Map<String, LaneQueue> laneQueues = new HashMap<>();
    private final ArrayDeque<LaneQueue> readyRing = new ArrayDeque<>();
    private int inFlightCount;

    /** Successful lane resolutions, cached once per name; guarded by {@link #laneLock}. */
    private final Object laneLock = new Object();
    private final ConcurrentMap<String, ResolvedLane> laneByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> identityByFundingScope = new ConcurrentHashMap<>();

    /** Retention FIFOs over final-settled items/batches; guarded by {@link #retentionLock}. */
    private final Object retentionLock = new Object();
    private final ArrayDeque<ItemState> settledFifo = new ArrayDeque<>();
    private final ArrayDeque<BatchState> settledBatchFifo = new ArrayDeque<>();

    /** Cumulative projection-derived counters, unaffected by eviction. */
    private final AtomicLong acceptedCount = new AtomicLong();
    private final AtomicLong plannedCount = new AtomicLong();
    private final AtomicLong submittedCount = new AtomicLong();
    private final AtomicLong confirmedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong cancelledCount = new AtomicLong();
    private final AtomicLong recoveryRequiredCount = new AtomicLong();

    private final Object abortLock = new Object();
    private volatile AbortReport abortReport;
    private volatile String abortReason;

    private final Object reattachLock = new Object();
    private volatile ReattachReport reattachReport;

    private final Object bootstrapLock = new Object();
    private volatile BootstrapReport bootstrapReport;

    private volatile boolean started;
    private volatile boolean accepting;
    private volatile boolean closed;
    private volatile boolean aborted;
    private volatile boolean healthy = true;
    /**
     * Whether the configured source has been started; a standby that becomes
     * ACTIVE (or re-activates after a fenced step-down) starts it at most once.
     */
    private volatile boolean sourceStarted;
    /**
     * Whether {@link #openForWork()} has run its once-only bits (onStreamStarted +
     * the reconciliation observer). Guards against a reclaim after a fenced
     * step-down re-firing them / double-arming the observer. Guarded by
     * {@link #stateLock}.
     */
    private boolean openedForWorkOnce;

    // ------------------------------------------------------------------
    // Single-owner ownership (ADR 0004 iteration 3d — active/standby failover).
    // OFF unless ownerToken != null (validated at build()). State guarded by
    // stateLock; the fields are volatile so the dispatch gate can read them
    // outside the lock. Only the current epoch-holder dispatches.
    // ------------------------------------------------------------------
    private final String ownerToken;                 // null unless ownership enabled
    private final Duration ownershipLeaseDuration;   // null unless ownership enabled
    private final boolean ownershipEnabled;
    private volatile OwnershipStatus.State ownershipState;
    private volatile StreamOwnershipLease ownershipLease;
    /** Scheduling epoch invalidating a stale/late ownership tick after shutdown. */
    private long ownershipSchedEpoch;
    private ScheduledFuture<?> ownershipTimer;
    /** Sink handed to the source, exposing the real non-blocking trySubmit. */
    private final TxWorkSink sink = new TxWorkSink() {
        @Override
        public TxStreamReceipt submit(TxWorkItem item) {
            return EngineTxFlowStream.this.submit(item);
        }

        @Override
        public EmitResult trySubmit(TxWorkItem item) {
            return EngineTxFlowStream.this.trySubmit(item);
        }
    };
    /**
     * Dispatch gate for the {@link LanePolicy#partitioned(PartitionedLanes)}
     * mode: no partitioned execution may dispatch onto a lane until the fan-out
     * bootstrap has funded the lanes (FINDING-1). It is meaningless for the
     * other three modes — {@link #bootstrapGateOpen()} treats them as satisfied
     * — and stays {@code false} for a partitioned stream until {@link #start()}
     * confirms the bootstrap {@code RAN}/{@code MATCHED}/was {@code DISABLED}. A
     * failed bootstrap leaves it {@code false}, so a later {@code close()} /
     * {@code drain()} that re-enters {@link #schedulePump()} still refuses to
     * dispatch queued (e.g. re-attach re-dispatched) work against unfunded lanes.
     */
    private volatile boolean bootstrapSatisfied;

    EngineTxFlowStream(TxFlowStream.Builder builder) {
        this.streamId = builder.streamId;
        this.namespace = StreamIdentities.namespace(builder.streamId);
        this.gateway = builder.gateway;
        this.laneMode = builder.lanePolicy.mode();
        this.staticLane = builder.lanePolicy.staticLane();
        this.laneResolver = builder.laneResolver;
        this.partitioning = builder.lanePolicy.partitioning();
        this.partitionLanes = buildPartitionLanes(builder.streamId, partitioning);
        this.source = builder.source;
        this.stateStore = builder.stateStore;
        this.listener = builder.eventListener;
        this.planner = builder.planner;
        this.windowPolicy = builder.windowPolicy;
        this.maintenanceExecutor = builder.maintenanceExecutor;
        this.executor = builder.executor;
        this.clock = builder.clock != null ? builder.clock : Clock.systemUTC();
        this.maxInFlight = builder.maxInFlight;
        this.maxRetainedSettledItems = builder.maxRetainedSettledItems;
        this.reconciliationInterval = builder.reconciliationInterval;
        this.reconciliationBatchSize = builder.reconciliationBatchSize;
        this.ownerToken = builder.ownerToken;
        this.ownershipLeaseDuration = builder.ownershipLeaseDuration;
        this.ownershipEnabled = builder.ownerToken != null;
        this.ownershipState = ownershipEnabled
                ? OwnershipStatus.State.STANDBY : OwnershipStatus.State.DISABLED;
        this.capacity = new Semaphore(builder.maxBufferSize);
        this.idFactory = StreamIdentities.idFactory(namespace);
        this.inlinePlanning = builder.planner == BuiltInPlanners.PER_ITEM
                && builder.windowPolicy == null;
        this.templates = compileTemplates(builder.templates);
    }

    /**
     * Compiles every registered template once, at build time: validates
     * portability (a non-portable template is rejected here, typed at build time
     * rather than per item), portable-encodes it, fingerprints the encoding, and
     * captures the definition's terminal step as the projection/binding anchor.
     */
    private Map<String, RegisteredTemplate> compileTemplates(Map<String, TxFlow> declared) {
        if (declared.isEmpty()) {
            return Map.of();
        }
        Map<String, RegisteredTemplate> compiled = new LinkedHashMap<>();
        for (Map.Entry<String, TxFlow> entry : declared.entrySet()) {
            String templateId = entry.getKey();
            TxFlow definition = entry.getValue();
            if (definition.getSteps().isEmpty()) {
                throw new IllegalStateException(
                        "Template '" + templateId + "' declares no steps");
            }
            List<FlowDiagnostic> diagnostics = PortableFlowValidator.validate(definition);
            if (!diagnostics.isEmpty()) {
                throw new IllegalStateException(
                        "Template '" + templateId + "' is not portable: " + diagnostics);
            }
            String portableFlow;
            try {
                portableFlow = codec.write(definition,
                        FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
            } catch (RuntimeException encodeFailure) {
                throw new IllegalStateException(
                        "Template '" + templateId + "' has no portable encoding: "
                                + encodeFailure.getMessage(), encodeFailure);
            }
            List<FlowStep> steps = definition.getSteps();
            String terminalStepId = steps.get(steps.size() - 1).getId();
            compiled.put(templateId, new RegisteredTemplate(templateId, definition, portableFlow,
                    StreamIdentities.fingerprint40(portableFlow), terminalStepId));
        }
        return Map.copyOf(compiled);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void start() {
        synchronized (stateLock) {
            if (closed) throw new IllegalStateException("Stream is closed");
            if (started) return;
            started = true;
            // accepting stays false until the fan-out bootstrap has funded the
            // lanes and re-attach has recovered in-flight executions — a
            // partitioned/durable start must resolve both before it opens for
            // new work.
        }
        if (laneMode == LanePolicy.Mode.PARTITIONED) {
            // Fund the N lanes before anything (including re-attach's
            // re-dispatch) can run against them. A failed bootstrap fails start
            // typed rather than dispatching items onto unfunded lanes.
            //
            // MINOR(ii): with ownership enabled, the fan-out bootstrap runs on
            // EVERY instance at start() — before the ownership decision below —
            // including instances that go on to STAND BY. This is by design and
            // safe: the bootstrap is a single idempotent engine execution keyed by
            // a deterministic claim, so a standby's run MATCHes the active owner's
            // (never a second split / re-drain of the funding wallet). The only
            // cost is one extra idempotent engine round-trip per standby at start;
            // gating it to the eventual ACTIVE owner would also have to re-run it
            // on failover (activateOnFailover does not), so the per-instance
            // idempotent bootstrap is the clean choice.
            BootstrapReport report = bootstrap();
            if (report.outcome() == BootstrapReport.Outcome.FAILED) {
                TxStreamException failure = report.error().orElseGet(() -> new TxStreamException(
                        "TXSTREAM_BOOTSTRAP_FAILED",
                        "Fan-out bootstrap failed for stream '" + streamId + "'"));
                // FINDING-1: settle any pending partitioned work — buffered items
                // and, crucially, executions a standalone reattach() re-dispatched
                // into the lane queues before this start() ran the bootstrap — so a
                // later close()/drain() returns instead of hanging. The dispatch
                // gate stays closed (bootstrapSatisfied == false), so none of them
                // can ever dispatch onto the unfunded lanes.
                failPendingPartitioned(failure);
                throw failure;
            }
            // Bootstrap ran/matched or was disabled: the lanes are funded (or the
            // caller asserts they are). Open the dispatch gate BEFORE re-attach so
            // its re-dispatched executions and the start()-final pump can run.
            bootstrapSatisfied = true;
        }
        // Single-owner ownership decision (ADR 0004 iteration 3d). When ownership
        // is enabled, only the ACTIVE lease-holder opens for dispatch; a standby
        // arms the acquire-poll and defers re-attach/dispatch until it takes over.
        boolean active = !ownershipEnabled || acquireOwnershipAtStart();
        if (active) {
            openForWork();
            if (ownershipEnabled) {
                safeListener(() -> listener.onOwnershipAcquired(ownership()));
            }
        }
    }

    /**
     * Opens this instance for dispatch: re-attaches to durable in-flight items
     * (for a durable stream), opens acceptance, starts the source, kicks the
     * pump, and arms the reconciliation observer. Called from {@link #start()}
     * when this instance is active (ownership disabled, or acquired at start) and
     * from {@link #activateOnFailover()} when a standby takes over — which can run
     * more than once for one instance (ACTIVE → fenced STANDBY → reclaimed
     * ACTIVE). Idempotent across reclaims: the source starts at most once, and the
     * once-only bits ({@code onStreamStarted}, the reconciliation observer) fire
     * only on the first open so a reclaim never double-arms them. Re-attach is
     * memoized within one ACTIVE tenure but the memo is RESET on a fenced
     * step-down ({@link #stepDownFenced}), so a reclaim genuinely re-scans durable
     * state — picking up work an interim owner planned+persisted while this
     * instance stood by (FINDING A); a re-scan is safe (deterministic execution
     * ids + the {@code !items.containsKey(...)} guard in {@code runReattach}).
     */
    private void openForWork() {
        if (stateStore.isDurable()) {
            reattach();
        }
        boolean firstOpen;
        synchronized (stateLock) {
            accepting = true;
            firstOpen = !openedForWorkOnce;
            openedForWorkOnce = true;
        }
        // Hand the source a sink exposing BOTH the blocking submit and the real
        // non-blocking trySubmit, so a backpressure-aware source (for example
        // TxWorkSource.fromPublisher) sees FULL/PAUSED/CLOSED instead of
        // blocking.
        startSourceOnce();
        // Reactivation nudge (composition probe 2): a source that parked items
        // on a PAUSED disposition while this instance was an ownership STANDBY
        // (FlowWorkSource holds them in its bounded deque) retries them now that
        // acceptance is open again. A default/no-op resume() makes this free for
        // every other source, and on the very first open it is a harmless
        // no-work drain.
        source.resume();
        schedulePump();
        if (firstOpen) {
            safeListener(() -> listener.onStreamStarted(streamId));
            // Start the reconciliation observer AFTER re-attach (for a durable
            // stream) so its first pass sees the recovered projections; only once,
            // so a reclaim after a fenced step-down does not double-arm it.
            startReconciliationObserver();
        }
    }

    /** Starts the configured source at most once. */
    private void startSourceOnce() {
        boolean doStart;
        synchronized (stateLock) {
            doStart = !sourceStarted && !closed && !aborted;
            if (doStart) sourceStarted = true;
        }
        if (doStart) source.start(sink);
    }

    // ------------------------------------------------------------------
    // Single-owner ownership (ADR 0004 iteration 3d — active/standby failover)
    // ------------------------------------------------------------------

    @Override
    public OwnershipStatus ownership() {
        synchronized (stateLock) {
            return ownershipSnapshotLocked();
        }
    }

    /** Guarded by {@link #stateLock}. */
    private OwnershipStatus ownershipSnapshotLocked() {
        if (!ownershipEnabled) {
            return new OwnershipStatus(OwnershipStatus.State.DISABLED, null, 0L);
        }
        long epoch = ownershipLease != null ? ownershipLease.epoch() : 0L;
        return new OwnershipStatus(ownershipState, ownerToken, epoch);
    }

    /**
     * The fence, read at every dispatch decision: dispatch is allowed only when
     * ownership is disabled, or this instance is ACTIVE and holds a
     * currently-valid (unexpired) lease. A stale owner whose lease has expired
     * (clock past expiry) is refused even before its renewal formally fences it,
     * and a stepped-down/standby instance is refused because it is not ACTIVE.
     */
    private boolean ownershipDispatchAllowed() {
        if (!ownershipEnabled) {
            return true;
        }
        StreamOwnershipLease lease = ownershipLease;
        return ownershipState == OwnershipStatus.State.ACTIVE
                && lease != null && lease.expiresAt().isAfter(clock.instant());
    }

    /**
     * At {@link #start()}: try to acquire ownership. Acquired → ACTIVE; otherwise
     * STANDBY. Either way arms the periodic ownership tick on the maintenance
     * scheduler. Returns whether this instance became ACTIVE.
     */
    private boolean acquireOwnershipAtStart() {
        Optional<StreamOwnershipLease> acquired = tryAcquireOwnershipSafe();
        StreamOwnershipLease stray = null;
        boolean active = false;
        synchronized (stateLock) {
            if (closed || aborted) {
                stray = acquired.orElse(null); // acquired but shutting down: release below
            } else {
                if (acquired.isPresent()) {
                    ownershipLease = acquired.get();
                    ownershipState = OwnershipStatus.State.ACTIVE;
                    active = true;
                } else {
                    ownershipState = OwnershipStatus.State.STANDBY;
                }
                scheduleOwnershipLocked(ownershipSchedEpoch);
            }
        }
        if (stray != null) releaseLeaseBestEffort(stray);
        return active;
    }

    private Optional<StreamOwnershipLease> tryAcquireOwnershipSafe() {
        try {
            return stateStore.tryAcquireOwnership(streamId, ownerToken, clock.instant(),
                    ownershipLeaseDuration);
        } catch (RuntimeException failure) {
            log.warn("TxFlowStream[{}] ownership acquire attempt failed; staying standby",
                    streamId, failure);
            return Optional.empty();
        }
    }

    /**
     * Guarded by {@link #stateLock}. Arms the next ownership tick on the
     * caller-owned maintenance scheduler at a fraction of the lease duration
     * ({@code leaseDuration/3}), so an ACTIVE owner renews well before expiry and
     * a standby polls to take over. Mirrors {@link #scheduleReconciliationLocked}
     * — a scheduler rejection disables ownership maintenance for the stream's
     * lifetime with a warn, never a fatal.
     */
    private void scheduleOwnershipLocked(long epoch) {
        long intervalMillis = Math.max(1L, ownershipLeaseDuration.toMillis() / 3L);
        try {
            ownershipTimer = maintenanceExecutor.schedule(() -> onOwnershipTick(epoch),
                    intervalMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException scheduleFailure) {
            log.warn("TxFlowStream[{}] ownership lease maintenance could not be scheduled; the"
                    + " lease renewal / acquire-poll is DISABLED for the lifetime of this stream"
                    + " (an ACTIVE owner will not renew and will lose ownership at expiry)",
                    streamId, scheduleFailure);
        }
    }

    /**
     * Guarded by {@link #stateLock}. Bumps the ownership scheduling epoch so any
     * already-fired but not-yet-run tick no-ops, and cancels the armed future —
     * mirroring the window/reconciliation timer cancellation.
     */
    private void cancelOwnershipLocked() {
        ownershipSchedEpoch++;
        if (ownershipTimer != null) {
            ownershipTimer.cancel(false);
            ownershipTimer = null;
        }
    }

    /**
     * One ownership maintenance tick on the caller-owned maintenance scheduler.
     * ACTIVE → renew the lease (fenced renewal fails → step down); STANDBY →
     * try to take over. Epoch/closed guards make a stale/late fire harmless. The
     * tick re-arms the next one unless the stream shut down.
     */
    private void onOwnershipTick(long epoch) {
        OwnershipStatus.State state;
        StreamOwnershipLease lease;
        synchronized (stateLock) {
            if (closed || aborted || epoch != ownershipSchedEpoch) {
                return; // late/stale fire after shutdown or supersession: no re-arm
            }
            state = ownershipState;
            lease = ownershipLease;
        }
        try {
            if (state == OwnershipStatus.State.ACTIVE) {
                renewTick(lease);
            } else if (state == OwnershipStatus.State.STANDBY) {
                pollTick();
            }
        } catch (Throwable tickFailure) {
            // A throwing store/listener must never kill the ownership timer.
            log.warn("TxFlowStream[{}] ownership tick failed", streamId, tickFailure);
        }
        synchronized (stateLock) {
            if (closed || aborted || epoch != ownershipSchedEpoch) {
                return;
            }
            scheduleOwnershipLocked(epoch);
        }
    }

    /**
     * ACTIVE renewal: extend the lease. If the renewal is fenced (our epoch was
     * superseded — another instance took over) or fails for any reason, step down
     * to STANDBY immediately and stop dispatching.
     */
    private void renewTick(StreamOwnershipLease lease) {
        if (lease == null) {
            stepDownFenced(new TxStreamException("TXSTREAM_OWNERSHIP_FENCED",
                    "ACTIVE owner holds no lease to renew"));
            return;
        }
        StreamOwnershipLease renewed;
        try {
            renewed = stateStore.renewOwnership(lease, clock.instant(), ownershipLeaseDuration);
        } catch (RuntimeException fenced) {
            // The FENCE (or a transient store failure): do not keep dispatching
            // without a confirmed-valid lease. Step down.
            stepDownFenced(fenced);
            return;
        }
        synchronized (stateLock) {
            // Install the renewal only if we are still the same ACTIVE epoch (a
            // concurrent step-down/close in a race takes precedence).
            if (ownershipState == OwnershipStatus.State.ACTIVE && ownershipLease != null
                    && ownershipLease.epoch() == renewed.epoch()) {
                ownershipLease = renewed;
            }
        }
    }

    /** STANDBY poll: attempt to take over ownership; on success, activate. */
    private void pollTick() {
        Optional<StreamOwnershipLease> acquired = tryAcquireOwnershipSafe();
        if (acquired.isEmpty()) {
            return; // stay standby
        }
        StreamOwnershipLease stray = null;
        boolean activate = false;
        synchronized (stateLock) {
            if (closed || aborted) {
                stray = acquired.get();
            } else if (ownershipState == OwnershipStatus.State.STANDBY) {
                ownershipLease = acquired.get();
                ownershipState = OwnershipStatus.State.ACTIVE;
                activate = true;
            } else {
                stray = acquired.get(); // raced to a non-standby state
            }
        }
        if (stray != null) {
            releaseLeaseBestEffort(stray);
            return;
        }
        if (activate) {
            activateOnFailover();
        }
    }

    /**
     * A standby has taken over: open this instance for dispatch. Re-attach
     * resumes the stream's durable non-terminal items (idempotent — deterministic
     * execution ids + the engine claim mean anything already submitted MATCHes
     * rather than double-runs).
     */
    private void activateOnFailover() {
        log.info("TxFlowStream[{}] took over ownership as ACTIVE (owner '{}', epoch {})",
                streamId, ownerToken, ownershipLease != null ? ownershipLease.epoch() : 0L);
        openForWork();
        safeListener(() -> listener.onOwnershipAcquired(ownership()));
    }

    /**
     * Typed cause for work settled {@code CANCELLED} because this instance lost
     * (or does not hold) dispatch ownership. Shared by the step-down drain and
     * the planning/dispatch-pipeline ownership rescues so every path reports the
     * identical, actionable message.
     */
    private static TxStreamException ownershipLostCause() {
        return new TxStreamException("TXSTREAM_OWNERSHIP_LOST",
                "Stream instance lost ownership before this item could dispatch; it was not sent to"
                        + " the engine. Resubmit the work to the new owner with a NEW item id (the"
                        + " same item id is already terminally cancelled in the durable store)");
    }

    /**
     * Step down from ACTIVE after a fenced renewal (or explicit loss): stop
     * dispatching, drain and settle any queued-but-unstarted work so
     * drain/close returns, and become STANDBY (keep polling to reclaim later).
     * <b>In-flight engine executions already started are NOT aborted</b> — they
     * are durable and are reconciled by whichever instance is ACTIVE via
     * re-attach / the reconciliation observer (they may be mid-submission).
     * <p>
     * <b>Invariant (composition probe 1):</b> after this method returns AND the
     * planning pump quiesces, no unsettled, non-in-flight item exists anywhere —
     * not in the window buffer, the planning queue, or a lane queue. The drain
     * below only sees work that is IN one of those queues at fence time; work
     * that is <em>between</em> queues (a batch the planning pump already
     * dequeued and is inside {@code planner.plan(...)} for, or an accept racing
     * the fence) lands in a queue only AFTER this drain — and each of those
     * enqueue sites re-checks ownership AFTER its enqueue and settles the work
     * {@code CANCELLED} ({@code TXSTREAM_OWNERSHIP_LOST}) itself:
     * {@link #runPlanning} (entry check for a batch handed over after the fence
     * + post-enqueue rescue for a batch mid-plan during the fence),
     * {@link #dispatchTemplate} (post-enqueue rescue), and
     * {@link #rescueWindowStraggler} (window-buffer straggler, typed
     * ownership-lost when the stop was a step-down). The re-checks read the
     * volatile ownership fields after an enqueue under {@link #stateLock}, and
     * removal races are settled by the lock: exactly one of {this drain, the
     * site's rescue} removes-and-settles a given execution. Corollary: a later
     * RECLAIM ({@link #activateOnFailover}) finds empty lane queues — it can
     * never blindly dispatch pre-fence executions whose durable rows the interim
     * owner already reaped ({@code TXSTREAM_ABANDONED}) — and its re-attach
     * re-scan works from durable truth alone.
     * <p>
     * Deliberate: the reconciliation observer is NOT cancelled here — a standby
     * keeps running read/repair-only reconciliation passes (benign: store writes
     * are CAS-arbitrated, and the observer dispatches nothing).
     */
    private void stepDownFenced(RuntimeException cause) {
        List<ItemState> orphaned = new ArrayList<>();
        OwnershipStatus lost;
        synchronized (stateLock) {
            if (ownershipState != OwnershipStatus.State.ACTIVE) {
                return; // already stepped down / released in a race
            }
            ownershipState = OwnershipStatus.State.STANDBY;
            ownershipLease = null;       // dispatch gate closes immediately
            accepting = false;           // reject new submits while not the owner
            cancelWindowTimerLocked();
            // Drain queued-but-unstarted work from the lanes / window / planning
            // queue; leave in-flight (started) executions running.
            ItemState windowed;
            while ((windowed = windowBuffer.poll()) != null) {
                orphaned.add(windowed);
            }
            // MINOR(i): the window is now drained; bump the window epoch (like
            // noteWindowEmptied / abort) so any already-fired-but-not-run age
            // wakeup for the drained window can never re-arm a stale successor.
            windowEpoch++;
            BatchState pendingBatch;
            while ((pendingBatch = planningQueue.poll()) != null) {
                pendingBatch.inPlanningQueue = false;
                for (ItemState member : pendingBatch.membersView()) {
                    if (!member.projection.isSettled() && !member.prePlanCancelled) {
                        orphaned.add(member);
                    }
                }
            }
            for (LaneQueue lane : laneQueues.values()) {
                ExecutionState queued;
                while ((queued = lane.queue.poll()) != null) {
                    orphaned.addAll(queued.members);
                    executionsById.remove(queued.executionId, queued);
                }
                lane.inRing = false;
                // lane.inFlight is deliberately left running.
            }
            readyRing.clear();
            lost = ownershipSnapshotLocked();
        }
        // FINDING A: this instance was ACTIVE and may have re-attached once already;
        // a later reclaim of ownership must RE-SCAN durable state, because an
        // interim owner could have planned+persisted new work while we stood by.
        // Reset the memoized re-attach so the next openForWork()/activateOnFailover
        // re-runs reattach() (idempotent — deterministic execution ids plus the
        // !items.containsKey(...) guard in runReattach make a re-scan safe). Done
        // OUTSIDE stateLock to preserve the reattachLock -> stateLock ordering.
        synchronized (reattachLock) {
            reattachReport = null;
        }
        log.warn("TxFlowStream[{}] ownership lease FENCED; stepping down to STANDBY and stopping"
                + " dispatch. In-flight engine executions continue and are reconciled by the new"
                + " owner.", streamId, cause);
        // FINDING C: the dropped item is registered and terminally CANCELLED in the
        // shared durable store, so resubmitting the SAME item id to the new owner is
        // rejected TXSTREAM_DUPLICATE_ITEM — only a NEW item id recovers the work.
        TxStreamException lostCause = ownershipLostCause();
        for (ItemState state : orphaned) {
            releasePermit(state);
            project(state, TxStreamItemStatus.CANCELLED,
                    itemBuilder -> itemBuilder.error(lostCause), false);
        }
        safeListener(() -> listener.onOwnershipLost(lost));
    }

    /**
     * Releases ownership on close/abort: drops this instance's lease (if still
     * current) and marks it RELEASED. Best-effort — the engine claim, not this
     * release, is the correctness guard; a failed release only delays a standby's
     * take-over until the lease expires.
     * <p>
     * FINDING B: an ACTIVE instance that releases fires
     * {@link TxStreamEventListener#onOwnershipLost} too, so a caller gets a
     * symmetric acquired/lost signal (its javadoc promises the callback on
     * close/abort). Guarded on {@code wasActive} so a STANDBY/DISABLED close never
     * fires a spurious loss, and so a second release (close after abort) does not
     * double-fire — the state is already RELEASED.
     */
    private void releaseOwnershipBestEffort() {
        if (!ownershipEnabled) {
            return;
        }
        StreamOwnershipLease lease;
        boolean wasActive;
        OwnershipStatus lost;
        synchronized (stateLock) {
            wasActive = ownershipState == OwnershipStatus.State.ACTIVE;
            lease = ownershipLease;
            ownershipLease = null;
            ownershipState = OwnershipStatus.State.RELEASED;
            lost = ownershipSnapshotLocked();
        }
        releaseLeaseBestEffort(lease);
        if (wasActive) {
            safeListener(() -> listener.onOwnershipLost(lost));
        }
    }

    private void releaseLeaseBestEffort(StreamOwnershipLease lease) {
        if (lease == null) {
            return;
        }
        try {
            stateStore.releaseOwnership(lease);
        } catch (RuntimeException failure) {
            log.warn("TxFlowStream[{}] ownership release failed", streamId, failure);
        }
    }

    @Override
    public ReattachReport reattach() {
        if (!stateStore.isDurable()) {
            return ReattachReport.empty();
        }
        synchronized (reattachLock) {
            if (reattachReport != null) {
                return reattachReport;
            }
            ReattachReport report;
            try {
                report = runReattach();
            } catch (RuntimeException reattachFailure) {
                log.error("TxFlowStream[{}] re-attach failed", streamId, reattachFailure);
                report = ReattachReport.empty();
            }
            reattachReport = report;
            return report;
        }
    }

    @Override
    public BootstrapReport bootstrap() {
        if (laneMode != LanePolicy.Mode.PARTITIONED) {
            return BootstrapReport.notApplicable();
        }
        synchronized (bootstrapLock) {
            if (bootstrapReport != null) {
                return bootstrapReport;
            }
            bootstrapReport = runBootstrap();
            return bootstrapReport;
        }
    }

    /**
     * Runs the one-time fan-out bootstrap as a single idempotent engine
     * execution (ADR 0004 Decision 2). Deterministic execution id + claim make
     * it run at most once — a restart or a second instance matches and never
     * re-splits. A non-COMPLETED or thrown outcome yields a {@code FAILED}
     * report; {@link #start()} turns that into a typed start failure.
     */
    private BootstrapReport runBootstrap() {
        int laneCount = partitionLanes.size();
        if (!partitioning.bootstrapEnabled()) {
            return BootstrapReport.disabled(laneCount);
        }
        String claimKey = bootstrapClaimKey();
        String executionId = StreamIdentities.executionId(namespace, claimKey);
        // FINDING-2 (funds-at-stake): on a durable stream, a persisted bootstrap
        // fingerprint that differs from the current one means the funding source,
        // seed, lane count, or the lane-address list/ORDER changed since the last
        // run — a change that mints a NEW split claim and re-drains the funding
        // wallet (and reorders remap partitionIndex→lane). Fail loud BEFORE
        // submitting any split. A non-durable stream cannot persist, so it cannot
        // detect drift — documented on PartitionedLanes.
        if (stateStore.isDurable()) {
            Optional<String> persisted = stateStore.getBootstrapFingerprint(streamId);
            if (persisted.isPresent() && !persisted.get().equals(claimKey)) {
                return BootstrapReport.failed(executionId, laneCount, new TxStreamException(
                        "TXSTREAM_BOOTSTRAP_CONFIG_DRIFT",
                        "Partitioned bootstrap configuration for stream '" + streamId
                                + "' changed since the last run (persisted fingerprint '"
                                + persisted.get() + "' != current '" + claimKey + "'). The funding"
                                + " source, seed, lane count (N), and the lane-address list AND its"
                                + " order must be byte-stable across restarts: changing any of them"
                                + " re-splits the funding wallet and reordering remaps lanes. No"
                                + " split was submitted."));
            }
        }
        FlowExecutionRequest request;
        try {
            request = FlowExecutionRequest.builder(buildBootstrapFlow(claimKey))
                    .executionId(executionId)
                    .idempotency(namespace, claimKey)
                    .spendingResource(partitionFundingIdentity())
                    .build();
        } catch (RuntimeException buildFailure) {
            return BootstrapReport.failed(executionId, laneCount, new TxStreamException(
                    "TXSTREAM_BOOTSTRAP_FAILED",
                    "Fan-out bootstrap request could not be built for stream '" + streamId + "'",
                    buildFailure));
        }
        EngineGateway.ExecutionHandle handle;
        try {
            handle = gateway.start(request);
        } catch (RuntimeException startFailure) {
            return BootstrapReport.failed(executionId, laneCount, new TxStreamException(
                    "TXSTREAM_BOOTSTRAP_FAILED",
                    "Fan-out bootstrap start failed for stream '" + streamId + "'", startFailure));
        }
        BindingOutcome binding;
        try {
            binding = classifyStartOutcome(handle);
        } catch (RuntimeException classifyFailure) {
            binding = BindingOutcome.CREATED;
        }
        FlowExecutionResult result;
        try {
            result = handle.completion().toCompletableFuture().join();
        } catch (RuntimeException completionFailure) {
            return BootstrapReport.failed(executionId, laneCount, new TxStreamException(
                    "TXSTREAM_BOOTSTRAP_FAILED",
                    "Fan-out bootstrap execution failed for stream '" + streamId + "'",
                    completionFailure));
        }
        if (result == null || result.state() != FlowExecutionState.COMPLETED) {
            return BootstrapReport.failed(executionId, laneCount, new TxStreamException(
                    "TXSTREAM_BOOTSTRAP_FAILED",
                    "Fan-out bootstrap did not complete for stream '" + streamId + "' (state "
                            + (result == null ? "null" : result.state()) + ")"));
        }
        // FINDING-2: record the bootstrap fingerprint so a later run with a
        // drifted configuration fails fast (above) instead of silently re-splitting
        // the wallet. Best-effort: the engine idempotency claim is the real
        // exactly-once guard — a failed fingerprint write only forfeits the loud
        // early drift warning, so it warns rather than failing an already-run split.
        if (stateStore.isDurable()) {
            try {
                stateStore.persistBootstrapFingerprint(streamId, claimKey);
            } catch (RuntimeException persistFailure) {
                log.warn("TxFlowStream[{}] could not persist the bootstrap fingerprint; drift"
                        + " detection is degraded for this stream", streamId, persistFailure);
            }
        }
        log.info("TxFlowStream[{}] fan-out bootstrap {} into {} lanes (execution '{}')",
                streamId, binding == BindingOutcome.MATCHED ? "matched" : "ran", laneCount,
                executionId);
        return binding == BindingOutcome.MATCHED
                ? BootstrapReport.matched(executionId, laneCount)
                : BootstrapReport.ran(executionId, laneCount);
    }

    /**
     * The bootstrap's idempotency claim key: {@code bootstrap:<N>:<fingerprint>}
     * where the fingerprint covers the funding source, the lane addresses (in
     * order), and the seed. Same configuration ⇒ same claim ⇒ runs exactly
     * once across restarts and instances.
     */
    private String bootstrapClaimKey() {
        StringBuilder input = new StringBuilder();
        LaneFundingScope funding = partitioning.fundingSource();
        input.append(funding.kind()).append(':').append(funding.source());
        for (String laneAddress : partitioning.laneAddresses()) {
            input.append(' ').append(laneAddress);
        }
        Amount seed = partitioning.seedPerLane();
        input.append(' ').append(seed.getUnit()).append(':').append(seed.getQuantity());
        return "bootstrap:" + partitionLanes.size() + ":"
                + StreamIdentities.fingerprint40(input.toString());
    }

    /**
     * Builds the fan-out transaction: one {@link Tx} spending the funding source
     * and paying the per-lane seed to each of the N lane addresses, wrapped in a
     * single portable flow step under a deterministic flow id.
     */
    private TxFlow buildBootstrapFlow(String claimKey) {
        LaneFundingScope funding = partitioning.fundingSource();
        Amount seed = partitioning.seedPerLane();
        Tx tx = new Tx();
        for (String laneAddress : partitioning.laneAddresses()) {
            tx.payToAddress(laneAddress, seed);
        }
        if (funding.kind() == LaneFundingScope.Kind.ADDRESS) {
            tx.from(funding.source());
        } else {
            tx.fromRef(funding.source());
        }
        FlowStep step = FlowStep.builder(StreamIdentities.GENERATED_STEP_ID)
                .withTxPlan(TxPlan.from(tx)).build();
        return TxFlow.builder(StreamIdentities.flowId(namespace, claimKey))
                .addStep(step).build();
    }

    /** The funding source's canonical spending identity ({@code addr:}/{@code ref:}). */
    private String partitionFundingIdentity() {
        LaneFundingScope funding = partitioning.fundingSource();
        return (funding.kind() == LaneFundingScope.Kind.ADDRESS ? "addr:" : "ref:")
                + funding.source();
    }

    @Override
    public void drain() {
        stopAccepting();
        flush();
        schedulePump();
        awaitPromises(null);
        safeListener(() -> listener.onStreamDrained(streamId));
    }

    @Override
    public void awaitDrain(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        stopAccepting();
        flush();
        schedulePump();
        awaitPromises(timeout);
        safeListener(() -> listener.onStreamDrained(streamId));
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            if (closed) return;
            closed = true;
            accepting = false;
            cancelReconciliationLocked();
            cancelOwnershipLocked();
        }
        releaseOwnershipBestEffort();
        try {
            // Not flush(): the public entry point no-ops once closed, and the
            // closed flag is already up — this close must still plan the open
            // window it is about to await.
            flushOpenWindow();
            schedulePump();
            awaitPromises(null);
        } finally {
            try {
                source.close();
            } catch (Exception failure) {
                log.warn("TxFlowStream[{}] source close failed", streamId, failure);
            }
            safeListener(() -> listener.onStreamClosed(streamId));
        }
    }

    @Override
    public void close(Duration graceDeadline) {
        Objects.requireNonNull(graceDeadline, "graceDeadline");
        try {
            awaitDrain(graceDeadline);
        } catch (TxStreamTimeoutException deadlineElapsed) {
            abort("Close grace deadline of " + graceDeadline + " elapsed");
            return;
        }
        close();
    }

    @Override
    public AbortReport abort(String reason) {
        synchronized (abortLock) {
            AbortReport existing = abortReport;
            if (existing != null) {
                return existing;
            }
            List<ItemState> buffered = new ArrayList<>();
            List<ExecutionState> signalled = new ArrayList<>();
            synchronized (stateLock) {
                aborted = true;
                abortReason = reason;
                accepting = false;
                closed = true;
                cancelWindowTimerLocked();
                cancelReconciliationLocked();
                cancelOwnershipLocked();
                ItemState windowed;
                while ((windowed = windowBuffer.poll()) != null) {
                    buffered.add(windowed);
                }
                BatchState pendingBatch;
                while ((pendingBatch = planningQueue.poll()) != null) {
                    pendingBatch.inPlanningQueue = false;
                    for (ItemState member : pendingBatch.membersView()) {
                        if (!member.projection.isSettled() && !member.prePlanCancelled) {
                            buffered.add(member);
                        }
                    }
                }
                for (LaneQueue lane : laneQueues.values()) {
                    ExecutionState queued;
                    while ((queued = lane.queue.poll()) != null) {
                        buffered.addAll(queued.members);
                        executionsById.remove(queued.executionId, queued);
                    }
                    lane.inRing = false;
                    if (lane.inFlight != null) {
                        signalled.add(lane.inFlight);
                    }
                }
                readyRing.clear();
            }
            List<String> cancelledItemIds = new ArrayList<>(buffered.size());
            for (ItemState state : buffered) {
                cancelledItemIds.add(state.item.getItemId());
            }
            List<String> signalledExecutionIds = new ArrayList<>(signalled.size());
            List<CompletableFuture<?>> outstanding = new ArrayList<>();
            for (ExecutionState execution : signalled) {
                signalledExecutionIds.add(execution.executionId);
                for (ItemState member : execution.members) {
                    outstanding.add(member.projection.promise());
                }
            }
            // Reentrancy guard: the report is fully built and PUBLISHED before
            // any side effect that can call back into user code below
            // (projection listeners, receipt promise completions, source
            // close, onStreamClosed). abortLock is reentrant on this thread,
            // so a listener that calls abort() from one of those callbacks
            // re-enters this monitor, sees the published report, and returns
            // it — the same instance the outer call returns, consistent with
            // idempotency. The reentrant caller observes in-progress
            // semantics: cancellations and signals may still be in flight on
            // the outer call, but the report contents are already frozen.
            AbortReport report = new AbortReport(cancelledItemIds, signalledExecutionIds,
                    CompletableFuture.allOf(outstanding.toArray(new CompletableFuture[0]))
                            .minimalCompletionStage());
            abortReport = report;
            TxStreamException cause = new TxStreamException("TXSTREAM_ABORTED",
                    reason != null ? reason : "Stream aborted");
            for (ItemState state : buffered) {
                releasePermit(state);
                project(state, TxStreamItemStatus.CANCELLED,
                        itemBuilder -> itemBuilder.error(cause), false);
            }
            for (ExecutionState execution : signalled) {
                execution.pendingCancelReason = reason != null ? reason : "Stream aborted";
                EngineGateway.ExecutionHandle handle = execution.handle;
                if (handle != null) {
                    try {
                        handle.requestCancel(reason);
                    } catch (RuntimeException cancelFailure) {
                        log.warn("TxFlowStream[{}] abort cancel signal failed for execution '{}'",
                                streamId, execution.executionId, cancelFailure);
                    }
                }
            }
            releaseOwnershipBestEffort();
            try {
                source.close();
            } catch (Exception failure) {
                log.warn("TxFlowStream[{}] source close failed during abort", streamId, failure);
            }
            safeListener(() -> listener.onStreamClosed(streamId));
            return report;
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    private void stopAccepting() {
        synchronized (stateLock) {
            accepting = false;
        }
    }

    private boolean isAccepting() {
        return started && accepting && !closed && healthy;
    }

    /**
     * Refusal disposition when {@link #isAccepting()} is false: an ownership
     * {@code STANDBY} on an otherwise-live stream is a <em>temporary</em>
     * condition (the instance polls to reclaim), reported {@code PAUSED} so a
     * source adapter parks and retries instead of tearing down permanently
     * (composition probe 2). Every genuinely terminal not-accepting state —
     * not started, closed, aborted, unhealthy, or an ACTIVE stream draining —
     * stays {@code CLOSED}.
     */
    private Acceptance refuseNotAccepting() {
        boolean standby = ownershipEnabled
                && ownershipState == OwnershipStatus.State.STANDBY
                && started && !closed && !aborted && healthy;
        return standby ? Acceptance.paused() : Acceptance.closed();
    }

    private void awaitPromises(Duration timeout) {
        CompletableFuture<?>[] outstanding = items.values().stream()
                .map(state -> state.projection.promise())
                .toArray(CompletableFuture[]::new);
        CompletableFuture<Void> all = CompletableFuture.allOf(outstanding);
        try {
            if (timeout == null) {
                // Interruptible on purpose: an untimed drain must not pin an
                // interrupted thread forever.
                all.get();
            } else {
                all.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (TimeoutException timeoutFailure) {
            throw new TxStreamTimeoutException(
                    "Stream '" + streamId + "' did not drain within " + timeout, timeoutFailure);
        } catch (InterruptedException interrupt) {
            Thread.currentThread().interrupt();
            throw new TxStreamException("TXSTREAM_INTERRUPTED",
                    "Interrupted while draining stream '" + streamId + "'", interrupt);
        } catch (ExecutionException impossible) {
            // Item promises complete normally by construction; treat defensively.
            throw new TxStreamException("TXSTREAM_DRAIN_FAILED",
                    "Stream drain failed unexpectedly", impossible.getCause());
        }
    }

    // ------------------------------------------------------------------
    // Submission
    // ------------------------------------------------------------------

    @Override
    public TxStreamReceipt submit(TxWorkItem item) {
        Objects.requireNonNull(item, "item");
        Acceptance acceptance = accept(item, true);
        switch (acceptance.disposition) {
            case ACCEPTED:
            case ATTACHED:
            case VALIDATION_FAILED:
                return acceptance.receipt;
            case CONFLICT:
                throw acceptance.conflict;
            case PAUSED:
                // Blocking submit has no park-and-retry contract; a standby
                // refusal keeps the established TXSTREAM_CLOSED code (only the
                // non-blocking trySubmit distinguishes PAUSED for adapters).
                throw new TxStreamException("TXSTREAM_CLOSED",
                        "Stream '" + streamId + "' is a standby instance (not the current"
                                + " ownership lease holder); submit to the ACTIVE owner");
            case CLOSED:
            default:
                throw new TxStreamException("TXSTREAM_CLOSED",
                        "Stream '" + streamId + "' is not accepting work");
        }
    }

    @Override
    public EmitResult trySubmit(TxWorkItem item) {
        Objects.requireNonNull(item, "item");
        Acceptance acceptance;
        try {
            acceptance = accept(item, false);
        } catch (TxStreamException rejection) {
            // trySubmit never throws for content/registration outcomes; the
            // typed cause travels on the REJECTED disposition instead.
            return EmitResult.rejected(rejection);
        }
        switch (acceptance.disposition) {
            case ACCEPTED:
            case VALIDATION_FAILED:
                return EmitResult.ok(acceptance.receipt);
            case ATTACHED:
                return EmitResult.duplicateAttached(acceptance.receipt);
            case CONFLICT:
                return EmitResult.conflict(acceptance.conflict);
            case FULL:
                return EmitResult.full();
            case PAUSED:
                return EmitResult.paused();
            case CLOSED:
            default:
                return EmitResult.closed();
        }
    }

    private Acceptance accept(TxWorkItem item, boolean blocking) {
        ItemState existing = items.get(item.getItemId());
        if (existing != null) {
            return attachOrConflict(existing, item);
        }
        if (!isAccepting()) {
            return refuseNotAccepting();
        }
        if (blocking) {
            try {
                capacity.acquire();
            } catch (InterruptedException interrupt) {
                Thread.currentThread().interrupt();
                throw new TxStreamException("TXSTREAM_INTERRUPTED",
                        "Interrupted while submitting item '" + item.getItemId() + "'", interrupt);
            }
        } else if (!capacity.tryAcquire()) {
            return Acceptance.full();
        }
        boolean buffered = false;
        try {
            if (!isAccepting()) {
                return refuseNotAccepting();
            }
            existing = items.get(item.getItemId());
            if (existing != null) {
                return attachOrConflict(existing, item);
            }
            PreparedItem prepared;
            try {
                prepared = prepare(item);
            } catch (TxStreamException validationFailure) {
                if ("TXSTREAM_LANE_UNRESOLVED".equals(validationFailure.getCode())) {
                    // Transient infrastructure, not item content: settle typed
                    // but retain nothing, so redelivery retries fresh once the
                    // resolver recovers instead of attaching to a poisoned
                    // failure.
                    return acceptLaneUnresolved(item, validationFailure);
                }
                return acceptValidationFailed(item, validationFailure);
            } catch (IllegalArgumentException invalidItem) {
                // Invalid item content (for example an idempotency key that
                // violates the store text policy) is a content outcome:
                // submit throws it typed and trySubmit reports it REJECTED.
                // Only a null item stays an untyped NPE at the submit entry
                // points — a programming error, never a content outcome.
                throw new TxStreamException("TXSTREAM_INVALID_ITEM",
                        "Item '" + item.getItemId() + "' is invalid: "
                                + invalidItem.getMessage(), invalidItem);
            }
            ItemState state = ItemState.pending(this, item, prepared,
                    inlineIdentity() || prepared.isTemplate());
            state.wholeFlow = prepared.isTemplate();
            ItemState raced = items.putIfAbsent(item.getItemId(), state);
            if (raced != null) {
                return attachOrConflict(raced, item);
            }
            String claimOwner = itemIdByClaimKey.putIfAbsent(prepared.claimKey, item.getItemId());
            if (claimOwner != null && !claimOwner.equals(item.getItemId())) {
                TxStreamException reuse = new TxStreamException("TXSTREAM_IDEMPOTENCY_KEY_REUSE",
                        "Idempotency key '" + prepared.claimKey + "' is already bound to item '"
                                + claimOwner + "'; item '" + item.getItemId()
                                + "' cannot reuse it — redelivery must reuse the original item id");
                // Settle before removing: a receipt attached concurrently to
                // this state must settle too, never hang on a removed item.
                // The store never learns about a rejected item (no projection
                // for an unregistered, removed item), and the rejection is
                // counted nowhere — the submission never became stream work,
                // so it must not bump the accepted/failed stats.
                state.suppressStoreProjection = true;
                state.suppressCounters = true;
                failItem(state, "TXSTREAM_IDEMPOTENCY_KEY_REUSE", reuse.getMessage(), reuse);
                items.remove(item.getItemId(), state);
                throw reuse;
            }
            acceptedCount.incrementAndGet();
            try {
                // Authoritative planning write: fails closed, rejecting the submit.
                stateStore.registerItem(new TxStreamItemRecord(item.getItemId(),
                        prepared.claimKey, prepared.lane.laneName(), prepared.fingerprint,
                        clock.instant()));
            } catch (TxStreamDuplicateItemException duplicate) {
                // Settle before removing (same reason as above).
                state.suppressStoreProjection = true;
                failItem(state, "TXSTREAM_DUPLICATE_ITEM",
                        "Item registration conflicted for '" + item.getItemId() + "'", duplicate);
                items.remove(item.getItemId(), state);
                itemIdByClaimKey.remove(prepared.claimKey, item.getItemId());
                return Acceptance.conflict(duplicate);
            } catch (RuntimeException registrationFailure) {
                TxStreamException rejection = new TxStreamException("TXSTREAM_REGISTRATION_FAILED",
                        "Authoritative item registration failed for '" + item.getItemId() + "'",
                        registrationFailure);
                // Settle before removing (same reason as above).
                state.suppressStoreProjection = true;
                failItem(state, "TXSTREAM_REGISTRATION_FAILED", rejection.getMessage(), rejection);
                items.remove(item.getItemId(), state);
                itemIdByClaimKey.remove(prepared.claimKey, item.getItemId());
                throw rejection;
            }
            ItemProjection.Applied accepted = new ItemProjection.Applied(
                    state.projection.current(), 1, null);
            safeStoreProject(accepted);
            safeListener(() -> listener.onItemAccepted(item, state.receipt));
            safeListener(() -> listener.onItemUpdated(accepted.result()));
            state.permitHeld.set(true);
            buffered = true;
            if (prepared.isTemplate()) {
                // A template invocation is already a whole compiled flow — it
                // does not window or batch with other items. It dispatches as
                // its own single-member execution, bypassing the planner (the
                // item↔step planner model cannot express one item owning a
                // whole multi-step flow).
                dispatchTemplate(state, prepared);
            } else {
                acceptIntoWindow(state);
                rescueWindowStraggler(state);
            }
            return Acceptance.accepted(state.receipt);
        } finally {
            if (!buffered) {
                capacity.release();
            }
        }
    }

    /** Whether execution identity is item-claim-derived and known at accept. */
    private boolean inlineIdentity() {
        return planner == BuiltInPlanners.PER_ITEM;
    }

    private void releasePermit(ItemState state) {
        if (state.permitHeld.compareAndSet(true, false)) {
            capacity.release();
        }
    }

    private Acceptance attachOrConflict(ItemState existing, TxWorkItem candidate) {
        String candidateFingerprint = null;
        try {
            candidateFingerprint = prepare(candidate).fingerprint;
        } catch (TxStreamException validationFailure) {
            // A payload that fails eager validation still fingerprints — over
            // its raw fields plus the diagnostic code — so an identical
            // redelivery of the same bad item attaches to its settled failed
            // receipt instead of conflicting.
            candidateFingerprint = validationFailedFingerprint(candidate, validationFailure);
        } catch (RuntimeException nonComparable) {
            // Left null: a payload that cannot be fingerprinted at all can
            // never match a stored fingerprint; conflicts below.
        }
        if (candidateFingerprint != null && candidateFingerprint.equals(existing.fingerprint)) {
            return Acceptance.attached(existing.receipt);
        }
        String message = candidateFingerprint == null
                ? "Item '" + candidate.getItemId() + "' was already accepted and the"
                        + " redelivered payload could not be fingerprinted for comparison"
                : "Item '" + candidate.getItemId() + "' was already accepted"
                        + (existing.projection.isSettled()
                                && existing.projection.current().getStatus()
                                        == TxStreamItemStatus.FAILED
                                ? " (and settled as FAILED)" : "")
                        + " with different content";
        return Acceptance.conflict(
                new TxStreamDuplicateItemException(candidate.getItemId(), message));
    }

    private String validationFailedFingerprint(TxWorkItem item, TxStreamException failure) {
        String claimKey = item.getIdempotencyKey() != null
                ? item.getIdempotencyKey() : item.getItemId();
        return StreamIdentities.failedItemFingerprint(item.getItemId(), claimKey,
                item.getLane(), item.getMetadata(), failure.getCode());
    }

    private Acceptance acceptValidationFailed(TxWorkItem item, TxStreamException failure) {
        String laneName = item.getLane() != null ? item.getLane()
                : staticLane != null ? staticLane.laneName() : null;
        TxStreamItemResult failed = TxStreamItemResult
                .builder(streamId, item.getItemId(), TxStreamItemStatus.FAILED)
                .laneName(laneName)
                .error(failure)
                .updatedAt(clock.instant())
                .build();
        ItemState state = ItemState.settledWithoutRegistration(this, item, failed,
                validationFailedFingerprint(item, failure));
        ItemState raced = items.putIfAbsent(item.getItemId(), state);
        if (raced != null) {
            return attachOrConflict(raced, item);
        }
        acceptedCount.incrementAndGet();
        failedCount.incrementAndGet();
        noteSettledForRetention(state);
        // Deliberately not registered in the state store and never buffered.
        safeListener(() -> listener.onItemAccepted(item, state.receipt));
        safeListener(() -> listener.onItemUpdated(failed));
        return Acceptance.validationFailed(state.receipt);
    }

    /**
     * Accepts an item whose lane failed to resolve ({@code
     * TXSTREAM_LANE_UNRESOLVED}) — a transient infrastructure failure, not
     * item content. The item's receipt settles typed like a validation
     * failure, but the state is failed-and-released: it enters neither the
     * live item map, the claim index, the state store, nor the retention
     * FIFO. A redelivery after the resolver recovers therefore retries fresh
     * and dispatches normally, instead of attaching forever to an outage-era
     * failure. Content and configuration lane failures
     * ({@code TXSTREAM_LANE_REQUIRED} / {@code _MISMATCH} /
     * {@code _SCOPE_OVERLAP}) keep the retained settled-and-attach semantics
     * of {@link #acceptValidationFailed(TxWorkItem, TxStreamException)}.
     */
    private Acceptance acceptLaneUnresolved(TxWorkItem item, TxStreamException failure) {
        TxStreamItemResult failed = TxStreamItemResult
                .builder(streamId, item.getItemId(), TxStreamItemStatus.FAILED)
                .laneName(item.getLane())
                .error(failure)
                .updatedAt(clock.instant())
                .build();
        ItemState state = ItemState.settledWithoutRegistration(this, item, failed, null);
        acceptedCount.incrementAndGet();
        failedCount.incrementAndGet();
        safeListener(() -> listener.onItemAccepted(item, state.receipt));
        safeListener(() -> listener.onItemUpdated(failed));
        return Acceptance.validationFailed(state.receipt);
    }

    /**
     * Validates one item eagerly — lane resolution, portability, funding
     * scope — and derives its accept-time planning seed and fingerprint, all
     * before anything is registered. The enforced single-transaction step is
     * the planner seed; the per-item flow/execution identities are the
     * default planner's (and the fingerprint's) claim-derived derivations.
     */
    private PreparedItem prepare(TxWorkItem item) {
        String claimKey = item.getIdempotencyKey() != null
                ? item.getIdempotencyKey() : item.getItemId();
        FlowStoreTextPolicy.requireIdentifier(claimKey, "idempotency key",
                FlowStoreTextPolicy.MAX_IDEMPOTENCY_KEY_BYTES);
        if (item.getKind() == TxWorkItem.Kind.TEMPLATE) {
            return prepareTemplate(item, claimKey);
        }
        String flowId = StreamIdentities.flowId(namespace, claimKey);
        FlowStep step = item.getKind() == TxWorkItem.Kind.TX_PLAN
                ? FlowStep.builder(StreamIdentities.GENERATED_STEP_ID)
                        .withTxPlan(item.getTxPlan()).build()
                : item.getFlowStep();
        TxFlow definition = TxFlow.builder(flowId).addStep(step).build();
        // Portability is a property of the payload, independent of the lane, so
        // validate it BEFORE deriving the lane (QUALITY). Otherwise a non-portable
        // payload — e.g. a multi-transaction plan under byFundingAddress, which has
        // no single funding source to derive from — would surface the downstream
        // TXSTREAM_LANE_UNDERIVABLE instead of the precise TXSTREAM_NON_PORTABLE_ITEM.
        List<FlowDiagnostic> diagnostics = PortableFlowValidator.validate(definition);
        if (!diagnostics.isEmpty()) {
            throw new TxStreamException("TXSTREAM_NON_PORTABLE_ITEM",
                    "Item '" + item.getItemId() + "' is not portable: " + diagnostics);
        }
        ResolvedLane lane = resolveLane(item);
        FlowStep enforced = enforceLaneFundingScope(item, step, lane);
        if (enforced != step) {
            definition = TxFlow.builder(flowId).addStep(enforced).build();
            step = enforced;
        }
        String payload;
        try {
            payload = codec.write(definition,
                    FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
        } catch (RuntimeException encodingFailure) {
            throw new TxStreamException("TXSTREAM_NON_PORTABLE_ITEM",
                    "Item '" + item.getItemId() + "' has no portable encoding: "
                            + encodingFailure.getMessage(), encodingFailure);
        }
        // The fingerprint covers the lane as submitted (the planner-visible
        // field), defaulting to the effective lane's label so 1A single-lane
        // fingerprints stay stable.
        String fingerprintLane = item.getLane() != null ? item.getLane() : lane.laneName();
        String fingerprint = StreamIdentities.itemFingerprint(item.getItemId(), claimKey,
                fingerprintLane, item.getMetadata(), payload, item.getBindings(),
                item.getSecureBindingReferences(), item.getSensitiveBindings());
        return new PreparedItem(claimKey,
                StreamIdentities.executionId(namespace, claimKey),
                flowId, step.getId(), step, fingerprint, lane, null, null);
    }

    /**
     * Prepares one template item (ADR 0004, iteration 3): resolves the
     * pre-registered template (unknown id → {@code TXSTREAM_TEMPLATE_UNKNOWN},
     * which the accept path settles+retains so an identical redelivery
     * attaches), resolves the item's explicit lane, and fingerprints over the
     * template <em>reference</em> plus the item's bindings instead of an inline
     * portable payload. The definition itself was validated and encoded once at
     * build time; nothing is recompiled per item. The template's funding is
     * inside its definition, so the per-tx lane funding-scope enforcement does
     * not apply — template items require an explicit lane (validated in
     * {@link #resolveLane(TxWorkItem)}).
     */
    private PreparedItem prepareTemplate(TxWorkItem item, String claimKey) {
        RegisteredTemplate template = templates.get(item.getTemplateId());
        if (template == null) {
            throw new TxStreamException("TXSTREAM_TEMPLATE_UNKNOWN",
                    "Item '" + item.getItemId() + "' references template '"
                            + item.getTemplateId() + "', which is not registered on this stream"
                            + " (register it with TxFlowStream.Builder.template(id, definition))");
        }
        ResolvedLane lane = resolveLane(item);
        String fingerprintLane = item.getLane() != null ? item.getLane() : lane.laneName();
        String fingerprint = StreamIdentities.templateItemFingerprint(item.getItemId(), claimKey,
                fingerprintLane, item.getMetadata(), template.templateId(), item.getBindings(),
                item.getSecureBindingReferences(), item.getSensitiveBindings());
        return new PreparedItem(claimKey,
                StreamIdentities.executionId(namespace, claimKey),
                template.definition().getId(), template.terminalStepId(), null, fingerprint,
                lane, template.templateId(), template.definition());
    }

    /**
     * Resolves the item's effective lane, dispatching on the configured
     * {@link LanePolicy} mode.
     *
     * <p>{@code single()} pins every item to one statically configured lane;
     * {@code explicit()} resolves item-named lanes through the
     * {@link LaneIdentityResolver}; {@code byFundingAddress()} derives the lane
     * from the item transaction's own funding source; {@code partitioned()}
     * assigns the item to one of N hash-partitioned lanes. Each mode fails the
     * item typed on its own violations, never the stream and never at
     * startup.</p>
     */
    private ResolvedLane resolveLane(TxWorkItem item) {
        if (item.getKind() == TxWorkItem.Kind.TEMPLATE
                && (laneMode == LanePolicy.Mode.BY_FUNDING_ADDRESS
                        || laneMode == LanePolicy.Mode.PARTITIONED)) {
            // A template's funding lives inside its (multi-step) definition, so
            // there is no single per-item transaction to derive/hash a lane
            // from. Template items therefore require an explicit lane
            // (single()/explicit()); deriving a lane from a template's bound
            // definition is a follow-up (ADR 0004, iteration 3).
            throw new TxStreamException("TXSTREAM_LANE_REQUIRED",
                    "Item '" + item.getItemId() + "' invokes template '" + item.getTemplateId()
                            + "', but LanePolicy." + laneMode + " cannot derive a lane for a"
                            + " template item; configure LanePolicy.single(...) or"
                            + " LanePolicy.explicit() with an explicit lane for template items");
        }
        switch (laneMode) {
            case SINGLE:
                return resolveSingleLane(item);
            case BY_FUNDING_ADDRESS:
                return deriveLaneFromFundingSource(item);
            case PARTITIONED:
                return partitionLane(item);
            case EXPLICIT:
            default:
                return resolveExplicitLane(item);
        }
    }

    /**
     * Single-lane policy: an item may omit the lane or name the configured
     * lane; a different name is a typed failure ({@code TXSTREAM_LANE_MISMATCH})
     * — a lane name is never a silent scheduling-only label.
     */
    private ResolvedLane resolveSingleLane(TxWorkItem item) {
        String requested = item.getLane();
        if (requested != null && !requested.equals(staticLane.laneName())) {
            throw new TxStreamException("TXSTREAM_LANE_MISMATCH",
                    "Item '" + item.getItemId() + "' names lane '" + requested
                            + "' but this stream is configured with the single lane '"
                            + staticLane.laneName() + "'");
        }
        return staticLane;
    }

    /**
     * Explicit policy: the item must name a lane ({@code TXSTREAM_LANE_REQUIRED});
     * the name resolves through the {@link LaneIdentityResolver} once, at first
     * use, and is cached. A resolver failure or {@code null} fails the item
     * typed ({@code TXSTREAM_LANE_UNRESOLVED}) on the failed-and-released path —
     * transient resolver outages retain nothing, so failures are never cached
     * and a redelivery retries fresh. A resolved funding scope already owned by
     * a different canonical identity fails typed
     * ({@code TXSTREAM_LANE_SCOPE_OVERLAP}) — overlapping scopes cannot be
     * independent lanes.
     */
    private ResolvedLane resolveExplicitLane(TxWorkItem item) {
        String requested = item.getLane();
        if (requested == null) {
            throw new TxStreamException("TXSTREAM_LANE_REQUIRED",
                    "Item '" + item.getItemId() + "' names no lane; LanePolicy.explicit()"
                            + " requires TxWorkItem.Builder.withLane(String)");
        }
        ResolvedLane cached = laneByName.get(requested);
        if (cached != null) {
            return cached;
        }
        synchronized (laneLock) {
            cached = laneByName.get(requested);
            if (cached != null) {
                return cached;
            }
            ResolvedLane resolved;
            try {
                resolved = laneResolver.resolve(requested);
            } catch (RuntimeException resolverFailure) {
                throw new TxStreamException("TXSTREAM_LANE_UNRESOLVED",
                        "Lane '" + requested + "' failed to resolve for item '"
                                + item.getItemId() + "': " + resolverFailure.getMessage(),
                        resolverFailure);
            }
            if (resolved == null) {
                throw new TxStreamException("TXSTREAM_LANE_UNRESOLVED",
                        "Lane '" + requested + "' resolved to null for item '"
                                + item.getItemId() + "'");
            }
            registerLaneScope(resolved, requested);
            laneByName.put(requested, resolved);
            return resolved;
        }
    }

    /**
     * Funding-address policy: derive the lane from the item transaction's own
     * funding source. The lane <em>is</em> the funding source — its name is the
     * source string and its canonical identity is derived from it — so items
     * from the same sender serialize and items from different senders run
     * concurrently, all through the canonical-identity scheduler and with no
     * resolver. An item whose transaction names no funding source fails typed
     * ({@code TXSTREAM_LANE_UNDERIVABLE}); an item that also
     * {@link TxWorkItem.Builder#withLane(String) names a lane} must name the
     * derived one, or fails ({@code TXSTREAM_LANE_MISMATCH}) — mirroring
     * {@code single()} rather than silently ignoring the label.
     */
    private ResolvedLane deriveLaneFromFundingSource(TxWorkItem item) {
        ResolvedLane derived = fundingSourceLane(item);
        String requested = item.getLane();
        if (requested != null && !requested.equals(derived.laneName())) {
            throw new TxStreamException("TXSTREAM_LANE_MISMATCH",
                    "Item '" + item.getItemId() + "' names lane '" + requested
                            + "' but LanePolicy.byFundingAddress() derives the lane from the"
                            + " transaction's funding source '" + derived.laneName() + "'");
        }
        // Cache under the lane name so the planner-side resolvePlannedLane finds it.
        laneByName.putIfAbsent(derived.laneName(), derived);
        return derived;
    }

    /**
     * Derives a lane from the item transaction's {@code from} address or
     * {@code from_ref}. A plan with no single transaction or no funding source
     * fails typed {@code TXSTREAM_LANE_UNDERIVABLE}.
     */
    private ResolvedLane fundingSourceLane(TxWorkItem item) {
        Tx tx = singleTx(itemTxPlan(item));
        if (tx != null) {
            String from = tx.getSender();
            String fromRef = tx.getFromRef();
            boolean hasFrom = from != null && !from.isEmpty();
            boolean hasFromRef = fromRef != null && !fromRef.isEmpty();
            if (hasFrom && hasFromRef) {
                // Ambiguous: two funding sources cannot resolve to one lane.
                // Fail with a clear underivable message (QUALITY) rather than
                // letting enforceLaneFundingScope surface a confusing
                // TXSTREAM_LANE_SCOPE_VIOLATION downstream.
                throw new TxStreamException("TXSTREAM_LANE_UNDERIVABLE",
                        "Item '" + item.getItemId() + "' names BOTH a from address and a from_ref;"
                                + " LanePolicy.byFundingAddress() cannot derive a single lane from an"
                                + " ambiguous funding source — declare exactly one of from / from_ref");
            }
            if (hasFrom) {
                return ResolvedLane.ofAddress(from, from);
            }
            if (hasFromRef) {
                return ResolvedLane.ofFundingRef(fromRef, fromRef);
            }
        }
        throw new TxStreamException("TXSTREAM_LANE_UNDERIVABLE",
                "Item '" + item.getItemId() + "' has no funding source (from / from_ref) to"
                        + " derive a lane from; LanePolicy.byFundingAddress() requires the"
                        + " transaction to name its sender");
    }

    /**
     * Registers a resolved lane's funding scope against its canonical identity,
     * failing typed ({@code TXSTREAM_LANE_SCOPE_OVERLAP}) when the same scope is
     * already owned by a different identity.
     */
    private void registerLaneScope(ResolvedLane resolved, String requested) {
        String scopeKey = resolved.fundingScope().kind() + ":"
                + resolved.fundingScope().source();
        String scopeOwner = identityByFundingScope.putIfAbsent(scopeKey,
                resolved.canonicalSpendingIdentity());
        if (scopeOwner != null && !scopeOwner.equals(resolved.canonicalSpendingIdentity())) {
            throw new TxStreamException("TXSTREAM_LANE_SCOPE_OVERLAP",
                    "Lane '" + requested + "' claims canonical identity '"
                            + resolved.canonicalSpendingIdentity() + "' but its funding scope ("
                            + scopeKey + ") is already owned by identity '" + scopeOwner
                            + "' — overlapping funding scopes cannot be independent lanes");
        }
    }

    /** The item payload's transaction plan, or {@code null} for a template step. */
    private static TxPlan itemTxPlan(TxWorkItem item) {
        if (item.getKind() == TxWorkItem.Kind.TX_PLAN) {
            return item.getTxPlan();
        }
        FlowStep step = item.getFlowStep();
        return step != null ? step.getTxPlan() : null;
    }

    /** The single {@link Tx} of a plan, or {@code null} when it is not exactly one Tx. */
    private static Tx singleTx(TxPlan plan) {
        if (plan == null || plan.getTxs().size() != 1 || !(plan.getTxs().get(0) instanceof Tx)) {
            return null;
        }
        return (Tx) plan.getTxs().get(0);
    }

    /**
     * Partitioned policy: assign the item to one of the N precomputed lanes by
     * {@code hash(idempotencyKey) % N} — deterministic and stable across
     * restarts, so a redelivered item always lands on the same lane. A lane
     * name a caller supplied is ignored: the assignment is by hash, not by
     * label. The item's transaction is later pinned to the assigned lane
     * address by {@link #enforceLaneFundingScope}.
     */
    private ResolvedLane partitionLane(TxWorkItem item) {
        String claimKey = item.getIdempotencyKey() != null
                ? item.getIdempotencyKey() : item.getItemId();
        int index = StreamIdentities.partitionIndex(claimKey, partitionLanes.size());
        ResolvedLane lane = partitionLanes.get(index);
        // Cache under the lane name so the planner-side resolvePlannedLane finds it.
        laneByName.putIfAbsent(lane.laneName(), lane);
        return lane;
    }

    /**
     * Precomputes the N deterministic lanes of a partitioned policy. Lane
     * {@code i} is {@code ResolvedLane.ofAddress("part-" + i + "-" + streamId,
     * laneAddresses[i])}, so the label is stream-scoped and the canonical
     * identity is the lane address — stable across restarts. Empty for every
     * other policy.
     */
    private static List<ResolvedLane> buildPartitionLanes(String streamId,
                                                          PartitionedLanes config) {
        if (config == null) {
            return List.of();
        }
        List<String> addresses = config.laneAddresses();
        List<ResolvedLane> lanes = new ArrayList<>(addresses.size());
        for (int i = 0; i < addresses.size(); i++) {
            lanes.add(ResolvedLane.ofAddress("part-" + i + "-" + streamId, addresses.get(i)));
        }
        return List.copyOf(lanes);
    }

    /**
     * Mechanical lane funding-scope enforcement (ADR 0004 Decision 2: a lane
     * "materializes and validates", it never merely labels).
     *
     * <p>An absent funding source is materialized from the lane's declared
     * scope onto a defensive copy of the plan — the caller's objects are never
     * mutated. A present source must equal the lane's declared source; a
     * different source fails the item typed
     * ({@code TXSTREAM_LANE_SCOPE_VIOLATION}) on the same settled-unregistered
     * path as portability failures. Multi-transaction plans are already
     * rejected by the portable validator, so enforcement inspects the single
     * transaction; template-backed steps carry no {@code TxPlan} yet and are
     * covered by later template work.</p>
     */
    private FlowStep enforceLaneFundingScope(TxWorkItem item, FlowStep step, ResolvedLane lane) {
        TxPlan plan = step.getTxPlan();
        Tx tx = singleTx(plan);
        if (tx == null) {
            return step;
        }
        String from = tx.getSender();
        String fromRef = tx.getFromRef();
        boolean hasFrom = from != null && !from.isEmpty();
        boolean hasFromRef = fromRef != null && !fromRef.isEmpty();
        LaneFundingScope scope = lane.fundingScope();
        boolean addressScope = scope.kind() == LaneFundingScope.Kind.ADDRESS;
        if (!hasFrom && !hasFromRef) {
            // Absent: materialize the lane's declared source onto a defensive
            // copy of the plan (YAML round trip = the plan's own deep copy).
            TxPlan copy = TxPlan.from(plan.toYaml());
            Tx copiedTx = (Tx) copy.getTxs().get(0);
            if (addressScope) {
                copiedTx.from(scope.source());
            } else {
                copiedTx.fromRef(scope.source());
            }
            return rebuildStepWithPlan(step, copy);
        }
        boolean matches = addressScope
                ? hasFrom && !hasFromRef && scope.source().equals(from)
                : hasFromRef && !hasFrom && scope.source().equals(fromRef);
        if (!matches) {
            String declared = (addressScope ? "from " : "from_ref ") + scope.source();
            String actual = hasFrom ? "from " + from : "from_ref " + fromRef;
            throw new TxStreamException("TXSTREAM_LANE_SCOPE_VIOLATION",
                    "Item '" + item.getItemId() + "' draws outside its lane's funding scope:"
                            + " lane '" + lane.laneName() + "' declares " + declared
                            + " but the transaction declares " + actual);
        }
        return step;
    }

    private FlowStep rebuildStepWithPlan(FlowStep step, TxPlan plan) {
        FlowStep.Builder builder = FlowStep.builder(step.getId()).withTxPlan(plan);
        if (step.getDescription() != null) {
            builder.withDescription(step.getDescription());
        }
        if (step.getRetryPolicy() != null) {
            builder.withRetryPolicy(step.getRetryPolicy());
        }
        step.getDependencies().forEach(builder::dependsOn);
        step.getNeeds().forEach(builder::needs);
        step.getOutputBindings().forEach(builder::bindOutput);
        return builder.build();
    }

    // ------------------------------------------------------------------
    // Windowing
    // ------------------------------------------------------------------

    private void acceptIntoWindow(ItemState state) {
        BatchState closedWindow = null;
        synchronized (stateLock) {
            windowBuffer.add(state);
            if (windowBuffer.size() == 1) {
                windowOpenedAt = clock.instant();
                scheduleWindowTimerLocked();
            }
            int maxItems = windowPolicy != null ? windowPolicy.getMaxItems() : 1;
            if (windowBuffer.size() >= maxItems) {
                closedWindow = closeWindowLocked();
            }
        }
        if (closedWindow != null) {
            dispatchBatch(closedWindow);
        }
    }

    /**
     * Post-buffer re-check mirroring the systemic-failure rescue: accept can
     * race a stop transition — {@code abort()}, {@code close()}, a drain's
     * stop-accepting — or a dispatcher death whose window drain/flush ran
     * BEFORE this item reached the window buffer. Such a straggler would sit
     * in the buffer forever: nothing is accepted any more to reach the count
     * bound, the age wakeup declines to run on a closed stream, and
     * {@code flush()}/{@code drain()}/{@code close()} already ran — so
     * {@code drain()}/{@code close()} would hang on its promise. The removal
     * is atomic with the buffer under {@link #stateLock}: a concurrent
     * flush/close that already snapshotted the item into a batch wins (the
     * item executes or settles through the batch machinery) and this rescue
     * backs off; otherwise the item settles here, typed like the stop that
     * stranded it ({@code TXSTREAM_ABORTED} / {@code TXSTREAM_CLOSED} /
     * {@code TXSTREAM_UNHEALTHY}).
     */
    private void rescueWindowStraggler(ItemState state) {
        if (healthy && !closed && !aborted && accepting) {
            // Common case: no stop transition raced this accept. A stop that
            // begins after this read is safe without the rescue — the item is
            // already in the window buffer, so that stop's own drain/flush
            // disposes of it.
            return;
        }
        boolean abortedNow;
        boolean stopped;
        boolean standbyNow;
        synchronized (stateLock) {
            abortedNow = aborted;
            stopped = closed || !accepting;
            standbyNow = !closed && ownershipEnabled
                    && ownershipState == OwnershipStatus.State.STANDBY;
            if (healthy && !abortedNow && !stopped) {
                return;
            }
            if (!windowBuffer.remove(state)) {
                return; // already batched or already settled by the stop path
            }
            noteWindowEmptiedLocked();
        }
        releasePermit(state);
        if (abortedNow || stopped) {
            // A stop caused by an ownership step-down (accept raced the fence;
            // the item reached the window buffer only after the fence's drain)
            // is typed as the ownership loss it is, matching the fence's own
            // settlement of queued-but-unstarted work.
            TxStreamException cause;
            if (abortedNow) {
                cause = new TxStreamException("TXSTREAM_ABORTED",
                        abortReason != null ? abortReason : "Stream aborted");
            } else if (standbyNow) {
                cause = ownershipLostCause();
            } else {
                cause = new TxStreamException("TXSTREAM_CLOSED",
                        "Stream stopped accepting before the item's window closed");
            }
            project(state, TxStreamItemStatus.CANCELLED,
                    itemBuilder -> itemBuilder.error(cause), false);
        } else {
            failItem(state, "TXSTREAM_UNHEALTHY",
                    "Stream dispatcher failed before the item could run", null);
        }
    }

    /**
     * Dispatches one template item as its own single-member execution (ADR 0004,
     * iteration 3): builds an {@link ExecutionState} whose definition is the
     * pre-registered, compiled template and whose bindings are the item's
     * parameter values, registers it, and queues it FIFO on the item's lane —
     * the same lane scheduler, two-phase binding, and projection as every other
     * execution, only bypassing the window/planner (which cannot express one
     * item owning a whole flow). The deterministic execution id is still
     * claim-derived, so redelivery of the same (template, bindings) matches.
     */
    private void dispatchTemplate(ItemState state, PreparedItem prepared) {
        ExecutionState execution = new ExecutionState(state.executionId, state.claimKey,
                prepared.templateDefinition(), state.lane, List.of(state), null,
                RequestBindings.ofMembers(List.of(state)), prepared.templateId());
        state.sharedExecution = false;
        state.execution = execution;
        if (executionsById.putIfAbsent(state.executionId, execution) != null) {
            // A live execution already owns this claim-derived id. Fresh
            // template items carry a unique claim key (the reuse guard enforces
            // it, and redelivery attaches at accept), so this is only reachable
            // on an internal invariant break; fail the item typed rather than
            // clobbering the live execution.
            releasePermit(state);
            failItem(state, "TXSTREAM_DISPATCH_FAILED",
                    "Template execution id '" + state.executionId
                            + "' is already claimed by a live execution", null);
            return;
        }
        synchronized (stateLock) {
            LaneQueue lane = laneQueues.computeIfAbsent(
                    state.lane.canonicalSpendingIdentity(), LaneQueue::new);
            lane.queue.add(execution);
            makeReady(lane);
        }
        schedulePump();
        if (!healthy) {
            // Mirror the accept/runPlanning rescue: the dispatcher may have died
            // while this execution was being enqueued.
            if (removeQueuedExecution(execution)) {
                executionsById.remove(execution.executionId, execution);
                releasePermit(state);
                failItem(state, "TXSTREAM_UNHEALTHY",
                        "Stream dispatcher failed before the item could run", null);
            }
        }
        if (!ownershipDispatchAllowed()) {
            // Post-enqueue ownership rescue (mirror of runPlanning's): the fence
            // may have drained the lanes between the accept-path ownership check
            // and this enqueue — the ownership-gated pump would never dispatch
            // this execution, stranding the item unsettled. Settle it CANCELLED,
            // typed; the stateLock arbitration with the fence's drain makes the
            // removal exactly-once.
            if (removeQueuedExecution(execution)) {
                executionsById.remove(execution.executionId, execution);
                releasePermit(state);
                TxStreamException lostCause = ownershipLostCause();
                project(state, TxStreamItemStatus.CANCELLED,
                        itemBuilder -> itemBuilder.error(lostCause), false);
            }
        }
    }

    @Override
    public void flush() {
        if (closed) {
            // Interface contract: flush is a no-op on a closed stream —
            // close() and abort() dispose of the open window through their
            // own internal paths.
            return;
        }
        flushOpenWindow();
    }

    /** Closes and dispatches the open window regardless of lifecycle flags. */
    private void flushOpenWindow() {
        BatchState closedWindow = null;
        synchronized (stateLock) {
            if (!windowBuffer.isEmpty()) {
                closedWindow = closeWindowLocked();
            }
        }
        if (closedWindow != null) {
            dispatchBatch(closedWindow);
        }
    }

    /** Guarded by {@link #stateLock}. */
    private BatchState closeWindowLocked() {
        cancelWindowTimerLocked();
        List<ItemState> members = new ArrayList<>(windowBuffer);
        windowBuffer.clear();
        windowEpoch++;
        String batchId = "batch-" + batchCounter.incrementAndGet();
        BatchState batch = new BatchState(batchId, members);
        for (ItemState member : members) {
            member.batchId = batchId;
        }
        batches.put(batchId, batch);
        return batch;
    }

    /**
     * Publishes the closed window's PLANNED batch snapshot and routes it to
     * the planner — inline for the fast path, through the serialized
     * planning pump (window-close order, on the dispatch executor) otherwise.
     */
    private void dispatchBatch(BatchState batch) {
        publishBatch(batch);
        if (inlinePlanning) {
            runPlanning(batch);
            return;
        }
        synchronized (stateLock) {
            batch.inPlanningQueue = true;
            planningQueue.add(batch);
        }
        schedulePlanning();
    }

    /** Guarded by {@link #stateLock}. */
    private void scheduleWindowTimerLocked() {
        if (windowPolicy == null || !windowPolicy.isTimeBased()) {
            return;
        }
        scheduleWindowWakeupLocked(windowEpoch, windowPolicy.getMaxAge());
    }

    /** Guarded by {@link #stateLock}. */
    private void scheduleWindowWakeupLocked(long epoch, Duration delay) {
        try {
            windowTimer = maintenanceExecutor.schedule(() -> onWindowTimer(epoch),
                    Math.max(0L, delay.toMillis()), TimeUnit.MILLISECONDS);
        } catch (RuntimeException scheduleFailure) {
            // Degraded but bounded: the window still closes on the count
            // bound, flush(), drain(), or close().
            log.warn("TxFlowStream[{}] window-age wakeup could not be scheduled;"
                    + " the open window closes on count/flush/drain only",
                    streamId, scheduleFailure);
        }
    }

    /** Guarded by {@link #stateLock}. */
    private void cancelWindowTimerLocked() {
        if (windowTimer != null) {
            windowTimer.cancel(false);
            windowTimer = null;
        }
    }

    /**
     * Guarded by {@link #stateLock}. Out-of-band removal ({@code cancelItem},
     * the window-straggler rescue) may empty the open window: nothing is left
     * to age out, so the armed wakeup is cancelled, and the epoch is bumped so
     * a wakeup already running for THIS window can never observe a LATER
     * window under an unchanged epoch and re-arm a second timer next to that
     * window's own (the stale-wakeup double-arm window).
     */
    private void noteWindowEmptiedLocked() {
        if (windowBuffer.isEmpty()) {
            windowEpoch++;
            cancelWindowTimerLocked();
        }
    }

    /**
     * Window-age wakeup, run on the caller-owned maintenance scheduler. Epoch
     * guards make stale and spurious wakeups harmless: a wakeup for a window
     * that already closed is a no-op, and a wakeup that fires before the age
     * bound (per the injected clock) re-arms itself for the remainder.
     */
    private void onWindowTimer(long epoch) {
        BatchState closedWindow = null;
        synchronized (stateLock) {
            if (closed || aborted || !healthy) {
                return;
            }
            if (epoch != windowEpoch || windowBuffer.isEmpty()) {
                return; // stale wakeup: the window already closed
            }
            Duration age = Duration.between(windowOpenedAt, clock.instant());
            if (age.compareTo(windowPolicy.getMaxAge()) < 0) {
                // Spurious/early wakeup: tolerate and re-arm for the remainder.
                scheduleWindowWakeupLocked(epoch, windowPolicy.getMaxAge().minus(age));
                return;
            }
            closedWindow = closeWindowLocked();
        }
        // Never plan on the maintenance scheduler's thread beyond handing the
        // batch to the planning pump (the fast path has no timer by
        // construction, so this always goes through the pump).
        if (closedWindow != null) {
            dispatchBatch(closedWindow);
        }
    }

    // ------------------------------------------------------------------
    // Reconciliation observer (ADR 0004, iteration 3)
    // ------------------------------------------------------------------

    /**
     * Arms the first reconciliation pass when the observer is opted in. Called
     * by {@link #start()} after re-attach, so a durable stream's first pass sees
     * the recovered projections. A no-op when no reconciliation interval was
     * configured (read-through repair only — the simple front door and
     * non-scheduler users are unaffected).
     */
    private void startReconciliationObserver() {
        if (reconciliationInterval == null) {
            return;
        }
        synchronized (stateLock) {
            if (closed || aborted) {
                return;
            }
            scheduleReconciliationLocked(reconciliationEpoch);
        }
    }

    /** Guarded by {@link #stateLock}. Mirrors {@link #scheduleWindowWakeupLocked}. */
    private void scheduleReconciliationLocked(long epoch) {
        try {
            reconciliationTimer = maintenanceExecutor.schedule(
                    () -> onReconciliationTick(epoch),
                    Math.max(0L, reconciliationInterval.toMillis()), TimeUnit.MILLISECONDS);
        } catch (RuntimeException scheduleFailure) {
            // Degraded but bounded, and NOT retried: the maintenance scheduler
            // rejected the pass, so PUSH-repair is disabled for the remaining
            // lifetime of this stream (the timer is never re-armed). This is not
            // fatal — read-through repair (getItemStatus/reconcile) still resolves
            // every RECOVERY_REQUIRED item on demand; only the background push is
            // lost. Recreate the stream with a healthy maintenanceExecutor to
            // restore push-repair.
            log.warn("TxFlowStream[{}] reconciliation observer could not be scheduled;"
                    + " push-repair is DISABLED for the lifetime of this stream —"
                    + " RECOVERY_REQUIRED items are repaired by read-through"
                    + " (getItemStatus/reconcile) only",
                    streamId, scheduleFailure);
        }
    }

    /**
     * Guarded by {@link #stateLock}. Bumps the epoch so any already-fired but
     * not-yet-run tick (or one racing the cancel) sees a mismatch and no-ops,
     * and cancels the armed future — mirroring the window-timer cancellation.
     */
    private void cancelReconciliationLocked() {
        reconciliationEpoch++;
        if (reconciliationTimer != null) {
            reconciliationTimer.cancel(false);
            reconciliationTimer = null;
        }
    }

    /**
     * One reconciliation pass, run on the caller-owned maintenance scheduler.
     * Epoch/closed guards make a stale or spurious fire harmless: a fire after
     * close/abort (or superseded by a newer epoch) is a no-op and never re-arms.
     * The pass never dispatches — it only reads the engine and repairs
     * projections — and it re-arms the next pass unless the stream shut down
     * during it.
     */
    private void onReconciliationTick(long epoch) {
        synchronized (stateLock) {
            if (closed || aborted || epoch != reconciliationEpoch) {
                return; // late/stale fire after shutdown or supersession: no-op, no re-arm
            }
        }
        try {
            runReconciliationPass();
        } catch (Throwable passFailure) {
            // A throwing store/listener/reconcile must never kill the observer
            // or the scheduler; per-item work is already isolated, this is the
            // last-resort guard.
            log.warn("TxFlowStream[{}] reconciliation pass failed", streamId, passFailure);
        }
        synchronized (stateLock) {
            if (closed || aborted || epoch != reconciliationEpoch) {
                return;
            }
            scheduleReconciliationLocked(epoch);
        }
    }

    /**
     * Enumerates the stream's {@code RECOVERY_REQUIRED} items and push-repairs
     * each through the existing read-through reconcile path
     * ({@link #reconcileFromSnapshot}) — from the live projection map (phase 1),
     * and for a durable stream additionally from the durable store's non-terminal
     * set (phase 2 — items recovered on a prior/other instance and never loaded
     * into this live map). Work is bounded per fire by
     * {@code reconciliationBatchSize}: any remainder waits for the next pass.
     * Idempotent — an item already terminal is skipped by the transition table,
     * and one still genuinely recovery-required stays put.
     *
     * <p>F2 (bounded fairness): the two phases share the one per-fire budget, but
     * which phase draws from it FIRST alternates every pass. Without this, a
     * persistent live RECOVERY_REQUIRED residency at or above the batch size
     * (e.g. many foreign-process executions this instance can only read-through,
     * so they never leave RECOVERY_REQUIRED) would consume the whole budget in
     * phase 1 on every fire and phase 2 would never run — a newly recovered
     * durable-absent item on the shared store would never be discovered.
     * Alternating guarantees phase 2 gets the budget first at least once every
     * two fires, so a durable-absent item is discovered within a bounded number
     * of fires regardless of live residency, without ever reducing a phase's
     * throughput on the fires where it has priority. Phase 2 is a no-op on a
     * non-durable stream, so the order is immaterial there (phase 1 always draws
     * the full budget).</p>
     */
    private void runReconciliationPass() {
        int budget = reconciliationBatchSize;
        boolean durablePhaseFirst =
                stateStore.isDurable() && (reconciliationPassCount.getAndIncrement() & 1L) == 1L;
        if (durablePhaseFirst) {
            budget = reconcileDurableAbsentPhase(budget);
            reconcileLivePhase(budget);
        } else {
            budget = reconcileLivePhase(budget);
            reconcileDurableAbsentPhase(budget);
        }
    }

    /**
     * Phase 1: push-repair the live projection map's {@code RECOVERY_REQUIRED}
     * items, up to the remaining budget. Returns the budget left for phase 2.
     */
    private int reconcileLivePhase(int budget) {
        for (ItemState state : items.values()) {
            if (budget == 0) {
                return 0; // cap reached; the rest wait for the next fire
            }
            if (state.projection.current().getStatus() != TxStreamItemStatus.RECOVERY_REQUIRED) {
                continue;
            }
            reconcileObserverItem(state);
            budget--;
        }
        return budget;
    }

    /**
     * Phase 2: push-repair durable {@code RECOVERY_REQUIRED} rows not present in
     * this live map (recovered on another instance / not loaded by re-attach),
     * up to the remaining budget. A no-op on a non-durable stream. Returns the
     * budget left.
     */
    private int reconcileDurableAbsentPhase(int budget) {
        if (budget == 0 || !stateStore.isDurable()) {
            return budget;
        }
        List<String> nonTerminal;
        try {
            nonTerminal = stateStore.listNonTerminalItemIds(streamId);
        } catch (RuntimeException listFailure) {
            log.warn("TxFlowStream[{}] reconciliation could not enumerate durable non-terminal"
                    + " items", streamId, listFailure);
            return budget;
        }
        Map<String, MemberRef> plannedIndex = null; // built lazily on first durable candidate
        for (String itemId : nonTerminal) {
            if (budget == 0) {
                return 0;
            }
            if (items.containsKey(itemId)) {
                continue; // already handled by the live pass (or being handled live)
            }
            Optional<TxStreamItemResult> stored;
            try {
                stored = stateStore.getItem(streamId, itemId);
            } catch (RuntimeException readFailure) {
                log.warn("TxFlowStream[{}] reconciliation store read failed for item '{}'",
                        streamId, itemId, readFailure);
                continue;
            }
            if (stored.isEmpty()
                    || stored.get().getStatus() != TxStreamItemStatus.RECOVERY_REQUIRED) {
                // The observer only push-repairs RECOVERY_REQUIRED items; PLANNED/
                // SUBMITTED durable rows are covered by live watching / re-attach.
                continue;
            }
            if (plannedIndex == null) {
                plannedIndex = buildPlannedIndex();
            }
            if (reconcileDurableAbsentItem(itemId, plannedIndex)) {
                budget--;
            }
        }
        return budget;
    }

    /**
     * Reconciles one live RECOVERY_REQUIRED item through the existing
     * read-through path, isolated so a throwing store or listener can never kill
     * the observer or its scheduler (listener callbacks are already isolated in
     * {@link #project}; this is the last-resort guard for the rest of the path).
     */
    private void reconcileObserverItem(ItemState state) {
        try {
            reconcileFromSnapshot(state);
        } catch (RuntimeException reconcileFailure) {
            log.warn("TxFlowStream[{}] reconciliation failed for item '{}'",
                    streamId, state.item.getItemId(), reconcileFailure);
        }
    }

    /**
     * Reconstructs a durable RECOVERY_REQUIRED item that is not in this live map
     * from its persisted plan (exactly as re-attach does) and push-repairs it.
     * Reads-and-repairs only: it publishes the reconstructed item so a later
     * pass and {@link #getItemStatus(String)} find it, but never enqueues it for
     * dispatch. Returns whether it was reconstructed and reconciled (so it
     * counts against the per-fire cap); a missing plan or a race with a live
     * insert returns {@code false}.
     */
    private boolean reconcileDurableAbsentItem(String itemId, Map<String, MemberRef> index) {
        MemberRef ref = index.get(itemId);
        if (ref == null) {
            // No planned record to reconstruct from — not reconcilable by the
            // observer; re-attach's ghost reaper handles truly abandoned rows.
            return false;
        }
        ItemState state;
        try {
            state = reconstructItemState(ref.record, ref.member, ref.shared);
        } catch (RuntimeException reconstructFailure) {
            log.warn("TxFlowStream[{}] reconciliation could not reconstruct durable item '{}'",
                    streamId, itemId, reconstructFailure);
            return false;
        }
        if (items.putIfAbsent(itemId, state) != null) {
            return false; // raced a live insert; the live pass owns it now
        }
        registerReattachedClaim(state);
        reconcileObserverItem(state);
        return true;
    }

    /**
     * Builds an itemId → (planned record, member, shared) index from the durable
     * store's planned records, so a durable-absent recovery item can be
     * reconstructed. Built lazily, at most once per pass.
     */
    private Map<String, MemberRef> buildPlannedIndex() {
        Map<String, MemberRef> index = new HashMap<>();
        for (TxStreamPlannedRecord record : stateStore.listPlanned(streamId)) {
            boolean shared = record.members().size() > 1;
            for (TxStreamPlannedRecord.Member member : record.members()) {
                index.putIfAbsent(member.itemId(), new MemberRef(record, member, shared));
            }
        }
        return index;
    }

    // ------------------------------------------------------------------
    // Planning
    // ------------------------------------------------------------------

    private void schedulePlanning() {
        if (!started || !healthy) return;
        if (planningActive.compareAndSet(false, true)) {
            try {
                executor.execute(this::planPump);
            } catch (Throwable schedulingFailure) {
                planningActive.set(false);
                onSystemicFailure(schedulingFailure);
            }
        }
    }

    /** Serialized planning loop: plans closed windows in window-close order. */
    private void planPump() {
        try {
            while (healthy) {
                BatchState batch;
                synchronized (stateLock) {
                    batch = planningQueue.poll();
                    if (batch != null) {
                        batch.inPlanningQueue = false;
                    }
                }
                if (batch == null) break;
                runPlanning(batch);
            }
        } catch (Throwable unexpected) {
            onSystemicFailure(unexpected);
        } finally {
            planningActive.set(false);
            boolean more;
            synchronized (stateLock) {
                more = healthy && !planningQueue.isEmpty();
            }
            if (more) schedulePlanning();
        }
    }

    /**
     * Plans one closed window: planner (isolated) → plan validation (typed,
     * whole-plan) → omitted-item settlement → execution materialization →
     * lane enqueue. Planner and validation failures fail only this window's
     * items typed; they never kill the worker or the stream.
     */
    private void runPlanning(BatchState batch) {
        synchronized (stateLock) {
            batch.inPlanningQueue = false;
        }
        if (aborted) {
            String reason = abortReason;
            failWindowItems(batch, TxStreamItemStatus.CANCELLED,
                    new TxStreamException("TXSTREAM_ABORTED",
                            reason != null ? reason : "Stream aborted"));
            return;
        }
        if (!healthy) {
            failWindowItems(batch, TxStreamItemStatus.FAILED,
                    new TxStreamException("TXSTREAM_UNHEALTHY",
                            "Stream dispatcher failed before the item could run"));
            return;
        }
        if (!ownershipDispatchAllowed()) {
            // Ownership entry check (composition probe 1): loss of ownership is
            // treated exactly like abort in the planning pipeline. This batch
            // may have reached the planning queue only AFTER stepDownFenced
            // drained it (an accept racing the fence), or been handed to the
            // pump while this instance was already refused by the dispatch gate
            // (stale ACTIVE past lease expiry — the gate's javadoc: refused even
            // before the renewal formally fences it) — either way the
            // ownership-gated pump would never dispatch what this plan produces,
            // so the window's live items settle CANCELLED, typed, here.
            failWindowItems(batch, TxStreamItemStatus.CANCELLED, ownershipLostCause());
            return;
        }
        List<ItemState> live = new ArrayList<>();
        for (ItemState member : batch.membersView()) {
            if (!member.prePlanCancelled && !member.projection.isSettled()) {
                live.add(member);
            }
        }
        if (live.isEmpty()) {
            return; // everything cancelled before planning; batch derives from members
        }
        Map<String, ItemState> liveById = new LinkedHashMap<>();
        List<TxWorkItem> windowItems = new ArrayList<>(live.size());
        Map<String, TxStreamPlanningContext.PlanningSeed> seeds = new HashMap<>();
        for (ItemState member : live) {
            liveById.put(member.item.getItemId(), member);
            windowItems.add(member.item);
            seeds.put(member.item.getItemId(), new TxStreamPlanningContext.PlanningSeed(
                    member.claimKey, member.lane, member.enforcedStep));
        }
        TxStreamPlan plan;
        try {
            // The planner is isolated: a throw (or a null plan) fails only
            // this window's items, typed, and the worker survives.
            plan = planner.plan(new TxStreamPlanningContext(streamId, windowItems,
                    idFactory, seeds));
            if (plan == null) {
                throw new TxStreamException("TXSTREAM_PLANNER_FAILED",
                        "Planner returned a null plan");
            }
        } catch (Throwable plannerFailure) {
            log.warn("TxFlowStream[{}] planner failed for batch '{}'",
                    streamId, batch.batchId, plannerFailure);
            failWindowItems(batch, TxStreamItemStatus.FAILED,
                    plannerFailure instanceof TxStreamException
                            && "TXSTREAM_PLANNER_FAILED"
                                    .equals(((TxStreamException) plannerFailure).getCode())
                            ? (TxStreamException) plannerFailure
                            : new TxStreamException("TXSTREAM_PLANNER_FAILED",
                                    "Planner failed for batch '" + batch.batchId + "': "
                                            + plannerFailure.getMessage(), plannerFailure));
            return;
        }
        List<ExecutionState> executions;
        try {
            executions = materializePlan(batch, plan, liveById);
        } catch (TxStreamException planRejection) {
            // Whole-plan rejection: every live window item fails typed.
            failWindowItems(batch, TxStreamItemStatus.FAILED, planRejection);
            return;
        } catch (Throwable unexpected) {
            log.warn("TxFlowStream[{}] plan materialization failed for batch '{}'",
                    streamId, batch.batchId, unexpected);
            failWindowItems(batch, TxStreamItemStatus.FAILED,
                    new TxStreamException("TXSTREAM_PLANNER_FAILED",
                            "Planning failed for batch '" + batch.batchId + "'", unexpected));
            return;
        }
        // Items the planner omitted fail typed; the rest of the plan proceeds.
        Set<String> mapped = new HashSet<>();
        for (ExecutionState execution : executions) {
            for (ItemState member : execution.members) {
                mapped.add(member.item.getItemId());
            }
        }
        for (ItemState member : live) {
            if (!mapped.contains(member.item.getItemId())) {
                releasePermit(member);
                failItem(member, "TXSTREAM_PLAN_OMITTED",
                        "The planner omitted item '" + member.item.getItemId()
                                + "' from the plan for batch '" + batch.batchId + "'", null);
            }
        }
        List<String> executionIds = new ArrayList<>(executions.size());
        for (ExecutionState execution : executions) {
            // Already registered in executionsById by materializePlan (the
            // GAP-1 putIfAbsent enforcement point).
            executionIds.add(execution.executionId);
        }
        batch.setRunning(executionIds);
        publishBatch(batch);
        synchronized (stateLock) {
            for (ExecutionState execution : executions) {
                LaneQueue lane = laneQueues.computeIfAbsent(
                        execution.lane.canonicalSpendingIdentity(), LaneQueue::new);
                lane.queue.add(execution);
                makeReady(lane);
            }
        }
        schedulePump();
        if (!healthy) {
            // Mirror of the accept-side re-check: if the dispatcher died
            // while these executions were being enqueued, the systemic
            // failure handler may have drained the lanes before they arrived.
            for (ExecutionState execution : executions) {
                if (removeQueuedExecution(execution)) {
                    executionsById.remove(execution.executionId, execution);
                    for (ItemState member : execution.members) {
                        releasePermit(member);
                        failItem(member, "TXSTREAM_UNHEALTHY",
                                "Stream dispatcher failed before the item could run", null);
                    }
                }
            }
        }
        if (!ownershipDispatchAllowed()) {
            // Post-enqueue ownership rescue (composition probe 1, mirroring the
            // !healthy rescue above): the ownership fence may have fired while
            // this window was INSIDE planner.plan(...) — the batch was in no
            // queue when stepDownFenced drained, so its executions land in the
            // lane queues only now, where the ownership-gated pump would never
            // dispatch them (stranding them unsettled; drain()/close() would
            // hang, and a later reclaim could dispatch executions whose durable
            // rows the new owner already reaped). Settle them CANCELLED, typed.
            // Exactly one of {the fence's drain, this rescue} wins the removal —
            // both mutate the lane queues under stateLock.
            TxStreamException lostCause = ownershipLostCause();
            for (ExecutionState execution : executions) {
                if (removeQueuedExecution(execution)) {
                    executionsById.remove(execution.executionId, execution);
                    for (ItemState member : execution.members) {
                        releasePermit(member);
                        project(member, TxStreamItemStatus.CANCELLED,
                                itemBuilder -> itemBuilder.error(lostCause), false);
                    }
                }
            }
        }
    }

    /**
     * Validates the plan's mechanically checkable rules and materializes the
     * execution states (two passes: validate everything, then mutate). The
     * genuine-bug checks all throw {@code TXSTREAM_PLAN_INVALID}: an item mapped
     * more than once across the plan, an item in two different flows, a mapping
     * to an item outside the window, a mapping to a step absent from the flow, a
     * blank/oversized/duplicate flow claim key, a claim key colliding with a
     * live in-flight execution, an empty mapping, and an <em>orphan</em> flow
     * step mapped by no item. A flow whose members span lanes (or whose declared
     * lane is not the members' lane) throws {@code TXSTREAM_PLAN_CROSS_LANE}.
     * Both reject the whole plan.
     * <p>
     * A single step MAY, however, be mapped by MULTIPLE items — the
     * {@code batching()} planner merges several payment items into one shared
     * step, and each member is projected transaction-granularly from that one
     * step's outcome. ({@code plan.executions()} is an immutable copy that admits
     * no null elements, so per-element null checks are unnecessary here.)
     */
    private List<ExecutionState> materializePlan(BatchState batch, TxStreamPlan plan,
                                                 Map<String, ItemState> liveById) {
        Set<String> mappedItemIds = new HashSet<>();
        Set<String> claimKeys = new HashSet<>();
        for (PlannedExecution planned : plan.executions()) {
            if (planned.items().isEmpty()) {
                throw planInvalid(batch, "planned flow '" + planned.flow().getId()
                        + "' maps no items");
            }
            String flowId = planned.flow().getId();
            if (flowId == null || flowId.isBlank()) {
                throw planInvalid(batch, "a planned flow has a blank flow id");
            }
            String claimKey = planned.idempotencyKey();
            try {
                FlowStoreTextPolicy.requireIdentifier(claimKey, "flow idempotency key",
                        FlowStoreTextPolicy.MAX_IDEMPOTENCY_KEY_BYTES);
            } catch (IllegalArgumentException badKey) {
                throw planInvalid(batch, "planned flow '" + flowId
                        + "' has a blank or oversized idempotency key: " + badKey.getMessage());
            }
            if (!claimKeys.add(claimKey)) {
                throw planInvalid(batch, "flow idempotency key '" + claimKey
                        + "' is used by two planned flows");
            }
            // GAP-1: the claim-derived execution id must not collide with a
            // LIVE (queued or in-flight) execution from an earlier window —
            // registering it would clobber that execution's dispatch state.
            // Planning is serialized, so this pre-check plus the putIfAbsent
            // registration below is race-free.
            ExecutionState liveOwner = executionsById.get(
                    StreamIdentities.executionId(namespace, claimKey));
            if (liveOwner != null) {
                throw planInvalid(batch, "planned flow '" + flowId
                        + "' claims idempotency key '" + claimKey
                        + "', which is still claimed by the live execution '"
                        + liveOwner.executionId + "' from an earlier window — a claim key"
                        + " cannot be reused while its execution is queued or in flight");
            }
            ResolvedLane lane = resolvePlannedLane(planned.laneName());
            if (lane == null) {
                throw planInvalid(batch, "planned flow '" + flowId + "' names lane '"
                        + planned.laneName() + "', which is not an established lane of"
                        + " this stream");
            }
            Set<String> stepIds = new HashSet<>();
            for (TxStreamPlannedItem mapping : planned.items()) {
                ItemState member = liveById.get(mapping.itemId());
                if (member == null) {
                    throw planInvalid(batch, "mapping references item '" + mapping.itemId()
                            + "', which is not part of this window");
                }
                if (!mappedItemIds.add(mapping.itemId())) {
                    throw planInvalid(batch, "item '" + mapping.itemId()
                            + "' is mapped more than once in the plan");
                }
                if (mapping.stepId().isBlank()) {
                    throw planInvalid(batch, "item '" + mapping.itemId()
                            + "' is mapped to a blank step id");
                }
                // A step MAY be mapped by multiple items: the batching() planner
                // merges several payment items into ONE shared step so item
                // status is transaction-granular (they share one transaction's
                // fate). Only track that the step is mapped, for the
                // no-orphan-step check below — two items on one step is
                // legitimate, not a bug. (An item mapped more than once is still
                // rejected above; an item in two flows is caught by mappedItemIds.)
                stepIds.add(mapping.stepId());
                if (planned.flow().getStep(mapping.stepId()).isEmpty()) {
                    throw planInvalid(batch, "item '" + mapping.itemId()
                            + "' is mapped to step '" + mapping.stepId()
                            + "', which does not exist in flow '" + flowId + "'");
                }
                if (!member.lane.canonicalSpendingIdentity()
                        .equals(lane.canonicalSpendingIdentity())) {
                    throw new TxStreamException("TXSTREAM_PLAN_CROSS_LANE",
                            "Plan for batch '" + batch.batchId + "' is invalid: flow '" + flowId
                                    + "' runs on lane '" + planned.laneName() + "' (identity '"
                                    + lane.canonicalSpendingIdentity() + "') but its member item '"
                                    + mapping.itemId() + "' belongs to lane identity '"
                                    + member.lane.canonicalSpendingIdentity()
                                    + "' — a planned flow's items must share exactly one lane");
                }
            }
            // Extra steps the mapping does not cover would execute
            // transactions no item owns — no receipt, no cancellation, no
            // projection would ever cover them.
            for (FlowStep step : planned.flow().getSteps()) {
                if (!stepIds.contains(step.getId())) {
                    throw planInvalid(batch, "flow '" + flowId + "' declares step '"
                            + step.getId() + "', which is not mapped to any item — every"
                            + " step of a planned flow must be mapped to an item");
                }
            }
        }
        // Everything validated; materialize and bind member planning fields.
        List<ExecutionState> executions = new ArrayList<>(plan.executions().size());
        for (PlannedExecution planned : plan.executions()) {
            ResolvedLane lane = resolvePlannedLane(planned.laneName());
            String executionId = StreamIdentities.executionId(namespace,
                    planned.idempotencyKey());
            List<ItemState> members = new ArrayList<>(planned.items().size());
            for (TxStreamPlannedItem mapping : planned.items()) {
                members.add(liveById.get(mapping.itemId()));
            }
            ExecutionState execution = new ExecutionState(executionId,
                    planned.idempotencyKey(), planned.flow(), lane, members, batch.batchId,
                    RequestBindings.ofMembers(members));
            boolean shared = members.size() > 1;
            for (TxStreamPlannedItem mapping : planned.items()) {
                ItemState member = liveById.get(mapping.itemId());
                member.executionId = executionId;
                member.flowId = planned.flow().getId();
                member.stepId = mapping.stepId();
                member.sharedExecution = shared;
                member.execution = execution;
            }
            executions.add(execution);
        }
        // GAP-1 enforcement point: register the plan's executions as live via
        // putIfAbsent so a claim key can never clobber a LIVE execution from
        // an earlier window even if one slipped past the validation
        // pre-check. A collision unwinds this plan's registrations and
        // rejects the whole plan — the live execution is untouched.
        List<ExecutionState> registered = new ArrayList<>(executions.size());
        for (ExecutionState execution : executions) {
            ExecutionState liveOwner = executionsById.putIfAbsent(
                    execution.executionId, execution);
            if (liveOwner != null) {
                for (ExecutionState undo : registered) {
                    executionsById.remove(undo.executionId, undo);
                }
                throw planInvalid(batch, "planned flow '" + execution.flowId()
                        + "' claims idempotency key '" + execution.claimKey
                        + "', which is still claimed by the live execution '"
                        + liveOwner.executionId + "'");
            }
            registered.add(execution);
        }
        return executions;
    }

    private TxStreamException planInvalid(BatchState batch, String detail) {
        return new TxStreamException("TXSTREAM_PLAN_INVALID",
                "Plan for batch '" + batch.batchId + "' is invalid: " + detail);
    }

    /**
     * Resolves a planner-declared lane name against the stream's established
     * lanes. Planners never trigger new lane resolution: the single static
     * lane matches by name, and dynamically named lanes must already have
     * been resolved by their member items at accept time.
     */
    private ResolvedLane resolvePlannedLane(String laneName) {
        if (staticLane != null) {
            return staticLane.laneName().equals(laneName) ? staticLane : null;
        }
        return laneByName.get(laneName);
    }

    /** Fails every unsettled, not-pre-cancelled member of a window typed. */
    private void failWindowItems(BatchState batch, TxStreamItemStatus target,
                                 TxStreamException cause) {
        batch.noteFailure(cause);
        for (ItemState member : batch.membersView()) {
            if (member.prePlanCancelled || member.projection.isSettled()) {
                continue;
            }
            releasePermit(member);
            project(member, target, builder -> builder.error(cause), false);
        }
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    /** Removes a queued execution from its lane FIFO; {@code false} if not queued. */
    private boolean removeQueuedExecution(ExecutionState execution) {
        synchronized (stateLock) {
            LaneQueue lane = laneQueues.get(execution.lane.canonicalSpendingIdentity());
            return lane != null && lane.queue.remove(execution);
        }
    }

    /** Guarded by {@link #stateLock}. */
    private void makeReady(LaneQueue lane) {
        if (!lane.inRing && lane.inFlight == null && !lane.queue.isEmpty()) {
            readyRing.add(lane);
            lane.inRing = true;
        }
    }

    /**
     * Claims the next dispatchable execution under the global in-flight cap
     * with round-robin fairness over ready lanes; {@code null} when nothing
     * can dispatch. Guarded by {@link #stateLock} internally.
     */
    private ExecutionState claimNext() {
        synchronized (stateLock) {
            // Ownership fence: never dispatch unless this instance is the current
            // ACTIVE lease-holder (or ownership is disabled). Returning null here
            // does not busy-spin — schedulePump() is the re-arm point and is
            // gated identically, so the pump simply does not re-enter.
            if (!ownershipDispatchAllowed()) {
                return null;
            }
            while (inFlightCount < maxInFlight) {
                LaneQueue lane = readyRing.poll();
                if (lane == null) {
                    return null;
                }
                lane.inRing = false;
                if (lane.inFlight != null || lane.queue.isEmpty()) {
                    continue; // stale ring entry (cancelled execution / raced completion)
                }
                ExecutionState next = lane.queue.poll();
                lane.inFlight = next;
                inFlightCount++;
                for (ItemState member : next.members) {
                    releasePermit(member);
                }
                return next;
            }
            return null;
        }
    }

    private void finishLane(ExecutionState execution) {
        synchronized (stateLock) {
            LaneQueue lane = laneQueues.get(execution.lane.canonicalSpendingIdentity());
            if (lane != null && lane.inFlight == execution) {
                lane.inFlight = null;
                inFlightCount--;
                makeReady(lane);
            }
        }
    }

    /**
     * Whether the partitioned dispatch gate is open (FINDING-1). Always open for
     * the non-partitioned modes; for {@link LanePolicy#partitioned(PartitionedLanes)}
     * it opens only once {@link #start()} confirms the fan-out bootstrap funded
     * the lanes, so no partitioned execution can dispatch onto an unfunded lane.
     */
    private boolean bootstrapGateOpen() {
        return laneMode != LanePolicy.Mode.PARTITIONED || bootstrapSatisfied;
    }

    private void schedulePump() {
        if (!started || !healthy || !bootstrapGateOpen() || !ownershipDispatchAllowed()) return;
        if (pumpActive.compareAndSet(false, true)) {
            try {
                executor.execute(this::pump);
            } catch (Throwable schedulingFailure) {
                pumpActive.set(false);
                onSystemicFailure(schedulingFailure);
            }
        }
    }

    /**
     * Work-scheduling loop: claims ready lanes' head executions under the cap
     * and submits each as its own executor task, so different lanes may
     * dispatch concurrently while per-lane exclusivity is guaranteed by the
     * claim.
     */
    private void pump() {
        try {
            while (healthy) {
                ExecutionState next = claimNext();
                if (next == null) break;
                try {
                    executor.execute(() -> runDispatch(next));
                } catch (RuntimeException | Error schedulingFailure) {
                    failMembers(next, "TXSTREAM_UNHEALTHY",
                            "Stream dispatcher failed before the item could run",
                            schedulingFailure);
                    executionsById.remove(next.executionId, next);
                    finishLane(next);
                    onSystemicFailure(schedulingFailure);
                    break;
                }
            }
        } catch (Throwable unexpected) {
            onSystemicFailure(unexpected);
        } finally {
            pumpActive.set(false);
            boolean more;
            synchronized (stateLock) {
                more = healthy && inFlightCount < maxInFlight && !readyRing.isEmpty();
            }
            if (more) schedulePump();
        }
    }

    private void runDispatch(ExecutionState execution) {
        if (aborted) {
            // Dispatch resources are released on abort; work claimed before
            // the abort but not yet started never reaches the engine.
            String reason = abortReason;
            TxStreamException cause = new TxStreamException("TXSTREAM_ABORTED",
                    reason != null ? reason : "Stream aborted");
            for (ItemState member : execution.members) {
                project(member, TxStreamItemStatus.CANCELLED,
                        itemBuilder -> itemBuilder.error(cause), false);
            }
            executionsById.remove(execution.executionId, execution);
            finishLane(execution);
            // A straggler accepted in the abort window may already be queued
            // behind this claim; the freed slot must wake the pump so that
            // work is claimed and settles (CANCELLED via this same branch) —
            // without the wakeup it would never settle and drain() would hang.
            schedulePump();
            return;
        }
        dispatch(execution);
    }

    /**
     * Dispatches one claimed execution. {@code finishLane} must run exactly
     * once per claimed execution; the paths are:
     * <ol>
     *   <li>abort branch in {@link #runDispatch} — finishLane there;</li>
     *   <li>write-ahead binding failure — {@code finally} below (no start);</li>
     *   <li>engine start failure — {@code finally} below (no start);</li>
     *   <li>unexpected pre-start failure — {@code finally} below (no start);</li>
     *   <li>successful start with the completion observer registered —
     *       {@link #onExecutionComplete}'s {@code finally}, exactly once;</li>
     *   <li>successful start whose completion observer could NOT be
     *       registered — never: the lane is deliberately left busy (see
     *       {@link #afterStart}) because the execution still occupies its
     *       spending identity.</li>
     * </ol>
     */
    private void dispatch(ExecutionState execution) {
        EngineGateway.ExecutionHandle handle = null;
        try {
            try {
                // Two-phase binding, phase 1: write-ahead DISPATCHING binding
                // for EVERY member — authoritative, fails closed before the
                // engine is invoked. A flow must never execute without a
                // durable record of which items it belongs to. In durable mode
                // the planned request is persisted alongside the binding (still
                // before start), so a crash before start can be re-dispatched
                // from the stored plan; the no-secrets rule is enforced here.
                for (ItemState member : execution.members) {
                    stateStore.bind(member.item.getItemId(), new TxStreamBinding(
                            execution.executionId, execution.flowId(), member.stepId,
                            execution.lane.laneName()));
                }
                persistPlannedIfDurable(execution);
            } catch (TxStreamException failClosed) {
                // No-secrets rejection or a planned-flow write failure: fail the
                // items closed with the typed code, before the engine is
                // invoked.
                failMembers(execution, failClosed.getCode(), failClosed.getMessage(), failClosed);
                return;
            } catch (RuntimeException bindFailure) {
                failMembers(execution, "TXSTREAM_BINDING_FAILED",
                        "Write-ahead binding failed for execution '"
                                + execution.executionId + "'", bindFailure);
                return;
            }
            for (ItemState member : execution.members) {
                String memberExecutionId = execution.executionId;
                String memberStepId = member.stepId;
                String laneName = execution.lane.laneName();
                project(member, TxStreamItemStatus.PLANNED,
                        builder -> builder.executionId(memberExecutionId)
                                .stepId(memberStepId).laneName(laneName), false);
            }
            try {
                // Phase 2: start with the explicit claim-derived execution id.
                handle = gateway.start(buildRequest(execution));
            } catch (RuntimeException startFailure) {
                confirmMembers(execution, BindingOutcome.REJECTED);
                failMembers(execution, "TXSTREAM_DISPATCH_FAILED",
                        "Engine start failed for execution '" + execution.executionId + "'",
                        startFailure);
                return;
            }
        } catch (Throwable unexpected) {
            // Dispatcher supervision, pre-start only: nothing is running yet,
            // so failing these items typed and freeing the lane is safe and
            // keeps the stream healthy.
            failMembers(execution, "TXSTREAM_DISPATCH_FAILED",
                    "Unexpected dispatch failure for execution '"
                            + execution.executionId + "'", unexpected);
            return;
        } finally {
            if (handle == null) {
                executionsById.remove(execution.executionId, execution);
                finishLane(execution);
                schedulePump();
            }
        }
        afterStart(execution, handle);
    }

    /**
     * Post-start seam. From here the engine execution is RUNNING, so this
     * method must be total: no failure below may fail an item as if the
     * transaction never happened, and none may free the lane while the
     * execution occupies its spending identity.
     */
    private void afterStart(ExecutionState execution, EngineGateway.ExecutionHandle handle) {
        execution.handle = handle;
        for (ItemState member : execution.members) {
            member.handle = handle;
        }
        String cancelReason = execution.pendingCancelReason;
        if (cancelReason != null) {
            // Forwarding a pending cancel is a best-effort signal: a throwing
            // cancel channel must not fail a running item or free its lane.
            try {
                handle.requestCancel(cancelReason);
            } catch (RuntimeException cancelFailure) {
                log.warn("TxFlowStream[{}] pending cancel signal failed for execution '{}'",
                        streamId, execution.executionId, cancelFailure);
            }
        }
        // Phase 3: confirm the binding outcome. The execution has already
        // started, so this cannot fail closed; an unresolved DISPATCHING
        // binding is repaired from the engine store on restart. Outcome
        // classification reads engine state and is isolated the same way.
        BindingOutcome outcome;
        try {
            outcome = classifyStartOutcome(handle);
        } catch (RuntimeException classifyFailure) {
            log.warn("TxFlowStream[{}] start outcome classification failed for execution '{}'",
                    streamId, execution.executionId, classifyFailure);
            outcome = BindingOutcome.CREATED;
        }
        confirmMembers(execution, outcome);
        try {
            handle.completion().whenComplete(
                    (result, failure) -> onExecutionComplete(execution, result, failure));
        } catch (Throwable registrationFailure) {
            // The execution is running but this stream can no longer observe
            // it. Failing the items would be dishonest (the transactions may
            // land) and freeing the lane would put a second execution onto
            // the same spending identity. So: settle every member
            // RECOVERY_REQUIRED — honest (running, unobserved), with the
            // execution id on the result for operator reconcile/recover —
            // leave the lane busy, and treat the gateway as broken: a stream
            // that cannot observe its executions must stop dispatching
            // (isHealthy() false, pending items fail typed).
            log.error("TxFlowStream[{}] completion observer registration failed for"
                            + " execution '{}'; its items settle RECOVERY_REQUIRED",
                    streamId, execution.executionId, registrationFailure);
            TxStreamException unobservable = new TxStreamException(
                    "TXSTREAM_EXECUTION_UNOBSERVABLE",
                    "Execution '" + execution.executionId + "' started but its completion"
                            + " observer could not be registered; the execution is running"
                            + " unobserved — reconcile once the engine reports its outcome",
                    registrationFailure);
            for (ItemState member : execution.members) {
                project(member, TxStreamItemStatus.RECOVERY_REQUIRED,
                        builder -> builder.error(unobservable), false);
            }
            onSystemicFailure(registrationFailure);
        }
    }

    private void onExecutionComplete(ExecutionState execution, FlowExecutionResult result,
                                     Throwable failure) {
        try {
            if (failure != null || result == null) {
                failMembers(execution, "TXSTREAM_EXECUTION_FAILED",
                        "Engine completion failed for execution '"
                                + execution.executionId + "'", failure);
            } else {
                // One memoized P2 snapshot read serves every member of this
                // terminal pass: MATCHED stored flows need it for hash
                // recovery and per-member attempt evidence, and reading it
                // once keeps an N-member flow at one store read, not N.
                SnapshotLookup snapshots = new SnapshotLookup(execution.executionId);
                for (ItemState member : execution.members) {
                    try {
                        projectTerminal(member, execution, result, snapshots);
                    } catch (Throwable projectionFailure) {
                        log.warn("TxFlowStream[{}] terminal projection failed for item '{}'",
                                streamId, member.item.getItemId(), projectionFailure);
                        failItem(member, "TXSTREAM_PROJECTION_FAILED",
                                "Terminal projection failed for item '"
                                        + member.item.getItemId() + "'", projectionFailure);
                    }
                }
            }
        } finally {
            executionsById.remove(execution.executionId, execution);
            finishLane(execution);
            schedulePump();
        }
    }

    private void onSystemicFailure(Throwable failure) {
        healthy = false;
        log.error("TxFlowStream[{}] dispatcher failed; failing pending items", streamId, failure);
        List<ItemState> pending = new ArrayList<>();
        synchronized (stateLock) {
            cancelWindowTimerLocked();
            ItemState windowed;
            while ((windowed = windowBuffer.poll()) != null) {
                pending.add(windowed);
            }
            BatchState batch;
            while ((batch = planningQueue.poll()) != null) {
                batch.inPlanningQueue = false;
                for (ItemState member : batch.membersView()) {
                    if (!member.prePlanCancelled && !member.projection.isSettled()) {
                        pending.add(member);
                    }
                }
            }
            for (LaneQueue lane : laneQueues.values()) {
                ExecutionState queued;
                while ((queued = lane.queue.poll()) != null) {
                    executionsById.remove(queued.executionId, queued);
                    pending.addAll(queued.members);
                }
                lane.inRing = false;
            }
            readyRing.clear();
        }
        for (ItemState state : pending) {
            releasePermit(state);
            failItem(state, "TXSTREAM_UNHEALTHY",
                    "Stream dispatcher failed before the item could run", failure);
        }
    }

    /**
     * Fails every pending partitioned execution when the fan-out bootstrap could
     * not fund the lanes (FINDING-1). It drains the window buffer, the planning
     * queue, and — the reason this exists separately from the gate — any
     * executions a standalone {@code reattach()} re-dispatched into the lane
     * queues before this {@code start()} ran the bootstrap. Each member settles
     * typed {@code TXSTREAM_BOOTSTRAP_FAILED}, so a subsequent {@code close()} /
     * {@code drain()} finds settled promises and returns instead of hanging.
     * Unlike {@link #onSystemicFailure(Throwable)} it does NOT mark the stream
     * unhealthy: the stream never opened for work, and the closed dispatch gate
     * ({@code bootstrapSatisfied} stays {@code false}) is what keeps anything
     * from dispatching onto the unfunded lanes.
     */
    private void failPendingPartitioned(TxStreamException failure) {
        List<ItemState> pending = new ArrayList<>();
        synchronized (stateLock) {
            cancelWindowTimerLocked();
            ItemState windowed;
            while ((windowed = windowBuffer.poll()) != null) {
                pending.add(windowed);
            }
            BatchState batch;
            while ((batch = planningQueue.poll()) != null) {
                batch.inPlanningQueue = false;
                for (ItemState member : batch.membersView()) {
                    if (!member.prePlanCancelled && !member.projection.isSettled()) {
                        pending.add(member);
                    }
                }
            }
            for (LaneQueue lane : laneQueues.values()) {
                ExecutionState queued;
                while ((queued = lane.queue.poll()) != null) {
                    executionsById.remove(queued.executionId, queued);
                    for (ItemState member : queued.members) {
                        if (!member.projection.isSettled()) {
                            pending.add(member);
                        }
                    }
                }
                lane.inRing = false;
            }
            readyRing.clear();
        }
        for (ItemState state : pending) {
            releasePermit(state);
            failItem(state, "TXSTREAM_BOOTSTRAP_FAILED", failure.getMessage(), failure);
        }
    }

    private BindingOutcome classifyStartOutcome(EngineGateway.ExecutionHandle handle) {
        FlowExecutionResult result = handle.resultIfDone();
        if (result == null) return BindingOutcome.CREATED;
        FlowError error = result.error();
        if (error != null && ("TXFLOW_STORED_EXECUTION_TERMINAL".equals(error.code())
                || (result.state() == FlowExecutionState.RECOVERY_REQUIRED
                && "TXFLOW_RECOVERY_REQUIRED".equals(error.code())
                && result.steps().isEmpty()))) {
            return BindingOutcome.MATCHED;
        }
        if (error == null && result.state() == FlowExecutionState.COMPLETED
                && result.steps().isEmpty()) {
            return BindingOutcome.MATCHED;
        }
        if (result.state() == FlowExecutionState.FAILED && error != null
                && (error.category() == FlowErrorCategory.VALIDATION
                || error.category() == FlowErrorCategory.POLICY)) {
            return BindingOutcome.REJECTED;
        }
        return BindingOutcome.CREATED;
    }

    private void confirmMembers(ExecutionState execution, BindingOutcome outcome) {
        for (ItemState member : execution.members) {
            try {
                stateStore.confirmBinding(member.item.getItemId(), outcome);
            } catch (RuntimeException confirmFailure) {
                log.warn("TxFlowStream[{}] binding confirmation ({}) failed for item '{}'",
                        streamId, outcome, member.item.getItemId(), confirmFailure);
            }
        }
    }

    private void failMembers(ExecutionState execution, String code, String message,
                             Throwable cause) {
        for (ItemState member : execution.members) {
            failItem(member, code, message, cause);
        }
    }

    /** Builds the engine request for an execution, including its bindings. */
    private FlowExecutionRequest buildRequest(ExecutionState execution) {
        FlowExecutionRequest.Builder builder = FlowExecutionRequest.builder(execution.definition)
                .executionId(execution.executionId)
                .idempotency(namespace, execution.claimKey)
                .spendingResource(execution.lane.canonicalSpendingIdentity());
        RequestBindings bindings = execution.requestBindings;
        FlowBindings flowBindings = bindings.toFlowBindings();
        if (!flowBindings.asMap().isEmpty()) {
            builder.bindings(flowBindings);
        }
        bindings.secureRefs.forEach(builder::secureBindingReference);
        return builder.build();
    }

    // ------------------------------------------------------------------
    // Durable planned-flow persistence (ADR 0004 Decision 5)
    // ------------------------------------------------------------------

    /**
     * Persists the planned request alongside the write-ahead binding in durable
     * mode, as part of the fail-closed bind phase before {@code start()}. The
     * no-secrets rule is enforced here: an execution carrying an inline
     * sensitive binding (a value not expressed as a secure reference) is not
     * persistable and fails the item typed {@code TXSTREAM_NON_PERSISTABLE_SECRET}.
     * The persisted record carries the portable-encoded flow, the non-sensitive
     * bindings, and the secure-binding references plus fingerprints — never a
     * secret value.
     */
    private void persistPlannedIfDurable(ExecutionState execution) {
        if (!stateStore.isDurable()) {
            return;
        }
        RequestBindings bindings = execution.requestBindings;
        if (bindings.hasSensitiveInline()) {
            throw new TxStreamException("TXSTREAM_NON_PERSISTABLE_SECRET",
                    "Execution '" + execution.executionId + "' carries inline sensitive binding(s) "
                            + bindings.sensitive.keySet() + " that a durable stream cannot persist"
                            + " without becoming a plaintext secret store; supply them as secure"
                            + " binding references instead");
        }
        String portableFlow;
        try {
            portableFlow = codec.write(execution.definition,
                    FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
        } catch (RuntimeException encodeFailure) {
            throw new TxStreamException("TXSTREAM_PLANNED_ENCODE_FAILED",
                    "Planned flow for execution '" + execution.executionId
                            + "' has no portable encoding", encodeFailure);
        }
        Map<String, String> fingerprints = new LinkedHashMap<>();
        bindings.secureRefs.forEach((name, reference) ->
                fingerprints.put(name, StreamIdentities.secureRefFingerprint(reference)));
        List<TxStreamPlannedRecord.Member> members = new ArrayList<>(execution.members.size());
        for (ItemState member : execution.members) {
            members.add(new TxStreamPlannedRecord.Member(member.item.getItemId(),
                    memberClaimKey(member), member.stepId, member.fingerprint));
        }
        // DEV-T1: persist the template definition's fingerprint alongside the
        // ref so re-attach re-dispatch can fail fast if a DIFFERENT definition
        // is re-registered under the same id (config drift), mirroring the
        // fan-out bootstrap drift guard. The template is registered on the
        // planning instance, so its fingerprint is always available here.
        String templateFingerprint = null;
        if (execution.templateId != null) {
            RegisteredTemplate template = templates.get(execution.templateId);
            if (template != null) {
                templateFingerprint = template.fingerprint();
            }
        }
        TxStreamPlannedRecord record = new TxStreamPlannedRecord(streamId, execution.executionId,
                execution.claimKey, execution.lane.laneName(),
                execution.lane.canonicalSpendingIdentity(), portableFlow, bindings.nonSensitive,
                bindings.secureRefs, fingerprints, members, execution.templateId,
                templateFingerprint);
        try {
            stateStore.persistPlanned(record);
        } catch (RuntimeException writeFailure) {
            throw new TxStreamException("TXSTREAM_PLANNED_WRITE_FAILED",
                    "Planned-flow persistence failed for execution '"
                            + execution.executionId + "'", writeFailure);
        }
    }

    /**
     * The member's own per-item claim key — the key the idempotency-key-reuse
     * guard tracks, distinct from the flow-level {@code execution.claimKey} under
     * a window planner. Freshly-planned members carry it directly; re-dispatched
     * members carry it from the persisted per-member record.
     */
    private static String memberClaimKey(ItemState member) {
        if (member.claimKey != null) {
            return member.claimKey;
        }
        return member.item.getIdempotencyKey() != null
                ? member.item.getIdempotencyKey() : member.item.getItemId();
    }

    // ------------------------------------------------------------------
    // Restart re-attach (ADR 0004 Decision 5)
    // ------------------------------------------------------------------

    /**
     * Resolves every persisted non-terminal binding against engine truth,
     * re-projecting present executions and re-dispatching absent ones, all
     * through the same projection and dispatch paths as live work.
     */
    private ReattachReport runReattach() {
        Set<String> nonTerminal = new HashSet<>(stateStore.listNonTerminalItemIds(streamId));
        if (nonTerminal.isEmpty()) {
            return ReattachReport.empty();
        }
        int reattached = 0;
        int redispatched = 0;
        int recoveryRequired = 0;
        List<String> reattachedItemIds = new ArrayList<>();
        // Every item id that has a persisted planned record, so the ghost reaper
        // below never touches an item the record loop is (or will be) resolving.
        Set<String> plannedItemIds = new HashSet<>();
        // BUG-2: rebuild the idempotency-key-reuse guard from the persisted
        // members BEFORE resolving them, so a key still owned by a re-attached
        // item cannot be reused by fresh work accepted after re-attach opens.
        for (TxStreamPlannedRecord record : stateStore.listPlanned(streamId)) {
            for (TxStreamPlannedRecord.Member member : record.members()) {
                plannedItemIds.add(member.itemId());
                if (nonTerminal.contains(member.itemId())) {
                    itemIdByClaimKey.putIfAbsent(member.idempotencyKey(), member.itemId());
                }
            }
        }
        for (TxStreamPlannedRecord record : stateStore.listPlanned(streamId)) {
            List<TxStreamPlannedRecord.Member> liveMembers = new ArrayList<>();
            for (TxStreamPlannedRecord.Member member : record.members()) {
                if (nonTerminal.contains(member.itemId()) && !items.containsKey(member.itemId())) {
                    liveMembers.add(member);
                }
            }
            if (liveMembers.isEmpty()) {
                continue;
            }
            try {
                Optional<FlowExecutionSnapshot> snapshot =
                        gateway.executionSnapshot(record.executionId());
                if (snapshot.isPresent()) {
                    for (TxStreamPlannedRecord.Member member : liveMembers) {
                        TxStreamItemStatus outcome = reattachPresentMember(record, member,
                                liveMembers.size() > 1, snapshot.get());
                        if (outcome == null) {
                            continue;
                        }
                        reattached++;
                        reattachedItemIds.add(member.itemId());
                        if (outcome == TxStreamItemStatus.RECOVERY_REQUIRED) {
                            recoveryRequired++;
                        }
                    }
                } else {
                    redispatched += redispatchAbsent(record, liveMembers);
                }
            } catch (RuntimeException recordFailure) {
                log.error("TxFlowStream[{}] re-attach of execution '{}' failed",
                        streamId, record.executionId(), recordFailure);
            }
        }
        reapAbandonedGhosts(nonTerminal, plannedItemIds);
        ReattachReport report = new ReattachReport(reattached, redispatched, recoveryRequired,
                reattachedItemIds);
        log.info("TxFlowStream[{}] re-attach recovered {} item(s): {} re-attached, {} re-dispatched,"
                + " {} recovery-required", streamId, reattached + redispatched, reattached,
                redispatched, recoveryRequired);
        return report;
    }

    /**
     * Terminally settles the non-terminal store rows that no planned record and
     * no resolvable engine execution can ever resolve — an item registered and
     * projected {@code ACCEPTED} before the crash but never bound (so it has no
     * planned record to re-dispatch and never reached the engine). Left
     * untouched they would be returned by {@link TxStreamStateStore#listNonTerminalItemIds}
     * on every restart, growing the re-attach scan without bound (BUG-4). Each is
     * settled {@code CANCELLED} typed {@code TXSTREAM_ABANDONED} in the store —
     * a documented bounded loss; the answer for such work is idempotent source
     * redelivery. Rows that DO have a planned record (handled by the record loop,
     * including BUG-1's terminal repair) or a present engine snapshot (still
     * resolvable) are never touched.
     */
    private void reapAbandonedGhosts(Set<String> nonTerminal, Set<String> plannedItemIds) {
        for (String itemId : nonTerminal) {
            if (plannedItemIds.contains(itemId) || items.containsKey(itemId)) {
                continue;
            }
            Optional<TxStreamItemResult> stored = stateStore.getItem(streamId, itemId);
            if (stored.isEmpty() || ItemProjection.isFinal(stored.get().getStatus())) {
                continue;
            }
            TxStreamItemResult prior = stored.get();
            // A stored execution id whose snapshot is present is still resolvable
            // — leave it for an operator reconcile rather than abandoning it.
            String executionId = prior.getExecutionId();
            if (executionId != null) {
                try {
                    if (gateway.executionSnapshot(executionId).isPresent()) {
                        continue;
                    }
                } catch (RuntimeException snapshotFailure) {
                    log.warn("TxFlowStream[{}] abandoned-ghost snapshot probe failed for '{}'",
                            streamId, itemId, snapshotFailure);
                    continue; // uncertain: do not abandon
                }
            }
            long sequence = stateStore.lastProjectionSequence(streamId, itemId)
                    .orElse(REATTACH_SEQUENCE_FLOOR);
            TxStreamException abandoned = new TxStreamException("TXSTREAM_ABANDONED",
                    "Item '" + itemId + "' was accepted before restart but never bound to an"
                            + " execution and has no persisted plan to re-dispatch; it is"
                            + " abandoned — redeliver it idempotently to run it");
            TxStreamItemResult cancelled = prior.toBuilder()
                    .status(TxStreamItemStatus.CANCELLED)
                    .error(abandoned)
                    .updatedAt(clock.instant())
                    .build();
            try {
                stateStore.projectItem(cancelled, sequence + 1);
            } catch (RuntimeException reapFailure) {
                log.warn("TxFlowStream[{}] abandoned-ghost reap failed for '{}'",
                        streamId, itemId, reapFailure);
            }
        }
    }

    /**
     * Re-attaches one member of a present (already-started) execution by
     * re-projecting from the authoritative engine snapshot through the normal
     * projection path — an authoritative fast-forward: a snapshot-terminal
     * member settles to its final status directly, while a still-running one is
     * surfaced {@code RECOVERY_REQUIRED} (this process refreshes it by
     * read-through, not live watching). Returns the resulting status, or
     * {@code null} when the member was skipped.
     */
    private TxStreamItemStatus reattachPresentMember(TxStreamPlannedRecord record,
                                                     TxStreamPlannedRecord.Member member,
                                                     boolean shared,
                                                     FlowExecutionSnapshot snapshot) {
        ItemState state = reconstructItemState(record, member, shared);
        if (items.putIfAbsent(member.itemId(), state) != null) {
            return null;
        }
        registerReattachedClaim(state);
        // The binding was written DISPATCHING before the crash; the present
        // snapshot proves the start happened, so confirm it (best-effort).
        try {
            stateStore.confirmBinding(member.itemId(), BindingOutcome.MATCHED);
        } catch (RuntimeException confirmFailure) {
            log.warn("TxFlowStream[{}] re-attach binding confirmation failed for item '{}'",
                    streamId, member.itemId(), confirmFailure);
        }
        // BUG-T1: a whole-flow template item's status derives from the flow's
        // OVERALL state (templateFlowStatus), symmetric with the live terminal
        // pass (projectTemplateTerminal) — never snapshotStatus, which maps a
        // PARTIALLY_COMPLETED flow to FAILED and would permanently misreport a
        // multi-step template whose remaining transactions may still confirm.
        TxStreamItemStatus target = state.wholeFlow
                ? templateFlowStatus(snapshot.state())
                : shared
                        ? memberSnapshotStatus(snapshot, state)
                        : snapshotStatus(snapshot.state());
        String hash = hashFromSnapshotData(snapshot.data(), shared ? state.stepId : null);
        // A RECOVERY_REQUIRED target (a whole-flow PARTIALLY_COMPLETED /
        // still-running template) is a settling-but-repairable status, not a
        // terminal fast-forward — route it through the honest "still uncertain"
        // surface below, the same as a per-member null. The non-whole-flow
        // paths never yield RECOVERY_REQUIRED here (snapshotStatus /
        // memberSnapshotStatus return null while non-terminal), so their
        // behaviour is unchanged.
        if (target != null && target != TxStreamItemStatus.RECOVERY_REQUIRED) {
            TxStreamItemStatus finalTarget = target;
            String finalHash = hash;
            project(state, finalTarget,
                    builder -> builder.transactionHash(finalHash)
                            .error(finalTarget == TxStreamItemStatus.CONFIRMED ? null
                                    : reattachedTerminalError(finalTarget, record.executionId())),
                    true);
        } else {
            // Running (or a partially-completed template), but this process
            // cannot watch a foreign-process execution push-based (iteration 3).
            // Surface RECOVERY_REQUIRED — honest, settles the promise, and
            // read-through repairs it once the engine reports a terminal outcome.
            String finalHash = hash;
            TxStreamException uncertain = new TxStreamException("TXSTREAM_REATTACH_UNCONFIRMED",
                    "Execution '" + record.executionId() + "' is still running after restart;"
                            + " this process re-attaches by read-through, not live watching —"
                            + " reconcile once the engine reports its outcome");
            project(state, TxStreamItemStatus.RECOVERY_REQUIRED,
                    builder -> builder.transactionHash(finalHash).error(uncertain), true);
        }
        // A RECOVERY_REQUIRED seed whose snapshot is still non-terminal produces
        // no transition; settle its promise explicitly so drain() cannot hang.
        if (ItemProjection.settles(state.projection.current().getStatus())
                && !state.projection.isSettled()) {
            state.projection.completePromise(state.projection.current());
        }
        return state.projection.current().getStatus();
    }

    private TxStreamException reattachedTerminalError(TxStreamItemStatus status,
                                                      String executionId) {
        if (status == TxStreamItemStatus.CANCELLED) {
            return new TxStreamException("TXSTREAM_REATTACH_CANCELLED",
                    "Execution '" + executionId + "' was cancelled before restart");
        }
        return new TxStreamException("TXSTREAM_REATTACH_FAILED",
                "Execution '" + executionId + "' failed before restart");
    }

    /**
     * Re-dispatches an absent (never-started) execution from its persisted plan
     * through the normal dispatch path. The deterministic execution id makes
     * this idempotent — the engine creates the execution at most once. Returns
     * the number of members re-dispatched.
     */
    private int redispatchAbsent(TxStreamPlannedRecord record,
                                 List<TxStreamPlannedRecord.Member> liveMembers) {
        String templateId = record.templateId();
        TxFlow definition;
        if (templateId != null) {
            // A template execution re-dispatches from the RE-REGISTERED template
            // (ADR 0004, iteration 3), never from the stored flow — the template
            // is load-bearing config the durable stream must re-register. A
            // template that is not re-registered on this instance surfaces its
            // items typed rather than silently losing them.
            RegisteredTemplate template = templates.get(templateId);
            if (template == null) {
                surfaceUnknownTemplate(record, liveMembers, templateId);
                return 0;
            }
            // DEV-T1: fail fast if a DIFFERENT definition was re-registered under
            // the same template id since this execution was planned. Running the
            // re-registered flow under the original claim would silently execute
            // a different flow (config drift) — the direct analogue of the
            // fan-out bootstrap config-drift guard. A null persisted fingerprint
            // (a record written before this field existed) skips the check.
            String persistedFingerprint = record.templateFingerprint();
            if (persistedFingerprint != null
                    && !persistedFingerprint.equals(template.fingerprint())) {
                surfaceTemplateDrift(record, liveMembers, templateId);
                return 0;
            }
            definition = template.definition();
        } else {
            try {
                definition = codec.parse(record.portableFlow(), FlowParseOptions.serverDefaults())
                        .requireFlow();
            } catch (RuntimeException parseFailure) {
                log.error("TxFlowStream[{}] re-attach could not decode planned flow for execution"
                        + " '{}'; the item cannot be re-dispatched", streamId,
                        record.executionId(), parseFailure);
                return 0;
            }
        }
        ResolvedLane lane = reattachLane(record);
        boolean shared = liveMembers.size() > 1;
        // Reconstruct the member states WITHOUT publishing them into `items`
        // yet: the executionsById guard runs first (BUG-3), so a collision (a
        // prior re-dispatch of the same execution id already live) strands no
        // open member promise for drain() to wait on forever.
        List<ItemState> members = new ArrayList<>(liveMembers.size());
        for (TxStreamPlannedRecord.Member member : liveMembers) {
            members.add(reconstructItemState(record, member, shared));
        }
        ExecutionState execution = new ExecutionState(record.executionId(), record.idempotencyKey(),
                definition, lane, members, null, RequestBindings.ofRecord(record), templateId);
        if (executionsById.putIfAbsent(record.executionId(), execution) != null) {
            return 0; // already live (e.g. a prior re-dispatch); nothing published
        }
        for (ItemState member : members) {
            member.executionId = record.executionId();
            member.flowId = definition.getId();
            member.sharedExecution = shared;
            member.execution = execution;
            items.putIfAbsent(member.item.getItemId(), member);
            registerReattachedClaim(member);
        }
        synchronized (stateLock) {
            LaneQueue laneQueue = laneQueues.computeIfAbsent(
                    lane.canonicalSpendingIdentity(), LaneQueue::new);
            laneQueue.queue.add(execution);
            makeReady(laneQueue);
        }
        schedulePump();
        return members.size();
    }

    /**
     * Surfaces the items of a re-attached template execution whose template was
     * NOT re-registered on this stream instance (ADR 0004, iteration 3): each
     * member is reconstructed, published so {@link #getItemStatus(String)} finds
     * it, and settled typed {@code TXSTREAM_TEMPLATE_UNKNOWN} — a re-registration
     * omission is a loud, recoverable failure (re-register the template and
     * restart), never a silent loss.
     */
    private void surfaceUnknownTemplate(TxStreamPlannedRecord record,
                                        List<TxStreamPlannedRecord.Member> liveMembers,
                                        String templateId) {
        boolean shared = liveMembers.size() > 1;
        TxStreamException unknown = new TxStreamException("TXSTREAM_TEMPLATE_UNKNOWN",
                "Re-attach of execution '" + record.executionId() + "' references template '"
                        + templateId + "', which is not registered on this stream instance;"
                        + " re-register the template under the same id and restart to recover"
                        + " its items");
        for (TxStreamPlannedRecord.Member member : liveMembers) {
            ItemState state = reconstructItemState(record, member, shared);
            if (items.putIfAbsent(state.item.getItemId(), state) != null) {
                continue;
            }
            registerReattachedClaim(state);
            failItem(state, "TXSTREAM_TEMPLATE_UNKNOWN", unknown.getMessage(), unknown);
        }
    }

    /**
     * Surfaces the items of a re-attached template execution whose re-registered
     * template definition DIFFERS from the one persisted when the execution was
     * planned (DEV-T1 — template-definition config drift). Rather than silently
     * running a different flow under the original claim, each member is
     * reconstructed, published so {@link #getItemStatus(String)} finds it, and
     * settled typed {@code TXSTREAM_TEMPLATE_DRIFT} — the operator must
     * re-register the identical definition to recover (fail-fast, the analogue
     * of the fan-out bootstrap config-drift guard).
     */
    private void surfaceTemplateDrift(TxStreamPlannedRecord record,
                                      List<TxStreamPlannedRecord.Member> liveMembers,
                                      String templateId) {
        boolean shared = liveMembers.size() > 1;
        TxStreamException drift = new TxStreamException("TXSTREAM_TEMPLATE_DRIFT",
                "Re-attach of execution '" + record.executionId() + "' references template '"
                        + templateId + "', but the definition registered under this templateId on"
                        + " this stream instance differs from the one persisted when the execution"
                        + " was planned; re-register the identical definition to recover its items"
                        + " (the drifted flow was NOT run)");
        for (TxStreamPlannedRecord.Member member : liveMembers) {
            ItemState state = reconstructItemState(record, member, shared);
            if (items.putIfAbsent(state.item.getItemId(), state) != null) {
                continue;
            }
            registerReattachedClaim(state);
            failItem(state, "TXSTREAM_TEMPLATE_DRIFT", drift.getMessage(), drift);
        }
    }

    /**
     * Re-registers a re-attached item's per-item claim key in the
     * idempotency-key-reuse guard so a key still owned by a recovered item
     * cannot be reused by fresh work accepted after re-attach opens (BUG-2).
     * {@code putIfAbsent} preserves whatever the rebuild pass already recorded.
     */
    private void registerReattachedClaim(ItemState state) {
        if (state.claimKey != null) {
            itemIdByClaimKey.putIfAbsent(state.claimKey, state.item.getItemId());
        }
        accountSeededRecoveryRequired(state);
    }

    /**
     * F1: keeps the {@code recoveryRequiredItemCount} gauge symmetric across a
     * seeded item and its later repair, at the single post-install hook every
     * re-attach / observer seed path funnels through
     * ({@link #registerReattachedClaim}).
     *
     * <p>A re-attached or observer-reconstructed item whose stored projection is
     * ALREADY {@code RECOVERY_REQUIRED} enters the live map at that status
     * WITHOUT passing through {@link #project}/{@link #recordTransition} — its
     * promise was seeded already-settled by
     * {@link ItemProjection#reattaching(TxStreamItemResult, long)} — so the gauge
     * never saw the increment. Yet the eventual terminal repair DOES run through
     * {@code recordTransition}, which decrements on
     * {@code previous == RECOVERY_REQUIRED && isFinal(target)} and would drive the
     * gauge to -1. Incrementing here at the seed site (covering BOTH the observer
     * durable-absent seed and the re-attach present/absent seeds) balances that
     * decrement, so the gauge is provably non-negative and equals the live
     * RECOVERY_REQUIRED residency. Gated on {@code !suppressCounters}, exactly
     * like the matching decrement in {@code recordTransition}.</p>
     */
    private void accountSeededRecoveryRequired(ItemState state) {
        if (!state.suppressCounters
                && state.projection.current().getStatus()
                        == TxStreamItemStatus.RECOVERY_REQUIRED) {
            recoveryRequiredCount.incrementAndGet();
        }
    }

    /**
     * Reconstructs an {@link ItemState} for a re-attached member, seeded from
     * its stored projection (or a synthesized {@code PLANNED} snapshot) so
     * re-attach flows through the same projection cell as live work. A
     * settling seed ({@code RECOVERY_REQUIRED}) starts already-settled but
     * still repairable; a non-settling seed keeps an open promise.
     */
    private ItemState reconstructItemState(TxStreamPlannedRecord record,
                                           TxStreamPlannedRecord.Member member, boolean shared) {
        TxWorkItem item = reconstructWorkItem(record, member);
        TxStreamItemResult seed = stateStore.getItem(streamId, member.itemId())
                .orElseGet(() -> TxStreamItemResult
                        .builder(streamId, member.itemId(), TxStreamItemStatus.PLANNED)
                        .executionId(record.executionId())
                        .stepId(member.stepId())
                        .laneName(record.laneName())
                        .updatedAt(clock.instant())
                        .build());
        // BUG-1: seed the live projection at the durable projection's last
        // sequence so the first authoritative advance writes at storedSeq+1 and
        // wins the store CAS; without this the terminal repair is dropped as
        // stale and the item is re-attached on every restart forever.
        long storedSequence = stateStore.lastProjectionSequence(streamId, member.itemId())
                .orElse(REATTACH_SEQUENCE_FLOOR);
        ItemState state = ItemState.reattached(this, item, member.idempotencyKey(),
                member.fingerprint(), reattachLane(record), seed, storedSequence);
        state.executionId = record.executionId();
        state.flowId = null;
        state.stepId = member.stepId();
        state.sharedExecution = shared;
        // A template execution is a single-member, whole-flow execution: its
        // status derives from the flow's overall state, not a single step.
        state.wholeFlow = record.templateId() != null;
        return state;
    }

    /**
     * Reconstructs a minimal work item for a re-attached member from the
     * persisted portable flow. Its portable step is only needed so re-dispatch
     * (which rebuilds the request from the persisted plan, not from the item)
     * has a valid payload and identity to carry.
     */
    private TxWorkItem reconstructWorkItem(TxStreamPlannedRecord record,
                                           TxStreamPlannedRecord.Member member) {
        TxFlow flow = codec.parse(record.portableFlow(), FlowParseOptions.serverDefaults())
                .requireFlow();
        FlowStep step = flow.getStep(member.stepId())
                .orElseThrow(() -> new TxStreamException("TXSTREAM_REATTACH_STEP_MISSING",
                        "Persisted plan for execution '" + record.executionId()
                                + "' has no step '" + member.stepId() + "' for item '"
                                + member.itemId() + "'"));
        return TxWorkItem.fromFlowStep(member.itemId(), step);
    }

    /**
     * Reconstructs the lane for a re-attached execution. The canonical spending
     * identity is preserved exactly (it keys the lane FIFO); the funding scope
     * is derived from it for labelling only — re-attach never re-enforces the
     * scope, since the persisted flow is already materialized.
     */
    private ResolvedLane reattachLane(TxStreamPlannedRecord record) {
        String identity = record.canonicalSpendingIdentity();
        LaneFundingScope scope;
        if (identity.startsWith("addr:")) {
            scope = LaneFundingScope.address(identity.substring("addr:".length()));
        } else if (identity.startsWith("ref:")) {
            scope = LaneFundingScope.fundingRef(identity.substring("ref:".length()));
        } else {
            scope = LaneFundingScope.fundingRef(identity);
        }
        return new ResolvedLane(record.laneName(), identity, scope);
    }

    // ------------------------------------------------------------------
    // Projection
    // ------------------------------------------------------------------

    /**
     * Maps a terminal engine result onto one member item per the
     * terminal-precedence rule: the item always reaches a settled status in
     * this single pass, its status derives from ITS planned step's result
     * (a flow-level failure before the step ran fails the item without a
     * hash), a submitted-but-unconfirmed step becomes
     * {@code RECOVERY_REQUIRED} with its hash retained, and a known hash is
     * never dropped.
     */
    private void projectTerminal(ItemState state, ExecutionState execution,
                                 FlowExecutionResult result, SnapshotLookup snapshots) {
        if (state.wholeFlow) {
            projectTemplateTerminal(state, result, snapshots);
            return;
        }
        advanceCursorToHandleTail(state);
        boolean shared = state.sharedExecution;
        FlowStepResult stepResult = result.steps().stream()
                .filter(step -> state.stepId.equals(step.getStepId()))
                .findFirst()
                // Single-member fallback preserved from the single-item core:
                // a lone step result is the item's regardless of its id. A
                // shared flow never borrows a sibling's step result.
                .orElse(!shared && !result.steps().isEmpty() ? result.steps().get(0) : null);
        String hash = stepResult != null ? stepResult.getTransactionHash() : null;
        if (hash == null) {
            hash = hashFromEvents(state, shared);
        }
        if (hash == null) {
            hash = hashFromAttempts(result.attempts(), shared ? state.stepId : null);
        }
        if (hash == null && stepResult == null) {
            // MATCHED re-submit of a stored execution: recover the transaction
            // identity through the engine's projection reads (P2), scoped to
            // the member's step for shared flows.
            hash = snapshots.get()
                    .map(snapshot -> hashFromSnapshotData(snapshot.data(),
                            shared ? state.stepId : null))
                    .orElse(null);
        }
        if (hash != null) {
            String submittedHash = hash;
            project(state, TxStreamItemStatus.SUBMITTED,
                    builder -> builder.transactionHash(submittedHash), false);
        }
        TxStreamItemStatus target;
        if (shared && stepResult == null
                && result.state() == FlowExecutionState.PARTIALLY_COMPLETED) {
            // BUG-1C-R2: a PARTIALLY_COMPLETED flow state says nothing about
            // WHICH members confirmed, and a MATCHED stored terminal may
            // carry no step results at all — mapping such a member to FAILED
            // would be a guess that could contradict its own confirmed
            // transaction. The member's own attempt evidence from the durable
            // snapshot decides instead; without evidence the member settles
            // RECOVERY_REQUIRED, which the read-through reconcile repairs
            // once evidence exists. A FAILED flow state still maps to FAILED
            // through memberTerminalStatus: engine semantics guarantee no
            // step of a FAILED flow confirmed.
            target = snapshots.get()
                    .map(snapshot -> memberSnapshotStatus(snapshot, state))
                    .orElse(null);
            if (target == null) {
                target = TxStreamItemStatus.RECOVERY_REQUIRED;
            }
        } else {
            target = memberTerminalStatus(stepResult, result.state());
        }
        Throwable error = target == TxStreamItemStatus.CONFIRMED
                ? null : terminalError(stepResult, result);
        String finalHash = hash;
        // Terminal projection of an engine result is authoritative: engine
        // truth settles every item of a completed flow in this pass, and
        // final statuses remain immutable regardless.
        project(state, target, builder -> builder.error(error).transactionHash(finalHash), true);
    }

    /**
     * Terminal projection of a template item (ADR 0004, iteration 3): a
     * single-member, whole-flow execution whose status IS the flow's overall
     * state and whose transaction hash is the latest submitted attempt across
     * the whole flow. Honest per the terminal-precedence rule: a
     * PARTIALLY_COMPLETED flow (some steps submitted, others unconfirmed) is
     * {@code RECOVERY_REQUIRED} with the hash retained, never a false
     * {@code FAILED}.
     */
    private void projectTemplateTerminal(ItemState state, FlowExecutionResult result,
                                         SnapshotLookup snapshots) {
        advanceCursorToHandleTail(state);
        String hash = hashFromAnyEvent(state);
        if (hash == null) {
            hash = hashFromAttempts(result.attempts(), null);
        }
        if (hash == null) {
            hash = snapshots.get()
                    .map(snapshot -> hashFromSnapshotData(snapshot.data(), null))
                    .orElse(null);
        }
        if (hash != null) {
            String submittedHash = hash;
            project(state, TxStreamItemStatus.SUBMITTED,
                    builder -> builder.transactionHash(submittedHash), false);
        }
        TxStreamItemStatus target = templateFlowStatus(result.state());
        Throwable error = target == TxStreamItemStatus.CONFIRMED ? null
                : terminalError(null, result);
        String finalHash = hash;
        project(state, target, builder -> builder.error(error).transactionHash(finalHash), true);
    }

    /**
     * Maps a whole-flow terminal engine state onto a template item's status.
     * PARTIALLY_COMPLETED and RECOVERY_REQUIRED both become
     * {@code RECOVERY_REQUIRED} (some transactions may still confirm — the
     * honest answer); COMPLETED is {@code CONFIRMED}; FAILED/ROLLED_BACK is
     * {@code FAILED}; CANCELLED is {@code CANCELLED}.
     */
    private TxStreamItemStatus templateFlowStatus(FlowExecutionState flowState) {
        switch (flowState) {
            case COMPLETED:
                return TxStreamItemStatus.CONFIRMED;
            case FAILED:
            case ROLLED_BACK:
                return TxStreamItemStatus.FAILED;
            case CANCELLED:
                return TxStreamItemStatus.CANCELLED;
            case PARTIALLY_COMPLETED:
            case RECOVERY_REQUIRED:
            default:
                return TxStreamItemStatus.RECOVERY_REQUIRED;
        }
    }

    /**
     * Latest submitted transaction hash across the whole flow (any step) — the
     * template item's hash source, since a template invocation owns the whole
     * flow rather than one step.
     */
    private String hashFromAnyEvent(ItemState state) {
        EngineGateway.ExecutionHandle handle = state.handle;
        if (handle == null) return null;
        return handle.events().stream()
                .filter(event -> event.transactionHash() != null)
                .filter(event -> event.type() == FlowEventType.TRANSACTION_SUBMITTED)
                .max(Comparator.comparingLong(FlowEvent::sequence))
                .map(FlowEvent::transactionHash)
                .orElse(null);
    }

    /**
     * Per-member terminal mapping: the member's own step result decides where
     * one exists (terminal precedence: {@code IN_PROGRESS} inside a terminal
     * flow is {@code RECOVERY_REQUIRED}; a rolled-back flow fails its
     * members); without a step result the flow state decides — a flow-level
     * failure before the step ran is {@code FAILED}, and a MATCHED stored
     * COMPLETED flow with compacted steps is {@code CONFIRMED}.
     */
    private TxStreamItemStatus memberTerminalStatus(FlowStepResult stepResult,
                                                    FlowExecutionState flowState) {
        if (stepResult != null && stepResult.getStatus() == FlowStatus.IN_PROGRESS) {
            return TxStreamItemStatus.RECOVERY_REQUIRED;
        }
        if (flowState == FlowExecutionState.ROLLED_BACK) {
            return TxStreamItemStatus.FAILED;
        }
        if (stepResult != null) {
            switch (stepResult.getStatus()) {
                case COMPLETED:
                    return TxStreamItemStatus.CONFIRMED;
                case FAILED:
                    return TxStreamItemStatus.FAILED;
                case CANCELLED:
                    return TxStreamItemStatus.CANCELLED;
                case PENDING:
                default:
                    break; // never ran: fall through to the flow state
            }
        }
        switch (flowState) {
            case COMPLETED:
                return TxStreamItemStatus.CONFIRMED;
            case CANCELLED:
                return TxStreamItemStatus.CANCELLED;
            case FAILED:
            case PARTIALLY_COMPLETED:
                // FAILED is safe for members without a step result: engine
                // semantics guarantee no step of a FAILED flow confirmed.
                // The shared-member PARTIALLY_COMPLETED case never reaches
                // this fallback — projectTerminal decides it from the
                // member's own snapshot attempt evidence (BUG-1C-R2); here it
                // only maps single-member flows, where the flow state IS the
                // member's state.
                return TxStreamItemStatus.FAILED;
            case RECOVERY_REQUIRED:
            default:
                return TxStreamItemStatus.RECOVERY_REQUIRED;
        }
    }

    private Throwable terminalError(FlowStepResult stepResult, FlowExecutionResult result) {
        if (stepResult != null && stepResult.getError() != null) {
            return stepResult.getError();
        }
        FlowError error = result.error();
        if (error != null) {
            return new TxStreamException(error.code(), error.message());
        }
        return null;
    }

    /** Advances the item's event cursor over everything the handle has emitted. */
    private void advanceCursorToHandleTail(ItemState state) {
        EngineGateway.ExecutionHandle handle = state.handle;
        if (handle == null) return;
        try {
            long tail = 0;
            for (FlowEvent event : handle.events()) {
                tail = Math.max(tail, event.sequence());
            }
            state.eventCursor.accumulateAndGet(tail, Math::max);
        } catch (RuntimeException readFailure) {
            log.warn("TxFlowStream[{}] event cursor read failed for item '{}'",
                    streamId, state.item.getItemId(), readFailure);
        }
    }

    private String hashFromEvents(ItemState state, boolean shared) {
        EngineGateway.ExecutionHandle handle = state.handle;
        if (handle == null) return null;
        // Hash selection is deliberately consistent across sources: like
        // hashFromAttempts, pick the LATEST submitted attempt's hash — the
        // last TRANSACTION_SUBMITTED event in sequence order. Members of a
        // shared flow only ever match events carrying THEIR step id; the
        // single-member core keeps its null-step tolerance.
        return handle.events().stream()
                .filter(event -> event.transactionHash() != null)
                .filter(event -> event.type() == FlowEventType.TRANSACTION_SUBMITTED)
                .filter(event -> shared
                        ? state.stepId.equals(event.stepId())
                        : event.stepId() == null || event.stepId().equals(state.stepId))
                .max(Comparator.comparingLong(FlowEvent::sequence))
                .map(FlowEvent::transactionHash)
                .orElse(null);
    }

    private String hashFromAttempts(List<FlowAttemptSnapshot> attempts, String stepIdFilter) {
        // Consistent with hashFromEvents: the LATEST submitted attempt wins.
        return attempts.stream()
                .filter(attempt -> attempt.signedPayload() != null)
                .filter(attempt -> stepIdFilter == null || stepIdFilter.equals(attempt.stepId()))
                .max(Comparator.comparingInt(FlowAttemptSnapshot::attemptNumber))
                .map(attempt -> attempt.signedPayload().transactionHash())
                .orElse(null);
    }

    /**
     * Memoizes one engine snapshot read (P2) for a whole terminal projection
     * pass, so an N-member MATCHED flow costs one store read instead of one
     * per member. A failed read memoizes as empty for the pass. Confined to
     * the completion callback thread — no synchronization.
     */
    private final class SnapshotLookup {
        private final String executionId;
        private Optional<FlowExecutionSnapshot> snapshot;

        SnapshotLookup(String executionId) {
            this.executionId = executionId;
        }

        Optional<FlowExecutionSnapshot> get() {
            if (snapshot == null) {
                try {
                    snapshot = gateway.executionSnapshot(executionId);
                } catch (RuntimeException readFailure) {
                    log.warn("TxFlowStream[{}] snapshot read failed for execution '{}'",
                            streamId, executionId, readFailure);
                    snapshot = Optional.empty();
                }
            }
            return snapshot;
        }
    }

    /**
     * Recovers the latest submitted attempt's hash from durable snapshot data.
     *
     * <p>Coupling note: durable attempt history is persisted by the engine's
     * {@code DurableExecutionPersistence} under its package-private
     * {@code ATTEMPTS_KEY} ({@code "attempts"}) snapshot-data entry as a map
     * of {@code FlowAttemptSnapshot}s. That constant is not visible from this
     * package, so this method scans the snapshot data for attempt-snapshot
     * maps rather than importing it; if the engine's key or value shape
     * changes, this projection must change with it.</p>
     */
    private String hashFromSnapshotData(Map<String, Object> data, String stepIdFilter) {
        String best = null;
        int bestAttempt = Integer.MIN_VALUE;
        for (Object value : data.values()) {
            if (!(value instanceof Map)) continue;
            for (Object candidate : ((Map<?, ?>) value).values()) {
                if (!(candidate instanceof FlowAttemptSnapshot)) continue;
                FlowAttemptSnapshot attempt = (FlowAttemptSnapshot) candidate;
                if (stepIdFilter != null && !stepIdFilter.equals(attempt.stepId())) continue;
                if (attempt.signedPayload() != null && attempt.attemptNumber() > bestAttempt) {
                    bestAttempt = attempt.attemptNumber();
                    best = attempt.signedPayload().transactionHash();
                }
            }
        }
        return best;
    }

    private void failItem(ItemState state, String code, String message, Throwable cause) {
        TxStreamException error = cause instanceof TxStreamException
                && code.equals(((TxStreamException) cause).getCode())
                ? (TxStreamException) cause
                : new TxStreamException(code, message, cause);
        project(state, TxStreamItemStatus.FAILED, builder -> builder.error(error), false);
    }

    /**
     * Advances one item's projection through the single transition-table
     * enforcement point, mirrors the accepted snapshot to the state store and
     * listener, updates the cumulative counters, and settles the item promise
     * (plus retention and batch bookkeeping) when the status settles.
     */
    private TxStreamItemResult project(ItemState state, TxStreamItemStatus target,
                                       UnaryOperator<TxStreamItemResult.Builder> customize,
                                       boolean authoritative) {
        ItemProjection.Applied applied = state.projection.advance(
                target, customize, clock.instant(), authoritative);
        if (applied == null) return null;
        if (!state.suppressCounters) {
            recordTransition(applied.previous(), target);
        }
        if (!state.suppressStoreProjection) {
            safeStoreProject(applied);
        }
        // Complete the item promise BEFORE the (inline) listener callback so a
        // subscriber that violates Reactive-Streams §2.2 by blocking inside
        // onNext cannot wedge this item's receipt.completion()/drain()/
        // awaitPromises — only its own lane's dispatch stalls (delivery is
        // inline; the adapters own no threads). The durable store write above
        // stays first (durable truth precedes both).
        if (ItemProjection.settles(target)) {
            state.projection.completePromise(applied.result());
        }
        safeListener(() -> listener.onItemUpdated(applied.result()));
        if (ItemProjection.isFinal(target)) {
            noteSettledForRetention(state);
            maybeCompleteBatch(state);
        }
        return applied.result();
    }

    private void recordTransition(TxStreamItemStatus previous, TxStreamItemStatus target) {
        switch (target) {
            case PLANNED: plannedCount.incrementAndGet(); break;
            case SUBMITTED: submittedCount.incrementAndGet(); break;
            case CONFIRMED: confirmedCount.incrementAndGet(); break;
            case FAILED: failedCount.incrementAndGet(); break;
            case CANCELLED: cancelledCount.incrementAndGet(); break;
            case RECOVERY_REQUIRED: recoveryRequiredCount.incrementAndGet(); break;
            default: break;
        }
        if (previous == TxStreamItemStatus.RECOVERY_REQUIRED && ItemProjection.isFinal(target)) {
            // A repaired recovery-required item moves into its final bucket.
            recoveryRequiredCount.decrementAndGet();
        }
    }

    // ------------------------------------------------------------------
    // Batches
    // ------------------------------------------------------------------

    /**
     * Derives the batch's terminal status once every member reached a FINAL
     * status. A member settled {@code RECOVERY_REQUIRED} keeps the batch
     * RUNNING until reconciled — the honest answer while a member
     * transaction's disposition is uncertain; the eventual repair re-derives
     * the batch.
     */
    private void maybeCompleteBatch(ItemState state) {
        String batchId = state.batchId;
        if (batchId == null) return;
        BatchState batch = batches.get(batchId);
        if (batch == null) return;
        if (batch.tryComplete(streamId)) {
            publishBatch(batch);
            noteBatchSettledForRetention(batch);
        }
    }

    /** Best-effort batch projection write + listener callback (isolated). */
    private void publishBatch(BatchState batch) {
        TxStreamBatchResult result = batch.snapshot(streamId);
        try {
            stateStore.recordBatch(result);
        } catch (RuntimeException recordFailure) {
            log.warn("TxFlowStream[{}] batch projection write failed for '{}'",
                    streamId, batch.batchId, recordFailure);
        }
        safeListener(() -> listener.onBatchUpdated(result));
    }

    @Override
    public Optional<TxStreamBatchResult> getBatchStatus(String batchId) {
        BatchState batch = batches.get(batchId);
        if (batch != null) {
            return Optional.of(batch.snapshot(streamId));
        }
        return stateStore.getBatch(streamId, batchId);
    }

    // ------------------------------------------------------------------
    // Retention
    // ------------------------------------------------------------------

    /**
     * Records a final-settled item in the retention FIFO and evicts the
     * oldest-settled items beyond {@code maxRetainedSettledItems}. Only items
     * that reached a final status enter the FIFO, so unsettled items are never
     * evicted; counters are cumulative and unaffected.
     */
    private void noteSettledForRetention(ItemState state) {
        if (state.suppressStoreProjection) {
            // Rejected/unregistered states (idempotency-key reuse, failed
            // registration) are retained nowhere — they are removed from the
            // live map right after settling — so they must never enter the
            // retention FIFO: evicting such a stale state later would run
            // eviction side effects against an item id that may since have
            // been legitimately re-accepted by a live successor.
            return;
        }
        List<ItemState> evicted = null;
        synchronized (retentionLock) {
            settledFifo.add(state);
            while (settledFifo.size() > maxRetainedSettledItems) {
                if (evicted == null) evicted = new ArrayList<>();
                evicted.add(settledFifo.poll());
            }
        }
        if (evicted != null) {
            for (ItemState old : evicted) {
                evict(old);
            }
        }
    }

    /** Terminal batches share the settled-item retention cap, FIFO. */
    private void noteBatchSettledForRetention(BatchState batch) {
        List<BatchState> evicted = null;
        synchronized (retentionLock) {
            settledBatchFifo.add(batch);
            while (settledBatchFifo.size() > maxRetainedSettledItems) {
                if (evicted == null) evicted = new ArrayList<>();
                evicted.add(settledBatchFifo.poll());
            }
        }
        if (evicted != null) {
            for (BatchState old : evicted) {
                batches.remove(old.batchId, old);
                try {
                    stateStore.evictBatch(old.batchId);
                } catch (RuntimeException evictFailure) {
                    log.warn("TxFlowStream[{}] state-store batch eviction failed for '{}'",
                            streamId, old.batchId, evictFailure);
                }
            }
        }
    }

    private void evict(ItemState state) {
        String itemId = state.item.getItemId();
        if (!items.remove(itemId, state)) {
            // This exact state instance is not the live entry for its item id
            // (it was already released, or the id was re-accepted after an
            // earlier eviction). None of the side effects below may run for a
            // stale state: the claim-key mapping and the store record now
            // belong to the live successor and must survive.
            return;
        }
        if (state.claimKey != null) {
            itemIdByClaimKey.remove(state.claimKey, itemId);
        }
        try {
            stateStore.evictItem(itemId);
        } catch (RuntimeException evictFailure) {
            log.warn("TxFlowStream[{}] state-store eviction failed for item '{}'",
                    streamId, itemId, evictFailure);
        }
    }

    private void safeStoreProject(ItemProjection.Applied applied) {
        try {
            stateStore.projectItem(applied.result(), applied.sequence());
        } catch (RuntimeException projectionFailure) {
            log.warn("TxFlowStream[{}] projection write failed for item '{}'",
                    streamId, applied.result().getItemId(), projectionFailure);
        }
    }

    private void safeListener(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable listenerFailure) {
            log.warn("TxFlowStream[{}] event listener failed", streamId, listenerFailure);
        }
    }

    // ------------------------------------------------------------------
    // Queries, cancellation, reconciliation
    // ------------------------------------------------------------------

    @Override
    public CancelOutcome cancelItem(String itemId, String reason) {
        ItemState state = items.get(itemId);
        if (state == null || state.projection.isSettled()) {
            return CancelOutcome.unknownOrSettled();
        }
        boolean cancelledPrePlan = false;
        synchronized (stateLock) {
            if (windowBuffer.remove(state)) {
                cancelledPrePlan = true;
                noteWindowEmptiedLocked();
            } else if (state.execution == null && state.batchId != null) {
                BatchState batch = batches.get(state.batchId);
                if (batch != null && batch.inPlanningQueue) {
                    // The window closed but has not been snapshotted by the
                    // planning pump yet (the pump dequeues under this lock):
                    // flag the member so planning excludes it, then settle it.
                    state.prePlanCancelled = true;
                    cancelledPrePlan = true;
                }
            }
        }
        if (cancelledPrePlan) {
            releasePermit(state);
            TxStreamException cause = new TxStreamException("TXSTREAM_ITEM_CANCELLED",
                    reason != null ? reason : "Cancelled before dispatch");
            project(state, TxStreamItemStatus.CANCELLED, builder -> builder.error(cause), false);
            return CancelOutcome.cancelledBuffered();
        }
        ExecutionState execution = state.execution;
        if (execution == null) {
            // Mid-acceptance or mid-planning: not in a cancellable stage
            // right now; the caller may retry.
            return CancelOutcome.unknownOrSettled();
        }
        if (execution.members.size() > 1) {
            // Never silently widened: the caller must escalate explicitly.
            List<String> memberIds = new ArrayList<>(execution.members.size());
            for (ItemState member : execution.members) {
                memberIds.add(member.item.getItemId());
            }
            return CancelOutcome.rejectedShared(execution.executionId, memberIds);
        }
        if (removeQueuedExecution(execution)) {
            executionsById.remove(execution.executionId, execution);
            releasePermit(state);
            TxStreamException cause = new TxStreamException("TXSTREAM_ITEM_CANCELLED",
                    reason != null ? reason : "Cancelled before dispatch");
            project(state, TxStreamItemStatus.CANCELLED, builder -> builder.error(cause), false);
            return CancelOutcome.cancelledBuffered();
        }
        execution.pendingCancelReason = reason != null ? reason : "cancelled";
        EngineGateway.ExecutionHandle handle = execution.handle;
        if (handle != null) {
            handle.requestCancel(reason);
            return CancelOutcome.signalledSingle();
        }
        synchronized (stateLock) {
            // Between claim and start the dispatch task observes the pending
            // reason and forwards it as soon as the handle exists.
            LaneQueue lane = laneQueues.get(execution.lane.canonicalSpendingIdentity());
            return lane != null && lane.inFlight == execution
                    ? CancelOutcome.signalledSingle()
                    : CancelOutcome.unknownOrSettled();
        }
    }

    @Override
    public boolean cancelExecution(String executionId, String reason) {
        ExecutionState execution = executionsById.get(executionId);
        if (execution == null) {
            return false;
        }
        if (removeQueuedExecution(execution)) {
            // Queued whole: no transaction exists yet, so every member is
            // cancelled immediately (buffered members of a DISPATCHED flow
            // cannot exist — flows dispatch whole).
            executionsById.remove(executionId, execution);
            TxStreamException cause = new TxStreamException("TXSTREAM_EXECUTION_CANCELLED",
                    reason != null ? reason : "Execution cancelled before dispatch");
            for (ItemState member : execution.members) {
                releasePermit(member);
                project(member, TxStreamItemStatus.CANCELLED,
                        builder -> builder.error(cause), false);
            }
            return true;
        }
        execution.pendingCancelReason = reason != null ? reason : "cancelled";
        EngineGateway.ExecutionHandle handle = execution.handle;
        if (handle != null) {
            try {
                handle.requestCancel(reason);
            } catch (RuntimeException cancelFailure) {
                log.warn("TxFlowStream[{}] cancel signal failed for execution '{}'",
                        streamId, executionId, cancelFailure);
            }
            return true;
        }
        synchronized (stateLock) {
            // Claimed but not started: the dispatch task forwards the pending
            // reason the moment the handle exists; members settle from the
            // engine outcome.
            LaneQueue lane = laneQueues.get(execution.lane.canonicalSpendingIdentity());
            return lane != null && lane.inFlight == execution;
        }
    }

    @Override
    public Optional<TxStreamItemResult> getItemStatus(String itemId) {
        ItemState state = items.get(itemId);
        if (state == null) {
            return stateStore.getItem(streamId, itemId);
        }
        projectLiveSubmitted(state);
        TxStreamItemResult current = state.projection.current();
        if (current.getStatus() == TxStreamItemStatus.RECOVERY_REQUIRED) {
            return Optional.of(reconcileFromSnapshot(state));
        }
        return Optional.of(current);
    }

    @Override
    public Optional<TxStreamItemResult> reconcile(String itemId) {
        ItemState state = items.get(itemId);
        if (state == null) {
            return Optional.empty();
        }
        projectLiveSubmitted(state);
        return Optional.of(reconcileFromSnapshot(state));
    }

    /**
     * Live SUBMITTED read-through: for a {@code PLANNED} item with a live
     * handle, scans the handle's events after the item's cursor for
     * {@code TRANSACTION_SUBMITTED} and projects {@code SUBMITTED} (a live
     * transition-table hop) with the latest submitted attempt's hash. The
     * advancing cursor is kept on the item state and exposed through
     * {@link TxStreamReceipt#eventCursor()}. Members of a shared flow only
     * match events carrying their own step id.
     */
    private void projectLiveSubmitted(ItemState state) {
        if (state.projection.current().getStatus() != TxStreamItemStatus.PLANNED) {
            return;
        }
        EngineGateway.ExecutionHandle handle = state.handle;
        if (handle == null) {
            return;
        }
        List<FlowEvent> tail;
        try {
            tail = handle.eventsAfter(state.eventCursor.get());
        } catch (RuntimeException readFailure) {
            log.warn("TxFlowStream[{}] live event read failed for item '{}'",
                    streamId, state.item.getItemId(), readFailure);
            return;
        }
        long cursor = state.eventCursor.get();
        String hash = null;
        boolean shared = state.sharedExecution;
        boolean wholeFlow = state.wholeFlow;
        for (FlowEvent event : tail) {
            cursor = Math.max(cursor, event.sequence());
            // A template item owns the whole flow, so any step's submission is
            // its live SUBMITTED evidence; other items match only their step.
            boolean stepMatches = wholeFlow ? true
                    : shared
                            ? state.stepId.equals(event.stepId())
                            : event.stepId() == null || event.stepId().equals(state.stepId);
            if (event.type() == FlowEventType.TRANSACTION_SUBMITTED
                    && event.transactionHash() != null
                    && stepMatches) {
                // Latest submitted attempt wins, consistent with hashFromEvents.
                hash = event.transactionHash();
            }
        }
        long advanced = cursor;
        state.eventCursor.accumulateAndGet(advanced, Math::max);
        if (hash != null) {
            String submittedHash = hash;
            project(state, TxStreamItemStatus.SUBMITTED,
                    builder -> builder.transactionHash(submittedHash), false);
        }
    }

    /**
     * Read-through repair: consults the authoritative engine snapshot and
     * fast-forwards a non-final projection to the snapshot-derived status,
     * emitting the repair to the event listener.
     */
    private TxStreamItemResult reconcileFromSnapshot(ItemState state) {
        TxStreamItemResult current = state.projection.current();
        if (ItemProjection.isFinal(current.getStatus()) || state.executionId == null) {
            return current;
        }
        Optional<FlowExecutionSnapshot> snapshot;
        try {
            snapshot = gateway.executionSnapshot(state.executionId);
        } catch (RuntimeException readFailure) {
            log.warn("TxFlowStream[{}] reconciliation read failed for item '{}'",
                    streamId, state.item.getItemId(), readFailure);
            return current;
        }
        if (snapshot.isEmpty()) {
            return current;
        }
        // BUG-T1: whole-flow template items reconcile through templateFlowStatus,
        // symmetric with the live terminal pass and the present-snapshot
        // re-attach — a PARTIALLY_COMPLETED template stays RECOVERY_REQUIRED
        // (equal to current → no-op) instead of being fast-forwarded to a false
        // FAILED, while a later COMPLETED still repairs it to CONFIRMED (the
        // isFinal short-circuit above never blocks a non-final RECOVERY_REQUIRED).
        TxStreamItemStatus target = state.wholeFlow
                ? templateFlowStatus(snapshot.get().state())
                : state.sharedExecution
                        ? memberSnapshotStatus(snapshot.get(), state)
                        : snapshotStatus(snapshot.get().state());
        if (target == null || target == current.getStatus()) {
            return current;
        }
        String repairedHash = current.getTransactionHash() != null
                ? current.getTransactionHash()
                : hashFromSnapshotData(snapshot.get().data(),
                        state.sharedExecution ? state.stepId : null);
        TxStreamItemResult repaired = project(state, target,
                builder -> builder
                        .transactionHash(repairedHash)
                        .error(target == TxStreamItemStatus.CONFIRMED ? null
                                : current.getError()),
                true);
        return repaired != null ? repaired : state.projection.current();
    }

    private TxStreamItemStatus snapshotStatus(FlowExecutionState state) {
        switch (state) {
            case COMPLETED:
                return TxStreamItemStatus.CONFIRMED;
            case FAILED:
            case PARTIALLY_COMPLETED:
            case ROLLED_BACK:
                return TxStreamItemStatus.FAILED;
            case CANCELLED:
                return TxStreamItemStatus.CANCELLED;
            default:
                return null; // running or still recovery-required: no repair
        }
    }

    /**
     * Snapshot-derived repair target for one member of a shared execution.
     *
     * <p>A flow-level state is not per-member truth in a multi-item flow, so
     * the member's own latest attempt (scanned from the snapshot data, scoped
     * to the member's step) decides first: {@code CONFIRMED}/{@code FAILED}/
     * {@code CANCELLED} attempt states repair the member directly. Without
     * member-level evidence the flow state is used only where it is
     * unambiguous for every member ({@code COMPLETED}, {@code CANCELLED},
     * whole-flow {@code FAILED}/{@code ROLLED_BACK}); a
     * {@code PARTIALLY_COMPLETED} flow says nothing about which members
     * confirmed, so the member stays {@code RECOVERY_REQUIRED} rather than
     * being guessed {@code FAILED}.</p>
     */
    private TxStreamItemStatus memberSnapshotStatus(FlowExecutionSnapshot snapshot,
                                                    ItemState state) {
        AttemptState attemptState = latestMemberAttemptState(snapshot.data(), state.stepId);
        if (attemptState != null) {
            switch (attemptState) {
                case CONFIRMED:
                    return TxStreamItemStatus.CONFIRMED;
                case FAILED:
                    return TxStreamItemStatus.FAILED;
                case CANCELLED:
                    return TxStreamItemStatus.CANCELLED;
                default:
                    break; // not member-terminal: fall through to the flow state
            }
        }
        switch (snapshot.state()) {
            case COMPLETED:
                return TxStreamItemStatus.CONFIRMED;
            case CANCELLED:
                return TxStreamItemStatus.CANCELLED;
            case FAILED:
            case ROLLED_BACK:
                return TxStreamItemStatus.FAILED;
            default:
                return null; // PARTIALLY_COMPLETED / running: ambiguous per member
        }
    }

    /**
     * Latest attempt state recorded for one step in durable snapshot data;
     * same coupling note as {@link #hashFromSnapshotData(Map, String)}.
     */
    private AttemptState latestMemberAttemptState(Map<String, Object> data, String stepId) {
        AttemptState best = null;
        int bestAttempt = Integer.MIN_VALUE;
        for (Object value : data.values()) {
            if (!(value instanceof Map)) continue;
            for (Object candidate : ((Map<?, ?>) value).values()) {
                if (!(candidate instanceof FlowAttemptSnapshot)) continue;
                FlowAttemptSnapshot attempt = (FlowAttemptSnapshot) candidate;
                if (!stepId.equals(attempt.stepId())) continue;
                if (attempt.attemptNumber() > bestAttempt) {
                    bestAttempt = attempt.attemptNumber();
                    best = attempt.state();
                }
            }
        }
        return best;
    }

    @Override
    public TxStreamStats getStats() {
        int pending = 0;
        int inFlight;
        synchronized (stateLock) {
            pending += windowBuffer.size();
            for (BatchState batch : planningQueue) {
                for (ItemState member : batch.membersView()) {
                    if (!member.prePlanCancelled && !member.projection.isSettled()) {
                        pending++;
                    }
                }
            }
            for (LaneQueue lane : laneQueues.values()) {
                for (ExecutionState queued : lane.queue) {
                    pending += queued.members.size();
                }
            }
            inFlight = inFlightCount;
        }
        return new TxStreamStats(acceptedCount.get(), plannedCount.get(), submittedCount.get(),
                confirmedCount.get(), failedCount.get(), cancelledCount.get(),
                // F1 backstop: the seed-site accounting (accountSeededRecoveryRequired)
                // makes the gauge provably non-negative; this clamp guards against any
                // future accounting drift so the published gauge never under-reports as
                // a negative.
                Math.max(0L, recoveryRequiredCount.get()), pending, inFlight);
    }

    // ------------------------------------------------------------------
    // Internal types
    // ------------------------------------------------------------------

    private record PreparedItem(String claimKey, String executionId, String flowId,
                                String stepId, FlowStep enforcedStep, String fingerprint,
                                ResolvedLane lane, String templateId, TxFlow templateDefinition) {
        boolean isTemplate() {
            return templateDefinition != null;
        }
    }

    /** Per-canonical-identity dispatch FIFO; state guarded by the stream's stateLock. */
    private static final class LaneQueue {
        final String identity;
        final ArrayDeque<ExecutionState> queue = new ArrayDeque<>();
        ExecutionState inFlight;
        boolean inRing;

        LaneQueue(String identity) {
            this.identity = identity;
        }
    }

    /**
     * One planned engine execution: the flow, its lane, the flow-level claim,
     * and the member items riding it. Single-member executions are the
     * per-item planner's; multi-member executions share one handle and can
     * only be cancelled whole.
     */
    private static final class ExecutionState {
        final String executionId;
        final String claimKey;
        final TxFlow definition;
        final ResolvedLane lane;
        final List<ItemState> members;
        final String batchId;
        final RequestBindings requestBindings;
        /** Template reference for a parameterized-invocation execution, else null. */
        final String templateId;
        volatile EngineGateway.ExecutionHandle handle;
        volatile String pendingCancelReason;

        ExecutionState(String executionId, String claimKey, TxFlow definition,
                       ResolvedLane lane, List<ItemState> members, String batchId,
                       RequestBindings requestBindings) {
            this(executionId, claimKey, definition, lane, members, batchId, requestBindings, null);
        }

        ExecutionState(String executionId, String claimKey, TxFlow definition,
                       ResolvedLane lane, List<ItemState> members, String batchId,
                       RequestBindings requestBindings, String templateId) {
            this.executionId = executionId;
            this.claimKey = claimKey;
            this.definition = definition;
            this.lane = lane;
            this.members = List.copyOf(members);
            this.batchId = batchId;
            this.requestBindings = requestBindings;
            this.templateId = templateId;
        }

        String flowId() {
            return definition.getId();
        }
    }

    /**
     * The bindings, secure references, and inline sensitive values gathered for
     * one execution's request. Non-sensitive bindings and secure references are
     * persistable in a durable planned record; inline sensitive values are not
     * — an execution carrying any of them is rejected
     * {@code TXSTREAM_NON_PERSISTABLE_SECRET} at bind time in durable mode.
     */
    private static final class RequestBindings {
        final Map<String, Object> nonSensitive;
        final Map<String, Object> sensitive;
        final Map<String, String> secureRefs;

        RequestBindings(Map<String, Object> nonSensitive, Map<String, Object> sensitive,
                        Map<String, String> secureRefs) {
            this.nonSensitive = Map.copyOf(nonSensitive);
            this.sensitive = Map.copyOf(sensitive);
            this.secureRefs = Map.copyOf(secureRefs);
        }

        boolean hasSensitiveInline() {
            return !sensitive.isEmpty();
        }

        FlowBindings toFlowBindings() {
            if (nonSensitive.isEmpty() && sensitive.isEmpty()) {
                return FlowBindings.empty();
            }
            FlowBindings.Builder builder = FlowBindings.builder();
            nonSensitive.forEach(builder::put);
            sensitive.forEach(builder::put);
            return builder.build();
        }

        static RequestBindings ofMembers(List<ItemState> members) {
            Map<String, Object> nonSensitive = new LinkedHashMap<>();
            Map<String, Object> sensitive = new LinkedHashMap<>();
            Map<String, String> secureRefs = new LinkedHashMap<>();
            for (ItemState member : members) {
                nonSensitive.putAll(member.item.getBindings());
                sensitive.putAll(member.item.getSensitiveBindings());
                secureRefs.putAll(member.item.getSecureBindingReferences());
            }
            return new RequestBindings(nonSensitive, sensitive, secureRefs);
        }

        static RequestBindings ofRecord(TxStreamPlannedRecord record) {
            // A persisted plan never carries inline sensitive values.
            return new RequestBindings(record.bindings(), Map.of(),
                    record.secureBindingReferences());
        }
    }

    /**
     * One planning batch (one closed window). Batch status derives from the
     * member items and is frozen — member references dropped — once terminal,
     * so retained batches never pin evicted item states.
     */
    private static final class BatchState {
        final String batchId;
        final List<String> itemIds;
        private List<ItemState> members;
        private TxStreamBatchStatus status = TxStreamBatchStatus.PLANNED;
        private List<String> executionIds = List.of();
        private Throwable failure;
        private TxStreamBatchResult frozen;
        /** Guarded by the stream's stateLock. */
        boolean inPlanningQueue;

        BatchState(String batchId, List<ItemState> members) {
            this.batchId = batchId;
            this.members = List.copyOf(members);
            List<String> ids = new ArrayList<>(members.size());
            for (ItemState member : members) {
                ids.add(member.item.getItemId());
            }
            this.itemIds = List.copyOf(ids);
        }

        synchronized List<ItemState> membersView() {
            return members;
        }

        synchronized void setRunning(List<String> executionIds) {
            if (frozen == null && status == TxStreamBatchStatus.PLANNED) {
                this.status = TxStreamBatchStatus.RUNNING;
                this.executionIds = List.copyOf(executionIds);
            }
        }

        synchronized void noteFailure(Throwable cause) {
            if (frozen == null && failure == null) {
                failure = cause;
            }
        }

        /**
         * Derives the terminal status once every member is FINAL; freezes the
         * snapshot and drops the member references. Returns {@code true} when
         * this call performed the completion.
         */
        synchronized boolean tryComplete(String streamId) {
            if (frozen != null) {
                return false;
            }
            int confirmed = 0;
            int cancelled = 0;
            for (ItemState member : members) {
                TxStreamItemStatus memberStatus = member.projection.current().getStatus();
                if (!ItemProjection.isFinal(memberStatus)) {
                    return false;
                }
                if (memberStatus == TxStreamItemStatus.CONFIRMED) confirmed++;
                if (memberStatus == TxStreamItemStatus.CANCELLED) cancelled++;
            }
            int total = members.size();
            if (confirmed == total) {
                status = TxStreamBatchStatus.COMPLETED;
            } else if (confirmed > 0) {
                status = TxStreamBatchStatus.PARTIALLY_COMPLETED;
            } else if (cancelled == total) {
                status = TxStreamBatchStatus.CANCELLED;
            } else {
                status = TxStreamBatchStatus.FAILED;
            }
            frozen = new TxStreamBatchResult(streamId, batchId, status, itemIds,
                    executionIds, failure);
            members = List.of();
            return true;
        }

        synchronized TxStreamBatchResult snapshot(String streamId) {
            if (frozen != null) {
                return frozen;
            }
            return new TxStreamBatchResult(streamId, batchId, status, itemIds,
                    executionIds, failure);
        }
    }

    /**
     * A planned-record member together with the record that carries it and
     * whether that execution is shared (&gt; 1 member) — the inputs
     * {@link #reconstructItemState} needs to rebuild a durable-absent recovery
     * item during a reconciliation pass.
     */
    private static final class MemberRef {
        final TxStreamPlannedRecord record;
        final TxStreamPlannedRecord.Member member;
        final boolean shared;

        MemberRef(TxStreamPlannedRecord record, TxStreamPlannedRecord.Member member,
                  boolean shared) {
            this.record = record;
            this.member = member;
            this.shared = shared;
        }
    }

    private static final class ItemState {
        final TxWorkItem item;
        final String claimKey;
        final String fingerprint;
        final ResolvedLane lane;
        final FlowStep enforcedStep;
        final ItemProjection projection;
        final TxStreamReceipt receipt;
        /** Exclusive engine-event cursor consumed by the projection. */
        final AtomicLong eventCursor = new AtomicLong();
        /** Whether this item currently holds one buffer-capacity permit. */
        final AtomicBoolean permitHeld = new AtomicBoolean();
        /**
         * Planning outputs. Under the default per-item planner the identity
         * is claim-derived and assigned at accept; under other planners the
         * fields are assigned when the item's window is planned.
         */
        volatile String executionId;
        volatile String flowId;
        volatile String stepId;
        volatile String batchId;
        volatile boolean sharedExecution;
        /**
         * Set for a template item (ADR 0004, iteration 3): a single-member
         * execution of a whole (possibly multi-step) parameterized flow whose
         * item status derives from the flow's overall state, not one step's
         * result. Never {@code true} together with {@code sharedExecution}.
         */
        volatile boolean wholeFlow;
        volatile ExecutionState execution;
        volatile EngineGateway.ExecutionHandle handle;
        /**
         * Set (under the stream's stateLock) when the item was cancelled
         * after its window closed but before the planning pump snapshotted
         * the batch; planning excludes flagged members.
         */
        volatile boolean prePlanCancelled;
        /**
         * Set when the item is rejected before it exists anywhere
         * authoritative (idempotency-key reuse, failed registration): its
         * settle projection still reaches attached receipts and listeners but
         * is never written to the state store, which must not resurrect a
         * rejected item; the state also never enters the retention FIFO — it
         * is retained nowhere, so there is nothing to evict.
         */
        volatile boolean suppressStoreProjection;
        /**
         * Set when the settling projection must not touch the cumulative
         * stream counters (idempotency-key reuse: the submission never became
         * stream work, so it bumps neither the accepted nor the failed count).
         */
        volatile boolean suppressCounters;

        private ItemState(TxWorkItem item, String claimKey, String fingerprint,
                          ResolvedLane lane, FlowStep enforcedStep, ItemProjection projection,
                          EngineTxFlowStream stream) {
            this.item = item;
            this.claimKey = claimKey;
            this.fingerprint = fingerprint;
            this.lane = lane;
            this.enforcedStep = enforcedStep;
            this.projection = projection;
            this.receipt = new TxStreamReceipt(stream.streamId, item.getItemId(),
                    projection, eventCursor);
        }

        static ItemState pending(EngineTxFlowStream stream, TxWorkItem item,
                                 PreparedItem prepared, boolean claimDerivedIdentity) {
            TxStreamItemResult.Builder accepted = TxStreamItemResult
                    .builder(stream.streamId, item.getItemId(), TxStreamItemStatus.ACCEPTED)
                    .laneName(prepared.lane.laneName())
                    .updatedAt(stream.clock.instant());
            if (claimDerivedIdentity) {
                accepted.executionId(prepared.executionId).stepId(prepared.stepId);
            }
            ItemProjection projection = new ItemProjection(accepted.build());
            ItemState state = new ItemState(item, prepared.claimKey, prepared.fingerprint,
                    prepared.lane, prepared.enforcedStep, projection, stream);
            if (claimDerivedIdentity) {
                state.executionId = prepared.executionId;
                state.flowId = prepared.flowId;
                state.stepId = prepared.stepId;
            }
            return state;
        }

        static ItemState settledWithoutRegistration(EngineTxFlowStream stream, TxWorkItem item,
                                                    TxStreamItemResult failed,
                                                    String fingerprint) {
            ItemProjection projection = ItemProjection.settled(failed);
            return new ItemState(item, null, fingerprint, null, null, projection, stream);
        }

        /**
         * Reconstructs a re-attached item from a persisted plan, seeded at its
         * stored projection AND at the durable projection's last per-item
         * sequence (BUG-1), so the first authoritative advance dominates the
         * store CAS. A settling seed ({@code RECOVERY_REQUIRED}) begins
         * already-settled but still repairable; a non-settling seed keeps an
         * open promise the re-attach or live dispatch completes. The item's real
         * per-item claim key is carried (BUG-2) so the idempotency-key-reuse
         * guard survives restart and eviction cleans the index up.
         */
        static ItemState reattached(EngineTxFlowStream stream, TxWorkItem item, String claimKey,
                                    String fingerprint, ResolvedLane lane, TxStreamItemResult seed,
                                    long storedSequence) {
            ItemProjection projection = ItemProjection.reattaching(seed, storedSequence);
            return new ItemState(item, claimKey, fingerprint, lane, null, projection, stream);
        }
    }

    private static final class Acceptance {
        enum Disposition { ACCEPTED, ATTACHED, VALIDATION_FAILED, CONFLICT, FULL, CLOSED, PAUSED }

        final Disposition disposition;
        final TxStreamReceipt receipt;
        final TxStreamDuplicateItemException conflict;

        private Acceptance(Disposition disposition, TxStreamReceipt receipt,
                           TxStreamDuplicateItemException conflict) {
            this.disposition = disposition;
            this.receipt = receipt;
            this.conflict = conflict;
        }

        static Acceptance accepted(TxStreamReceipt receipt) {
            return new Acceptance(Disposition.ACCEPTED, receipt, null);
        }

        static Acceptance attached(TxStreamReceipt receipt) {
            return new Acceptance(Disposition.ATTACHED, receipt, null);
        }

        static Acceptance validationFailed(TxStreamReceipt receipt) {
            return new Acceptance(Disposition.VALIDATION_FAILED, receipt, null);
        }

        static Acceptance conflict(TxStreamDuplicateItemException conflict) {
            return new Acceptance(Disposition.CONFLICT, null, conflict);
        }

        static Acceptance full() {
            return new Acceptance(Disposition.FULL, null, null);
        }

        static Acceptance closed() {
            return new Acceptance(Disposition.CLOSED, null, null);
        }

        /** Temporarily not accepting: an ownership STANDBY that may reclaim. */
        static Acceptance paused() {
            return new Acceptance(Disposition.PAUSED, null, null);
        }
    }
}
