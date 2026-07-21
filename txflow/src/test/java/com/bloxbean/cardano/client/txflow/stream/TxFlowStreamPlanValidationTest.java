package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 1C custom-planner SPI validation: mechanically checkable plan
 * rules reject the whole plan typed {@code TXSTREAM_PLAN_INVALID} /
 * {@code TXSTREAM_PLAN_CROSS_LANE}, omitted items fail typed
 * {@code TXSTREAM_PLAN_OMITTED} while the rest proceeds, and a throwing
 * planner fails only its window typed {@code TXSTREAM_PLANNER_FAILED} — the
 * worker always survives and every window item settles with its buffer
 * capacity released.
 */
class TxFlowStreamPlanValidationTest {
    private static final String SENDER_A = "addr_test1vpqsendera";
    private static final String SENDER_B = "addr_test1vpqsenderb";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String LANE = "payouts-lane";

    // ------------------------------------------------------------------
    // TXSTREAM_PLAN_INVALID
    // ------------------------------------------------------------------

    @Test
    void duplicateItemMappingInsideOneFlowRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(new PlannedExecution(valid.flow(), LANE,
                    valid.idempotencyKey(),
                    List.of(valid.items().get(0), valid.items().get(0)))));
        }, "mapped more than once");
    }

    @Test
    void itemMappedIntoTwoFlowsRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution first = singleItemExecution(context, "pay-1");
            PlannedExecution second = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(first,
                    new PlannedExecution(TxFlow.builder("other-flow")
                                    .addStep(copyStep(stepFor(context, "pay-1"), "other-step"))
                                    .build(),
                            LANE, "other-claim",
                            List.of(new TxStreamPlannedItem("pay-1", "other-step")))));
        }, "mapped more than once");
    }

    @Test
    void mappingReferencingAForeignItemIdRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(new PlannedExecution(valid.flow(), LANE,
                    valid.idempotencyKey(),
                    List.of(new TxStreamPlannedItem("ghost-item",
                            valid.items().get(0).stepId())))));
        }, "not part of this window");
    }

    @Test
    void twoItemsMappedToOneSharedStepIsAllowedAndDispatchesOneExecution() {
        // Shared steps are now legitimate (the batching() planner merges several
        // payment items into one transaction). A plan mapping BOTH window items
        // to ONE step of ONE flow is accepted, dispatches a single execution, and
        // every member is projected transaction-granularly from that step.
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        TxStreamPlanner shareStep = context -> {
            List<String> ids = context.items().stream()
                    .map(TxWorkItem::getItemId).sorted().toList();
            FlowStep step = copyStep(stepFor(context, ids.get(0)), "shared-step");
            TxFlow flow = TxFlow.builder("shared-flow").addStep(step).build();
            return TxStreamPlan.of(List.of(new PlannedExecution(flow, LANE, "shared-claim",
                    ids.stream()
                            .map(id -> new TxStreamPlannedItem(id, "shared-step"))
                            .toList())));
        };
        try (TxFlowStream stream = singleLaneBuilder(gateway, shareStep)
                .stateStore(store)
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(planItem("pay-1"));
            TxStreamReceipt b = stream.submit(planItem("pay-2"));

            assertEquals(1, gateway.started.size(), "both items share one execution");
            assertEquals(1, gateway.started.get(0).getDefinition().getSteps().size(),
                    "both items ride one merged step");
            assertEquals("shared-step", store.bindings.get("pay-1").stepId());
            assertEquals("shared-step", store.bindings.get("pay-2").stepId(),
                    "every member binds the shared step id");
            assertEquals(store.bindings.get("pay-1").executionId(),
                    store.bindings.get("pay-2").executionId());

            gateway.lastHandle().completeConfirmed("shared-step", "tx-shared");
            TxStreamItemResult ra = a.completion().toCompletableFuture().join();
            TxStreamItemResult rb = b.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, ra.getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, rb.getStatus());
            assertEquals("tx-shared", ra.getTransactionHash());
            assertEquals("tx-shared", rb.getTransactionHash(),
                    "members sharing a step share the transaction's outcome and hash");
            assertTrue(stream.isHealthy());
        }
    }

    @Test
    void mappingToAStepAbsentFromTheFlowRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(new PlannedExecution(valid.flow(), LANE,
                    valid.idempotencyKey(),
                    List.of(new TxStreamPlannedItem("pay-1", "ghost-step")))));
        }, "does not exist in flow");
    }

    @Test
    void blankFlowClaimKeyRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(new PlannedExecution(valid.flow(), LANE,
                    "   ", valid.items())));
        }, "blank or oversized idempotency key");
    }

    @Test
    void oversizedFlowClaimKeyRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(new PlannedExecution(valid.flow(), LANE,
                    "k".repeat(4096), valid.items())));
        }, "blank or oversized idempotency key");
    }

    @Test
    void oneClaimKeyOnTwoPlannedFlowsRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution first = singleItemExecution(context, "pay-1");
            PlannedExecution second = singleItemExecution(context, "pay-2");
            return TxStreamPlan.of(List.of(first, new PlannedExecution(second.flow(), LANE,
                    first.idempotencyKey(), second.items())));
        }, "used by two planned flows");
    }

    @Test
    void executionMappingNoItemsRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(new PlannedExecution(valid.flow(), LANE,
                    valid.idempotencyKey(), List.of())));
        }, "maps no items");
    }

    @Test
    void unknownLaneNameRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            return TxStreamPlan.of(List.of(new PlannedExecution(valid.flow(), "ghost-lane",
                    valid.idempotencyKey(), valid.items())));
        }, "not an established lane");
    }

    @Test
    void flowDeclaringAStepNotMappedToAnyItemRejectsTheWholePlanTyped() {
        assertPlanInvalid(context -> {
            PlannedExecution valid = singleItemExecution(context, "pay-1");
            FlowStep extra = copyStep(stepFor(context, "pay-2"), "extra-step");
            TxFlow padded = TxFlow.builder(valid.flow().getId())
                    .addStep(valid.flow().getSteps().get(0))
                    .addStep(extra)
                    .build();
            return TxStreamPlan.of(List.of(new PlannedExecution(padded, LANE,
                    valid.idempotencyKey(), valid.items())));
        }, "must be mapped to an item");
    }

    // ------------------------------------------------------------------
    // GAP-1: planner claim key colliding with a LIVE in-flight execution
    // ------------------------------------------------------------------

    @Test
    void plannerClaimKeyCollidingWithALiveInFlightExecutionRejectsThePlanTyped()
            throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamPlanner constantClaim = context -> {
            String itemId = context.items().get(0).getItemId();
            FlowStep step = copyStep(stepFor(context, itemId), "step-1");
            TxFlow flow = TxFlow.builder("flow-constant").addStep(step).build();
            return TxStreamPlan.of(List.of(new PlannedExecution(flow, LANE, "constant-claim",
                    List.of(new TxStreamPlannedItem(itemId, "step-1")))));
        };
        try (TxFlowStream stream = singleLaneBuilder(gateway, constantClaim)
                .window(WindowPolicy.count(1)).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            assertEquals(1, gateway.started.size(), "the first window dispatches normally");
            String liveExecutionId = gateway.started.get(0).getExecutionId();
            assertFalse(first.completion().toCompletableFuture().isDone(),
                    "the first execution is deliberately held in flight");

            // Second window: the planner reuses the SAME claim key while the
            // first execution is live — accepting the plan would clobber the
            // live execution's state, so the whole plan is rejected typed.
            TxStreamReceipt second = stream.submit(planItem("pay-2"));
            TxStreamItemResult outcome = second.completion().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            TxStreamException error =
                    assertInstanceOf(TxStreamException.class, outcome.getError());
            assertEquals("TXSTREAM_PLAN_INVALID", error.getCode());
            assertTrue(error.getMessage().contains("constant-claim"),
                    "the rejection must name the offending claim key: " + error.getMessage());
            assertTrue(error.getMessage().contains(liveExecutionId),
                    "the rejection must name the live execution: " + error.getMessage());
            assertEquals(1, gateway.started.size(), "the live execution is untouched");
            assertTrue(stream.isHealthy());

            // The live execution stays fully operable: cancellable and
            // completable with its own outcome.
            assertTrue(stream.cancelExecution(liveExecutionId, "still mine"));
            assertTrue(gateway.lastHandle().cancelRequested.get());
            gateway.lastHandle().completeConfirmed("step-1", "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture()
                            .get(10, TimeUnit.SECONDS).getStatus());
        }
    }

    // ------------------------------------------------------------------
    // TXSTREAM_PLAN_CROSS_LANE
    // ------------------------------------------------------------------

    @Test
    void flowWhoseMembersSpanLanesRejectsTheWholePlanTypedCrossLane() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamPlanner crossLane = context -> {
            FlowStep stepA = copyStep(stepFor(context, "a-1"), "s-a");
            FlowStep stepB = copyStep(stepFor(context, "b-1"), "s-b");
            TxFlow flow = TxFlow.builder("cross-flow").addStep(stepA).addStep(stepB).build();
            return TxStreamPlan.of(List.of(new PlannedExecution(flow, "lane-a", "cross-claim",
                    List.of(new TxStreamPlannedItem("a-1", "s-a"),
                            new TxStreamPlannedItem("b-1", "s-b")))));
        };
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(laneName -> "lane-a".equals(laneName)
                        ? ResolvedLane.ofAddress("lane-a", SENDER_A)
                        : ResolvedLane.ofAddress("lane-b", SENDER_B))
                .planner(crossLane)
                .window(WindowPolicy.count(2))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(TxWorkItem.builder("a-1")
                    .withTxPlan(plainPlan()).withLane("lane-a").build());
            TxStreamReceipt b = stream.submit(TxWorkItem.builder("b-1")
                    .withTxPlan(plainPlan()).withLane("lane-b").build());

            for (TxStreamReceipt receipt : List.of(a, b)) {
                TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
                TxStreamException error =
                        assertInstanceOf(TxStreamException.class, outcome.getError());
                assertEquals("TXSTREAM_PLAN_CROSS_LANE", error.getCode());
                assertTrue(error.getMessage().contains("share exactly one lane"));
            }
            assertTrue(gateway.started.isEmpty());
            assertTrue(stream.isHealthy());
        }
    }

    // ------------------------------------------------------------------
    // TXSTREAM_PLAN_OMITTED
    // ------------------------------------------------------------------

    @Test
    void omittedItemFailsTypedWhileTheRestOfThePlanProceeds() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamPlanner forgetful = context ->
                TxStreamPlan.of(List.of(singleItemExecution(context, "pay-1")));
        try (TxFlowStream stream = singleLaneBuilder(gateway, forgetful)
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt kept = stream.submit(planItem("pay-1"));
            TxStreamReceipt omitted = stream.submit(planItem("pay-2"));

            TxStreamItemResult omittedOutcome =
                    omitted.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, omittedOutcome.getStatus());
            assertEquals("TXSTREAM_PLAN_OMITTED", assertInstanceOf(TxStreamException.class,
                    omittedOutcome.getError()).getCode());

            assertEquals(1, gateway.started.size(), "the mapped item still dispatches");
            gateway.lastHandle().completeConfirmed(
                    gateway.started.get(0).getDefinition().getSteps().get(0).getId(), "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    kept.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "one member confirmed, one failed");
            assertTrue(stream.isHealthy());
        }
    }

    // ------------------------------------------------------------------
    // TXSTREAM_PLANNER_FAILED
    // ------------------------------------------------------------------

    @Test
    void throwingPlannerFailsOnlyItsWindowTypedAndTheWorkerSurvives() {
        StubEngineGateway gateway = new StubEngineGateway();
        AtomicReference<TxStreamPlanner> delegate = new AtomicReference<>(context -> {
            throw new IllegalStateException("planner boom");
        });
        try (TxFlowStream stream = singleLaneBuilder(gateway,
                context -> delegate.get().plan(context))
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));

            for (TxStreamReceipt receipt : List.of(first, second)) {
                TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
                TxStreamException error =
                        assertInstanceOf(TxStreamException.class, outcome.getError());
                assertEquals("TXSTREAM_PLANNER_FAILED", error.getCode());
                assertNotNull(error.getCause());
            }
            assertTrue(stream.isHealthy(), "a planner failure must never kill the worker");
            TxStreamBatchResult failedBatch = stream.getBatchStatus("batch-1").orElseThrow();
            assertEquals(TxStreamBatchStatus.FAILED, failedBatch.status());
            assertNotNull(failedBatch.failure());

            // The next window plans normally once the planner behaves.
            delegate.set(TxStreamPlanner.perWindow());
            TxStreamReceipt third = stream.submit(planItem("pay-3"));
            TxStreamReceipt fourth = stream.submit(planItem("pay-4"));
            assertEquals(1, gateway.started.size());
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.complete(new com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult(
                    handle.executionId(), "fp",
                    com.bloxbean.cardano.client.txflow.exec.FlowExecutionState.COMPLETED,
                    gateway.started.get(0).getDefinition().getSteps().stream()
                            .map(step -> com.bloxbean.cardano.client.txflow.result.FlowStepResult
                                    .successAt(step.getId(), "tx-" + step.getId(),
                                            List.of(), List.of(), StubEngineGateway.NOW))
                            .toList(),
                    null, StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    third.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    fourth.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void nullPlanFailsTheWindowTypedPlannerFailed() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(gateway, context -> null)
                .window(WindowPolicy.count(1)).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_PLANNER_FAILED", assertInstanceOf(TxStreamException.class,
                    outcome.getError()).getCode());
            assertTrue(stream.isHealthy());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Runs a two-item window through a broken planner and asserts: every
     * window item settles FAILED typed {@code TXSTREAM_PLAN_INVALID} with the
     * detail in the message, nothing reaches the engine, the stream stays
     * healthy, and every buffer permit is released (the buffer is exactly
     * window-sized, so a full follow-up window is accepted).
     */
    private void assertPlanInvalid(TxStreamPlanner broken, String expectedDetail) {
        StubEngineGateway gateway = new StubEngineGateway();
        AtomicReference<TxStreamPlanner> delegate = new AtomicReference<>(broken);
        try (TxFlowStream stream = singleLaneBuilder(gateway,
                context -> delegate.get().plan(context))
                .maxBufferSize(2)
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            TxStreamReceipt second = stream.submit(planItem("pay-2"));

            for (TxStreamReceipt receipt : List.of(first, second)) {
                TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus(),
                        "every window item must settle on plan rejection");
                TxStreamException error =
                        assertInstanceOf(TxStreamException.class, outcome.getError());
                assertEquals("TXSTREAM_PLAN_INVALID", error.getCode());
                assertTrue(error.getMessage().contains(expectedDetail),
                        "unexpected detail: " + error.getMessage());
            }
            assertTrue(gateway.started.isEmpty(), "a rejected plan must never dispatch");
            assertTrue(stream.isHealthy());
            assertEquals(TxStreamBatchStatus.FAILED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());

            // Capacity released: the buffer is exactly window-sized, so a full
            // second window is accepted without FULL.
            delegate.set(TxStreamPlanner.perWindow());
            assertEquals(EmitResult.Status.OK, stream.trySubmit(planItem("pay-3")).getStatus());
            assertEquals(EmitResult.Status.OK, stream.trySubmit(planItem("pay-4")).getStatus());
            assertEquals(1, gateway.started.size());
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.complete(new com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult(
                    handle.executionId(), "fp",
                    com.bloxbean.cardano.client.txflow.exec.FlowExecutionState.COMPLETED,
                    gateway.started.get(0).getDefinition().getSteps().stream()
                            .map(step -> com.bloxbean.cardano.client.txflow.result.FlowStepResult
                                    .successAt(step.getId(), "tx-" + step.getId(),
                                            List.of(), List.of(), StubEngineGateway.NOW))
                            .toList(),
                    null, StubEngineGateway.NOW, StubEngineGateway.NOW));
        }
    }

    /** One valid single-item execution for the given window item. */
    private PlannedExecution singleItemExecution(TxStreamPlanningContext context, String itemId) {
        FlowStep step = copyStep(stepFor(context, itemId), context.ids().stepId(itemId));
        TxFlow flow = TxFlow.builder(context.ids().flowId(List.of(itemId)))
                .addStep(step).build();
        return new PlannedExecution(flow, LANE, itemId,
                List.of(new TxStreamPlannedItem(itemId, step.getId())));
    }

    private FlowStep stepFor(TxStreamPlanningContext context, String itemId) {
        TxWorkItem item = context.items().stream()
                .filter(candidate -> candidate.getItemId().equals(itemId))
                .findFirst().orElseThrow();
        return FlowStep.builder("seed-" + itemId).withTxPlan(item.getTxPlan()).build();
    }

    private FlowStep copyStep(FlowStep step, String newId) {
        return FlowStep.builder(newId).withTxPlan(step.getTxPlan()).build();
    }

    private TxFlowStream.Builder singleLaneBuilder(StubEngineGateway gateway,
                                                   TxStreamPlanner planner) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress(LANE, SENDER_A))
                .planner(planner)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER_A)));
    }

    private TxPlan plainPlan() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)));
    }
}
