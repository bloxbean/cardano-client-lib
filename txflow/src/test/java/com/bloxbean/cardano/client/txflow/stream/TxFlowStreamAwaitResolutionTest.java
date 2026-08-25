package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxFlowStreamAwaitResolutionTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void awaitConfirmedClassifiesUncertaintyWithoutHiddenReconciliation() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder(gateway).open()) {
            TxStreamReceipt receipt = stream.submit("pay-1", plan());
            driveRecoveryRequired(gateway, "tx-uncertain");
            int readsBefore = gateway.snapshotReads.get();

            TxStreamUncertainException uncertain = assertThrows(
                    TxStreamUncertainException.class, receipt::awaitConfirmed);

            assertEquals(readsBefore, gateway.snapshotReads.get());
            assertEquals("tx-uncertain", uncertain.result().getTransactionHash());
            assertEquals(1, gateway.started.size());
        }
    }

    @Test
    void awaitResolutionPollsUntilConfirmedWithoutResubmitting() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder(gateway).open()) {
            TxStreamReceipt receipt = stream.submit("pay-1", plan());
            driveRecoveryRequired(gateway, "tx-uncertain");
            String executionId = receipt.executionId().orElseThrow();
            Thread repair = new Thread(() -> {
                while (gateway.snapshotReads.get() < 2) {
                    Thread.yield();
                }
                gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED);
            });
            repair.start();

            TxStreamItemResult resolved = stream.awaitResolution("pay-1",
                    Duration.ofSeconds(2), Duration.ofMillis(5));
            repair.join(1_000);

            assertFalse(repair.isAlive());
            assertEquals(TxStreamItemStatus.CONFIRMED, resolved.getStatus());
            assertEquals("tx-uncertain", resolved.getTransactionHash());
            assertTrue(gateway.snapshotReads.get() >= 3);
            assertEquals(1, gateway.started.size(),
                    "resolution may inspect but must never resubmit the transaction");
        }
    }

    @Test
    void awaitResolutionRaisesConclusiveFailedAndCancelledOutcomes() {
        for (FlowExecutionState state : List.of(
                FlowExecutionState.FAILED, FlowExecutionState.CANCELLED)) {
            StubEngineGateway gateway = new StubEngineGateway();
            try (TxFlowStream stream = builder(gateway).open()) {
                TxStreamReceipt receipt = stream.submit("pay-" + state, plan());
                driveRecoveryRequired(gateway, "tx-" + state);
                gateway.putSnapshot(receipt.executionId().orElseThrow(), state);

                if (state == FlowExecutionState.FAILED) {
                    TxStreamFailedException failed = assertThrows(TxStreamFailedException.class,
                            () -> stream.awaitResolution(receipt.itemId(), Duration.ofSeconds(1),
                                    Duration.ofMillis(5)));
                    assertSame(receipt.current(), failed.result());
                } else {
                    TxStreamCancelledException cancelled = assertThrows(
                            TxStreamCancelledException.class,
                            () -> stream.awaitResolution(receipt.itemId(), Duration.ofSeconds(1),
                                    Duration.ofMillis(5)));
                    assertSame(receipt.current(), cancelled.result());
                }
                assertEquals(1, gateway.started.size());
            }
        }
    }

    @Test
    void awaitResolutionTimeoutCarriesLatestProjectionAndDoesNotCancelWork() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder(gateway).open()) {
            TxStreamReceipt receipt = stream.submit("pay-1", plan());
            driveRecoveryRequired(gateway, "tx-uncertain");

            TxStreamTimeoutException timeout = assertThrows(TxStreamTimeoutException.class,
                    () -> stream.awaitResolution("pay-1", Duration.ofMillis(20),
                            Duration.ofMillis(5)));

            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    timeout.result().getStatus());
            assertEquals("tx-uncertain", timeout.result().getTransactionHash());
            assertFalse(gateway.lastHandle().cancelRequested.get());
            assertEquals(1, gateway.started.size());
            assertTrue(gateway.snapshotReads.get() >= 2);
        }
    }

    @Test
    void awaitResolutionValidatesInputsAndRejectsUnknownOrPrematureItems() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder(gateway).open()) {
            assertThrows(NullPointerException.class,
                    () -> stream.awaitResolution(null, Duration.ofSeconds(1),
                            Duration.ofMillis(1)));
            assertThrows(NullPointerException.class,
                    () -> stream.awaitResolution("pay-1", null, Duration.ofMillis(1)));
            assertThrows(IllegalArgumentException.class,
                    () -> stream.awaitResolution("pay-1", Duration.ZERO,
                            Duration.ofMillis(1)));
            assertThrows(IllegalArgumentException.class,
                    () -> stream.awaitResolution("pay-1", Duration.ofSeconds(1),
                            Duration.ofSeconds(-1)));
            assertEquals("TXSTREAM_ITEM_UNKNOWN", assertThrows(TxStreamException.class,
                    () -> stream.awaitResolution("unknown", Duration.ofSeconds(1),
                            Duration.ofMillis(1))).getCode());

            stream.submit("pay-1", plan());
            assertEquals("TXSTREAM_ITEM_FAILED", assertThrows(TxStreamException.class,
                    () -> stream.awaitResolution("pay-1", Duration.ofSeconds(1),
                            Duration.ofMillis(1))).getCode());
            gateway.lastHandle().completeCancelled();
        }
    }

    @Test
    void awaitResolutionRestoresInterruptionAndStopsPolling() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        TxFlowStream stream = builder(gateway).open();
        try {
            stream.submit("pay-1", plan());
            driveRecoveryRequired(gateway, "tx-uncertain");
            AtomicReference<TxStreamException> failure = new AtomicReference<>();
            AtomicBoolean interruptedFlag = new AtomicBoolean();
            Thread waiter = new Thread(() -> {
                try {
                    stream.awaitResolution("pay-1", Duration.ofSeconds(5),
                            Duration.ofSeconds(1));
                } catch (TxStreamException exception) {
                    failure.set(exception);
                    interruptedFlag.set(Thread.currentThread().isInterrupted());
                }
            });

            waiter.start();
            while (gateway.snapshotReads.get() == 0) {
                Thread.yield();
            }
            waiter.interrupt();
            waiter.join(TimeUnit.SECONDS.toMillis(1));

            assertFalse(waiter.isAlive());
            assertEquals("TXSTREAM_INTERRUPTED", failure.get().getCode());
            assertTrue(interruptedFlag.get());
            assertEquals(1, gateway.started.size());
        } finally {
            stream.close();
        }
    }

    private TxFlowStream.Builder builder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private static TxPlan plan() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER));
    }

    private static void driveRecoveryRequired(StubEngineGateway gateway, String hash) {
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
