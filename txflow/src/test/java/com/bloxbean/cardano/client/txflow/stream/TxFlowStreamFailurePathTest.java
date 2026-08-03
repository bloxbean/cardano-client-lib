package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.exec.ExecutionEventView;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Failure-path behavior from the 1A review pass: MATCHED re-attach projection,
 * typed dispatch/execution/registration failures, isolated confirm failures,
 * non-portable redelivery attach semantics, and the buffered-item vs systemic
 * failure race.
 */
class TxFlowStreamFailurePathTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void matchedStoredSnapshotReattachProjectsTerminalWithEngineStoreHash() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        String executionId = StreamIdentities.executionId(
                StreamIdentities.namespace("payouts"), "pay-1");
        FlowAttemptSnapshot attempt = new FlowAttemptSnapshot(STEP_ID, 1,
                AttemptState.CONFIRMED,
                new SignedPayload.InlineCbor(new byte[]{1}, "sha", "tx-stored"),
                null, null, List.of(), List.of(), StubEngineGateway.NOW, null);
        gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                Map.of("attempts", Map.of(STEP_ID + ":1", attempt)));
        // Stored-snapshot re-attach: the engine returns a completed handle with
        // an empty step list, exactly like handleForStoredSnapshot.
        gateway.immediateResult = request -> new FlowExecutionResult(
                request.getExecutionId(), "fp", FlowExecutionState.COMPLETED,
                List.of(), null, StubEngineGateway.NOW, StubEngineGateway.NOW);
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("tx-stored", outcome.getTransactionHash(),
                    "hash must be recovered from the engine store snapshot");
            assertEquals(List.of(BindingOutcome.MATCHED), store.outcomes,
                    "stored-snapshot re-attach must classify MATCHED");
        }
    }

    @Test
    void gatewayStartThrowingSynchronouslyFailsItemTypedAndKeepsStreamHealthy() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        gateway.startFailure = new IllegalStateException("engine down");
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_DISPATCH_FAILED",
                    assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            assertEquals(List.of(BindingOutcome.REJECTED), store.outcomes);
            assertTrue(stream.isHealthy(), "a per-item dispatch failure must not poison the stream");

            gateway.startFailure = null;
            TxStreamReceipt next = stream.submit(planItem("pay-2"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-2");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    next.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void handleCompletingExceptionallyFailsItemTyped() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.lastHandle().future.completeExceptionally(
                    new IllegalStateException("execution machinery failed"));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_EXECUTION_FAILED",
                    assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            assertTrue(stream.isHealthy());

            TxStreamReceipt next = stream.submit(planItem("pay-2"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-2");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    next.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void registerItemRuntimeFailureThrowsOnSubmitAndRejectsOnTrySubmit() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        store.registerFailure = new IllegalStateException("registry storage down");
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();

            TxStreamException thrown = assertThrows(TxStreamException.class,
                    () -> stream.submit(planItem("pay-1")));
            assertEquals("TXSTREAM_REGISTRATION_FAILED", thrown.getCode());

            // BUG-4: trySubmit never throws for registration outcomes.
            EmitResult emit = stream.trySubmit(planItem("pay-1"));
            assertEquals(EmitResult.Status.REJECTED, emit.getStatus());
            assertNotNull(emit.getRejection());
            assertEquals("TXSTREAM_REGISTRATION_FAILED", emit.getRejection().getCode());
            assertTrue(stream.isHealthy());

            store.registerFailure = null;
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void confirmBindingFailureIsIsolatedAndItemSettlesFromEngineOutcome() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        store.confirmFailure = new IllegalStateException("confirm write failed");
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("tx-1", outcome.getTransactionHash());
            assertTrue(stream.isHealthy());
            assertEquals(List.of(BindingOutcome.CREATED), store.outcomes,
                    "the confirm attempt itself must have been made");
        }
    }

    // ------------------------------------------------------------------
    // BUG-3: non-portable redelivery
    // ------------------------------------------------------------------

    @Test
    void identicalNonPortableRedeliveryAttachesToSettledFailedReceipt() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(nonPortableItem("bad-item", "v1"));
            assertEquals(TxStreamItemStatus.FAILED,
                    first.completion().toCompletableFuture().join().getStatus());

            TxStreamReceipt redelivered = stream.submit(nonPortableItem("bad-item", "v1"));
            assertSame(first, redelivered,
                    "identical redelivery of a validation-failed item must attach");

            EmitResult emit = stream.trySubmit(nonPortableItem("bad-item", "v1"));
            assertEquals(EmitResult.Status.DUPLICATE_ATTACHED, emit.getStatus());
            assertSame(first, emit.getReceipt());
            assertTrue(gateway.started.isEmpty());
        }
    }

    @Test
    void differentNonPortableContentConflictsWithAccurateMessage() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            stream.submit(nonPortableItem("bad-item", "v1"));

            TxStreamDuplicateItemException conflict = assertThrows(
                    TxStreamDuplicateItemException.class,
                    () -> stream.submit(nonPortableItem("bad-item", "v2")));
            assertEquals("bad-item", conflict.getItemId());
            assertTrue(conflict.getMessage().contains("settled as FAILED"),
                    "message must state the actual situation: " + conflict.getMessage());
            assertTrue(conflict.getMessage().contains("different content"));
        }
    }

    // ------------------------------------------------------------------
    // BUG-C: the post-start seam must be total
    // ------------------------------------------------------------------

    @Test
    void completionObserverRegistrationFailurePostStartSettlesRecoveryRequiredAndStopsDispatch() {
        StubEngineGateway inner = new StubEngineGateway();
        EngineGateway unobservable = new DelegatingGateway(inner) {
            @Override
            public ExecutionHandle start(FlowExecutionRequest request) {
                return new DelegatingHandle(inner.start(request)) {
                    @Override
                    public CompletionStage<FlowExecutionResult> completion() {
                        throw new IllegalStateException("completion channel broken");
                    }
                };
            }
        };
        ManualExecutor executor = new ManualExecutor();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", unobservable)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(executor)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt started = stream.submit(planItem("pay-1"));
            TxStreamReceipt pending = stream.submit(planItem("pay-2"));
            executor.runAll();

            // The execution started but can never be observed: honest answer
            // is RECOVERY_REQUIRED with the execution id attached — never a
            // FAILED that denies a possibly landing transaction.
            TxStreamItemResult outcome = started.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, outcome.getStatus());
            TxStreamException error = assertInstanceOf(TxStreamException.class,
                    outcome.getError());
            assertEquals("TXSTREAM_EXECUTION_UNOBSERVABLE", error.getCode());
            assertEquals(started.executionId().orElseThrow(), outcome.getExecutionId(),
                    "the execution id must be attached for operator reconcile/recover");

            assertFalse(stream.isHealthy(),
                    "an unobservable-execution gateway is a broken stream");
            assertEquals(1, stream.getStats().inFlightCount(),
                    "the lane must stay busy: the execution still occupies its identity");

            TxStreamItemResult drained = pending.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, drained.getStatus());
            assertEquals("TXSTREAM_UNHEALTHY", assertInstanceOf(TxStreamException.class,
                    drained.getError()).getCode());
            stream.drain();   // both items settled: drain returns despite the busy lane
        }
    }

    @Test
    void throwingPendingCancelSignalIsIsolatedAndItemStillSettlesFromEngine() {
        StubEngineGateway inner = new StubEngineGateway();
        EngineGateway brokenCancel = new DelegatingGateway(inner) {
            @Override
            public ExecutionHandle start(FlowExecutionRequest request) {
                return new DelegatingHandle(inner.start(request)) {
                    @Override
                    public void requestCancel(String reason) {
                        throw new IllegalStateException("cancel channel broken");
                    }
                };
            }
        };
        ManualExecutor executor = new ManualExecutor();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", brokenCancel)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(executor)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            executor.runNext();     // pump claims pay-1; its dispatch task is pending
            assertTrue(stream.cancel("pay-1", "too late"),
                    "a claimed item records a pending cancel");
            executor.runAll();      // dispatch starts, then the cancel forwarding throws

            assertTrue(stream.isHealthy(),
                    "a throwing cancel signal is isolated and must not poison the stream");
            assertFalse(receipt.completion().toCompletableFuture().isDone(),
                    "the running item must not be failed by the cancel-forwarding failure");
            inner.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus(),
                    "the item settles from engine truth, not from the broken signal");
        }
    }

    // ------------------------------------------------------------------
    // Invalid item content: typed, never a raw IllegalArgumentException
    // ------------------------------------------------------------------

    @Test
    void invalidIdempotencyKeyIsTypedInvalidItemOnSubmitAndRejectedOnTrySubmit() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            String oversizedKey = "k".repeat(600);   // exceeds MAX_IDEMPOTENCY_KEY_BYTES
            TxWorkItem invalid = TxWorkItem.builder("pay-1")
                    .withTxPlan(TxPlan.from(new Tx()
                            .payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER)))
                    .withIdempotencyKey(oversizedKey)
                    .build();

            TxStreamException thrown = assertThrows(TxStreamException.class,
                    () -> stream.submit(invalid));
            assertEquals("TXSTREAM_INVALID_ITEM", thrown.getCode());

            EmitResult emit = stream.trySubmit(invalid);
            assertEquals(EmitResult.Status.REJECTED, emit.getStatus());
            assertEquals("TXSTREAM_INVALID_ITEM", emit.getRejection().getCode());

            assertTrue(store.calls.isEmpty(), "an invalid item is never registered");
            assertTrue(gateway.started.isEmpty());
            assertTrue(stream.isHealthy());
            assertTrue(stream.getItemStatus("pay-1").isEmpty(), "nothing is retained");

            // A null item stays a programming error, not a content outcome.
            assertThrows(NullPointerException.class, () -> stream.submit(null));

            TxStreamReceipt valid = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    valid.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // BUG-1: item published to the buffer while the dispatcher dies
    // ------------------------------------------------------------------

    @Test
    void itemBufferedDuringSystemicFailureIsFailedTypedNotStranded() {
        TogglingExecutor executor = new TogglingExecutor();
        StubEngineGateway gateway = new StubEngineGateway();
        // Between accept's isAccepting() re-check and the buffer publish the
        // stream calls onItemAccepted. This saboteur uses that window to kill
        // the dispatcher deterministically: it flips the executor to rejecting
        // and completes the in-flight execution, whose completion callback
        // re-schedules the pump and hits the rejection -> onSystemicFailure
        // drains a buffer that does not contain the new item yet.
        TxStreamEventListener saboteur = new TxStreamEventListener() {
            @Override
            public void onItemAccepted(TxWorkItem item, TxStreamReceipt receipt) {
                if ("pay-2".equals(item.getItemId())) {
                    executor.reject = true;
                    gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
                }
            }
        };
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(executor)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .eventListener(saboteur)
                .build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));

            assertFalse(stream.isHealthy());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
            TxStreamItemResult stranded = second.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, stranded.getStatus());
            assertEquals("TXSTREAM_UNHEALTHY",
                    assertInstanceOf(TxStreamException.class, stranded.getError()).getCode());
            assertEquals(0, stream.getStats().pendingBufferSize(),
                    "the item must not be stranded in the buffer");
            stream.drain();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static final class TogglingExecutor implements Executor {
        volatile boolean reject;

        @Override
        public void execute(Runnable command) {
            if (reject) {
                throw new RejectedExecutionException("executor rejecting");
            }
            command.run();
        }
    }

    /** Delegates to a stub gateway; tests override {@code start} to decorate handles. */
    abstract static class DelegatingGateway implements EngineGateway {
        private final StubEngineGateway delegate;

        DelegatingGateway(StubEngineGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<FlowExecutionSnapshot> executionSnapshot(String executionId) {
            return delegate.executionSnapshot(executionId);
        }

        @Override
        public Optional<ExecutionEventView> executionEvents(String executionId,
                                                            long afterSequence, int limit) {
            return delegate.executionEvents(executionId, afterSequence, limit);
        }
    }

    /** Pass-through handle; tests override single methods to inject faults. */
    static class DelegatingHandle implements EngineGateway.ExecutionHandle {
        private final EngineGateway.ExecutionHandle delegate;

        DelegatingHandle(EngineGateway.ExecutionHandle delegate) {
            this.delegate = delegate;
        }

        @Override
        public String executionId() {
            return delegate.executionId();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public FlowExecutionResult resultIfDone() {
            return delegate.resultIfDone();
        }

        @Override
        public CompletionStage<FlowExecutionResult> completion() {
            return delegate.completion();
        }

        @Override
        public List<FlowEvent> events() {
            return delegate.events();
        }

        @Override
        public List<FlowEvent> eventsAfter(long sequence) {
            return delegate.eventsAfter(sequence);
        }

        @Override
        public void requestCancel(String reason) {
            delegate.requestCancel(reason);
        }
    }

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

    private TxWorkItem nonPortableItem(String itemId, String marker) {
        return TxWorkItem.builder(itemId)
                .withFlowStep(FlowStep.builder("factory-step")
                        .withTxContext(quickTxBuilder -> null)
                        .build())
                .addMetadata("marker", marker)
                .build();
    }
}
