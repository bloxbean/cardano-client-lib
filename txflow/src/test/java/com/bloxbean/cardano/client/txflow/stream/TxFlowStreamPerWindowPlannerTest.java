package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 1C {@code perWindow()} planner: lane partitioning (one flow per
 * lane group, exactly one lane per execution), deterministic claim-derived
 * flow/step/execution identities regardless of submission order, and
 * flow-level dedup of an identical window resubmission.
 */
class TxFlowStreamPerWindowPlannerTest {
    private static final String SENDER_A = "addr_test1vpqsendera";
    private static final String SENDER_B = "addr_test1vpqsenderb";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String NAMESPACE = StreamIdentities.namespace("payouts");

    // ------------------------------------------------------------------
    // Planner-local chaining mode
    // ------------------------------------------------------------------

    @Test
    void defaultAndExplicitSequentialAreTheSameCompatibilityPlanner() {
        assertSame(TxStreamPlanner.perWindow(),
                TxStreamPlanner.perWindow(ChainingMode.SEQUENTIAL));

        FlowExecutionRequest legacy = runOneWindow(new StubEngineGateway(),
                List.of("pay-1", "pay-2"), TxStreamPlanner.perWindow());
        FlowExecutionRequest explicit = runOneWindow(new StubEngineGateway(),
                List.of("pay-1", "pay-2"),
                TxStreamPlanner.perWindow(ChainingMode.SEQUENTIAL));

        assertEquals(legacy.getDefinition().getId(), explicit.getDefinition().getId());
        assertEquals(legacy.getDefinition().getStepIds(),
                explicit.getDefinition().getStepIds(),
                "explicit sequential must preserve the legacy definition identity");
        assertEquals(legacy.getExecutionId(), explicit.getExecutionId());
        assertEquals(legacy.getIdempotencyKey(), explicit.getIdempotencyKey());
        assertNull(explicit.getDefinition().getExecutionSettings().getChainingMode(),
                "legacy sequential stays implicit for request compatibility");
    }

    @Test
    void pipelinedModeIsAppliedToTheGeneratedMultiStepFlow() {
        FlowExecutionRequest request = runOneWindow(new StubEngineGateway(),
                List.of("pay-1", "pay-2"),
                TxStreamPlanner.perWindow(ChainingMode.PIPELINED));

        assertEquals(2, request.getDefinition().getSteps().size());
        assertEquals(ChainingMode.PIPELINED,
                request.getDefinition().getExecutionSettings().getChainingMode());
        assertTrue(request.getDefinition().getSteps().get(0).getFundingFrom().isEmpty());
        assertEquals(request.getDefinition().getSteps().get(0).getId(),
                request.getDefinition().getSteps().get(1).getFundingFrom().get(0),
                "same-lane pipelining must expose the previous pending change output");
    }

    @Test
    void perWindowRejectsNullAndBatchInsteadOfDowngrading() {
        assertThrows(IllegalArgumentException.class, () -> TxStreamPlanner.perWindow(null));
        IllegalArgumentException unsupported = assertThrows(IllegalArgumentException.class,
                () -> TxStreamPlanner.perWindow(ChainingMode.BATCH));
        assertTrue(unsupported.getMessage().contains("only SEQUENTIAL and PIPELINED"));
    }

    @Test
    void plannerLocalModeDoesNotChangeOtherBuiltInOrCustomPlannerFlows() {
        StubEngineGateway perItemGateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(perItemGateway,
                TxStreamPlanner.perItem()).build()) {
            stream.start();
            stream.submit(planItem("per-item"));
            assertNull(perItemGateway.started.get(0).getDefinition()
                    .getExecutionSettings().getChainingMode());
            completeAllStepsConfirmed(perItemGateway, 0);
        }

        StubEngineGateway batchingGateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(batchingGateway,
                TxStreamPlanner.batching()).build()) {
            stream.start();
            stream.submit(planItem("batched"));
            assertNull(batchingGateway.started.get(0).getDefinition()
                    .getExecutionSettings().getChainingMode());
            completeAllStepsConfirmed(batchingGateway, 0);
        }

        TxStreamPlanner custom = context -> {
            TxWorkItem item = context.items().get(0);
            TxStreamPlanningContext.PlanningSeed seed = context.seed(item.getItemId());
            FlowStep step = seed.enforcedStep;
            TxFlow flow = TxFlow.builder(context.ids().flowId(List.of(seed.claimKey)))
                    .withChainingMode(ChainingMode.BATCH)
                    .addStep(step)
                    .build();
            return TxStreamPlan.of(List.of(new PlannedExecution(flow, seed.lane.laneName(),
                    seed.claimKey,
                    List.of(new TxStreamPlannedItem(item.getItemId(), step.getId())))));
        };
        StubEngineGateway customGateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(customGateway, custom).build()) {
            stream.start();
            stream.submit(planItem("custom"));
            assertEquals(ChainingMode.BATCH, customGateway.started.get(0).getDefinition()
                    .getExecutionSettings().getChainingMode(),
                    "custom planners retain ownership of their flow settings");
            completeAllStepsConfirmed(customGateway, 0);
        }
    }

    // ------------------------------------------------------------------
    // Lane partitioning
    // ------------------------------------------------------------------

    @Test
    void windowSpanningTwoLanesPartitionsIntoOneFlowPerLaneWithCorrectMembers() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = explicitBuilder(gateway)
                .stateStore(store)
                .window(WindowPolicy.count(3))
                .build()) {
            stream.start();
            TxStreamReceipt a1 = stream.submit(laneItem("a-1", "lane-a"));
            TxStreamReceipt b1 = stream.submit(laneItem("b-1", "lane-b"));
            TxStreamReceipt a2 = stream.submit(laneItem("a-2", "lane-a"));

            assertEquals(2, gateway.started.size(),
                    "a two-lane window partitions into exactly two flows");
            FlowExecutionRequest laneARequest = gateway.started.get(0);
            FlowExecutionRequest laneBRequest = gateway.started.get(1);

            // One lane per execution: each request declares exactly its lane's
            // canonical identity as the spending resource.
            assertEquals(List.of("addr:" + SENDER_A),
                    List.copyOf(laneARequest.getSpendingResources()));
            assertEquals(List.of("addr:" + SENDER_B),
                    List.copyOf(laneBRequest.getSpendingResources()));
            assertEquals(2, laneARequest.getDefinition().getSteps().size());
            assertEquals(1, laneBRequest.getDefinition().getSteps().size());

            // Member mapping: every item is bound to its own deterministic
            // step of its lane's flow.
            assertEquals(StreamIdentities.stepId("a-1"), store.bindings.get("a-1").stepId());
            assertEquals(StreamIdentities.stepId("a-2"), store.bindings.get("a-2").stepId());
            assertEquals(StreamIdentities.stepId("b-1"), store.bindings.get("b-1").stepId());
            assertEquals(laneARequest.getExecutionId(), store.bindings.get("a-1").executionId());
            assertEquals(laneARequest.getExecutionId(), store.bindings.get("a-2").executionId());
            assertEquals(laneBRequest.getExecutionId(), store.bindings.get("b-1").executionId());
            assertEquals(List.of(StreamIdentities.stepId("a-1"), StreamIdentities.stepId("a-2")),
                    laneARequest.getDefinition().getSteps().stream()
                            .map(step -> step.getId()).toList(),
                    "members ride their own steps, ordered by claim key");

            // The flow claim covers exactly its lane group's member keys.
            assertEquals(StreamIdentities.windowClaimKey(List.of("a-1", "a-2")),
                    laneARequest.getIdempotencyKey());
            assertEquals(StreamIdentities.windowClaimKey(List.of("b-1")),
                    laneBRequest.getIdempotencyKey());

            completeAllStepsConfirmed(gateway, 0);
            completeAllStepsConfirmed(gateway, 1);
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a1.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a2.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b1.completion().toCompletableFuture().join().getStatus());
            assertEquals("lane-a", a1.current().getLaneName());
            assertEquals("lane-b", b1.current().getLaneName());
            assertEquals("tx-" + StreamIdentities.stepId("a-2"),
                    a2.current().getTransactionHash(),
                    "each member carries its own step's transaction hash");

            TxStreamBatchResult batch = stream.getBatchStatus("batch-1").orElseThrow();
            assertEquals(TxStreamBatchStatus.COMPLETED, batch.status());
            assertEquals(List.of("a-1", "b-1", "a-2"), batch.itemIds(),
                    "batch members stay in window acceptance order");
            assertEquals(2, batch.executionIds().size());
        }
    }

    // ------------------------------------------------------------------
    // Deterministic identity
    // ------------------------------------------------------------------

    @Test
    void sameWindowItemsInDifferentSubmitOrdersProduceIdenticalIdentities() {
        FlowExecutionRequest firstRequest = runOneWindow(new StubEngineGateway(),
                List.of("pay-1", "pay-2"));
        FlowExecutionRequest secondRequest = runOneWindow(new StubEngineGateway(),
                List.of("pay-2", "pay-1"));

        assertEquals(firstRequest.getExecutionId(), secondRequest.getExecutionId(),
                "execution identity derives from the sorted member keys");
        assertEquals(firstRequest.getIdempotencyKey(), secondRequest.getIdempotencyKey());
        assertEquals(firstRequest.getDefinition().getId(),
                secondRequest.getDefinition().getId());
        assertEquals(
                firstRequest.getDefinition().getSteps().stream()
                        .map(step -> step.getId()).toList(),
                secondRequest.getDefinition().getSteps().stream()
                        .map(step -> step.getId()).toList(),
                "step order derives from sorted claim keys, not submission order");
        assertEquals("stream:payouts", secondRequest.getIdempotencyNamespace());
    }

    @Test
    void stableIdFactoryDerivesSortedClaimBasedIdentitiesAndRejectsBlankKeys() {
        StableIdFactory ids = StreamIdentities.idFactory(NAMESPACE);
        assertEquals(ids.flowId(List.of("a", "b")), ids.flowId(List.of("b", "a")),
                "member keys are sorted internally");
        assertNotEquals(ids.flowId(List.of("a")), ids.flowId(List.of("a", "b")));
        assertEquals(ids.stepId("a"), ids.stepId("a"));
        assertNotEquals(ids.stepId("a"), ids.stepId("b"));
        assertEquals(StreamIdentities.flowId(NAMESPACE, "a"), ids.flowId(List.of("a")),
                "a single-member flow id stays byte-identical to the per-item derivation");
        assertThrows(IllegalArgumentException.class, () -> ids.flowId(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ids.flowId(List.of("a", " ")));
        assertThrows(IllegalArgumentException.class, () -> ids.stepId(" "));
    }

    // ------------------------------------------------------------------
    // Flow-level dedup (Decision 3)
    // ------------------------------------------------------------------

    @Test
    void identicalWindowResubmissionOnANewStreamMatchesTheStoredExecution() {
        StubEngineGateway shared = new StubEngineGateway();
        String stepA = StreamIdentities.stepId("pay-1");
        String stepB = StreamIdentities.stepId("pay-2");
        String executionId;
        String claimKey;
        try (TxFlowStream first = singleLaneBuilder(shared)
                .window(WindowPolicy.count(2)).build()) {
            first.start();
            TxStreamReceipt r1 = first.submit(planItem("pay-1"));
            TxStreamReceipt r2 = first.submit(planItem("pay-2"));
            assertEquals(1, shared.started.size());
            executionId = shared.started.get(0).getExecutionId();
            claimKey = shared.started.get(0).getIdempotencyKey();
            shared.handles.get(0).submittedEvent(stepA, "tx-a");
            shared.handles.get(0).submittedEvent(stepB, "tx-b");
            shared.handles.get(0).complete(new FlowExecutionResult(executionId, "fp",
                    FlowExecutionState.COMPLETED,
                    List.of(FlowStepResult.successAt(stepA, "tx-a", List.of(), List.of(),
                                    StubEngineGateway.NOW),
                            FlowStepResult.successAt(stepB, "tx-b", List.of(), List.of(),
                                    StubEngineGateway.NOW)),
                    null, StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    r1.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    r2.completion().toCompletableFuture().join().getStatus());
        }

        // The engine holds the claim durably; a stored-snapshot MATCH returns
        // a completed handle with an empty step list, and the durable
        // snapshot carries the per-step attempt history.
        shared.putSnapshot(executionId, FlowExecutionState.COMPLETED, Map.of("attempts", Map.of(
                stepA + ":1", confirmedAttempt(stepA, "tx-a"),
                stepB + ":1", confirmedAttempt(stepB, "tx-b"))));
        shared.immediateResult = request -> new FlowExecutionResult(request.getExecutionId(),
                "fp", FlowExecutionState.COMPLETED, List.of(), null,
                StubEngineGateway.NOW, StubEngineGateway.NOW);

        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream second = singleLaneBuilder(shared)
                .stateStore(store)
                .window(WindowPolicy.count(2)).build()) {
            second.start();
            // Reversed submit order: the flow claim is order-independent.
            TxStreamReceipt r2 = second.submit(planItem("pay-2"));
            TxStreamReceipt r1 = second.submit(planItem("pay-1"));

            assertEquals(2, shared.started.size());
            assertEquals(executionId, shared.started.get(1).getExecutionId(),
                    "the resubmitted window must target the stored execution's claim");
            assertEquals(claimKey, shared.started.get(1).getIdempotencyKey());
            assertEquals(List.of(BindingOutcome.MATCHED, BindingOutcome.MATCHED), store.outcomes,
                    "an identical window resubmission is a MATCH, not a second run");

            TxStreamItemResult first = r1.completion().toCompletableFuture().join();
            TxStreamItemResult secondResult = r2.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, first.getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, secondResult.getStatus());
            assertEquals("tx-a", first.getTransactionHash(),
                    "the member's own stored hash is recovered through P2 reads");
            assertEquals("tx-b", secondResult.getTransactionHash());
        }
    }

    @Test
    void differentlyComposedWindowIsANewClaim() {
        StubEngineGateway gateway = new StubEngineGateway();
        String firstClaim;
        try (TxFlowStream stream = singleLaneBuilder(gateway)
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            stream.submit(planItem("pay-2"));
            firstClaim = gateway.started.get(0).getIdempotencyKey();
            completeAllStepsConfirmed(gateway, 0);
        }
        StubEngineGateway regrouped = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(regrouped)
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            stream.submit(planItem("pay-1"));
            stream.submit(planItem("pay-3"));
            assertNotEquals(firstClaim, regrouped.started.get(0).getIdempotencyKey(),
                    "perWindow gives flow-level dedup only: a redelivered item in a"
                            + " differently-composed window is a new claim");
            completeAllStepsConfirmed(regrouped, 0);
        }
    }

    @Test
    void perWindowWithoutAWindowPolicyPlansEachItemAsItsOwnWindow() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            assertEquals(1, gateway.started.size(),
                    "no window policy means a window of one, planned immediately");
            assertEquals(StreamIdentities.windowClaimKey(List.of("pay-1")),
                    gateway.started.get(0).getIdempotencyKey(),
                    "perWindow always claims through the window derivation");
            completeAllStepsConfirmed(gateway, 0);
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertFalse(gateway.started.get(0).getExecutionId().contains("batch"),
                    "batch sequence never leaks into engine identity");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private FlowExecutionRequest runOneWindow(StubEngineGateway gateway, List<String> itemIds) {
        return runOneWindow(gateway, itemIds, TxStreamPlanner.perWindow());
    }

    private FlowExecutionRequest runOneWindow(StubEngineGateway gateway, List<String> itemIds,
                                              TxStreamPlanner planner) {
        try (TxFlowStream stream = singleLaneBuilder(gateway, planner)
                .window(WindowPolicy.count(itemIds.size())).build()) {
            stream.start();
            for (String itemId : itemIds) {
                stream.submit(planItem(itemId));
            }
            assertEquals(1, gateway.started.size());
            completeAllStepsConfirmed(gateway, 0);
            return gateway.started.get(0);
        }
    }

    private void completeAllStepsConfirmed(StubEngineGateway gateway, int handleIndex) {
        StubEngineGateway.StubHandle handle = gateway.handles.get(handleIndex);
        FlowExecutionRequest request = gateway.started.get(handleIndex);
        List<FlowStepResult> steps = request.getDefinition().getSteps().stream()
                .map(step -> FlowStepResult.successAt(step.getId(), "tx-" + step.getId(),
                        List.of(), List.of(), StubEngineGateway.NOW))
                .toList();
        handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                FlowExecutionState.COMPLETED, steps, null,
                StubEngineGateway.NOW, StubEngineGateway.NOW));
    }

    private FlowAttemptSnapshot confirmedAttempt(String stepId, String hash) {
        return new FlowAttemptSnapshot(stepId, 1, AttemptState.CONFIRMED,
                new SignedPayload.InlineCbor(new byte[]{1}, "sha", hash),
                null, null, List.of(), List.of(), StubEngineGateway.NOW, null);
    }

    private TxFlowStream.Builder singleLaneBuilder(StubEngineGateway gateway) {
        return singleLaneBuilder(gateway, TxStreamPlanner.perWindow());
    }

    private TxFlowStream.Builder singleLaneBuilder(StubEngineGateway gateway,
                                                   TxStreamPlanner planner) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER_A))
                .planner(planner)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxFlowStream.Builder explicitBuilder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(laneName -> {
                    switch (laneName) {
                        case "lane-a":
                            return ResolvedLane.ofAddress("lane-a", SENDER_A);
                        case "lane-b":
                            return ResolvedLane.ofAddress("lane-b", SENDER_B);
                        default:
                            return null;
                    }
                })
                .planner(TxStreamPlanner.perWindow())
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxWorkItem laneItem(String itemId, String lane) {
        return TxWorkItem.builder(itemId)
                .withTxPlan(TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5))))
                .withLane(lane)
                .build();
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER_A)));
    }
}
