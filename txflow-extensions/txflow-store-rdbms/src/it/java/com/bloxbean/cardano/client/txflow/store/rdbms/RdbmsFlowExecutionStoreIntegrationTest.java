package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import com.bloxbean.cardano.client.txflow.store.ResourceLease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdbmsFlowExecutionStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void fileBackedStoreRetainsSnapshotsEventsClaimsAndLeaseEpochsAcrossReopen() {
        String url = "jdbc:h2:file:"
                + temporaryDirectory.resolve("txflow-restart").toAbsolutePath();
        long executionEpoch;
        long resourceEpoch;
        try (RdbmsFlowExecutionStore first = RdbmsFlowExecutionStore.builder()
                .jdbcUrl(url).clock(fixedClock()).build()) {
            FlowExecutionSnapshot initial = snapshot("restart", "request",
                    Map.of("bindings", List.of("alice"), "retry", 2));
            first.createOrGet("tenant", "restart-operation", initial);
            ExecutionLease execution = first.acquireExecutionLease(
                    "restart", "owner", NOW, LEASE_DURATION);
            ResourceLease resource = first.acquireResourceLease(
                    "wallet", "restart", "owner", NOW, LEASE_DURATION);
            executionEpoch = execution.epoch();
            resourceEpoch = resource.epoch();
            FlowEvent event = new FlowEvent(1, "restart", FlowEventType.EXECUTION_STARTED,
                    NOW.plusSeconds(1), "step", null, Map.of("attempt", 1L));
            first.append("restart", 0, new MutationFence(execution, List.of(resource)),
                    List.of(event), current -> current.withState(FlowExecutionState.RUNNING,
                            NOW.plusSeconds(1), Map.of("bindings", List.of("alice"),
                                    "retry", 2, "resources", Set.of("wallet"))));
        }

        try (RdbmsFlowExecutionStore reopened = RdbmsFlowExecutionStore.builder()
                .jdbcUrl(url).schemaManagement(SchemaManagement.VALIDATE)
                .clock(fixedClock()).build()) {
            FlowExecutionSnapshot recovered = reopened.get("restart").orElseThrow();
            assertEquals(FlowExecutionState.RUNNING, recovered.state());
            assertEquals(Set.of("wallet"), recovered.data().get("resources"));
            assertEquals(FlowEventType.EXECUTION_STARTED,
                    reopened.readEvents("restart", 0, 10).events().get(0).type());
            assertFalse(reopened.createOrGet("tenant", "restart-operation",
                    snapshot("ignored", "request", Map.of())).created());

            ExecutionLease execution = reopened.acquireExecutionLease(
                    "restart", "owner", NOW.plusSeconds(1), LEASE_DURATION);
            ResourceLease resource = reopened.acquireResourceLease(
                    "wallet", "restart", "owner", NOW.plusSeconds(1), LEASE_DURATION);
            assertTrue(execution.epoch() > executionEpoch);
            assertTrue(resource.epoch() > resourceEpoch);
        }
    }

    @Test
    void concurrentDifferentExecutionIdsProduceOneClaimWithoutOrphans() throws Exception {
        try (RdbmsFlowExecutionStore store = memoryStore("claim-race")) {
            int callers = 16;
            ExecutorService executor = Executors.newFixedThreadPool(callers);
            CountDownLatch ready = new CountDownLatch(callers);
            CountDownLatch start = new CountDownLatch(1);
            List<String> ids = new ArrayList<>();
            List<Callable<IdempotencyClaimResult>> calls = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                String id = "candidate-" + i;
                ids.add(id);
                calls.add(() -> {
                    ready.countDown();
                    start.await();
                    return store.createOrGet("tenant", "one-operation",
                            snapshot(id, "same-request", Map.of()));
                });
            }
            try {
                List<Future<IdempotencyClaimResult>> futures = calls.stream()
                        .map(executor::submit).toList();
                ready.await();
                start.countDown();
                List<IdempotencyClaimResult> results = new ArrayList<>();
                for (Future<IdempotencyClaimResult> future : futures) {
                    results.add(future.get());
                }
                assertEquals(1, results.stream()
                        .filter(IdempotencyClaimResult::created).count());
                String winner = results.get(0).snapshot().executionId();
                assertTrue(results.stream().allMatch(result ->
                        winner.equals(result.snapshot().executionId())));
                assertEquals(1, ids.stream().filter(id -> store.get(id).isPresent()).count());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void concurrentCompareAndSetAppendHasExactlyOneCommit() throws Exception {
        try (RdbmsFlowExecutionStore store = memoryStore("append-race")) {
            store.createOrGet("tenant", "append-race",
                    snapshot("append-race", "request", Map.of()));
            ExecutionLease lease = store.acquireExecutionLease(
                    "append-race", "owner", NOW, LEASE_DURATION);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            AtomicInteger committed = new AtomicInteger();
            AtomicInteger conflicted = new AtomicInteger();
            Callable<Void> append = () -> {
                ready.countDown();
                start.await();
                try {
                    store.append("append-race", 0, MutationFence.executionOnly(lease),
                            List.of(new FlowEvent(1, "append-race",
                                    FlowEventType.EXECUTION_STARTED, NOW, null, null, Map.of())),
                            current -> current.withState(FlowExecutionState.RUNNING,
                                    NOW.plusSeconds(1), Map.of()));
                    committed.incrementAndGet();
                } catch (FlowStoreException failure) {
                    if (!"TXFLOW_REVISION_CONFLICT".equals(failure.getCode())) throw failure;
                    conflicted.incrementAndGet();
                }
                return null;
            };
            try {
                Future<Void> first = executor.submit(append);
                Future<Void> second = executor.submit(append);
                ready.await();
                start.countDown();
                first.get();
                second.get();
            } finally {
                executor.shutdownNow();
            }
            assertEquals(1, committed.get());
            assertEquals(1, conflicted.get());
            assertEquals(1, store.get("append-race").orElseThrow().revision());
            assertEquals(1, store.readEvents("append-race", 0, 10).events().size());
        }
    }

    @Test
    void concurrentResourceAcquisitionHasOneOwner() throws Exception {
        try (RdbmsFlowExecutionStore store = memoryStore("resource-race")) {
            store.createOrGet("tenant", "first",
                    snapshot("first", "request", Map.of()));
            store.createOrGet("tenant", "second",
                    snapshot("second", "request", Map.of()));
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger acquired = new AtomicInteger();
            AtomicInteger conflicted = new AtomicInteger();
            List<Callable<Void>> calls = List.of(
                    resourceCall(store, "first", "owner-one", ready, start,
                            acquired, conflicted),
                    resourceCall(store, "second", "owner-two", ready, start,
                            acquired, conflicted));
            try {
                List<Future<Void>> futures = calls.stream().map(executor::submit).toList();
                ready.await();
                start.countDown();
                for (Future<Void> future : futures) future.get();
            } finally {
                executor.shutdownNow();
            }
            assertEquals(1, acquired.get());
            assertEquals(1, conflicted.get());
        }
    }

    @Test
    @Timeout(10)
    void appendSamplesExpiryAfterEveryFenceRowLockIsHeld() throws Exception {
        String url = "jdbc:h2:mem:fence-linearization-"
                + temporaryDirectory.hashCode() + ";DB_CLOSE_DELAY=-1";
        RecordingClock clock = new RecordingClock(NOW);
        ResourceLockObservingDialect dialect = new ResourceLockObservingDialect();
        try (RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
                .jdbcUrl(url)
                .dialect(dialect)
                .clock(clock)
                .build()) {
            store.createOrGet("tenant", "fence-linearization",
                    snapshot("fence-linearization", "request", Map.of()));
            ExecutionLease execution = store.acquireExecutionLease(
                    "fence-linearization", "owner", NOW, Duration.ofMinutes(1));
            ResourceLease resource = store.acquireResourceLease(
                    "wallet", "fence-linearization", "owner", NOW,
                    Duration.ofSeconds(10));
            clock.resetReads();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try (Connection blocker = DriverManager.getConnection(url)) {
                blocker.setAutoCommit(false);
                try (PreparedStatement statement = blocker.prepareStatement(
                        "SELECT resource_id FROM txflow_resource_lease "
                                + "WHERE resource_id = ? FOR UPDATE")) {
                    statement.setString(1, "wallet");
                    try (ResultSet row = statement.executeQuery()) {
                        assertTrue(row.next());
                    }
                }

                Future<FlowExecutionSnapshot> append = executor.submit(() -> store.append(
                        "fence-linearization", 0,
                        new MutationFence(execution, List.of(resource)), List.of(),
                        current -> current));
                assertTrue(dialect.awaitResourceLockAttempt(),
                        "append did not reach the blocked resource fence");
                assertEquals(0, clock.reads(),
                        "expiry must not be sampled before all fence locks are held");

                clock.advance(Duration.ofSeconds(11));
                blocker.commit();
                ExecutionException failure = assertThrows(
                        ExecutionException.class, append::get);
                FlowStoreException storeFailure = (FlowStoreException) failure.getCause();
                assertEquals("TXFLOW_RESOURCE_LEASE_EXPIRED", storeFailure.getCode());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private Callable<Void> resourceCall(RdbmsFlowExecutionStore store,
                                        String executionId, String owner,
                                        CountDownLatch ready, CountDownLatch start,
                                        AtomicInteger acquired, AtomicInteger conflicted) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                store.acquireResourceLease(
                        "wallet", executionId, owner, NOW, LEASE_DURATION);
                acquired.incrementAndGet();
            } catch (FlowStoreException failure) {
                if (!"TXFLOW_RESOURCE_LEASE_CONFLICT".equals(failure.getCode())) throw failure;
                conflicted.incrementAndGet();
            }
            return null;
        };
    }

    private RdbmsFlowExecutionStore memoryStore(String name) {
        return RdbmsFlowExecutionStore.builder()
                .jdbcUrl("jdbc:h2:mem:" + name + "-" + temporaryDirectory.hashCode())
                .clock(fixedClock()).build();
    }

    private FlowExecutionSnapshot snapshot(String executionId, String requestFingerprint,
                                           Map<String, Object> data) {
        return new FlowExecutionSnapshot(executionId, "definition", requestFingerprint,
                FlowExecutionState.CREATED, 0, 0, 0, NOW, data);
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static final class ResourceLockObservingDialect implements TxFlowSqlDialect {
        private final CountDownLatch resourceLockAttempt = new CountDownLatch(1);

        @Override
        public String name() {
            return "H2 test dialect";
        }

        @Override
        public boolean accepts(String jdbcUrl) {
            return H2Dialect.INSTANCE.accepts(jdbcUrl);
        }

        @Override
        public String schemaResource() {
            return H2Dialect.INSTANCE.schemaResource();
        }

        @Override
        public void validateDatabase(Connection connection) throws SQLException {
            H2Dialect.INSTANCE.validateDatabase(connection);
        }

        @Override
        public String forUpdate(String selectSql) {
            if (selectSql.contains("FROM txflow_resource_lease")) {
                resourceLockAttempt.countDown();
            }
            return TxFlowSqlDialect.super.forUpdate(selectSql);
        }

        private boolean awaitResourceLockAttempt() throws InterruptedException {
            return resourceLockAttempt.await(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingClock extends Clock {
        private final AtomicReference<Instant> current;
        private final AtomicInteger reads = new AtomicInteger();

        private RecordingClock(Instant initial) {
            current = new AtomicReference<>(initial);
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
            reads.incrementAndGet();
            return current.get();
        }

        private void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }

        private void resetReads() {
            reads.set(0);
        }

        private int reads() {
            return reads.get();
        }
    }
}
