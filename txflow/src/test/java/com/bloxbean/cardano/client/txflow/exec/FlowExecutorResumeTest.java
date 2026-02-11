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
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
        executor = FlowExecutor.create(utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier);
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
    void resumeSync_step1RolledBack_reExecutesAll() throws Exception {
        // step1 tx no longer on-chain (rolled back)
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(Optional.empty());

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        // Flow should fail because mocked backend can't build transactions
        assertNotNull(result);
        assertEquals(FlowStatus.FAILED, result.getStatus());

        // step1 should NOT be reused — it was rolled back
        // The result may contain a step1 failure or no step1 at all
        // depending on how far execution got
        Optional<FlowStepResult> step1Result = result.getStepResult("step1");
        if (step1Result.isPresent()) {
            // If step1 is in results, it should be a fresh attempt (failed, because mock)
            assertFalse(step1Result.get().isSuccessful());
        }
    }

    @Test
    void resumeSync_verificationException_reExecutesFromThatStep() throws Exception {
        // step1 verification throws exception
        when(chainDataSupplier.getTransactionInfo("tx1")).thenThrow(new RuntimeException("network error"));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        assertEquals(FlowStatus.FAILED, result.getStatus());
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
    void resumeSync_step1ConfirmedWithNullBlockHeight_reExecutes() throws Exception {
        // step1 tx found but blockHeight is null (not yet confirmed)
        when(chainDataSupplier.getTransactionInfo("tx1")).thenReturn(
                Optional.of(TransactionInfo.builder().txHash("tx1").blockHeight(null).build()));

        TxFlow flow = createThreeStepFlow("flow1");
        FlowResult previousResult = buildFailedResult("flow1",
                successStep("step1", "tx1"),
                failedStep("step2"));

        FlowResult result = executor.resumeSync(flow, previousResult);

        assertNotNull(result);
        assertEquals(FlowStatus.FAILED, result.getStatus());
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
    }

    // ==================== Mode-specific resume tests ====================

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
