package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for BasicWatchHandle monitoring features.
 * 
 * Tests all aspects of:
 * - CompletableFuture integration
 * - Step completion callbacks
 * - Chain progress monitoring
 * - Timeout and cancellation support
 * - Result retrieval methods
 */
class BasicWatchHandleTest {

    private BasicWatchHandle watchHandle;
    private static final String TEST_CHAIN_ID = "test-chain";
    private static final int TOTAL_STEPS = 3;
    private static final String TEST_DESCRIPTION = "Test chain description";

    @BeforeEach
    void setUp() {
        watchHandle = new BasicWatchHandle(TEST_CHAIN_ID, TOTAL_STEPS, TEST_DESCRIPTION);
    }

    // ========== Basic Functionality Tests ==========

    @Test
    void testBasicCreation() {
        assertEquals(TEST_CHAIN_ID, watchHandle.getChainId());
        assertEquals(WatchStatus.PENDING, watchHandle.getStatus());
        assertEquals(TOTAL_STEPS, watchHandle.getStepStatuses().size() + (TOTAL_STEPS - watchHandle.getStepStatuses().size()));
        assertTrue(watchHandle.getDescription().isPresent());
        assertEquals(TEST_DESCRIPTION, watchHandle.getDescription().get());
        assertFalse(watchHandle.isCompleted());
        assertFalse(watchHandle.isSuccessful());
        assertTrue(watchHandle.getError().isEmpty());
    }

    @Test
    void testCreationWithoutDescription() {
        BasicWatchHandle handle = new BasicWatchHandle("test-id", 2);
        assertEquals("test-id", handle.getChainId());
        assertTrue(handle.getDescription().isEmpty());
    }

    @Test
    void testProgressCalculation() {
        assertEquals(0.0, watchHandle.getProgress(), 0.001);

        // Add one completed step
        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        watchHandle.recordStepResult("step1", step1);
        assertEquals(1.0 / 3.0, watchHandle.getProgress(), 0.001);

        // Add another completed step
        StepResult step2 = createStepResult("step2", WatchStatus.CONFIRMED, "tx2");
        watchHandle.recordStepResult("step2", step2);
        assertEquals(2.0 / 3.0, watchHandle.getProgress(), 0.001);

        // Complete all steps
        StepResult step3 = createStepResult("step3", WatchStatus.CONFIRMED, "tx3");
        watchHandle.recordStepResult("step3", step3);
        assertEquals(1.0, watchHandle.getProgress(), 0.001);
    }

    // ========== CompletableFuture Integration Tests ==========

    @Test
    void testChainFutureCompletionOnSuccess() throws InterruptedException, ExecutionException {
        CompletableFuture<ChainResult> future = watchHandle.getChainFuture();
        assertFalse(future.isDone());

        // Complete all steps
        completeAllStepsSuccessfully();

        assertTrue(future.isDone());
        ChainResult result = future.get();
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(TEST_CHAIN_ID, result.getChainId());
        assertEquals(3, result.getSuccessfulStepCount());
        assertEquals(0, result.getFailedStepCount());
    }

    @Test
    void testChainFutureCompletionOnFailure() throws InterruptedException, ExecutionException {
        CompletableFuture<ChainResult> future = watchHandle.getChainFuture();
        
        Exception testError = new RuntimeException("Test failure");
        watchHandle.markFailed(testError);

        assertTrue(future.isDone());
        ChainResult result = future.get();
        assertNotNull(result);
        assertTrue(result.isFailed());
        assertEquals(WatchStatus.FAILED, result.getStatus());
        assertTrue(result.getError().isPresent());
        assertEquals(testError, result.getError().get());
    }

    @Test
    void testChainFutureCompletionOnCancellation() throws InterruptedException, ExecutionException {
        CompletableFuture<ChainResult> future = watchHandle.getChainFuture();
        
        watchHandle.cancelChain();

        assertTrue(future.isDone());
        ChainResult result = future.get();
        assertNotNull(result);
        assertTrue(result.isCancelled());
        assertEquals(WatchStatus.CANCELLED, result.getStatus());
    }

    // ========== Step Completion Callback Tests ==========

    @Test
    void testStepCompletionCallbacks() {
        List<StepResult> capturedResults = new ArrayList<>();
        Consumer<StepResult> stepListener = capturedResults::add;
        
        watchHandle.onStepComplete(stepListener);

        // Add step results and verify callbacks
        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        watchHandle.recordStepResult("step1", step1);
        
        assertEquals(1, capturedResults.size());
        assertEquals(step1, capturedResults.get(0));

        StepResult step2 = createStepResult("step2", WatchStatus.FAILED, null);
        watchHandle.recordStepResult("step2", step2);
        
        assertEquals(2, capturedResults.size());
        assertEquals(step2, capturedResults.get(1));
    }

    @Test
    void testMultipleStepListeners() {
        List<StepResult> listener1Results = new ArrayList<>();
        List<StepResult> listener2Results = new ArrayList<>();
        
        watchHandle.onStepComplete(listener1Results::add);
        watchHandle.onStepComplete(listener2Results::add);

        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        watchHandle.recordStepResult("step1", step1);

        assertEquals(1, listener1Results.size());
        assertEquals(1, listener2Results.size());
        assertEquals(step1, listener1Results.get(0));
        assertEquals(step1, listener2Results.get(0));
    }

    @Test
    void testChainCompletionCallbacks() {
        List<ChainResult> capturedResults = new ArrayList<>();
        Consumer<ChainResult> chainListener = capturedResults::add;
        
        watchHandle.onChainComplete(chainListener);
        assertTrue(capturedResults.isEmpty()); // Not completed yet

        completeAllStepsSuccessfully();

        assertEquals(1, capturedResults.size());
        ChainResult result = capturedResults.get(0);
        assertTrue(result.isSuccessful());
        assertEquals(3, result.getSuccessfulStepCount());
    }

    @Test
    void testChainCallbackOnAlreadyCompletedChain() {
        // Complete the chain first
        completeAllStepsSuccessfully();
        
        List<ChainResult> capturedResults = new ArrayList<>();
        Consumer<ChainResult> chainListener = capturedResults::add;
        
        // Add listener after completion - should be called immediately
        watchHandle.onChainComplete(chainListener);
        
        assertEquals(1, capturedResults.size());
        assertTrue(capturedResults.get(0).isSuccessful());
    }

    // ========== Timeout and Cancellation Tests ==========

    @Test
    void testAwaitWithTimeout() throws InterruptedException, ExecutionException, TimeoutException {
        // Start async completion
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100); // Simulate some work
                completeAllStepsSuccessfully();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ChainResult result = watchHandle.await(Duration.ofSeconds(5));
        assertNotNull(result);
        assertTrue(result.isSuccessful());
    }

    @Test
    void testAwaitTimeout() {
        assertThrows(TimeoutException.class, () -> {
            watchHandle.await(Duration.ofMillis(100));
        });
    }

    @Test
    void testAwaitWithoutTimeout() throws InterruptedException, ExecutionException {
        // Start async completion
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
                completeAllStepsSuccessfully();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ChainResult result = watchHandle.await();
        assertNotNull(result);
        assertTrue(result.isSuccessful());
    }

    @Test
    void testCancellation() {
        assertFalse(watchHandle.isCancelled());
        
        watchHandle.cancelChain();
        
        assertTrue(watchHandle.isCancelled());
        assertTrue(watchHandle.isCompleted());
        assertEquals(WatchStatus.CANCELLED, watchHandle.getStatus());
        assertTrue(watchHandle.cancel()); // Should also work via parent method
    }

    // ========== Result Retrieval Tests ==========

    @Test
    void testGetCurrentResultIncomplete() {
        ChainResult result = watchHandle.getCurrentResult();
        assertNotNull(result);
        assertEquals(WatchStatus.PENDING, result.getStatus());
        assertFalse(result.isSuccessful());
        assertEquals(0, result.getStepResults().size());
        assertTrue(result.getCompletedAt().isEmpty());
        assertTrue(result.getDuration().isEmpty());
    }

    @Test
    void testGetCurrentResultWithPartialProgress() {
        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        watchHandle.recordStepResult("step1", step1);

        ChainResult result = watchHandle.getCurrentResult();
        assertEquals(WatchStatus.WATCHING, result.getStatus());
        assertEquals(1, result.getStepResults().size());
        assertEquals(1, result.getSuccessfulStepCount());
        assertEquals(0, result.getFailedStepCount());
    }

    @Test
    void testGetCurrentResultCompleted() {
        completeAllStepsSuccessfully();

        ChainResult result = watchHandle.getCurrentResult();
        assertTrue(result.isSuccessful());
        assertEquals(WatchStatus.CONFIRMED, result.getStatus());
        assertEquals(3, result.getSuccessfulStepCount());
        assertTrue(result.getCompletedAt().isPresent());
        assertTrue(result.getDuration().isPresent());
        assertEquals(3, result.getTransactionHashes().size());
    }

    @Test
    void testGetTransactionHashes() {
        assertTrue(watchHandle.getTransactionHashes().isEmpty());

        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        StepResult step2 = createStepResult("step2", WatchStatus.FAILED, null);
        StepResult step3 = createStepResult("step3", WatchStatus.CONFIRMED, "tx3");

        watchHandle.recordStepResult("step1", step1);
        watchHandle.recordStepResult("step2", step2);
        watchHandle.recordStepResult("step3", step3);

        List<String> hashes = watchHandle.getTransactionHashes();
        assertEquals(2, hashes.size()); // Only successful steps
        assertTrue(hashes.contains("tx1"));
        assertTrue(hashes.contains("tx3"));
    }

    // ========== Error Handling Tests ==========

    @Test
    void testStepFailureCausesChainFailure() {
        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        StepResult step2 = createStepResult("step2", WatchStatus.FAILED, null);
        
        watchHandle.recordStepResult("step1", step1);
        assertEquals(WatchStatus.WATCHING, watchHandle.getStatus());
        
        watchHandle.recordStepResult("step2", step2);
        assertEquals(WatchStatus.FAILED, watchHandle.getStatus());
        assertTrue(watchHandle.isCompleted());
    }

    @Test
    void testCallbackExceptionHandling() {
        // Add a listener that throws an exception
        watchHandle.onStepComplete(result -> {
            throw new RuntimeException("Listener error");
        });

        // Add a normal listener to verify it still gets called
        List<StepResult> capturedResults = new ArrayList<>();
        watchHandle.onStepComplete(capturedResults::add);

        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        watchHandle.recordStepResult("step1", step1);

        // Normal listener should still receive the event
        assertEquals(1, capturedResults.size());
        assertEquals(step1, capturedResults.get(0));
    }

    // ========== Status Transition Tests ==========

    @Test
    void testStatusTransitionSequence() {
        assertEquals(WatchStatus.PENDING, watchHandle.getStatus());

        // First step
        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        watchHandle.recordStepResult("step1", step1);
        assertEquals(WatchStatus.WATCHING, watchHandle.getStatus());

        // Second step
        StepResult step2 = createStepResult("step2", WatchStatus.CONFIRMED, "tx2");
        watchHandle.recordStepResult("step2", step2);
        assertEquals(WatchStatus.WATCHING, watchHandle.getStatus());

        // Final step
        StepResult step3 = createStepResult("step3", WatchStatus.CONFIRMED, "tx3");
        watchHandle.recordStepResult("step3", step3);
        assertEquals(WatchStatus.CONFIRMED, watchHandle.getStatus());
        assertTrue(watchHandle.isCompleted());
        assertTrue(watchHandle.isSuccessful());
    }

    @Test
    void testStatusImmutableAfterCompletion() {
        completeAllStepsSuccessfully();
        assertEquals(WatchStatus.CONFIRMED, watchHandle.getStatus());

        // Try to update status - should be ignored
        watchHandle.updateStepStatus("new-step", WatchStatus.FAILED);
        assertEquals(WatchStatus.CONFIRMED, watchHandle.getStatus());
    }

    // ========== Helper Methods ==========

    private void completeAllStepsSuccessfully() {
        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        StepResult step2 = createStepResult("step2", WatchStatus.CONFIRMED, "tx2");
        StepResult step3 = createStepResult("step3", WatchStatus.CONFIRMED, "tx3");

        watchHandle.recordStepResult("step1", step1);
        watchHandle.recordStepResult("step2", step2);
        watchHandle.recordStepResult("step3", step3);
    }

    private StepResult createStepResult(String stepId, WatchStatus status, String txHash) {
        if (status == WatchStatus.CONFIRMED) {
            return StepResult.success(stepId, txHash, null);
        } else if (status == WatchStatus.FAILED) {
            return StepResult.failure(stepId, new RuntimeException("Test failure"));
        } else {
            // For other statuses, use the constructor
            return new StepResult(stepId, status, txHash, null);
        }
    }
}