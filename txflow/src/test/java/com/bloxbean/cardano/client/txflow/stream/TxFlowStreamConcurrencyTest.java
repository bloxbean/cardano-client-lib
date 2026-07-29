package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic multi-threaded coverage for the 1A review pass: submission
 * races, the attach-vs-failed-registration race (BUG-2), and systemic executor
 * failure (BUG-1). Coordination uses latches on the stream side only — engine
 * completions stay scripted.
 */
class TxFlowStreamConcurrencyTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    @Test
    void concurrentIdenticalSubmitsRegisterOnceAndBothReceiptsSettleConfirmed()
            throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            CountDownLatch go = new CountDownLatch(1);
            Future<TxStreamReceipt> first = pool.submit(() -> {
                go.await(10, TimeUnit.SECONDS);
                return stream.submit(planItem("pay-1"));
            });
            Future<TxStreamReceipt> second = pool.submit(() -> {
                go.await(10, TimeUnit.SECONDS);
                return stream.submit(planItem("pay-1"));
            });
            go.countDown();
            TxStreamReceipt firstReceipt = first.get(10, TimeUnit.SECONDS);
            TxStreamReceipt secondReceipt = second.get(10, TimeUnit.SECONDS);

            assertEquals(1, gateway.started.size(), "exactly one execution may start");
            assertEquals(1, store.calls.stream()
                    .filter(call -> call.startsWith("register:")).count());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED, firstReceipt.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, secondReceipt.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDifferentContentSameIdOneWinsOneGetsTypedConflict() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            CountDownLatch go = new CountDownLatch(1);
            Future<Object> first = pool.submit(() -> submitCatching(stream, go,
                    TxWorkItem.fromTxPlan("pay-1", plan(Amount.ada(1.5)))));
            Future<Object> second = pool.submit(() -> submitCatching(stream, go,
                    TxWorkItem.fromTxPlan("pay-1", plan(Amount.ada(99)))));
            go.countDown();
            Object firstOutcome = first.get(10, TimeUnit.SECONDS);
            Object secondOutcome = second.get(10, TimeUnit.SECONDS);

            long receipts = countInstances(TxStreamReceipt.class, firstOutcome, secondOutcome);
            long conflicts = countInstances(TxStreamDuplicateItemException.class,
                    firstOutcome, secondOutcome);
            assertEquals(1, receipts, "exactly one submit wins");
            assertEquals(1, conflicts, "the other must get the typed conflict");
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            stream.drain();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void executorRejectionMidStreamFailsAllPendingItemsAndDrainReturns() {
        TxFlowStreamFailurePathTest.TogglingExecutor executor =
                new TxFlowStreamFailurePathTest.TogglingExecutor();
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(executor)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt active = stream.submit(planItem("pay-1"));
            TxStreamReceipt bufferedFirst = stream.submit(planItem("pay-2"));
            TxStreamReceipt bufferedSecond = stream.submit(planItem("pay-3"));

            executor.reject = true;
            // Completing the in-flight item re-schedules the pump, which hits
            // the rejecting executor and takes down the dispatcher.
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");

            assertFalse(stream.isHealthy());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    active.completion().toCompletableFuture().join().getStatus());
            for (TxStreamReceipt receipt : List.of(bufferedFirst, bufferedSecond)) {
                TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
                assertEquals("TXSTREAM_UNHEALTHY",
                        assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            }
            stream.drain();
            assertEquals(EmitResult.Status.CLOSED,
                    stream.trySubmit(planItem("pay-4")).getStatus());
        }
    }

    @Test
    void attachRacingFailedRegistrationSettlesAttachedReceiptTyped() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        store.registerEntered = new CountDownLatch(1);
        store.registerGate = new CountDownLatch(1);
        store.registerFailure = new IllegalStateException("registry storage down");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            Future<TxStreamException> registrant = pool.submit(() -> {
                try {
                    stream.submit(planItem("pay-1"));
                    return null;
                } catch (TxStreamException expected) {
                    return expected;
                }
            });
            assertTrue(store.registerEntered.await(10, TimeUnit.SECONDS),
                    "registration must be in flight before attaching");

            // Attach while the registration is still blocked in the store.
            TxStreamReceipt attached = stream.submit(planItem("pay-1"));
            assertFalse(attached.completion().toCompletableFuture().isDone());

            store.registerGate.countDown();
            TxStreamException thrown = registrant.get(10, TimeUnit.SECONDS);
            assertNotNull(thrown, "the registering submit must throw typed");
            assertEquals("TXSTREAM_REGISTRATION_FAILED", thrown.getCode());

            // BUG-2: the concurrently attached receipt settles instead of hanging.
            TxStreamItemResult outcome = attached.completion().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_REGISTRATION_FAILED",
                    assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());

            // The failed registration left no residue: a fresh submit works.
            store.registerFailure = null;
            store.registerEntered = null;
            store.registerGate = null;
            TxStreamReceipt retried = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED, retried.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentIdenticalNonPortableSubmitsBothSettleFailedWithoutConflict()
            throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            CountDownLatch go = new CountDownLatch(1);
            Future<TxStreamReceipt> first = pool.submit(() -> {
                go.await(10, TimeUnit.SECONDS);
                return stream.submit(nonPortableItem("bad-item"));
            });
            Future<TxStreamReceipt> second = pool.submit(() -> {
                go.await(10, TimeUnit.SECONDS);
                return stream.submit(nonPortableItem("bad-item"));
            });
            go.countDown();
            for (Future<TxStreamReceipt> future : List.of(first, second)) {
                TxStreamItemResult outcome = future.get(10, TimeUnit.SECONDS)
                        .completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
                assertEquals("TXSTREAM_NON_PORTABLE_ITEM",
                        assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            }
            assertTrue(gateway.started.isEmpty());
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Object submitCatching(TxFlowStream stream, CountDownLatch go, TxWorkItem item)
            throws InterruptedException {
        go.await(10, TimeUnit.SECONDS);
        try {
            return stream.submit(item);
        } catch (TxStreamDuplicateItemException conflict) {
            return conflict;
        }
    }

    private long countInstances(Class<?> type, Object... values) {
        long count = 0;
        for (Object value : values) {
            if (type.isInstance(value)) count++;
        }
        return count;
    }

    private TxFlowStream.Builder builder(String streamId, StubEngineGateway gateway) {
        return new TxFlowStream.Builder(streamId, gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId, plan(Amount.ada(1.5)));
    }

    private TxPlan plan(Amount amount) {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, amount).from(SENDER));
    }

    private TxWorkItem nonPortableItem(String itemId) {
        return TxWorkItem.fromFlowStep(itemId,
                FlowStep.builder("factory-step").withTxContext(quickTxBuilder -> null).build());
    }
}
