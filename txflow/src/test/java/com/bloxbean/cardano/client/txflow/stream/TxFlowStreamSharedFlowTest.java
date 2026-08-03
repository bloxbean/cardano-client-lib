package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowError;
import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 1C multi-item flows: per-member projection of one shared engine
 * result (terminal precedence, own-step hashes, no borrowed sibling results),
 * two-phase binding for every member before start, the batch lifecycle
 * (PLANNED → RUNNING → terminal incl. PARTIALLY_COMPLETED, batch ids
 * monotonic and never engine identity), and shared-flow cancellation
 * (REJECTED_SHARED / cancelExecution escalation).
 */
class TxFlowStreamSharedFlowTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String SENDER_B = "addr_test1vpqsenderb";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_A = StreamIdentities.stepId("pay-a");
    private static final String STEP_B = StreamIdentities.stepId("pay-b");

    // ------------------------------------------------------------------
    // Multi-item projection of one shared engine result
    // ------------------------------------------------------------------

    @Test
    void mixedStepOutcomesProjectPerItemStatusesAndPartiallyCompletedBatch() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            assertEquals(1, gateway.started.size(), "one shared flow for the window");

            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(STEP_A, "tx-a");
            handle.submittedEvent(STEP_B, "tx-b");
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.PARTIALLY_COMPLETED,
                    List.of(FlowStepResult.successAt(STEP_A, "tx-a", List.of(), List.of(),
                                    StubEngineGateway.NOW),
                            FlowStepResult.failureAfterSubmissionAt(STEP_B, "tx-b",
                                    List.of(), List.of(),
                                    new IllegalStateException("script rejected"),
                                    StubEngineGateway.NOW)),
                    null, StubEngineGateway.NOW, StubEngineGateway.NOW));

            TxStreamItemResult confirmed = a.completion().toCompletableFuture().join();
            TxStreamItemResult failed = b.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, confirmed.getStatus());
            assertEquals("tx-a", confirmed.getTransactionHash());
            assertNull(confirmed.getError());
            assertEquals(TxStreamItemStatus.FAILED, failed.getStatus());
            assertEquals("tx-b", failed.getTransactionHash(),
                    "a failed member keeps its own submitted hash");
            assertNotNull(failed.getError());
            assertEquals(STEP_A, confirmed.getStepId());
            assertEquals(STEP_B, failed.getStepId());

            assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    @Test
    void inProgressMemberBecomesRecoveryRequiredWhileSiblingConfirms() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(STEP_A, "tx-a");
            handle.submittedEvent(STEP_B, "tx-b");
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.successAt(STEP_A, "tx-a", List.of(), List.of(),
                                    StubEngineGateway.NOW),
                            FlowStepResult.submissionPendingAt(STEP_B, "tx-b",
                                    List.of(), List.of(),
                                    new IllegalStateException("confirmation abandoned"),
                                    StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_EXECUTION_FAILED", FlowErrorCategory.CONFIRMATION,
                            "flow failed", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus(),
                    "the sibling's terminal is never dragged down by the flow error");
            TxStreamItemResult uncertain = b.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, uncertain.getStatus(),
                    "submitted-unconfirmed inside a terminal flow is RECOVERY_REQUIRED");
            assertEquals("tx-b", uncertain.getTransactionHash(), "the hash is never dropped");

            assertEquals(TxStreamBatchStatus.RUNNING,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "a batch with an unresolved member stays RUNNING until repaired");
        }
    }

    @Test
    void flowLevelFailureBeforeAMemberStepRanFailsThatMemberWithoutHash() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(STEP_A, "tx-a");
            // The flow dies after step A: step B never ran and has no result.
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.failureAfterSubmissionAt(STEP_A, "tx-a",
                            List.of(), List.of(), new IllegalStateException("rejected"),
                            StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_EXECUTION_FAILED", FlowErrorCategory.SUBMISSION,
                            "step failed", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            TxStreamItemResult failedWithHash = a.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, failedWithHash.getStatus());
            assertEquals("tx-a", failedWithHash.getTransactionHash());

            TxStreamItemResult neverRan = b.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, neverRan.getStatus(),
                    "a flow-level failure fails members whose steps never ran");
            assertNull(neverRan.getTransactionHash(),
                    "no transaction existed for this member — no borrowed sibling hash");
            assertEquals("TXFLOW_EXECUTION_FAILED", assertInstanceOf(TxStreamException.class,
                    neverRan.getError()).getCode());

            assertEquals(TxStreamBatchStatus.FAILED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    @Test
    void liveSubmittedReadThroughMatchesEachMembersOwnStep() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();

            handle.submittedEvent(STEP_A, "tx-a");
            TxStreamItemResult liveA = stream.getItemStatus("pay-a").orElseThrow();
            assertEquals(TxStreamItemStatus.SUBMITTED, liveA.getStatus());
            assertEquals("tx-a", liveA.getTransactionHash());
            assertEquals(TxStreamItemStatus.PLANNED,
                    stream.getItemStatus("pay-b").orElseThrow().getStatus(),
                    "a sibling's submission event must never project this member");

            handle.submittedEvent(STEP_B, "tx-b");
            TxStreamItemResult liveB = stream.getItemStatus("pay-b").orElseThrow();
            assertEquals(TxStreamItemStatus.SUBMITTED, liveB.getStatus());
            assertEquals("tx-b", liveB.getTransactionHash(),
                    "the member projects its own step's hash, not the sibling's");

            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.COMPLETED,
                    List.of(FlowStepResult.successAt(STEP_A, "tx-a", List.of(), List.of(),
                                    StubEngineGateway.NOW),
                            FlowStepResult.successAt(STEP_B, "tx-b", List.of(), List.of(),
                                    StubEngineGateway.NOW)),
                    null, StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals("tx-a",
                    a.completion().toCompletableFuture().join().getTransactionHash());
            assertEquals("tx-b",
                    b.completion().toCompletableFuture().join().getTransactionHash());
        }
    }

    // ------------------------------------------------------------------
    // Read-through reconciliation is member-scoped for shared flows
    // ------------------------------------------------------------------

    @Test
    void sharedMemberReconcileUsesMemberAttemptEvidenceNotTheFlowState() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            String executionId = gateway.started.get(0).getExecutionId();
            completeBothRecoveryRequired(gateway.lastHandle());
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    a.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    b.completion().toCompletableFuture().join().getStatus());

            // Operator recovery resolved member A's transaction as confirmed
            // and member B's as failed; the flow itself is PARTIALLY_COMPLETED
            // — flow-level truth that must not be projected onto members.
            gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                    Map.of("attempts", Map.of(
                            STEP_A + ":1", attempt(STEP_A, AttemptState.CONFIRMED, "tx-a"),
                            STEP_B + ":1", attempt(STEP_B, AttemptState.FAILED, "tx-b"))));

            TxStreamItemResult repairedA = stream.reconcile("pay-a").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repairedA.getStatus(),
                    "the member's own confirmed attempt decides, not the mixed flow state");
            assertEquals("tx-a", repairedA.getTransactionHash());

            TxStreamItemResult repairedB = stream.reconcile("pay-b").orElseThrow();
            assertEquals(TxStreamItemStatus.FAILED, repairedB.getStatus());
            assertEquals("tx-b", repairedB.getTransactionHash());

            assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "the repaired members complete the batch");
        }
    }

    @Test
    void sharedMemberWithoutAttemptEvidenceIsNeverGuessedFromAMixedFlowState() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            stream.submit(planItem("pay-b"));
            String executionId = gateway.started.get(0).getExecutionId();
            completeBothRecoveryRequired(gateway.lastHandle());
            a.completion().toCompletableFuture().join();

            // PARTIALLY_COMPLETED says nothing about WHICH members confirmed.
            gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED);
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    stream.reconcile("pay-a").orElseThrow().getStatus(),
                    "no member-level evidence: the projection must not guess FAILED");

            // An unambiguous flow state still repairs every member.
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED);
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.reconcile("pay-a").orElseThrow().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // BUG-1C-R2 regression: MATCHED resubmission of a stored
    // PARTIALLY_COMPLETED shared flow must not guess FAILED per member
    // ------------------------------------------------------------------

    @Test
    void matchedStoredPartiallyCompletedFlowProjectsEachMemberFromItsOwnAttemptEvidence() {
        StubEngineGateway gateway = new StubEngineGateway();
        String claimKey = StreamIdentities.windowClaimKey(List.of("pay-a", "pay-b"));
        String executionId = StreamIdentities.executionId(
                StreamIdentities.namespace("payouts"), claimKey);
        // The stored terminal: member A's transaction confirmed, member B's
        // failed — the flow itself is PARTIALLY_COMPLETED.
        gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                Map.of("attempts", Map.of(
                        STEP_A + ":1", attempt(STEP_A, AttemptState.CONFIRMED, "tx-a"),
                        STEP_B + ":1", attempt(STEP_B, AttemptState.FAILED, "tx-b"))));
        // A MATCHED stored terminal completes with the stored flow state and
        // no step results — flow-level truth only.
        gateway.immediateResult = request -> new FlowExecutionResult(
                request.getExecutionId(), "fp", FlowExecutionState.PARTIALLY_COMPLETED,
                List.of(), null, StubEngineGateway.NOW, StubEngineGateway.NOW);
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            assertEquals(executionId, gateway.started.get(0).getExecutionId(),
                    "the identical window must MATCH the stored execution");

            TxStreamItemResult confirmed = a.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, confirmed.getStatus(),
                    "the member whose own attempt confirmed must never be mapped FAILED"
                            + " from the mixed flow state");
            assertEquals("tx-a", confirmed.getTransactionHash());
            assertNull(confirmed.getError());

            TxStreamItemResult failed = b.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, failed.getStatus());
            assertEquals("tx-b", failed.getTransactionHash());

            assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    @Test
    void matchedStoredPartiallyCompletedMemberWithoutAttemptEvidenceIsRecoveryRequired() {
        StubEngineGateway gateway = new StubEngineGateway();
        String claimKey = StreamIdentities.windowClaimKey(List.of("pay-a", "pay-b"));
        String executionId = StreamIdentities.executionId(
                StreamIdentities.namespace("payouts"), claimKey);
        // PARTIALLY_COMPLETED with no attempt history: the snapshot cannot
        // say WHICH members confirmed.
        gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED);
        gateway.immediateResult = request -> new FlowExecutionResult(
                request.getExecutionId(), "fp", FlowExecutionState.PARTIALLY_COMPLETED,
                List.of(), null, StubEngineGateway.NOW, StubEngineGateway.NOW);
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            stream.submit(planItem("pay-b"));

            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    a.completion().toCompletableFuture().join().getStatus(),
                    "no member-level evidence: the projection must not guess FAILED");
            assertEquals(TxStreamBatchStatus.RUNNING,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "unresolved members keep the batch RUNNING until repaired");

            // Read-through reconcile repairs the member once evidence exists.
            gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                    Map.of("attempts", Map.of(
                            STEP_A + ":1", attempt(STEP_A, AttemptState.CONFIRMED, "tx-a"))));
            TxStreamItemResult repaired = stream.reconcile("pay-a").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repaired.getStatus());
            assertEquals("tx-a", repaired.getTransactionHash());
        }
    }

    // ------------------------------------------------------------------
    // Two-phase binding for multi-item flows
    // ------------------------------------------------------------------

    @Test
    void everyMemberIsBoundBeforeStartAndConfirmedAfter() {
        List<String> callLog = new CopyOnWriteArrayList<>();
        StubEngineGateway gateway = new StubEngineGateway(callLog);
        RecordingStateStore store = new RecordingStateStore(callLog);
        try (TxFlowStream stream = sharedFlowBuilder(gateway).stateStore(store).build()) {
            stream.start();
            stream.submit(planItem("pay-a"));
            stream.submit(planItem("pay-b"));
            String executionId = gateway.started.get(0).getExecutionId();
            completeBothConfirmed(gateway.lastHandle());

            assertEquals(List.of(
                    "register:pay-a",
                    "register:pay-b",
                    "bind:pay-a",
                    "bind:pay-b",
                    "start:" + executionId,
                    "confirm:pay-a:CREATED",
                    "confirm:pay-b:CREATED"), callLog,
                    "every member binds write-ahead of start and confirms after");
            assertEquals(executionId, store.bindings.get("pay-a").executionId());
            assertEquals(executionId, store.bindings.get("pay-b").executionId());
            assertEquals(STEP_A, store.bindings.get("pay-a").stepId());
            assertEquals(STEP_B, store.bindings.get("pay-b").stepId());
        }
    }

    @Test
    void bindFailureForAnyMemberFailsTheWholeFlowTypedBeforeEngineStart() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        store.bindFailure = new IllegalStateException("binding storage down");
        store.bindFailureItemId = "pay-b";
        try (TxFlowStream stream = sharedFlowBuilder(gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));

            for (TxStreamReceipt receipt : List.of(a, b)) {
                TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus(),
                        "a fail-closed binding failure settles every member");
                assertEquals("TXSTREAM_BINDING_FAILED",
                        assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            }
            assertTrue(gateway.started.isEmpty(),
                    "the engine must never start a flow with an unbound member");
            assertTrue(stream.isHealthy());
            assertEquals(TxStreamBatchStatus.FAILED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    // ------------------------------------------------------------------
    // Batch lifecycle
    // ------------------------------------------------------------------

    @Test
    void batchLifecycleRunsPlannedRunningTerminalWithMonotonicIds() {
        StubEngineGateway gateway = new StubEngineGateway();
        BatchRecordingListener listener = new BatchRecordingListener();
        try (TxFlowStream stream = sharedFlowBuilder(gateway)
                .eventListener(listener).build()) {
            stream.start();
            stream.submit(planItem("pay-a"));
            stream.submit(planItem("pay-b"));
            completeBothConfirmed(gateway.lastHandle());

            assertEquals(List.of(TxStreamBatchStatus.PLANNED, TxStreamBatchStatus.RUNNING,
                            TxStreamBatchStatus.COMPLETED),
                    listener.statuses("batch-1"),
                    "onBatchUpdated must observe the full lifecycle");
            TxStreamBatchResult completed = stream.getBatchStatus("batch-1").orElseThrow();
            assertEquals(TxStreamBatchStatus.COMPLETED, completed.status());
            assertEquals(List.of("pay-a", "pay-b"), completed.itemIds());
            assertEquals(1, completed.executionIds().size());
            assertNull(completed.failure());

            // Batch ids are stream-scoped and monotonic; engine identity never
            // contains them.
            stream.submit(planItem("pay-c"));
            stream.submit(planItem("pay-d"));
            completeBothConfirmed(gateway.handles.get(1),
                    StreamIdentities.stepId("pay-c"), StreamIdentities.stepId("pay-d"));
            assertEquals(TxStreamBatchStatus.COMPLETED,
                    stream.getBatchStatus("batch-2").orElseThrow().status());
            for (var request : gateway.started) {
                assertFalse(request.getExecutionId().contains("batch"));
                assertFalse(request.getDefinition().getId().contains("batch"));
                assertFalse(request.getIdempotencyKey().contains("batch"));
            }
        }
    }

    @Test
    void twoLaneExecutionsOfOneBatchSettlingConcurrentlyDeriveTheBatchAtomically()
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                StubEngineGateway gateway = new StubEngineGateway();
                BatchRecordingListener listener = new BatchRecordingListener();
                try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                        .lanes(LanePolicy.explicit())
                        .laneResolver(name -> "lane-a".equals(name)
                                ? ResolvedLane.ofAddress("lane-a", SENDER)
                                : ResolvedLane.ofAddress("lane-b", SENDER_B))
                        .planner(TxStreamPlanner.perWindow())
                        .window(WindowPolicy.count(2))
                        .eventListener(listener)
                        .executor(Runnable::run)
                        .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                        .build()) {
                    stream.start();
                    TxStreamReceipt a = stream.submit(TxWorkItem.builder("pay-a")
                            .withTxPlan(TxPlan.from(new Tx()
                                    .payToAddress(RECEIVER, Amount.ada(1.5))))
                            .withLane("lane-a").build());
                    TxStreamReceipt b = stream.submit(TxWorkItem.builder("pay-b")
                            .withTxPlan(TxPlan.from(new Tx()
                                    .payToAddress(RECEIVER, Amount.ada(1.5))))
                            .withLane("lane-b").build());
                    assertEquals(2, gateway.started.size(),
                            "one batch partitions into one execution per lane");

                    // Both executions of the one batch settle simultaneously
                    // on two real threads: batch derivation must be atomic —
                    // exactly one terminal publication, COMPLETED, and it
                    // never regresses.
                    CyclicBarrier barrier = new CyclicBarrier(2);
                    List<Future<?>> completers = new ArrayList<>();
                    for (int i = 0; i < 2; i++) {
                        StubEngineGateway.StubHandle handle = gateway.handles.get(i);
                        String stepId = gateway.started.get(i)
                                .getDefinition().getSteps().get(0).getId();
                        completers.add(pool.submit(() -> {
                            barrier.await();
                            handle.completeConfirmed(stepId, "tx-" + stepId);
                            return null;
                        }));
                    }
                    for (Future<?> completer : completers) {
                        completer.get(10, TimeUnit.SECONDS);
                    }
                    assertEquals(TxStreamItemStatus.CONFIRMED,
                            a.completion().toCompletableFuture()
                                    .get(10, TimeUnit.SECONDS).getStatus());
                    assertEquals(TxStreamItemStatus.CONFIRMED,
                            b.completion().toCompletableFuture()
                                    .get(10, TimeUnit.SECONDS).getStatus());

                    List<TxStreamBatchStatus> statuses = listener.statuses("batch-1");
                    assertEquals(TxStreamBatchStatus.COMPLETED,
                            statuses.get(statuses.size() - 1),
                            "the terminal publication is last: " + statuses);
                    assertEquals(1, statuses.stream()
                                    .filter(status -> status == TxStreamBatchStatus.COMPLETED)
                                    .count(),
                            "exactly one terminal derivation: " + statuses);
                    assertEquals(TxStreamBatchStatus.COMPLETED,
                            stream.getBatchStatus("batch-1").orElseThrow().status(),
                            "COMPLETED never regresses");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Shared-flow cancellation
    // ------------------------------------------------------------------

    @Test
    void cancelItemOnAnInFlightSharedFlowMemberIsRejectedSharedWithFullShape() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            stream.submit(planItem("pay-b"));
            String executionId = gateway.started.get(0).getExecutionId();

            CancelOutcome outcome = stream.cancelItem("pay-a", "changed my mind");
            assertEquals(CancelOutcome.Kind.REJECTED_SHARED, outcome.kind());
            assertEquals(executionId, outcome.executionId().orElseThrow(),
                    "the rejection names the shared execution for explicit escalation");
            assertEquals(List.of("pay-a", "pay-b"), outcome.memberItemIds(),
                    "the rejection carries the full affected member set");
            assertFalse(stream.cancel("pay-a", "changed my mind"),
                    "the boolean convenience reports shared rejection as false");
            assertFalse(gateway.lastHandle().cancelRequested.get(),
                    "a rejected item cancel must not signal the engine");
            assertFalse(a.completion().toCompletableFuture().isDone(),
                    "nothing was cancelled");

            completeBothConfirmed(gateway.lastHandle());
        }
    }

    @Test
    void cancelExecutionSignalsTheWholeFlowAndMembersSettlePerEngineOutcome() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            String executionId = gateway.started.get(0).getExecutionId();

            assertTrue(stream.cancelExecution(executionId, "operator abort"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            assertTrue(handle.cancelRequested.get());
            assertEquals("operator abort", handle.cancelReason);

            // Step A won the race before the cancel took effect.
            handle.submittedEvent(STEP_A, "tx-a");
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.CANCELLED,
                    List.of(FlowStepResult.successAt(STEP_A, "tx-a", List.of(), List.of(),
                                    StubEngineGateway.NOW),
                            FlowStepResult.cancelledAt(STEP_B,
                                    new IllegalStateException("operator abort"),
                                    StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_CANCELLED", FlowErrorCategory.CANCELLATION,
                            "Execution cancelled", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus(),
                    "a member whose step completed keeps its honest CONFIRMED");
            assertEquals(TxStreamItemStatus.CANCELLED,
                    b.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    @Test
    void cancelExecutionOnAQueuedSharedFlowCancelsEveryMemberWithoutDispatch() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = sharedFlowBuilder(gateway).build()) {
            stream.start();
            // First window occupies the lane.
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            stream.submit(planItem("pay-b"));
            assertEquals(1, gateway.started.size());
            // Second window queues behind it on the same lane.
            TxStreamReceipt c = stream.submit(planItem("pay-c"));
            TxStreamReceipt d = stream.submit(planItem("pay-d"));
            assertEquals(1, gateway.started.size(), "the lane serializes the second flow");

            // A member of the queued flow can still only be cancelled whole.
            CancelOutcome rejected = stream.cancelItem("pay-c", "late");
            assertEquals(CancelOutcome.Kind.REJECTED_SHARED, rejected.kind());
            String queuedExecutionId = rejected.executionId().orElseThrow();

            assertTrue(stream.cancelExecution(queuedExecutionId, "late"));
            assertEquals(TxStreamItemStatus.CANCELLED,
                    c.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CANCELLED,
                    d.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamBatchStatus.CANCELLED,
                    stream.getBatchStatus("batch-2").orElseThrow().status());

            completeBothConfirmed(gateway.lastHandle());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus());
            assertEquals(1, gateway.started.size(),
                    "the cancelled flow must never reach the engine");
        }
    }

    @Test
    void cancelItemOnASingleMemberPerWindowFlowIsStillSignalledSingle() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .planner(TxStreamPlanner.perWindow())
                .window(WindowPolicy.count(1))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-a"));
            assertEquals(1, gateway.started.size());

            CancelOutcome outcome = stream.cancelItem("pay-a", "abort");
            assertEquals(CancelOutcome.Kind.SIGNALLED_SINGLE, outcome.kind(),
                    "a one-member flow keeps single-item cancel semantics");
            assertTrue(stream.cancel("pay-a", "abort"),
                    "the boolean convenience delegates consistently");
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            assertTrue(handle.cancelRequested.get());

            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.CANCELLED,
                    List.of(FlowStepResult.cancelledAt(STEP_A,
                            new IllegalStateException("abort"), StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_CANCELLED", FlowErrorCategory.CANCELLATION,
                            "Execution cancelled", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.CANCELLED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Single lane, perWindow planner, two-item count windows. */
    private TxFlowStream.Builder sharedFlowBuilder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .planner(TxStreamPlanner.perWindow())
                .window(WindowPolicy.count(2))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private void completeBothConfirmed(StubEngineGateway.StubHandle handle) {
        completeBothConfirmed(handle, STEP_A, STEP_B);
    }

    private void completeBothConfirmed(StubEngineGateway.StubHandle handle,
                                       String firstStep, String secondStep) {
        handle.submittedEvent(firstStep, "tx-" + firstStep);
        handle.submittedEvent(secondStep, "tx-" + secondStep);
        handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                FlowExecutionState.COMPLETED,
                List.of(FlowStepResult.successAt(firstStep, "tx-" + firstStep,
                                List.of(), List.of(), StubEngineGateway.NOW),
                        FlowStepResult.successAt(secondStep, "tx-" + secondStep,
                                List.of(), List.of(), StubEngineGateway.NOW)),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW));
    }

    private void completeBothRecoveryRequired(StubEngineGateway.StubHandle handle) {
        handle.submittedEvent(STEP_A, "tx-a");
        handle.submittedEvent(STEP_B, "tx-b");
        handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                FlowExecutionState.FAILED,
                List.of(FlowStepResult.submissionPendingAt(STEP_A, "tx-a", List.of(),
                                List.of(), new IllegalStateException("abandoned"),
                                StubEngineGateway.NOW),
                        FlowStepResult.submissionPendingAt(STEP_B, "tx-b", List.of(),
                                List.of(), new IllegalStateException("abandoned"),
                                StubEngineGateway.NOW)),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW));
    }

    private FlowAttemptSnapshot attempt(String stepId, AttemptState state, String hash) {
        return new FlowAttemptSnapshot(stepId, 1, state,
                new SignedPayload.InlineCbor(new byte[]{1}, "sha", hash),
                null, null, List.of(), List.of(), StubEngineGateway.NOW, null);
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER)));
    }

    static final class BatchRecordingListener implements TxStreamEventListener {
        final List<TxStreamBatchResult> batches = new CopyOnWriteArrayList<>();

        @Override
        public void onBatchUpdated(TxStreamBatchResult batch) {
            batches.add(batch);
        }

        List<TxStreamBatchStatus> statuses(String batchId) {
            return batches.stream()
                    .filter(batch -> batch.batchId().equals(batchId))
                    .map(TxStreamBatchResult::status)
                    .toList();
        }
    }
}
