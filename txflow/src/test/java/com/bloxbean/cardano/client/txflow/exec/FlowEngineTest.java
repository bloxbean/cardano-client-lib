package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import com.bloxbean.cardano.client.txflow.store.SignedPayloadVerifier;
import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.compile.CompiledTxFlow;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.recovery.FlowRecoveryRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalMatchers.aryEq;

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

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.remove().run(); }
    }
}
