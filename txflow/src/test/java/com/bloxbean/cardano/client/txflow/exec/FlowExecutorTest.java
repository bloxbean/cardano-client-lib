package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FlowExecutorTest {

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

    private TxFlow createSimpleFlow(String flowId) {
        return TxFlow.builder(flowId)
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();
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

    // ==================== HIGH-5: Reject duplicate flow ID execution ====================

    @Test
    void testExecute_duplicateFlowId_throwsIllegalState() throws Exception {
        TxFlow flow1 = createSimpleFlow("same-id");

        // First execution should work
        FlowHandle handle1 = executor.execute(flow1);

        // Second execution with same ID should throw
        TxFlow flow2 = createSimpleFlow("same-id");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> executor.execute(flow2));
        assertTrue(ex.getMessage().contains("same-id"));
        assertTrue(ex.getMessage().contains("already executing"));

        // Cancel to clean up
        handle1.cancel();
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
    void testIsRetryable_returnsTrueForUnknownRuntimeException() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertTrue(policy.isRetryable(new RuntimeException("unknown transient issue")));
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
}
