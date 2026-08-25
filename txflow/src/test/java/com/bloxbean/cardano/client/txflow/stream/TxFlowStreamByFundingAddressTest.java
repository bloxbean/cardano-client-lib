package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 2c / ADR 0005 {@link LanePolicy#byFundingSource()}: the stream derives each
 * item's lane from its transaction's own funding source, so items from
 * different senders lane concurrently while items from the same sender
 * serialize — with no resolver, no per-item lane name, and no bootstrap.
 */
class TxFlowStreamByFundingAddressTest {
    private static final String SENDER_A = "addr_test1vpqsendera";
    private static final String SENDER_B = "addr_test1vpqsenderb";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void byFundingSourceIsTheDefaultAndOldNameForwardsToIt() {
        assertEquals(LanePolicy.byFundingSource().mode(),
                LanePolicy.byFundingAddress().mode());

        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(fromItem("default-1", SENDER_A));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-default");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals(SENDER_A, receipt.current().getLaneName());
        }
    }

    @Test
    void bothFundingFormsFailWithAmbiguousDiagnostic() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            Tx ambiguous = new Tx().from(SENDER_A)
                    .payToAddress(RECEIVER, Amount.ada(1.5));
            // The public Tx builder already prevents this invalid shape. Set
            // the field reflectively to prove the stream's defensive boundary
            // still rejects malformed/deserialized plans carrying both forms.
            Field fromRef = Tx.class.getDeclaredField("fromRef");
            fromRef.setAccessible(true);
            fromRef.set(ambiguous, "account://sender");

            TxStreamException result = assertThrows(TxStreamException.class,
                    () -> stream.submit(
                            TxWorkItem.fromTxPlan("ambiguous", TxPlan.from(ambiguous))));

            assertEquals("TXSTREAM_LANE_AMBIGUOUS", result.getCode());
            assertTrue(gateway.started.isEmpty());
        }
    }

    @Test
    void addressAndReferenceForOneWalletRemainDistinctSyntacticLanes() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt address = stream.submit(fromItem("address", SENDER_A));
            TxPlan refPlan = TxPlan.from(new Tx().fromRef("account://sender-a")
                    .payToAddress(RECEIVER, Amount.ada(1.5)));
            TxStreamReceipt ref = stream.submit(TxWorkItem.fromTxPlan("reference", refPlan));

            assertEquals(2, gateway.started.size(),
                    "addr: and ref: identities are intentionally distinct and may contend if mixed");
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-address");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-reference");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    address.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    ref.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void differentSendersLaneConcurrentlyWhileTheSameSenderStaysSerialFifo() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = byFundingSource(gateway).build()) {
            stream.start();
            TxStreamReceipt a1 = stream.submit(fromItem("a-1", SENDER_A));
            TxStreamReceipt a2 = stream.submit(fromItem("a-2", SENDER_A));
            TxStreamReceipt b1 = stream.submit(fromItem("b-1", SENDER_B));

            assertEquals(2, gateway.started.size(),
                    "one execution per funding source may be in flight");
            assertEquals(2, stream.getStats().inFlightCount());
            assertEquals(1, stream.getStats().pendingBufferSize(),
                    "the same-sender follower must queue");
            assertEquals(List.of("addr:" + SENDER_A),
                    List.copyOf(gateway.started.get(0).getSpendingResources()));
            assertEquals(List.of("addr:" + SENDER_B),
                    List.copyOf(gateway.started.get(1).getSpendingResources()));
            // The lane label is the funding source string.
            assertEquals(SENDER_A, a1.current().getLaneName());
            assertEquals(SENDER_B, b1.current().getLaneName());

            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-a1");
            assertEquals(3, gateway.started.size(),
                    "completing the sender's head dispatches the same sender's next item");
            assertEquals(List.of("addr:" + SENDER_A),
                    List.copyOf(gateway.started.get(2).getSpendingResources()));

            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-b1");
            gateway.handles.get(2).completeConfirmed(STEP_ID, "tx-a2");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a1.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a2.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b1.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void itemsFromDifferentSendersDispatchConcurrentlyOnRealExecutor() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        CountDownLatch bothStartsEntered = new CountDownLatch(2);
        CountDownLatch bothHandlesCreated = new CountDownLatch(2);
        gateway.startHook = () -> {
            bothStartsEntered.countDown();
            try {
                if (!bothStartsEntered.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("second sender's dispatch never arrived");
                }
            } catch (InterruptedException interrupt) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupt);
            }
        };
        gateway.handleCreatedHook = bothHandlesCreated::countDown;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = byFundingSource(gateway).executor(pool).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(fromItem("a-1", SENDER_A));
            TxStreamReceipt second = stream.submit(fromItem("b-1", SENDER_B));

            assertTrue(bothStartsEntered.await(10, TimeUnit.SECONDS),
                    "dispatch of different funding sources must run concurrently");
            assertTrue(bothHandlesCreated.await(10, TimeUnit.SECONDS));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            assertEquals(TxStreamItemStatus.CONFIRMED, first.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, second.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void itemWithNoFundingSourceIsRejectedUnderivableAndRetainedNowhere() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = byFundingSource(gateway).build()) {
            stream.start();
            // No from / from_ref: the lane cannot be derived.
            TxPlan sourceless = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1)));
            TxStreamException outcome = assertThrows(TxStreamException.class,
                    () -> stream.submit(TxWorkItem.fromTxPlan("no-src", sourceless)));
            assertEquals("TXSTREAM_LANE_UNDERIVABLE", outcome.getCode());
            assertTrue(gateway.started.isEmpty(), "an underivable item never reaches the engine");
            assertTrue(stream.getItemStatus("no-src").isEmpty(),
                    "a rejected item is retained nowhere");
            assertEquals("TXSTREAM_LANE_UNDERIVABLE", assertThrows(TxStreamException.class,
                    () -> stream.submit(TxWorkItem.fromTxPlan("no-src", sourceless))).getCode());
        }
    }

    @Test
    void fromRefBackedItemsLaneByTheirFundingReference() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = byFundingSource(gateway).build()) {
            stream.start();
            TxPlan plan = TxPlan.from(
                    new Tx().fromRef("account://treasury").payToAddress(RECEIVER, Amount.ada(2)));
            TxStreamReceipt receipt = stream.submit(TxWorkItem.fromTxPlan("ref-1", plan));

            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-ref");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals(List.of("ref:account://treasury"),
                    List.copyOf(gateway.started.get(0).getSpendingResources()));
            assertEquals("account://treasury", receipt.current().getLaneName());
        }
    }

    @Test
    void executionIdIsClaimKeyDerivedAndUnperturbedByTheDerivedLane() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = byFundingSource(gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.builder("pay-42")
                    .withTxPlan(fromPlan(SENDER_A))
                    .withIdempotencyKey("order-42")
                    .build());

            String expected = StreamIdentities.executionId(
                    StreamIdentities.namespace("payouts"), "order-42");
            // Settle first so a later assertion failure never wedges close().
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals(expected, receipt.executionId().orElseThrow(),
                    "the execution id is claim-derived; the lane must not perturb it");
            assertEquals(expected, gateway.started.get(0).getExecutionId());
        }
    }

    @Test
    void namingALaneThatDisagreesWithTheFundingSourceFailsMismatchButAgreeingNameIsAccepted() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = byFundingSource(gateway).build()) {
            stream.start();
            // A lane name that disagrees with the derived (from) lane fails typed.
            TxStreamException mismatch = assertThrows(TxStreamException.class,
                    () -> stream.submit(TxWorkItem.builder("bad-1")
                            .withTxPlan(fromPlan(SENDER_A))
                            .withLane("some-other-lane")
                            .build()));
            assertEquals("TXSTREAM_LANE_MISMATCH", mismatch.getCode());
            assertTrue(gateway.started.isEmpty());

            // A lane name equal to the funding source is accepted.
            TxStreamReceipt ok = stream.submit(TxWorkItem.builder("ok-1")
                    .withTxPlan(fromPlan(SENDER_A))
                    .withLane(SENDER_A)
                    .build());
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    ok.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void noLaneResolverIsRequiredForByFundingSource() {
        StubEngineGateway gateway = new StubEngineGateway();
        // build() must not demand a laneResolver for byFundingSource().
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.byFundingSource())
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(fromItem("a-1", SENDER_A));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TxFlowStream.Builder byFundingSource(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.byFundingSource())
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxPlan fromPlan(String sender) {
        return TxPlan.from(new Tx().from(sender).payToAddress(RECEIVER, Amount.ada(1.5)));
    }

    private TxWorkItem fromItem(String itemId, String sender) {
        return TxWorkItem.fromTxPlan(itemId, fromPlan(sender));
    }
}
