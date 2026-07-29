package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 1C windowing: count windows close at N, time windows close through
 * one deterministic wakeup on the caller-owned maintenance scheduler (delay
 * asserted, fired manually — no real delays), {@code flush()}/{@code drain()}
 * close partial windows, {@code abort()} cancels them, and the no-window
 * default keeps the immediate per-item fast path.
 */
class TxFlowStreamWindowPolicyTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";

    // ------------------------------------------------------------------
    // Count windows
    // ------------------------------------------------------------------

    @Test
    void countWindowBuffersUntilNThenPlansOneFlowForTheWindow() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.count(3)).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));
            assertTrue(gateway.started.isEmpty(), "a partial window must not dispatch");
            assertEquals(2, stream.getStats().pendingBufferSize());
            assertTrue(stream.getBatchStatus("batch-1").isEmpty(),
                    "no batch exists before the window closes");

            confirmAllStepsOnStart(gateway);
            TxStreamReceipt third = stream.submit(planItem("pay-3"));

            assertEquals(1, gateway.started.size(), "the closed window plans one flow");
            assertEquals(3, gateway.started.get(0).getDefinition().getSteps().size());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    second.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    third.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamBatchStatus.COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    @Test
    void flushClosesThePartialWindowImmediately() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.count(5)).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));
            assertTrue(gateway.started.isEmpty());

            confirmAllStepsOnStart(gateway);
            stream.flush();

            assertEquals(1, gateway.started.size(), "flush must close the partial window");
            assertEquals(2, gateway.started.get(0).getDefinition().getSteps().size());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    second.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void drainPlansTheOpenPartialWindowBeforeAwaitingQuiescence() {
        StubEngineGateway gateway = new StubEngineGateway();
        confirmAllStepsOnStart(gateway);
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.count(10)).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));

            stream.drain();

            assertEquals(1, gateway.started.size(),
                    "drain must flush and plan the open partial window first");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    second.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void abortFailsOpenWindowItemsCancelledWithoutPlanning() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.count(10)).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));

            AbortReport report = stream.abort("shutting down");

            assertEquals(List.of("pay-1", "pay-2"), report.cancelledItemIds());
            assertTrue(report.signalledExecutionIds().isEmpty());
            assertTrue(report.quiescence().toCompletableFuture().isDone());
            assertTrue(gateway.started.isEmpty(), "an aborted window must never plan");
            assertEquals(TxStreamItemStatus.CANCELLED,
                    first.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CANCELLED,
                    second.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Time windows through the manual scheduler seam
    // ------------------------------------------------------------------

    @Test
    void timeWindowSchedulesWakeupWithMaxAgeDelayAndPlansWhenAgeReached() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.countOrTime(10, Duration.ofSeconds(5)))
                .maintenanceExecutor(scheduler)
                .clock(clock)
                .build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));

            ManualScheduler.ScheduledTask wakeup = scheduler.pending();
            assertEquals(5000, wakeup.delayMillis,
                    "one wakeup per open window, armed with the full maxAge");
            assertTrue(gateway.started.isEmpty());

            confirmAllStepsOnStart(gateway);
            clock.advance(Duration.ofSeconds(5));
            wakeup.fire();

            assertEquals(1, gateway.started.size(), "the aged window must plan on the wakeup");
            assertEquals(2, gateway.started.get(0).getDefinition().getSteps().size());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    second.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void earlyWakeupRearmsForTheRemainderWithoutPlanning() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.countOrTime(10, Duration.ofSeconds(5)))
                .maintenanceExecutor(scheduler)
                .clock(clock)
                .build()) {
            stream.start();
            stream.submit(planItem("pay-1"));

            ManualScheduler.ScheduledTask early = scheduler.pending();
            clock.advance(Duration.ofSeconds(2));
            early.fire();

            assertTrue(gateway.started.isEmpty(), "an early wakeup must not close the window");
            ManualScheduler.ScheduledTask rearmed = scheduler.pending();
            assertEquals(3000, rearmed.delayMillis, "the wakeup re-arms for the remainder");

            confirmAllStepsOnStart(gateway);
            clock.advance(Duration.ofSeconds(3));
            rearmed.fire();
            assertEquals(1, gateway.started.size());
        }
    }

    @Test
    void countCloseCancelsThePendingWakeupAndStaleWakeupIsHarmless() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        confirmAllStepsOnStart(gateway);
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.countOrTime(2, Duration.ofSeconds(5)))
                .maintenanceExecutor(scheduler)
                .clock(clock)
                .build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            ManualScheduler.ScheduledTask wakeup = scheduler.pending();
            stream.submit(planItem("pay-2"));

            assertEquals(1, gateway.started.size(), "the count bound closes the window");
            assertTrue(wakeup.isCancelled(), "the count close must cancel the age wakeup");

            // A wakeup that fires anyway (cancellation raced) is a no-op.
            wakeup.fire();
            assertEquals(1, gateway.started.size());
        }
    }

    @Test
    void cancellingTheLastWindowedItemCancelsTheAgeWakeup() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.countOrTime(10, Duration.ofSeconds(5)))
                .maintenanceExecutor(scheduler)
                .clock(clock)
                .build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            ManualScheduler.ScheduledTask wakeup = scheduler.pending();

            assertEquals(CancelOutcome.Kind.CANCELLED_BUFFERED,
                    stream.cancelItem("pay-1", "not needed").kind());
            assertTrue(wakeup.isCancelled(),
                    "an emptied window has nothing left to age out");
            assertEquals(TxStreamItemStatus.CANCELLED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertTrue(gateway.started.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // BUG-1C-R1 regression: post-stop window straggler
    // ------------------------------------------------------------------

    @Test
    void postAbortWindowStragglerSettlesCancelledAbortedAndDrainReturns() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.count(3))
                .stateStore(store)
                .build()) {
            stream.start();
            TxStreamReceipt buffered = stream.submit(planItem("pay-1"));

            // The straggler passes both accepting checks, then parks inside
            // the authoritative registration write — exactly the abort window.
            store.registerEntered = new CountDownLatch(1);
            store.registerGate = new CountDownLatch(1);
            Future<TxStreamReceipt> straggler = pool.submit(
                    () -> stream.submit(planItem("pay-2")));
            assertTrue(store.registerEntered.await(10, TimeUnit.SECONDS),
                    "the straggler must be past the accepting checks before the abort");

            AbortReport report = stream.abort("shutting down");
            assertEquals(List.of("pay-1"), report.cancelledItemIds(),
                    "only the drained window member appears in the report");

            // Released after the abort drained the window buffer: the item
            // lands in a buffer nothing will ever close (count 3 can't fill,
            // there is no age bound, flush/drain already unavailable) unless
            // the accept-side rescue settles it.
            store.registerGate.countDown();
            TxStreamItemResult outcome = straggler.get(10, TimeUnit.SECONDS)
                    .completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CANCELLED, outcome.getStatus());
            assertEquals("TXSTREAM_ABORTED", assertInstanceOf(TxStreamException.class,
                    outcome.getError()).getCode());
            assertEquals(TxStreamItemStatus.CANCELLED,
                    buffered.completion().toCompletableFuture().join().getStatus());
            assertTrue(gateway.started.isEmpty(), "nothing may reach the engine");
            stream.drain();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void drainRacingWindowStragglerSettlesItCancelledClosedAndDrainReturns() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        confirmAllStepsOnStart(gateway);
        CountDownLatch flowStarted = new CountDownLatch(1);
        gateway.handleCreatedHook = flowStarted::countDown;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.count(3))
                .stateStore(store)
                .build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));

            store.registerEntered = new CountDownLatch(1);
            store.registerGate = new CountDownLatch(1);
            Future<TxStreamReceipt> straggler = pool.submit(
                    () -> stream.submit(planItem("pay-2")));
            assertTrue(store.registerEntered.await(10, TimeUnit.SECONDS),
                    "the straggler must be past the accepting checks before the drain");

            // drain() stops accepting and flushes the open window ([pay-1])
            // synchronously before blocking on quiescence; the engine start
            // of that window proves both happened while the straggler is
            // still parked inside registration.
            Future<?> drained = pool.submit(stream::drain);
            assertTrue(flowStarted.await(10, TimeUnit.SECONDS));

            store.registerGate.countDown();
            TxStreamItemResult outcome = straggler.get(10, TimeUnit.SECONDS)
                    .completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CANCELLED, outcome.getStatus(),
                    "a straggler behind a stopped stream's flushed window must settle");
            assertEquals("TXSTREAM_CLOSED", assertInstanceOf(TxStreamException.class,
                    outcome.getError()).getCode());

            drained.get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
            assertEquals(1, gateway.started.size(),
                    "only the flushed window reached the engine");
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // No-window default: the immediate per-item fast path
    // ------------------------------------------------------------------

    @Test
    void noWindowDefaultPlansInlineOnTheAcceptingThread() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualExecutor manual = new ManualExecutor();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(manual)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            manual.runAll(); // consume the start() pump wakeup
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));

            // Planning already happened inline during submit — the batch is
            // RUNNING and the claim-derived execution identity is bound —
            // even though no executor task has run yet (only engine dispatch
            // rides the executor).
            assertEquals(TxStreamBatchStatus.RUNNING,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
            assertTrue(receipt.executionId().isPresent(),
                    "per-item identity is claim-derived and known at accept");
            assertTrue(gateway.started.isEmpty());

            manual.runAll();
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Windowed items keep attach/conflict and key-reuse semantics
    // ------------------------------------------------------------------

    @Test
    void windowedItemsKeepAttachConflictAndKeyReuseSemantics() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = windowedBuilder(gateway)
                .window(WindowPolicy.count(10)).build()) {
            stream.start();
            TxStreamReceipt original = stream.submit(planItem("pay-1"));

            // Identical redelivery of a still-windowed item attaches.
            assertSame(original, stream.submit(planItem("pay-1")));
            assertEquals(EmitResult.Status.DUPLICATE_ATTACHED,
                    stream.trySubmit(planItem("pay-1")).getStatus());

            // Different content for the same windowed item id conflicts.
            TxWorkItem different = TxWorkItem.builder("pay-1")
                    .withTxPlan(TxPlan.from(new Tx()
                            .payToAddress(RECEIVER, Amount.ada(42)).from(SENDER)))
                    .build();
            assertThrows(TxStreamDuplicateItemException.class, () -> stream.submit(different));

            // Idempotency-key reuse is enforced while the owner is windowed.
            TxStreamException reuse = assertThrows(TxStreamException.class,
                    () -> stream.submit(TxWorkItem.builder("pay-2")
                            .withTxPlan(plan()).withIdempotencyKey("pay-1").build()));
            assertEquals("TXSTREAM_IDEMPOTENCY_KEY_REUSE", reuse.getCode());

            assertTrue(gateway.started.isEmpty(), "nothing dispatches while the window is open");
            confirmAllStepsOnStart(gateway);
            stream.flush();
            assertEquals(1, gateway.started.size());
            assertEquals(1, gateway.started.get(0).getDefinition().getSteps().size(),
                    "only the original item is in the window");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    original.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Builder validation
    // ------------------------------------------------------------------

    @Test
    void timeBasedWindowRequiresMaintenanceExecutorAtBuild() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxFlowStream.Builder builder = windowedBuilder(gateway)
                .window(WindowPolicy.countOrTime(10, Duration.ofSeconds(5)));
        IllegalStateException failure = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(failure.getMessage().contains("maintenanceExecutor"));

        // Count-only windows need no scheduler.
        try (TxFlowStream stream = windowedBuilder(new StubEngineGateway())
                .window(WindowPolicy.count(5)).build()) {
            stream.start();
        }
    }

    @Test
    void countOnlyWindowLargerThanBufferIsRejectedAtBuild() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxFlowStream.Builder builder = windowedBuilder(gateway)
                .maxBufferSize(4)
                .window(WindowPolicy.count(5));
        IllegalStateException failure = assertThrows(IllegalStateException.class, builder::build,
                "a count-only window that can never fill would wedge blocking submit");
        assertTrue(failure.getMessage().contains("maxBufferSize"));

        // With a time bound the same count is fine: age closes the window.
        try (TxFlowStream stream = windowedBuilder(new StubEngineGateway())
                .maxBufferSize(4)
                .window(WindowPolicy.countOrTime(5, Duration.ofSeconds(5)))
                .maintenanceExecutor(new ManualScheduler())
                .build()) {
            stream.start();
        }
    }

    @Test
    void windowPolicyValidatesItsBounds() {
        assertThrows(IllegalArgumentException.class, () -> WindowPolicy.count(0));
        assertThrows(IllegalArgumentException.class,
                () -> WindowPolicy.countOrTime(5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> WindowPolicy.time(Duration.ofSeconds(-1)));
        assertEquals(Integer.MAX_VALUE, WindowPolicy.time(Duration.ofSeconds(1)).getMaxItems());
        assertFalse(WindowPolicy.count(3).isTimeBased());
        assertTrue(WindowPolicy.countOrTime(3, Duration.ofSeconds(1)).isTimeBased());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Single-lane builder defaulting to the perWindow planner. */
    private TxFlowStream.Builder windowedBuilder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .planner(TxStreamPlanner.perWindow())
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    /** Every engine start completes immediately with all steps confirmed. */
    private void confirmAllStepsOnStart(StubEngineGateway gateway) {
        gateway.immediateResult = request -> new FlowExecutionResult(
                request.getExecutionId(), "fp", FlowExecutionState.COMPLETED,
                request.getDefinition().getSteps().stream()
                        .map(step -> FlowStepResult.successAt(step.getId(),
                                "tx-" + step.getId(), List.of(), List.of(),
                                StubEngineGateway.NOW))
                        .collect(Collectors.toList()),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW);
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId, plan());
    }

    private TxPlan plan() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER));
    }

    /** Deterministic, manually advanced clock for window-age checks. */
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
