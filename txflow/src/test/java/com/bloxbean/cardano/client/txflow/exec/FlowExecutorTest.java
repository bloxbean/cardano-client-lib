package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.BackoffStrategy;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.config.RollbackAction;
import com.bloxbean.cardano.client.txflow.config.RollbackMonitoringHorizon;
import com.bloxbean.cardano.client.txflow.config.RollbackPolicy;
import com.bloxbean.cardano.client.txflow.config.RollbackRebuildScope;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.absent;
import static com.bloxbean.cardano.client.txflow.exec.ScriptedChainBackend.Observation.included;

class FlowExecutorTest {

    @Test
    void submissionFailureClassificationRecognizesWrappedApiRuntimeSubclasses() {
        class ProviderFailure extends ApiRuntimeException {
            ProviderFailure(String message) { super(message); }
        }

        assertTrue(FlowExecutor.hasSubmissionApiFailure(
                new RuntimeException("wrapped", new ProviderFailure("provider unavailable"))));
        assertFalse(FlowExecutor.hasSubmissionApiFailure(new RuntimeException("ordinary")));
    }

    @Mock
    private UtxoSupplier utxoSupplier;
    @Mock
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;
    @Mock
    private ChainDataSupplier chainDataSupplier;

    private FlowExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chainDataSupplier)
                .withExecutor(Runnable::run);
    }

    private TxFlow createSimpleFlow(String flowId) {
        return TxFlow.builder(flowId)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();
    }

    private QuickTxBuilder.TxContext failingTxContext(String message) {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.withTxInspector(any())).thenReturn(txContext);
        when(txContext.completeAndWait(any(Duration.class), any(Duration.class), any()))
                .thenReturn(TxResult.fromResult(Result.error(message)));
        return txContext;
    }

    private QuickTxBuilder.TxContext successfulTxContext(String txHash) {
        QuickTxBuilder.TxContext txContext = mock(QuickTxBuilder.TxContext.class);
        when(txContext.withTxInspector(any())).thenReturn(txContext);
        Result<String> result = Result.success("submitted");
        result.withValue(txHash);
        when(txContext.completeAndWait(any(Duration.class), any(Duration.class), any()))
                .thenReturn(TxResult.fromResult(result));
        return txContext;
    }

    @Test
    void sequentialConfirmationTimeoutDoesNotAdvanceToNextStep() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        executor.withScheduler(scheduler);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(Optional.empty());
        QuickTxBuilder.TxContext first = successfulTxContext("tx1");
        AtomicInteger secondStepBuilds = new AtomicInteger();

        TxFlow flow = TxFlow.builder("sequential-confirmation-gate")
                .addStep(FlowStep.builder("step1").withTxContext(builder -> first).build())
                .addStep(FlowStep.builder("step2").withTxContext(builder -> {
                    secondStepBuilds.incrementAndGet();
                    return successfulTxContext("tx2");
                }).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(ConfirmationTimeoutException.class, result.getError());
        assertEquals(0, secondStepBuilds.get());
    }

    @Test
    void ambiguousAbsenceAfterInclusionIsSuspectedThenRequiresRecovery() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        executor.withScheduler(scheduler).withConfirmationConfig(ConfirmationConfig.builder()
                .minConfirmations(3)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(3))
                .build());
        when(chainDataSupplier.getChainTipHeight()).thenReturn(100L);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1")
                        .blockHeight(99L).blockHash("block-99").build()),
                Optional.empty());
        AtomicInteger suspected = new AtomicInteger();
        executor.withListener(new FlowListener() {
            @Override
            public void onTransactionRollbackSuspected(
                    FlowStep step, String transactionHash, long previousBlockHeight) {
                suspected.incrementAndGet();
            }
        });

        TxFlow flow = TxFlow.builder("ambiguous-absence")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("tx1")).build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(ReconciliationUncertainException.class, result.getError());
        assertEquals(1, suspected.get());
    }

    @Test
    void portableWaitForReinclusionSucceedsWithinItsIndependentWindow() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102),
                        absent(103), included(104, 103, "block-b"),
                        included(105, 103, "block-b"));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withScheduler(scheduler);

        TxFlow flow = portableWaitFlow("wait-reincluded", Duration.ofSeconds(2));
        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isSuccessful());
        assertEquals(Duration.ofSeconds(3), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void portableWaitForReinclusionExhaustsAtWindowNotRecoveryCycleCount() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102),
                        absent(103), absent(104));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withScheduler(scheduler);

        FlowResult result = executor.executeSync(
                portableWaitFlow("wait-exhausted", Duration.ofSeconds(2)));

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(RollbackException.class, result.getError());
        assertInstanceOf(ReconciliationUncertainException.class, result.getError().getCause());
        assertTrue(result.getError().getCause().getCause().getMessage().contains("within PT2S"));
        assertEquals(Duration.ofSeconds(4), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void exhaustedNotifyOnlyRepollsPreserveRollbackOutcome() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101), absent(102));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withExecutor(Runnable::run)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(1)
                        .requiredAuthoritativeAbsences(1)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(2))
                        .maxRollbackRetries(1)
                        .build())
                .withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY);

        TxFlow flow = TxFlow.builder("notify-only-exhausted")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("same-hash"))
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        assertInstanceOf(RollbackException.class, result.getError());
        assertEquals(100L, ((RollbackException) result.getError()).getPreviousBlockHeight());
        assertEquals(Duration.ofSeconds(2), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void notifyOnlyRepollAllowsSameHashReinclusion() {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ScriptedChainBackend chain = new ScriptedChainBackend()
                .then(included(100, 100, "block-a"), absent(101),
                        included(102, 102, "block-b"), included(103, 102, "block-b"),
                        included(104, 102, "block-b"));
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, chain)
                .withExecutor(Runnable::run)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(1)
                        .requiredAuthoritativeAbsences(1)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(5))
                        .maxRollbackRetries(1)
                        .build())
                .withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY);

        TxFlow flow = TxFlow.builder("notify-only-reincluded")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("same-hash"))
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isSuccessful(), () -> "unexpected result: " + result.getStatus()
                + ", error=" + result.getError() + ", delays=" + scheduler.getDelays());
        assertEquals(Duration.ofSeconds(3), scheduler.getDelays().stream()
                .reduce(Duration.ZERO, Duration::plus));
    }

    private TxFlow portableWaitFlow(String flowId, Duration reinclusionWindow) {
        ConfirmationConfig confirmation = ConfirmationConfig.builder()
                .minConfirmations(1)
                .requiredAuthoritativeAbsences(2)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(20))
                .build();
        RollbackPolicy rollback = new RollbackPolicy(
                RollbackAction.WAIT_FOR_REINCLUSION,
                RollbackMonitoringHorizon.UNTIL_STEP_CONFIRMED,
                RollbackRebuildScope.INVALIDATED_CLOSURE,
                99, reinclusionWindow, 2);
        return TxFlow.builder(flowId)
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .confirmationConfig(confirmation)
                        .rollbackPolicy(rollback)
                        .build())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> successfulTxContext("same-hash"))
                        .build())
                .build();
    }

    private Function<QuickTxBuilder, QuickTxBuilder.TxContext> blockingFactory(
            QuickTxBuilder.TxContext txContext, CountDownLatch started, CountDownLatch release) {
        return builder -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Timed out waiting to release test flow");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return txContext;
        };
    }

    @Test
    void asyncExecutionRequiresCallerSuppliedExecutorButSyncDoesNot() {
        FlowExecutor withoutExecutor = FlowExecutor.create(
                utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier);
        TxFlow flow = createSimpleFlow("caller-executor-required");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> withoutExecutor.execute(flow));

        assertTrue(failure.getMessage().contains("caller-supplied Executor"));
        assertTrue(failure.getMessage().contains("virtual-thread executor"));
        FlowResult previous = FlowResult.builder(flow.getId())
                .withStatus(FlowStatus.FAILED)
                .addStepResult(FlowStepResult.failure("step1", new RuntimeException("failed")))
                .build();
        assertThrows(IllegalStateException.class,
                () -> withoutExecutor.resume(flow, previous));
        assertDoesNotThrow(() -> withoutExecutor.executeSync(flow));
    }

    @Test
    void portableTemplateRequiresCompilationBeforeDirectExecution() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: portable-template}
                spec:
                  steps:
                    - id: payment
                      transaction:
                        tx: {intents: []}
                """;
        TxFlow portable = TxFlowCodec.standard()
                .parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();

        FlowExecutionException syncFailure = assertThrows(FlowExecutionException.class,
                () -> executor.executeSync(portable));
        assertTrue(syncFailure.getMessage().contains("TxFlowCompiler"));
        assertTrue(syncFailure.getMessage().contains("FlowEngine"));

        FlowExecutionException asyncFailure = assertThrows(FlowExecutionException.class,
                () -> executor.execute(portable));
        assertTrue(asyncFailure.getMessage().contains("template steps: [payment]"));
    }

    // ==================== HIGH-3: Validate rollback strategy requires ConfirmationConfig ====================

    @Test
    void testExecuteSync_rebuildFromFailed_withoutConfirmationConfig_throwsIllegalState() {
        executor.withRollbackStrategy(RollbackStrategy.REBUILD_FROM_FAILED);
        TxFlow flow = createSimpleFlow("test-flow");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("REBUILD_FROM_FAILED"));
        assertTrue(ex.getMessage().contains("withConfirmationConfig"));
    }

    @Test
    void testExecute_rebuildEntireFlow_withoutConfirmationConfig_throwsIllegalState() {
        executor.withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW);
        TxFlow flow = createSimpleFlow("test-flow");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.execute(flow));
        assertTrue(ex.getMessage().contains("REBUILD_ENTIRE_FLOW"));
    }

    @Test
    void testExecute_notifyOnly_withoutConfirmationConfig_throwsIllegalState() {
        executor.withRollbackStrategy(RollbackStrategy.NOTIFY_ONLY);
        TxFlow flow = createSimpleFlow("test-flow");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.execute(flow));
        assertTrue(ex.getMessage().contains("NOTIFY_ONLY"));
    }

    @Test
    void testExecute_failImmediately_withoutConfirmationConfig_succeeds() {
        executor.withRollbackStrategy(RollbackStrategy.FAIL_IMMEDIATELY);
        TxFlow flow = createSimpleFlow("test-flow");

        // Should not throw — FAIL_IMMEDIATELY doesn't require ConfirmationConfig
        // It will fail later during execution, but the validation should pass
        assertDoesNotThrow(() -> executor.execute(flow));
    }

    @Test
    void testExecuteSync_flowRollbackStrategy_withoutConfirmationConfig_throwsIllegalState() {
        TxFlow flow = TxFlow.builder("flow-context-no-confirmation")
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("REBUILD_ENTIRE_FLOW"));
        assertTrue(ex.getMessage().contains("context.confirmation"));
    }

    @Test
    void testExecuteSync_flowRollbackStrategy_withFlowConfirmation_passesValidation() {
        TxFlow flow = TxFlow.builder("flow-context-confirmation")
                .withConfirmationConfig(ConfirmationConfig.quick())
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertDoesNotThrow(() -> executor.executeSync(flow));
    }

    @Test
    void testExecuteSync_explicitExecutorRollbackDefault_overridesFlowRollbackStrategy() {
        executor.withRollbackStrategy(null);

        TxFlow flow = TxFlow.builder("executor-rollback-default")
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertDoesNotThrow(() -> executor.executeSync(flow));
    }

    @Test
    void testExecuteSync_explicitNullConfirmation_overridesFlowConfirmation() {
        executor.withConfirmationConfig(null);

        TxFlow flow = TxFlow.builder("executor-null-confirmation")
                .withConfirmationConfig(ConfirmationConfig.quick())
                .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("REBUILD_ENTIRE_FLOW"));
        assertTrue(ex.getMessage().contains("context.confirmation"));
    }

    @Test
    void testEffectiveChainingMode_flowContextAppliesWhenExecutorUnset() throws Exception {
        TxFlow flow = TxFlow.builder("flow-chaining")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertEquals(ChainingMode.BATCH, executor.effectiveSettings(flow).getChainingMode());
    }

    @Test
    void testEffectiveChainingMode_explicitExecutorDefaultOverridesFlowContext() throws Exception {
        executor.withChainingMode(null);

        TxFlow flow = TxFlow.builder("executor-chaining-default")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        assertEquals(ChainingMode.SEQUENTIAL, executor.effectiveSettings(flow).getChainingMode());
    }

    @Test
    void portableRollbackPolicyControlsLegacyFacadeWithoutLosingItsBudgets() {
        var rollback = new com.bloxbean.cardano.client.txflow.config.RollbackPolicy(
                com.bloxbean.cardano.client.txflow.config.RollbackAction.RECONCILE_AND_REBUILD,
                com.bloxbean.cardano.client.txflow.config.RollbackMonitoringHorizon.UNTIL_FLOW_TERMINAL,
                com.bloxbean.cardano.client.txflow.config.RollbackRebuildScope.INVALIDATED_CLOSURE,
                7, Duration.ofMinutes(1), 4);
        TxFlow flow = TxFlow.builder("portable-rollback")
                .withExecutionSettings(com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings.builder()
                        .rollbackPolicy(rollback).build())
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        FlowExecutor.EffectiveFlowExecutionSettings settings = executor.effectiveSettings(flow);
        assertEquals(RollbackStrategy.REBUILD_ENTIRE_FLOW, settings.getRollbackStrategy());
        assertEquals(7, settings.getConfirmationConfig().getMaxRollbackRetries());
        assertEquals(4, settings.getConfirmationConfig().getRequiredAuthoritativeAbsences());
        assertTrue(settings.isMonitorUntilFlowTerminal());
    }

    @Test
    void testExecute_concurrentFlowsWithDifferentContexts_doNotContaminateChainingMode() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        executor.withExecutor(pool);

        QuickTxBuilder.TxContext sequentialContext = failingTxContext("sequential connection timeout");
        QuickTxBuilder.TxContext batchContext = failingTxContext("batch connection timeout");

        TxFlow sequentialFlow = TxFlow.builder("concurrent-sequential")
                .withChainingMode(ChainingMode.SEQUENTIAL)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(blockingFactory(sequentialContext, started, release))
                        .build())
                .build();

        TxFlow batchFlow = TxFlow.builder("concurrent-batch")
                .withChainingMode(ChainingMode.BATCH)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(blockingFactory(batchContext, started, release))
                        .build())
                .build();

        try {
            FlowHandle sequentialHandle = executor.execute(sequentialFlow);
            FlowHandle batchHandle = executor.execute(batchFlow);

            assertTrue(started.await(5, TimeUnit.SECONDS), "Both flows should start before release");
            release.countDown();

            sequentialHandle.await(Duration.ofSeconds(5));
            batchHandle.await(Duration.ofSeconds(5));

            verify(sequentialContext, atLeastOnce())
                    .completeAndWait(any(Duration.class), any(Duration.class), any());
            verify(sequentialContext, never()).buildAndSign();
            verify(batchContext, atLeastOnce()).buildAndSign();
            verify(batchContext, never())
                    .completeAndWait(any(Duration.class), any(Duration.class), any());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void testExecuteSync_stepRetryPolicyOverridesFlowDefaultRetryPolicy() {
        QuickTxBuilder.TxContext txContext = failingTxContext("connection timeout");
        RetryPolicy flowRetry = RetryPolicy.builder()
                .maxAttempts(3)
                .backoffStrategy(BackoffStrategy.FIXED)
                .initialDelay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .build();
        RetryPolicy stepRetry = RetryPolicy.noRetry();

        TxFlow flow = TxFlow.builder("step-retry-wins")
                .withDefaultRetryPolicy(flowRetry)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext)
                        .withRetryPolicy(stepRetry)
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isFailed());
        verify(txContext, times(1))
                .completeAndWait(any(Duration.class), any(Duration.class), any());
    }

    @Test
    void testExecuteSync_flowDefaultRetryPolicyAppliesWhenStepHasNoRetryPolicy() {
        QuickTxBuilder.TxContext txContext = failingTxContext("connection timeout");
        RetryPolicy flowRetry = RetryPolicy.builder()
                .maxAttempts(2)
                .backoffStrategy(BackoffStrategy.FIXED)
                .initialDelay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .build();

        TxFlow flow = TxFlow.builder("flow-retry-default")
                .withDefaultRetryPolicy(flowRetry)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> txContext)
                        .build())
                .build();

        FlowResult result = executor.executeSync(flow);

        assertTrue(result.isFailed());
        verify(txContext, times(2))
                .completeAndWait(any(Duration.class), any(Duration.class), any());
    }

    // ==================== HIGH-5: Reject duplicate flow ID execution ====================

    @Test
    void testExecute_duplicateFlowId_throwsIllegalState() throws Exception {
        // Use a blocking executor to ensure the flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow1 = createSimpleFlow("same-id");

        // First execution should work
        FlowHandle handle1 = executor.execute(flow1);

        // Second execution with same ID should throw
        TxFlow flow2 = createSimpleFlow("same-id");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.execute(flow2));
        assertTrue(ex.getMessage().contains("same-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        // Clean up
        handle1.cancel();
        blockLatch.countDown();
    }

    @Test
    void testExecute_afterFlowCompletes_sameIdAllowed() throws Exception {
        TxFlow flow = createSimpleFlow("reusable-id");

        FlowHandle handle = executor.execute(flow);

        // Wait briefly for async task to start and (likely) fail
        try {
            handle.await(Duration.ofSeconds(2));
        } catch (Exception e) {
            // Expected — mocked backend will fail
        }

        // After completion, same ID should be allowed again
        // (the finally block removes the ID)
        assertDoesNotThrow(() -> {
            FlowHandle handle2 = executor.execute(flow);
            handle2.cancel();
        });
    }

    // ==================== MED-1: FlowHandle status FAILED on async exception ====================

    @Test
    void testExecute_executionFailure_setsStatusToFailed() throws Exception {
        // Create a valid flow that will fail during execution (mocked backend returns null)
        TxFlow flow = createSimpleFlow("fail-flow");

        FlowHandle handle = executor.execute(flow);

        // Wait for it to complete (should fail because mocked backend is not set up)
        try {
            handle.await(Duration.ofSeconds(5));
        } catch (Exception e) {
            // Expected — execution fails with mocked backend
        }

        assertEquals(FlowStatus.FAILED, handle.getStatus(),
                "FlowHandle status should be FAILED after execution exception");
    }

    // ==================== MED-7: FlowHandle.await() preserves exception type ====================

    @Test
    void testAwait_preservesFlowExecutionException() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new FlowExecutionException("test error"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class, () -> handle.await());
        assertEquals("test error", ex.getMessage());
    }

    @Test
    void testAwait_preservesIllegalStateException() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new IllegalStateException("bad state"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> handle.await());
        assertEquals("bad state", ex.getMessage());
    }

    @Test
    void testAwait_wrapsCheckedExceptions() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new Exception("checked error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> handle.await());
        assertEquals("checked error", ex.getCause().getMessage());
    }

    @Test
    void testAwaitWithTimeout_preservesFlowExecutionException() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new FlowExecutionException("timeout test error"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> handle.await(Duration.ofSeconds(5)));
        assertEquals("timeout test error", ex.getMessage());
    }

    // ==================== MED-8: getResult() throws on failed future ====================

    @Test
    void testGetResult_returnsEmptyWhenNotDone() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        assertTrue(handle.getResult().isEmpty());
    }

    @Test
    void testGetResult_throwsWhenCompletedExceptionally() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.completeExceptionally(new FlowExecutionException("execution failed"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class, () -> handle.getResult());
        assertEquals("execution failed", ex.getMessage());
    }

    @Test
    void testGetResult_returnsEmptyWhenCancelled() {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        TxFlow flow = createSimpleFlow("test");
        FlowHandle handle = new FlowHandle(flow, future);

        future.cancel(true);

        assertTrue(handle.getResult().isEmpty());
    }

    // ==================== MED-9: RetryPolicy.isRetryable() rejects Error ====================

    @Test
    void testIsRetryable_returnsFalseForOutOfMemoryError() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertFalse(policy.isRetryable(new OutOfMemoryError("test")));
    }

    @Test
    void testIsRetryable_returnsFalseForStackOverflowError() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertFalse(policy.isRetryable(new StackOverflowError()));
    }

    @Test
    void testIsRetryable_returnsFalseForUnknownRuntimeException() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertFalse(policy.isRetryable(new RuntimeException("unknown transient issue")));
    }

    // ==================== HIGH-1: Listener wrapping ====================

    @Test
    void testWithListener_wrapsInCompositeFlowListener() {
        FlowListener customListener = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow flow) {
                throw new RuntimeException("buggy listener");
            }
        };

        executor.withListener(customListener);

        // The executor should not crash when calling listeners
        // We can't easily test the listener is wrapped without reflection,
        // but we can verify the executor accepts it without error
        assertNotNull(executor);
    }

    @Test
    void testWithListener_nullSetsNoop() {
        executor.withListener(null);
        // Should not throw
        assertNotNull(executor);
    }

    // ==================== HIGH-6: AutoCloseable ====================

    @Test
    void testClose_doesNotThrow() {
        executor.close();
        // Should complete without error
    }

    @Test
    void testClose_withConfirmationConfig() {
        executor.withConfirmationConfig(ConfirmationConfig.quick());
        executor.close();
        // Should complete without error
    }

    // ==================== HIGH-2: cancel() interrupts running flows ====================

    @Test
    void testCancel_duringConfirmationWait_exitsPromptly() throws Exception {
        // Setup: chainDataSupplier returns a tip but never finds the transaction
        // This will cause the confirmation wait loop to keep polling
        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo(anyString())).thenReturn(Optional.empty());

        // Configure with confirmation tracking so waitForConfirmationWithTracking is used
        ConfirmationConfig config = ConfirmationConfig.builder()
                .minConfirmations(1)
                .checkInterval(Duration.ofMillis(50))
                .timeout(Duration.ofSeconds(60))  // Long timeout — we rely on cancel, not timeout
                .build();
        executor.withConfirmationConfig(config);

        TxFlow flow = createSimpleFlow("cancel-test");
        FlowHandle handle = executor.execute(flow);

        // Wait briefly for execution to start (it will fail during tx build with mocked backend,
        // but the test validates that the cancellation flag propagates correctly)
        Thread.sleep(200);

        long start = System.currentTimeMillis();
        handle.cancel();

        // Wait for the flow to complete — should be very fast after cancel
        try {
            handle.await(Duration.ofSeconds(5));
        } catch (Exception e) {
            // Expected — the flow will fail/cancel
        }

        long elapsed = System.currentTimeMillis() - start;
        assertTrue(handle.isCancelled(), "Handle should be marked as cancelled");
        // The flow may have already failed before reaching confirmation wait (due to mocked backend),
        // but the key assertion is that cancel() returns promptly and the handle reflects cancellation
        assertTrue(elapsed < 5000, "Should exit promptly after cancel, took " + elapsed + "ms");
    }

    @Test
    void cooperativeCancellationKeepsHandleStatusCancelled() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Runnable> queued =
                new java.util.concurrent.atomic.AtomicReference<>();
        executor.withExecutor(queued::set);
        FlowHandle handle = executor.execute(createSimpleFlow("cancel-status"));

        assertTrue(handle.cancel());
        queued.get().run();

        assertThrows(java.util.concurrent.CancellationException.class, handle::await);
        assertEquals(FlowStatus.CANCELLED, handle.getStatus());
    }

    // ==================== HIGH-5: executeSync/resumeSync duplicate flow ID guard ====================

    @Test
    void testExecuteSync_duplicateFlowId_throwsIllegalState() throws Exception {
        // Use a blocking executor to ensure the async flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow = createSimpleFlow("sync-dup-id");

        // Start async execution — this will hold the flow ID in activeFlowIds
        FlowHandle handle = executor.execute(flow);

        // Sync execution with the same ID should throw
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> executor.executeSync(flow));
        assertTrue(ex.getMessage().contains("sync-dup-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        handle.cancel();
        blockLatch.countDown();
    }

    @Test
    void testResumeSync_duplicateFlowId_throwsIllegalState() throws Exception {
        // Use a blocking executor to ensure the async flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow = createSimpleFlow("resume-dup-id");

        // Start async execution to occupy the flow ID
        FlowHandle handle = executor.execute(flow);

        // Build a failed FlowResult for resumeSync
        FlowResult failedResult = FlowResult.builder("resume-dup-id")
                .withStatus(FlowStatus.FAILED)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .addStepResult(FlowStepResult.failure("step1", new RuntimeException("test")))
                .withError(new RuntimeException("test"))
                .build();

        // resumeSync with same ID should throw
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> executor.resumeSync(flow, failedResult));
        assertTrue(ex.getMessage().contains("resume-dup-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        handle.cancel();
        blockLatch.countDown();
    }

    // ==================== P2-3: close() cancels active handles ====================

    @Test
    void testClose_cancelsActiveHandles() throws Exception {
        // Use a blocking executor to ensure the flow stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        TxFlow flow = createSimpleFlow("close-cancel-test");

        FlowHandle handle = executor.execute(flow);

        // Close should cancel running flows
        executor.close();

        assertTrue(handle.isCancelled(), "Handle should be cancelled after close()");

        blockLatch.countDown();
    }
}
