package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Read-through reconciliation, transition-table enforcement, and state-store
 * contract behavior.
 */
class TxFlowStreamRecoveryTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void getItemStatusRepairsRecoveryRequiredFromAuthoritativeSnapshot() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxFlowStreamTest.RecordingListener listener = new TxFlowStreamTest.RecordingListener();
        try (TxFlowStream stream = builder("payouts", gateway).eventListener(listener).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            String executionId = receipt.executionId().orElseThrow();
            completeRecoveryRequired(gateway, "tx-uncertain");
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    receipt.completion().toCompletableFuture().join().getStatus());

            // Nothing to repair while the engine has no authoritative answer.
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus());

            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED);
            TxStreamItemResult repaired = stream.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repaired.getStatus());
            assertEquals("tx-uncertain", repaired.getTransactionHash(),
                    "hash must survive the repair");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    listener.updates.get(listener.updates.size() - 1).getStatus(),
                    "the repair must be emitted to the listener");
            // The settled promise keeps its point-in-time outcome.
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, receipt.current().getStatus());
        }
    }

    @Test
    void reconcileRepairsRecoveryRequiredToFailed() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            completeRecoveryRequired(gateway, "tx-uncertain");

            gateway.putSnapshot(receipt.executionId().orElseThrow(), FlowExecutionState.FAILED);
            TxStreamItemResult repaired = stream.reconcile("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.FAILED, repaired.getStatus());
            assertEquals("tx-uncertain", repaired.getTransactionHash());
        }
    }

    @Test
    void reconcileLeavesItemUntouchedWhileSnapshotIsNonTerminal() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            completeRecoveryRequired(gateway, "tx-uncertain");

            gateway.putSnapshot(receipt.executionId().orElseThrow(), FlowExecutionState.RUNNING);
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    stream.reconcile("pay-1").orElseThrow().getStatus());
            assertTrue(stream.reconcile("missing").isEmpty());
        }
    }

    @Test
    void finalStatusesAreImmutableAgainstLateAuthoritativeRepairs() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());

            gateway.putSnapshot(receipt.executionId().orElseThrow(), FlowExecutionState.FAILED);
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.reconcile("pay-1").orElseThrow().getStatus(),
                    "a final projection can never be overwritten");
        }
    }

    @Test
    void liveTransitionTableRejectsIllegalEdges() {
        assertTrue(ItemProjection.allowsLive(TxStreamItemStatus.ACCEPTED, TxStreamItemStatus.PLANNED));
        assertTrue(ItemProjection.allowsLive(TxStreamItemStatus.PLANNED, TxStreamItemStatus.SUBMITTED));
        assertTrue(ItemProjection.allowsLive(TxStreamItemStatus.SUBMITTED, TxStreamItemStatus.CONFIRMED));
        assertTrue(ItemProjection.allowsLive(TxStreamItemStatus.RECOVERY_REQUIRED, TxStreamItemStatus.CONFIRMED));
        assertFalse(ItemProjection.allowsLive(TxStreamItemStatus.ACCEPTED, TxStreamItemStatus.SUBMITTED));
        assertFalse(ItemProjection.allowsLive(TxStreamItemStatus.PLANNED, TxStreamItemStatus.CONFIRMED));
        assertFalse(ItemProjection.allowsLive(TxStreamItemStatus.CONFIRMED, TxStreamItemStatus.FAILED));
        assertFalse(ItemProjection.allowsLive(TxStreamItemStatus.FAILED, TxStreamItemStatus.CONFIRMED));
        assertFalse(ItemProjection.allowsLive(TxStreamItemStatus.CANCELLED, TxStreamItemStatus.CONFIRMED));
        assertFalse(ItemProjection.allowsLive(TxStreamItemStatus.RECOVERY_REQUIRED, TxStreamItemStatus.SUBMITTED));
    }

    @Test
    void inMemoryStoreEnforcesRegistrationAndSequenceContract() {
        TxStreamStateStore store = TxStreamStateStore.inMemory();
        TxStreamItemRecord record = new TxStreamItemRecord("item-1", "key-1", "lane",
                "fingerprint", StubEngineGateway.NOW);
        store.registerItem(record);
        assertThrows(TxStreamDuplicateItemException.class, () -> store.registerItem(record));

        TxStreamBinding binding = new TxStreamBinding("txs-1", "flow-1", "item", "lane");
        assertThrows(TxStreamException.class, () -> store.bind("unknown", binding));
        assertThrows(TxStreamException.class,
                () -> store.confirmBinding("item-1", BindingOutcome.CREATED));
        store.bind("item-1", binding);
        store.confirmBinding("item-1", BindingOutcome.CREATED);

        TxStreamItemResult submitted = TxStreamItemResult
                .builder("s", "item-1", TxStreamItemStatus.SUBMITTED)
                .transactionHash("tx").updatedAt(StubEngineGateway.NOW).build();
        TxStreamItemResult stale = TxStreamItemResult
                .builder("s", "item-1", TxStreamItemStatus.PLANNED)
                .updatedAt(StubEngineGateway.NOW).build();
        assertTrue(store.projectItem(submitted, 3));
        assertFalse(store.projectItem(stale, 2), "stale sequence must be rejected");
        assertFalse(store.projectItem(stale, 3), "same sequence must be rejected");
        assertEquals(TxStreamItemStatus.SUBMITTED,
                store.getItem("s", "item-1").orElseThrow().getStatus());
        assertTrue(store.getItem("other-stream", "item-1").isEmpty());
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
                TxPlan.from(new Tx().payToAddress("addr_test1vpqreceiver", Amount.ada(1.5))
                        .from(SENDER)));
    }

    private void completeRecoveryRequired(StubEngineGateway gateway, String hash) {
        StubEngineGateway.StubHandle handle = gateway.lastHandle();
        handle.submittedEvent(STEP_ID, hash);
        handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                FlowExecutionState.FAILED,
                List.of(FlowStepResult.submissionPendingAt(STEP_ID, hash, List.of(), List.of(),
                        new IllegalStateException("confirmation abandoned"),
                        StubEngineGateway.NOW)),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW));
    }
}
