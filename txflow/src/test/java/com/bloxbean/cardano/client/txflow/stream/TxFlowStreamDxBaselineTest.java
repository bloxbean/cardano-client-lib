package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 0 characterization for ADR 0005. These tests deliberately pin the
 * pre-DX contracts that later milestones either preserve or intentionally
 * update. Keep an expectation change explicit in the milestone that changes
 * the corresponding contract.
 */
class TxFlowStreamDxBaselineTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void builderCurrentlyRequiresAnExplicitLaneAndExecutor() {
        StubEngineGateway gateway = new StubEngineGateway();

        IllegalStateException missingLane = assertThrows(IllegalStateException.class,
                () -> new TxFlowStream.Builder("payouts", gateway)
                        .executor(Runnable::run)
                        .build());
        assertTrue(missingLane.getMessage().contains("A lane policy is required"));

        NullPointerException missingExecutor = assertThrows(NullPointerException.class,
                () -> new TxFlowStream.Builder("payouts", gateway)
                        .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                        .build());
        assertTrue(missingExecutor.getMessage().contains("executor must be supplied"));
    }

    @Test
    void explicitConfigurationPreservesListenerOrderingAndCallerExecutorOwnership() {
        StubEngineGateway gateway = new StubEngineGateway();
        DirectExecutorService executor = new DirectExecutorService();
        List<String> events = new ArrayList<>();
        TxStreamEventListener listener = new TxStreamEventListener() {
            @Override
            public void onStreamStarted(String streamId) {
                events.add("started");
            }

            @Override
            public void onItemAccepted(TxWorkItem item, TxStreamReceipt receipt) {
                events.add("accepted");
            }

            @Override
            public void onItemUpdated(TxStreamItemResult result) {
                events.add("updated:" + result.getStatus());
            }

            @Override
            public void onStreamDrained(String streamId) {
                events.add("drained");
            }

            @Override
            public void onStreamClosed(String streamId) {
                events.add("closed");
            }
        };

        TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(executor)
                .eventListener(listener)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build();
        stream.start();
        TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
        gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
        assertEquals(TxStreamItemStatus.CONFIRMED,
                receipt.completion().toCompletableFuture().join().getStatus());
        stream.drain();
        stream.close();

        assertBefore(events, "started", "accepted");
        assertBefore(events, "accepted", "updated:ACCEPTED");
        assertBefore(events, "updated:CONFIRMED", "drained");
        assertBefore(events, "drained", "closed");
        assertFalse(executor.isShutdown(),
                "an explicitly supplied executor remains caller-owned after stream close");
        executor.shutdown();
    }

    @Test
    void portabilityValidationCurrentlyReturnsAnAcceptedFailedReceipt() {
        StubEngineGateway gateway = new StubEngineGateway();
        List<String> acceptedItems = new ArrayList<>();
        TxStreamEventListener listener = new TxStreamEventListener() {
            @Override
            public void onItemAccepted(TxWorkItem item, TxStreamReceipt receipt) {
                acceptedItems.add(item.getItemId());
            }
        };

        try (TxFlowStream stream = builder(gateway).eventListener(listener).build()) {
            stream.start();
            TxWorkItem nonPortable = TxWorkItem.fromFlowStep("factory-item",
                    FlowStep.builder("factory-step")
                            .withTxContext(quickTxBuilder -> null)
                            .build());

            TxStreamReceipt receipt = stream.submit(nonPortable);
            TxStreamItemResult result = receipt.completion().toCompletableFuture().join();

            assertEquals(TxStreamItemStatus.FAILED, result.getStatus());
            assertEquals("TXSTREAM_NON_PORTABLE_ITEM",
                    assertInstanceOf(TxStreamException.class, result.getError()).getCode());
            assertEquals(List.of("factory-item"), acceptedItems);
            assertEquals(1, stream.getStats().acceptedItemCount());
            assertEquals(1, stream.getStats().failedItemCount());
            assertSame(receipt, stream.submit(nonPortable),
                    "the current retained validation failure attaches on redelivery");
            assertTrue(gateway.started.isEmpty());
        }
    }

    private TxFlowStream.Builder builder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private static TxWorkItem planItem(String itemId) {
        TxPlan plan = TxPlan.from(new Tx()
                .payToAddress(RECEIVER, Amount.ada(1.5))
                .from(SENDER));
        return TxWorkItem.fromTxPlan(itemId, plan);
    }

    private static void assertBefore(List<String> events, String first, String second) {
        int firstIndex = events.indexOf(first);
        int secondIndex = events.indexOf(second);
        assertTrue(firstIndex >= 0, () -> "missing event " + first + ": " + events);
        assertTrue(secondIndex >= 0, () -> "missing event " + second + ": " + events);
        assertTrue(firstIndex < secondIndex,
                () -> first + " must precede " + second + ": " + events);
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new IllegalStateException("executor is shut down");
            }
            command.run();
        }
    }
}
