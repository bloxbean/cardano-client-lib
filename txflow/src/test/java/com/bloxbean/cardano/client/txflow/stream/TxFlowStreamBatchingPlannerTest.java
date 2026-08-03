package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowError;
import com.bloxbean.cardano.client.txflow.exec.FlowErrorCategory;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 2d {@code batching()} planner: merging compatible payment-shaped
 * items in a window into FEWER transactions with transaction-granular item
 * status. Covers payment-shaped grouping and deterministic byte-stable merged
 * transactions, non-payment items falling back to their own single-item flow,
 * per-lane separation, {@code maxItemsPerTransaction} overflow, the
 * transaction-granular projection (a merged tx's fate is shared by every
 * member), any-member fail-closed binding, and — loudly — the re-batch
 * double-pay hazard (flow-level dedup only).
 */
class TxFlowStreamBatchingPlannerTest {
    private static final String SENDER = "addr_test1vpqsender";
    private static final String SENDER_B = "addr_test1vpqsenderb";
    private static final String FUND_REF = "account://treasury";
    private static final String NAMESPACE = StreamIdentities.namespace("payouts");
    private static final String POLICY =
            "00000000000000000000000000000000000000000000000000000000";

    // ------------------------------------------------------------------
    // Payment-shaped grouping
    // ------------------------------------------------------------------

    @Test
    void fivePaymentItemsOnOneLaneMergeIntoOneFlowWithOneStepCarryingAllOutputs() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        List<String> keys = List.of("pay-1", "pay-2", "pay-3", "pay-4", "pay-5");
        String mergedStep = StreamIdentities.mergedStepId(keys);
        String executionId = StreamIdentities.executionId(NAMESPACE,
                StreamIdentities.windowClaimKey(keys));
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .stateStore(store)
                .window(WindowPolicy.count(5)).build()) {
            stream.start();
            List<TxStreamReceipt> receipts = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                receipts.add(stream.submit(payItem("pay-" + i, "recv-" + i)));
            }

            assertEquals(1, gateway.started.size(),
                    "five payments on one lane merge into ONE flow");
            FlowExecutionRequest req = gateway.started.get(0);
            assertEquals(executionId, req.getExecutionId());
            assertEquals(1, req.getDefinition().getSteps().size(), "one merged step");
            assertEquals(mergedStep, req.getDefinition().getSteps().get(0).getId());
            assertEquals(List.of("recv-1", "recv-2", "recv-3", "recv-4", "recv-5"),
                    outputAddresses(req),
                    "the merged tx carries every member's output in sorted claim-key order");
            assertEquals(SENDER, mergedTx(req).getSender(),
                    "the merged tx is funded from the lane's source");
            assertEquals(List.of("addr:" + SENDER),
                    List.copyOf(req.getSpendingResources()));

            // All five map to the ONE shared step.
            for (int i = 1; i <= 5; i++) {
                assertEquals(mergedStep, store.bindings.get("pay-" + i).stepId());
                assertEquals(executionId, store.bindings.get("pay-" + i).executionId());
            }

            gateway.lastHandle().completeConfirmed(mergedStep, "tx-merged");
            for (TxStreamReceipt r : receipts) {
                TxStreamItemResult outcome = r.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
                assertEquals("tx-merged", outcome.getTransactionHash(),
                        "every batched member shares the one transaction's hash");
            }
            assertEquals(TxStreamBatchStatus.COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    @Test
    void aSinglePaymentItemStillProducesAMergedFlowOfOne() {
        StubEngineGateway gateway = new StubEngineGateway();
        String mergedStep = StreamIdentities.mergedStepId(List.of("pay-1"));
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .build()) {
            stream.start();
            TxStreamReceipt r = stream.submit(payItem("pay-1", "recv-1"));
            assertEquals(1, gateway.started.size());
            FlowExecutionRequest req = gateway.started.get(0);
            assertEquals(mergedStep, req.getDefinition().getSteps().get(0).getId(),
                    "even a batch of one rides the merged-step derivation");
            assertEquals(List.of("recv-1"), outputAddresses(req));
            gateway.lastHandle().completeConfirmed(mergedStep, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    r.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void aFundingRefLaneMergesUsingFromRef() {
        StubEngineGateway gateway = new StubEngineGateway();
        List<String> keys = List.of("pay-a", "pay-b");
        String mergedStep = StreamIdentities.mergedStepId(keys);
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofFundingRef("treasury-lane", FUND_REF))
                .planner(TxStreamPlanner.batching())
                .window(WindowPolicy.count(2))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(payItem("pay-a", "recv-a"));
            TxStreamReceipt b = stream.submit(payItem("pay-b", "recv-b"));
            assertEquals(1, gateway.started.size());
            FlowExecutionRequest req = gateway.started.get(0);
            assertEquals(FUND_REF, mergedTx(req).getFromRef(),
                    "a funding-ref lane merges with from_ref, not from");
            assertEquals(List.of("ref:" + FUND_REF), List.copyOf(req.getSpendingResources()));
            gateway.lastHandle().completeConfirmed(mergedStep, "tx-m");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void multiAssetPaymentsPreserveEveryOutputAmountThroughTheMerge() {
        // A merge where a member pays a multi-asset Value (ADA + native tokens,
        // specific quantities) must carry the EXACT full amount list on that
        // member's merged output — not just the address.
        StubEngineGateway gateway = new StubEngineGateway();
        String mergedStep = StreamIdentities.mergedStepId(List.of("pay-1", "pay-2"));
        List<Amount> richAmounts = List.of(
                Amount.ada(2),
                Amount.asset(POLICY, "TokenA", 100),
                Amount.asset(POLICY, "TokenB", 7));
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(payItemAmounts("pay-1", "recv-1", richAmounts));
            TxStreamReceipt b = stream.submit(payItem("pay-2", "recv-2"));

            assertEquals(1, gateway.started.size(), "both payments merge into one tx");
            FlowExecutionRequest req = gateway.started.get(0);
            List<List<Amount>> outputs = outputAmounts(req);
            assertEquals(2, outputs.size(), "one merged output per member");
            // pay-1 sorts before pay-2, so its multi-asset output is first.
            assertEquals(richAmounts, outputs.get(0),
                    "the merged output carries the member's FULL multi-asset amount"
                            + " list with exact quantities, not just the address");
            assertEquals(List.of(Amount.ada(1.5)), outputs.get(1),
                    "the single-asset member's amount survives unchanged too");

            gateway.lastHandle().completeConfirmed(mergedStep, "tx-m");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void twoMembersPayingTheSameAddressBothSurviveTheMergeAsSeparateOutputs() {
        // Two members paying the SAME address must stay two distinct outputs —
        // the stream keeps two payToAddress calls; it never dedups or sums them.
        StubEngineGateway gateway = new StubEngineGateway();
        String mergedStep = StreamIdentities.mergedStepId(List.of("pay-1", "pay-2"));
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(payItem("pay-1", "recv-same"));
            TxStreamReceipt b = stream.submit(payItem("pay-2", "recv-same"));

            assertEquals(1, gateway.started.size());
            FlowExecutionRequest req = gateway.started.get(0);
            assertEquals(List.of("recv-same", "recv-same"), outputAddresses(req),
                    "two payments to one address remain two separate outputs in"
                            + " the merged tx — not deduped or summed away");
            assertEquals(2, outputAmounts(req).size(), "two distinct outputs survive");

            gateway.lastHandle().completeConfirmed(mergedStep, "tx-m");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Non-payment items are never merged
    // ------------------------------------------------------------------

    @Test
    void aNonPaymentItemGetsItsOwnSingleItemFlowAndIsNotMerged() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(3)).build()) {
            stream.start();
            stream.submit(payItem("pay-1", "recv-1"));
            stream.submit(metadataItem("meta-1", "recv-meta"));
            stream.submit(payItem("pay-2", "recv-2"));

            // Two executions serialize on the one lane; complete each to advance.
            assertEquals(1, gateway.started.size());
            gateway.handles.get(0).completeConfirmed(
                    gateway.started.get(0).getDefinition().getSteps().get(0).getId(), "tx-0");
            assertEquals(2, gateway.started.size());
            gateway.handles.get(1).completeConfirmed(
                    gateway.started.get(1).getDefinition().getSteps().get(0).getId(), "tx-1");

            FlowExecutionRequest merged = requestWithClaim(gateway,
                    StreamIdentities.windowClaimKey(List.of("pay-1", "pay-2")));
            FlowExecutionRequest metaExecution = requestWithClaim(gateway, "meta-1");
            assertNotNull(merged, "the two payment items merged into one flow");
            assertNotNull(metaExecution, "the metadata item claims under its own key, unmerged");
            assertEquals(List.of("recv-1", "recv-2"), outputAddresses(merged),
                    "only the payment items are in the batch; the metadata item is not");
            Tx metaTx = mergedTx(metaExecution);
            assertTrue(metaTx.getIntentions().stream()
                            .anyMatch(intent -> "metadata".equals(intent.getType())),
                    "the non-payment item keeps its own unmerged transaction");
        }
    }

    @Test
    void aDatumBearingPaymentIsNotBatchedBecauseTheMergeWouldDropTheDatum() {
        // A payToContract output is a PaymentIntent, but carrying a datum it is
        // NOT reproducible by payToAddress(address, amounts) — merging would
        // silently drop the datum, so it must run unmerged.
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(3)).build()) {
            stream.start();
            stream.submit(payItem("pay-1", "recv-1"));
            stream.submit(datumItem("datum-1", "recv-datum"));
            stream.submit(payItem("pay-2", "recv-2"));

            assertEquals(1, gateway.started.size());
            gateway.handles.get(0).completeConfirmed(
                    gateway.started.get(0).getDefinition().getSteps().get(0).getId(), "tx-0");
            assertEquals(2, gateway.started.size());
            gateway.handles.get(1).completeConfirmed(
                    gateway.started.get(1).getDefinition().getSteps().get(0).getId(), "tx-1");

            FlowExecutionRequest merged = requestWithClaim(gateway,
                    StreamIdentities.windowClaimKey(List.of("pay-1", "pay-2")));
            FlowExecutionRequest datumExecution = requestWithClaim(gateway, "datum-1");
            assertNotNull(merged, "only the two pure payments merged");
            assertNotNull(datumExecution, "the datum output claims under its own key, unmerged");
            assertEquals(List.of("recv-1", "recv-2"), outputAddresses(merged),
                    "the datum output is not in the batch");
        }
    }

    @Test
    void aPaymentWhoseReEmissionWouldDifferIsExcludedFromBatching() {
        // FINDING-2 positive round-trip guard: a plain payToAddress that ALSO
        // carries a reference script is a PaymentIntent whose re-emission via
        // payToAddress(address, amounts) would DIFFER (the script ref is
        // dropped). The batcher's positive equivalence guard — not a field
        // denylist — detects the difference and runs the item unmerged, so the
        // merge never silently drops the attachment. A FUTURE output-affecting
        // PaymentIntent field would be excluded by the same mechanism.
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(3)).build()) {
            stream.start();
            stream.submit(payItem("pay-1", "recv-1"));
            stream.submit(refScriptItem("ref-1", "recv-ref"));
            stream.submit(payItem("pay-2", "recv-2"));

            assertEquals(1, gateway.started.size());
            gateway.handles.get(0).completeConfirmed(
                    gateway.started.get(0).getDefinition().getSteps().get(0).getId(), "tx-0");
            assertEquals(2, gateway.started.size());
            gateway.handles.get(1).completeConfirmed(
                    gateway.started.get(1).getDefinition().getSteps().get(0).getId(), "tx-1");

            FlowExecutionRequest merged = requestWithClaim(gateway,
                    StreamIdentities.windowClaimKey(List.of("pay-1", "pay-2")));
            FlowExecutionRequest refExecution = requestWithClaim(gateway, "ref-1");
            assertNotNull(merged, "only the two pure payments merged");
            assertNotNull(refExecution,
                    "the reference-script payment claims under its own key, unmerged");
            assertEquals(List.of("recv-1", "recv-2"), outputAddresses(merged),
                    "the reference-script-bearing output is NOT in the batch");
        }
    }

    @Test
    void rejectModeFailsTheWindowWhenANonPaymentItemAppears() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamPlanner rejecting = TxStreamPlanner.batching(
                BatchingOptions.builder().allowNonPaymentSingletons(false).build());
        try (TxFlowStream stream = singleLaneBuilder(gateway, rejecting)
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt pay = stream.submit(payItem("pay-1", "recv-1"));
            TxStreamReceipt meta = stream.submit(metadataItem("meta-1", "recv-meta"));

            for (TxStreamReceipt receipt : List.of(pay, meta)) {
                TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
                TxStreamException error =
                        assertInstanceOf(TxStreamException.class, outcome.getError());
                assertEquals("TXSTREAM_PLANNER_FAILED", error.getCode(),
                        "the planner rejection surfaces through the planner isolation");
                assertEquals("TXSTREAM_BATCH_INELIGIBLE_ITEM",
                        assertInstanceOf(TxStreamException.class, error.getCause()).getCode());
            }
            assertTrue(gateway.started.isEmpty(), "nothing dispatches on a rejected window");
            assertTrue(stream.isHealthy(), "a planner rejection never kills the worker");
        }
    }

    // ------------------------------------------------------------------
    // Per-lane separation and overflow
    // ------------------------------------------------------------------

    @Test
    void mixedLanesProduceSeparateMergedFlowsPerLane() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = explicitBuilder(gateway)
                .window(WindowPolicy.count(4)).build()) {
            stream.start();
            stream.submit(laneItem("a-1", "recv-a1", "lane-a"));
            stream.submit(laneItem("b-1", "recv-b1", "lane-b"));
            stream.submit(laneItem("a-2", "recv-a2", "lane-a"));
            stream.submit(laneItem("b-2", "recv-b2", "lane-b"));

            // Two lanes, two merged flows; distinct identities dispatch concurrently.
            assertEquals(2, gateway.started.size(),
                    "each lane merges into its own flow — lanes never share a tx");
            FlowExecutionRequest laneA = requestWithSpendingResource(gateway, "addr:" + SENDER);
            FlowExecutionRequest laneB = requestWithSpendingResource(gateway, "addr:" + SENDER_B);
            assertNotNull(laneA);
            assertNotNull(laneB);
            assertEquals(List.of("recv-a1", "recv-a2"), outputAddresses(laneA));
            assertEquals(List.of("recv-b1", "recv-b2"), outputAddresses(laneB));

            for (int i = 0; i < 2; i++) {
                gateway.handles.get(i).completeConfirmed(
                        gateway.started.get(i).getDefinition().getSteps().get(0).getId(),
                        "tx-" + i);
            }
            assertEquals(TxStreamBatchStatus.COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status());
        }
    }

    @Test
    void maxItemsPerTransactionOverflowSplitsIntoMultipleMergedFlows() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxStreamPlanner planner = TxStreamPlanner.batching(
                BatchingOptions.builder().maxItemsPerTransaction(2).build());
        try (TxFlowStream stream = singleLaneBuilder(gateway, planner)
                .window(WindowPolicy.count(5)).build()) {
            stream.start();
            for (int i = 1; i <= 5; i++) {
                stream.submit(payItem("pay-" + i, "recv-" + i));
            }

            // The three merged flows serialize on the one lane: complete each to
            // free the lane for its successor.
            List<Integer> outputCounts = new ArrayList<>();
            for (int flow = 0; flow < 3; flow++) {
                assertEquals(flow + 1, gateway.started.size(),
                        "flow " + flow + " dispatches only after its predecessor completes");
                FlowExecutionRequest req = gateway.started.get(flow);
                outputCounts.add(outputAddresses(req).size());
                gateway.handles.get(flow).completeConfirmed(
                        req.getDefinition().getSteps().get(0).getId(), "tx-" + flow);
            }
            assertEquals(List.of(2, 2, 1), outputCounts,
                    "5 payments capped at 2/tx split into 2+2+1 merged flows");
        }
    }

    // ------------------------------------------------------------------
    // Determinism / byte-stability / one-submission redelivery
    // ------------------------------------------------------------------

    @Test
    void theMergedTransactionIsByteStableAcrossSubmitOrder() {
        String first = mergedYamlFor(List.of("pay-1", "pay-2", "pay-3"));
        String reordered = mergedYamlFor(List.of("pay-3", "pay-1", "pay-2"));
        assertEquals(first, reordered,
                "the merged transaction is byte-identical regardless of submit order");
    }

    @Test
    void deterministicFlowAndExecutionIdentitiesAcrossStreamInstances() {
        FlowExecutionRequest a = firstRequestFor(List.of("pay-2", "pay-1"));
        FlowExecutionRequest b = firstRequestFor(List.of("pay-1", "pay-2"));
        assertEquals(a.getExecutionId(), b.getExecutionId(),
                "execution identity derives from the sorted member keys");
        assertEquals(a.getIdempotencyKey(), b.getIdempotencyKey());
        assertEquals(a.getDefinition().getId(), b.getDefinition().getId());
        assertEquals(a.getDefinition().getSteps().get(0).getId(),
                b.getDefinition().getSteps().get(0).getId(),
                "the merged step id is a pure function of the member set");
        assertEquals("stream:payouts", a.getIdempotencyNamespace());
    }

    @Test
    void redeliveryOfTheSameBatchMatchesOneSubmissionAcrossStreamInstances() {
        StubEngineGateway shared = new StubEngineGateway();
        shared.idempotentMatch = true;
        List<String> keys = List.of("pay-1", "pay-2", "pay-3");
        String mergedStep = StreamIdentities.mergedStepId(keys);
        String executionId = StreamIdentities.executionId(NAMESPACE,
                StreamIdentities.windowClaimKey(keys));

        try (TxFlowStream first = singleLaneBuilder(shared, TxStreamPlanner.batching())
                .window(WindowPolicy.count(3)).build()) {
            first.start();
            TxStreamReceipt a = first.submit(payItem("pay-1", "recv-1"));
            first.submit(payItem("pay-2", "recv-2"));
            first.submit(payItem("pay-3", "recv-3"));
            assertEquals(executionId, shared.started.get(0).getExecutionId());
            shared.lastHandle().completeConfirmed(mergedStep, "tx-merged");
            a.completion().toCompletableFuture().join();
        }

        try (TxFlowStream second = singleLaneBuilder(shared, TxStreamPlanner.batching())
                .window(WindowPolicy.count(3)).build()) {
            second.start();
            // Reversed submit order: the identical batch matches, not re-runs.
            TxStreamReceipt a = second.submit(payItem("pay-3", "recv-3"));
            second.submit(payItem("pay-1", "recv-1"));
            second.submit(payItem("pay-2", "recv-2"));

            assertEquals(1, shared.started.size(),
                    "the identical batch MATCHES the stored execution — no second submission");
            assertEquals(List.of("start:" + executionId, "match:" + executionId),
                    shared.callLog,
                    "exactly one on-chain submission for the batch across both instances");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Transaction-granular projection
    // ------------------------------------------------------------------

    @Test
    void mergedTransactionFailureProjectsEveryMemberFailedWithTheSameHash() {
        StubEngineGateway gateway = new StubEngineGateway();
        String mergedStep = StreamIdentities.mergedStepId(List.of("pay-a", "pay-b"));
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(payItem("pay-a", "recv-a"));
            TxStreamReceipt b = stream.submit(payItem("pay-b", "recv-b"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(mergedStep, "tx-m");
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.failureAfterSubmissionAt(mergedStep, "tx-m",
                            List.of(), List.of(), new IllegalStateException("submit rejected"),
                            StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_EXECUTION_FAILED", FlowErrorCategory.SUBMISSION,
                            "failed", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            for (TxStreamReceipt r : List.of(a, b)) {
                TxStreamItemResult outcome = r.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
                assertEquals("tx-m", outcome.getTransactionHash(),
                        "one failed tx -> every member FAILED with its hash");
            }
            assertEquals(TxStreamBatchStatus.FAILED,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "a batched tx has no partial: every member fails together");
        }
    }

    @Test
    void inProgressStepInATerminalMergedFlowProjectsEveryMemberRecoveryRequiredWithHash() {
        StubEngineGateway gateway = new StubEngineGateway();
        String mergedStep = StreamIdentities.mergedStepId(List.of("pay-a", "pay-b"));
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(payItem("pay-a", "recv-a"));
            TxStreamReceipt b = stream.submit(payItem("pay-b", "recv-b"));
            StubEngineGateway.StubHandle handle = gateway.lastHandle();
            handle.submittedEvent(mergedStep, "tx-m");
            // The merged tx was submitted but never confirmed inside a terminal flow.
            handle.complete(new FlowExecutionResult(handle.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.submissionPendingAt(mergedStep, "tx-m",
                            List.of(), List.of(), new IllegalStateException("abandoned"),
                            StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_EXECUTION_FAILED", FlowErrorCategory.CONFIRMATION,
                            "flow failed", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            for (TxStreamReceipt r : List.of(a, b)) {
                TxStreamItemResult outcome = r.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.RECOVERY_REQUIRED, outcome.getStatus(),
                        "submitted-unconfirmed inside a terminal flow -> RECOVERY_REQUIRED");
                assertEquals("tx-m", outcome.getTransactionHash(), "the hash is never dropped");
            }
            assertEquals(TxStreamBatchStatus.RUNNING,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "unresolved members keep the batch RUNNING until repaired");
        }
    }

    @Test
    void independentMergedFlowsInOneWindowCanSettleToDifferentOutcomes() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = explicitBuilder(gateway)
                .window(WindowPolicy.count(4)).build()) {
            stream.start();
            stream.submit(laneItem("a-1", "recv-a1", "lane-a"));
            stream.submit(laneItem("a-2", "recv-a2", "lane-a"));
            stream.submit(laneItem("b-1", "recv-b1", "lane-b"));
            stream.submit(laneItem("b-2", "recv-b2", "lane-b"));
            assertEquals(2, gateway.started.size());

            FlowExecutionRequest laneA = requestWithSpendingResource(gateway, "addr:" + SENDER);
            FlowExecutionRequest laneB = requestWithSpendingResource(gateway, "addr:" + SENDER_B);
            StubEngineGateway.StubHandle handleA = handleFor(gateway, laneA.getExecutionId());
            StubEngineGateway.StubHandle handleB = handleFor(gateway, laneB.getExecutionId());
            String stepA = laneA.getDefinition().getSteps().get(0).getId();
            String stepB = laneB.getDefinition().getSteps().get(0).getId();

            // lane-a's merged tx confirms; lane-b's merged tx fails.
            handleA.completeConfirmed(stepA, "tx-a");
            handleB.submittedEvent(stepB, "tx-b");
            handleB.complete(new FlowExecutionResult(handleB.executionId(), "fp",
                    FlowExecutionState.FAILED,
                    List.of(FlowStepResult.failureAfterSubmissionAt(stepB, "tx-b",
                            List.of(), List.of(), new IllegalStateException("rejected"),
                            StubEngineGateway.NOW)),
                    new FlowError("TXFLOW_EXECUTION_FAILED", FlowErrorCategory.SUBMISSION,
                            "failed", null, false),
                    StubEngineGateway.NOW, StubEngineGateway.NOW));

            assertEquals(TxStreamBatchStatus.PARTIALLY_COMPLETED,
                    stream.getBatchStatus("batch-1").orElseThrow().status(),
                    "across-flow partial is possible; within a merged tx there is no partial");
        }
    }

    // ------------------------------------------------------------------
    // Two-phase binding for a batched flow
    // ------------------------------------------------------------------

    @Test
    void anyMemberBindFailureFailsTheWholeBatchedFlowBeforeStart() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        store.bindFailure = new IllegalStateException("binding storage down");
        store.bindFailureItemId = "pay-b";
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .stateStore(store)
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(payItem("pay-a", "recv-a"));
            TxStreamReceipt b = stream.submit(payItem("pay-b", "recv-b"));

            for (TxStreamReceipt r : List.of(a, b)) {
                TxStreamItemResult outcome = r.completion().toCompletableFuture().join();
                assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus(),
                        "a fail-closed binding failure settles every batched member");
                assertEquals("TXSTREAM_BINDING_FAILED",
                        assertInstanceOf(TxStreamException.class, outcome.getError()).getCode());
            }
            assertTrue(gateway.started.isEmpty(),
                    "the engine must never start a batch with an unbound member");
            assertTrue(stream.isHealthy());
        }
    }

    @Test
    void cancellingAMemberOfAnInFlightBatchedFlowIsRejectedShared() {
        StubEngineGateway gateway = new StubEngineGateway();
        String mergedStep = StreamIdentities.mergedStepId(List.of("pay-a", "pay-b"));
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(2)).build()) {
            stream.start();
            stream.submit(payItem("pay-a", "recv-a"));
            stream.submit(payItem("pay-b", "recv-b"));
            String executionId = gateway.started.get(0).getExecutionId();

            CancelOutcome outcome = stream.cancelItem("pay-a", "changed my mind");
            assertEquals(CancelOutcome.Kind.REJECTED_SHARED, outcome.kind(),
                    "a batched member can only be cancelled whole");
            assertEquals(executionId, outcome.executionId().orElseThrow());
            assertEquals(List.of("pay-a", "pay-b"), outcome.memberItemIds());
            assertFalse(gateway.lastHandle().cancelRequested.get(),
                    "a rejected member cancel must not signal the engine");

            gateway.lastHandle().completeConfirmed(mergedStep, "tx-m");
        }
    }

    // ------------------------------------------------------------------
    // Re-batch double-pay hazard (flow-level dedup only)
    // ------------------------------------------------------------------

    @Test
    void reBatchingTheSameItemIntoADifferentBatchIsANewClaimAndDoublePays() {
        StubEngineGateway shared = new StubEngineGateway();
        shared.idempotentMatch = true;

        // Batch 1: pay-x grouped with pay-1.
        String firstExecution;
        try (TxFlowStream first = singleLaneBuilder(shared, TxStreamPlanner.batching())
                .window(WindowPolicy.count(2)).build()) {
            first.start();
            first.submit(payItem("pay-1", "recv-1"));
            TxStreamReceipt x = first.submit(payItem("pay-x", "recv-x"));
            firstExecution = shared.started.get(0).getExecutionId();
            shared.lastHandle().completeConfirmed(
                    shared.started.get(0).getDefinition().getSteps().get(0).getId(), "tx-1");
            x.completion().toCompletableFuture().join();
        }

        // Batch 2: the SAME pay-x, now grouped with pay-2 -> a DIFFERENT member
        // set -> a different claim -> a genuine SECOND on-chain payment for pay-x.
        try (TxFlowStream second = singleLaneBuilder(shared, TxStreamPlanner.batching())
                .window(WindowPolicy.count(2)).build()) {
            second.start();
            second.submit(payItem("pay-2", "recv-2"));
            TxStreamReceipt x = second.submit(payItem("pay-x", "recv-x"));
            String secondExecution = shared.started.get(1).getExecutionId();

            assertNotEquals(firstExecution, secondExecution,
                    "the same item in a differently-composed batch is a NEW claim");
            assertEquals(2, shared.callLog.stream()
                            .filter(entry -> entry.startsWith("start:")).count(),
                    "batching is flow-level dedup only: pay-x is submitted in TWO batches"
                            + " — a re-batched payment double-pays. Use perItem() for"
                            + " per-item exactly-once.");
            shared.lastHandle().completeConfirmed(
                    shared.started.get(1).getDefinition().getSteps().get(0).getId(), "tx-2");
            x.completion().toCompletableFuture().join();
        }
    }

    // ------------------------------------------------------------------
    // BatchingOptions
    // ------------------------------------------------------------------

    @Test
    void batchingOptionsExposeConservativeDefaultsAndValidate() {
        assertEquals(20, BatchingOptions.defaults().maxItemsPerTransaction());
        assertTrue(BatchingOptions.defaults().allowNonPaymentSingletons());
        assertEquals(5, BatchingOptions.builder().maxItemsPerTransaction(5).build()
                .maxItemsPerTransaction());
        assertFalse(BatchingOptions.builder().allowNonPaymentSingletons(false).build()
                .allowNonPaymentSingletons());
        assertThrows(IllegalArgumentException.class,
                () -> BatchingOptions.builder().maxItemsPerTransaction(0).build());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private FlowExecutionRequest firstRequestFor(List<String> submitOrder) {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = singleLaneBuilder(gateway, TxStreamPlanner.batching())
                .window(WindowPolicy.count(submitOrder.size())).build()) {
            stream.start();
            for (String id : submitOrder) {
                stream.submit(payItem(id, "recv-" + id));
            }
            assertEquals(1, gateway.started.size());
            FlowExecutionRequest request = gateway.started.get(0);
            gateway.lastHandle().completeConfirmed(
                    request.getDefinition().getSteps().get(0).getId(), "tx-m");
            return request;
        }
    }

    private String mergedYamlFor(List<String> submitOrder) {
        return firstRequestFor(submitOrder).getDefinition().getSteps().get(0)
                .getTxPlan().toYaml();
    }

    private static Tx mergedTx(FlowExecutionRequest request) {
        return (Tx) request.getDefinition().getSteps().get(0).getTxPlan().getTxs().get(0);
    }

    private static List<String> outputAddresses(FlowExecutionRequest request) {
        return mergedTx(request).getIntentions().stream()
                .filter(intent -> intent instanceof PaymentIntent)
                .map(intent -> ((PaymentIntent) intent).getAddress())
                .toList();
    }

    private static List<List<Amount>> outputAmounts(FlowExecutionRequest request) {
        return mergedTx(request).getIntentions().stream()
                .filter(intent -> intent instanceof PaymentIntent)
                .map(intent -> ((PaymentIntent) intent).getAmounts())
                .toList();
    }

    private static FlowExecutionRequest requestWithClaim(StubEngineGateway gateway,
                                                         String claimKey) {
        return gateway.started.stream()
                .filter(request -> claimKey.equals(request.getIdempotencyKey()))
                .findFirst().orElse(null);
    }

    private static FlowExecutionRequest requestWithSpendingResource(StubEngineGateway gateway,
                                                                    String resource) {
        return gateway.started.stream()
                .filter(request -> request.getSpendingResources().contains(resource))
                .findFirst().orElse(null);
    }

    private static StubEngineGateway.StubHandle handleFor(StubEngineGateway gateway,
                                                          String executionId) {
        return gateway.handles.stream()
                .filter(handle -> handle.executionId().equals(executionId))
                .findFirst().orElseThrow();
    }

    private TxFlowStream.Builder singleLaneBuilder(StubEngineGateway gateway,
                                                   TxStreamPlanner planner) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts-lane", SENDER))
                .planner(planner)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    private TxFlowStream.Builder explicitBuilder(StubEngineGateway gateway) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(laneName -> {
                    switch (laneName) {
                        case "lane-a":
                            return ResolvedLane.ofAddress("lane-a", SENDER);
                        case "lane-b":
                            return ResolvedLane.ofAddress("lane-b", SENDER_B);
                        default:
                            return null;
                    }
                })
                .planner(TxStreamPlanner.batching())
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    /** A pure single-payment item with no funding source (materialized to the lane). */
    private TxWorkItem payItem(String itemId, String receiver) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(receiver, Amount.ada(1.5))));
    }

    /** A pure single-payment item paying an explicit (possibly multi-asset) amount list. */
    private TxWorkItem payItemAmounts(String itemId, String receiver, List<Amount> amounts) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(receiver, amounts)));
    }

    /**
     * A payToAddress that ALSO attaches a reference script — a PaymentIntent
     * whose re-emission via {@code payToAddress(address, amounts)} would drop
     * the script ref, so it is NOT batchable (FINDING-2 positive guard).
     */
    private TxWorkItem refScriptItem(String itemId, String receiver) {
        byte[] scriptRefBytes = new byte[] {(byte) 0x82, 0x01, 0x00};
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(receiver, List.of(Amount.ada(1.5)),
                        scriptRefBytes)));
    }

    /** A pure single-payment item on a named explicit lane. */
    private TxWorkItem laneItem(String itemId, String receiver, String lane) {
        return TxWorkItem.builder(itemId)
                .withTxPlan(TxPlan.from(new Tx().payToAddress(receiver, Amount.ada(1.5))))
                .withLane(lane)
                .build();
    }

    /** A datum-bearing contract payment: a PaymentIntent that is NOT pure (not batchable). */
    private TxWorkItem datumItem(String itemId, String receiver) {
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToContract(receiver, Amount.ada(1.5),
                        com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData.of(42))));
    }

    /** A NON-payment item: a payment plus attached metadata (portable, not batchable). */
    private TxWorkItem metadataItem(String itemId, String receiver) {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(674), "batching-test");
        return TxWorkItem.fromTxPlan(itemId,
                TxPlan.from(new Tx().payToAddress(receiver, Amount.ada(1.5))
                        .attachMetadata(metadata)));
    }
}
