package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR 0004 iteration 3a — portable-template items: an item is a reference to a
 * pre-registered parameterized {@link TxFlow} definition plus its bindings, so a
 * stream becomes a stream of parameterized invocations of one compiled,
 * fingerprinted flow. Deterministic: every execution runs through the scripted
 * {@link StubEngineGateway}; no real threads, timers, or sleeps.
 */
class TxFlowStreamTemplateTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String TEMPLATE_ID = "payout";
    private static final String STEP_ID = "pay";
    private static final String STEP2_ID = "pay2";

    // ------------------------------------------------------------------
    // One registered definition, many parameterized invocations
    // ------------------------------------------------------------------

    @Test
    void nItemsWithDifferentBindingsRunNExecutionsOffOneRegisteredDefinition() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            // One statically configured lane serializes items FIFO, so complete
            // each before the next dispatches — the point is that N invocations
            // reuse ONE compiled definition, each carrying its own bindings.
            stream.submit(templateItem("pay-1", RECEIVER + "1", 10L));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            stream.submit(templateItem("pay-2", RECEIVER + "2", 20L));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-2");
            stream.submit(templateItem("pay-3", RECEIVER + "3", 30L));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-3");

            assertEquals(3, gateway.started.size());
            // Every request carries the SAME registered definition instance —
            // it is compiled and held once, not rebuilt per item.
            FlowExecutionRequest first = gateway.started.get(0);
            for (FlowExecutionRequest request : gateway.started) {
                assertSame(first.getDefinition(), request.getDefinition(),
                        "every invocation reuses the one registered definition");
                assertEquals(template().getId(), request.getDefinition().getId());
            }
            // Each request carries ITS item's bindings.
            assertEquals(Map.of("receiver", RECEIVER + "1", "amount", 10L),
                    bindings(gateway.started.get(0)));
            assertEquals(Map.of("receiver", RECEIVER + "2", "amount", 20L),
                    bindings(gateway.started.get(1)));
            assertEquals(Map.of("receiver", RECEIVER + "3", "amount", 30L),
                    bindings(gateway.started.get(2)));
        }
    }

    @Test
    void templateInvocationProjectsConfirmedFromTheWholeFlowState() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            assertTrue(receipt.executionId().isPresent());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-hash-1");

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("tx-hash-1", outcome.getTransactionHash());
            assertEquals("payouts-lane", outcome.getLaneName());
        }
    }

    @Test
    void templateExecutionIdIsClaimDerivedAndIndependentOfBindings() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.builder("pay-1")
                    .withTemplate(TEMPLATE_ID)
                    .withIdempotencyKey("order-1")
                    .withBinding("receiver", RECEIVER)
                    .withBinding("amount", 7L)
                    .build());
            FlowExecutionRequest request = gateway.started.get(0);
            assertEquals("stream:payouts", request.getIdempotencyNamespace());
            assertEquals("order-1", request.getIdempotencyKey());
            assertEquals(receipt.executionId().orElseThrow(), request.getExecutionId());
            // The request fingerprint (engine-side) covers definition + bindings;
            // the lane's canonical identity is declared as its spending resource.
            assertEquals(List.of("addr:" + SENDER), List.copyOf(request.getSpendingResources()));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    @Test
    void executionIdentityIsClaimDerivedAndStableAcrossInstances() {
        StubEngineGateway first = new StubEngineGateway();
        StubEngineGateway second = new StubEngineGateway();
        String firstExecution;
        // Same (template, claimKey) on two instances → the same claim-derived
        // execution id, independent of the bindings.
        try (TxFlowStream stream = builder("payouts", first).build()) {
            stream.start();
            stream.submit(templateItem("pay-1", RECEIVER, 1L));
            firstExecution = first.started.get(0).getExecutionId();
            first.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
        try (TxFlowStream stream = builder("payouts", second).build()) {
            stream.start();
            stream.submit(templateItem("pay-1", RECEIVER + "-different", 999L));
            assertEquals(firstExecution, second.started.get(0).getExecutionId(),
                    "the execution id is derived from the claim, not the bindings");
            second.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    // ------------------------------------------------------------------
    // Attach vs conflict
    // ------------------------------------------------------------------

    @Test
    void sameTemplateSameBindingsRedeliveryAttachesWithoutASecondDispatch() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt original = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            TxStreamReceipt redelivered = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            assertSame(original, redelivered);
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    @Test
    void sameItemIdDifferentBindingsIsATypedConflict() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            stream.submit(templateItem("pay-1", RECEIVER, 5L));
            TxStreamDuplicateItemException conflict = assertThrows(
                    TxStreamDuplicateItemException.class,
                    () -> stream.submit(templateItem("pay-1", RECEIVER, 9L)));
            assertTrue(conflict.getMessage().contains("different content"));
            assertEquals(1, gateway.started.size());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    // ------------------------------------------------------------------
    // Unknown template
    // ------------------------------------------------------------------

    @Test
    void unknownTemplateIdSettlesFailedRetainedAndIdenticalRedeliveryAttaches() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.builder("pay-1")
                    .withTemplate("does-not-exist")
                    .withBinding("receiver", RECEIVER)
                    .build());
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_TEMPLATE_UNKNOWN",
                    ((TxStreamException) outcome.getError()).getCode());
            assertEquals(0, gateway.started.size(), "an unknown template never reaches the engine");

            // Settled + retained: an identical redelivery attaches to the failed receipt.
            TxStreamReceipt redelivered = stream.submit(TxWorkItem.builder("pay-1")
                    .withTemplate("does-not-exist")
                    .withBinding("receiver", RECEIVER)
                    .build());
            assertSame(receipt, redelivered);
        }
    }

    // ------------------------------------------------------------------
    // Build-time template validation
    // ------------------------------------------------------------------

    @Test
    void nonPortableTemplateIsRejectedAtBuildTime() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxFlow nonPortable = TxFlow.builder("bad-template")
                .addStep(FlowStep.builder("factory-step").withTxContext(qtb -> null).build())
                .build();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> builder("payouts", gateway).template("bad", nonPortable).build());
        assertTrue(failure.getMessage().contains("not portable"),
                "a non-portable template is rejected at build time, not per item");
    }

    @Test
    void duplicateTemplateIdIsABuildError() {
        StubEngineGateway gateway = new StubEngineGateway();
        assertThrows(IllegalStateException.class,
                () -> baseBuilder("payouts", gateway)
                        .template(TEMPLATE_ID, template())
                        .template(TEMPLATE_ID, template()));
    }

    @Test
    void withTemplateIsMutuallyExclusiveWithOtherPayloads() {
        assertThrows(IllegalStateException.class, () -> TxWorkItem.builder("x")
                .withTemplate(TEMPLATE_ID)
                .withTxPlan(com.bloxbean.cardano.client.quicktx.serialization.TxPlan.from(
                        new com.bloxbean.cardano.client.quicktx.Tx()
                                .payToAddress(RECEIVER, com.bloxbean.cardano.client.api.model.Amount.ada(1))
                                .from(SENDER))));
    }

    // ------------------------------------------------------------------
    // Lane interaction
    // ------------------------------------------------------------------

    @Test
    void templateItemUnderByFundingAddressWithoutLaneFailsLaneRequired() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = baseBuilder("payouts", gateway)
                .lanes(LanePolicy.byFundingAddress())
                .template(TEMPLATE_ID, template())
                .build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_LANE_REQUIRED",
                    ((TxStreamException) outcome.getError()).getCode());
            assertEquals(0, gateway.started.size());
        }
    }

    @Test
    void templateItemsRunOnExplicitLanes() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = baseBuilder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(name -> ResolvedLane.ofAddress(name, name))
                .template(TEMPLATE_ID, template())
                .build()) {
            stream.start();
            stream.submit(TxWorkItem.builder("pay-1").withTemplate(TEMPLATE_ID)
                    .withLane("treasury").withBinding("receiver", RECEIVER).build());
            assertEquals(List.of("addr:treasury"),
                    List.copyOf(gateway.started.get(0).getSpendingResources()));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    // ------------------------------------------------------------------
    // Durable: no-secrets + re-attach
    // ------------------------------------------------------------------

    @Test
    void sensitiveBindingOnADurableTemplateItemFailsNonPersistableSecret() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();
        try (TxFlowStream stream = durableBuilder(gateway, store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.builder("pay-1")
                    .withTemplate(TEMPLATE_ID)
                    .withBinding("receiver", RECEIVER)
                    .withSensitiveBinding("secret", "top-secret")
                    .build());
            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_NON_PERSISTABLE_SECRET",
                    ((TxStreamException) outcome.getError()).getCode());
            assertEquals(0, gateway.started.size(), "the item never reaches the engine");
        }
    }

    @Test
    void durableTemplateItemPersistsTheTemplateReferenceNotAResolvedSecret() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();
        try (TxFlowStream stream = durableBuilder(gateway, store).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(TxWorkItem.builder("pay-1")
                    .withTemplate(TEMPLATE_ID)
                    .withBinding("receiver", RECEIVER)
                    .withSecureBindingReference("signer", "vault://payouts/signer")
                    .build());
            TxStreamPlannedRecord planned =
                    store.plannedByExecution(receipt.executionId().orElseThrow()).orElseThrow();
            assertEquals(TEMPLATE_ID, planned.templateId());
            assertEquals(Map.of("receiver", RECEIVER), planned.bindings());
            assertEquals(Map.of("signer", "vault://payouts/signer"),
                    planned.secureBindingReferences());
            assertFalse(planned.secureBindingFingerprints().isEmpty(),
                    "the secure reference is persisted as a fingerprint, never a resolved secret");
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    @Test
    void durableTemplateItemReattachRedispatchesWhenTemplateReRegistered() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();

        // Stream A persists the plan + binding, then the process dies before the
        // start durably happened (no snapshot, and clear the non-durable record).
        TxFlowStream a = durableBuilder(gateway, store).build();
        a.start();
        TxStreamReceipt receiptA = a.submit(templateItem("pay-1", RECEIVER, 5L));
        String executionId = receiptA.executionId().orElseThrow();
        assertTrue(store.plannedByExecution(executionId).isPresent());
        gateway.started.clear();
        gateway.handles.clear();

        // Stream B re-registers the SAME template and re-attaches → re-dispatch.
        try (TxFlowStream b = durableBuilder(gateway, store).build()) {
            b.start(); // durable start runs re-attach, then pumps the re-dispatch
            ReattachReport report = b.reattach();
            assertEquals(1, report.redispatched());
            assertEquals(1, gateway.started.size(), "the absent execution is re-dispatched once");
            FlowExecutionRequest request = gateway.started.get(0);
            assertEquals(executionId, request.getExecutionId());
            assertEquals(template().getId(), request.getDefinition().getId());
            assertEquals(Map.of("receiver", RECEIVER, "amount", 5L), bindings(request));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    @Test
    void durableTemplateItemReattachWithoutReRegistrationSurfacesTemplateUnknown() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();

        TxFlowStream a = durableBuilder(gateway, store).build();
        a.start();
        a.submit(templateItem("pay-1", RECEIVER, 5L));
        gateway.started.clear();

        // Stream B does NOT re-register the template → the item is surfaced typed,
        // never silently lost.
        try (TxFlowStream b = durableBuilderWithoutTemplate(gateway, store).build()) {
            b.reattach();
            assertEquals(0, gateway.started.size(), "an unresolved template dispatches nothing");
            TxStreamItemResult outcome = b.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_TEMPLATE_UNKNOWN",
                    ((TxStreamException) outcome.getError()).getCode());
        }
    }

    @Test
    void durableTemplateItemWithAPresentSnapshotIsReprojectedNotRedispatched() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();

        store.suppressConfirmOutcome = true;
        TxFlowStream a = durableBuilder(gateway, store).build();
        a.start();
        TxStreamReceipt receiptA = a.submit(templateItem("pay-1", RECEIVER, 5L));
        String executionId = receiptA.executionId().orElseThrow();
        assertEquals(1, gateway.started.size());
        gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
        store.suppressConfirmOutcome = false;

        try (TxFlowStream b = durableBuilder(gateway, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(1, report.reattachedItems());
            assertEquals(0, report.redispatched());
            assertEquals(1, gateway.started.size(), "a present snapshot is never re-dispatched");
            TxStreamItemResult repaired = b.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repaired.getStatus());
            assertEquals("tx-1", repaired.getTransactionHash());
        }
    }

    // ------------------------------------------------------------------
    // BUG-T1 — a multi-step template ending PARTIALLY_COMPLETED must stay
    // RECOVERY_REQUIRED (never a permanent false FAILED) across the live
    // terminal pass, the read-through reconcile, AND durable re-attach; a later
    // COMPLETED still repairs it to CONFIRMED. (The multi-step coverage gap is
    // exactly what hid this: single-step "parameterized invocation" templates
    // never reach PARTIALLY_COMPLETED.)
    // ------------------------------------------------------------------

    @Test
    void multiStepTemplateEndingPartiallyCompletedSettlesRecoveryRequiredWithHash() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = multiStepBuilder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            // First step confirmed on-chain, the flow overall PARTIALLY_COMPLETED
            // (a later step's tx may still confirm).
            completePartial(gateway.lastHandle(), STEP_ID, "tx-1");

            TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, outcome.getStatus(),
                    "a partially-completed template is honest RECOVERY_REQUIRED, never FAILED");
            assertEquals("tx-1", outcome.getTransactionHash(), "the submitted hash is retained");
        }
    }

    @Test
    void getItemStatusAndReconcileKeepAPartiallyCompletedTemplateRecoveryRequiredThenCompletedRepairs() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = multiStepBuilder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            String executionId = receipt.executionId().orElseThrow();
            completePartial(gateway.lastHandle(), STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    receipt.completion().toCompletableFuture().join().getStatus());

            // A PARTIALLY_COMPLETED snapshot is present: BEFORE the fix,
            // getItemStatus/reconcile fast-forward RECOVERY_REQUIRED → FAILED
            // (snapshotStatus) permanently. It must stay RECOVERY_REQUIRED.
            gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    stream.getItemStatus("pay-1").orElseThrow().getStatus(),
                    "getItemStatus must not fast-forward a partial template to FAILED");
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED,
                    stream.reconcile("pay-1").orElseThrow().getStatus(),
                    "reconcile must not fast-forward a partial template to FAILED");

            // A later full COMPLETED snapshot repairs it to CONFIRMED via reconcile.
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
            TxStreamItemResult repaired = stream.reconcile("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, repaired.getStatus(),
                    "a subsequent COMPLETED repairs the recovery-required template");
            assertEquals("tx-1", repaired.getTransactionHash());
        }
    }

    @Test
    void durablePresentPartiallyCompletedTemplateReattachesRecoveryRequiredThenCompletedRepairs() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();

        store.suppressConfirmOutcome = true; // binding stays DISPATCHING (crash between start+confirm)
        TxFlowStream a = durableMultiStepBuilder(gateway, store).build();
        a.start();
        TxStreamReceipt receiptA = a.submit(templateItem("pay-1", RECEIVER, 5L));
        String executionId = receiptA.executionId().orElseThrow();
        // The foreign process left the flow PARTIALLY_COMPLETED.
        gateway.putSnapshot(executionId, FlowExecutionState.PARTIALLY_COMPLETED,
                attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
        store.suppressConfirmOutcome = false;

        try (TxFlowStream b = durableMultiStepBuilder(gateway, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(1, report.reattachedItems());
            assertEquals(0, report.redispatched(), "a present snapshot is never re-dispatched");
            assertEquals(1, report.recoveryRequired());
            // BEFORE the fix reattachPresentMember uses snapshotStatus →
            // permanently FAILED. It must surface RECOVERY_REQUIRED.
            TxStreamItemResult reattached = b.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, reattached.getStatus());

            // The engine later reports COMPLETED; reconcile repairs to CONFIRMED
            // (the isFinal short-circuit never blocks a non-final RECOVERY_REQUIRED).
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.reconcile("pay-1").orElseThrow().getStatus());
        }
    }

    @Test
    void multiStepTemplateCompletedIsConfirmedAcrossLiveAndSnapshotPaths() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = multiStepBuilder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            String executionId = receipt.executionId().orElseThrow();
            // Live COMPLETED → CONFIRMED (whole-flow templateFlowStatus).
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            // A subsequent COMPLETED snapshot reconciles consistently (no-op:
            // already CONFIRMED, final and immutable).
            gateway.putSnapshot(executionId, FlowExecutionState.COMPLETED,
                    attemptData(STEP_ID, AttemptState.CONFIRMED, "tx-1"));
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    stream.reconcile("pay-1").orElseThrow().getStatus());
        }
    }

    @Test
    void multiStepTemplateFailedIsFailedAcrossLiveAndSnapshotPaths() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = multiStepBuilder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            // Live FAILED (no step confirmed) → FAILED (whole-flow templateFlowStatus).
            gateway.lastHandle().complete(new FlowExecutionResult(
                    receipt.executionId().orElseThrow(), "fp", FlowExecutionState.FAILED,
                    List.of(), null, StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.FAILED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // DEV-T1 — template-definition drift on absent re-dispatch is fail-fast:
    // re-registering a DIFFERENT definition under the same id must NOT silently
    // run the wrong flow under the original claim.
    // ------------------------------------------------------------------

    @Test
    void durableTemplateReattachWithADriftedDefinitionFailsTemplateDriftAndDispatchesNothing() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();

        // Stream A registers the SINGLE-step definition, plans + binds, then
        // crashes before the start durably happened (absent snapshot).
        TxFlowStream a = durableBuilder(gateway, store).build(); // single-step template()
        a.start();
        TxStreamReceipt receiptA = a.submit(templateItem("pay-1", RECEIVER, 5L));
        String executionId = receiptA.executionId().orElseThrow();
        String persistedFingerprint =
                store.plannedByExecution(executionId).orElseThrow().templateFingerprint();
        assertFalse(persistedFingerprint == null || persistedFingerprint.isBlank(),
                "the template fingerprint is persisted for drift detection");
        gateway.started.clear();
        gateway.handles.clear();

        // Stream B re-registers a DIFFERENT definition (two-step) under the SAME
        // id → the absent re-dispatch must fail typed, not run the drifted flow.
        // (The persisted single-step fingerprint differs from the two-step one;
        // the TXSTREAM_TEMPLATE_DRIFT outcome below is proof of the mismatch.)
        try (TxFlowStream b = durableMultiStepBuilder(gateway, store).build()) {
            ReattachReport report = b.reattach();
            assertEquals(0, report.redispatched(), "the drifted flow is never re-dispatched");
            assertEquals(0, gateway.started.size(), "nothing reaches the engine");
            TxStreamItemResult outcome = b.getItemStatus("pay-1").orElseThrow();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_TEMPLATE_DRIFT",
                    ((TxStreamException) outcome.getError()).getCode());
        }
    }

    @Test
    void durableTemplateReattachWithTheSameDefinitionRedispatchesFine() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        StubEngineGateway gateway = durableEngine();

        TxFlowStream a = durableMultiStepBuilder(gateway, store).build();
        a.start();
        TxStreamReceipt receiptA = a.submit(templateItem("pay-1", RECEIVER, 5L));
        String executionId = receiptA.executionId().orElseThrow();
        gateway.started.clear();
        gateway.handles.clear();

        // Stream B re-registers the IDENTICAL definition → fingerprint matches →
        // the absent execution re-dispatches exactly once. start() runs re-attach
        // AND enables the pump so the re-dispatch actually runs (schedulePump
        // gates on started); reattach() then returns the cached report.
        try (TxFlowStream b = durableMultiStepBuilder(gateway, store).build()) {
            b.start();
            ReattachReport report = b.reattach();
            assertEquals(1, report.redispatched());
            assertEquals(1, gateway.started.size());
            assertEquals(executionId, gateway.started.get(0).getExecutionId());
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    // ------------------------------------------------------------------
    // Cross-kind non-attach: an inline item and a template item with the same
    // item id are a typed conflict, never a false attach.
    // ------------------------------------------------------------------

    @Test
    void anInlineAndATemplateItemWithTheSameItemIdConflictNeverFalseAttach() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            stream.submit(TxWorkItem.builder("pay-1")
                    .withTxPlan(TxPlan.from(new Tx()
                            .payToAddress(RECEIVER, Amount.ada(1)).from(SENDER)))
                    .build());
            // Same item id but a TEMPLATE payload — the fingerprint is
            // structurally distinct (template field vs payload field), so this
            // is a typed conflict, never a false attach to the inline receipt.
            TxStreamDuplicateItemException conflict = assertThrows(
                    TxStreamDuplicateItemException.class,
                    () -> stream.submit(templateItem("pay-1", RECEIVER, 5L)));
            assertTrue(conflict.getMessage().contains("different content"));
            assertEquals(1, gateway.started.size(), "the template never dispatched");
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
        }
    }

    // ------------------------------------------------------------------
    // Template cancel / abort / drain (single-member execution semantics).
    // ------------------------------------------------------------------

    @Test
    void inFlightTemplateItemCooperativeCancelIsSignalledSingle() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            CancelOutcome outcome = stream.cancelItem("pay-1", "changed my mind");
            assertEquals(CancelOutcome.Kind.SIGNALLED_SINGLE, outcome.kind(),
                    "a single-member template execution cancels cooperatively");
            assertTrue(gateway.lastHandle().cancelRequested.get(),
                    "the cancel signal reached the template's handle");
            // The engine honours the signal; the item settles CANCELLED.
            gateway.lastHandle().complete(new FlowExecutionResult(
                    receipt.executionId().orElseThrow(), "fp", FlowExecutionState.CANCELLED,
                    List.of(), null, StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.CANCELLED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void abortSettlesAnInFlightTemplateCancelled() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            String executionId = receipt.executionId().orElseThrow();

            AbortReport report = stream.abort("operator abort");
            assertTrue(report.signalledExecutionIds().contains(executionId),
                    "an in-flight template execution is signalled by abort");
            assertTrue(gateway.lastHandle().cancelRequested.get());

            // The engine settles the signalled execution CANCELLED; the item
            // settles from the real engine outcome.
            gateway.lastHandle().complete(new FlowExecutionResult(executionId, "fp",
                    FlowExecutionState.CANCELLED, List.of(), null,
                    StubEngineGateway.NOW, StubEngineGateway.NOW));
            assertEquals(TxStreamItemStatus.CANCELLED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            report.quiescence().toCompletableFuture().join();
            stream.drain();
        }
    }

    @Test
    void drainAwaitsTheInFlightTemplatePromise() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = builder("payouts", gateway).build()) {
            stream.start();
            TxStreamReceipt receipt = stream.submit(templateItem("pay-1", RECEIVER, 5L));
            assertFalse(receipt.completion().toCompletableFuture().isDone(),
                    "the template is in flight");

            // drain() must await the in-flight template promise; it returns only
            // once the promise settles (no fixed sleep — the bounded get returns
            // the moment drain completes).
            CompletableFuture<Void> drained = CompletableFuture.runAsync(stream::drain);
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx");
            drained.get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ---- helpers ----

    private TxFlowStream.Builder baseBuilder(String streamId, StubEngineGateway gateway) {
        return new TxFlowStream.Builder(streamId, gateway)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxFlowStream.Builder builder(String streamId, StubEngineGateway gateway) {
        return baseBuilder(streamId, gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .template(TEMPLATE_ID, template());
    }

    private TxFlowStream.Builder multiStepBuilder(String streamId, StubEngineGateway gateway) {
        return baseBuilder(streamId, gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .template(TEMPLATE_ID, multiStepTemplate());
    }

    private TxFlowStream.Builder durableMultiStepBuilder(StubEngineGateway gateway,
                                                         TxStreamStateStore store) {
        return multiStepBuilder("payouts", gateway).stateStore(store);
    }

    private StubEngineGateway durableEngine() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.durable = true;
        return gateway;
    }

    private TxFlowStream.Builder durableBuilder(StubEngineGateway gateway,
                                                TxStreamStateStore store) {
        return builder("payouts", gateway).stateStore(store);
    }

    private TxFlowStream.Builder durableBuilderWithoutTemplate(StubEngineGateway gateway,
                                                               TxStreamStateStore store) {
        return baseBuilder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .stateStore(store);
    }

    private TxWorkItem templateItem(String itemId, String receiver, long amount) {
        return TxWorkItem.builder(itemId)
                .withTemplate(TEMPLATE_ID)
                .withBinding("receiver", receiver)
                .withBinding("amount", amount)
                .build();
    }

    private Map<String, Object> bindings(FlowExecutionRequest request) {
        return request.getBindings().asMap();
    }

    /** A parameterized, portable payout template with a single "pay" step. */
    private TxFlow template() {
        String yaml = "api_version: txflow.cardano-client.dev/v1alpha1\n"
                + "kind: TxFlow\n"
                + "metadata: {name: payout-template}\n"
                + "spec:\n"
                + "  parameters:\n"
                + "    receiver: {type: address, required: true}\n"
                + "    amount: {type: integer, required: true}\n"
                + "  steps:\n"
                + "    - id: " + STEP_ID + "\n"
                + "      transaction:\n"
                + "        tx: {intents: []}\n";
        return TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
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
                + "    - id: " + STEP_ID + "\n"
                + "      transaction:\n"
                + "        tx: {intents: []}\n"
                + "    - id: " + STEP2_ID + "\n"
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

    private Map<String, Object> attemptData(String stepId, AttemptState state, String hash) {
        return Map.of("attempts", Map.of(stepId + ":1", attempt(stepId, state, hash)));
    }

    private FlowAttemptSnapshot attempt(String stepId, AttemptState state, String hash) {
        return new FlowAttemptSnapshot(stepId, 1, state,
                new SignedPayload.InlineCbor(new byte[]{1}, "sha", hash),
                null, null, List.of(), List.of(), StubEngineGateway.NOW, null);
    }
}
