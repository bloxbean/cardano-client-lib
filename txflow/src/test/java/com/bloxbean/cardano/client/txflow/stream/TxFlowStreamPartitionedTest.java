package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 2c {@link LanePolicy#partitioned(PartitionedLanes)}: application-
 * provided lane addresses, deterministic {@code hash(idempotencyKey) % N} lane
 * assignment, the one-time fan-out bootstrap (runs once, matches on restart),
 * lane-scoped coin selection pinning, and typed bootstrap-failure handling.
 */
class TxFlowStreamPartitionedTest {
    private static final String FUNDING = "addr_test1vpqfunding";
    private static final String LANE_0 = "addr_test1vpqlane0";
    private static final String LANE_1 = "addr_test1vpqlane1";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;
    private static final BigInteger TEN_ADA = BigInteger.valueOf(10_000_000L);

    // ------------------------------------------------------------------
    // Hash assignment: deterministic + balanced + config validation
    // ------------------------------------------------------------------

    @Test
    void partitionAssignmentIsDeterministicAndReasonablyBalanced() {
        int n = 4;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < 400; i++) {
            String key = "order-" + i;
            int index = StreamIdentities.partitionIndex(key, n);
            assertTrue(index >= 0 && index < n, "index in range");
            assertEquals(index, StreamIdentities.partitionIndex(key, n),
                    "the same key always lands on the same lane (stable across restarts)");
            counts.merge(index, 1, Integer::sum);
        }
        assertEquals(n, counts.size(), "every lane receives some items");
        for (int lane = 0; lane < n; lane++) {
            assertTrue(counts.get(lane) > 400 / n / 3,
                    "lane " + lane + " is reasonably balanced (" + counts.get(lane) + ")");
        }
    }

    @Test
    void partitionedConfigValidatesLaneCountDistinctnessAndSeed() {
        assertThrows(IllegalArgumentException.class, () -> PartitionedLanes.fromAddress(FUNDING)
                .laneAddresses(List.of()).seedPerLane(Amount.ada(1)).build(), "N >= 1");
        assertThrows(IllegalArgumentException.class, () -> PartitionedLanes.fromAddress(FUNDING)
                .laneAddresses(List.of(LANE_0, LANE_0)).seedPerLane(Amount.ada(1)).build(),
                "distinct lane addresses");
        assertThrows(IllegalArgumentException.class, () -> PartitionedLanes.fromAddress(FUNDING)
                .lane(LANE_0).seedPerLane(Amount.lovelace(BigInteger.ZERO)).build(),
                "positive seed");
        assertThrows(NullPointerException.class, () -> PartitionedLanes.fromAddress(FUNDING)
                .lane(LANE_0).build(), "seed required");
        PartitionedLanes ok = PartitionedLanes.fromAddress(FUNDING)
                .lane(LANE_0).lane(LANE_1).seedPerLane(Amount.ada(5)).build();
        assertEquals(2, ok.laneCount());
        assertEquals(List.of(LANE_0, LANE_1), ok.laneAddresses());
        assertTrue(ok.bootstrapEnabled());
    }

    // ------------------------------------------------------------------
    // Fan-out bootstrap: runs once, then matches (never re-splits)
    // ------------------------------------------------------------------

    @Test
    void bootstrapRunsOnceBuildingTheSplitFlowFromTheFundingSource() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.immediateResult = bootstrapOnly(gateway);
        try (TxFlowStream stream = partitioned(gateway, twoLaneConfig(true)).build()) {
            stream.start();

            BootstrapReport report = stream.bootstrap();
            assertEquals(BootstrapReport.Outcome.RAN, report.outcome());
            assertEquals(2, report.laneCount());
            assertTrue(report.executionId().isPresent());

            FlowExecutionRequest split = bootstrapRequest(gateway);
            assertEquals(List.of("addr:" + FUNDING),
                    List.copyOf(split.getSpendingResources()),
                    "the split spends from the funding source");
            Tx tx = bootstrapTx(split);
            assertEquals(FUNDING, tx.getSender(), "the split transaction's sender is the funding source");
            Map<String, BigInteger> outputs = paymentOutputs(tx);
            assertEquals(2, outputs.size(), "one seed output per lane");
            assertEquals(TEN_ADA, outputs.get(LANE_0));
            assertEquals(TEN_ADA, outputs.get(LANE_1));
        }
    }

    @Test
    void bootstrapMatchesOnASecondInstanceAndNeverReSplits() {
        StubEngineGateway shared = new StubEngineGateway();
        shared.idempotentMatch = true;
        shared.immediateResult = bootstrapOnly(shared);

        try (TxFlowStream first = partitioned(shared, twoLaneConfig(true)).build()) {
            first.start();
            assertEquals(BootstrapReport.Outcome.RAN, first.bootstrap().outcome());
        }
        // A second stream instance over the same engine: MATCH, no re-split.
        try (TxFlowStream second = partitioned(shared, twoLaneConfig(true)).build()) {
            second.start();
            assertEquals(BootstrapReport.Outcome.MATCHED, second.bootstrap().outcome());
        }

        long splitStarts = shared.started.stream()
                .filter(request -> request.getIdempotencyKey().startsWith("bootstrap:"))
                .count();
        assertEquals(1, splitStarts, "the wallet is split exactly once across instances");
        assertTrue(shared.callLog.stream().anyMatch(entry -> entry.startsWith("match:")),
                "the second instance matched the existing split");
    }

    @Test
    void bootstrapFailureFailsStartTypedAndDispatchesNoItems() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.immediateResult = request -> request.getIdempotencyKey().startsWith("bootstrap:")
                ? failed(request) : null;
        try (TxFlowStream stream = partitioned(gateway, twoLaneConfig(true)).build()) {
            TxStreamException failure = assertThrows(TxStreamException.class, stream::start);
            assertEquals("TXSTREAM_BOOTSTRAP_FAILED", failure.getCode());
            assertEquals(BootstrapReport.Outcome.FAILED, stream.bootstrap().outcome());
            assertTrue(gateway.started.stream()
                            .allMatch(request -> request.getIdempotencyKey().startsWith("bootstrap:")),
                    "no item is dispatched against unfunded lanes when the bootstrap fails");
        }
    }

    @Test
    void openAbortsExactlyOnceWhenBootstrapFails() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.immediateResult = request -> request.getIdempotencyKey().startsWith("bootstrap:")
                ? failed(request) : null;
        AtomicInteger closedEvents = new AtomicInteger();
        TxStreamEventListener listener = new TxStreamEventListener() {
            @Override
            public void onStreamClosed(String streamId) {
                closedEvents.incrementAndGet();
            }
        };

        TxStreamException failure = assertThrows(TxStreamException.class,
                () -> partitioned(gateway, twoLaneConfig(true))
                        .eventListener(listener)
                        .open());

        assertEquals("TXSTREAM_BOOTSTRAP_FAILED", failure.getCode());
        assertEquals(1, closedEvents.get(), "the failed open must abort exactly once");
        assertTrue(gateway.started.stream()
                        .allMatch(request -> request.getIdempotencyKey().startsWith("bootstrap:")),
                "startup cleanup must not dispatch work against unfunded lanes");
    }

    @Test
    void bootstrapDisabledSubmitsNoSplitFlowAndAssumesPreFundedLanes() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = partitioned(gateway, twoLaneConfig(false)).build()) {
            stream.start();
            assertEquals(BootstrapReport.Outcome.DISABLED, stream.bootstrap().outcome());
            assertTrue(gateway.started.isEmpty(), "no fan-out split is submitted");

            String key = firstKeyForLane(0, 2);
            TxStreamReceipt receipt = stream.submit(sourcelessItem(key));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Lane-scoped coin selection + concurrency
    // ------------------------------------------------------------------

    @Test
    void eachItemIsPinnedToItsAssignedLaneAddressWithoutMutatingTheCallerPlan() {
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.immediateResult = this::completedWithStep;
        try (TxFlowStream stream = partitioned(gateway, twoLaneConfig(false)).build()) {
            stream.start();
            String key = firstKeyForLane(1, 2);   // lands on lane 1 (LANE_1)
            TxPlan callerPlan = TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1)));
            TxStreamReceipt receipt = stream.submit(TxWorkItem.builder("pin-1")
                    .withTxPlan(callerPlan).withIdempotencyKey(key).build());

            assertEquals(TxStreamItemStatus.CONFIRMED,
                    receipt.completion().toCompletableFuture().join().getStatus());
            assertEquals("part-1-payouts", receipt.current().getLaneName());
            assertEquals(List.of("addr:" + LANE_1),
                    List.copyOf(gateway.started.get(0).getSpendingResources()));
            Tx dispatched = bootstrapTx(gateway.started.get(0));
            assertEquals(LANE_1, dispatched.getSender(),
                    "the dispatched transaction is pinned to the assigned lane address");
            // The caller's plan is a defensive copy source: never mutated.
            assertNull(((Tx) callerPlan.getTxs().get(0)).getSender(),
                    "the caller's plan is not mutated by lane pinning");
        }
    }

    @Test
    void anItemDrawingOutsideItsAssignedLaneFailsScopeViolation() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = partitioned(gateway, twoLaneConfig(false)).build()) {
            stream.start();
            String key = firstKeyForLane(0, 2);   // lane 0 == LANE_0
            TxPlan foreign = TxPlan.from(new Tx().from(RECEIVER).payToAddress(LANE_0, Amount.ada(1)));
            TxStreamException outcome = assertThrows(TxStreamException.class,
                    () -> stream.submit(TxWorkItem.builder("foreign-1")
                            .withTxPlan(foreign).withIdempotencyKey(key).build()));
            assertEquals("TXSTREAM_LANE_SCOPE_VIOLATION", outcome.getCode());
            assertTrue(stream.getItemStatus("foreign-1").isEmpty());
            assertTrue(gateway.started.isEmpty(), "a scope-violating item never reaches the engine");
        }
    }

    @Test
    void itemsDispatchConcurrentlyAcrossPartitionLanesOnRealExecutor() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = partitioned(gateway, twoLaneConfig(false))
                .executor(pool)
                .build()) {
            stream.start();   // bootstrap disabled: no split, lanes pre-funded
            CountDownLatch bothStartsEntered = new CountDownLatch(2);
            CountDownLatch bothHandlesCreated = new CountDownLatch(2);
            gateway.startHook = () -> {
                bothStartsEntered.countDown();
                try {
                    if (!bothStartsEntered.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("second lane's dispatch never arrived");
                    }
                } catch (InterruptedException interrupt) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupt);
                }
            };
            gateway.handleCreatedHook = bothHandlesCreated::countDown;

            TxStreamReceipt lane0 = stream.submit(sourcelessItem(firstKeyForLane(0, 2)));
            TxStreamReceipt lane1 = stream.submit(sourcelessItem(firstKeyForLane(1, 2)));

            assertTrue(bothStartsEntered.await(10, TimeUnit.SECONDS),
                    "items on different partition lanes must dispatch concurrently");
            assertTrue(bothHandlesCreated.await(10, TimeUnit.SECONDS));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-0");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED, lane0.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED, lane1.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void partitionIndexForASinglePartitionIsAlwaysZero() {
        for (int i = 0; i < 200; i++) {
            assertEquals(0, StreamIdentities.partitionIndex("order-" + i, 1),
                    "a single-partition stream always assigns lane 0");
        }
    }

    @Test
    void aMidFlightBootstrapCrashLeftNonTerminalFailsTheNextStartWithoutReSplittingOrDispatching() {
        // A prior instance crashed mid-split, leaving the bootstrap execution
        // non-terminal (RECOVERY_REQUIRED). The next start() awaits the bootstrap
        // and sees a non-COMPLETED terminal result → FAILED report → typed throw.
        StubEngineGateway gateway = new StubEngineGateway();
        gateway.immediateResult = request -> request.getIdempotencyKey().startsWith("bootstrap:")
                ? recoveryRequired(request) : null;
        try (TxFlowStream stream = partitioned(gateway, twoLaneConfig(true)).build()) {
            TxStreamException failure = assertThrows(TxStreamException.class, stream::start);
            assertEquals("TXSTREAM_BOOTSTRAP_FAILED", failure.getCode());
            assertEquals(BootstrapReport.Outcome.FAILED, stream.bootstrap().outcome());
            assertEquals(1, gateway.started.size(),
                    "the split is attempted once — a non-terminal outcome is never re-split");
            assertTrue(gateway.started.stream()
                            .allMatch(request -> request.getIdempotencyKey().startsWith("bootstrap:")),
                    "no item is dispatched against unfunded lanes");
        }
    }

    // ------------------------------------------------------------------
    // FINDING-1: the partitioned dispatch gate (reattach then start)
    // ------------------------------------------------------------------

    @Test
    void reattachThenFailingBootstrapSettlesRedispatchedItemsAndDispatchesNothing() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        String key = firstKeyForLane(0, 2);
        // Instance 1: bootstrap runs, one item dispatches (in flight, no snapshot),
        // then the process is dropped without draining.
        StubEngineGateway engine1 = new StubEngineGateway();
        engine1.durable = true;
        engine1.immediateResult = bootstrapOnly(engine1);
        TxFlowStream a = durablePartitioned(engine1, store, twoLaneConfig(true)).build();
        a.start();
        a.submit(sourcelessItem(key));

        // Instance 2: a fresh engine whose bootstrap FAILS. reattach() BEFORE start()
        // re-dispatches the absent item, but the dispatch gate keeps it queued.
        StubEngineGateway engine2 = new StubEngineGateway();
        engine2.durable = true;
        engine2.immediateResult = request -> request.getIdempotencyKey().startsWith("bootstrap:")
                ? failed(request) : null;
        try (TxFlowStream b = durablePartitioned(engine2, store, twoLaneConfig(true)).build()) {
            ReattachReport reattach = b.reattach();
            assertEquals(1, reattach.redispatched(), "the absent item is queued for re-dispatch");
            assertTrue(engine2.started.isEmpty(),
                    "the gate keeps re-dispatched work from running before start()");

            TxStreamException failure = assertThrows(TxStreamException.class, b::start);
            assertEquals("TXSTREAM_BOOTSTRAP_FAILED", failure.getCode());

            // close() returns (settled promises, no hang) and NOTHING dispatched.
            b.close();
            TxStreamItemResult outcome = b.getItemStatus("item-" + key).orElseThrow();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_BOOTSTRAP_FAILED", assertInstanceOf(TxStreamException.class,
                    outcome.getError()).getCode());
            assertTrue(engine2.started.stream()
                            .allMatch(request -> request.getIdempotencyKey().startsWith("bootstrap:")),
                    "no re-dispatched item ever dispatched onto an unfunded lane");
        }
    }

    @Test
    void reattachThenSucceedingBootstrapDispatchesRedispatchedItemsOntoFundedLanes() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        String key = firstKeyForLane(0, 2);
        StubEngineGateway engine1 = new StubEngineGateway();
        engine1.durable = true;
        engine1.immediateResult = bootstrapOnly(engine1);
        TxFlowStream a = durablePartitioned(engine1, store, twoLaneConfig(true)).build();
        a.start();
        a.submit(sourcelessItem(key));

        // Instance 2: a fresh engine whose bootstrap succeeds. The re-dispatched item
        // runs on its funded lane and completes.
        StubEngineGateway engine2 = new StubEngineGateway();
        engine2.durable = true;
        engine2.immediateResult = bootstrapOnly(engine2);
        TxFlowStream b = durablePartitioned(engine2, store, twoLaneConfig(true)).build();
        try {
            ReattachReport reattach = b.reattach();
            assertEquals(1, reattach.redispatched());
            assertTrue(engine2.started.isEmpty(),
                    "the gate keeps re-dispatched work from running before start()");

            b.start();
            assertEquals(BootstrapReport.Outcome.RAN, b.bootstrap().outcome());
            assertTrue(engine2.started.stream()
                            .anyMatch(request -> !request.getIdempotencyKey().startsWith("bootstrap:")),
                    "the re-dispatched item dispatches once the bootstrap funds the lanes");

            engine2.lastHandle().completeConfirmed(STEP_ID, "tx-redispatched");
            TxStreamItemResult outcome = b.getItemStatus("item-" + key).orElseThrow();
            assertEquals(TxStreamItemStatus.CONFIRMED, outcome.getStatus());
            assertEquals("tx-redispatched", outcome.getTransactionHash());
            b.close();
        } finally {
            b.abort("test complete");
        }
    }

    @Test
    void lanePinningSurvivesDurablePersistPortableReencodeAndRedispatch() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        String key = firstKeyForLane(1, 2);   // pins to lane 1 (LANE_1)
        StubEngineGateway engine1 = new StubEngineGateway();
        engine1.durable = true;
        engine1.immediateResult = bootstrapOnly(engine1);
        TxFlowStream a = durablePartitioned(engine1, store, twoLaneConfig(true)).build();
        a.start();
        a.submit(sourcelessItem(key));   // materialized from(LANE_1), started, persisted

        StubEngineGateway engine2 = new StubEngineGateway();
        engine2.durable = true;
        engine2.immediateResult = bootstrapOnly(engine2);
        TxFlowStream b = durablePartitioned(engine2, store, twoLaneConfig(true)).build();
        try {
            b.start();   // bootstrap RAN, then re-attach re-dispatches the pinned item

            FlowExecutionRequest redispatched = engine2.started.stream()
                    .filter(request -> !request.getIdempotencyKey().startsWith("bootstrap:"))
                    .findFirst().orElseThrow(() -> new AssertionError("item was not re-dispatched"));
            // The canonical spending identity — the load-bearing lane pin — is
            // carried through durable persist + re-dispatch verbatim.
            assertEquals(List.of("addr:" + LANE_1),
                    List.copyOf(redispatched.getSpendingResources()),
                    "the re-dispatched execution stays pinned to its lane's spending identity");
            // And the transaction body's from was pinned to the lane address before
            // persistence, so the persisted-and-re-parsed portable flow still names it.
            String persistedFlow = store.plannedByExecution(redispatched.getExecutionId())
                    .orElseThrow(() -> new AssertionError("no persisted plan for the re-dispatched item"))
                    .portableFlow();
            assertTrue(persistedFlow.contains(LANE_1),
                    "the persisted portable flow keeps the tx pinned to the lane address");

            // Let the re-dispatched item complete so graceful close() drains cleanly.
            engine2.lastHandle().completeConfirmed(STEP_ID, "tx-redispatched");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.getItemStatus("item-" + key).orElseThrow().getStatus());
            b.close();
        } finally {
            b.abort("test complete");
        }
    }

    // ------------------------------------------------------------------
    // FINDING-2: durable config-drift detection
    // ------------------------------------------------------------------

    @Test
    void reorderedLaneAddressesOnADurableStreamFailStartWithConfigDriftAndSubmitNoSplit() {
        SharedDurableTxStreamStore store = new SharedDurableTxStreamStore();
        // Instance 1: bootstrap runs and persists the fingerprint for [LANE_0, LANE_1].
        StubEngineGateway engine1 = new StubEngineGateway();
        engine1.durable = true;
        engine1.immediateResult = bootstrapOnly(engine1);
        try (TxFlowStream a = durablePartitioned(engine1, store, PartitionedLanes.fromAddress(FUNDING)
                .laneAddresses(List.of(LANE_0, LANE_1)).seedPerLane(Amount.ada(10)).build()).build()) {
            a.start();
            assertEquals(BootstrapReport.Outcome.RAN, a.bootstrap().outcome());
        }

        // Instance 2: same addresses, REORDERED — the fingerprint drifts.
        StubEngineGateway engine2 = new StubEngineGateway();
        engine2.durable = true;
        engine2.immediateResult = bootstrapOnly(engine2);
        try (TxFlowStream b = durablePartitioned(engine2, store, PartitionedLanes.fromAddress(FUNDING)
                .laneAddresses(List.of(LANE_1, LANE_0)).seedPerLane(Amount.ada(10)).build()).build()) {
            TxStreamException failure = assertThrows(TxStreamException.class, b::start);
            assertEquals("TXSTREAM_BOOTSTRAP_CONFIG_DRIFT", failure.getCode());
            assertEquals(BootstrapReport.Outcome.FAILED, b.bootstrap().outcome());
            assertTrue(engine2.started.isEmpty(),
                    "no split is submitted when the bootstrap configuration drifts");
        }
    }

    @Test
    void notApplicableForNonPartitionedStreams() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lane(ResolvedLane.ofAddress("payouts", FUNDING))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            assertEquals(BootstrapReport.Outcome.NOT_APPLICABLE, stream.bootstrap().outcome());
            stream.start();
            assertEquals(BootstrapReport.Outcome.NOT_APPLICABLE, stream.bootstrap().outcome());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PartitionedLanes twoLaneConfig(boolean bootstrap) {
        return PartitionedLanes.fromAddress(FUNDING)
                .laneAddresses(List.of(LANE_0, LANE_1))
                .seedPerLane(Amount.ada(10))
                .bootstrap(bootstrap)
                .build();
    }

    private TxFlowStream.Builder partitioned(StubEngineGateway gateway, PartitionedLanes config) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.partitioned(config))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    /** A durable partitioned stream over a shared, restart-surviving store. */
    private TxFlowStream.Builder durablePartitioned(StubEngineGateway gateway,
            TxStreamStateStore store, PartitionedLanes config) {
        return partitioned(gateway, config).stateStore(store);
    }

    /** A non-terminal (RECOVERY_REQUIRED) engine result — a mid-flight bootstrap crash. */
    private FlowExecutionResult recoveryRequired(FlowExecutionRequest request) {
        return new FlowExecutionResult(request.getExecutionId(), "fp",
                FlowExecutionState.RECOVERY_REQUIRED, List.of(), null,
                StubEngineGateway.NOW, StubEngineGateway.NOW);
    }

    /** Completes only the fan-out bootstrap; item handles stay in flight. */
    private Function<FlowExecutionRequest, FlowExecutionResult> bootstrapOnly(
            StubEngineGateway gateway) {
        return request -> request.getIdempotencyKey().startsWith("bootstrap:")
                ? completedWithStep(request) : null;
    }

    private FlowExecutionResult completedWithStep(FlowExecutionRequest request) {
        return new FlowExecutionResult(request.getExecutionId(), "fp",
                FlowExecutionState.COMPLETED,
                List.of(FlowStepResult.successAt(STEP_ID, "tx-boot", List.of(), List.of(),
                        StubEngineGateway.NOW)),
                null, StubEngineGateway.NOW, StubEngineGateway.NOW);
    }

    private FlowExecutionResult failed(FlowExecutionRequest request) {
        return new FlowExecutionResult(request.getExecutionId(), "fp",
                FlowExecutionState.FAILED, List.of(), null,
                StubEngineGateway.NOW, StubEngineGateway.NOW);
    }

    private FlowExecutionRequest bootstrapRequest(StubEngineGateway gateway) {
        return gateway.started.stream()
                .filter(request -> request.getIdempotencyKey().startsWith("bootstrap:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no bootstrap request was submitted"));
    }

    private Tx bootstrapTx(FlowExecutionRequest request) {
        TxPlan plan = request.getDefinition().getStep(STEP_ID).orElseThrow().getTxPlan();
        return (Tx) plan.getTxs().get(0);
    }

    private Map<String, BigInteger> paymentOutputs(Tx tx) {
        Map<String, BigInteger> outputs = new LinkedHashMap<>();
        for (TxIntent intent : tx.getIntentions()) {
            if (intent instanceof PaymentIntent) {
                PaymentIntent payment = (PaymentIntent) intent;
                BigInteger lovelace = payment.getAmounts().stream()
                        .filter(amount -> CardanoConstants.LOVELACE.equals(amount.getUnit()))
                        .map(Amount::getQuantity)
                        .findFirst()
                        .orElse(BigInteger.ZERO);
                outputs.put(payment.getAddress(), lovelace);
            }
        }
        return outputs;
    }

    /** A source-less item whose claim key deterministically selects a lane. */
    private TxWorkItem sourcelessItem(String key) {
        return TxWorkItem.builder("item-" + key)
                .withTxPlan(TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1))))
                .withIdempotencyKey(key)
                .build();
    }

    /** First {@code "k<i>"} key that hashes to the given lane, for N lanes. */
    private String firstKeyForLane(int lane, int n) {
        for (int i = 0; i < 10_000; i++) {
            String key = "k" + i;
            if (StreamIdentities.partitionIndex(key, n) == lane) {
                return key;
            }
        }
        throw new AssertionError("no key mapped to lane " + lane);
    }
}
