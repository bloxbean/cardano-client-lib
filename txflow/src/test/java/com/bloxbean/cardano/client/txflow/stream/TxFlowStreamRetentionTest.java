package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scheduled 1B items: settled-item retention/eviction
 * ({@code maxRetainedSettledItems}), idempotency-key reuse rejection (DEV-4),
 * and the receipt-level event cursor (DEV-2).
 */
class TxFlowStreamRetentionTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    // ------------------------------------------------------------------
    // Retention / eviction
    // ------------------------------------------------------------------

    @Test
    void settledItemsBeyondTheCapAreEvictedFifoFromLiveMapAndStore() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = builder("payouts", gateway)
                .stateStore(store)
                .maxRetainedSettledItems(2)
                .build()) {
            stream.start();
            for (int i = 1; i <= 3; i++) {
                TxStreamReceipt receipt = stream.submit(planItem("pay-" + i));
                gateway.lastHandle().completeConfirmed(STEP_ID, "tx-" + i);
                assertEquals(TxStreamItemStatus.CONFIRMED,
                        receipt.completion().toCompletableFuture().join().getStatus());
            }

            assertTrue(stream.getItemStatus("pay-1").isEmpty(),
                    "the oldest settled item must be evicted FIFO");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.getItemStatus("pay-2").orElseThrow().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.getItemStatus("pay-3").orElseThrow().getStatus());
            assertTrue(store.calls.contains("evict:pay-1"),
                    "eviction must remove the item from the state store too: " + store.calls);

            TxStreamStats stats = stream.getStats();
            assertEquals(3, stats.acceptedItemCount(),
                    "counters are cumulative and unaffected by eviction");
            assertEquals(3, stats.confirmedItemCount());
            assertEquals(3, stats.submittedItemCount());
        }
    }

    @Test
    void unsettledItemsAreNeverEvicted() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway)
                .maxRetainedSettledItems(1)
                .build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(planItem("pay-1"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());

            stream.submit(planItem("pay-2"));           // in flight, unsettled
            stream.submit(planItem("pay-3"));           // buffered, unsettled
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus(),
                    "one settled item is within the cap");

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-2");   // second settle evicts pay-1
            assertTrue(stream.getItemStatus("pay-1").isEmpty());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.getItemStatus("pay-2").orElseThrow().getStatus());
            TxStreamItemResult third = stream.getItemStatus("pay-3").orElseThrow();
            assertTrue(third.getStatus() == TxStreamItemStatus.PLANNED
                            || third.getStatus() == TxStreamItemStatus.SUBMITTED,
                    "an unsettled item must never be evicted; was " + third.getStatus());

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-3");
            stream.drain();
        }
    }

    @Test
    void redeliveryAfterEvictionIsAcceptedFreshWithTheSameDeterministicExecutionId() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway)
                .maxRetainedSettledItems(1)
                .build()) {
            stream.start();
            TxStreamReceipt original = stream.submit(planItem("pay-1"));
            String executionId = original.executionId().orElseThrow();
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            original.completion().toCompletableFuture().join();

            TxStreamReceipt second = stream.submit(planItem("pay-2"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-2");
            second.completion().toCompletableFuture().join();
            assertTrue(stream.getItemStatus("pay-1").isEmpty(), "pay-1 must be evicted");

            // Redelivery of the evicted item re-registers and re-dispatches;
            // the engine's idempotency claim (same deterministic execution id)
            // protects against a duplicate on-chain transaction.
            TxStreamReceipt redelivered = stream.submit(planItem("pay-1"));
            assertNotSame(original, redelivered, "an evicted item is accepted fresh");
            assertEquals(executionId, redelivered.executionId().orElseThrow(),
                    "the claim-derived execution id is stable across eviction");
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    redelivered.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // DEV-4: idempotency-key reuse under a different item id
    // ------------------------------------------------------------------

    @Test
    void idempotencyKeyReuseUnderDifferentItemIdIsTypedConflict() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt owner = stream.submit(TxWorkItem.builder("pay-1")
                    .withTxPlan(plan()).withIdempotencyKey("order-1").build());

            TxWorkItem reusing = TxWorkItem.builder("pay-2")
                    .withTxPlan(plan()).withIdempotencyKey("order-1").build();
            TxStreamException thrown = assertThrows(TxStreamException.class,
                    () -> stream.submit(reusing));
            assertEquals("TXSTREAM_IDEMPOTENCY_KEY_REUSE", thrown.getCode());
            assertTrue(thrown.getMessage().contains("pay-1"),
                    "the conflict must name the owning item: " + thrown.getMessage());

            EmitResult emit = stream.trySubmit(reusing);
            assertEquals(EmitResult.Status.REJECTED, emit.getStatus());
            assertEquals("TXSTREAM_IDEMPOTENCY_KEY_REUSE", emit.getRejection().getCode());

            assertEquals(1, gateway.started.size(),
                    "the reusing item must never dispatch — one claim, one receipt");
            assertTrue(stream.getItemStatus("pay-2").isEmpty(),
                    "no receipt is retained for the rejected item");
            TxStreamStats stats = stream.getStats();
            assertEquals(1, stats.acceptedItemCount(),
                    "a reuse-rejected submission never became stream work: no accepted bump");
            assertEquals(0, stats.failedItemCount(),
                    "a reuse-rejected submission must not count as a failed item");

            // Redelivery under the original item id still attaches normally.
            assertSame(owner, stream.submit(TxWorkItem.builder("pay-1")
                    .withTxPlan(plan()).withIdempotencyKey("order-1").build()));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    owner.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // BUG-A regression: a stale rejected state must never clobber a live
    // successor's claim-key mapping or store record through eviction
    // ------------------------------------------------------------------

    @Test
    void staleRejectedRegistrationNeverClobbersLiveSuccessorOnEviction() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = builder("payouts", gateway)
                .stateStore(store)
                .maxRetainedSettledItems(1)
                .build()) {
            try {
                stream.start();
                // Registration fails: the rejected state settles typed and is
                // released — retained nowhere, so nothing of it may ever evict.
                store.registerFailure = new IllegalStateException("registry storage down");
                assertThrows(TxStreamException.class, () -> stream.submit(TxWorkItem.builder("pay-1")
                        .withTxPlan(plan()).withIdempotencyKey("order-1").build()));

                // The retry succeeds and is the live successor for the same item
                // id and claim key; keep it running while eviction pressure hits.
                store.registerFailure = null;
                TxStreamReceipt live = stream.submit(TxWorkItem.builder("pay-1")
                        .withTxPlan(plan()).withIdempotencyKey("order-1").build());
                assertEquals(1, gateway.started.size());

                // Settle two other items to push the retention FIFO past the cap.
                stream.submit(planItem("pay-2"));
                stream.submit(planItem("pay-3"));
                assertTrue(stream.cancel("pay-2", "settle for eviction pressure"));
                assertTrue(stream.cancel("pay-3", "settle for eviction pressure"));

                assertTrue(store.calls.contains("evict:pay-2"),
                        "legitimate settled items must still evict FIFO: " + store.calls);
                assertFalse(store.calls.contains("evict:pay-1"),
                        "the stale rejected state must never reach the store's eviction: "
                                + store.calls);
                assertTrue(store.getItem("payouts", "pay-1").isPresent(),
                        "the live successor's store record must survive");
                assertEquals(live.executionId().orElseThrow(),
                        store.bindings.get("pay-1").executionId(),
                        "the live successor's binding must survive");
                assertTrue(stream.getItemStatus("pay-1").isPresent(),
                        "the live successor must stay visible");

                // The live claim-key mapping must survive: reuse still rejects.
                TxStreamException reuse = assertThrows(TxStreamException.class,
                        () -> stream.submit(TxWorkItem.builder("pay-9")
                                .withTxPlan(plan()).withIdempotencyKey("order-1").build()));
                assertEquals("TXSTREAM_IDEMPOTENCY_KEY_REUSE", reuse.getCode());
                assertTrue(reuse.getMessage().contains("pay-1"),
                        "the surviving mapping must still name the live owner");
                assertEquals(1, gateway.started.size(), "the reusing item never dispatches");

                gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
                assertEquals(TxStreamItemStatus.CONFIRMED,
                        live.completion().toCompletableFuture().join().getStatus());
            } finally {
                // Fail fast instead of hanging: if an assertion above fired
                // while pay-1 was still in flight, a graceful close would
                // block on its promise forever; after abort, close is a no-op.
                stream.abort("test cleanup");
            }
        }
    }

    @Test
    void failedRegistrationReleasesTheClaimKeyForRetry() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        store.registerFailure = new IllegalStateException("registry storage down");
        try (TxFlowStream stream = builder("payouts", gateway).stateStore(store).build()) {
            stream.start();
            assertThrows(TxStreamException.class, () -> stream.submit(TxWorkItem.builder("pay-1")
                    .withTxPlan(plan()).withIdempotencyKey("order-1").build()));

            // The failed registration must not leave the key bound: the same
            // key under the same item id retries cleanly.
            store.registerFailure = null;
            TxStreamReceipt retried = stream.submit(TxWorkItem.builder("pay-1")
                    .withTxPlan(plan()).withIdempotencyKey("order-1").build());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    retried.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // DEV-2: receipt event cursor
    // ------------------------------------------------------------------

    @Test
    void receiptEventCursorAdvancesWithLiveSubmittedReadThroughAndTerminalPass() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            assertEquals(0, receipt.eventCursor(),
                    "no engine event has been consumed yet");

            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(STEP_ID, "tx-live");     // sequence 1
            assertEquals(TxStreamItemStatus.SUBMITTED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus());
            assertEquals(1, receipt.eventCursor(),
                    "the live SUBMITTED read-through must advance the cursor");

            handle.completeConfirmed(STEP_ID, "tx-live");  // appends sequence 2
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals(2, receipt.eventCursor(),
                    "the terminal projection pass must consume the handle's tail");
        }
    }

    @Test
    void eventCursorIsUnchangedBySnapshotOnlyFastForwardRepair() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            gateway.putSnapshot(receipt.executionId().orElseThrow(),
                    FlowExecutionState.COMPLETED);

            TxStreamItemResult repaired = stream.reconcile("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repaired.getStatus(),
                    "the snapshot fast-forwards the projection authoritatively");
            assertEquals(0, receipt.eventCursor(),
                    "a snapshot-only fast-forward consumes no events and must not move the cursor");
        }
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
        return TxWorkItem.fromTxPlan(itemId, plan());
    }

    private TxPlan plan() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER));
    }
}
