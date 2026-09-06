package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxFlowStreamDurableRedeliveryTest {
    private static final String STEP = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void identicalRedeliveryAttachesAfterEvictionAndRestartWithoutChangingCountersOrExecuting() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.durable = true;
        TxStreamStateStore store = TxStreamStateStore.inMemoryDurable();
        try (TxFlowStream stream = builder(gateway, store).maxRetainedSettledItems(1).open()) {
            for (String id : List.of("one", "two")) {
                stream.submit(id, plan(2));
                gateway.lastHandle().completeConfirmed(STEP, "hash-" + id);
            }
            assertAttached(stream, "one");
            assertEquals(2, stream.getStats().acceptedItemCount());
            assertEquals(2, stream.getStats().confirmedItemCount());
            assertEquals(EmitResult.Status.CONFLICT, stream.trySubmit("one", plan(3)).getStatus());
        }
        try (TxFlowStream restarted = builder(gateway, store).open()) {
            assertAttached(restarted, "one");
            assertEquals(0, restarted.getStats().acceptedItemCount());
            assertEquals(0, restarted.getStats().confirmedItemCount());
        }
        assertEquals(2, gateway.started.size());
    }

    @Test
    void publicReadAndRedeliveryHydrateRemoteUncertaintyAndRepairFromEngineTruth() {
        for (boolean useRedelivery : new boolean[] {false, true}) {
            StubEngineGateway gateway = new StubEngineGateway();
            gateway.durable = true;
            TxStreamStateStore store = TxStreamStateStore.inMemoryDurable();
            try (TxFlowStream reader = builder(gateway, store).open();
                 TxFlowStream writer = builder(gateway, store).open()) {
                TxStreamReceipt original = writer.submit("one", plan(2));
                StubEngineGateway.StubHandle handle = gateway.lastHandle();
                handle.submittedEvent(STEP, "hash-one");
                handle.complete(new FlowExecutionResult(handle.executionId(), "fp", FlowExecutionState.FAILED,
                        List.of(FlowStepResult.submissionPendingAt(STEP, "hash-one", List.of(), List.of(),
                                new IllegalStateException("uncertain"), StubEngineGateway.NOW)),
                        null, StubEngineGateway.NOW, StubEngineGateway.NOW));
                assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, original.current().getStatus());
                TxStreamReceipt attached = null;
                if (useRedelivery) {
                    EmitResult redelivery = reader.trySubmit("one", plan(2));
                    assertEquals(EmitResult.Status.DUPLICATE_ATTACHED, redelivery.getStatus());
                    attached = redelivery.getReceipt();
                    assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, attached.awaitSettled().getStatus());
                }
                gateway.putSnapshot(original.executionId().orElseThrow(), FlowExecutionState.COMPLETED);
                assertEquals(TxStreamItemStatus.CONFIRMED, reader.awaitResolution("one",
                        Duration.ofSeconds(1), Duration.ofMillis(1)).getStatus());
                if (attached != null) assertEquals(TxStreamItemStatus.CONFIRMED, attached.current().getStatus());
                assertEquals(TxStreamItemStatus.CONFIRMED, store.getItem("durable", "one").orElseThrow().getStatus());
                assertEquals(0, reader.getStats().acceptedItemCount());
                assertEquals(0, reader.getStats().recoveryRequiredItemCount());
                assertEquals(1, gateway.started.size());
            }
        }
    }

    @Test
    void redeliveryOfRegisteredButUnplannedWorkNeverStartsAnotherExecution() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.durable = true;
        TxStreamStateStore store = TxStreamStateStore.inMemoryDurable();
        TxFlowStream writer = builder(gateway, store).window(WindowPolicy.count(2)).open();
        try (TxFlowStream reader = builder(gateway, store).open()) {
            writer.submit("one", plan(2));
            EmitResult result = reader.trySubmit("one", plan(2));
            assertEquals(EmitResult.Status.REJECTED, result.getStatus());
            assertEquals(TxStreamCodes.REGISTRATION_INCOMPLETE, result.getRejection().getCode());
            assertTrue(gateway.started.isEmpty());
        } finally {
            writer.abort("test cleanup");
        }
    }

    private void assertAttached(TxFlowStream stream, String itemId) {
        EmitResult result = stream.trySubmit(itemId, plan(2));
        assertEquals(EmitResult.Status.DUPLICATE_ATTACHED, result.getStatus());
        assertEquals("hash-" + itemId, result.getReceipt().awaitConfirmed().getTransactionHash());
    }

    private TxFlowStream.Builder builder(StubEngineGateway gateway, TxStreamStateStore store) {
        return new TxFlowStream.Builder("durable", gateway).executor(Runnable::run).stateStore(store);
    }

    private TxPlan plan(int ada) {
        return TxPlan.from(new Tx().payToAddress("addr_test1vpqreceiver", Amount.ada(ada))
                .from("addr_test1vpqsender"));
    }
}
