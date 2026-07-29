package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stream-owned reconciliation observer (ADR 0004, iteration 3): a periodic
 * background pass that PUSH-repairs {@code RECOVERY_REQUIRED} items instead of
 * relying on a caller poll. All passes are fired manually through the
 * {@link ManualScheduler} maintenance-executor seam; every engine outcome is
 * scripted through {@link StubEngineGateway}. No real threads, timers, or sleeps.
 */
class TxFlowStreamReconciliationObserverTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String NAMESPACE = StreamIdentities.namespace("payouts");
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;
    private static final String TEMPLATE_ID = "payout";
    private static final String TEMPLATE_STEP_ID = "pay";
    private static final String TEMPLATE_STEP2_ID = "pay2";

    // ------------------------------------------------------------------
    // (a) push repair, NOT read-through
    // ------------------------------------------------------------------

    @Test
    void oneObserverFirePushRepairsARecoveryRequiredItemWhoseSnapshotIsNowCompleted() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        TxFlowStreamTest.RecordingListener listener = new TxFlowStreamTest.RecordingListener();
        try (TxFlowStream stream = observerBuilder(gateway, scheduler, listener).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            String executionId = receipt.executionId().orElseThrow();
            driveRecoveryRequired(gateway, "tx-1");
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, receipt.current().getStatus());
            assertEquals(1L, stream.getStats().recoveryRequiredItemCount(),
                    "a live item at RECOVERY_REQUIRED is counted in the recovery gauge");

            // The engine now reports an authoritative terminal outcome.
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED);

            // Fire the observer once — WITHOUT any getItemStatus/reconcile call.
            scheduler.pending().fire();

            // The LIVE projection is repaired to CONFIRMED (push, not read-through).
            assertEquals(TxStreamItemStatus.CONFIRMED, receipt.current().getStatus());
            assertEquals("tx-1", receipt.current().getTransactionHash(),
                    "the hash survives the push repair");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    listener.updates.get(listener.updates.size() - 1).getStatus(),
                    "the repair is emitted to the event listener");
            assertEquals(0L, stream.getStats().recoveryRequiredItemCount(),
                    "the repaired item leaves the recovery gauge (never negative)");
        }
    }

    // ------------------------------------------------------------------
    // (b) still-recovery-required stays put; a later fire retries
    // ------------------------------------------------------------------

    @Test
    void aStillRecoveryRequiredSnapshotStaysAndTheNextFireRetries() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        try (TxFlowStream stream = observerBuilder(gateway, scheduler,
                TxStreamEventListener.NOOP).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            String executionId = receipt.executionId().orElseThrow();
            driveRecoveryRequired(gateway, "tx-1");

            // Engine still running: no authoritative answer to repair with.
            gateway.putSnapshot(executionId, FlowExecutionState.RUNNING);
            scheduler.pending().fire();
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, receipt.current().getStatus());

            // Operator recovers the execution; the next pass repairs it.
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED);
            scheduler.pending().fire();
            assertEquals(TxStreamItemStatus.CONFIRMED, receipt.current().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // (c) durable: an item only in listNonTerminalItemIds, not in the live map
    // ------------------------------------------------------------------

    @Test
    void aDurableRecoveryRequiredItemNotInTheLiveMapIsReconciledByTheObserver() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        ManualScheduler scheduler = new ManualScheduler();
        TxFlowStreamTest.RecordingListener listener = new TxFlowStreamTest.RecordingListener();

        try (TxFlowStream observer =
                     durableObserverBuilder(engine, store, scheduler, listener).build()) {
            // The observer instance starts against an empty store: re-attach
            // finds nothing, so "pay-remote" is never loaded into its live map.
            observer.start();

            // A DIFFERENT stream instance (same durable store + engine) recovers
            // an item AFTER the observer instance started, persisting a real
            // planned record + a RECOVERY_REQUIRED projection.
            TxFlowStream other = plainDurableBuilder(engine, store).build();
            other.start();
            String executionId = other.submit(planItem("pay-remote")).executionId().orElseThrow();
            driveRecoveryRequired(engine, "tx-remote");
            other.close();

            // The engine's snapshot now reports an authoritative terminal outcome.
            engine.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-remote"));

            // Precondition: the row is durable-non-terminal but NOT in the
            // observer's live map (getItemStatus returns the stored projection
            // verbatim, no read-through repair for a non-live item).
            assertTrue(store.listNonTerminalItemIds("payouts").contains("pay-remote"));
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    observer.getItemStatus("pay-remote").orElseThrow().getStatus());

            // The observer must recover by READ-THROUGH only: capture the engine's
            // start count so we can prove it never re-executes the foreign item.
            int startsBeforeFire = engine.started.size();

            // Fire the observer: the durable scan reconstructs and push-repairs it.
            scheduler.pending().fire();

            assertEquals(TxStreamItemStatus.CONFIRMED,
                    store.getItem("payouts", "pay-remote").orElseThrow().getStatus());
            assertEquals("tx-remote",
                    store.getItem("payouts", "pay-remote").orElseThrow().getTransactionHash());
            assertTrue(listener.updates.stream()
                            .anyMatch(update -> update.getItemId().equals("pay-remote")
                                    && update.getStatus() == TxStreamItemStatus.CONFIRMED),
                    "the observer emits the durable repair to the listener");
            assertEquals(startsBeforeFire, engine.started.size(),
                    "the observer recovers a foreign-instance item by read-through — it must"
                            + " NEVER start or re-execute it on the engine");
        }
    }

    // ------------------------------------------------------------------
    // (d) per-pass cap honored
    // ------------------------------------------------------------------

    @Test
    void theReconciliationBatchSizeCapIsHonoredAcrossFires() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        try (TxFlowStream stream = observerBuilder(gateway, scheduler, TxStreamEventListener.NOOP)
                .reconciliationBatchSize(2)
                .build()) {
            stream.start();
            List<TxStreamReceipt> receipts = new ArrayList<>();
            // N+1 = 3 recovery-required items, all with a COMPLETED snapshot.
            for (int i = 0; i < 3; i++) {
                TxStreamReceipt receipt = stream.submit(planItem("pay-" + i));
                driveRecoveryRequired(gateway, "tx-" + i);
                gateway.putSnapshot(receipt.executionId().orElseThrow(),
                        FlowExecutionState.COMPLETED);
                receipts.add(receipt);
            }

            scheduler.pending().fire();
            assertEquals(2, countConfirmed(receipts),
                    "cap 2 reconciles exactly 2 of the 3 this fire");

            scheduler.pending().fire();
            assertEquals(3, countConfirmed(receipts), "the remainder is reconciled next fire");
        }
    }

    // ------------------------------------------------------------------
    // (e) a throwing listener never kills the observer
    // ------------------------------------------------------------------

    @Test
    void aThrowingListenerDuringARepairDoesNotKillTheObserverOrItsScheduler() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        TxStreamEventListener throwing = new TxStreamEventListener() {
            @Override
            public void onItemUpdated(TxStreamItemResult result) {
                throw new RuntimeException("listener boom");
            }
        };
        try (TxFlowStream stream = observerBuilder(gateway, scheduler, throwing).build()) {
            stream.start();
            List<TxStreamReceipt> receipts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                TxStreamReceipt receipt = stream.submit(planItem("pay-" + i));
                driveRecoveryRequired(gateway, "tx-" + i);
                gateway.putSnapshot(receipt.executionId().orElseThrow(),
                        FlowExecutionState.COMPLETED);
                receipts.add(receipt);
            }

            scheduler.pending().fire();

            assertEquals(2, countConfirmed(receipts),
                    "a throwing listener is isolated; both items are still reconciled");
            // The observer survived and re-armed the next pass.
            assertFalse(scheduler.pending().isCancelled());
        }
    }

    // ------------------------------------------------------------------
    // (f) close()/abort() cancels the observer; a stale fire is a no-op
    // ------------------------------------------------------------------

    @Test
    void closeCancelsTheObserverAndAStaleFireIsANoOp() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        TxFlowStream stream = observerBuilder(gateway, scheduler, TxStreamEventListener.NOOP).build();
        stream.start();
        ManualScheduler.ScheduledTask observerTask = scheduler.pending();
        TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
        driveRecoveryRequired(gateway, "tx-1");
        gateway.putSnapshot(receipt.executionId().orElseThrow(), FlowExecutionState.COMPLETED);

        stream.close();
        assertTrue(observerTask.isCancelled(), "close() cancels the observer");

        // A late/stale fire of the cancelled task is a no-op — no repair.
        observerTask.fire();
        assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, receipt.current().getStatus());
    }

    @Test
    void abortCancelsTheObserver() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        TxFlowStream stream = observerBuilder(gateway, scheduler, TxStreamEventListener.NOOP).build();
        stream.start();
        ManualScheduler.ScheduledTask observerTask = scheduler.pending();

        stream.abort("test");
        assertTrue(observerTask.isCancelled(), "abort() cancels the observer");
        observerTask.fire(); // stale fire: no-op, no throw
    }

    // ------------------------------------------------------------------
    // (g) builder rules + default-off
    // ------------------------------------------------------------------

    @Test
    void reconciliationIntervalWithoutMaintenanceExecutorFailsTheBuildTyped() {
        StubEngineGateway gateway = new StubEngineGateway();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new TxFlowStream.Builder("payouts", gateway)
                        .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                        .executor(Runnable::run)
                        .reconciliationInterval(Duration.ofSeconds(30))
                        .build());
        assertTrue(failure.getMessage().contains("maintenanceExecutor"),
                "the build failure names the missing maintenance executor");
    }

    @Test
    void aNonPositiveReconciliationIntervalIsRejected() {
        StubEngineGateway gateway = new StubEngineGateway();
        assertThrows(IllegalStateException.class,
                () -> new TxFlowStream.Builder("payouts", gateway)
                        .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                        .executor(Runnable::run)
                        .maintenanceExecutor(new ManualScheduler())
                        .reconciliationInterval(Duration.ZERO)
                        .build());
    }

    @Test
    void reconciliationBatchSizeMustBePositive() {
        StubEngineGateway gateway = new StubEngineGateway();
        assertThrows(IllegalArgumentException.class,
                () -> new TxFlowStream.Builder("payouts", gateway).reconciliationBatchSize(0));
    }

    @Test
    void defaultOffSchedulesNothingAndReadThroughStillRepairs() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        // A maintenance executor is present but no reconciliation interval is set.
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .maintenanceExecutor(scheduler)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            assertTrue(scheduler.tasks.isEmpty(), "the observer is off by default: nothing scheduled");

            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            driveRecoveryRequired(gateway, "tx-1");
            gateway.putSnapshot(receipt.executionId().orElseThrow(), FlowExecutionState.COMPLETED);

            // Read-through repair still works without the observer.
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus());
            assertTrue(scheduler.tasks.isEmpty(), "read-through never schedules a pass");
        }
    }

    // ------------------------------------------------------------------
    // (h) no double-settle of an already-settled receipt future
    // ------------------------------------------------------------------

    @Test
    void theObserverNeverDoubleSettlesAnAlreadySettledReceiptFuture() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        try (TxFlowStream stream = observerBuilder(gateway, scheduler,
                TxStreamEventListener.NOOP).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            String executionId = receipt.executionId().orElseThrow();
            driveRecoveryRequired(gateway, "tx-1");
            // The receipt future already fired at RECOVERY_REQUIRED (settles once).
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    receipt.completion().toCompletableFuture().join().getStatus());

            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED);
            scheduler.pending().fire();
            scheduler.pending().fire(); // idempotent: a second pass is a no-op

            // The live projection reflects the repair; the settled future keeps
            // its point-in-time outcome (never re-completed).
            assertEquals(TxStreamItemStatus.CONFIRMED, receipt.current().getStatus());
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // (F1) the recovery-required gauge is symmetric and never negative across
    // a seeded RECOVERY_REQUIRED item and its later terminal repair
    // ------------------------------------------------------------------

    @Test
    void aDurableAbsentSeededRecoveryRequiredItemKeepsTheRecoveryGaugeNonNegative() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        ManualScheduler scheduler = new ManualScheduler();
        TxFlowStreamTest.RecordingListener listener = new TxFlowStreamTest.RecordingListener();

        try (TxFlowStream observer =
                     durableObserverBuilder(engine, store, scheduler, listener).build()) {
            observer.start();

            // A different instance recovers pay-remote (RECOVERY_REQUIRED) AFTER
            // the observer started, so re-attach never loaded it into the map.
            TxFlowStream other = plainDurableBuilder(engine, store).build();
            other.start();
            String executionId = other.submit(planItem("pay-remote")).executionId().orElseThrow();
            driveRecoveryRequired(engine, "tx-remote");
            other.close();

            // Snapshot still RUNNING: phase 2 reconstructs+SEEDS pay-remote at
            // RECOVERY_REQUIRED (a status that bypasses recordTransition), so the
            // gauge is only correct if the seed site accounts for it.
            engine.putSnapshot(executionId, FlowExecutionState.RUNNING);
            int startsBeforeFire = engine.started.size();
            scheduler.pending().fire();
            assertEquals(1L, observer.getStats().recoveryRequiredItemCount(),
                    "the seeded durable-absent RR item is counted (root fix: not left uncounted,"
                            + " which would later drive the gauge to -1)");
            assertEquals(startsBeforeFire, engine.started.size(),
                    "read-through reconciliation never re-executes the foreign item");

            // The engine now reports COMPLETED; the next fire repairs it to
            // CONFIRMED and the gauge returns to 0 — symmetric, never -1.
            engine.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-remote"));
            scheduler.pending().fire();
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    store.getItem("payouts", "pay-remote").orElseThrow().getStatus());
            assertEquals(0L, observer.getStats().recoveryRequiredItemCount(),
                    "the repaired seeded item leaves the gauge at exactly 0 (never negative)");
        }
    }

    @Test
    void reattachPresentSeededRecoveryRequiredKeepsTheRecoveryGaugeNonNegative() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();

        // Instance 1 leaves a RECOVERY_REQUIRED item durably persisted.
        TxFlowStream a = plainDurableBuilder(engine, store).build();
        a.start();
        String executionId = a.submit(planItem("pay-1")).executionId().orElseThrow();
        driveRecoveryRequired(engine, "tx-1");
        a.close();
        assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                store.getItem("payouts", "pay-1").orElseThrow().getStatus());

        // The engine snapshot is PRESENT but still RUNNING: reattachPresentMember
        // SEEDS the live projection from the stored RECOVERY_REQUIRED (bypassing
        // recordTransition) and surfaces it RECOVERY_REQUIRED.
        engine.putSnapshot(executionId, FlowExecutionState.RUNNING);
        try (TxFlowStream b = plainDurableBuilder(engine, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(1, report.recoveryRequired());
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    b.getItemStatus("pay-1").orElseThrow().getStatus());
            assertEquals(1L, b.getStats().recoveryRequiredItemCount(),
                    "the reattachPresentMember RR seed is counted at the seed site");

            // A later COMPLETED snapshot repairs it via read-through; the gauge
            // decrements symmetrically to 0 (without the seed increment it is -1).
            engine.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.reconcile("pay-1").orElseThrow().getStatus());
            assertEquals(0L, b.getStats().recoveryRequiredItemCount(),
                    "the repaired reattached item leaves the gauge at exactly 0 (never negative)");
        }
    }

    // ------------------------------------------------------------------
    // (F2) bounded fairness: full live RECOVERY_REQUIRED residency (>= batch
    // size) must not starve durable-absent discovery forever
    // ------------------------------------------------------------------

    @Test
    void fullLiveRecoveryResidencyStillDiscoversANewDurableAbsentItemWithinBoundedFires() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway engine = durableEngine();
        ManualScheduler scheduler = new ManualScheduler();

        try (TxFlowStream observer = durableObserverBuilder(engine, store, scheduler,
                TxStreamEventListener.NOOP)
                .reconciliationBatchSize(2)
                .build()) {
            observer.start();

            // Two live items that stay RECOVERY_REQUIRED forever (their snapshots
            // never turn terminal) — residency == batchSize, so phase 1 alone
            // would consume the whole per-fire budget on every fire.
            for (int i = 0; i < 2; i++) {
                String exec = observer.submit(planItem("live-" + i)).executionId().orElseThrow();
                driveRecoveryRequired(engine, "tx-live-" + i);
                engine.putSnapshot(exec, FlowExecutionState.RUNNING);
            }

            // A brand-new durable-absent RR item recovered on another instance
            // after the observer started (only in listNonTerminalItemIds, not in
            // the live map), whose snapshot is authoritatively terminal.
            TxFlowStream other = plainDurableBuilder(engine, store).build();
            other.start();
            String remoteExec = other.submit(planItem("pay-remote")).executionId().orElseThrow();
            driveRecoveryRequired(engine, "tx-remote");
            other.close();
            engine.putSnapshot(remoteExec, FlowExecutionState.COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-remote"));
            assertTrue(store.listNonTerminalItemIds("payouts").contains("pay-remote"));

            // Without the phase-alternation, phase-1 residency (2 == batchSize)
            // starves phase 2 on EVERY fire and pay-remote is never discovered.
            // Alternation guarantees discovery within a bounded number of fires.
            scheduler.pending().fire();
            scheduler.pending().fire();

            assertEquals(TxStreamItemStatus.CONFIRMED,
                    store.getItem("payouts", "pay-remote").orElseThrow().getStatus(),
                    "a durable-absent RR item is discovered despite a full live RR residency");
        }
    }

    // ------------------------------------------------------------------
    // (TEST GAP) 3a/1C fixes hold through the PUSH observer, not only
    // read-through
    // ------------------------------------------------------------------

    @Test
    void observerKeepsAPartiallyCompletedMultiStepTemplateRecoveryRequiredThenRepairsOnCompleted() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        try (TxFlowStream stream =
                     templateObserverBuilder(gateway, scheduler, TxStreamEventListener.NOOP).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            String executionId = receipt.executionId().orElseThrow();
            // Whole-flow template ends PARTIALLY_COMPLETED → RECOVERY_REQUIRED
            // (a later step's tx may still confirm — BUG-T1: never FAILED).
            completePartial(gateway.lastHandle(), TEMPLATE_STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, receipt.current().getStatus());

            // An observer fire against a PARTIALLY_COMPLETED snapshot must NOT
            // fast-forward the whole-flow template to FAILED (the BUG-T1 sibling
            // via push repair) — it stays RECOVERY_REQUIRED.
            gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                    attemptData(TEMPLATE_STEP_ID, AttemptState.CONFIRMED, "tx-1"));
            scheduler.pending().fire();
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, receipt.current().getStatus(),
                    "the observer must not push a partial multi-step template to FAILED");

            // A later COMPLETED snapshot: the next observer fire repairs it.
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(TEMPLATE_STEP_ID, AttemptState.CONFIRMED, "tx-1"));
            scheduler.pending().fire();
            assertEquals(TxStreamItemStatus.CONFIRMED, receipt.current().getStatus(),
                    "a later COMPLETED snapshot repairs the template via the observer");
            assertEquals("tx-1", receipt.current().getTransactionHash());
        }
    }

    @Test
    void observerReconcilesASharedMemberFromItsOwnConfirmedStepNotTheMixedFlowState() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        try (TxFlowStream stream =
                     sharedObserverBuilder(gateway, scheduler, TxStreamEventListener.NOOP).build()) {
            stream.start();
            String stepA = StreamIdentities.stepId("pay-a");
            String stepB = StreamIdentities.stepId("pay-b");
            TxStreamReceipt a = stream.submit(planItem("pay-a"));
            TxStreamReceipt b = stream.submit(planItem("pay-b"));
            String executionId = gateway.started.get(0).getExecutionId();
            // Both members submitted-unconfirmed inside a terminal flow → both
            // RECOVERY_REQUIRED.
            completeBothRecoveryRequired(gateway.lastHandle(), stepA, stepB);
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, a.current().getStatus());
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, b.current().getStatus());

            // Operator recovery: member A's own tx confirmed, member B's failed;
            // the FLOW is PARTIALLY_COMPLETED. The observer must reconcile each
            // member from ITS OWN attempt evidence (BUG-1C-R2 sibling via push):
            // A → CONFIRMED (never FAILED from the mixed flow state), B → FAILED.
            gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                    Map.of("attempts", Map.of(
                            stepA + ":1", attempt(stepA, AttemptState.CONFIRMED, "tx-a"),
                            stepB + ":1", attempt(stepB, AttemptState.FAILED, "tx-b"))));
            scheduler.pending().fire();

            assertEquals(TxStreamItemStatus.CONFIRMED, a.current().getStatus(),
                    "the observer reconciles the member whose own step confirmed to CONFIRMED,"
                            + " never FAILED from the PARTIALLY_COMPLETED flow state");
            assertEquals("tx-a", a.current().getTransactionHash());
            assertEquals(TxStreamItemStatus.FAILED, b.current().getStatus());
            assertEquals("tx-b", b.current().getTransactionHash());
        }
    }

    @Test
    void aTimeWindowAndTheReconciliationObserverShareOneSchedulerAndFireIndependently() {
        StubEngineGateway gateway = new StubEngineGateway();
        ManualScheduler scheduler = new ManualScheduler();
        MutableClock clock = new MutableClock(StubEngineGateway.NOW);
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .planner(TxStreamPlanner.perWindow())
                .window(WindowPolicy.countOrTime(10, Duration.ofSeconds(5)))
                .executor(Runnable::run)
                .maintenanceExecutor(scheduler)
                .reconciliationInterval(Duration.ofSeconds(30))
                .clock(clock)
                .build()) {
            stream.start();
            // start() arms only the reconciliation pass (the window is not open).
            assertEquals(1, liveTaskCount(scheduler));
            ManualScheduler.ScheduledTask reconcileTask = taskWithDelay(scheduler, 30_000);

            // Opening a window arms the window-age wakeup on the SAME scheduler.
            TxStreamReceipt receipt = stream.submit(planItem("pay-1"));
            assertEquals(2, liveTaskCount(scheduler), "window + reconciliation coexist");
            ManualScheduler.ScheduledTask windowTask = taskWithDelay(scheduler, 5_000);

            // The window closes on its OWN timer, independently of reconciliation.
            clock.advance(Duration.ofSeconds(5));
            windowTask.fire();
            assertEquals(1, gateway.started.size(), "the window timer closed + dispatched the window");
            assertFalse(reconcileTask.isCancelled(),
                    "closing the window must not clobber the reconciliation future/epoch");

            // Drive the dispatched (single-member) flow to RECOVERY_REQUIRED.
            String executionId = gateway.started.get(0).getExecutionId();
            String memberStep = StreamIdentities.stepId("pay-1");
            driveRecoveryRequired(gateway.lastHandle(), memberStep, "tx-1");
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, receipt.current().getStatus());
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(memberStep, AttemptState.CONFIRMED, "tx-1"));

            // The reconciliation pass fires independently and repairs the item,
            // then re-arms its own next pass (no shared-epoch clobber).
            reconcileTask.fire();
            assertEquals(TxStreamItemStatus.CONFIRMED, receipt.current().getStatus(),
                    "the observer reconciled the item on its own timer");
            assertEquals(1, liveTaskCount(scheduler),
                    "the reconciliation pass re-armed exactly one successor task");
            assertEquals(30_000, taskWithDelay(scheduler, 30_000).delayMillis);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TxFlowStream.Builder observerBuilder(StubEngineGateway gateway, ManualScheduler scheduler,
                                                 TxStreamEventListener listener) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .eventListener(listener)
                .maintenanceExecutor(scheduler)
                .reconciliationInterval(Duration.ofSeconds(30))
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxFlowStream.Builder durableObserverBuilder(StubEngineGateway engine,
                                                        TxStreamStateStore store,
                                                        ManualScheduler scheduler,
                                                        TxStreamEventListener listener) {
        return new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .stateStore(store)
                .eventListener(listener)
                .maintenanceExecutor(scheduler)
                .reconciliationInterval(Duration.ofSeconds(30))
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxFlowStream.Builder plainDurableBuilder(StubEngineGateway engine,
                                                     TxStreamStateStore store) {
        return new TxFlowStream.Builder("payouts", engine)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .executor(Runnable::run)
                .stateStore(store)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private StubEngineGateway durableEngine() {
        StubEngineGateway engine = new StubEngineGateway();
        engine.durable = true;
        return engine;
    }

    private TxWorkItem planItem(String itemId) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)).from(SENDER)));
    }

    /** Drives the last-started execution to a submitted-but-unconfirmed terminal flow → RECOVERY_REQUIRED. */
    private void driveRecoveryRequired(StubEngineGateway gateway, String hash) {
        driveRecoveryRequired(gateway.lastHandle(), STEP_ID, hash);
    }

    /** Drives one handle to a submitted-but-unconfirmed terminal flow on {@code stepId} → RECOVERY_REQUIRED. */
    private void driveRecoveryRequired(StubEngineGateway.StubHandle handle, String stepId, String hash) {
        handle.submittedEvent(stepId, hash);
        handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                FlowExecutionState.FAILED,
                List.of(FlowStepResult.submissionPendingAt(stepId, hash, List.of(), List.of(),
                        new IllegalStateException("confirmation abandoned"),
                        StubEngineGateway.NOW)),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW));
    }

    private Map<String, Object> attemptData(String stepId, AttemptState state, String hash) {
        return Map.of("attempts", Map.of(stepId + ":1", attempt(stepId, state, hash)));
    }

    private FlowAttemptSnapshot attempt(String stepId, AttemptState state, String hash) {
        return new FlowAttemptSnapshot(stepId, 1, state,
                new SignedPayload.InlineCbor(new byte[]{1}, "sha", hash),
                null, null, List.of(), List.of(), StubEngineGateway.NOW, null);
    }

    private long countConfirmed(List<TxStreamReceipt> receipts) {
        return receipts.stream()
                .filter(receipt -> receipt.current().getStatus() == TxStreamItemStatus.CONFIRMED)
                .count();
    }

    // ---- template (whole-flow) observer helpers ----

    private TxFlowStream.Builder templateObserverBuilder(StubEngineGateway gateway,
                                                         ManualScheduler scheduler,
                                                         TxStreamEventListener listener) {
        return observerBuilder(gateway, scheduler, listener)
                .template(TEMPLATE_ID, multiStepTemplate());
    }

    private TxWorkItem templateItem(String itemId, String receiver, long amount) {
        return TxWorkItem.builder(itemId)
                .withTemplate(TEMPLATE_ID)
                .withBinding("receiver", receiver)
                .withBinding("amount", amount)
                .build();
    }

    /** A parameterized, portable payout template with TWO steps (can be PARTIALLY_COMPLETED). */
    private TxFlow multiStepTemplate() {
        String yaml = "api_version: txflow.cardano-client.dev/v1alpha1\n"
                + "kind: TxFlow\n"
                + "metadata: {name: payout-template-multi}\n"
                + "spec:\n"
                + "  parameters:\n"
                + "    receiver: {type: address, required: true}\n"
                + "    amount: {type: integer, required: true}\n"
                + "  steps:\n"
                + "    - id: " + TEMPLATE_STEP_ID + "\n"
                + "      transaction:\n"
                + "        tx: {intents: []}\n"
                + "    - id: " + TEMPLATE_STEP2_ID + "\n"
                + "      transaction:\n"
                + "        tx: {intents: []}\n";
        return TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
    }

    /** Emits a submitted event for one step, then completes the flow PARTIALLY_COMPLETED. */
    private void completePartial(StubEngineGateway.StubHandle handle, String stepId, String hash) {
        handle.submittedEvent(stepId, hash);
        handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                FlowExecutionState.PARTIALLY_COMPLETED,
                List.of(FlowStepResult.successAt(stepId, hash, List.of(), List.of(),
                        StubEngineGateway.NOW)),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW));
    }

    // ---- shared multi-item flow observer helpers ----

    private TxFlowStream.Builder sharedObserverBuilder(StubEngineGateway gateway,
                                                       ManualScheduler scheduler,
                                                       TxStreamEventListener listener) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .planner(TxStreamPlanner.perWindow())
                .window(WindowPolicy.count(2))
                .executor(Runnable::run)
                .eventListener(listener)
                .maintenanceExecutor(scheduler)
                .reconciliationInterval(Duration.ofSeconds(30))
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    /** Both members submitted-unconfirmed inside a terminal flow → both RECOVERY_REQUIRED. */
    private void completeBothRecoveryRequired(StubEngineGateway.StubHandle handle,
                                              String stepA, String stepB) {
        handle.submittedEvent(stepA, "tx-a");
        handle.submittedEvent(stepB, "tx-b");
        handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                FlowExecutionState.FAILED,
                List.of(FlowStepResult.submissionPendingAt(stepA, "tx-a", List.of(), List.of(),
                                new IllegalStateException("abandoned"), StubEngineGateway.NOW),
                        FlowStepResult.submissionPendingAt(stepB, "tx-b", List.of(), List.of(),
                                new IllegalStateException("abandoned"), StubEngineGateway.NOW)),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW));
    }

    // ---- scheduler introspection (two coexisting tasks: window + reconciliation) ----

    private int liveTaskCount(ManualScheduler scheduler) {
        return (int) scheduler.tasks.stream()
                .filter(task -> !task.fired.get() && !task.cancelled.get())
                .count();
    }

    private ManualScheduler.ScheduledTask taskWithDelay(ManualScheduler scheduler, long delayMillis) {
        return scheduler.tasks.stream()
                .filter(task -> !task.fired.get() && !task.cancelled.get()
                        && task.delayMillis == delayMillis)
                .reduce((first, second) -> {
                    throw new IllegalStateException("more than one live task with delay " + delayMillis);
                })
                .orElseThrow(() -> new IllegalStateException("no live task with delay " + delayMillis));
    }

    /** Deterministic, manually advanced clock for the window-age check. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
