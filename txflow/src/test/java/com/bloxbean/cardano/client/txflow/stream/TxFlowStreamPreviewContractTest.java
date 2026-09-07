package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class TxFlowStreamPreviewContractTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";

    @Test
    void nextLaneExecutionWaitsForBackendIndexingWithoutResubmittingPreviousPayment() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        gateway.maintenanceScheduler = scheduler;
        AtomicInteger checks = new AtomicInteger();
        gateway.outputVisibility = hash -> {
            assertEquals("previous-hash", hash);
            assertEquals(1, gateway.started.size());
            return checks.incrementAndGet() >= 3;
        };
        TxFlowStream stream = new TxFlowStream.Builder("visibility", gateway)
                .executor(Runnable::run)
                .backendVisibility(Duration.ofSeconds(1), Duration.ofMillis(1))
                .open();
        try {
            stream.submit("first", TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER)));
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "previous-hash");
            stream.submit("next", TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER)));
            assertEquals(1, checks.get());
            scheduler.pending().fire();
            scheduler.pending().fire();
            assertEquals(3, checks.get());
            assertEquals(2, gateway.started.size());
        } finally {
            stream.abort("test cleanup");
        }
    }

    @Test
    void backendTimeoutFailsOnlyNewWorkBeforeEngineStartAndRemembersPendingIndexing() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.outputVisibility = hash -> false;
        TxFlowStream stream = new TxFlowStream.Builder("visibility", gateway)
                .executor(Runnable::run).backendVisibility(Duration.ofMillis(5), Duration.ofMillis(1))
                .open();
        try {
            TxPlan plan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER));
            TxStreamReceipt previous = stream.submit("first", plan);
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "previous-hash");
            for (String id : new String[] {"next", "another"}) {
                TxStreamItemResult result = stream.submit(id, plan).awaitSettled(Duration.ofSeconds(1));
                assertEquals(TxStreamItemStatus.FAILED, result.getStatus());
                assertEquals(TxStreamCodes.BACKEND_NOT_READY,
                        ((TxStreamException) result.getError()).getCode());
            }
            assertEquals(TxStreamItemStatus.CONFIRMED, previous.current().getStatus());
            assertEquals(1, gateway.started.size());
            gateway.outputVisibility = hash -> true;
            stream.submit("after-indexing", plan);
            assertEquals(2, gateway.started.size());
        } finally {
            stream.abort("test cleanup");
        }
    }

    @Test
    void uncertainLaneDoesNotHoldFlightSlotAndRepairWakesItBeforeTimer() {
        for (FlowExecutionState terminal : List.of(FlowExecutionState.FAILED, FlowExecutionState.CANCELLED)) {
            StubEngineGateway gateway = new StubEngineGateway();
            gateway.outputVisibility = hash -> false;
            ManualScheduler scheduler = new ManualScheduler();
            TxFlowStream stream = new TxFlowStream.Builder("repair", gateway)
                    .executor(Runnable::run).maintenanceExecutor(scheduler).maxInFlight(1)
                    .backendVisibility(Duration.ofSeconds(60), Duration.ofSeconds(30)).open();
            try {
                TxPlan plan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER));
                TxStreamReceipt first = stream.submit("first", plan);
                StubEngineGateway.StubHandle handle = gateway.lastHandle();
                handle.complete(new FlowExecutionResult(handle.executionId(), "fp", FlowExecutionState.FAILED,
                        List.of(FlowStepResult.submissionPendingAt(StreamIdentities.GENERATED_STEP_ID,
                                "uncertain-hash", List.of(), List.of(), new IllegalStateException("uncertain"),
                                StubEngineGateway.NOW)), null, StubEngineGateway.NOW, StubEngineGateway.NOW));
                stream.submit("waiting", plan);
                ManualScheduler.ScheduledTask retry = scheduler.pending();
                assertEquals(1, gateway.started.size());
                stream.submit("other-wallet", TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2))
                        .from("addr_test1vpqother")));
                assertEquals(2, gateway.started.size(), "a visibility wait must release the flight slot");
                gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "other-hash");
                gateway.putSnapshot(first.executionId().orElseThrow(), terminal);
                stream.reconcile("first");
                assertEquals(3, gateway.started.size(), "repair must wake the lane without waiting for the timer");
                assertTrue(retry.isCancelled(), "repair must release the scheduled task");
                retry.fire();
                assertEquals(3, gateway.started.size(), "stale timer must not redispatch");
            } finally {
                stream.abort("test cleanup");
            }
        }
    }

    @Test
    void abortCancelsPendingVisibilityTimer() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.outputVisibility = hash -> false;
        ManualScheduler scheduler = new ManualScheduler();
        TxFlowStream stream = new TxFlowStream.Builder("abort-visibility", gateway)
                .executor(Runnable::run).maintenanceExecutor(scheduler).open();
        try {
            TxPlan plan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER));
            stream.submit("first", plan);
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "hash");
            TxStreamReceipt waiting = stream.submit("waiting", plan);
            ManualScheduler.ScheduledTask retry = scheduler.pending();
            stream.abort("stop");
            assertTrue(retry.isCancelled());
            retry.fire();
            assertEquals(1, gateway.started.size());
            assertEquals(TxStreamItemStatus.CANCELLED, waiting.awaitSettled(Duration.ofSeconds(1)).getStatus());
        } finally {
            stream.abort("test cleanup");
        }
    }

    @Test
    void plainCloseKeepsParkedLaneRetryAliveUntilDrainCompletes() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.outputVisibility = hash -> false;
        ManualScheduler scheduler = new ManualScheduler();
        TxFlowStream stream = new TxFlowStream.Builder("close-visibility", gateway)
                .executor(Runnable::run).maintenanceExecutor(scheduler).open();
        CompletableFuture<Void> closed = new CompletableFuture<>();
        Thread closer = new Thread(() -> {
            try {
                stream.close();
                closed.complete(null);
            } catch (Throwable failure) {
                closed.completeExceptionally(failure);
            }
        });
        try {
            TxPlan plan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER));
            stream.submit("first", plan);
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "first-hash");
            TxStreamReceipt waiting = stream.submit("waiting", plan);
            ManualScheduler.ScheduledTask retry = scheduler.pending();
            gateway.outputVisibility = hash -> true;
            closer.start();
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                while (closer.getState() != Thread.State.WAITING) Thread.sleep(1);
            });
            assertFalse(retry.isCancelled(), "close must preserve the wakeup while draining");
            retry.fire();
            assertEquals(2, gateway.started.size());
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "second-hash");
            closed.get(2, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CONFIRMED, waiting.current().getStatus());
        } finally {
            stream.abort("test cleanup");
            closer.join(2000);
        }
    }

    @Test
    void interruptedCloseCancelsParkedWorkAndTimersWithoutDuplicateCloseEvent() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.outputVisibility = hash -> false;
        ManualScheduler scheduler = new ManualScheduler();
        AtomicInteger closeEvents = new AtomicInteger();
        TxFlowStream stream = new TxFlowStream.Builder("interrupted-close", gateway)
                .executor(Runnable::run).maintenanceExecutor(scheduler)
                .eventListener(new TxStreamEventListener() {
                    @Override
                    public void onStreamClosed(String streamId) { closeEvents.incrementAndGet(); }
                }).open();
        CompletableFuture<Throwable> closeFailure = new CompletableFuture<>();
        AtomicBoolean interruptedFlag = new AtomicBoolean();
        Thread closer = new Thread(() -> {
            try {
                stream.close();
                closeFailure.complete(null);
            } catch (Throwable failure) {
                interruptedFlag.set(Thread.currentThread().isInterrupted());
                closeFailure.complete(failure);
            }
        });
        try {
            TxPlan plan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER));
            stream.submit("first", plan);
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "first-hash");
            TxStreamReceipt waiting = stream.submit("waiting", plan);
            ManualScheduler.ScheduledTask retry = scheduler.pending();
            closer.start();
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                while (closer.getState() != Thread.State.WAITING) Thread.sleep(1);
            });
            closer.interrupt();
            assertEquals("TXSTREAM_INTERRUPTED", ((TxStreamException) closeFailure.get(2, TimeUnit.SECONDS)).getCode());
            assertTrue(interruptedFlag.get());
            assertTrue(retry.isCancelled());
            assertEquals(TxStreamItemStatus.CANCELLED, waiting.awaitSettled(Duration.ofSeconds(1)).getStatus());
            assertEquals(0, stream.getStats().pendingBufferSize());
            assertEquals(1, closeEvents.get());
            retry.fire();
            assertEquals(1, gateway.started.size());
        } finally {
            stream.abort("test cleanup");
            closer.join(2000);
        }
    }

    @Test
    void parkedWorkRetainsBufferCapacityUntilDispatchOrCancellation() {
        for (boolean cancel : new boolean[] {false, true}) {
            StubEngineGateway gateway = new StubEngineGateway();
            gateway.outputVisibility = hash -> false;
            ManualScheduler scheduler = new ManualScheduler();
            TxFlowStream stream = new TxFlowStream.Builder("buffer-visibility", gateway)
                    .executor(Runnable::run).maintenanceExecutor(scheduler).maxBufferSize(1).open();
            try {
                TxPlan plan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER));
                stream.submit("first", plan);
                gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "first-hash");
                stream.submit("waiting", plan);
                assertEquals(1, stream.getStats().pendingBufferSize());
                assertEquals(0, stream.getStats().inFlightCount());
                assertEquals(EmitResult.Status.FULL, stream.trySubmit("overflow", plan).getStatus());
                if (cancel) {
                    assertTrue(stream.cancel("waiting", "cancel before dispatch"));
                } else {
                    gateway.outputVisibility = hash -> true;
                    scheduler.pending().fire();
                    assertEquals(2, gateway.started.size());
                }
                assertEquals(0, stream.getStats().pendingBufferSize());
            } finally {
                stream.abort("test cleanup");
            }
        }
    }

    @Test
    void abortDuringVisibilityProbeReportsAbortedBeforeEngineStart() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxFlowStream stream = new TxFlowStream.Builder("probe-abort", gateway).executor(Runnable::run).open();
        try {
            TxPlan plan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER));
            stream.submit("first", plan);
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "first-hash");
            gateway.outputVisibility = hash -> {
                stream.abort("abort during probe");
                return true;
            };
            TxStreamItemResult result = stream.submit("waiting", plan).awaitSettled(Duration.ofSeconds(1));
            assertEquals(TxStreamItemStatus.CANCELLED, result.getStatus());
            assertEquals("TXSTREAM_ABORTED", ((TxStreamException) result.getError()).getCode());
            assertEquals(1, gateway.started.size());
            assertEquals(0, stream.getStats().pendingBufferSize());
        } finally {
            stream.abort("test cleanup");
        }
    }

    @Test
    void callerMutationDoesNotChangeAcceptedPlanOrRedeliveryFingerprint() {
        for (boolean stepPayload : new boolean[] {false, true}) {
            StubEngineGateway gateway = new StubEngineGateway();
            TxFlowStream stream = new TxFlowStream.Builder("snapshot", gateway)
                    .executor(Runnable::run).window(WindowPolicy.count(2)).open();
            try {
                Tx tx = new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER);
                TxPlan plan = TxPlan.from(tx);
                String acceptedYaml = plan.toYaml();
                TxWorkItem item = item(stepPayload, plan);
                TxStreamReceipt receipt = stream.submit(item);
                tx.payToAddress(RECEIVER, Amount.ada(3));
                stream.flush();
                TxPlan dispatched = gateway.started.get(0).getDefinition()
                        .getSteps().get(0).getTxPlan();
                assertEquals(acceptedYaml, dispatched.toYaml());
                assertEquals(EmitResult.Status.CONFLICT, stream.trySubmit(item).getStatus());
                assertEquals(receipt, stream.submit(item(stepPayload, TxPlan.from(acceptedYaml))));
            } finally {
                stream.abort("test cleanup");
            }
        }
    }

    @Test
    void resolutionReturnsStoredTerminalOutcomesWithoutLiveStateOrResubmission() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.durable = true;
        TxStreamStateStore store = TxStreamStateStore.inMemoryDurable();
        try (TxFlowStream stream = new TxFlowStream.Builder("stored", gateway)
                .executor(Runnable::run).stateStore(store).open()) {
            for (TxStreamItemStatus status : new TxStreamItemStatus[] {
                    TxStreamItemStatus.CONFIRMED, TxStreamItemStatus.FAILED,
                    TxStreamItemStatus.CANCELLED, TxStreamItemStatus.RECOVERY_REQUIRED}) {
                store.projectItem(TxStreamItemResult.builder("stored", status.name(), status)
                        .transactionHash("known-hash").updatedAt(StubEngineGateway.NOW).build(), 1);
            }
            assertEquals(TxStreamItemStatus.CONFIRMED, stream.awaitResolution("CONFIRMED",
                    Duration.ofSeconds(1), Duration.ofMillis(1)).getStatus());
            assertThrows(TxStreamFailedException.class, () -> stream.awaitResolution("FAILED",
                    Duration.ofSeconds(1), Duration.ofMillis(1)));
            assertThrows(TxStreamCancelledException.class, () -> stream.awaitResolution("CANCELLED",
                    Duration.ofSeconds(1), Duration.ofMillis(1)));
            assertThrows(TxStreamTimeoutException.class, () -> stream.awaitResolution("RECOVERY_REQUIRED",
                    Duration.ofMillis(5), Duration.ofMillis(1)));
            assertEquals(0, gateway.started.size());
        }
    }

    private TxWorkItem item(boolean stepPayload, TxPlan plan) {
        return stepPayload ? TxWorkItem.fromFlowStep("one",
                FlowStep.builder("payment").withTxPlan(plan).build())
                : TxWorkItem.fromTxPlan("one", plan);
    }
}
