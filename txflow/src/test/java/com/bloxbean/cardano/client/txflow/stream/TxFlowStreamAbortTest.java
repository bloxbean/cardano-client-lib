package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowError;
import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR 0004 Decision 7.5: {@code abort(reason)} is forced but honest about
 * cooperativeness, and {@code close(Duration)} composes drain-then-abort.
 */
class TxFlowStreamAbortTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void abortCancelsBufferedSignalsInFlightAndQuiescenceWaitsForHandles() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt inFlight = stream.submit(planItem("pay-1"));
            TxStreamReceipt buffered = stream.submit(planItem("pay-2"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();

            AbortReport report = stream.abort("shutting down");

            assertEquals(List.of("pay-2"), report.cancelledItemIds());
            TxStreamItemResult cancelled = buffered.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CANCELLED, cancelled.getStatus());
            TxStreamException cause = assertInstanceOf(TxStreamException.class,
                    cancelled.getError());
            assertEquals("TXSTREAM_ABORTED", cause.getCode());
            assertEquals("shutting down", cause.getMessage());

            assertEquals(List.of(inFlight.executionId().orElseThrow()),
                    report.signalledExecutionIds());
            assertTrue(handle.cancelRequested.get(), "the in-flight handle must be signalled");
            assertEquals("shutting down", handle.cancelReason);
            assertFalse(report.quiescence().toCompletableFuture().isDone(),
                    "quiescence must wait for the signalled handle");
            assertEquals(0, stream.getStats().pendingBufferSize(),
                    "dispatch queues are released immediately");

            // Dispatch resources are released: nothing new is accepted.
            assertEquals(EmitResult.Status.CLOSED, stream.trySubmit(planItem("pay-3")).getStatus());
            TxStreamException closedFailure = assertThrows(TxStreamException.class,
                    () -> stream.submit(planItem("pay-4")));
            assertEquals("TXSTREAM_CLOSED", closedFailure.getCode());

            // The engine acknowledges the cancellation; the retained
            // completion machinery still delivers the terminal outcome.
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.CANCELLED, List.of(),
                    new FlowError("TXFLOW_CANCELLED", FlowErrorCategory.CANCELLATION,
                            "Execution cancelled", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.CANCELLED,
                    inFlight.completion().toCompletableFuture().get(10, TimeUnit.SECONDS)
                            .getStatus());
            report.quiescence().toCompletableFuture().get(10, TimeUnit.SECONDS);
            stream.drain();
        }
    }

    @Test
    void abortStillDeliversConfirmedOutcomeWhenExecutionWinsTheRace() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt inFlight = stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();

            AbortReport report = stream.abort("too late");
            assertTrue(handle.cancelRequested.get());

            // The transaction was already submitted; the execution confirms
            // despite the cooperative signal — the projection must be honest.
            handle.completeConfirmed(STEP_ID, "tx-raced");
            TxStreamItemResult outcome = inFlight.completion().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("tx-raced", outcome.getTransactionHash());
            report.quiescence().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus(),
                    "projection machinery must survive the abort");
        }
    }

    @Test
    void abortWithNothingInFlightCompletesQuiescenceImmediately() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());

            AbortReport report = stream.abort(null);
            assertTrue(report.cancelledItemIds().isEmpty());
            assertTrue(report.signalledExecutionIds().isEmpty());
            assertTrue(report.quiescence().toCompletableFuture().isDone());
        }
    }

    @Test
    void abortIsIdempotentReturningTheFirstReport() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();

            AbortReport first = stream.abort("first");
            AbortReport second = stream.abort("second");
            assertSame(first, second, "repeated aborts return the first report");

            handle.completeConfirmed(STEP_ID, "tx-1");
            first.quiescence().toCompletableFuture().join();
        }
    }

    @Test
    void abortCallbackObservesPublishedReportExactlyOnceBeforeClosedAndIsReentrant() {
        StubEngineGateway gateway = new StubEngineGateway();
        AtomicReference<TxFlowStream> streamRef = new AtomicReference<>();
        AtomicReference<AbortReport> callbackReport = new AtomicReference<>();
        AtomicReference<AbortReport> reentrantReport = new AtomicReference<>();
        AtomicBoolean callbackSawIncompleteQuiescence = new AtomicBoolean();
        List<String> events = new ArrayList<>();
        TxStreamEventListener listener = new TxStreamEventListener() {
            @Override
            public void onStreamAborted(String streamId, AbortReport report) {
                events.add("aborted");
                callbackReport.set(report);
                callbackSawIncompleteQuiescence.set(
                        !report.quiescence().toCompletableFuture().isDone());
                reentrantReport.set(streamRef.get().abort("reentrant"));
            }

            @Override
            public void onStreamClosed(String streamId) {
                events.add("closed");
            }
        };

        TxFlowStream stream = builder("payouts", gateway).eventListener(listener).build();
        streamRef.set(stream);
        stream.start();
        stream.submit(planItem("pay-1"));
        StubEngineGateway.StubHandle handle = gateway.lastHandle();

        AbortReport report = stream.abort("outer");
        assertSame(report, callbackReport.get(),
                "the callback must observe the already-published report");
        assertSame(report, reentrantReport.get(),
                "reentrant abort returns the same immutable report");
        assertTrue(callbackSawIncompleteQuiescence.get(),
                "abort notification does not imply cooperative cancellation has quiesced");
        assertEquals(List.of("aborted", "closed"), events,
                "abort fires once before the existing close notification");

        assertSame(report, stream.abort("later"));
        assertEquals(List.of("aborted", "closed"), events,
                "repeated abort must not duplicate lifecycle callbacks");
        handle.completeConfirmed(STEP_ID, "tx-1");
        report.quiescence().toCompletableFuture().join();
    }

    @Test
    void throwingAbortListenerCannotSuppressClosedNotification() {
        StubEngineGateway gateway = new StubEngineGateway();
        AtomicInteger aborted = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        TxStreamEventListener listener = new TxStreamEventListener() {
            @Override
            public void onStreamAborted(String streamId, AbortReport report) {
                aborted.incrementAndGet();
                throw new IllegalStateException("listener failure");
            }

            @Override
            public void onStreamClosed(String streamId) {
                closed.incrementAndGet();
            }
        };

        TxFlowStream stream = builder("payouts", gateway).eventListener(listener).build();
        stream.start();
        AbortReport first = stream.abort("stop");
        assertSame(first, stream.abort("again"));
        assertEquals(1, aborted.get());
        assertEquals(1, closed.get(),
                "listener isolation must allow the close notification to follow");
    }

    @Test
    void closeWithGraceDeadlineDrainsInTimeWithoutCancellingAnything() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.completeConfirmed(STEP_ID, "tx-1");

            stream.close(Duration.ofSeconds(5));

            assertFalse(handle.cancelRequested.get(), "a drained close cancels nothing");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals(EmitResult.Status.CLOSED, stream.trySubmit(planItem("pay-2")).getStatus());
        }
    }

    @Test
    void closeWithGraceDeadlineAbortsTheRemainderWhenTheDeadlineElapses() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt inFlight = stream.submit(planItem("pay-1"));
            TxStreamReceipt buffered = stream.submit(planItem("pay-2"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();

            // pay-1 can never complete on its own, so a ZERO grace deadline
            // elapses immediately and deterministically — no wall-clock wait.
            stream.close(Duration.ZERO);

            assertTrue(handle.cancelRequested.get(),
                    "the deadline must escalate to abort and signal the in-flight handle");
            TxStreamItemResult cancelled = buffered.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CANCELLED, cancelled.getStatus());
            assertEquals("TXSTREAM_ABORTED", assertInstanceOf(TxStreamException.class,
                    cancelled.getError()).getCode());
            assertEquals(EmitResult.Status.CLOSED, stream.trySubmit(planItem("pay-3")).getStatus());
            assertFalse(inFlight.completion().toCompletableFuture().isDone(),
                    "close(Duration) does not promise execution termination at the deadline");

            handle.completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    inFlight.completion().toCompletableFuture().get(10, TimeUnit.SECONDS)
                            .getStatus());
        }
    }

    @Test
    void closeWithZeroGraceDeadlineIsGracefulWhenNothingIsOutstanding() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());

            // Boundary: with every accepted item already settled, a ZERO
            // grace deadline still takes the graceful branch — nothing to
            // wait for means nothing to abort.
            stream.close(Duration.ZERO);

            assertFalse(handle.cancelRequested.get(),
                    "a fully drained close(ZERO) must not escalate to abort");
            assertEquals(EmitResult.Status.CLOSED, stream.trySubmit(planItem("pay-2")).getStatus());
        }
    }

    // ------------------------------------------------------------------
    // BUG-B regression: post-abort straggler lost wakeup
    // ------------------------------------------------------------------

    @Test
    void postAbortStragglerAcceptedInTheAbortWindowSettlesAndDrainReturns() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        ManualExecutor executor = new ManualExecutor();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .stateStore(store)
                .executor(executor)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            executor.runAll();                          // initial pump: nothing to claim

            TxStreamReceipt claimed = stream.submit(planItem("pay-1"));
            executor.runNext();                         // pump claims pay-1; its dispatch
                                                        // task is queued but NOT run yet

            // The straggler passes both accepting checks, then parks inside
            // the authoritative registration write — exactly the abort window.
            store.registerEntered = new CountDownLatch(1);
            store.registerGate = new CountDownLatch(1);
            Future<TxStreamReceipt> straggler = pool.submit(
                    () -> stream.submit(planItem("pay-2")));
            assertTrue(store.registerEntered.await(10, TimeUnit.SECONDS),
                    "the straggler must be past the accepting checks before the abort");

            AbortReport report = stream.abort("shutting down");
            assertTrue(report.cancelledItemIds().isEmpty(),
                    "nothing was buffered when the abort drained the queues");
            assertEquals(List.of(claimed.executionId().orElseThrow()),
                    report.signalledExecutionIds());

            store.registerGate.countDown();
            TxStreamReceipt stragglerReceipt = straggler.get(10, TimeUnit.SECONDS);

            // Consume the straggler's own pump wakeup FIRST: the lane is
            // still claimed by pay-1, so this pump claims nothing. When
            // pay-1's pending dispatch task then hits the aborted branch and
            // frees the lane, only the re-pump on that branch can rescue the
            // queued straggler — this is the lost wakeup under test.
            executor.runLast();
            executor.runAll();

            assertEquals(TxStreamItemStatus.CANCELLED,
                    claimed.completion().toCompletableFuture().get(10, TimeUnit.SECONDS)
                            .getStatus());
            TxStreamItemResult outcome = stragglerReceipt.completion().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CANCELLED, outcome.getStatus());
            assertEquals("TXSTREAM_ABORTED", assertInstanceOf(TxStreamException.class,
                    outcome.getError()).getCode());
            report.quiescence().toCompletableFuture().get(10, TimeUnit.SECONDS);
            stream.drain();
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Abort racing dispatch on a real pool
    // ------------------------------------------------------------------

    @Test
    void abortRacingDispatchOnRealPoolEndsCancelledAndNeverLeavesAnUnobservedExecution()
            throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        CompletableFuture<StubEngineGateway.StubHandle> handleCreated = new CompletableFuture<>();
        gateway.handleCreatedHook = () -> handleCreated.complete(gateway.lastHandle());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = builder("payouts", gateway).executor(pool).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            // Race: the pool is claiming/dispatching pay-1 while this thread
            // aborts. Legal outcomes — buffered-cancel, claimed-then-cancelled
            // before start, or started-then-signalled — must all end CANCELLED
            // with no execution left unobserved.
            AbortReport report = stream.abort("race");
            String executionId = receipt.executionId().orElseThrow();

            boolean cancelledBuffered = report.cancelledItemIds().contains("pay-1");
            boolean signalled = report.signalledExecutionIds().contains(executionId);
            assertTrue(cancelledBuffered ^ signalled,
                    "the item was either still buffered or claimed at abort time, never both");

            CompletableFuture<TxStreamItemResult> settled =
                    receipt.completion().toCompletableFuture();
            // Whichever happens first: the item settles without an engine
            // start, or the start won the race and the test must deliver the
            // engine's terminal outcome for the signalled execution.
            CompletableFuture.anyOf(settled, handleCreated).get(10, TimeUnit.SECONDS);
            if (!settled.isDone()) {
                StubEngineGateway.StubHandle handle = handleCreated.get(10, TimeUnit.SECONDS);
                handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                        FlowExecutionState.CANCELLED, List.of(),
                        new FlowError("TXFLOW_CANCELLED", FlowErrorCategory.CANCELLATION,
                                "Execution cancelled", null, false),
                        StubEngineGateway.NOW, StubEngineGateway.NOW));
            }
            TxStreamItemResult outcome = settled.get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CANCELLED, outcome.getStatus(),
                    "every branch of the race must end terminal CANCELLED");
            if (!gateway.handles.isEmpty()) {
                // A started execution is never unobserved: the Dekker pair
                // (abort writes the pending reason then reads the handle;
                // dispatch publishes the handle then reads the reason)
                // guarantees at least one side delivered the cancel signal.
                assertTrue(gateway.lastHandle().cancelRequested.get(),
                        "a started execution must have received the cancellation signal");
            }
            report.quiescence().toCompletableFuture().get(10, TimeUnit.SECONDS);
            stream.drain();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void drainAfterAbortReturnsOnceSignalledHandlesSettle() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            stream.abort("stop");

            CompletableFuture<Void> drained = CompletableFuture.runAsync(stream::drain);
            assertFalse(drained.isDone(), "drain must wait for the signalled handle");
            handle.completeConfirmed(STEP_ID, "tx-1");
            drained.get(10, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TxFlowStream.Builder builder(String streamId, StubEngineGateway gateway) {
        return new TxFlowStream.Builder(streamId, gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER)));
    }
}
