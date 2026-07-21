package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowFormat;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion;
import com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 2b-core — the durable-mode surface and restart re-attach protocol
 * (ADR 0004 Decision 5). "Restart" is simulated deterministically: build stream
 * A on a shared, restart-surviving durable store S and shared engine store E,
 * advance items to various states, drop A without draining, then build stream B
 * on the SAME S+E and re-attach. All executions run through the stubbed engine
 * gateway; no real threads, timers, or sleeps.
 */
class TxFlowStreamDurableReattachTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String NAMESPACE = StreamIdentities.namespace("payouts");
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    // ------------------------------------------------------------------
    // (f) Durable-mode builder invariant (P5)
    // ------------------------------------------------------------------

    @Test
    void builderRejectsADurableStoreWithANonDurableEngine() {
        StubEngineGateway engine = new StubEngineGateway();
        engine.durable = false;
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> durableBuilder(engine, new SharedDurableTxStreamStore()).build());
        assertTrue(failure.getMessage().contains("durable stream store requires a durable"),
                "the invariant must name the durable-engine requirement");
    }

    @Test
    void builderAcceptsADurableStoreWithADurableEngineAndAnyStoreWithAnInMemoryEngine() {
        StubEngineGateway durableEngine = new StubEngineGateway();
        durableEngine.durable = true;
        durableBuilder(durableEngine, new SharedDurableTxStreamStore()).build().close();

        // A non-durable store stays legal with any engine (today's behavior).
        StubEngineGateway inMemoryEngine = new StubEngineGateway();
        inMemoryEngine.durable = false;
        durableBuilder(inMemoryEngine, TxStreamStateStore.inMemory()).build().close();
    }

    // ------------------------------------------------------------------
    // (a) DISPATCHING with a present snapshot: re-projected, not re-dispatched
    // ------------------------------------------------------------------

    @Test
    void aBoundItemWithAPresentSnapshotIsReprojectedNotRedispatched() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();

        // Stream A dispatches to PLANNED (start happened, handle left running),
        // then crashes before recording completion — the binding stays
        // DISPATCHING (its start outcome is never confirmed).
        store.suppressConfirmOutcome = true;
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        TxStreamReceipt receiptA = a.submit(planItem("pay-1"));
        String executionId = receiptA.executionId().orElseThrow();
        assertTrue(store.outcome("pay-1").isEmpty(), "binding must be DISPATCHING (unconfirmed)");
        assertEquals(1, engine.started.size());

        // The engine actually finished the execution while nobody watched.
        engine.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
        store.suppressConfirmOutcome = false;

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(1, report.reattachedItems());
            assertEquals(0, report.redispatched());
            assertEquals(1, engine.started.size(), "a present snapshot is never re-dispatched");

            TxStreamItemResult repaired = b.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repaired.getStatus());
            assertEquals("tx-1", repaired.getTransactionHash());
            assertEquals(BindingOutcome.MATCHED, store.outcome("pay-1").orElseThrow(),
                    "re-attach confirms the DISPATCHING binding once the snapshot proves the start");
        }
    }

    // ------------------------------------------------------------------
    // (b) DISPATCHING with an absent snapshot: re-dispatched from the plan
    // ------------------------------------------------------------------

    @Test
    void aBoundItemWithAnAbsentSnapshotIsRedispatchedOnceWithADeterministicId() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();

        // Stream A persists the plan and binding, then the process dies before
        // the start durably happened: clear the engine's (non-durable) in-flight
        // record and leave no snapshot → the engine store has no evidence.
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        TxStreamReceipt receiptA = a.submit(planItem("pay-1"));
        String executionId = receiptA.executionId().orElseThrow();
        assertTrue(store.plannedByExecution(executionId).isPresent(),
                "the plan must be persisted before start");
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.start(); // durable start runs re-attach, then pumps the re-dispatch
            ReattachReport report = b.reattach();
            assertEquals(0, report.reattachedItems());
            assertEquals(1, report.redispatched());
            assertEquals(1, engine.started.size(), "re-dispatch starts the execution exactly once");
            assertEquals(StreamIdentities.executionId(NAMESPACE, "pay-1"),
                    engine.started.get(0).getExecutionId(),
                    "the deterministic execution id makes re-dispatch idempotent");

            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            TxStreamItemResult confirmed = b.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, confirmed.getStatus());
            assertEquals("tx-1", confirmed.getTransactionHash());
        }
    }

    // ------------------------------------------------------------------
    // (c) Non-terminal CONFIRMED-phase item: authoritative fast-forward via P2
    // ------------------------------------------------------------------

    @Test
    void aRunningItemFastForwardsToConfirmedFromACompletedSnapshotKeepingItsHash() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();
        assertEquals(BindingOutcome.CREATED, store.outcome("pay-1").orElseThrow(),
                "the binding was confirmed CREATED before the crash");

        engine.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-final"));

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.reattach();
            TxStreamItemResult repaired = b.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repaired.getStatus(),
                    "PLANNED fast-forwards straight to CONFIRMED without visiting SUBMITTED");
            assertEquals("tx-final", repaired.getTransactionHash());
        }
    }

    // ------------------------------------------------------------------
    // (d) Stored PARTIALLY_COMPLETED shared flow: per-member evidence
    // ------------------------------------------------------------------

    @Test
    void aSharedPartiallyCompletedFlowReprojectsEachMemberFromItsOwnEvidence() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        String stepA = StreamIdentities.stepId("pay-a");
        String stepB = StreamIdentities.stepId("pay-b");

        TxFlowStream a = sharedDurableBuilder(engine, store).build();
        a.start();
        a.submit(planItem("pay-a"));
        a.submit(planItem("pay-b"));
        String executionId = engine.started.get(0).getExecutionId();
        assertEquals(1, engine.started.size(), "one shared flow for the window");

        // The flow itself is PARTIALLY_COMPLETED; only the per-member attempt
        // evidence says which member confirmed (BUG-1C-R2 path on re-attach).
        engine.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                Map.of("attempts", Map.of(
                        stepA + ":1", attempt(stepA, AttemptState.CONFIRMED, "tx-a"),
                        stepB + ":1", attempt(stepB, AttemptState.FAILED, "tx-b"))));

        try (TxFlowStream b = sharedDurableBuilder(engine, store).build()) {
            b.reattach();
            TxStreamItemResult memberA = b.getItemStatus("pay-a").orElseThrow();
            TxStreamItemResult memberB = b.getItemStatus("pay-b").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, memberA.getStatus(),
                    "a confirmed member re-projects CONFIRMED, never FAILED from the mixed flow");
            assertEquals("tx-a", memberA.getTransactionHash());
            assertEquals(TxStreamItemStatus.FAILED, memberB.getStatus());
        }
    }

    // ------------------------------------------------------------------
    // (e) Still-running execution: surfaced RECOVERY_REQUIRED, never FAILED
    // ------------------------------------------------------------------

    @Test
    void aStillRunningExecutionIsSurfacedRecoveryRequiredNotFailed() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();

        engine.putSnapshot(executionId, FlowExecutionState.RUNNING);

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(1, report.reattachedItems());
            assertEquals(1, report.recoveryRequired());
            assertEquals(0, report.redispatched());
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus(),
                    "a running foreign execution is honest RECOVERY_REQUIRED, refreshed by read-through");
        }
    }

    // ------------------------------------------------------------------
    // (g) No-secrets persistence boundary
    // ------------------------------------------------------------------

    @Test
    void anInlineSensitiveBindingFailsNonPersistableAtBindInDurableMode() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        try (TxFlowStream stream = durableBuilder(engine, store).build()) {
            stream.start();
            TxWorkItem item = TxWorkItem.builder("pay-1")
                    .withTxPlan(payment())
                    .withSensitiveBinding("signer", "top-secret-mnemonic")
                    .build();
            TxStreamReceipt receipt = stream.submit(item);
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_NON_PERSISTABLE_SECRET",
                    ((TxStreamException) outcome.getError()).getCode());
            assertTrue(engine.started.isEmpty(),
                    "a non-persistable request must never reach the engine");
            assertEquals(0, store.allPlanned().size(),
                    "no plan is persisted for a non-persistable request");
        }
    }

    @Test
    void secureBindingReferencesArePersistedAsReferencesAndFingerprintsWithoutSecretValues() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        try (TxFlowStream stream = durableBuilder(engine, store).build()) {
            stream.start();
            TxWorkItem item = TxWorkItem.builder("pay-1")
                    .withTxPlan(payment())
                    .withSecureBindingReference("signer", "vault://cardano/payouts-signer")
                    .withBinding("memo", "invoice-42")
                    .build();
            String executionId = stream.submit(item).executionId().orElseThrow();

            TxStreamPlannedRecord planned = store.plannedByExecution(executionId).orElseThrow();
            assertEquals("vault://cardano/payouts-signer",
                    planned.secureBindingReferences().get("signer"),
                    "the reference (a pointer, not a secret) is persisted verbatim");
            assertEquals(StreamIdentities.secureRefFingerprint("vault://cardano/payouts-signer"),
                    planned.secureBindingFingerprints().get("signer"));
            assertEquals("invoice-42", planned.bindings().get("memo"),
                    "non-sensitive bindings are persisted");
            // The persisted record must carry NO resolved secret value anywhere.
            assertFalse(planned.toString().contains("top-secret"),
                    "no secret value is present in the persisted plan");
            assertEquals(1, engine.started.size(), "the request dispatched normally");

            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1"); // settle so close() drains
        }
    }

    // ------------------------------------------------------------------
    // (h) ReattachReport counts across a mixed recovery
    // ------------------------------------------------------------------

    @Test
    void reattachReportCountsReattachedRedispatchedAndRecoveryRequired() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        // Distinct lanes so all three items dispatch concurrently and end up
        // bound-and-PLANNED simultaneously (one lane would serialize them).
        String laneDone = "addr_test1vpqdone";
        String laneRunning = "addr_test1vpqrunning";
        String laneLost = "addr_test1vpqlost";
        TxFlowStream a = explicitDurableBuilder(engine, store).build();
        a.start();
        String doneId = a.submit(lanedItem("done", laneDone)).executionId().orElseThrow();
        String runningId = a.submit(lanedItem("running", laneRunning)).executionId().orElseThrow();
        String lostId = a.submit(lanedItem("lost", laneLost)).executionId().orElseThrow();
        assertEquals(3, store.allPlanned().size(), "each item persisted its own plan");

        engine.putSnapshot(doneId, FlowExecutionState.COMPLETED,
                attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-done"));
        engine.putSnapshot(runningId, FlowExecutionState.RUNNING);
        // "lost" has neither a snapshot nor an in-flight record.
        assertEquals(lostId, StreamIdentities.executionId(NAMESPACE, "lost"));
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = explicitDurableBuilder(engine, store).build()) {
            b.start();
            ReattachReport report = b.reattach();
            assertEquals(2, report.reattachedItems(), "done + running were re-attached");
            assertEquals(1, report.redispatched(), "lost was re-dispatched");
            assertEquals(1, report.recoveryRequired(), "running is recovery-required");
            assertTrue(report.reattachedItemIds().contains("done"));
            assertTrue(report.reattachedItemIds().contains("running"));
            assertFalse(report.reattachedItemIds().contains("lost"),
                    "a re-dispatched item is not counted as re-attached");
            assertEquals(1, engine.started.size(), "only the absent item re-dispatched");

            // Settle the re-dispatched item so close() can drain.
            engine.lastHandle().completeConfirmed(STEP_ID, "tx-lost");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.getItemStatus("lost").orElseThrow().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Idempotency / non-durable behavior
    // ------------------------------------------------------------------

    @Test
    void reattachIsIdempotentAndEmptyWhenNothingIsPersisted() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        try (TxFlowStream stream = durableBuilder(engine, store).build()) {
            ReattachReport first = stream.reattach();
            ReattachReport second = stream.reattach();
            assertEquals(0, first.reattachedItems());
            assertEquals(0, first.redispatched());
            assertSame(first, second);
        }
    }

    @Test
    void aNonDurableStreamHasNothingToReattach() {
        StubEngineGateway engine = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .stateStore(TxStreamStateStore.inMemory())
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            ReattachReport report = stream.reattach();
            assertEquals(0, report.reattachedItems());
            assertEquals(0, report.redispatched());
            assertEquals(0, report.recoveryRequired());
        }
    }

    // ------------------------------------------------------------------
    // BUG-1 — re-attach fast-forward must reach the DURABLE store, not just
    // the live cell, or the item is re-attached on every restart forever
    // ------------------------------------------------------------------

    @Test
    void reattachToACompletedSnapshotDrivesTheDurableProjectionTerminalSoNoFurtherRestartReattaches() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        // Stream A dispatches to PLANNED, crashes before completion (binding DISPATCHING).
        store.suppressConfirmOutcome = true;
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();
        assertTrue(store.listNonTerminalItemIds("payouts").contains("pay-1"),
                "the durable projection is non-terminal before re-attach");
        store.suppressConfirmOutcome = false;
        engine.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.reattach();
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus());
            TxStreamItemResult stored = store.getItem("payouts", "pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, stored.getStatus(),
                    "the DURABLE projection itself must reach CONFIRMED, not just the live cell");
            assertEquals("tx-1", stored.getTransactionHash());
            assertTrue(store.listNonTerminalItemIds("payouts").isEmpty(),
                    "a terminal durable projection leaves the non-terminal set");
        }

        // Third instance: the CONFIRMED store row is not re-attached again.
        try (TxFlowStream c = durableBuilder(engine, store).build()) {
            ReattachReport report = c.reattach();
            assertEquals(0, report.reattachedItems());
            assertEquals(0, report.redispatched());
            assertEquals(0, report.recoveryRequired());
        }
    }

    @Test
    void redispatchToConfirmedDrivesTheDurableProjectionTerminalSoNoFurtherRestartReattaches() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        // Stream A persists plan + binding, then the durable start never happens
        // (no engine snapshot): the item must be re-dispatched, not re-attached.
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        a.submit(planItem("pay-1"));
        assertTrue(store.listNonTerminalItemIds("payouts").contains("pay-1"));
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.start();
            assertEquals(1, engine.started.size(), "the absent item is re-dispatched once");
            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus());
            TxStreamItemResult stored = store.getItem("payouts", "pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, stored.getStatus(),
                    "the DURABLE projection reaches CONFIRMED through the normal dispatch path");
            assertTrue(store.listNonTerminalItemIds("payouts").isEmpty());
        }

        try (TxFlowStream c = durableBuilder(engine, store).build()) {
            ReattachReport report = c.reattach();
            assertEquals(0, report.redispatched(), "a CONFIRMED durable row is never re-dispatched");
            assertEquals(0, report.reattachedItems());
        }
    }

    // ------------------------------------------------------------------
    // BUG-2 — the idempotency-key-reuse guard must survive restart
    // ------------------------------------------------------------------

    @Test
    void aReattachedItemsClaimKeyStillBlocksReuseByFreshWorkAfterRestart() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        // Stream A submits "orig" claiming key K and leaves it running.
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        TxWorkItem orig = TxWorkItem.builder("orig").withTxPlan(payment())
                .withIdempotencyKey("K").build();
        String executionId = a.submit(orig).executionId().orElseThrow();
        engine.putSnapshot(executionId, FlowExecutionState.RUNNING);

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.start(); // re-attaches "orig" (RECOVERY_REQUIRED) and rebuilds the claim index
            TxWorkItem intruder = TxWorkItem.builder("intruder").withTxPlan(payment())
                    .withIdempotencyKey("K").build();
            TxStreamException reuse = assertThrows(TxStreamException.class,
                    () -> b.submit(intruder));
            assertEquals("TXSTREAM_IDEMPOTENCY_KEY_REUSE", reuse.getCode(),
                    "a key still owned by a re-attached item cannot be reused by fresh work");
        }
    }

    // ------------------------------------------------------------------
    // BUG-3 — redispatch must not strand member promises on an execution-id
    // collision (latent drain() hang)
    // ------------------------------------------------------------------

    @Test
    void twoPlannedRecordsCollidingOnOneExecutionIdReattachWithoutHangingOneWins() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        // Stream A produces a genuine planned record for "pay-1" (absent snapshot).
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();
        engine.started.clear();
        engine.handles.clear();

        // Inject a SECOND record colliding on the same execution id but carrying
        // a different member — a corrupt-store shape that must strand nothing.
        TxStreamPlannedRecord original = store.plannedByExecution(executionId).orElseThrow();
        TxStreamPlannedRecord collider = new TxStreamPlannedRecord(
                original.streamId(), executionId, original.idempotencyKey(),
                original.laneName(), original.canonicalSpendingIdentity(),
                original.portableFlow(), original.bindings(),
                original.secureBindingReferences(), original.secureBindingFingerprints(),
                List.of(new TxStreamPlannedRecord.Member("ghost-2", "ghost-2", STEP_ID, "fp")));
        store.injectPlanned(collider);
        store.projectItem(TxStreamItemResult
                .builder("payouts", "ghost-2", TxStreamItemStatus.PLANNED)
                .executionId(executionId).stepId(STEP_ID).laneName("payouts-lane")
                .updatedAt(StubEngineGateway.NOW).build(), 1);

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.start();
            assertEquals(1, engine.started.size(), "only one of the colliding records dispatches");
            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            b.drain(); // must return — the loser stranded no open promise
        }
    }

    // ------------------------------------------------------------------
    // BUG-4 — an ACCEPTED-but-unbound ghost is reaped, not re-scanned forever
    // ------------------------------------------------------------------

    @Test
    void anAcceptedButUnboundGhostIsReapedOnReattachAndNotReturnedNextRestart() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        // Registered + projected ACCEPTED before the crash, never bound (no
        // planned record), execution never reached the engine (no snapshot).
        store.registerItem(new TxStreamItemRecord("ghost", "ghost", "payouts-lane", "fp",
                StubEngineGateway.NOW));
        store.projectItem(TxStreamItemResult
                .builder("payouts", "ghost", TxStreamItemStatus.ACCEPTED)
                .executionId(StreamIdentities.executionId(NAMESPACE, "ghost"))
                .laneName("payouts-lane").updatedAt(StubEngineGateway.NOW).build(), 1);
        assertTrue(store.listNonTerminalItemIds("payouts").contains("ghost"));

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(0, report.reattachedItems());
            assertEquals(0, report.redispatched());
            TxStreamItemResult reaped = store.getItem("payouts", "ghost").orElseThrow();
            assertEquals(TxStreamItemStatus.CANCELLED, reaped.getStatus());
            assertEquals("TXSTREAM_ABANDONED", ((TxStreamException) reaped.getError()).getCode());
            assertFalse(store.listNonTerminalItemIds("payouts").contains("ghost"),
                    "a reaped ghost leaves the non-terminal set");
        }

        try (TxFlowStream c = durableBuilder(engine, store).build()) {
            ReattachReport report = c.reattach();
            assertEquals(0, report.reattachedItems() + report.redispatched()
                    + report.recoveryRequired(), "the reaped ghost is not re-scanned");
        }
    }

    // ------------------------------------------------------------------
    // Additional double-submit probe — a present CREATED snapshot (claim taken,
    // nothing ran) is RECOVERY_REQUIRED, NEVER re-dispatched
    // ------------------------------------------------------------------

    @Test
    void aPresentButCreatedSnapshotIsRecoveryRequiredAndNeverRedispatched() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();
        // The engine created the execution (claim taken) but nothing ran yet —
        // re-dispatching would DOUBLE-SUBMIT. A present snapshot never re-dispatches.
        engine.putSnapshot(executionId, FlowExecutionState.CREATED);
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(1, report.reattachedItems());
            assertEquals(0, report.redispatched(), "a present CREATED snapshot is never re-dispatched");
            assertEquals(1, report.recoveryRequired());
            assertEquals(0, engine.started.size(), "no double-submit");
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Secure-ref re-dispatch — the rebuilt request carries the secure reference
    // through the engine's secure-binding channel
    // ------------------------------------------------------------------

    @Test
    void redispatchRebuildsTheRequestWithItsSecureBindingReference() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        TxWorkItem item = TxWorkItem.builder("pay-1")
                .withTxPlan(payment())
                .withSecureBindingReference("signer", "vault://cardano/payouts-signer")
                .build();
        a.submit(item);
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.start();
            assertEquals(1, engine.started.size(), "the absent secure-ref item is re-dispatched");
            assertEquals("vault://cardano/payouts-signer",
                    engine.started.get(0).getSecureBindingReferences().get("signer"),
                    "the rebuilt request carries the secure reference for the engine to resolve");
            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // DEV-1 — the honest no-secrets trust model: only the sanctioned sensitive
    // channel is rejected; everything else is persisted verbatim, and toString
    // redacts secure-ref VALUES
    // ------------------------------------------------------------------

    @Test
    void nonSensitiveBindingsPersistVerbatimAndToStringRedactsSecureRefValues() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        try (TxFlowStream stream = durableBuilder(engine, store).build()) {
            stream.start();
            TxWorkItem item = TxWorkItem.builder("pay-1")
                    .withTxPlan(payment())
                    // A caller MIS-declaring a secret as non-sensitive is NOT
                    // scrubbed — it is persisted verbatim (honest trust model).
                    .withBinding("mislabeled", "not-actually-scrubbed")
                    .withSecureBindingReference("signer", "vault://cardano/payouts-signer")
                    .build();
            String executionId = stream.submit(item).executionId().orElseThrow();
            TxStreamPlannedRecord planned = store.plannedByExecution(executionId).orElseThrow();

            assertEquals("not-actually-scrubbed", planned.bindings().get("mislabeled"),
                    "a non-sensitive binding is persisted verbatim, never scrubbed");
            assertEquals("vault://cardano/payouts-signer",
                    planned.secureBindingReferences().get("signer"),
                    "the secure reference is retrievable verbatim through its getter");
            String text = planned.toString();
            assertFalse(text.contains("vault://cardano/payouts-signer"),
                    "toString redacts secure-ref VALUES");
            assertTrue(text.contains("signer"), "toString still names the secure-ref key");

            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1"); // settle so close() drains
        }
    }

    // ------------------------------------------------------------------
    // DEV-2 — round-trip fingerprint stability is correctness-critical
    // ------------------------------------------------------------------

    @Test
    void redispatchPortableFlowRoundTripsByteIdenticallyAndKeepsTheCompiledFingerprint() {
        TxFlowCodec codec = TxFlowCodec.standard();
        FlowWriteOptions writeOptions = FlowWriteOptions.of(FlowFormat.JSON,
                FlowSchemaVersion.V1ALPHA1);
        // A representative TxPlan-backed single-step flow, exactly as the stream
        // builds it before persisting the portable encoding.
        TxFlow flow = TxFlow.builder(StreamIdentities.flowId(NAMESPACE, "pay-1"))
                .addStep(FlowStep.builder(STEP_ID).withTxPlan(payment()).build())
                .build();
        String once = codec.write(flow, writeOptions);
        TxFlow reparsed = codec.parse(once, FlowParseOptions.serverDefaults()).requireFlow();
        String twice = codec.write(reparsed, writeOptions);
        assertEquals(once, twice,
                "re-dispatch parses the persisted flow and re-encodes it byte-identically");

        TxFlowCompiler compiler = new TxFlowCompiler();
        String originalFingerprint = compiler
                .compile(FlowCompilationRequest.builder(flow).build())
                .requireCompiledFlow().getFingerprint();
        String reparsedFingerprint = compiler
                .compile(FlowCompilationRequest.builder(reparsed).build())
                .requireCompiledFlow().getFingerprint();
        assertEquals(originalFingerprint, reparsedFingerprint,
                "the reparsed definition compiles to the same fingerprint, so a re-dispatched"
                        + " item MATCHES its claim rather than conflicting");
    }

    @Test
    void aRedispatchedItemRedeliveredLiveAttachesInsteadOfConflicting() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        a.submit(planItem("pay-1"));
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            b.start(); // re-dispatches pay-1 (now live, still running)
            String redispatchedExecution = b.getItemStatus("pay-1").orElseThrow()
                    .getExecutionId();
            int startedAfterRedispatch = engine.started.size();

            // A live redelivery of the SAME content attaches — the reconstructed
            // item's persisted fingerprint MATCHES a fresh submit's fingerprint.
            String attachedExecution = b.submit(planItem("pay-1")).executionId().orElseThrow();
            assertEquals(redispatchedExecution, attachedExecution,
                    "a redelivery of the re-dispatched item attaches to the same execution");
            assertEquals(startedAfterRedispatch, engine.started.size(),
                    "an attach never starts a second execution");

            // Different content for the same item id still conflicts.
            TxWorkItem different = TxWorkItem.builder("pay-1")
                    .withTxPlan(TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(9))
                            .from(SENDER)))
                    .build();
            assertThrows(TxStreamDuplicateItemException.class, () -> b.submit(different));

            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertNotEquals(TxStreamItemStatus.FAILED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // DEV-3 — reattach() before start() recovers, and start() then runs the
    // re-dispatched work (not a trap)
    // ------------------------------------------------------------------

    @Test
    void reattachBeforeStartQueuesRedispatchAndStartThenRunsIt() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        TxFlowStream a = durableBuilder(engine, store).build();
        a.start();
        a.submit(planItem("pay-1"));
        engine.started.clear();
        engine.handles.clear();

        try (TxFlowStream b = durableBuilder(engine, store).build()) {
            ReattachReport report = b.reattach(); // BEFORE start(): recovery runs
            assertEquals(1, report.redispatched());
            assertEquals(0, engine.started.size(),
                    "before start() the dispatcher is disabled — nothing runs yet");

            b.start(); // enables the dispatcher and runs the queued re-dispatch
            assertSame(report, b.reattach(), "reattach is idempotent after start");
            assertEquals(1, engine.started.size(), "start() runs the re-dispatched work");
            engine.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private StubEngineGateway durableEngine() {
        StubEngineGateway engine = new StubEngineGateway();
        engine.durable = true;
        return engine;
    }

    private TxFlowStream.Builder durableBuilder(StubEngineGateway engine, TxStreamStateStore store) {
        return new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .stateStore(store)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxFlowStream.Builder sharedDurableBuilder(StubEngineGateway engine,
                                                      TxStreamStateStore store) {
        return durableBuilder(engine, store)
                .planner(TxStreamPlanner.perWindow())
                .window(WindowPolicy.count(2));
    }

    private TxFlowStream.Builder explicitDurableBuilder(StubEngineGateway engine,
                                                        TxStreamStateStore store) {
        return new TxFlowStream.Builder("payouts", engine)
                .lanes(LanePolicy.explicit())
                .laneResolver(name -> ResolvedLane.ofAddress(name, name)) // lane label == address
                .executor(Runnable::run)
                .stateStore(store)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    /** An item pinned to a lane whose address is the lane label and its own sender. */
    private TxWorkItem lanedItem(String itemId, String laneAddress) {
        return TxWorkItem.builder(itemId)
                .withTxPlan(TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5))
                        .from(laneAddress)))
                .withLane(laneAddress)
                .build();
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId, payment());
    }

    private TxPlan payment() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER));
    }

    private Map<String, Object> attemptData(String stepId, AttemptState state, String hash) {
        return Map.of("attempts", Map.of(stepId + ":1", attempt(stepId, state, hash)));
    }

    private FlowAttemptSnapshot attempt(String stepId, AttemptState state, String hash) {
        return new FlowAttemptSnapshot(stepId, 1, state,
                new SignedPayload.InlineCbor(new byte[]{1}, "sha", hash),
                null, null, java.util.List.of(), java.util.List.of(), StubEngineGateway.NOW, null);
    }
}
