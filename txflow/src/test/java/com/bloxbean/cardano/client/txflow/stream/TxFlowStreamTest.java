package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.exec.FlowError;
import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxFlowStreamTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    // ------------------------------------------------------------------
    // Happy path and projection order
    // ------------------------------------------------------------------

    @Test
    void submitProjectsPlannedSubmittedConfirmedWithHash() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingListener listener = new RecordingListener();
        try (TxFlowStream stream = builder("payouts", gateway).eventListener(listener).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));

            assertTrue(receipt.executionId().isPresent());
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-hash-1");

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("tx-hash-1", outcome.getTransactionHash());
            assertEquals("payouts-lane", outcome.getLaneName());
            assertNull(outcome.getError());
            assertEquals(List.of(TxStreamItemStatus.ACCEPTED, TxStreamItemStatus.PLANNED,
                            TxStreamItemStatus.SUBMITTED, TxStreamItemStatus.CONFIRMED),
                    listener.statuses("pay-1"));
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus());
        }
    }

    @Test
    void engineRequestCarriesClaimAndLaneSpendingResource() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(
                    TxWorkItem.builder("pay-9").withTxPlan(plan()).withIdempotencyKey("order-9").build());

            var request = gateway.started.get(0);
            assertEquals("stream:payouts", request.getIdempotencyNamespace());
            assertEquals("order-9", request.getIdempotencyKey());
            assertEquals(receipt.executionId().orElseThrow(), request.getExecutionId());
            assertEquals(List.of("addr:" + SENDER), List.copyOf(request.getSpendingResources()));
            assertEquals(STEP_ID, request.getDefinition().getSteps().get(0).getId());
            assertTrue(request.getDefinition().getId().startsWith("flow-"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    @Test
    void executionIdentityIsDeterministicAcrossStreamInstances() {
        StubEngineGateway first = new StubEngineGateway();
        StubEngineGateway second = new StubEngineGateway();
        String firstExecution;
        String firstFlow;
        try (TxFlowStream stream = builder("payouts", first).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            firstExecution = first.started.get(0).getExecutionId();
            firstFlow = first.started.get(0).getDefinition().getId();
            first.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
        try (TxFlowStream stream = builder("payouts", second).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            assertEquals(firstExecution, second.started.get(0).getExecutionId());
            assertEquals(firstFlow, second.started.get(0).getDefinition().getId());
            second.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    // ------------------------------------------------------------------
    // Live redelivery
    // ------------------------------------------------------------------

    @Test
    void identicalRedeliveryAttachesToExistingReceiptWithoutSecondDispatch() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt original = stream.submit(planItem("pay-1"));
            TxStreamReceipt redelivered = stream.submit(planItem("pay-1"));
            assertSame(original, redelivered);

            EmitResult emit = stream.trySubmit(planItem("pay-1"));
            assertEquals(EmitResult.Status.DUPLICATE_ATTACHED, emit.getStatus());
            assertSame(original, emit.getReceipt());
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    @Test
    void differentContentForSameItemIdIsTypedConflict() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));

            TxWorkItem different = TxWorkItem.builder("pay-1")
                    .withTxPlan(TxPlan.from(new Tx()
                            .payToAddress(RECEIVER, Amount.ada(99)).from(SENDER)))
                    .build();
            TxStreamDuplicateItemException conflict = assertThrows(
                    TxStreamDuplicateItemException.class, () -> stream.submit(different));
            assertEquals("pay-1", conflict.getItemId());
            assertEquals("TXSTREAM_DUPLICATE_ITEM", conflict.getCode());

            EmitResult emit = stream.trySubmit(different);
            assertEquals(EmitResult.Status.CONFLICT, emit.getStatus());
            assertNotNull(emit.getConflict());
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    @Test
    void portableFlowStepKeepsCallerStepIdThroughBindingAndProjection() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            FlowStep step = FlowStep.builder("caller-step").withTxPlan(plan()).build();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromFlowStep("step-item", step));

            assertEquals("caller-step",
                    gateway.started.get(0).getDefinition().getSteps().get(0).getId());
            assertEquals("caller-step", store.bindings.get("step-item").stepId());
            assertEquals(StreamIdentities.executionId(
                            StreamIdentities.namespace("payouts"), "step-item"),
                    receipt.executionId().orElseThrow(),
                    "the execution id stays claim-derived, never step-derived");

            gateway.lastHandle().completeConfirmed("caller-step", "tx-step");
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("caller-step", outcome.getStepId());
            assertEquals("tx-step", outcome.getTransactionHash());
        }
    }

    @Test
    void metadataOnlyDifferenceIsTypedConflict() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            stream.submit(TxWorkItem.builder("pay-1").withTxPlan(plan())
                    .addMetadata("purpose", "invoice-a").build());

            TxWorkItem differentMetadata = TxWorkItem.builder("pay-1").withTxPlan(plan())
                    .addMetadata("purpose", "invoice-b").build();
            assertThrows(TxStreamDuplicateItemException.class,
                    () -> stream.submit(differentMetadata));
            assertEquals(EmitResult.Status.CONFLICT,
                    stream.trySubmit(differentMetadata).getStatus());
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    // ------------------------------------------------------------------
    // Live SUBMITTED read-through
    // ------------------------------------------------------------------

    @Test
    void liveSubmittedReadThroughProjectsSubmittedWithHashWhileInFlight() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            assertEquals(TxStreamItemStatus.PLANNED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus());

            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(STEP_ID, "tx-live");
            TxStreamItemResult live = stream.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.SUBMITTED, live.getStatus(),
                    "read-through must observe the live submission event");
            assertEquals("tx-live", live.getTransactionHash());
            assertFalse(receipt.completion().toCompletableFuture().isDone(),
                    "SUBMITTED is not a settling status");

            handle.completeConfirmed(STEP_ID, "tx-live");
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("tx-live", outcome.getTransactionHash());
            assertEquals(1, stream.getStats().submittedItemCount(),
                    "the read-through and terminal pass must not double-count");
        }
    }

    // ------------------------------------------------------------------
    // Eager portability validation
    // ------------------------------------------------------------------

    @Test
    void javaFactoryPayloadFailsTypedAtSubmitAndIsNeverRegistered() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            TxWorkItem nonPortable = TxWorkItem.fromFlowStep("factory-item",
                    FlowStep.builder("factory-step")
                            .withTxContext(quickTxBuilder -> null)
                            .build());

            TxStreamReceipt receipt = stream.submit(nonPortable);
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            TxStreamException error = assertInstanceOf(TxStreamException.class, outcome.getError());
            assertEquals("TXSTREAM_NON_PORTABLE_ITEM", error.getCode());
            assertTrue(error.getMessage().contains("TXFLOW_NON_PORTABLE_FACTORY"));

            assertTrue(store.calls.isEmpty(), "nothing may be registered: " + store.calls);
            assertTrue(gateway.started.isEmpty());
            assertTrue(receipt.executionId().isEmpty());
            stream.drain();
        }
    }

    // ------------------------------------------------------------------
    // Two-phase binding
    // ------------------------------------------------------------------

    @Test
    void bindingIsWrittenBeforeStartAndConfirmedCreatedAfter() {
        List<String> callLog = new CopyOnWriteArrayList<>();
        StubEngineGateway gateway = new StubEngineGateway(callLog);
        RecordingStateStore store = new RecordingStateStore(callLog);
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");

            String executionId = receipt.executionId().orElseThrow();
            assertEquals(List.of(
                    "register:pay-1",
                    "bind:pay-1",
                    "start:" + executionId,
                    "confirm:pay-1:CREATED"), callLog);
            assertEquals(executionId, store.bindings.get("pay-1").executionId());
        }
    }

    @Test
    void failClosedBindingFailureFailsItemTypedWithoutEngineStart() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        store.bindFailure = new IllegalStateException("binding storage down");
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_BINDING_FAILED",
                    assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            assertTrue(gateway.started.isEmpty(), "engine must not be invoked without a binding");
            assertTrue(stream.isHealthy(), "a per-item failure must not poison the stream");

            store.bindFailure = null;
            TxStreamReceipt next = stream.submit(planItem("pay-2"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-2");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    next.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Terminal precedence and honest projection
    // ------------------------------------------------------------------

    @Test
    void inProgressStepInsideTerminalFlowBecomesRecoveryRequiredWithHash() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(STEP_ID, "tx-uncertain");
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.submissionPendingAt(STEP_ID, "tx-uncertain",
                            List.of(), List.of(),
                            new IllegalStateException("confirmation abandoned"),
                            StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_EXECUTION_FAILED", FlowErrorCategory.CONFIRMATION,
                            "flow failed", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, outcome.getStatus());
            assertEquals("tx-uncertain", outcome.getTransactionHash());
            assertNotNull(outcome.getError());
        }
    }

    @Test
    void failedFlowRetainsSubmittedHashOnFailedItem() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(STEP_ID, "tx-failed");
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.failureAfterSubmissionAt(STEP_ID, "tx-failed",
                            List.of(), List.of(), new IllegalStateException("rejected"),
                            StubEngineGateway.NOW)),
                    null, StubEngineGateway.NOW, StubEngineGateway.NOW));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("tx-failed", outcome.getTransactionHash());
        }
    }

    @Test
    void cancelledFlowProjectsCancelledItem() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.CANCELLED,
                    List.of(FlowStepResult.cancelledAt(STEP_ID,
                            new IllegalStateException("operator cancelled"),
                            StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_CANCELLED", FlowErrorCategory.CANCELLATION,
                            "Execution cancelled", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            assertEquals(TxStreamItemStatus.CANCELLED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Cancellation
    // ------------------------------------------------------------------

    @Test
    void cancellingBufferedItemCancelsImmediatelyWithoutDispatch() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt active = stream.submit(planItem("pay-1"));
            TxStreamReceipt buffered = stream.submit(planItem("pay-2"));

            assertTrue(stream.cancel("pay-2", "no longer needed"));
            TxStreamItemResult outcome = buffered.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CANCELLED, outcome.getStatus());
            assertEquals("no longer needed",
                    assertInstanceOf(TxStreamException.class, outcome.getError()).getMessage());

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    active.completion().toCompletableFuture().join().getStatus());
            assertEquals(1, gateway.started.size(), "cancelled item must never dispatch");
        }
    }

    @Test
    void cancellingInFlightItemSignalsCooperativeEngineCancel() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));

            assertTrue(stream.cancel("pay-1", "abort requested"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            assertTrue(handle.cancelRequested.get());
            assertEquals("abort requested", handle.cancelReason);

            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.CANCELLED, List.of(),
                    new FlowError("TXFLOW_CANCELLED", FlowErrorCategory.CANCELLATION,
                            "Execution cancelled", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.CANCELLED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertFalse(stream.cancel("pay-1", "again"), "settled items cannot be cancelled");
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle: drain, close, buffer bounds
    // ------------------------------------------------------------------

    @Test
    void drainAwaitsEveryAcceptedPromiseIncludingValidationFailures() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingListener listener = new RecordingListener();
        try (TxFlowStream stream = builder("payouts", gateway).eventListener(listener).build()) {
            stream.start();
            TxStreamReceipt pending = stream.submit(planItem("pay-1"));
            stream.submit(TxWorkItem.fromFlowStep("factory-item",
                    FlowStep.builder("factory-step").withTxContext(quickTxBuilder -> null).build()));

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            stream.drain();

            assertTrue(pending.completion().toCompletableFuture().isDone());
            assertTrue(listener.drained.contains("payouts"));
            assertEquals(EmitResult.Status.CLOSED, stream.trySubmit(planItem("pay-3")).getStatus());
            TxStreamException closedFailure = assertThrows(TxStreamException.class,
                    () -> stream.submit(planItem("pay-4")));
            assertEquals("TXSTREAM_CLOSED", closedFailure.getCode());
        }
    }

    @Test
    void awaitDrainTimeoutThrowsTypedTimeout() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));

            assertThrows(TxStreamTimeoutException.class,
                    () -> stream.awaitDrain(Duration.ofMillis(5)));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void trySubmitReportsFullWhenBufferBoundReached() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).maxBufferSize(1).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));           // in flight, buffer freed
            stream.submit(planItem("pay-2"));           // occupies the single buffer slot
            assertEquals(EmitResult.Status.FULL, stream.trySubmit(planItem("pay-3")).getStatus());

            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            stream.drain();
        }
    }

    @Test
    void throwingListenerNeverBreaksDispatchOrDrain() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamEventListener hostile = new TxStreamEventListener() {
            @Override public void onItemAccepted(TxWorkItem item, TxStreamReceipt receipt) {
                throw new IllegalStateException("listener boom");
            }
            @Override public void onItemUpdated(TxStreamItemResult result) {
                throw new IllegalStateException("listener boom");
            }
            @Override public void onStreamStarted(String streamId) {
                throw new IllegalStateException("listener boom");
            }
            @Override public void onStreamDrained(String streamId) {
                throw new IllegalStateException("listener boom");
            }
            @Override public void onStreamClosed(String streamId) {
                throw new IllegalStateException("listener boom");
            }
        };
        try (TxFlowStream stream = builder("payouts", gateway).eventListener(hostile).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");

            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            stream.drain();
            assertTrue(stream.isHealthy());
        }
    }

    @Test
    void statsAreConsistentWithProjections() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt confirmed = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            TxStreamReceipt uncertain = stream.submit(planItem("pay-2"));
            stream.submit(planItem("pay-3"));
            assertTrue(stream.cancel("pay-3", "cancelled while buffered"));
            StubEngineGateway.StubHandle second = gateway.lastHandle();
            second.submittedEvent(STEP_ID, "tx-2");
            second.complete(new FlowExecutionResult(second.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.submissionPendingAt(STEP_ID, "tx-2", List.of(),
                            List.of(), new IllegalStateException("abandoned"),
                            StubEngineGateway.NOW)),
                    null, StubEngineGateway.NOW, StubEngineGateway.NOW));

            TxStreamStats stats = stream.getStats();
            assertEquals(3, stats.acceptedItemCount());
            assertEquals(2, stats.plannedItemCount());
            assertEquals(2, stats.submittedItemCount());
            assertEquals(1, stats.confirmedItemCount());
            assertEquals(0, stats.failedItemCount());
            assertEquals(1, stats.cancelledItemCount());
            assertEquals(1, stats.recoveryRequiredItemCount());
            assertEquals(0, stats.pendingBufferSize());
            assertEquals(0, stats.inFlightCount());
            assertEquals(TxStreamItemStatus.CONFIRMED, confirmed.current().getStatus());
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, uncertain.current().getStatus());
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
        return TxWorkItem.fromTxPlan(itemId, plan());
    }

    private TxPlan plan() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER));
    }

    static final class RecordingListener implements TxStreamEventListener {
        final List<TxStreamItemResult> updates = new CopyOnWriteArrayList<>();
        final List<String> drained = new CopyOnWriteArrayList<>();

        @Override
        public void onItemUpdated(TxStreamItemResult result) {
            updates.add(result);
        }

        @Override
        public void onStreamDrained(String streamId) {
            drained.add(streamId);
        }

        List<TxStreamItemStatus> statuses(String itemId) {
            return updates.stream()
                    .filter(result -> result.getItemId().equals(itemId))
                    .map(TxStreamItemResult::getStatus)
                    .toList();
        }
    }
}
