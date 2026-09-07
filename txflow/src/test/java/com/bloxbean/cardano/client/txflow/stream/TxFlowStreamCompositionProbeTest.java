package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-feature composition probes (ADR 0004 v2): interactions between
 * ownership failover, windows/planning, templates, batching, the Flow
 * ingestion adapter, and stats — combinations no single-slice review saw
 * together. Deterministic: {@code Runnable::run} executor, manual schedulers,
 * manual clock, scripted engine.
 */
class TxFlowStreamCompositionProbeTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;
    private static final String TEMPLATE_ID = "payout";
    private static final Duration LEASE = Duration.ofSeconds(30);

    // ==================================================================
    // Probe 1 (ownership x windows x planning pump).
    //
    // The ownership fence can fire on the maintenance scheduler while the
    // planning pump is INSIDE planner.plan(...) for a closed window: the batch
    // is out of the planning queue (so stepDownFenced's drain cannot see it)
    // and not yet in the lane queues (so the lane drain cannot see it either).
    // The builder's ownership javadoc promises: "Queued-but-unstarted work is
    // settled CANCELLED (TXSTREAM_OWNERSHIP_LOST) on step-down". This probe
    // fires the fence from inside the planner (exactly the interleaving a
    // concurrent scheduler thread produces) and asserts that promise.
    //
    // EXPECTED TO FAIL (bug): runPlanning has no ownership re-check after it
    // enqueues the executions (it re-checks only !healthy), and both
    // schedulePump() and claimNext() silently refuse to dispatch for a
    // non-owner - so the window's items are stranded unsettled in the lane
    // queues: never CANCELLED, never dispatched, and drain()/close() hang
    // until the instance happens to reclaim ownership.
    // ==================================================================

    @Test
    void ownershipStepDownWhileAWindowIsMidPlanningMustStillSettleItsItemsCancelled() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        TxFlowStreamOwnershipTest.MutableClock clock =
                new TxFlowStreamOwnershipTest.MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerA = new ManualScheduler();
        ManualScheduler schedulerB = new ManualScheduler();

        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();

        // A's planner triggers the fence mid-plan: the lease expires, B's poll
        // takes over, and A's renewal tick fires FENCED - all while A's
        // planning pump is inside plan() for this window.
        TxStreamPlanner fencingPlanner = context -> {
            clock.advance(LEASE.plusSeconds(1));
            schedulerB.pending().fire();   // B: STANDBY -> ACTIVE takeover
            schedulerA.pending().fire();   // A: fenced renewal -> step-down
            return TxStreamPlanner.perItem().plan(context);
        };
        TxFlowStream a = ownedBuilder(engine, store, schedulerA, clock, "owner-a")
                .window(WindowPolicy.count(2))
                .planner(fencingPlanner)
                .build();

        a.start();  // ACTIVE (epoch 1)
        b.start();  // STANDBY

        TxStreamReceipt first = a.submit(planItem("win-1"));
        TxStreamReceipt second = a.submit(planItem("win-2")); // closes the window; plans inline

        assertEquals(OwnershipStatus.State.STANDBY, a.ownership().ownershipState(),
                "precondition: A was fenced and stepped down during planning");
        assertEquals(0, engine.started.size(),
                "precondition: the fenced instance dispatched nothing");

        // Builder ownership contract: queued-but-unstarted work settles
        // CANCELLED (TXSTREAM_OWNERSHIP_LOST) on step-down. A window that was
        // mid-planning when the fence hit is exactly such work - it must not
        // be stranded unsettled (that hangs drain()/close() forever on a
        // never-reclaiming standby).
        assertTrue(first.completion().toCompletableFuture().isDone()
                        && second.completion().toCompletableFuture().isDone(),
                "items whose window was MID-PLANNING at the ownership fence are stranded"
                        + " unsettled in the lane queues: stepDownFenced drained the planning"
                        + " queue and lanes before runPlanning enqueued these executions, and"
                        + " runPlanning re-checks only !healthy (not ownership) after the"
                        + " enqueue - TXSTREAM_OWNERSHIP_LOST never fires and drain()/close()"
                        + " hang until an eventual reclaim");
        TxStreamItemResult r1 = first.completion().toCompletableFuture().join();
        assertEquals(TxStreamItemStatus.CANCELLED, r1.getStatus());
        assertEquals("TXSTREAM_OWNERSHIP_LOST",
                ((TxStreamException) r1.getError()).getCode());
        b.close();
    }

    // ==================================================================
    // Probe 2 (ownership x Flow ingestion adapter).
    //
    // A fenced step-down is TEMPORARY (the instance stays STANDBY and polls to
    // reclaim), but the stream's accept path reports the same CLOSED
    // disposition for "standby" as for a genuinely closed stream. The
    // FlowWorkSource adapter treats CLOSED as terminal: it drops its held
    // items, cancels the upstream subscription, and completes terminated()
    // NORMALLY - so a transient fence permanently kills the ingestion source
    // (a reclaim cannot restart it: sourceStarted is already true and the
    // adapter refuses to re-subscribe once closed), and the drop is signalled
    // as a clean completion.
    //
    // EXPECTED TO FAIL (bug): terminated() must not resolve as a normal,
    // clean completion when the publisher never completed and the stream was
    // never closed - held items were silently discarded on a temporary
    // condition.
    // ==================================================================

    @Test
    void ownershipStepDownMustNotPermanentlyKillTheFlowSourceOrDropHeldItems() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        TxFlowStreamOwnershipTest.MutableClock clock =
                new TxFlowStreamOwnershipTest.MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerA = new ManualScheduler();
        ManualScheduler schedulerB = new ManualScheduler();

        MiniPublisher publisher = new MiniPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 4);
        TxFlowStream a = ownedBuilder(engine, store, schedulerA, clock, "owner-a")
                .source(source)
                .maxBufferSize(1)
                .build();
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b").build();

        a.start();  // ACTIVE: the source subscribes and prefetches
        b.start();  // STANDBY
        publisher.next(planItem("pay-1"));   // dispatched (in flight on the lane)
        publisher.next(planItem("pay-2"));   // occupies the single buffer slot (queued)
        publisher.next(planItem("pay-3"));   // FULL -> held by the adapter, not dropped

        // Fence A: lease expires, B takes over, A's renewal steps it down.
        // Step-down cancels queued pay-2 -> its settle triggers the adapter's
        // retry of held pay-3 -> the standby stream answers CLOSED -> the
        // adapter tears itself down for good.
        clock.advance(LEASE.plusSeconds(1));
        schedulerB.pending().fire();
        schedulerA.pending().fire();
        assertEquals(OwnershipStatus.State.STANDBY, a.ownership().ownershipState(),
                "precondition: A stepped down to STANDBY (a temporary, reclaimable state)");

        assertFalse(source.terminated().toCompletableFuture().isDone(),
                "a TEMPORARY ownership step-down permanently killed the Flow ingestion"
                        + " source: the standby's CLOSED answer made the adapter drop held"
                        + " item pay-3, cancel the upstream subscription, and complete"
                        + " terminated() NORMALLY (indistinguishable from a clean publisher"
                        + " completion) - and a later reclaim cannot restart it"
                        + " (startSourceOnce already ran; a closed FlowWorkSource refuses"
                        + " re-subscription), so ingestion is silently dead for the rest of"
                        + " the instance's life");
        // Cleanup: B's takeover re-attach re-dispatched the still-running pay-1
        // under its deterministic execution id (the stub has no snapshot for a
        // running execution). Settle it so B's graceful close() - which by
        // design awaits in-flight work - returns (the ownership takeover test's
        // established settle-then-close pattern).
        engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
        b.close();
    }

    // ==================================================================
    // Probe 3 (ownership x work source, clean side): a STANDBY instance never
    // starts its configured source, and an activation starts it exactly once
    // (a later renewal tick does not double-start it).
    // ==================================================================

    @Test
    void aStandbyNeverStartsItsWorkSourceAndActivationStartsItExactlyOnce() {
        StubEngineGateway engine = durableEngine();
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        TxFlowStreamOwnershipTest.MutableClock clock =
                new TxFlowStreamOwnershipTest.MutableClock(StubEngineGateway.NOW);
        ManualScheduler schedulerB = new ManualScheduler();

        AtomicInteger starts = new AtomicInteger();
        TxWorkSource recordingSource = sink -> starts.incrementAndGet();

        TxFlowStream a = ownedBuilder(engine, store, new ManualScheduler(), clock, "owner-a")
                .build();
        TxFlowStream b = ownedBuilder(engine, store, schedulerB, clock, "owner-b")
                .source(recordingSource)
                .build();

        a.start();  // ACTIVE
        b.start();  // STANDBY
        assertEquals(0, starts.get(),
                "a standby must not start its source: nothing may pump work into a"
                        + " non-accepting instance");

        a.close();                     // releases the lease
        schedulerB.pending().fire();   // B's poll acquires -> ACTIVE -> opens for work
        assertEquals(OwnershipStatus.State.ACTIVE, b.ownership().ownershipState());
        assertEquals(1, starts.get(), "activation starts the source exactly once");

        schedulerB.pending().fire();   // a later renewal tick
        assertEquals(1, starts.get(), "renewal must not re-start the source");
        b.close();
    }

    // ==================================================================
    // Probe 4 (templates x partitioned lanes): TXSTREAM_LANE_REQUIRED is
    // enforced for a template item under partitioned() even when the item
    // explicitly names a REAL partition lane label via withLane(...) - the
    // label must not smuggle a template onto a hash-partitioned lane.
    // ==================================================================

    @Test
    void templateItemUnderPartitionedFailsLaneRequiredEvenWhenNamingARealPartitionLane() {
        StubEngineGateway engine = new StubEngineGateway();
        PartitionedLanes config = PartitionedLanes.fromAddress("addr_test1vpqfunding")
                .laneAddresses(List.of("addr_test1vpqlane0", "addr_test1vpqlane1"))
                .seedPerLane(Amount.ada(10))
                .bootstrap(false)
                .build();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", engine)
                .lanes(LanePolicy.partitioned(config))
                .template(TEMPLATE_ID, template())
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();

            TxWorkItem templated = TxWorkItem.builder("t-1")
                    .withTemplate(TEMPLATE_ID)
                    .withBinding("receiver", RECEIVER)
                    .withBinding("amount", 1L)
                    .withLane("part-0-payouts")   // the REAL partition-0 lane label
                    .build();
            TxStreamException result = assertThrows(TxStreamException.class,
                    () -> stream.submit(templated));

            assertEquals("TXSTREAM_LANE_REQUIRED", result.getCode(),
                    "naming a real partition lane label must not bypass the template-lane"
                            + " restriction under partitioned()");
            assertEquals(0, engine.started.size(), "nothing reached the engine");
        }
    }

    // ==================================================================
    // Probe 5 (templates x batching planner x one lane): a template item
    // bypasses the window/planner but rides the SAME lane FIFO as the
    // batching planner's merged execution - per-lane exclusivity and FIFO
    // order hold across the two dispatch paths, and the merged batch still
    // completes and derives its batch status.
    // ==================================================================

    @Test
    void templateBypassesTheBatchingPlannerButSharesTheLaneFifoWithItsMergedExecution() {
        StubEngineGateway engine = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .template(TEMPLATE_ID, template())
                .planner(TxStreamPlanner.batching())
                .window(WindowPolicy.count(2))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();

            TxStreamReceipt inline1 = stream.submit(planItem("pay-1")); // buffers (window 1/2)
            TxStreamReceipt templated = stream.submit(TxWorkItem.builder("t-1")
                    .withTemplate(TEMPLATE_ID)
                    .withBinding("receiver", RECEIVER)
                    .withBinding("amount", 1L)
                    .build());                                          // bypasses the window
            assertEquals(1, engine.started.size(),
                    "the template dispatches immediately, ahead of the still-open window");
            assertEquals(template().getId(),
                    engine.started.get(0).getDefinition().getId(),
                    "the first engine start is the compiled template flow");

            TxStreamReceipt inline2 = stream.submit(planItem("pay-2")); // closes the window
            assertEquals(1, engine.started.size(),
                    "the merged batch execution queues FIFO behind the in-flight template"
                            + " on the shared lane - per-lane exclusivity holds across the"
                            + " two dispatch paths");

            engine.handles.get(0).completeConfirmed(STEP_ID, "tx-template");
            assertEquals(2, engine.started.size(),
                    "the merged execution dispatches once the template frees the lane");

            String mergedStepId = engine.started.get(1).getDefinition()
                    .getSteps().get(0).getId();
            engine.handles.get(1).completeConfirmed(mergedStepId, "tx-batch");

            assertEquals(TxStreamItemStatus.CONFIRMED,
                    templated.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    inline1.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    inline2.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamBatchStatus.COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "the window batch completes; the template rides no batch");
        }
    }

    // ==================================================================
    // Probe 6 (stats coherence across mixed features): after a fully settled
    // mixed run (validation failure + pre-plan cancel + windowed pair +
    // template), the cumulative counters reconcile exactly:
    // accepted == confirmed + failed + cancelled, with nothing pending or in
    // flight and planned/submitted counting only work that reached dispatch.
    // ==================================================================

    @Test
    void statsReconcileAcrossAMixedWindowTemplateCancelAndValidationFailureRun() {
        StubEngineGateway engine = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .template(TEMPLATE_ID, template())
                .window(WindowPolicy.count(2))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();

            // 1. Validation failure: wrong lane on a single-lane stream.
            assertThrows(TxStreamException.class, () -> stream.submit(TxWorkItem.builder("bad-lane")
                    .withTxPlan(payment())
                    .withLane("some-other-lane")
                    .build()));
            // 2. Windowed then cancelled before its window closed.
            stream.submit(planItem("cancel-me"));
            assertEquals(CancelOutcome.Kind.CANCELLED_BUFFERED,
                    stream.cancelItem("cancel-me", "changed my mind").kind());
            // 3. A windowed pair that dispatches per-item on the single lane.
            stream.submit(planItem("win-1"));
            stream.submit(planItem("win-2"));
            engine.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            engine.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            // 4. A template invocation.
            stream.submit(TxWorkItem.builder("t-1")
                    .withTemplate(TEMPLATE_ID)
                    .withBinding("receiver", RECEIVER)
                    .withBinding("amount", 1L)
                    .build());
            engine.handles.get(2).completeConfirmed(STEP_ID, "tx-t");

            TxStreamStats stats = stream.getStats();
            assertEquals(4, stats.acceptedItemCount());
            assertEquals(3, stats.confirmedItemCount());
            assertEquals(0, stats.failedItemCount());
            assertEquals(1, stats.cancelledItemCount());
            assertEquals(0, stats.recoveryRequiredItemCount());
            assertEquals(0, stats.pendingBufferSize());
            assertEquals(0, stats.inFlightCount());
            assertEquals(stats.acceptedItemCount(),
                    stats.confirmedItemCount() + stats.failedItemCount()
                            + stats.cancelledItemCount(),
                    "every accepted item of a settled run lands in exactly one final bucket");
            assertEquals(3, stats.plannedItemCount(),
                    "only dispatched work is PLANNED (not the validation failure or the"
                            + " pre-plan cancel)");
            assertEquals(3, stats.submittedItemCount());
        }
    }

    // ==================================================================
    // Probe 7 (abort x Flow ingestion adapter, clean side): abort() with items
    // in the adapter's held deque tears the source down deterministically -
    // held items are discarded (abort semantics), the upstream subscription is
    // cancelled, terminated() resolves, and the abort report's quiescence
    // completes once the signalled in-flight execution reports its outcome.
    // ==================================================================

    @Test
    void abortWithHeldFlowItemsCancelsTheSubscriptionResolvesTerminatedAndQuiesces() {
        StubEngineGateway engine = new StubEngineGateway();
        MiniPublisher publisher = new MiniPublisher();
        FlowWorkSource source = TxWorkSource.fromPublisher(publisher, 4);
        TxFlowStream stream = new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .source(source)
                .maxBufferSize(1)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build();
        stream.start();
        publisher.next(planItem("pay-1"));   // in flight
        publisher.next(planItem("pay-2"));   // queued (single buffer slot)
        publisher.next(planItem("pay-3"));   // FULL -> held by the adapter

        AbortReport report = stream.abort("operator stop");

        assertEquals(List.of("pay-2"), report.cancelledItemIds(),
                "the queued item is cancelled; the held item never entered the stream");
        assertEquals(1, report.signalledExecutionIds().size());
        assertTrue(publisher.cancelled, "the upstream subscription is cancelled on abort");
        assertTrue(source.terminated().toCompletableFuture().isDone(),
                "the source's terminated() stage resolves on abort");
        assertTrue(stream.getItemStatus("pay-3").isEmpty(),
                "the held item never reached the stream (no phantom projection)");
        assertFalse(report.quiescence().toCompletableFuture().isDone(),
                "quiescence waits for the signalled in-flight execution");
        engine.handles.get(0).complete(new com.bloxbean.cardano.client.txflow.exec
                .FlowExecutionResult(engine.handles.get(0).executionId(), "fp",
                com.bloxbean.cardano.client.txflow.exec.FlowExecutionState.CANCELLED,
                List.of(), null, StubEngineGateway.NOW, StubEngineGateway.NOW));
        assertTrue(report.quiescence().toCompletableFuture().isDone(),
                "quiescence resolves once the signalled execution settles");
    }

    // ==================================================================
    // Probe 8 (batch results x cancelExecution, clean side): cancelling one
    // still-queued execution of a two-execution window batch settles its
    // member CANCELLED immediately, keeps the batch RUNNING until the sibling
    // settles, and then derives PARTIALLY_COMPLETED (confirmed > 0).
    // ==================================================================

    @Test
    void cancellingOneQueuedExecutionOfAMultiExecutionBatchDerivesPartiallyCompleted() {
        StubEngineGateway engine = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .window(WindowPolicy.count(2))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("win-1"));
            TxStreamReceipt second = stream.submit(planItem("win-2")); // closes the window
            assertEquals(1, engine.started.size(),
                    "one lane: the first execution dispatches, the second queues");

            String queuedExecutionId = second.executionId().orElseThrow();
            assertTrue(stream.cancelExecution(queuedExecutionId, "cancel the second"),
                    "a queued execution cancels whole");
            assertEquals(TxStreamItemStatus.CANCELLED,
                    second.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamBatchStatus.RUNNING,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "the batch stays RUNNING while its sibling execution is in flight");

            engine.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "confirmed > 0 with a cancelled member derives PARTIALLY_COMPLETED");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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

    /** A parameterized, portable payout template with a single step. */
    private TxFlow template() {
        String yaml = "api_version: txflow.cardano-client.dev/v1alpha1\n"
                + "kind: TxFlow\n"
                + "metadata: {name: payout-template}\n"
                + "spec:\n"
                + "  parameters:\n"
                + "    receiver: {type: address, required: true}\n"
                + "    amount: {type: integer, required: true}\n"
                + "  steps:\n"
                + "    - id: " + STEP_ID + "\n"
                + "      transaction:\n"
                + "        tx: {intents: []}\n";
        return TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
    }

    /** Minimal hand-driven publisher (mirrors the adapter test's seam). */
    private static final class MiniPublisher implements Flow.Publisher<TxWorkItem> {
        final AtomicLong requested = new AtomicLong();
        volatile boolean cancelled;
        private Flow.Subscriber<? super TxWorkItem> subscriber;

        @Override
        public void subscribe(Flow.Subscriber<? super TxWorkItem> s) {
            this.subscriber = s;
            s.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    requested.addAndGet(n);
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        void next(TxWorkItem item) {
            subscriber.onNext(item);
        }
    }
}
