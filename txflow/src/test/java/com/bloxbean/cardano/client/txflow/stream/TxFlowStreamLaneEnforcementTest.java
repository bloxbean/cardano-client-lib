package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mechanical lane funding-scope enforcement (ADR 0004 Decision 2: a lane
 * "materializes and validates" its funding scope).
 */
class TxFlowStreamLaneEnforcementTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String OTHER = "addr_test1vpqother";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String SENDER_REF = "account://sender";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void absentFundingSourceIsMaterializedOntoDefensiveCopy() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxPlan callerPlan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)));
        Tx callerTx = (Tx) callerPlan.getTxs().get(0);
        try (TxFlowStream stream = addressLaneBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromTxPlan("pay-1", callerPlan));

            FlowStep dispatched = gateway.started.get(0).getDefinition().getSteps().get(0);
            Tx dispatchedTx = (Tx) dispatched.getTxPlan().getTxs().get(0);
            assertEquals(SENDER, dispatchedTx.getSender(),
                    "the dispatched definition must carry the lane funding source");
            assertNotSame(callerPlan, dispatched.getTxPlan(),
                    "materialization must work on a defensive copy");
            assertNull(callerTx.getSender(), "the caller's plan must never be mutated");

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void absentFundingSourceIsMaterializedAsFundingRefOnRefLane() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxPlan callerPlan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)));
        try (TxFlowStream stream = refLaneBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromTxPlan("pay-1", callerPlan));

            var request = gateway.started.get(0);
            Tx dispatchedTx = (Tx) request.getDefinition().getSteps().get(0)
                    .getTxPlan().getTxs().get(0);
            assertEquals(SENDER_REF, dispatchedTx.getFromRef());
            assertNull(dispatchedTx.getSender());
            assertEquals(List.of("ref:" + SENDER_REF),
                    List.copyOf(request.getSpendingResources()));
            assertNull(((Tx) callerPlan.getTxs().get(0)).getFromRef(),
                    "the caller's plan must never be mutated");

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void matchingFundingSourcePassesWithoutCopying() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxPlan callerPlan = TxPlan.from(
                new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER));
        try (TxFlowStream stream = addressLaneBuilder(gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromTxPlan("pay-1", callerPlan));

            FlowStep dispatched = gateway.started.get(0).getDefinition().getSteps().get(0);
            assertSame(callerPlan, dispatched.getTxPlan(),
                    "a matching source passes through untouched");
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void differentFundingAddressFailsTypedSettledAndUnregistered() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = addressLaneBuilder(gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromTxPlan("pay-1",
                    TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(OTHER))));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            TxStreamException error = assertInstanceOf(TxStreamException.class,
                    outcome.getError());
            assertEquals("TXSTREAM_LANE_SCOPE_VIOLATION", error.getCode());
            assertTrue(error.getMessage().contains(OTHER));
            assertTrue(store.calls.isEmpty(), "the item must never be registered");
            assertTrue(gateway.started.isEmpty(), "the engine must never be invoked");
            assertTrue(receipt.executionId().isEmpty());
            stream.drain();
        }
    }

    @Test
    void fundingRefOnAddressLaneFailsTyped() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = addressLaneBuilder(gateway).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromTxPlan("pay-1",
                    TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5))
                            .fromRef(SENDER_REF))));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_LANE_SCOPE_VIOLATION",
                    assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            assertTrue(store.calls.isEmpty());
            assertTrue(gateway.started.isEmpty());
        }
    }

    @Test
    void flowStepPlanIsSubjectToTheSameEnforcement() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = addressLaneBuilder(gateway).build()) {
            stream.start();
            FlowStep step = FlowStep.builder("caller-step")
                    .withTxPlan(TxPlan.from(
                            new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(OTHER)))
                    .build();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromFlowStep("step-item", step));

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_LANE_SCOPE_VIOLATION",
                    assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TxFlowStream.Builder addressLaneBuilder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxFlowStream.Builder refLaneBuilder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofFundingRef("payouts-lane", SENDER_REF))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }
}
