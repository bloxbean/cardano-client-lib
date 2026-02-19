package com.bloxbean.cardano.client.txflow.exec.store;

import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryFlowStateStore.
 */
class InMemoryFlowStateStoreTest {

    private InMemoryFlowStateStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryFlowStateStore();
    }

    // ==================== Basic CRUD Operations ====================

    @Test
    void testSaveAndGetFlowState() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .description("Test flow")
                .totalSteps(3)
                .build();

        store.saveFlowState(snapshot);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals("flow-1", retrieved.get().getFlowId());
        assertEquals(FlowStatus.IN_PROGRESS, retrieved.get().getStatus());
        assertEquals("Test flow", retrieved.get().getDescription());
        assertEquals(3, retrieved.get().getTotalSteps());
    }

    @Test
    void testSaveReplacesExisting() {
        FlowStateSnapshot snapshot1 = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .description("Original")
                .build();
        store.saveFlowState(snapshot1);

        FlowStateSnapshot snapshot2 = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.COMPLETED)
                .description("Updated")
                .build();
        store.saveFlowState(snapshot2);

        assertEquals(1, store.getFlowCount());
        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(FlowStatus.COMPLETED, retrieved.get().getStatus());
        assertEquals("Updated", retrieved.get().getDescription());
    }

    @Test
    void testGetFlowStateNotFound() {
        Optional<FlowStateSnapshot> retrieved = store.getFlowState("non-existent");
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testDeleteFlow() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());

        assertTrue(store.deleteFlow("flow-1"));
        assertFalse(store.getFlowState("flow-1").isPresent());
        assertFalse(store.deleteFlow("flow-1")); // Already deleted
    }

    @Test
    void testDeleteFlowNotFound() {
        assertFalse(store.deleteFlow("non-existent"));
    }

    // ==================== loadPendingFlows ====================

    @Test
    void testLoadPendingFlowsReturnsInProgressAndPending() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-2")
                .status(FlowStatus.PENDING)
                .build());
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-3")
                .status(FlowStatus.COMPLETED)
                .build());
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-4")
                .status(FlowStatus.FAILED)
                .build());

        List<FlowStateSnapshot> pending = store.loadPendingFlows();
        assertEquals(2, pending.size());
        assertTrue(pending.stream().anyMatch(f -> f.getFlowId().equals("flow-1")));
        assertTrue(pending.stream().anyMatch(f -> f.getFlowId().equals("flow-2")));
    }

    @Test
    void testLoadPendingFlowsIncludesFlowsWithPendingTransactions() {
        // Completed status but has pending transaction - should be included
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.COMPLETED)
                .build();
        snapshot.addStep(StepStateSnapshot.submitted("step-1", "tx-123"));
        store.saveFlowState(snapshot);

        List<FlowStateSnapshot> pending = store.loadPendingFlows();
        assertEquals(1, pending.size());
        assertEquals("flow-1", pending.get(0).getFlowId());
    }

    @Test
    void testLoadPendingFlowsEmptyStore() {
        List<FlowStateSnapshot> pending = store.loadPendingFlows();
        assertTrue(pending.isEmpty());
    }

    // ==================== updateTransactionState ====================

    @Test
    void testUpdateTransactionStateExistingStep() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.addStep(StepStateSnapshot.pending("step-1"));
        store.saveFlowState(snapshot);

        TransactionStateDetails details = TransactionStateDetails.submitted(Instant.now());
        store.updateTransactionState("flow-1", "step-1", "tx-123", details);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        StepStateSnapshot step = retrieved.get().getStep("step-1");
        assertEquals("tx-123", step.getTransactionHash());
        assertEquals(TransactionState.SUBMITTED, step.getState());
        assertNotNull(step.getSubmittedAt());
    }

    @Test
    void testUpdateTransactionStateCreatesNewStep() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        store.saveFlowState(snapshot);

        TransactionStateDetails details = TransactionStateDetails.submitted(Instant.now());
        store.updateTransactionState("flow-1", "new-step", "tx-123", details);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        StepStateSnapshot step = retrieved.get().getStep("new-step");
        assertNotNull(step);
        assertEquals("tx-123", step.getTransactionHash());
    }

    @Test
    void testUpdateTransactionStateWithBlockDetails() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.addStep(StepStateSnapshot.submitted("step-1", "tx-123"));
        store.saveFlowState(snapshot);

        TransactionStateDetails details = TransactionStateDetails.confirmed(12345L, 6, Instant.now());
        store.updateTransactionState("flow-1", "step-1", "tx-123", details);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        StepStateSnapshot step = retrieved.get().getStep("step-1");
        assertEquals(TransactionState.CONFIRMED, step.getState());
        assertEquals(12345L, step.getBlockHeight());
        assertEquals(6, step.getConfirmationDepth());
        assertNotNull(step.getConfirmedAt());
    }

    @Test
    void testUpdateTransactionStateRolledBack() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.addStep(StepStateSnapshot.submitted("step-1", "tx-123"));
        store.saveFlowState(snapshot);

        TransactionStateDetails details = TransactionStateDetails.rolledBack(
                12340L, "Chain fork detected", Instant.now());
        store.updateTransactionState("flow-1", "step-1", "tx-123", details);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        StepStateSnapshot step = retrieved.get().getStep("step-1");
        assertEquals(TransactionState.ROLLED_BACK, step.getState());
        assertEquals("Chain fork detected", step.getErrorMessage());
    }

    @Test
    void testUpdateTransactionStateNonExistentFlow() {
        // Should not throw, just log warning
        TransactionStateDetails details = TransactionStateDetails.submitted(Instant.now());
        store.updateTransactionState("non-existent", "step-1", "tx-123", details);
        // Verify nothing was created
        assertFalse(store.getFlowState("non-existent").isPresent());
    }

    // ==================== markFlowComplete ====================

    @Test
    void testMarkFlowComplete() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .totalSteps(2)
                .build());

        store.markFlowComplete("flow-1", FlowStatus.COMPLETED);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(FlowStatus.COMPLETED, retrieved.get().getStatus());
        assertNotNull(retrieved.get().getCompletedAt());
    }

    @Test
    void testMarkFlowCompleteFailed() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());

        store.markFlowComplete("flow-1", FlowStatus.FAILED);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(FlowStatus.FAILED, retrieved.get().getStatus());
        assertNotNull(retrieved.get().getCompletedAt());
    }

    @Test
    void testMarkFlowCompleteUpdatesCompletedSteps() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .totalSteps(3)
                .build();
        snapshot.addStep(StepStateSnapshot.builder()
                .stepId("step-1")
                .transactionHash("tx-1")
                .state(TransactionState.CONFIRMED)
                .build());
        snapshot.addStep(StepStateSnapshot.builder()
                .stepId("step-2")
                .transactionHash("tx-2")
                .state(TransactionState.CONFIRMED)
                .build());
        snapshot.addStep(StepStateSnapshot.pending("step-3"));
        store.saveFlowState(snapshot);

        store.markFlowComplete("flow-1", FlowStatus.FAILED);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(2, retrieved.get().getCompletedSteps());
    }

    @Test
    void testMarkFlowCompleteNonExistent() {
        // Should not throw
        store.markFlowComplete("non-existent", FlowStatus.COMPLETED);
        // Verify nothing was created
        assertFalse(store.getFlowState("non-existent").isPresent());
    }

    // ==================== Utility Methods ====================

    @Test
    void testGetAllFlows() {
        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-1").build());
        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-2").build());
        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-3").build());

        List<FlowStateSnapshot> all = store.getAllFlows();
        assertEquals(3, all.size());
    }

    @Test
    void testGetFlowCount() {
        assertEquals(0, store.getFlowCount());

        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-1").build());
        assertEquals(1, store.getFlowCount());

        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-2").build());
        assertEquals(2, store.getFlowCount());

        store.deleteFlow("flow-1");
        assertEquals(1, store.getFlowCount());
    }

    @Test
    void testGetFlowsByStatus() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-2")
                .status(FlowStatus.IN_PROGRESS)
                .build());
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-3")
                .status(FlowStatus.COMPLETED)
                .build());

        List<FlowStateSnapshot> inProgress = store.getFlowsByStatus(FlowStatus.IN_PROGRESS);
        assertEquals(2, inProgress.size());

        List<FlowStateSnapshot> completed = store.getFlowsByStatus(FlowStatus.COMPLETED);
        assertEquals(1, completed.size());

        List<FlowStateSnapshot> failed = store.getFlowsByStatus(FlowStatus.FAILED);
        assertTrue(failed.isEmpty());
    }

    @Test
    void testContains() {
        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-1").build());

        assertTrue(store.contains("flow-1"));
        assertFalse(store.contains("flow-2"));
    }

    @Test
    void testClear() {
        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-1").build());
        store.saveFlowState(FlowStateSnapshot.builder().flowId("flow-2").build());
        assertEquals(2, store.getFlowCount());

        store.clear();
        assertEquals(0, store.getFlowCount());
        assertTrue(store.getAllFlows().isEmpty());
    }

    // ==================== Deep Copy Verification ====================

    @Test
    void testGetFlowStateReturnsDeepCopy() {
        FlowStateSnapshot original = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        original.addStep(StepStateSnapshot.pending("step-1"));
        store.saveFlowState(original);

        // Get the snapshot
        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());

        // Modify the retrieved snapshot
        retrieved.get().setStatus(FlowStatus.COMPLETED);
        retrieved.get().getStep("step-1").setTransactionHash("modified-tx");

        // Verify original in store is unchanged
        Optional<FlowStateSnapshot> reRetrieved = store.getFlowState("flow-1");
        assertTrue(reRetrieved.isPresent());
        assertEquals(FlowStatus.IN_PROGRESS, reRetrieved.get().getStatus());
        assertNull(reRetrieved.get().getStep("step-1").getTransactionHash());
    }

    @Test
    void testSaveCreatesDeepCopy() {
        FlowStateSnapshot original = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        original.addStep(StepStateSnapshot.pending("step-1"));
        store.saveFlowState(original);

        // Modify the original after saving
        original.setStatus(FlowStatus.COMPLETED);
        original.getStep("step-1").setTransactionHash("modified-tx");

        // Verify store copy is unchanged
        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(FlowStatus.IN_PROGRESS, retrieved.get().getStatus());
        assertNull(retrieved.get().getStep("step-1").getTransactionHash());
    }

    @Test
    void testLoadPendingFlowsReturnsDeepCopies() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());

        List<FlowStateSnapshot> pending = store.loadPendingFlows();
        assertEquals(1, pending.size());

        // Modify the returned snapshot
        pending.get(0).setStatus(FlowStatus.COMPLETED);

        // Verify original in store is unchanged
        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(FlowStatus.IN_PROGRESS, retrieved.get().getStatus());
    }

    // ==================== Null Parameter Validation ====================

    @Test
    void testSaveFlowStateNullSnapshot() {
        assertThrows(NullPointerException.class, () -> store.saveFlowState(null));
    }

    @Test
    void testSaveFlowStateNullFlowId() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId(null)
                .build();
        assertThrows(NullPointerException.class, () -> store.saveFlowState(snapshot));
    }

    @Test
    void testGetFlowStateNullFlowId() {
        assertThrows(NullPointerException.class, () -> store.getFlowState(null));
    }

    @Test
    void testDeleteFlowNullFlowId() {
        assertThrows(NullPointerException.class, () -> store.deleteFlow(null));
    }

    @Test
    void testContainsNullFlowId() {
        assertThrows(NullPointerException.class, () -> store.contains(null));
    }

    @Test
    void testUpdateTransactionStateNullFlowId() {
        assertThrows(NullPointerException.class, () ->
                store.updateTransactionState(null, "step-1", "tx-123",
                        TransactionStateDetails.submitted(Instant.now())));
    }

    @Test
    void testUpdateTransactionStateNullStepId() {
        assertThrows(NullPointerException.class, () ->
                store.updateTransactionState("flow-1", null, "tx-123",
                        TransactionStateDetails.submitted(Instant.now())));
    }

    @Test
    void testUpdateTransactionStateNullDetails() {
        assertThrows(NullPointerException.class, () ->
                store.updateTransactionState("flow-1", "step-1", "tx-123", null));
    }

    @Test
    void testMarkFlowCompleteNullFlowId() {
        assertThrows(NullPointerException.class, () ->
                store.markFlowComplete(null, FlowStatus.COMPLETED));
    }

    @Test
    void testMarkFlowCompleteNullStatus() {
        assertThrows(NullPointerException.class, () ->
                store.markFlowComplete("flow-1", null));
    }

    @Test
    void testGetFlowsByStatusNullStatus() {
        assertThrows(NullPointerException.class, () -> store.getFlowsByStatus(null));
    }

    // ==================== Concurrent Access ====================

    @Test
    void testConcurrentSave() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int flowCount = 100;
        CountDownLatch latch = new CountDownLatch(flowCount);

        for (int i = 0; i < flowCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    store.saveFlowState(FlowStateSnapshot.builder()
                            .flowId("flow-" + index)
                            .status(FlowStatus.IN_PROGRESS)
                            .build());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(flowCount, store.getFlowCount());

        executor.shutdown();
    }

    @Test
    void testConcurrentUpdateSameFlow() throws Exception {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int updateCount = 100;
        CountDownLatch latch = new CountDownLatch(updateCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < updateCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    TransactionStateDetails details = TransactionStateDetails.submitted(Instant.now());
                    store.updateTransactionState("flow-1", "step-" + index, "tx-" + index, details);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(updateCount, successCount.get());

        // Verify all steps were added
        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(updateCount, retrieved.get().getSteps().size());

        executor.shutdown();
    }

    @Test
    void testConcurrentReadAndWrite() throws Exception {
        // Pre-populate
        for (int i = 0; i < 50; i++) {
            store.saveFlowState(FlowStateSnapshot.builder()
                    .flowId("flow-" + i)
                    .status(FlowStatus.IN_PROGRESS)
                    .build());
        }

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(200);
        AtomicInteger readSuccess = new AtomicInteger(0);
        AtomicInteger writeSuccess = new AtomicInteger(0);

        // Concurrent reads
        for (int i = 0; i < 100; i++) {
            int index = i % 50;
            executor.submit(() -> {
                try {
                    Optional<FlowStateSnapshot> snapshot = store.getFlowState("flow-" + index);
                    if (snapshot.isPresent()) {
                        readSuccess.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Concurrent writes
        for (int i = 50; i < 150; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    store.saveFlowState(FlowStateSnapshot.builder()
                            .flowId("flow-" + index)
                            .status(FlowStatus.IN_PROGRESS)
                            .build());
                    writeSuccess.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(100, readSuccess.get());
        assertEquals(100, writeSuccess.get());

        executor.shutdown();
    }

    // ==================== Builder and Auto-Cleanup ====================

    @Test
    void testBuilderWithoutAutoCleanup() {
        InMemoryFlowStateStore builtStore = InMemoryFlowStateStore.builder().build();
        builtStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());
        assertEquals(1, builtStore.getFlowCount());
    }

    @Test
    void testBuilderWithAutoCleanup() throws Exception {
        InMemoryFlowStateStore cleanupStore = InMemoryFlowStateStore.builder()
                .withAutoCleanup(Duration.ofMillis(100))
                .build();

        cleanupStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());
        assertEquals(1, cleanupStore.getFlowCount());

        // Mark as complete - should trigger auto-cleanup
        cleanupStore.markFlowComplete("flow-1", FlowStatus.COMPLETED);

        // Wait for auto-cleanup (100ms + buffer)
        Thread.sleep(300);

        // Flow should be auto-cleaned
        assertEquals(0, cleanupStore.getFlowCount());
        assertFalse(cleanupStore.contains("flow-1"));

        cleanupStore.shutdown();
    }

    @Test
    void testAutoCleanupDoesNotRemoveInProgressFlows() throws Exception {
        InMemoryFlowStateStore cleanupStore = InMemoryFlowStateStore.builder()
                .withAutoCleanup(Duration.ofMillis(50))
                .build();

        cleanupStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());

        // Mark as complete then immediately set back to in-progress
        cleanupStore.markFlowComplete("flow-1", FlowStatus.COMPLETED);

        // Manually override status back to in-progress (simulating a race condition)
        cleanupStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());

        // Wait for auto-cleanup
        Thread.sleep(150);

        // Flow should NOT be cleaned because it's in-progress
        assertEquals(1, cleanupStore.getFlowCount());

        cleanupStore.shutdown();
    }

    @Test
    void testShutdown() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());
        assertEquals(1, store.getFlowCount());

        store.shutdown();

        // After shutdown, store should be cleared
        assertEquals(0, store.getFlowCount());
    }

    @Test
    void testShutdownWithAutoCleanup() {
        InMemoryFlowStateStore cleanupStore = InMemoryFlowStateStore.builder()
                .withAutoCleanup(Duration.ofMinutes(5))
                .build();

        cleanupStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());

        // Should not throw
        cleanupStore.shutdown();
        assertEquals(0, cleanupStore.getFlowCount());
    }

    // ==================== Edge Cases ====================

    @Test
    void testSaveFlowWithVariables() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.getVariables().put("key1", "value1");
        snapshot.getVariables().put("key2", 123);
        store.saveFlowState(snapshot);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals("value1", retrieved.get().getVariables().get("key1"));
        assertEquals(123, retrieved.get().getVariables().get("key2"));
    }

    @Test
    void testSaveFlowWithMetadata() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.getMetadata().put("source", "test");
        snapshot.getMetadata().put("version", "1.0");
        store.saveFlowState(snapshot);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals("test", retrieved.get().getMetadata().get("source"));
        assertEquals("1.0", retrieved.get().getMetadata().get("version"));
    }

    @Test
    void testUpdateTransactionStateWithNullTxHash() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.addStep(StepStateSnapshot.pending("step-1"));
        store.saveFlowState(snapshot);

        // Update with null tx hash should work (for PENDING state)
        TransactionStateDetails details = TransactionStateDetails.builder()
                .state(TransactionState.PENDING)
                .timestamp(Instant.now())
                .build();
        store.updateTransactionState("flow-1", "step-1", null, details);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        StepStateSnapshot step = retrieved.get().getStep("step-1");
        assertNull(step.getTransactionHash());
        assertEquals(TransactionState.PENDING, step.getState());
    }

    @Test
    void testCompleteFlowWithNoSteps() {
        store.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .totalSteps(0)
                .build());

        store.markFlowComplete("flow-1", FlowStatus.COMPLETED);

        Optional<FlowStateSnapshot> retrieved = store.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(FlowStatus.COMPLETED, retrieved.get().getStatus());
        assertEquals(0, retrieved.get().getCompletedSteps());
    }
}
