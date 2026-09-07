package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 3d — multi-instance stream ownership via epoch-fenced ownership leases
 * (ADR 0004): single-owner active/standby with lease-fenced failover. Two
 * {@link EngineTxFlowStream} instances share ONE durable {@link TxStreamStateStore}
 * and ONE durable engine gateway; a manual {@link MutableClock} and per-instance
 * {@link ManualScheduler}s drive the lease renewal / acquire-poll. No real
 * threads, timers, or sleeps.
 */
class TxFlowStreamOwnershipTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;
    private static final Duration LEASE = Duration.ofSeconds(30);

    // ------------------------------------------------------------------
    // (a) exactly one ACTIVE owner dispatches; the other stands by
    // ------------------------------------------------------------------

    @Test
    void twoInstancesOnOneStreamElectExactlyOneActiveOwnerAndTheStandbyDispatchesNothing() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);

        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a").build();
        TxFlowStream b = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-b").build();

        a.start();
        b.start();

        assertEquals(OwnershipStatus.State.ACTIVE, a.ownership().ownershipState(),
                "the first to start acquires the lease and becomes ACTIVE");
        assertTrue(a.ownership().isActive());
        assertEquals(OwnershipStatus.State.STANDBY, b.ownership().ownershipState(),
                "the second stands by; it cannot acquire while A's lease is unexpired");

        // The ACTIVE owner dispatches; the item reaches the shared engine.
        TxStreamReceipt receipt = a.submit(planItem("pay-1"));
        engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
        assertEquals(TxStreamItemStatus.CONFIRMED,
                receipt.completion().toCompletableFuture().join().getStatus());
        assertEquals(1, engine.started.size(), "exactly one start reached the engine");

        // The standby refuses new work — nothing of B's ever reaches the engine.
        TxStreamException rejected = assertThrows(TxStreamException.class,
                () -> b.submit(planItem("pay-2")));
        assertEquals("TXSTREAM_NOT_ACTIVE", rejected.getCode());
        assertEquals(1, engine.started.size(), "the standby dispatched nothing");

        a.close();
        b.close();
    }

    // ------------------------------------------------------------------
    // (b) failover: a standby takes over and resumes the durable pending items
    // ------------------------------------------------------------------

    @Test
    void whenTheActiveOwnerCrashesAStandbyTakesOverAndReattachesTheDurablePendingItems() {
        StubEngineGateway engine = durableEngine(); // no auto-complete: item stays running
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerB = new ManualScheduler();

        // A becomes ACTIVE and dispatches pay-1 (durably bound + persisted), then
        // "crashes" — we abandon it without closing, so its lease is not released.
        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a").build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();
        assertEquals(1, engine.started.size());

        // B starts as a standby (A still holds the unexpired lease).
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();
        b.start();
        assertEquals(OwnershipStatus.State.STANDBY, b.ownership().ownershipState());

        // The ACTIVE lease expires; B's acquire-poll fires and takes over.
        clock.advance(LEASE.plusSeconds(1));
        schedulerB.pending().fire();

        assertEquals(OwnershipStatus.State.ACTIVE, b.ownership().ownershipState(),
                "the standby took over ownership after the lease expired");
        // B re-attached and re-dispatched pay-1 under the SAME deterministic
        // execution id (a real engine claim dedups; the stub lacks idempotency for
        // a still-running execution, so it records a second start with that id).
        assertEquals(2, engine.started.size());
        assertEquals(executionId, engine.started.get(1).getExecutionId(),
                "the re-dispatch carries the same deterministic execution id");

        engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
        assertEquals(TxStreamItemStatus.CONFIRMED,
                b.getItemStatus("pay-1").orElseThrow().getStatus());
        b.close();
    }

    // ------------------------------------------------------------------
    // (c) the FENCE: a stale ACTIVE owner whose lease was taken over steps down
    //     on its next (fenced) renewal and dispatches nothing further
    // ------------------------------------------------------------------

    @Test
    void aStaleActiveOwnerIsFencedOnItsNextRenewalStepsDownAndDispatchesNothingFurther() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerA = new ManualScheduler();
        ManualScheduler schedulerB = new ManualScheduler();

        TxFlowStream a = ownedBuilder(engine, store, schedulerA, clock, "owner-a").build();
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();
        a.start(); // ACTIVE (epoch 1)
        b.start(); // STANDBY
        long epochA = a.ownership().epoch();

        // A GC-pauses past its lease expiry; B's poll takes over (epoch 2).
        clock.advance(LEASE.plusSeconds(1));
        schedulerB.pending().fire();
        assertEquals(OwnershipStatus.State.ACTIVE, b.ownership().ownershipState());
        assertTrue(b.ownership().epoch() > epochA, "the new owner's epoch exceeds the prior");

        // A resumes and, while it still thinks it is ACTIVE, accepts an item — but
        // its lease is expired, so the dispatch gate is closed: the item queues,
        // it never reaches the engine.
        TxStreamReceipt staleReceipt = a.submit(planItem("stale-1"));
        assertEquals(0, engine.started.size(), "a stale owner past expiry dispatches nothing");

        // A's renewal fires and is FENCED (epoch superseded) → A steps down.
        schedulerA.pending().fire();
        assertEquals(OwnershipStatus.State.STANDBY, a.ownership().ownershipState(),
                "the fenced owner steps down to STANDBY");
        assertEquals(0L, a.ownership().epoch(), "a stepped-down instance holds no lease");

        // The queued item was settled (never dispatched) and A dispatches nothing
        // further, even though it had queued work.
        assertEquals(0, engine.started.size(), "no dispatch after the fence");
        TxStreamItemResult staleOutcome = staleReceipt.completion().toCompletableFuture().join();
        assertEquals(TxStreamItemStatus.CANCELLED, staleOutcome.getStatus());
        assertTrue(staleOutcome.getError() instanceof TxStreamException);
        assertEquals("TXSTREAM_OWNERSHIP_LOST",
                ((TxStreamException) staleOutcome.getError()).getCode());

        // A submit after step-down is refused (A is no longer accepting).
        assertThrows(TxStreamException.class, () -> a.submit(planItem("stale-2")));
        assertEquals(0, engine.started.size());
        b.close();
    }

    // ------------------------------------------------------------------
    // (d) epoch monotonicity across takeovers
    // ------------------------------------------------------------------

    @Test
    void ownershipEpochIsMonotonicAcrossTakeovers() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerB = new ManualScheduler();

        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a").build();
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();
        a.start();
        b.start();
        long epochA = a.ownership().epoch();
        assertTrue(epochA >= 1);

        clock.advance(LEASE.plusSeconds(1));
        schedulerB.pending().fire();
        assertTrue(b.ownership().epoch() > epochA,
                "a takeover mints a strictly higher epoch: " + b.ownership().epoch()
                        + " !> " + epochA);
        a.close();
        b.close();
    }

    // ------------------------------------------------------------------
    // (e) release on close → a standby can immediately acquire
    // ------------------------------------------------------------------

    @Test
    void closingTheActiveOwnerReleasesOwnershipSoAStandbyCanImmediatelyAcquire() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerB = new ManualScheduler();

        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a").build();
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();
        a.start();
        b.start();
        long epochA = a.ownership().epoch();

        // A closes and releases; the clock has NOT advanced, yet B acquires
        // immediately (the lease was released, not merely expired) with a higher
        // epoch (the epoch high-water survives release).
        a.close();
        assertEquals(OwnershipStatus.State.RELEASED, a.ownership().ownershipState());

        schedulerB.pending().fire();
        assertEquals(OwnershipStatus.State.ACTIVE, b.ownership().ownershipState());
        assertTrue(b.ownership().epoch() > epochA);
        b.close();
    }

    // ------------------------------------------------------------------
    // (f) ownership disabled (default) → single-instance dispatches immediately
    // ------------------------------------------------------------------

    @Test
    void ownershipDisabledByDefaultDispatchesImmediatelyUnchanged() {
        StubEngineGateway engine = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            assertEquals(OwnershipStatus.State.DISABLED, stream.ownership().ownershipState());
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals(1, engine.started.size());
            assertEquals(OwnershipStatus.State.DISABLED, stream.ownership().ownershipState());
        }
    }

    // ------------------------------------------------------------------
    // (g) build() invariant: ownership requires durable store + engine + scheduler
    // ------------------------------------------------------------------

    @Test
    void ownershipRequiresADurableStoreADurableEngineAndAMaintenanceExecutor() {
        StubEngineGateway durable = durableEngine();
        StubEngineGateway nonDurable = new StubEngineGateway();

        // durable store + durable engine + scheduler → OK
        ownedBuilder(durable, new SharedDurableTxStreamStore(), new ManualScheduler(),
                Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC), "owner-a").build().close();

        // non-durable store
        assertBuildFails("requires a durable TxStreamStateStore",
                base(durable).stateStore(TxStreamStateStore.inMemory())
                        .maintenanceExecutor(new ManualScheduler())
                        .ownership("owner-a", LEASE));

        // durable store but non-durable engine
        assertBuildFails("requires a durable FlowEngine store",
                new TxFlowStream.Builder("payouts", nonDurable)
                        .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                        .executor(Runnable::run)
                        .stateStore(new SharedDurableTxStreamStore())
                        .maintenanceExecutor(new ManualScheduler())
                        .ownership("owner-a", LEASE));

        // durable store + durable engine but no maintenanceExecutor
        assertBuildFails("requires maintenanceExecutor",
                base(durable).stateStore(new SharedDurableTxStreamStore())
                        .ownership("owner-a", LEASE));

        // blank owner token / non-positive duration
        assertBuildFails("non-blank ownerToken",
                base(durable).stateStore(new SharedDurableTxStreamStore())
                        .maintenanceExecutor(new ManualScheduler())
                        .ownership("  ", LEASE));
        assertBuildFails("positive leaseDuration",
                base(durable).stateStore(new SharedDurableTxStreamStore())
                        .maintenanceExecutor(new ManualScheduler())
                        .ownership("owner-a", Duration.ZERO));
    }

    // ------------------------------------------------------------------
    // (h) no double-dispatch: the engine sees one start per deterministic id
    // ------------------------------------------------------------------

    @Test
    void aFailoverReattachDoesNotDoubleDispatchAnAlreadyRunningExecution() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerB = new ManualScheduler();

        // A dispatches pay-1 (start #1), the engine leaves it running, and A
        // crashes with the binding DISPATCHING; the engine finished it unwatched
        // and a durable snapshot exists.
        store.suppressConfirmOutcome = true;
        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a").build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();
        assertEquals(1, engine.started.size());
        engine.putSnapshot(executionId, FlowExecutionState.COMPLETED);
        store.suppressConfirmOutcome = false;

        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();
        b.start();

        clock.advance(LEASE.plusSeconds(1));
        schedulerB.pending().fire(); // B takes over → re-attach

        assertEquals(OwnershipStatus.State.ACTIVE, b.ownership().ownershipState());
        // A present engine snapshot is re-projected, NEVER re-dispatched: the
        // engine still sees exactly ONE start for the deterministic execution id.
        assertEquals(1, engine.started.size(), "a present snapshot is re-projected, not re-started");
        assertEquals(TxStreamItemStatus.CONFIRMED,
                b.getItemStatus("pay-1").orElseThrow().getStatus());
        b.close();
    }

    // ------------------------------------------------------------------
    // ACTIVE renewal keeps the owner ACTIVE (the happy renewal path)
    // ------------------------------------------------------------------

    @Test
    void anActiveOwnerRenewsOnScheduleAndKeepsDispatching() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler scheduler = new ManualScheduler();

        TxFlowStream a = ownedBuilder(engine, store, scheduler, clock, "owner-a").build();
        a.start();
        long epoch = a.ownership().epoch();

        // Advance within the lease and fire the renewal: still ACTIVE, same epoch.
        clock.advance(Duration.ofSeconds(10));
        scheduler.pending().fire();
        assertEquals(OwnershipStatus.State.ACTIVE, a.ownership().ownershipState());
        assertEquals(epoch, a.ownership().epoch(), "renewal preserves the epoch");

        // It still dispatches.
        TxStreamReceipt receipt = a.submit(planItem("pay-1"));
        engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
        assertEquals(TxStreamItemStatus.CONFIRMED,
                receipt.completion().toCompletableFuture().join().getStatus());
        a.close();
    }

    // ------------------------------------------------------------------
    // a fenced owner that later reclaims ownership re-opens for dispatch
    // (exercises the re-activation / idempotent open path)
    // ------------------------------------------------------------------

    @Test
    void aFencedOwnerThatLaterReclaimsOwnershipReopensForDispatch() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerA = new ManualScheduler();
        ManualScheduler schedulerB = new ManualScheduler();

        TxFlowStream a = ownedBuilder(engine, store, schedulerA, clock, "owner-a").build();
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();
        a.start(); // ACTIVE
        b.start(); // STANDBY

        // B takes over after A's lease expires; A is fenced on its next renewal.
        clock.advance(LEASE.plusSeconds(1));
        schedulerB.pending().fire();
        schedulerA.pending().fire(); // A renewal → fenced → STANDBY
        assertEquals(OwnershipStatus.State.STANDBY, a.ownership().ownershipState());

        // B releases (close); A's poll reclaims ownership and re-opens for dispatch.
        b.close();
        schedulerA.pending().fire(); // A poll → reclaims
        assertEquals(OwnershipStatus.State.ACTIVE, a.ownership().ownershipState(),
                "the fenced owner reclaimed ownership after the new owner released");

        TxStreamReceipt receipt = a.submit(planItem("pay-1"));
        engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
        assertEquals(TxStreamItemStatus.CONFIRMED,
                receipt.completion().toCompletableFuture().join().getStatus(),
                "a reclaimed owner dispatches again");
        a.close();
    }

    // ------------------------------------------------------------------
    // ownership + reconciliation observer coexist on one maintenance scheduler
    // ------------------------------------------------------------------

    @Test
    void ownershipAndTheReconciliationObserverCoexistOnOneSchedulerWithIndependentTimers() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        ManualScheduler scheduler = new ManualScheduler();

        try (TxFlowStream a = ownedBuilder(engine, store, scheduler,
                Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC), "owner-a")
                .reconciliationInterval(Duration.ofSeconds(60))
                .build()) {
            a.start(); // ACTIVE → arms BOTH the ownership timer and the reconciliation timer
            List<ManualScheduler.ScheduledTask> live = scheduler.tasks.stream()
                    .filter(task -> !task.fired.get() && !task.cancelled.get())
                    .toList();
            assertEquals(2, live.size(),
                    "the ownership renewal and the reconciliation observer share one scheduler");
            // The two delays are independent (leaseDuration/3 = 10s, reconciliation = 60s).
            assertTrue(live.stream().anyMatch(t -> t.delayMillis == 10_000L));
            assertTrue(live.stream().anyMatch(t -> t.delayMillis == 60_000L));

            // Firing each re-arms its own successor without clobbering the other.
            live.forEach(ManualScheduler.ScheduledTask::fire);
            assertEquals(OwnershipStatus.State.ACTIVE, a.ownership().ownershipState());
            long stillLive = scheduler.tasks.stream()
                    .filter(task -> !task.fired.get() && !task.cancelled.get()).count();
            assertEquals(2, stillLive, "each timer re-armed exactly one successor");
        }
    }

    // ------------------------------------------------------------------
    // ownership() reports DISABLED for a non-ownership stream, RELEASED after close
    // ------------------------------------------------------------------

    @Test
    void ownershipReportsReleasedAfterCloseAndStaleLeaseCannotBeReacquiredByADifferentOwner() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);

        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a").build();
        a.start();
        assertTrue(a.ownership().isActive());
        a.close();
        assertEquals(OwnershipStatus.State.RELEASED, a.ownership().ownershipState());
        assertEquals(0L, a.ownership().epoch());
    }

    // ------------------------------------------------------------------
    // FINDING A: a reclaim after a fenced step-down RE-SCANS durable state, so it
    // resumes work an interim owner planned+persisted while this instance stood by
    // ------------------------------------------------------------------

    @Test
    void aReclaimReattachesInterimDurableWorkThatAnInterimOwnerPlannedWhileWeStoodBy() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerA = new ManualScheduler();
        ManualScheduler schedulerB = new ManualScheduler();

        TxFlowStream a = ownedBuilder(engine, store, schedulerA, clock, "owner-a").build();
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();
        a.start(); // ACTIVE (epoch 1) — re-attach runs once vs an empty store, memoizes empty
        b.start(); // STANDBY

        // A GC-pauses past its lease; B takes over; A is fenced on its next renewal.
        clock.advance(LEASE.plusSeconds(1));
        schedulerB.pending().fire(); // B ACTIVE (epoch 2)
        schedulerA.pending().fire(); // A renewal fenced → STANDBY (re-attach memo reset)
        assertEquals(OwnershipStatus.State.STANDBY, a.ownership().ownershipState());

        // The interim owner B dispatches NEW durable work while A stands by: a
        // planned+persisted item whose engine execution finishes but whose binding
        // is left DISPATCHING (B "crashes" before confirming), so its stored
        // projection stays non-terminal (PLANNED). B is then abandoned (never closed).
        store.suppressConfirmOutcome = true;
        String interimExec = b.submit(planItem("pay-interim")).executionId().orElseThrow();
        engine.putSnapshot(interimExec, FlowExecutionState.COMPLETED);
        store.suppressConfirmOutcome = false;
        assertEquals(1, engine.started.size(), "the interim owner dispatched the interim item once");

        // B's lease expires; A's poll reclaims ownership and RE-SCANS durable state.
        clock.advance(LEASE.plusSeconds(1));
        schedulerA.pending().fire(); // A poll → reclaims → openForWork → reattach() re-scans
        assertEquals(OwnershipStatus.State.ACTIVE, a.ownership().ownershipState(),
                "the previously-fenced instance reclaimed ownership");

        // A's reclaim re-attach picked up B's interim durable item and resumed it
        // from the present engine snapshot — CONFIRMED, not stranded non-terminal.
        // Without the memo reset A would return its cached empty re-attach, never
        // re-scan, and leave pay-interim stuck at PLANNED forever.
        assertEquals(TxStreamItemStatus.CONFIRMED,
                a.getItemStatus("pay-interim").orElseThrow().getStatus(),
                "the reclaiming owner re-attached and resumed the interim owner's durable work");
        assertEquals(1, engine.started.size(),
                "the present-snapshot interim item is re-projected, never re-dispatched");
        a.close();
    }

    // ------------------------------------------------------------------
    // FINDING B: an ACTIVE close/abort fires onOwnershipLost (symmetric with
    // onOwnershipAcquired); a STANDBY close does not
    // ------------------------------------------------------------------

    @Test
    void closingAnActiveOwnerFiresOnOwnershipLostWhileAStandbyCloseDoesNot() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);

        List<OwnershipStatus> lostA = new ArrayList<>();
        List<OwnershipStatus> lostB = new ArrayList<>();
        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a")
                .eventListener(recordingLost(lostA)).build();
        TxFlowStream b = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-b")
                .eventListener(recordingLost(lostB)).build();
        a.start(); // ACTIVE
        b.start(); // STANDBY

        // A standby close never held ownership: it must NOT fire onOwnershipLost.
        b.close();
        assertTrue(lostB.isEmpty(), "a standby close must not fire onOwnershipLost");

        // An ACTIVE close releases ownership and fires onOwnershipLost exactly once
        // (Finding B: the callback is symmetric with onOwnershipAcquired, matching
        // its javadoc).
        a.close();
        assertEquals(1, lostA.size(), "an active close fires onOwnershipLost exactly once");
        assertEquals(OwnershipStatus.State.RELEASED, lostA.get(0).ownershipState());
    }

    // ------------------------------------------------------------------
    // FINDING D: ownership against a durable store that does not support the
    // ownership-lease trio fails at build() rather than wedging every instance
    // ------------------------------------------------------------------

    @Test
    void ownershipAgainstADurableStoreWithoutOwnershipSupportFailsAtBuild() {
        StubEngineGateway durable = durableEngine();
        SharedDurableTxStreamStore noOwnership = new SharedDurableTxStreamStore();
        noOwnership.supportsOwnership = false; // durable, but no epoch-fenced lease trio
        assertBuildFails("supports ownership leases",
                base(durable).stateStore(noOwnership)
                        .maintenanceExecutor(new ManualScheduler())
                        .ownership("owner-a", LEASE));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private TxStreamEventListener recordingLost(List<OwnershipStatus> sink) {
        return new TxStreamEventListener() {
            @Override
            public void onOwnershipLost(OwnershipStatus status) {
                sink.add(status);
            }
        };
    }

    private void assertBuildFails(String messageFragment, TxFlowStream.Builder builder) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(failure.getMessage().contains(messageFragment),
                "expected build failure mentioning '" + messageFragment + "' but was: "
                        + failure.getMessage());
    }

    private TxFlowStream.Builder base(StubEngineGateway engine) {
        return new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run);
    }

    private TxFlowStream.Builder ownedBuilder(StubEngineGateway engine, TxStreamStateStore store,
                                              ManualScheduler scheduler, Clock clock,
                                              String ownerToken) {
        return new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .stateStore(store)
                .maintenanceExecutor(scheduler)
                .ownership(ownerToken, LEASE)
                .clock(clock);
    }

    private StubEngineGateway durableEngine() {
        StubEngineGateway engine = new StubEngineGateway();
        engine.durable = true;
        return engine;
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId, payment());
    }

    private TxPlan payment() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER));
    }

    /** Deterministic, manually advanced clock. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
