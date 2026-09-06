package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TxFlowStreamPreviewContractTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";

    @Test
    void nextLaneExecutionWaitsForBackendIndexingWithoutResubmittingPreviousPayment() {
        StubEngineGateway gateway = new StubEngineGateway();
        AtomicInteger checks = new AtomicInteger();
        gateway.outputVisibility = hash -> {
            assertEquals("previous-hash", hash);
            assertEquals(1, gateway.started.size());
            return checks.incrementAndGet() >= 3;
        };
        TxFlowStream stream = new TxFlowStream.Builder("visibility", gateway)
                .executor(Runnable::run).backendVisibility(Duration.ofSeconds(1), Duration.ofMillis(1))
                .open();
        try {
            stream.submit("first", TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER)));
            gateway.lastHandle().completeConfirmed(StreamIdentities.GENERATED_STEP_ID, "previous-hash");
            stream.submit("next", TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(2)).from(SENDER)));
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
