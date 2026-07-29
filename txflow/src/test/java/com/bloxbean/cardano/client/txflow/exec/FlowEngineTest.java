package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.FlowStoreTextPolicy;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.compile.CompiledTxFlow;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.recovery.FlowRecoveryRequest;
import com.bloxbean.cardano.client.txflow.store.contract.AdjustableClock;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class FlowEngineTest {
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Test
    void requiresCallerOwnedExecutor() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> engineBuilder().build()).getMessage().contains("executor must be supplied"));
    }

    @Test
    void rejectsNonPositiveInMemoryIdempotencyCapacity() {
        IllegalArgumentException failure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> engineBuilder().executor(Runnable::run)
                        .maxInMemoryIdempotencyClaims(0).build());
        assertTrue(failure.getMessage().contains("maxInMemoryIdempotencyClaims"));
    }

    @Test
    void durableExecutionRequiresIndependentMaintenanceExecutor() {
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));

        NullPointerException failure = org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> engineBuilder().executor(new QueuedExecutor()).store(store).build());

        assertTrue(failure.getMessage().contains("maintenanceExecutor"));
    }

    @Test
    void cancellationBeforeCallerRunsTaskIsDeterministic() {
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("cancelled").build());

        assertTrue(handle.cancel());
        executor.runNext();

        assertEquals(FlowExecutionState.CANCELLED, handle.await().state());
        assertTrue(handle.getEvents().stream().anyMatch(e -> e.type() == FlowEventType.EXECUTION_CANCELLED));
    }

    @Test
    void equalIdempotencyRequestReturnsSameHandleAndMismatchConflicts() {
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor).build();
        TxFlow definition = definition();
        FlowExecutionHandle first = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("one").idempotency("tenant", "key").build());
        FlowExecutionHandle same = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("two").idempotency("tenant", "key").build());
        assertSame(first, same);

        FlowExecutionHandle conflict = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("three").idempotency("tenant", "key")
                .spendingResource("different").build());
        assertEquals("TXFLOW_IDEMPOTENCY_CONFLICT", conflict.await().error().code());
    }

    @Test
    void inMemoryIdempotencyCapacityIsBoundedWithoutEvictingClaims() {
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maxInMemoryIdempotencyClaims(1).build();
        FlowExecutionRequest firstRequest = FlowExecutionRequest.builder(definition())
                .executionId("first").idempotency("tenant", "first-key").build();
        FlowExecutionHandle first = engine.start(firstRequest);

        assertSame(first, engine.start(FlowExecutionRequest.builder(definition())
                .executionId("same").idempotency("tenant", "first-key").build()));
        FlowExecutionResult rejected = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("second").idempotency("tenant", "second-key").build()).await();

        assertEquals("TXFLOW_IDEMPOTENCY_CAPACITY_EXCEEDED", rejected.error().code());
        assertEquals(FlowErrorCategory.RESOURCE, rejected.error().category());
    }

    @Test
    void distinctExecutionIdsForOneDefinitionRunConcurrentlyAndEventsStayOrdered() {
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor).build();
        FlowExecutionHandle first = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("concurrent-one").build());
        FlowExecutionHandle second = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("concurrent-two").build());

        assertFalse(first == second);
        assertEquals("concurrent-one", first.getExecutionId());
        assertEquals("concurrent-two", second.getExecutionId());
        assertEquals(List.of(1L, 2L), first.getEventsAfter(0).stream()
                .map(FlowEvent::sequence).toList());
        assertTrue(first.getEventsAfter(2).isEmpty());
    }

    @Test
    void activeExecutionIdRequiresMatchingFingerprintAndClaimIdentity() {
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor).build();
        TxFlow definition = definition();
        FlowExecutionHandle first = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("shared-active-id").build());

        assertSame(first, engine.start(FlowExecutionRequest.builder(definition)
                .executionId("shared-active-id").build()));
        FlowExecutionResult fingerprintConflict = engine.start(
                FlowExecutionRequest.builder(definition)
                        .executionId("shared-active-id")
                        .spendingResource("different-resource").build()).await();
        FlowExecutionResult claimConflict = engine.start(
                FlowExecutionRequest.builder(definition)
                        .executionId("shared-active-id")
                        .idempotency("tenant", "operation").build()).await();

        assertEquals("TXFLOW_EXECUTION_ID_CONFLICT", fingerprintConflict.error().code());
        assertEquals("TXFLOW_EXECUTION_ID_CONFLICT", claimConflict.error().code());
        // A rejected collision must not reserve the unrelated idempotency identity.
        FlowExecutionHandle independent = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("independent-id")
                .idempotency("tenant", "operation").build());
        assertFalse(independent.isDone());
    }

    @Test
    void explicitNamespaceCannotAliasTheInternalExecutionClaim() {
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle implicit = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("claim-alias").build());

        FlowExecutionResult explicit = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("claim-alias")
                .idempotency("ccl.txflow.execution", "claim-alias").build()).await();

        assertFalse(implicit.isDone());
        assertEquals("TXFLOW_EXECUTION_ID_CONFLICT", explicit.error().code());
    }

    @Test
    void explicitClaimCannotAliasTheExactInternalStoreTuple() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle implicit = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("internal-claim-owner").build());
        org.mockito.ArgumentCaptor<String> namespace =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> key =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).createOrGet(namespace.capture(), key.capture(), any());

        FlowExecutionHandle explicit = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("explicit-claim-owner")
                .idempotency(namespace.getValue(), key.getValue()).build());

        assertFalse(implicit == explicit);
        assertFalse(explicit.isDone());
        assertTrue(store.get("internal-claim-owner").isPresent());
        assertTrue(store.get("explicit-claim-owner").isPresent());
    }

    @Test
    void durableIdempotencyPreservesFullAsciiNamespaceLimit() {
        assertDurableNamespaceBoundary("n".repeat(FlowStoreTextPolicy.MAX_NAMESPACE_BYTES),
                "ascii");
    }

    @Test
    void durableIdempotencyPreservesFullMultibyteNamespaceLimit() {
        assertDurableNamespaceBoundary("é".repeat(127) + "n", "multibyte");
    }

    @Test
    void durableExecutionClaimPreservesFullExecutionIdLimit() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        FlowEngine engine = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(store).build();
        String executionId = "e".repeat(FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);

        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId(executionId).build());

        assertFalse(handle.isDone());
        verify(store).createOrGet(eq("ccl.txflow.execution"),
                eq("execution:v1:" + SignedPayloadVerifier.sha256(executionId)), any());
        assertTrue(store.get(executionId).isPresent());
    }

    @Test
    void durableClaimsPreserveFullMultibyteKeyAndExecutionIdLimits() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        FlowEngine engine = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(store).build();
        String exactly512Bytes = "界".repeat(170) + "ab";

        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId(exactly512Bytes)
                .idempotency("tenant", exactly512Bytes).build());

        assertFalse(handle.isDone());
        verify(store).createOrGet(eq("tenant"),
                eq("idempotency:v1:" + SignedPayloadVerifier.sha256(exactly512Bytes)), any());
        assertTrue(store.get(exactly512Bytes).isPresent());
    }

    @Test
    void nonDurableIdempotencyAcceptsFullAsciiNamespaceLimit() {
        assertNonDurableNamespaceBoundary(
                "n".repeat(FlowStoreTextPolicy.MAX_NAMESPACE_BYTES), "ascii");
    }

    @Test
    void nonDurableIdempotencyAcceptsFullMultibyteNamespaceLimit() {
        assertNonDurableNamespaceBoundary("é".repeat(127) + "n", "multibyte");
    }

    @Test
    void cancellationAndEventsArePersistedBeforeCompletion() {
        QueuedExecutor executor = new QueuedExecutor();
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("durable-cancel").build());

        assertTrue(handle.cancel());
        executor.runNext();

        assertEquals(FlowExecutionState.CANCELLED, handle.await().state());
        assertEquals(FlowExecutionState.CANCELLED,
                store.get("durable-cancel").orElseThrow().state());
        assertEquals(handle.getEvents().size(),
                store.readEvents("durable-cancel", 0, 100).events().size());
    }

    @Test
    void durableCancellationIsCheckedBeforeResourceLeaseAcquisition() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        doThrow(new FlowStoreException(
                "TXFLOW_RESOURCE_LEASE_CONFLICT", "another execution owns wallet"))
                .when(store).acquireResourceLease(eq("wallet"), eq("durable-cancel-before-resource"),
                        anyString(), any(), any());
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("durable-cancel-before-resource")
                .spendingResource("wallet").build());

        assertTrue(handle.cancel());
        executor.runNext();

        assertEquals(FlowExecutionState.CANCELLED, handle.await().state());
        assertEquals(FlowExecutionState.CANCELLED,
                store.get("durable-cancel-before-resource").orElseThrow().state());
        verify(store, never()).acquireResourceLease(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void durableResourceLeaseContentionIsRetryableResourceBusy() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        doThrow(new FlowStoreException(
                "TXFLOW_RESOURCE_LEASE_CONFLICT", "another execution owns wallet"))
                .when(store).acquireResourceLease(eq("wallet"), eq("resource-contention"),
                        anyString(), any(), any());
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("resource-contention").spendingResource("wallet").build());

        executor.runNext();

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.FAILED, result.state());
        assertEquals("TXFLOW_RESOURCE_BUSY", result.error().code());
        assertEquals(FlowErrorCategory.RESOURCE, result.error().category());
        assertTrue(result.error().retryable());
        assertEquals(FlowExecutionState.FAILED,
                store.get("resource-contention").orElseThrow().state());
        assertEquals("TXFLOW_RESOURCE_BUSY",
                store.get("resource-contention").orElseThrow().data().get("failure_code"));
    }

    @Test
    void durableExecutionLeaseContentionIsBusyWithoutOverwritingStoredState() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        doThrow(new FlowStoreException(
                "TXFLOW_LEASE_CONFLICT", "another owner is already running it"))
                .when(store).acquireExecutionLease(eq("execution-contention"),
                        anyString(), any(), any());
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("execution-contention").build());

        executor.runNext();

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.FAILED, result.state());
        assertEquals("TXFLOW_RESOURCE_BUSY", result.error().code());
        assertEquals(FlowErrorCategory.RESOURCE, result.error().category());
        assertTrue(result.error().retryable());
        assertEquals(FlowExecutionState.CREATED,
                store.get("execution-contention").orElseThrow().state());
    }

    @Test
    void durableCatchPathPersistsTerminalFailureBeforeCompletingHandle() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        doThrow(new IllegalStateException("resource adapter failed"))
                .when(store).acquireResourceLease(eq("wallet"), eq("durable-failure"),
                        anyString(), any(), any());
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("durable-failure").spendingResource("wallet").build());

        executor.runNext();

        assertEquals(FlowExecutionState.FAILED, handle.await().state());
        FlowExecutionSnapshot snapshot = store.get("durable-failure").orElseThrow();
        assertEquals(FlowExecutionState.FAILED, snapshot.state());
        assertEquals("TXFLOW_ENGINE_FAILURE", snapshot.data().get("failure_code"));
        assertEquals(FlowEventType.EXECUTION_FAILED,
                store.readEvents("durable-failure", 0, 100).events().get(3).type());
    }

    @Test
    void terminalPersistenceFailureReturnsRecoveryRequiredInsteadOfFailed() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        doThrow(new FlowStoreException("TXFLOW_STORE_UNAVAILABLE", "append unavailable"))
                .when(store).append(anyString(), anyLong(), any(), anyList(), any());
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("terminal-persist-failure").build());

        executor.runNext();

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
        assertEquals("TXFLOW_TERMINAL_PERSISTENCE_FAILED", result.error().code());
        assertTrue(result.error().retryable());
        assertEquals(FlowExecutionState.CREATED,
                store.get("terminal-persist-failure").orElseThrow().state());
        assertTrue(handle.getEvents().stream()
                .anyMatch(event -> event.type() == FlowEventType.RECOVERY_REQUIRED));
        verify(store, times(2)).append(anyString(), anyLong(), any(), anyList(), any());
    }

    @Test
    void catchPathPreservesReadableDurableAttemptsAfterTerminalPersistenceFailure()
            throws Exception {
        FlowExecutionResult result = executeWithLateTerminalPersistenceFailure(false);

        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
        assertEquals(1, result.attempts().size());
        assertEquals(AttemptState.CONFIRMED, result.attempts().get(0).state());
    }

    @Test
    void catchPathStillCompletesWhenBestEffortAttemptReadFails() throws Exception {
        FlowExecutionResult result = executeWithLateTerminalPersistenceFailure(true);

        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
        assertTrue(result.attempts().isEmpty());
        assertEquals("TXFLOW_TERMINAL_PERSISTENCE_FAILED", result.error().code());
    }

    @Test
    void maintenanceExecutorRejectionRequiresRecoveryBecauseFenceHealthIsLost()
            throws Exception {
        Transaction transaction = batchTransaction(91);
        String hash = TransactionUtil.getTxHash(transaction);
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        AtomicReference<Consumer<Transaction>> inspector = new AtomicReference<>();
        CountDownLatch renewalDispatched = new CountDownLatch(1);
        when(txContext.withTxInspector(any())).thenAnswer(invocation -> {
            inspector.set(invocation.getArgument(0));
            return txContext;
        });
        when(txContext.completeAndWait(any(), any(), any())).thenAnswer(invocation -> {
            inspector.get().accept(transaction);
            assertTrue(renewalDispatched.await(5, TimeUnit.SECONDS));
            Result<String> accepted = Result.success("submitted");
            accepted.withValue(hash);
            return TxResult.fromResult(accepted);
        });
        TxFlow executable = TxFlow.builder("renewal-rejection")
                .addStep(FlowStep.builder("step")
                        .withTxContext(ignored -> txContext).build())
                .build();
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Executor rejectingMaintenance = command -> {
            renewalDispatched.countDown();
            throw new RejectedExecutionException("maintenance saturated");
        };
        try {
            FlowEngine engine = engineBuilder().executor(worker)
                    .maintenanceExecutor(rejectingMaintenance)
                    .leaseDuration(Duration.ofMillis(6))
                    .store(store).compiler(compilerFor(executable)).build();

            FlowExecutionResult result = engine.start(
                    FlowExecutionRequest.builder(executable)
                            .executionId("renewal-rejection").build()).await();

            assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
            assertEquals("TXFLOW_LEASE_RENEWAL_FAILED", result.error().code());
            assertEquals(FlowErrorCategory.PERSISTENCE, result.error().category());
            assertTrue(result.error().retryable());
            assertEquals(FlowExecutionState.RUNNING,
                    store.get("renewal-rejection").orElseThrow().state());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void durableIdempotencyClaimIsAuthoritativeAcrossEngineInstances() {
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        QueuedExecutor firstExecutor = new QueuedExecutor();
        FlowEngine firstEngine = engineBuilder().executor(firstExecutor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle first = firstEngine.start(FlowExecutionRequest.builder(definition())
                .executionId("original").idempotency("tenant", "same").build());

        FlowEngine secondEngine = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(store).build();
        FlowExecutionHandle recovered = secondEngine.start(FlowExecutionRequest.builder(definition())
                .executionId("duplicate").idempotency("tenant", "same").build());

        assertEquals(first.getExecutionId(), recovered.getExecutionId());
        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, recovered.await().state());
    }

    @Test
    void durableIdempotencyAlwaysConsultsTheStore() {
        FlowExecutionStore store = mock(FlowExecutionStore.class);
        AtomicReference<FlowExecutionSnapshot> claimed = new AtomicReference<>();
        when(store.createOrGet(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    FlowExecutionSnapshot existing = claimed.get();
                    if (existing != null) {
                        return new com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult(
                                existing, false);
                    }
                    FlowExecutionSnapshot initial = invocation.getArgument(2);
                    claimed.set(initial);
                    return new com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult(
                            initial, true);
                });
        FlowEngine engine = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(store).build();
        TxFlow definition = definition();

        FlowExecutionHandle first = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("store-first").idempotency("tenant", "key").build());
        FlowExecutionHandle duplicate = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("store-duplicate").idempotency("tenant", "key").build());

        assertSame(first, duplicate);
        org.mockito.Mockito.verify(store, org.mockito.Mockito.times(2)).createOrGet(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void durableActiveExecutionIdCannotBypassStoreConflictValidation() {
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor)
                .maintenanceExecutor(Runnable::run).store(store).build();
        TxFlow definition = definition();
        FlowExecutionHandle first = engine.start(FlowExecutionRequest.builder(definition)
                .executionId("durable-shared-id").idempotency("tenant", "operation").build());

        assertSame(first, engine.start(FlowExecutionRequest.builder(definition)
                .executionId("ignored-retry-id").idempotency("tenant", "operation").build()));
        FlowExecutionResult fingerprintConflict = engine.start(
                FlowExecutionRequest.builder(definition)
                        .executionId("durable-shared-id")
                        .idempotency("tenant", "operation")
                        .spendingResource("different-resource").build()).await();
        FlowExecutionResult executionIdConflict = engine.start(
                FlowExecutionRequest.builder(definition)
                        .executionId("durable-shared-id")
                        .idempotency("other-tenant", "other-operation").build()).await();

        assertEquals("TXFLOW_IDEMPOTENCY_CONFLICT", fingerprintConflict.error().code());
        assertEquals("TXFLOW_EXECUTION_ID_CONFLICT", executionIdConflict.error().code());
    }

    @Test
    void executorRejectionCompletesHandleAndDoesNotRetainInMemoryClaims() {
        RejectOnceExecutor executor = new RejectOnceExecutor();
        FlowEngine engine = engineBuilder().executor(executor).build();
        FlowExecutionRequest rejectedRequest = FlowExecutionRequest.builder(definition())
                .executionId("executor-rejected")
                .idempotency("tenant", "retryable-operation").build();

        FlowExecutionResult rejected = engine.start(rejectedRequest).await();

        assertEquals(FlowExecutionState.FAILED, rejected.state());
        assertEquals("TXFLOW_EXECUTOR_REJECTED", rejected.error().code());
        assertEquals(FlowErrorCategory.RESOURCE, rejected.error().category());
        assertTrue(rejected.error().retryable());
        FlowExecutionHandle retry = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("executor-rejected")
                .idempotency("tenant", "retryable-operation")
                .spendingResource("changed-after-rejection").build());
        assertFalse(retry.isDone());
    }

    @Test
    void executorRejectionIsDurablyTerminalized() {
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        FlowEngine engine = engineBuilder().executor(command -> {
                    throw new RejectedExecutionException("executor saturated");
                })
                .maintenanceExecutor(Runnable::run).store(store).build();

        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("durable-executor-rejected").build());

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.FAILED, result.state());
        assertEquals("TXFLOW_EXECUTOR_REJECTED", result.error().code());
        assertFalse(result.error().retryable());
        FlowExecutionSnapshot snapshot = store.get("durable-executor-rejected").orElseThrow();
        assertEquals(FlowExecutionState.FAILED, snapshot.state());
        assertEquals("TXFLOW_EXECUTOR_REJECTED", snapshot.data().get("failure_code"));
        assertEquals(handle.getEvents(),
                store.readEvents("durable-executor-rejected", 0, 100).events());
    }

    @Test
    void executorRejectionWithUnpersistableTerminalStateRequiresRecovery() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        doThrow(new FlowStoreException("TXFLOW_STORE_UNAVAILABLE", "append unavailable"))
                .when(store).append(anyString(), anyLong(), any(), anyList(), any());
        FlowEngine engine = engineBuilder().executor(command -> {
                    throw new RejectedExecutionException("executor saturated");
                })
                .maintenanceExecutor(Runnable::run).store(store).build();

        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("unpersistable-executor-rejection").build());

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
        assertEquals("TXFLOW_EXECUTOR_REJECTION_PERSISTENCE_FAILED", result.error().code());
        assertEquals(FlowExecutionState.CREATED,
                store.get("unpersistable-executor-rejection").orElseThrow().state());
        assertEquals(FlowEventType.RECOVERY_REQUIRED,
                handle.getEvents().get(handle.getEvents().size() - 1).type());
    }

    @Test
    void executorRejectionPersistsRecoveryRequiredAfterTransientAppendFailure() {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failOnce.compareAndSet(true, false)) {
                throw new FlowStoreException(
                        "TXFLOW_STORE_UNAVAILABLE", "transient append failure");
            }
            return invocation.callRealMethod();
        }).when(store).append(anyString(), anyLong(), any(), anyList(), any());
        FlowEngine engine = engineBuilder().executor(command -> {
                    throw new RejectedExecutionException("executor saturated");
                })
                .maintenanceExecutor(Runnable::run).store(store).build();

        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(definition())
                .executionId("transient-rejection-persistence").build()).await();

        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
        assertEquals("TXFLOW_EXECUTOR_REJECTION_PERSISTENCE_FAILED", result.error().code());
        FlowExecutionSnapshot snapshot = store.get(
                "transient-rejection-persistence").orElseThrow();
        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, snapshot.state());
        assertEquals("TXFLOW_EXECUTOR_REJECTION_PERSISTENCE_FAILED",
                snapshot.data().get("failure_code"));
        assertEquals(FlowEventType.RECOVERY_REQUIRED,
                store.readEvents("transient-rejection-persistence", 0, 10)
                        .events().get(3).type());
        verify(store, times(2)).append(anyString(), anyLong(), any(), anyList(), any());
    }

    @Test
    void stepFailureWithNullMessageStillEmitsFailureEvent() {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.withTxInspector(org.mockito.ArgumentMatchers.any())).thenReturn(txContext);
        when(txContext.completeAndWait(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException());
        TxFlow executable = TxFlow.builder("null-message")
                .addStep(FlowStep.builder("step")
                        .withTxContext(ignored -> txContext).build())
                .build();
        TxFlowCompiler compiler = mock(TxFlowCompiler.class);
        FlowCompilationResult compilation = mock(FlowCompilationResult.class);
        CompiledTxFlow compiled = mock(CompiledTxFlow.class);
        when(compiler.compile(org.mockito.ArgumentMatchers.any())).thenReturn(compilation);
        when(compilation.hasErrors()).thenReturn(false);
        when(compilation.requireCompiledFlow()).thenReturn(compiled);
        when(compiled.getExecutionPlan()).thenReturn(executable);
        when(compiled.getFingerprint()).thenReturn("fingerprint");
        when(compiled.getSpendingResources()).thenReturn(java.util.Set.of());
        when(compiled.getExplicitConsumers()).thenReturn(Map.of());
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor).compiler(compiler).build();

        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(executable)
                .executionId("null-message").build());
        executor.runNext();

        assertEquals(FlowExecutionState.FAILED, handle.await().state());
        FlowEvent failure = handle.getEvents().stream()
                .filter(event -> event.type() == FlowEventType.STEP_FAILED)
                .findFirst().orElseThrow();
        assertEquals("unknown", failure.details().get("message"));
    }

    @Test
    void reconciliationUncertaintyNestedUnderRollbackMapsToRecoveryRequired() {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.withTxInspector(org.mockito.ArgumentMatchers.any())).thenReturn(txContext);
        FlowStep step = FlowStep.builder("step").withTxContext(ignored -> txContext).build();
        ReconciliationUncertainException uncertainty = new ReconciliationUncertainException(
                "same-hash", new FlowExecutionException("reinclusion window exhausted"));
        RollbackException rollback = new RollbackException(
                "same-hash remains rolled back", uncertainty, "same-hash", step, 100L, false);
        when(txContext.completeAndWait(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new FlowExecutionException("legacy facade failed", rollback));
        TxFlow executable = TxFlow.builder("recovery-required")
                .addStep(step).build();
        TxFlowCompiler compiler = mock(TxFlowCompiler.class);
        FlowCompilationResult compilation = mock(FlowCompilationResult.class);
        CompiledTxFlow compiled = mock(CompiledTxFlow.class);
        when(compiler.compile(org.mockito.ArgumentMatchers.any())).thenReturn(compilation);
        when(compilation.hasErrors()).thenReturn(false);
        when(compilation.requireCompiledFlow()).thenReturn(compiled);
        when(compiled.getExecutionPlan()).thenReturn(executable);
        when(compiled.getFingerprint()).thenReturn("fingerprint");
        when(compiled.getSpendingResources()).thenReturn(java.util.Set.of());
        when(compiled.getExplicitConsumers()).thenReturn(Map.of());
        QueuedExecutor executor = new QueuedExecutor();
        FlowEngine engine = engineBuilder().executor(executor).compiler(compiler).build();

        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(executable)
                .executionId("recovery-required").build());
        executor.runNext();

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
        assertEquals("TXFLOW_RECOVERY_REQUIRED", result.error().code());
        assertEquals(FlowErrorCategory.RECOVERY, result.error().category());
        assertTrue(result.error().retryable());
    }

    @Test
    void batchUnknownSubmissionObservationFailureIsDurablyRecoveryRequired() throws Exception {
        class ProviderFailure extends ApiRuntimeException {
            ProviderFailure(String message) { super(message); }
        }
        Transaction transaction = batchTransaction(0);
        String hash = TransactionUtil.getTxHash(transaction);
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.buildAndSign()).thenReturn(transaction);
        TxFlow executable = TxFlow.builder("batch-durable-uncertain")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("step")
                        .withTxContext(ignored -> txContext).build())
                .build();
        TransactionProcessor processor = mock(TransactionProcessor.class);
        when(processor.submitTransaction(any(byte[].class)))
                .thenThrow(new ProviderFailure("submission response lost"));
        ChainDataSupplier chain = mock(ChainDataSupplier.class);
        when(chain.getTransactionInfo(hash))
                .thenThrow(new IllegalStateException("observation backend unavailable"));
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        QueuedExecutor queued = new QueuedExecutor();
        FlowEngine engine = FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class), processor, chain)
                .executor(queued).maintenanceExecutor(Runnable::run)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC)).store(store)
                .compiler(compilerFor(executable)).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(executable)
                .executionId("batch-durable-uncertain").build());

        queued.runNext();

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
        assertEquals("TXFLOW_RECOVERY_REQUIRED", result.error().code());
        assertEquals(FlowErrorCategory.RECOVERY, result.error().category());
        assertTrue(result.error().retryable());
        assertEquals(1, result.steps().size());
        assertFalse(result.steps().get(0).isSuccessful());
        assertTrue(result.steps().get(0).getError()
                instanceof ReconciliationUncertainException);
        assertEquals(1, result.attempts().size());
        assertEquals(AttemptState.SUBMITTING, result.attempts().get(0).state());
        assertEquals(FlowExecutionState.RECOVERY_REQUIRED,
                store.get("batch-durable-uncertain").orElseThrow().state());
    }

    @Test
    void batchSubmittedButUnconfirmedPrefixDoesNotBecomePartiallyCompleted() throws Exception {
        Transaction first = batchTransaction(0);
        Transaction second = batchTransaction(1);
        String firstHash = TransactionUtil.getTxHash(first);
        QuickTxBuilder.TxContext firstContext = mock(QuickTxBuilder.TxContext.class);
        QuickTxBuilder.TxContext secondContext = mock(QuickTxBuilder.TxContext.class);
        when(firstContext.buildAndSign()).thenReturn(first);
        when(secondContext.buildAndSign()).thenReturn(second);
        TxFlow executable = TxFlow.builder("batch-unconfirmed-prefix")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("first")
                        .withTxContext(ignored -> firstContext).build())
                .addStep(FlowStep.builder("second")
                        .withTxContext(ignored -> secondContext).build())
                .build();
        Result<String> accepted = Result.success("submitted");
        accepted.withValue(firstHash);
        TransactionProcessor processor = mock(TransactionProcessor.class);
        when(processor.submitTransaction(any(byte[].class)))
                .thenReturn(accepted, Result.error("second rejected"));
        QueuedExecutor queued = new QueuedExecutor();
        FlowEngine engine = FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class), processor,
                        mock(ChainDataSupplier.class))
                .executor(queued).clock(Clock.fixed(NOW, ZoneOffset.UTC))
                .compiler(compilerFor(executable)).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(executable)
                .executionId("batch-unconfirmed-prefix").build());

        queued.runNext();

        FlowExecutionResult result = handle.await();
        assertEquals(FlowExecutionState.FAILED, result.state());
        assertEquals(2, result.steps().size());
        assertEquals(com.bloxbean.cardano.client.txflow.result.FlowStatus.IN_PROGRESS,
                result.steps().get(0).getStatus());
        assertFalse(result.steps().get(0).isSuccessful());
        assertEquals(com.bloxbean.cardano.client.txflow.result.FlowStatus.FAILED,
                result.steps().get(1).getStatus());
    }

    @Test
    void expiredLeaseTakeoverFencesLatePreparedAppendAndPreventsSubmission() throws Exception {
        AdjustableClock clock = new AdjustableClock(NOW);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(clock);
        Transaction transaction = batchTransaction(0);
        TransactionProcessor processor = mock(TransactionProcessor.class);
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        AtomicReference<Consumer<Transaction>> inspector = new AtomicReference<>();
        CountDownLatch executionReachedSubmissionBoundary = new CountDownLatch(1);
        CountDownLatch allowLateAppend = new CountDownLatch(1);
        when(txContext.withTxInspector(any())).thenAnswer(invocation -> {
            inspector.set(invocation.getArgument(0));
            return txContext;
        });
        when(txContext.completeAndWait(any(), any(), any())).thenAnswer(invocation -> {
            executionReachedSubmissionBoundary.countDown();
            if (!allowLateAppend.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release late append");
            }
            // The durable onPrepared callback runs before backend I/O. A stale
            // fence must abort here, so the modeled submission below is unreachable.
            inspector.get().accept(transaction);
            processor.submitTransaction(transaction.serialize());
            Result<String> accepted = Result.success("submitted");
            accepted.withValue(TransactionUtil.getTxHash(transaction));
            return TxResult.fromResult(accepted);
        });
        TxFlow executable = TxFlow.builder("split-brain-fenced")
                .addStep(FlowStep.builder("step")
                        .withTxContext(ignored -> txContext).build())
                .build();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        QueuedExecutor maintenance = new QueuedExecutor();
        FlowEngine engine = FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class), processor,
                        mock(ChainDataSupplier.class))
                .executor(worker).maintenanceExecutor(maintenance)
                .clock(clock).store(store).leaseDuration(Duration.ofSeconds(1))
                .ownerToken("owner-one").compiler(compilerFor(executable)).build();

        ExecutionLease takeover = null;
        try {
            FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(executable)
                    .executionId("split-brain-fenced").build());
            assertTrue(executionReachedSubmissionBoundary.await(5, TimeUnit.SECONDS));

            clock.advance(Duration.ofSeconds(2));
            takeover = store.acquireExecutionLease(
                    "split-brain-fenced", "owner-two", clock.instant(), Duration.ofSeconds(10));
            allowLateAppend.countDown();

            FlowExecutionResult result = handle.await();
            assertEquals(FlowExecutionState.RECOVERY_REQUIRED, result.state());
            assertEquals(FlowErrorCategory.PERSISTENCE, result.error().category());
            assertEquals(1, result.steps().size());
            assertEquals(com.bloxbean.cardano.client.txflow.result.FlowStatus.FAILED,
                    result.steps().get(0).getStatus());
            assertEquals(FlowExecutionState.RUNNING,
                    store.get("split-brain-fenced").orElseThrow().state());
            verify(processor, never()).submitTransaction(any(byte[].class));
        } finally {
            allowLateAppend.countDown();
            if (takeover != null) store.releaseExecutionLease(takeover);
            worker.shutdownNow();
        }
    }

    @Test
    void durableClaimFailureIsReturnedAsTypedPersistenceError() {
        FlowExecutionStore store = mock(FlowExecutionStore.class);
        when(store.createOrGet(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new FlowStoreException("TXFLOW_STORE_UNAVAILABLE", "store unavailable"));
        FlowExecutionHandle handle = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(store).build()
                .start(FlowExecutionRequest.builder(definition()).executionId("failed-claim").build());

        assertEquals(FlowExecutionState.FAILED, handle.await().state());
        assertEquals(FlowErrorCategory.PERSISTENCE, handle.await().error().category());
        assertEquals("TXFLOW_STORE_UNAVAILABLE", handle.await().error().code());
    }

    @Test
    void concurrentSpendingOptOutRequiresExplicitServerPermission() {
        FlowExecutionHandle rejected = engineBuilder().executor(new QueuedExecutor()).build()
                .start(FlowExecutionRequest.builder(definition())
                        .executionId("unsafe-opt-out").allowConcurrentSpending(true).build());

        assertEquals("TXFLOW_CONCURRENT_SPENDING_FORBIDDEN", rejected.await().error().code());
        assertEquals(FlowErrorCategory.POLICY, rejected.await().error().category());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoveryByExecutionIdReconcilesAndPersistsIdenticalResubmission() throws Exception {
        byte[] cbor = new Transaction().serialize();
        String hash = TransactionUtil.getTxHash(cbor);
        SignedPayload payload = new SignedPayload.InlineCbor(
                cbor, SignedPayloadVerifier.sha256(cbor), hash);
        FlowAttemptSnapshot attempt = new FlowAttemptSnapshot("step", 1,
                AttemptState.SUBMITTING, payload, null, 100L,
                List.of(), List.of(), NOW, null);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        store.createOrGet("test", "recovery", new FlowExecutionSnapshot(
                "recoverable", "definition", "request", FlowExecutionState.RECOVERY_REQUIRED,
                0, 0, 0, NOW, Map.of(DurableExecutionPersistence.ATTEMPTS_KEY,
                Map.of("step#1", attempt))));
        ChainDataSupplier chain = mock(ChainDataSupplier.class);
        TransactionProcessor processor = mock(TransactionProcessor.class);
        when(chain.getTransactionInfo(hash)).thenReturn(java.util.Optional.empty());
        when(processor.submitTransaction(aryEq(cbor))).thenReturn(Result.success(hash));
        FlowEngine engine = FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class), processor, chain)
                .executor(Runnable::run).maintenanceExecutor(Runnable::run)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC)).store(store)
                .leaseDuration(Duration.ofMinutes(1)).build();

        assertEquals(AttemptState.SUBMITTED, engine.recover(FlowRecoveryRequest.builder()
                .executionId("recoverable").stepId("step").attemptNumber(1)
                .currentSlot(50).resubmitSafetyMargin(5).build()).state());
        FlowExecutionSnapshot persisted = store.get("recoverable").orElseThrow();
        assertEquals(FlowExecutionState.RUNNING, persisted.state());
        Map<String, FlowAttemptSnapshot> attempts = (Map<String, FlowAttemptSnapshot>)
                persisted.data().get(DurableExecutionPersistence.ATTEMPTS_KEY);
        assertEquals(AttemptState.SUBMITTED, attempts.get("step#1").state());
        assertEquals(List.of(FlowEventType.RECOVERY_STARTED, FlowEventType.RECOVERY_COMPLETED),
                store.readEvents("recoverable", 0, 10).events().stream()
                        .map(FlowEvent::type).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void durableSensitiveBindingsRequireReferencesAndAreNeverStoredInPlaintext() {
        TxFlow definition = sensitiveDefinition();
        InMemoryFlowExecutionStore rejectedStore = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        FlowExecutionHandle rejected = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(rejectedStore).build()
                .start(FlowExecutionRequest.builder(definition)
                        .executionId("missing-secret-ref")
                        .bindings(FlowBindings.builder().put("token", "top-secret").build())
                        .build());
        assertEquals("TXFLOW_SECURE_BINDING_REFERENCE_REQUIRED", rejected.await().error().code());

        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC));
        FlowExecutionHandle accepted = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(store).build()
                .start(FlowExecutionRequest.builder(definition)
                        .executionId("secure-ref")
                        .bindings(FlowBindings.builder().put("token", "top-secret").build())
                        .secureBindingReference("token", "vault://txflow/token/1").build());
        assertFalse(accepted.isDone());
        FlowExecutionSnapshot snapshot = store.get("secure-ref").orElseThrow();
        assertFalse(snapshot.data().toString().contains("top-secret"));
        List<PersistedBinding> bindings = (List<PersistedBinding>) snapshot.data().get("bindings");
        assertEquals("vault://txflow/token/1", bindings.get(0).secureValueRef());
        assertEquals(null, bindings.get(0).nonSensitiveValue());
        assertEquals("***", bindings.get(0).redactedDisplay());
    }

    private FlowEngine.Builder engineBuilder() {
        return FlowEngine.builder(mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class),
                        mock(TransactionProcessor.class), mock(ChainDataSupplier.class))
                .clock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private FlowExecutionResult executeWithLateTerminalPersistenceFailure(
            boolean failAttemptRead) throws Exception {
        Transaction transaction = batchTransaction(90);
        String hash = TransactionUtil.getTxHash(transaction);
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        AtomicReference<Consumer<Transaction>> inspector = new AtomicReference<>();
        when(txContext.withTxInspector(any())).thenAnswer(invocation -> {
            inspector.set(invocation.getArgument(0));
            return txContext;
        });
        when(txContext.completeAndWait(any(), any(), any())).thenAnswer(invocation -> {
            inspector.get().accept(transaction);
            Result<String> accepted = Result.success("submitted");
            accepted.withValue(hash);
            return TxResult.fromResult(accepted);
        });
        TxFlow executable = TxFlow.builder("late-terminal-persistence")
                .addStep(FlowStep.builder("step")
                        .withTxContext(ignored -> txContext).build())
                .build();
        ChainDataSupplier chain = mock(ChainDataSupplier.class);
        when(chain.getTransactionInfo(hash)).thenReturn(Optional.of(
                TransactionInfo.builder().txHash(hash).blockHeight(10L).build()));
        AtomicBoolean terminalAppendFailed = new AtomicBoolean();
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        doAnswer(invocation -> {
            List<FlowEvent> events = invocation.getArgument(3);
            if (events.stream().anyMatch(
                    event -> event.type() == FlowEventType.EXECUTION_COMPLETED)) {
                terminalAppendFailed.set(true);
                throw new FlowStoreException(
                        "TXFLOW_STORE_UNAVAILABLE", "terminal append unavailable");
            }
            return invocation.callRealMethod();
        }).when(store).append(anyString(), anyLong(), any(), anyList(), any());
        if (failAttemptRead) {
            doAnswer(invocation -> {
                if (terminalAppendFailed.get()) {
                    throw new FlowStoreException(
                            "TXFLOW_STORE_UNAVAILABLE", "attempt read unavailable");
                }
                return invocation.callRealMethod();
            }).when(store).get(anyString());
        }
        QueuedExecutor queued = new QueuedExecutor();
        FlowEngine engine = FlowEngine.builder(mock(UtxoSupplier.class),
                        mock(ProtocolParamsSupplier.class), mock(TransactionProcessor.class), chain)
                .executor(queued).maintenanceExecutor(Runnable::run)
                .clock(Clock.fixed(NOW, ZoneOffset.UTC)).store(store)
                .compiler(compilerFor(executable)).build();
        FlowExecutionHandle handle = engine.start(FlowExecutionRequest.builder(executable)
                .executionId("late-terminal-" + failAttemptRead).build());

        queued.runNext();
        return handle.await();
    }

    private TxFlowCompiler compilerFor(TxFlow executable) {
        TxFlowCompiler compiler = mock(TxFlowCompiler.class);
        FlowCompilationResult compilation = mock(FlowCompilationResult.class);
        CompiledTxFlow compiled = mock(CompiledTxFlow.class);
        when(compiler.compile(any())).thenReturn(compilation);
        when(compilation.hasErrors()).thenReturn(false);
        when(compilation.requireCompiledFlow()).thenReturn(compiled);
        when(compiled.getExecutionPlan()).thenReturn(executable);
        when(compiled.getFingerprint()).thenReturn("fingerprint");
        when(compiled.getSpendingResources()).thenReturn(java.util.Set.of());
        when(compiled.getExplicitConsumers()).thenReturn(Map.of());
        return compiler;
    }

    private Transaction batchTransaction(int inputIndex) {
        return Transaction.builder()
                .body(TransactionBody.builder()
                        .inputs(List.of(new TransactionInput("00".repeat(32), inputIndex)))
                        .fee(BigInteger.ZERO)
                        .build())
                .witnessSet(TransactionWitnessSet.builder().build())
                .isValid(true)
                .build();
    }

    private TxFlow definition() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: engine-test}
                spec:
                  steps:
                    - id: step
                      transaction:
                        tx: {intents: []}
                """;
        return TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
    }

    private TxFlow sensitiveDefinition() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: sensitive}
                spec:
                  parameters:
                    token: {type: string, required: true, sensitive: true}
                  steps:
                    - id: step
                      transaction:
                        tx:
                          from: '${{ inputs.token }}'
                          intents: []
                """;
        return TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
    }

    private void assertDurableNamespaceBoundary(String namespace, String executionPrefix) {
        InMemoryFlowExecutionStore store = spy(new InMemoryFlowExecutionStore(
                Clock.fixed(NOW, ZoneOffset.UTC)));
        FlowEngine engine = engineBuilder().executor(new QueuedExecutor())
                .maintenanceExecutor(Runnable::run).store(store).build();
        String applicationKey = "k".repeat(FlowStoreTextPolicy.MAX_IDEMPOTENCY_KEY_BYTES);

        FlowExecutionHandle first = engine.start(FlowExecutionRequest.builder(definition())
                .executionId(executionPrefix + "-durable-first")
                .idempotency(namespace, applicationKey).build());
        FlowExecutionHandle retry = engine.start(FlowExecutionRequest.builder(definition())
                .executionId(executionPrefix + "-durable-retry")
                .idempotency(namespace, applicationKey).build());

        assertSame(first, retry);
        org.mockito.ArgumentCaptor<String> persistedNamespace =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> persistedKey =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).createOrGet(
                persistedNamespace.capture(), persistedKey.capture(), any());
        assertEquals(List.of(namespace, namespace), persistedNamespace.getAllValues());
        assertEquals(persistedKey.getAllValues().get(0), persistedKey.getAllValues().get(1));
        assertEquals("idempotency:v1:" + SignedPayloadVerifier.sha256(applicationKey),
                persistedKey.getValue());
        assertTrue(persistedKey.getValue().length()
                < FlowStoreTextPolicy.MAX_IDEMPOTENCY_KEY_BYTES);
    }

    private void assertNonDurableNamespaceBoundary(String namespace, String executionPrefix) {
        FlowEngine engine = engineBuilder().executor(new QueuedExecutor()).build();
        String applicationKey = "k".repeat(FlowStoreTextPolicy.MAX_IDEMPOTENCY_KEY_BYTES);

        FlowExecutionHandle first = engine.start(FlowExecutionRequest.builder(definition())
                .executionId(executionPrefix + "-memory-first")
                .idempotency(namespace, applicationKey).build());
        FlowExecutionHandle retry = engine.start(FlowExecutionRequest.builder(definition())
                .executionId(executionPrefix + "-memory-retry")
                .idempotency(namespace, applicationKey).build());

        assertSame(first, retry);
        assertFalse(first.isDone());
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.remove().run(); }
    }

    private static final class RejectOnceExecutor implements Executor {
        private final AtomicBoolean reject = new AtomicBoolean(true);
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            if (reject.compareAndSet(true, false)) {
                throw new RejectedExecutionException("executor saturated");
            }
            tasks.add(command);
        }
    }
}
