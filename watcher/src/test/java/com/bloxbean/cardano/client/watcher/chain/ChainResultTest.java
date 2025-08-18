package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ChainResult.
 */
class ChainResultTest {

    private static final String TEST_CHAIN_ID = "test-chain";
    private static final String TEST_DESCRIPTION = "Test chain description";

    @Test
    void testSuccessResultCreation() {
        Instant startTime = Instant.now().minusSeconds(60);
        Instant endTime = Instant.now();
        Map<String, StepResult> stepResults = createSuccessfulStepResults();

        ChainResult result = ChainResult.success(TEST_CHAIN_ID, stepResults, startTime, endTime);

        assertEquals(TEST_CHAIN_ID, result.getChainId());
        assertEquals(WatchStatus.CONFIRMED, result.getStatus());
        assertTrue(result.isSuccessful());
        assertFalse(result.isFailed());
        assertFalse(result.isCancelled());
        assertEquals(startTime, result.getStartedAt());
        assertTrue(result.getCompletedAt().isPresent());
        assertEquals(endTime, result.getCompletedAt().get());
        assertTrue(result.getDuration().isPresent());
        assertTrue(result.getError().isEmpty());
        assertTrue(result.getDescription().isEmpty());
        assertEquals(3, result.getStepResults().size());
        assertEquals(3, result.getSuccessfulStepCount());
        assertEquals(0, result.getFailedStepCount());
        assertEquals(3, result.getTransactionHashes().size());
    }

    @Test
    void testFailureResultCreation() {
        Instant startTime = Instant.now().minusSeconds(30);
        Instant endTime = Instant.now();
        Map<String, StepResult> stepResults = createMixedStepResults();
        RuntimeException error = new RuntimeException("Test error");

        ChainResult result = ChainResult.failure(TEST_CHAIN_ID, error, stepResults, startTime, endTime);

        assertEquals(TEST_CHAIN_ID, result.getChainId());
        assertEquals(WatchStatus.FAILED, result.getStatus());
        assertFalse(result.isSuccessful());
        assertTrue(result.isFailed());
        assertFalse(result.isCancelled());
        assertTrue(result.getError().isPresent());
        assertEquals(error, result.getError().get());
        assertEquals(2, result.getSuccessfulStepCount());
        assertEquals(1, result.getFailedStepCount());
        assertEquals(2, result.getTransactionHashes().size()); // Only successful steps
    }

    @Test
    void testCancelledResultCreation() {
        Instant startTime = Instant.now().minusSeconds(15);
        Instant endTime = Instant.now();
        Map<String, StepResult> stepResults = createPartialStepResults();

        ChainResult result = ChainResult.cancelled(TEST_CHAIN_ID, stepResults, startTime, endTime);

        assertEquals(WatchStatus.CANCELLED, result.getStatus());
        assertFalse(result.isSuccessful());
        assertFalse(result.isFailed());
        assertTrue(result.isCancelled());
        assertTrue(result.getError().isEmpty());
        assertEquals(1, result.getSuccessfulStepCount());
        assertEquals(0, result.getFailedStepCount());
    }

    @Test
    void testBuilderPattern() {
        Instant startTime = Instant.now().minusSeconds(45);
        Instant endTime = Instant.now();
        Map<String, StepResult> stepResults = createSuccessfulStepResults();
        
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .status(WatchStatus.CONFIRMED)
            .stepResults(stepResults)
            .startedAt(startTime)
            .completedAt(endTime)
            .description(TEST_DESCRIPTION)
            .build();

        assertEquals(TEST_CHAIN_ID, result.getChainId());
        assertEquals(WatchStatus.CONFIRMED, result.getStatus());
        assertTrue(result.getDescription().isPresent());
        assertEquals(TEST_DESCRIPTION, result.getDescription().get());
        assertEquals(3, result.getStepResults().size());
        assertEquals(startTime, result.getStartedAt());
        assertTrue(result.getCompletedAt().isPresent());
        assertEquals(endTime, result.getCompletedAt().get());
    }

    @Test
    void testBuilderWithIndividualSteps() {
        StepResult step1 = createStepResult("step1", WatchStatus.CONFIRMED, "tx1");
        StepResult step2 = createStepResult("step2", WatchStatus.CONFIRMED, "tx2");
        
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .status(WatchStatus.WATCHING)
            .stepResult("step1", step1)
            .stepResult("step2", step2)
            .build();

        assertEquals(2, result.getStepResults().size());
        assertTrue(result.getStepResults().containsKey("step1"));
        assertTrue(result.getStepResults().containsKey("step2"));
        assertEquals(step1, result.getStepResults().get("step1"));
        assertEquals(step2, result.getStepResults().get("step2"));
    }

    @Test
    void testDurationCalculation() {
        Instant startTime = Instant.now().minusSeconds(120);
        Instant endTime = Instant.now();
        
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .status(WatchStatus.CONFIRMED)
            .startedAt(startTime)
            .completedAt(endTime)
            .build();

        assertTrue(result.getDuration().isPresent());
        Duration duration = result.getDuration().get();
        assertTrue(duration.getSeconds() >= 115 && duration.getSeconds() <= 125); // Allow some tolerance
    }

    @Test
    void testNoDurationForIncompleteChain() {
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .status(WatchStatus.WATCHING)
            .build();

        assertTrue(result.getDuration().isEmpty());
        assertTrue(result.getCompletedAt().isEmpty());
    }

    @Test
    void testTransactionHashesFiltering() {
        Map<String, StepResult> stepResults = new HashMap<>();
        stepResults.put("step1", createStepResult("step1", WatchStatus.CONFIRMED, "tx1"));
        stepResults.put("step2", createStepResult("step2", WatchStatus.FAILED, null));
        stepResults.put("step3", createStepResult("step3", WatchStatus.CONFIRMED, "tx3"));
        stepResults.put("step4", createStepResult("step4", WatchStatus.CONFIRMED, null)); // Successful but no hash

        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .stepResults(stepResults)
            .build();

        List<String> hashes = result.getTransactionHashes();
        assertEquals(2, hashes.size());
        assertTrue(hashes.contains("tx1"));
        assertTrue(hashes.contains("tx3"));
        assertFalse(hashes.contains(null));
    }

    @Test
    void testStepCounting() {
        Map<String, StepResult> stepResults = createMixedStepResults();
        
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .stepResults(stepResults)
            .build();

        assertEquals(3, stepResults.size());
        assertEquals(2, result.getSuccessfulStepCount());
        assertEquals(1, result.getFailedStepCount());
    }

    @Test
    void testToStringRepresentation() {
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .status(WatchStatus.CONFIRMED)
            .stepResults(createSuccessfulStepResults())
            .startedAt(Instant.now().minusSeconds(30))
            .completedAt(Instant.now())
            .build();

        String toString = result.toString();
        assertTrue(toString.contains(TEST_CHAIN_ID));
        assertTrue(toString.contains("CONFIRMED"));
        assertTrue(toString.contains("successfulSteps=3"));
        assertTrue(toString.contains("totalSteps=3"));
        assertFalse(toString.contains("ongoing"));
    }

    @Test
    void testToStringForOngoingChain() {
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .status(WatchStatus.WATCHING)
            .stepResults(createPartialStepResults())
            .build();

        String toString = result.toString();
        assertTrue(toString.contains("ongoing"));
    }

    @Test
    void testImmutabilityOfStepResults() {
        Map<String, StepResult> originalStepResults = createSuccessfulStepResults();
        Map<String, StepResult> mutableStepResults = new HashMap<>(originalStepResults);
        
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .stepResults(mutableStepResults)
            .build();

        // Modify the original map
        mutableStepResults.clear();
        
        // ChainResult should still have the original data
        assertEquals(3, result.getStepResults().size());
        
        // Try to modify the returned map - should throw exception
        assertThrows(UnsupportedOperationException.class, () -> {
            result.getStepResults().clear();
        });
    }

    @Test
    void testBuilderErrorHandling() {
        RuntimeException error = new RuntimeException("Builder test error");
        
        ChainResult result = ChainResult.builder(TEST_CHAIN_ID)
            .status(WatchStatus.FAILED)
            .error(error)
            .build();

        assertTrue(result.getError().isPresent());
        assertEquals(error, result.getError().get());
        assertTrue(result.isFailed());
    }

    // ========== Helper Methods ==========

    private Map<String, StepResult> createSuccessfulStepResults() {
        Map<String, StepResult> stepResults = new HashMap<>();
        stepResults.put("step1", createStepResult("step1", WatchStatus.CONFIRMED, "tx1"));
        stepResults.put("step2", createStepResult("step2", WatchStatus.CONFIRMED, "tx2"));
        stepResults.put("step3", createStepResult("step3", WatchStatus.CONFIRMED, "tx3"));
        return stepResults;
    }

    private Map<String, StepResult> createMixedStepResults() {
        Map<String, StepResult> stepResults = new HashMap<>();
        stepResults.put("step1", createStepResult("step1", WatchStatus.CONFIRMED, "tx1"));
        stepResults.put("step2", createStepResult("step2", WatchStatus.FAILED, null));
        stepResults.put("step3", createStepResult("step3", WatchStatus.CONFIRMED, "tx3"));
        return stepResults;
    }

    private Map<String, StepResult> createPartialStepResults() {
        Map<String, StepResult> stepResults = new HashMap<>();
        stepResults.put("step1", createStepResult("step1", WatchStatus.CONFIRMED, "tx1"));
        return stepResults;
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