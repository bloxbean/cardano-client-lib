package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.exec.store.FlowStateSnapshot;
import com.bloxbean.cardano.client.txflow.exec.store.InMemoryFlowStateStore;
import com.bloxbean.cardano.client.txflow.exec.store.StepStateSnapshot;
import com.bloxbean.cardano.client.txflow.exec.store.TransactionState;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FlowExecutor resume/retry functionality.
 */
class FlowExecutorResumeTest {

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

    private TxFlow createThreeStepFlow(String flowId) {
        return TxFlow.builder(flowId)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr2")))
                        .build())
                .addStep(FlowStep.builder("step3")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr3")))
                        .build())
                .build();
    }

    private FlowResult buildFailedResult(String flowId, FlowStepResult... stepResults) {
        FlowResult.Builder builder = FlowResult.builder(flowId)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .withStatus(FlowStatus.FAILED);
        for (FlowStepResult sr : stepResults) {
            builder.addStepResult(sr);
        }
        return builder.build();
    }

    private FlowStepResult successStep(String stepId, String txHash) {
        return FlowStepResult.success(stepId, txHash, Collections.emptyList(), Collections.emptyList());
    }

    private FlowStepResult failedStep(String stepId) {
        return FlowStepResult.failure(stepId, new RuntimeException("step failed"));
    }

    private InMemoryFlowStateStore stateStoreWithConfirmedPrefix(TxFlow flow) {
        Instant confirmedAt = Instant.parse("2026-07-13T01:02:03Z");
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId(flow.getId())
                .status(FlowStatus.FAILED)
                .startedAt(Instant.parse("2026-07-13T00:00:00Z"))
                .completedAt(Instant.parse("2026-07-13T00:05:00Z"))
                .totalSteps(flow.getSteps().size())
                .completedSteps(1)
                .build();
        snapshot.addStep(StepStateSnapshot.builder()
                .stepId("step1")
                .transactionHash("tx1")
                .state(TransactionState.CONFIRMED)
                .blockHeight(100L)
                .confirmationDepth(3)
                .confirmedAt(confirmedAt)
                .lastChecked(confirmedAt)
                .build());
        snapshot.addStep(StepStateSnapshot.pending("step2"));
        snapshot.addStep(StepStateSnapshot.pending("step3"));

        InMemoryFlowStateStore stateStore = new InMemoryFlowStateStore();
        stateStore.saveFlowState(snapshot);
        return stateStore;
    }

    // ==================== Validation tests ====================

    @Test
    void resumeSync_nullPreviousResult_throwsIllegalArgument() {
        TxFlow flow = createThreeStepFlow("flow1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.resumeSync(flow, null));
        assertTrue(ex.getMessage().contains("previousResult cannot be null"));
    }

    @Test
    void resumeSync_mismatchedFlowId_throwsIllegalArgument() {
        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("different-flow",
                successStep("step1", "tx1"),
                failedStep("step2"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.resumeSync(flow, previousResult));
        assertTrue(ex.getMessage().contains("Flow ID mismatch"));
        assertTrue(ex.getMessage().contains("flow1"));
        assertTrue(ex.getMessage().contains("different-flow"));
    }

    @Test
    void resumeSync_successfulPreviousResult_throwsIllegalArgument() {
        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = FlowResult.builder("flow1")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .addStepResult(successStep("step1", "tx1"))
                .addStepResult(successStep("step2", "tx2"))
                .addStepResult(successStep("step3", "tx3"))
                .success();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.resumeSync(flow, previousResult));
        assertTrue(ex.getMessage().contains("successful"));
        assertTrue(ex.getMessage().contains("nothing to resume"));
    }

    @Test
    void resumeSync_requiresConfirmationConfig_forRollbackStrategy() {
        executor.withRollbackStrategy(RollbackStrategy.REBUILD_FROM_FAILED);
        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> executor.resumeSync(flow, previousResult));
        assertTrue(ex.getMessage().contains("REBUILD_FROM_FAILED"));
    }

    // ==================== verifyPreviousSteps logic tests ====================

    @Test
    void resumeSync_step1Confirmed_skipsStep1() throws Exception {
        // step1 confirmed on-chain, step2 failed
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        // The executor will try to resume and execute step2.
        // step2 execution will fail because backend is mocked (no proper UTXO/protocol params setup).
        // But we can verify the flow doesn't throw on resume logic itself.
        FlowResult result = executor.resumeSync(flow, previousResult);

        // Flow should fail (because mocked backend can't actually build transactions)
        // but it should have gotten past the verification phase
        assertNotNull(result);
        assertEquals(FlowStatus.FAILED, result.getStatus());

        // step1 should be in results (reused from previous)
        Optional<FlowStepResult> step1Result = result.getStepResult("step1");
        assertTrue(step1Result.isPresent());
        assertTrue(step1Result.get().isSuccessful());
        assertEquals("tx1", step1Result.get().getTransactionHash());
    }

    @Test
    void resumeSync_ambiguousAbsence_requiresRecoveryInsteadOfRebuilding() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(Optional.empty());

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        assertThrows(ReconciliationUncertainException.class,
                () -> executor.resumeSync(flow, previousResult));
    }

    @Test
    void resumeSync_verificationException_requiresRecoveryInsteadOfRebuilding() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenThrow(new RuntimeException("network error"));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        ReconciliationUncertainException error = assertThrows(
                ReconciliationUncertainException.class,
                () -> executor.resumeSync(flow, previousResult));
        assertInstanceOf(RuntimeException.class, error.getCause());
    }

    @Test
    void resumeSync_allStepsFailed_reExecutesEntireFlow() throws Exception {
        // First step already failed
        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                failedStep("step1"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        assertEquals(FlowStatus.FAILED, result.getStatus());
    }

    @Test
    void resumeSync_step1WithNullBlockHeight_requiresRecovery() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(null).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        assertThrows(ReconciliationUncertainException.class,
                () -> executor.resumeSync(flow, previousResult));
    }

    @Test
    void resumeSync_shallowConfirmedPrefixWaitsForRequiredDepthAndIsRetainedOnce() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        FlowListener listener = mock(FlowListener.class);
        executor.withScheduler(scheduler).withListener(listener)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(2)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(5))
                        .build());
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));
        when(chainDataSupplier.getChainTipHeight()).thenReturn(100L, 102L, 102L);

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"), failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertEquals(1, result.getStepResults().stream()
                .filter(step -> "step1".equals(step.getStepId())).count());
        verify(listener, times(1)).onStepCompleted(
                argThat(step -> "step1".equals(step.getId())),
                argThat(step -> "tx1".equals(step.getTransactionHash())));
        assertFalse(scheduler.getDelays().isEmpty());
    }

    @Test
    void resumeSync_authoritativeAbsenceWithoutRecordedInclusionRequiresRecovery() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        ChainDataSupplier authoritative = ObservationCapabilities.withAuthoritativeAbsence(chainDataSupplier);
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier,
                        transactionProcessor, authoritative)
                .withScheduler(scheduler)
                .withConfirmationConfig(ConfirmationConfig.builder()
                        .minConfirmations(1)
                        .requiredAuthoritativeAbsences(2)
                        .checkInterval(Duration.ofSeconds(1))
                        .timeout(Duration.ofSeconds(5))
                        .build());
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(Optional.empty());

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"), failedStep("step2"));

        assertThrows(ReconciliationUncertainException.class,
                () -> executor.resumeSync(flow, previousResult));
        verify(chainDataSupplier, atLeast(2)).getTransactionInfo("tx1");
    }

    @Test
    void resumeReconciliationReadsOneChainTipPerPassForMultipleTransactions() throws Exception {
        TestFlowScheduler scheduler = new TestFlowScheduler();
        executor.withScheduler(scheduler).withConfirmationConfig(ConfirmationConfig.builder()
                .minConfirmations(2)
                .checkInterval(Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(5))
                .build());
        when(chainDataSupplier.getTransactionInfo(anyString())).thenAnswer(invocation -> {
            String txHash = invocation.getArgument(0);
            return Optional.of(TransactionInfo.builder().txHash(txHash)
                    .blockHeight(100L).build());
        });
        when(chainDataSupplier.getChainTipHeight()).thenReturn(102L);

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"), successStep("step2", "tx2"), failedStep("step3"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertEquals(FlowStatus.FAILED, result.getStatus());
        verify(chainDataSupplier, times(2)).getChainTipHeight();
        verify(chainDataSupplier, times(2)).getTransactionInfo("tx1");
        verify(chainDataSupplier, times(2)).getTransactionInfo("tx2");
    }

    @Test
    void resumeSync_contiguousPrefixOnly_step1And3Confirmed_onlySkipsStep1() throws Exception {
        // step1 confirmed, step2 failed, step3 succeeded (shouldn't happen normally, but edge case)
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));
        when(chainDataSupplier.getTransactionInfo("tx3")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx3").blockHeight(102L).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"),
                successStep("step3", "tx3"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        // step1 should be skipped (reused)
        Optional<FlowStepResult> step1Result = result.getStepResult("step1");
        assertTrue(step1Result.isPresent());
        assertTrue(step1Result.get().isSuccessful());
        assertEquals("tx1", step1Result.get().getTransactionHash());
    }

    // ==================== Multiple resume attempts ====================

    @Test
    void resumeSync_multipleResumesOnSameResult_allowed() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        // First resume
        FlowResult result1 = executor.resumeSync(flow, previousResult);
        assertNotNull(result1);

        // Second resume with same previousResult — should work (FlowResult is immutable)
        FlowResult result2 = executor.resumeSync(flow, previousResult);
        assertNotNull(result2);
    }

    // ==================== Flow with extra steps ====================

    @Test
    void resumeSync_flowHasMoreStepsThanOriginal_executesNewSteps() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        // Original flow had 2 steps, new flow has 3 (step4 added)
        TxFlow flow = TxFlow.builder("flow1")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr2")))
                        .build())
                .addStep(FlowStep.builder("step4")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr4")))
                        .build())
                .build();

        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);
        assertNotNull(result);
        // step1 should be skipped
        Optional<FlowStepResult> step1Result = result.getStepResult("step1");
        assertTrue(step1Result.isPresent());
        assertTrue(step1Result.get().isSuccessful());
    }

    // ==================== Async resume tests ====================

    @Test
    void resume_nullPreviousResult_throwsIllegalArgument() {
        TxFlow flow = createThreeStepFlow("flow1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.resume(flow, null));
        assertTrue(ex.getMessage().contains("previousResult cannot be null"));
    }

    @Test
    void resume_mismatchedFlowId_throwsIllegalArgument() {
        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("different-flow",
                successStep("step1", "tx1"),
                failedStep("step2"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.resume(flow, previousResult));
        assertTrue(ex.getMessage().contains("Flow ID mismatch"));
    }

    @Test
    void resume_successfulPreviousResult_throwsIllegalArgument() {
        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = FlowResult.builder("flow1")
                .startedAt(Instant.now())
                .success();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.resume(flow, previousResult));
        assertTrue(ex.getMessage().contains("nothing to resume"));
    }

    @Test
    void resume_asyncBasicFlow_returnsHandle() throws Exception {
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowHandle handle = executor.resume(flow, previousResult);
        assertNotNull(handle);

        // Wait for completion (will fail because of mocked backend)
        try {
            handle.await(Duration.ofSeconds(5));
        } catch (Exception e) {
            // Expected
        }

        // Verify handle completes
        assertNotNull(handle.getStatus());
    }

    @Test
    void resume_duplicateFlowId_throwsIllegalState() throws Exception {
        // Use a blocking executor to ensure the first resume stays active
        CountDownLatch blockLatch = new CountDownLatch(1);
        executor.withExecutor(r -> new Thread(() -> {
            try { blockLatch.await(); } catch (InterruptedException ignored) {}
            r.run();
        }).start());

        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("same-id");
        FlowResult previousResult = buildFailedResult("same-id",
                successStep("step1", "tx1"),
                failedStep("step2"));

        // First resume
        FlowHandle handle1 = executor.resume(flow, previousResult);

        // Second resume with same ID should throw
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> executor.resume(flow, previousResult));
        assertTrue(ex.getMessage().contains("same-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        handle1.cancel();
        blockLatch.countDown();
    }

    @ParameterizedTest
    @EnumSource(ChainingMode.class)
    void resumeSync_preservesPersistedConfirmedPrefixInEveryMode(ChainingMode mode) throws Exception {
        TxFlow flow = createThreeStepFlow("persisted-prefix-" + mode);
        InMemoryFlowStateStore stateStore = stateStoreWithConfirmedPrefix(flow);
        FlowListener listener = mock(FlowListener.class);
        executor.withChainingMode(mode)
                .withStateStore(stateStore)
                .withListener(listener);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        FlowResult previousResult = buildFailedResult(flow.getId(),
                successStep("step1", "tx1"), failedStep("step2"));
        executor.resumeSync(flow, previousResult);

        FlowStateSnapshot persisted = stateStore.getFlowState(flow.getId()).orElseThrow();
        StepStateSnapshot retained = persisted.getStep("step1");
        assertAll(
                () -> assertEquals(TransactionState.CONFIRMED, retained.getState()),
                () -> assertEquals("tx1", retained.getTransactionHash()),
                () -> assertEquals(100L, retained.getBlockHeight()),
                () -> assertEquals(3, retained.getConfirmationDepth()),
                () -> assertEquals(Instant.parse("2026-07-13T01:02:03Z"), retained.getConfirmedAt()),
                () -> assertEquals(1, persisted.getCompletedSteps()));
        verify(listener, never()).onFlowStarted(any());
    }

    @Test
    void resumeAsync_preservesPersistedConfirmedPrefix() throws Exception {
        TxFlow flow = createThreeStepFlow("async-persisted-prefix");
        InMemoryFlowStateStore stateStore = stateStoreWithConfirmedPrefix(flow);
        FlowListener listener = mock(FlowListener.class);
        executor.withStateStore(stateStore).withListener(listener);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        FlowResult previousResult = buildFailedResult(flow.getId(),
                successStep("step1", "tx1"), failedStep("step2"));
        executor.resume(flow, previousResult).await(Duration.ofSeconds(5));

        StepStateSnapshot retained = stateStore.getFlowState(flow.getId()).orElseThrow()
                .getStep("step1");
        assertEquals(TransactionState.CONFIRMED, retained.getState());
        assertEquals("tx1", retained.getTransactionHash());
        verify(listener, never()).onFlowStarted(any());
    }

    @ParameterizedTest
    @EnumSource(ChainingMode.class)
    void cancelledAsyncResumeDoesNotFailDuringRetainedPrefixRecheck(
            ChainingMode mode) throws Exception {
        AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        executor.withChainingMode(mode).withExecutor(queuedTask::set);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder()
                        .txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("cancelled-prefix-" + mode.name().toLowerCase());
        FlowResult previousResult = buildFailedResult(flow.getId(),
                successStep("step1", "tx1"), failedStep("step2"));

        FlowHandle handle = executor.resume(flow, previousResult);
        assertNotNull(queuedTask.get());
        assertTrue(handle.cancel());
        queuedTask.get().run();

        assertEquals(FlowStatus.CANCELLED, handle.getStatus());
        verify(chainDataSupplier, times(1)).getTransactionInfo("tx1");
    }

    @Test
    void freshExecutionFollowedBySyncAndAsyncResumeFiresFlowStartedExactlyOnce() throws Exception {
        TxFlow flow = createThreeStepFlow("single-start-callback");
        FlowListener listener = mock(FlowListener.class);
        executor.withListener(listener);

        FlowResult previousResult = executor.executeSync(flow);
        assertFalse(previousResult.isSuccessful());

        executor.resumeSync(flow, previousResult);
        executor.resume(flow, previousResult).await(Duration.ofSeconds(5));

        verify(listener, times(1)).onFlowStarted(flow);
    }

    // ==================== Mode-specific resume tests ====================

    @ParameterizedTest
    @EnumSource(ChainingMode.class)
    void resumePrefixResultHashAndCallbacksAreEmittedExactlyOnce(ChainingMode mode) throws Exception {
        FlowListener listener = mock(FlowListener.class);
        executor.withChainingMode(mode).withListener(listener);
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("shared-" + mode);
        FlowResult previousResult = buildFailedResult(flow.getId(),
                successStep("step1", "tx1"), failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertEquals(1, result.getStepResults().stream()
                .filter(step -> "step1".equals(step.getStepId())).count());
        assertEquals(1, result.getTransactionHashes().stream()
                .filter("tx1"::equals).count());
        verify(listener, times(1)).onTransactionConfirmed(
                argThat(step -> "step1".equals(step.getId())), eq("tx1"));
        verify(listener, times(1)).onStepCompleted(
                argThat(step -> "step1".equals(step.getId())),
                argThat(step -> "tx1".equals(step.getTransactionHash())));
    }

    @Test
    void freshAndResumeShareExactlyThreeModeExecutionStrategies() {
        List<String> strategies = java.util.Arrays.stream(FlowExecutor.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.matches("doExecute(Sequential|Pipelined|Batch)"))
                .sorted()
                .toList();

        assertEquals(List.of("doExecuteBatch", "doExecutePipelined", "doExecuteSequential"),
                strategies);
        assertTrue(java.util.Arrays.stream(FlowExecutor.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().contains("WithResume")));
    }

    @Test
    void resumeSync_pipelinedMode_step1Confirmed() throws Exception {
        executor.withChainingMode(ChainingMode.PIPELINED);

        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        // step1 should be reused
        Optional<FlowStepResult> step1Result = result.getStepResult("step1");
        assertTrue(step1Result.isPresent());
        assertTrue(step1Result.get().isSuccessful());
        assertEquals("tx1", step1Result.get().getTransactionHash());
    }

    @Test
    void resumeSync_batchMode_step1Confirmed() throws Exception {
        executor.withChainingMode(ChainingMode.BATCH);

        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        Optional<FlowStepResult> step1Result = result.getStepResult("step1");
        assertTrue(step1Result.isPresent());
        assertTrue(step1Result.get().isSuccessful());
        assertEquals("tx1", step1Result.get().getTransactionHash());
    }

    @Test
    void resumeSync_sequentialMode_isDefault() throws Exception {
        // Verify SEQUENTIAL is the default mode
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(100L).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        // step1 should be reused
        Optional<FlowStepResult> step1Result = result.getStepResult("step1");
        assertTrue(step1Result.isPresent());
        assertTrue(step1Result.get().isSuccessful());
    }

    // ==================== Edge case: empty previous result (no steps recorded) ====================

    @Test
    void resumeSync_emptyPreviousResult_reExecutesAll() throws Exception {
        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1");

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        assertEquals(FlowStatus.FAILED, result.getStatus());
    }

    // ==================== Validation fails on invalid flow ====================

    @Test
    void resumeSync_invalidFlow_throwsFlowExecutionException() {
        // Flow with a step that depends on a non-existent step triggers validation failure
        TxFlow flow = TxFlow.builder("flow1")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .dependsOn("nonexistent-step")
                        .build())
                .build();
        FlowResult previousResult = buildFailedResult("flow1", failedStep("step1"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> executor.resumeSync(flow, previousResult));
        assertTrue(ex.getMessage().contains("validation failed"));
    }

    @Test
    void resume_invalidFlow_throwsFlowExecutionException() {
        TxFlow flow = TxFlow.builder("flow1")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .dependsOn("nonexistent-step")
                        .build())
                .build();
        FlowResult previousResult = buildFailedResult("flow1", failedStep("step1"));

        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> executor.resume(flow, previousResult));
        assertTrue(ex.getMessage().contains("validation failed"));
    }
}
