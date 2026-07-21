package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.exec.ExecutionEventView;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration 1B lane concurrency: the dispatcher serializes on canonical
 * spending identity (never on lane name), different identities run
 * concurrently under the global {@code maxInFlight} cap with round-robin
 * fairness, and lane resolution failures are typed per-item planning
 * failures.
 */
class TxFlowStreamLaneConcurrencyTest {
    private static final String SENDER_A = "addr_test1vpqsendera";
    private static final String SENDER_B = "addr_test1vpqsenderb";
    private static final String SENDER_C = "addr_test1vpqsenderc";
    private static final String RECEIVER = "addr_test1vpqreceiver";
    private static final String STEP_ID = StreamIdentities.GENERATED_STEP_ID;

    // ------------------------------------------------------------------
    // Multi-lane parallelism and same-lane FIFO
    // ------------------------------------------------------------------

    @Test
    void itemsOnTwoLanesRunInFlightSimultaneouslyWhileSameLaneStaysSerialFifo() {
        StubEngineGateway gateway = new StubEngineGateway();
        LaneIdentityResolver resolver = addressResolver(
                Map.of("lane-a", SENDER_A, "lane-b", SENDER_B));
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).build()) {
            stream.start();
            TxStreamReceipt a1 = stream.submit(laneItem("a-1", "lane-a"));
            TxStreamReceipt a2 = stream.submit(laneItem("a-2", "lane-a"));
            TxStreamReceipt b1 = stream.submit(laneItem("b-1", "lane-b"));

            assertEquals(2, gateway.started.size(),
                    "one execution per lane identity may be in flight");
            assertEquals(2, stream.getStats().inFlightCount(),
                    "both lanes must be in flight simultaneously");
            assertEquals(1, stream.getStats().pendingBufferSize(),
                    "the same-lane follower must queue");
            assertEquals(List.of("addr:" + SENDER_A),
                    List.copyOf(gateway.started.get(0).getSpendingResources()));
            assertEquals(List.of("addr:" + SENDER_B),
                    List.copyOf(gateway.started.get(1).getSpendingResources()));

            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-a1");
            assertEquals(3, gateway.started.size(),
                    "completing the lane head must dispatch the same lane's next item FIFO");
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
            assertEquals("lane-a", a1.current().getLaneName());
            assertEquals("lane-b", b1.current().getLaneName());
        }
    }

    @Test
    void differentLanesDispatchConcurrentlyOnRealExecutor() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        // Both engine starts must be inside start() at the same time; a serial
        // dispatcher would park the first start on this latch forever.
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
        LaneIdentityResolver resolver = addressResolver(
                Map.of("lane-a", SENDER_A, "lane-b", SENDER_B));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = explicitBuilder(gateway, resolver)
                .executor(pool)
                .build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(laneItem("a-1", "lane-a"));
            TxStreamReceipt second = stream.submit(laneItem("b-1", "lane-b"));

            assertTrue(bothStartsEntered.await(10, TimeUnit.SECONDS),
                    "dispatch of different lanes must run concurrently");
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
    void aliasLaneNamesResolvingToOneIdentityShareOneFifo() {
        StubEngineGateway gateway = new StubEngineGateway();
        // Two labels, one wallet: identical canonical identity and scope.
        LaneIdentityResolver resolver = laneName -> ResolvedLane.ofAddress(laneName, SENDER_A);
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(laneItem("x-1", "alias-a"));
            TxStreamReceipt second = stream.submit(laneItem("x-2", "alias-b"));

            assertEquals(1, gateway.started.size(),
                    "alias lane names are one lane: never two in flight for one identity");
            assertEquals(1, stream.getStats().inFlightCount());
            assertEquals(1, stream.getStats().pendingBufferSize());

            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(2, gateway.started.size(), "the alias FIFO drains serially");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    second.completion().toCompletableFuture().join().getStatus());
            assertEquals("alias-a", first.current().getLaneName());
            assertEquals("alias-b", second.current().getLaneName());
        }
    }

    // ------------------------------------------------------------------
    // Lane resolution failures (typed, per item, never startup errors)
    // ------------------------------------------------------------------

    @Test
    void overlappingFundingScopesWithDifferentIdentitiesFailLaterLaneTyped() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        LaneIdentityResolver resolver = laneName -> "first".equals(laneName)
                ? new ResolvedLane("first", "identity-one", LaneFundingScope.address(SENDER_A))
                : new ResolvedLane("second", "identity-two", LaneFundingScope.address(SENDER_A));
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).stateStore(store).build()) {
            stream.start();
            TxStreamReceipt ok = stream.submit(laneItem("f-1", "first"));
            TxStreamReceipt overlapping = stream.submit(laneItem("s-1", "second"));

            TxStreamItemResult failed = overlapping.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, failed.getStatus());
            TxStreamException error = assertInstanceOf(TxStreamException.class, failed.getError());
            assertEquals("TXSTREAM_LANE_SCOPE_OVERLAP", error.getCode());
            assertTrue(error.getMessage().contains("identity-one"));
            assertTrue(error.getMessage().contains("identity-two"));
            assertEquals(1, gateway.started.size(), "the overlapping lane's item never dispatches");
            assertEquals(1, store.calls.stream()
                    .filter(call -> call.startsWith("register:")).count());
            assertTrue(stream.isHealthy());

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    ok.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void unresolvableLaneFailsItemTypedAndDoesNotPoisonTheStream() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        LaneIdentityResolver resolver = laneName -> {
            if ("ghost".equals(laneName)) return null;
            if ("broken".equals(laneName)) throw new IllegalStateException("resolver backend down");
            return ResolvedLane.ofAddress(laneName, SENDER_A);
        };
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).stateStore(store).build()) {
            stream.start();
            TxStreamItemResult nullResolved = stream.submit(laneItem("g-1", "ghost"))
                    .completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, nullResolved.getStatus());
            assertEquals("TXSTREAM_LANE_UNRESOLVED", assertInstanceOf(TxStreamException.class,
                    nullResolved.getError()).getCode());

            TxStreamItemResult thrown = stream.submit(laneItem("b-1", "broken"))
                    .completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, thrown.getStatus());
            assertEquals("TXSTREAM_LANE_UNRESOLVED", assertInstanceOf(TxStreamException.class,
                    thrown.getError()).getCode());

            assertTrue(gateway.started.isEmpty(), "unresolved items never reach the engine");
            assertTrue(store.calls.isEmpty(), "unresolved items are never registered");
            assertTrue(stream.isHealthy());
            assertTrue(stream.getItemStatus("g-1").isEmpty(),
                    "an unresolved item is failed-and-released, retained nowhere");
            assertTrue(stream.getItemStatus("b-1").isEmpty(),
                    "a resolver-outage item is failed-and-released, retained nowhere");

            TxStreamReceipt good = stream.submit(laneItem("ok-1", "good"));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    good.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void unresolvedLaneRedeliveryAfterResolverRecoversDispatchesNormally() {
        StubEngineGateway gateway = new StubEngineGateway();
        AtomicBoolean outage = new AtomicBoolean(true);
        AtomicInteger resolutions = new AtomicInteger();
        LaneIdentityResolver resolver = laneName -> {
            resolutions.incrementAndGet();
            if (outage.get()) {
                throw new IllegalStateException("resolver backend down");
            }
            return ResolvedLane.ofAddress(laneName, SENDER_A);
        };
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).build()) {
            stream.start();
            TxStreamReceipt duringOutage = stream.submit(laneItem("g-1", "flaky"));
            TxStreamItemResult failed = duringOutage.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, failed.getStatus());
            assertEquals("TXSTREAM_LANE_UNRESOLVED", assertInstanceOf(TxStreamException.class,
                    failed.getError()).getCode());
            assertTrue(stream.getItemStatus("g-1").isEmpty(),
                    "the outage-era failure is released, not retained");

            outage.set(false);
            TxStreamReceipt redelivered = stream.submit(laneItem("g-1", "flaky"));
            assertNotSame(duringOutage, redelivered,
                    "the redelivery retries fresh instead of attaching to the outage failure");
            assertEquals(1, gateway.started.size(), "the recovered item dispatches normally");
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    redelivered.completion().toCompletableFuture().join().getStatus());
            assertEquals(2, resolutions.get(),
                    "the failure is never cached; the successful resolution is cached once");
        }
    }

    @Test
    void itemWithoutLaneUnderExplicitPolicyFailsTypedLaneRequired() {
        StubEngineGateway gateway = new StubEngineGateway();
        LaneIdentityResolver resolver = laneName -> ResolvedLane.ofAddress(laneName, SENDER_A);
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).build()) {
            stream.start();
            TxStreamItemResult outcome = stream.submit(
                            TxWorkItem.fromTxPlan("no-lane", plainPlan()))
                    .completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, outcome.getStatus());
            assertEquals("TXSTREAM_LANE_REQUIRED", assertInstanceOf(TxStreamException.class,
                    outcome.getError()).getCode());
            assertTrue(gateway.started.isEmpty());
        }
    }

    @Test
    void resolverIsInvokedOncePerLaneNameAndCached() {
        StubEngineGateway gateway = new StubEngineGateway();
        Map<String, AtomicInteger> resolutions = new ConcurrentHashMap<>();
        LaneIdentityResolver resolver = laneName -> {
            resolutions.computeIfAbsent(laneName, ignored -> new AtomicInteger())
                    .incrementAndGet();
            return ResolvedLane.ofAddress(laneName, "hot".equals(laneName) ? SENDER_A : SENDER_B);
        };
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).build()) {
            stream.start();
            stream.submit(laneItem("h-1", "hot"));
            stream.submit(laneItem("h-2", "hot"));
            stream.submit(laneItem("h-3", "hot"));
            stream.submit(laneItem("h-1", "hot"));   // redelivery attach also uses the cache
            stream.submit(laneItem("c-1", "cold"));

            assertEquals(1, resolutions.get("hot").get(), "hot lane must resolve exactly once");
            assertEquals(1, resolutions.get("cold").get(), "cold lane must resolve exactly once");

            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-h1");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-c1");
            gateway.handles.get(2).completeConfirmed(STEP_ID, "tx-h2");
            gateway.handles.get(3).completeConfirmed(STEP_ID, "tx-h3");
            stream.drain();
        }
    }

    @Test
    void singleLanePolicyRejectsForeignLaneNamesAndAcceptsItsOwn() {
        StubEngineGateway gateway = new StubEngineGateway();
        RecordingStateStore store = new RecordingStateStore();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.single(ResolvedLane.ofAddress("payouts-lane", SENDER_A)))
                .stateStore(store)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt matching = stream.submit(TxWorkItem.builder("ok-1")
                    .withTxPlan(plainPlan()).withLane("payouts-lane").build());
            assertEquals(1, gateway.started.size(), "the matching lane name dispatches normally");

            TxStreamItemResult foreign = stream.submit(TxWorkItem.builder("bad-1")
                            .withTxPlan(plainPlan()).withLane("treasury").build())
                    .completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, foreign.getStatus());
            TxStreamException error = assertInstanceOf(TxStreamException.class, foreign.getError());
            assertEquals("TXSTREAM_LANE_MISMATCH", error.getCode());
            assertTrue(error.getMessage().contains("treasury"));
            assertEquals(1, gateway.started.size(), "a foreign lane name never dispatches");
            assertEquals(1, store.calls.stream()
                    .filter(call -> call.startsWith("register:")).count());

            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    matching.completion().toCompletableFuture().join().getStatus());
        }
    }

    @Test
    void laneNameIsTrimmedAndTheTrimmedValueDrivesMatchingAndFingerprint() {
        StubEngineGateway gateway = new StubEngineGateway();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.single(ResolvedLane.ofAddress("payouts-lane", SENDER_A)))
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(TxWorkItem.builder("ok-1")
                    .withTxPlan(plainPlan()).withLane("  payouts-lane  ").build());
            assertEquals(1, gateway.started.size(),
                    "an untrimmed lane name must match the single lane after trimming");
            // The trimmed value feeds the fingerprint: redelivery with the
            // already-trimmed name is identical content and attaches.
            assertSame(first, stream.submit(TxWorkItem.builder("ok-1")
                    .withTxPlan(plainPlan()).withLane("payouts-lane").build()));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Concurrent first-use lane resolution
    // ------------------------------------------------------------------

    @Test
    void concurrentFirstUseResolutionOfAliasAndOverlappingLaneNames() throws Exception {
        StubEngineGateway gateway = new StubEngineGateway();
        Map<String, AtomicInteger> resolutions = new ConcurrentHashMap<>();
        LaneIdentityResolver resolver = laneName -> {
            resolutions.computeIfAbsent(laneName, ignored -> new AtomicInteger())
                    .incrementAndGet();
            switch (laneName) {
                case "alias-a":
                case "alias-b":
                    // Two labels, one wallet: same identity and scope.
                    return ResolvedLane.ofAddress(laneName, SENDER_A);
                case "over-1":
                    return new ResolvedLane("over-1", "identity-one",
                            LaneFundingScope.address(SENDER_B));
                default:
                    // Same scope as over-1 under a different identity: overlap.
                    return new ResolvedLane("over-2", "identity-two",
                            LaneFundingScope.address(SENDER_B));
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).build()) {
            stream.start();

            // Alias pair racing first use: both must land on ONE lane FIFO.
            CountDownLatch aliasGo = new CountDownLatch(1);
            Future<TxStreamReceipt> aliasFirst = pool.submit(() -> {
                aliasGo.await(10, TimeUnit.SECONDS);
                return stream.submit(laneItem("x-1", "alias-a"));
            });
            Future<TxStreamReceipt> aliasSecond = pool.submit(() -> {
                aliasGo.await(10, TimeUnit.SECONDS);
                return stream.submit(laneItem("x-2", "alias-b"));
            });
            aliasGo.countDown();
            TxStreamReceipt x1 = aliasFirst.get(10, TimeUnit.SECONDS);
            TxStreamReceipt x2 = aliasSecond.get(10, TimeUnit.SECONDS);
            assertEquals(1, gateway.started.size(),
                    "alias names racing first use still share one lane: one in flight");
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-1");
            assertEquals(2, gateway.started.size(), "the shared FIFO drains serially");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-2");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    x1.completion().toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    x2.completion().toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
            assertEquals(1, resolutions.get("alias-a").get(),
                    "each alias name resolves exactly once despite the race");
            assertEquals(1, resolutions.get("alias-b").get());

            // Overlapping pair racing first use: exactly one claims the
            // scope; the other's item fails typed — never both, never neither.
            CountDownLatch overlapGo = new CountDownLatch(1);
            Future<TxStreamReceipt> overlapFirst = pool.submit(() -> {
                overlapGo.await(10, TimeUnit.SECONDS);
                return stream.submit(laneItem("o-1", "over-1"));
            });
            Future<TxStreamReceipt> overlapSecond = pool.submit(() -> {
                overlapGo.await(10, TimeUnit.SECONDS);
                return stream.submit(laneItem("o-2", "over-2"));
            });
            overlapGo.countDown();
            TxStreamReceipt o1 = overlapFirst.get(10, TimeUnit.SECONDS);
            TxStreamReceipt o2 = overlapSecond.get(10, TimeUnit.SECONDS);
            TxStreamReceipt loser = o1.current().getStatus() == TxStreamItemStatus.FAILED
                    ? o1 : o2;
            TxStreamReceipt winner = loser == o1 ? o2 : o1;
            TxStreamItemResult overlapOutcome = loser.completion().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertEquals(TxStreamItemStatus.FAILED, overlapOutcome.getStatus());
            assertEquals("TXSTREAM_LANE_SCOPE_OVERLAP",
                    assertInstanceOf(TxStreamException.class,
                            overlapOutcome.getError()).getCode());
            assertEquals(3, gateway.started.size(),
                    "exactly one of the overlapping lanes may dispatch");
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-3");
            assertEquals(TxStreamItemStatus.CONFIRMED, winner.completion()
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Per-item dispatch task rejection inside the pump
    // ------------------------------------------------------------------

    @Test
    void perItemDispatchTaskRejectionFailsThatItemTypedReleasesItsSlotAndOthersComplete() {
        StubEngineGateway gateway = new StubEngineGateway();
        LaneIdentityResolver resolver = addressResolver(
                Map.of("lane-a", SENDER_A, "lane-b", SENDER_B));
        AllowanceExecutor executor = new AllowanceExecutor();
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(resolver)
                .executor(executor)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            TxStreamReceipt first = stream.submit(laneItem("a-1", "lane-a"));
            assertEquals(1, gateway.started.size());

            // The pump task is accepted; the per-item dispatch task it
            // submits for the second lane's head is rejected.
            executor.allowRemaining.set(1);
            TxStreamReceipt second = stream.submit(laneItem("b-1", "lane-b"));

            TxStreamItemResult rejected = second.completion().toCompletableFuture().join();
            assertEquals(TxStreamItemStatus.FAILED, rejected.getStatus());
            assertEquals("TXSTREAM_UNHEALTHY", assertInstanceOf(TxStreamException.class,
                    rejected.getError()).getCode());
            assertFalse(stream.isHealthy());
            assertEquals(1, stream.getStats().inFlightCount(),
                    "the rejected item's slot must be released; only a-1 stays in flight");
            assertEquals(0, stream.getStats().pendingBufferSize());

            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-a1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    first.completion().toCompletableFuture().join().getStatus(),
                    "the already dispatched item still completes");
            assertEquals(0, stream.getStats().inFlightCount());
            stream.drain();
        }
    }

    // ------------------------------------------------------------------
    // Per-identity exclusivity under load
    // ------------------------------------------------------------------

    @Test
    void perIdentityExclusivityHoldsUnderLoadOnRealPool() throws Exception {
        int itemCount = 20;
        Map<String, AtomicInteger> inFlightByIdentity = new ConcurrentHashMap<>();
        AtomicInteger maxPerIdentity = new AtomicInteger();
        ExecutorService completer = Executors.newSingleThreadExecutor();
        EngineGateway gateway = new EngineGateway() {
            @Override
            public ExecutionHandle start(FlowExecutionRequest request) {
                String identity = request.getSpendingResources().iterator().next();
                AtomicInteger inFlight = inFlightByIdentity
                        .computeIfAbsent(identity, ignored -> new AtomicInteger());
                maxPerIdentity.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                CompletableFuture<FlowExecutionResult> future = new CompletableFuture<>();
                completer.execute(() -> {
                    // Release the identity BEFORE publishing completion, so a
                    // successor dispatched off the freed lane can never
                    // observe a stale in-flight count.
                    inFlight.decrementAndGet();
                    future.complete(new FlowExecutionResult(request.getExecutionId(), "fp",
                            FlowExecutionState.COMPLETED,
                            List.of(FlowStepResult.successAt(STEP_ID,
                                    "tx-" + request.getExecutionId(), List.of(), List.of(),
                                    StubEngineGateway.NOW)),
                            null, StubEngineGateway.NOW, StubEngineGateway.NOW));
                });
                return new CompletableHandle(request.getExecutionId(), future);
            }

            @Override
            public Optional<FlowExecutionSnapshot> executionSnapshot(String executionId) {
                return Optional.empty();
            }

            @Override
            public Optional<ExecutionEventView> executionEvents(String executionId,
                                                                long afterSequence, int limit) {
                return Optional.empty();
            }
        };
        LaneIdentityResolver resolver = addressResolver(Map.of(
                "lane-a", SENDER_A, "lane-b", SENDER_B, "lane-c", SENDER_C));
        String[] lanes = {"lane-a", "lane-b", "lane-c"};
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try (TxFlowStream stream = new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(resolver)
                .executor(pool)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC))
                .build()) {
            stream.start();
            List<TxStreamReceipt> receipts = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) {
                receipts.add(stream.submit(laneItem("load-" + i, lanes[i % lanes.length])));
            }
            for (TxStreamReceipt receipt : receipts) {
                assertEquals(TxStreamItemStatus.CONFIRMED, receipt.completion()
                                .toCompletableFuture().get(10, TimeUnit.SECONDS).getStatus(),
                        "every item must settle");
            }
            assertEquals(1, maxPerIdentity.get(),
                    "at most one execution may ever be in flight per canonical identity");
            stream.drain();
        } finally {
            pool.shutdownNow();
            completer.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Global cap and fairness
    // ------------------------------------------------------------------

    @Test
    void maxInFlightCapBoundsConcurrentExecutionsAcrossLanesAndAllComplete() {
        StubEngineGateway gateway = new StubEngineGateway();
        LaneIdentityResolver resolver = addressResolver(
                Map.of("lane-a", SENDER_A, "lane-b", SENDER_B, "lane-c", SENDER_C));
        try (TxFlowStream stream = explicitBuilder(gateway, resolver)
                .maxInFlight(2)
                .build()) {
            stream.start();
            TxStreamReceipt a = stream.submit(laneItem("a-1", "lane-a"));
            TxStreamReceipt b = stream.submit(laneItem("b-1", "lane-b"));
            TxStreamReceipt c = stream.submit(laneItem("c-1", "lane-c"));

            assertEquals(2, gateway.started.size(), "the global cap must hold at 2");
            assertEquals(2, stream.getStats().inFlightCount());
            assertEquals(1, stream.getStats().pendingBufferSize());

            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-a");
            assertEquals(3, gateway.started.size(),
                    "a freed slot must dispatch the waiting lane");
            assertEquals(2, stream.getStats().inFlightCount(), "never more than 2 in flight");

            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-b");
            gateway.handles.get(2).completeConfirmed(STEP_ID, "tx-c");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    a.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    b.completion().toCompletableFuture().join().getStatus());
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    c.completion().toCompletableFuture().join().getStatus());
            assertEquals(0, stream.getStats().inFlightCount());
        }
    }

    @Test
    void laneSchedulingIsRoundRobinSoOneLanesBacklogCannotStarveOthers() {
        StubEngineGateway gateway = new StubEngineGateway();
        LaneIdentityResolver resolver = addressResolver(
                Map.of("lane-a", SENDER_A, "lane-b", SENDER_B));
        try (TxFlowStream stream = explicitBuilder(gateway, resolver)
                .maxInFlight(1)
                .build()) {
            stream.start();
            stream.submit(laneItem("a-1", "lane-a"));
            stream.submit(laneItem("a-2", "lane-a"));
            stream.submit(laneItem("b-1", "lane-b"));

            assertEquals(List.of("addr:" + SENDER_A),
                    List.copyOf(gateway.started.get(0).getSpendingResources()));
            gateway.handles.get(0).completeConfirmed(STEP_ID, "tx-a1");
            assertEquals(List.of("addr:" + SENDER_B),
                    List.copyOf(gateway.started.get(1).getSpendingResources()),
                    "the other lane must run before lane-a's backlog continues");
            gateway.handles.get(1).completeConfirmed(STEP_ID, "tx-b1");
            assertEquals(List.of("addr:" + SENDER_A),
                    List.copyOf(gateway.started.get(2).getSpendingResources()));
            gateway.handles.get(2).completeConfirmed(STEP_ID, "tx-a2");
            stream.drain();
        }
    }

    @Test
    void explicitPolicyWithoutResolverIsRejectedAtBuild() {
        StubEngineGateway gateway = new StubEngineGateway();
        TxFlowStream.Builder builder = new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .executor(Runnable::run);
        IllegalStateException failure = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(failure.getMessage().contains("laneResolver"));
    }

    @Test
    void itemsOnDifferentLanesWithSameContentAreDistinctFingerprints() {
        StubEngineGateway gateway = new StubEngineGateway();
        LaneIdentityResolver resolver = addressResolver(
                Map.of("lane-a", SENDER_A, "lane-b", SENDER_B));
        try (TxFlowStream stream = explicitBuilder(gateway, resolver).build()) {
            stream.start();
            TxStreamReceipt original = stream.submit(laneItem("pay-1", "lane-a"));
            // Same item id, same payload, different lane: different content.
            TxStreamDuplicateItemException conflict = assertThrows(
                    TxStreamDuplicateItemException.class,
                    () -> stream.submit(laneItem("pay-1", "lane-b")));
            assertEquals("pay-1", conflict.getItemId());
            // Same lane redelivery still attaches.
            assertSame(original, stream.submit(laneItem("pay-1", "lane-a")));
            gateway.lastHandle().completeConfirmed(STEP_ID, "tx-1");
            assertEquals(TxStreamItemStatus.CONFIRMED,
                    original.completion().toCompletableFuture().join().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private LaneIdentityResolver addressResolver(Map<String, String> addressByLane) {
        return laneName -> {
            String address = addressByLane.get(laneName);
            return address != null ? ResolvedLane.ofAddress(laneName, address) : null;
        };
    }

    private TxFlowStream.Builder explicitBuilder(StubEngineGateway gateway,
                                                 LaneIdentityResolver resolver) {
        return new TxFlowStream.Builder("payouts", gateway)
                .lanes(LanePolicy.explicit())
                .laneResolver(resolver)
                .executor(Runnable::run)
                .clock(Clock.fixed(StubEngineGateway.NOW, ZoneOffset.UTC));
    }

    /** Plan without a funding source: the lane's scope is materialized onto it. */
    private TxPlan plainPlan() {
        return TxPlan.from(new Tx().payToAddress(RECEIVER, Amount.ada(1.5)));
    }

    private TxWorkItem laneItem(String itemId, String lane) {
        return TxWorkItem.builder(itemId)
                .withTxPlan(plainPlan())
                .withLane(lane)
                .build();
    }

    /**
     * Inline executor that, once armed, accepts a fixed number of further
     * tasks and rejects the rest — deterministic per-task rejection without
     * killing the task that is currently running.
     */
    static final class AllowanceExecutor implements Executor {
        final AtomicInteger allowRemaining = new AtomicInteger(Integer.MAX_VALUE);

        @Override
        public void execute(Runnable command) {
            if (allowRemaining.get() != Integer.MAX_VALUE
                    && allowRemaining.getAndDecrement() <= 0) {
                throw new RejectedExecutionException("dispatch task rejected");
            }
            command.run();
        }
    }

    /** Minimal completable engine handle without events. */
    static final class CompletableHandle implements EngineGateway.ExecutionHandle {
        private final String executionId;
        private final CompletableFuture<FlowExecutionResult> future;

        CompletableHandle(String executionId, CompletableFuture<FlowExecutionResult> future) {
            this.executionId = executionId;
            this.future = future;
        }

        @Override
        public String executionId() {
            return executionId;
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public FlowExecutionResult resultIfDone() {
            return future.getNow(null);
        }

        @Override
        public CompletionStage<FlowExecutionResult> completion() {
            return future;
        }

        @Override
        public List<FlowEvent> events() {
            return List.of();
        }

        @Override
        public List<FlowEvent> eventsAfter(long sequence) {
            return List.of();
        }

        @Override
        public void requestCancel(String reason) {
        }
    }
}
