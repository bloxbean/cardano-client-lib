package com.bloxbean.cardano.client.txflow.exec.store;

import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FlowStateStore interface and related classes.
 * Uses an in-memory mock implementation for testing.
 */
class FlowStateStoreTest {

    private MockFlowStateStore stateStore;

    @BeforeEach
    void setUp() {
        stateStore = new MockFlowStateStore();
    }

    // ==================== TransactionState Tests ====================

    @Test
    void testTransactionStateIsInProgress() {
        assertTrue(TransactionState.PENDING.isInProgress());
        assertTrue(TransactionState.SUBMITTED.isInProgress());
        assertTrue(TransactionState.IN_BLOCK.isInProgress());
        assertFalse(TransactionState.CONFIRMED.isInProgress());
        assertFalse(TransactionState.ROLLED_BACK.isInProgress());
    }

    @Test
    void testTransactionStateIsSuccessful() {
        assertFalse(TransactionState.PENDING.isSuccessful());
        assertFalse(TransactionState.SUBMITTED.isSuccessful());
        assertFalse(TransactionState.IN_BLOCK.isSuccessful());
        assertTrue(TransactionState.CONFIRMED.isSuccessful());
        assertFalse(TransactionState.ROLLED_BACK.isSuccessful());
    }

    @Test
    void testTransactionStateIsFailed() {
        assertFalse(TransactionState.PENDING.isFailed());
        assertFalse(TransactionState.SUBMITTED.isFailed());
        assertFalse(TransactionState.IN_BLOCK.isFailed());
        assertFalse(TransactionState.CONFIRMED.isFailed());
        assertTrue(TransactionState.ROLLED_BACK.isFailed());
    }

    // ==================== StepStateSnapshot Tests ====================

    @Test
    void testStepStateSnapshotPending() {
        StepStateSnapshot step = StepStateSnapshot.pending("step-1");

        assertEquals("step-1", step.getStepId());
        assertEquals(TransactionState.PENDING, step.getState());
        assertNull(step.getTransactionHash());
        assertFalse(step.isSubmitted());
        assertFalse(step.needsTracking());
    }

    @Test
    void testStepStateSnapshotSubmitted() {
        StepStateSnapshot step = StepStateSnapshot.submitted("step-1", "tx-hash-123");

        assertEquals("step-1", step.getStepId());
        assertEquals(TransactionState.SUBMITTED, step.getState());
        assertEquals("tx-hash-123", step.getTransactionHash());
        assertNotNull(step.getSubmittedAt());
        assertTrue(step.isSubmitted());
        assertTrue(step.needsTracking());
    }

    @Test
    void testStepStateSnapshotWithTrackingFields() {
        Instant now = Instant.now();
        StepStateSnapshot step = StepStateSnapshot.builder()
                .stepId("step-1")
                .transactionHash("tx-123")
                .state(TransactionState.CONFIRMED)
                .submittedAt(now.minusSeconds(60))
                .blockHeight(12345L)
                .confirmationDepth(6)
                .lastChecked(now)
                .confirmedAt(now)
                .build();

        assertEquals("step-1", step.getStepId());
        assertEquals("tx-123", step.getTransactionHash());
        assertEquals(TransactionState.CONFIRMED, step.getState());
        assertEquals(12345L, step.getBlockHeight());
        assertEquals(6, step.getConfirmationDepth());
        assertNotNull(step.getLastChecked());
        assertNotNull(step.getConfirmedAt());
    }

    @Test
    void testStepStateSnapshotWithErrorMessage() {
        StepStateSnapshot step = StepStateSnapshot.builder()
                .stepId("step-1")
                .transactionHash("tx-123")
                .state(TransactionState.ROLLED_BACK)
                .blockHeight(12340L)
                .errorMessage("Chain fork detected")
                .build();

        assertEquals(TransactionState.ROLLED_BACK, step.getState());
        assertEquals(12340L, step.getBlockHeight());
        assertEquals("Chain fork detected", step.getErrorMessage());
    }

    @Test
    void testStepStateSnapshotNeedsTracking() {
        // Pending without tx hash - doesn't need tracking
        StepStateSnapshot pending = StepStateSnapshot.pending("step-1");
        assertFalse(pending.needsTracking());

        // Submitted - needs tracking
        StepStateSnapshot submitted = StepStateSnapshot.submitted("step-1", "tx-123");
        assertTrue(submitted.needsTracking());

        // In block - needs tracking
        StepStateSnapshot inBlock = StepStateSnapshot.builder()
                .stepId("step-1")
                .transactionHash("tx-123")
                .state(TransactionState.IN_BLOCK)
                .build();
        assertTrue(inBlock.needsTracking());

        // Confirmed - doesn't need tracking
        StepStateSnapshot confirmed = StepStateSnapshot.builder()
                .stepId("step-1")
                .transactionHash("tx-123")
                .state(TransactionState.CONFIRMED)
                .build();
        assertFalse(confirmed.needsTracking());

    }

    // ==================== FlowStateSnapshot Tests ====================

    @Test
    void testFlowStateSnapshotBuilder() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .description("Test flow")
                .totalSteps(3)
                .completedSteps(1)
                .build();

        assertEquals("flow-1", snapshot.getFlowId());
        assertEquals(FlowStatus.IN_PROGRESS, snapshot.getStatus());
        assertTrue(snapshot.isInProgress());
        assertEquals("Test flow", snapshot.getDescription());
        assertEquals(3, snapshot.getTotalSteps());
        assertEquals(1, snapshot.getCompletedSteps());
    }

    @Test
    void testFlowStateSnapshotIsInProgress() {
        FlowStateSnapshot inProgress = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        assertTrue(inProgress.isInProgress());

        FlowStateSnapshot pending = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.PENDING)
                .build();
        assertTrue(pending.isInProgress());

        FlowStateSnapshot completed = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.COMPLETED)
                .build();
        assertFalse(completed.isInProgress());

        FlowStateSnapshot failed = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.FAILED)
                .build();
        assertFalse(failed.isInProgress());
    }

    @Test
    void testFlowStateSnapshotHasPendingTransactions() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();

        // No steps - no pending transactions
        assertFalse(snapshot.hasPendingTransactions());

        // Add pending step without tx hash - no pending transactions
        snapshot.addStep(StepStateSnapshot.pending("step-1"));
        assertFalse(snapshot.hasPendingTransactions());

        // Add submitted step - has pending transactions
        snapshot.addStep(StepStateSnapshot.submitted("step-2", "tx-123"));
        assertTrue(snapshot.hasPendingTransactions());
    }

    @Test
    void testFlowStateSnapshotGetStep() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .build();

        snapshot.addStep(StepStateSnapshot.pending("step-1"));
        snapshot.addStep(StepStateSnapshot.submitted("step-2", "tx-123"));

        assertEquals("step-1", snapshot.getStep("step-1").getStepId());
        assertEquals("step-2", snapshot.getStep("step-2").getStepId());
        assertNull(snapshot.getStep("non-existent"));
    }

    @Test
    void testFlowStateSnapshotGetPendingSteps() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .build();

        snapshot.addStep(StepStateSnapshot.pending("step-1"));
        snapshot.addStep(StepStateSnapshot.submitted("step-2", "tx-123"));
        snapshot.addStep(StepStateSnapshot.builder()
                .stepId("step-3")
                .transactionHash("tx-456")
                .state(TransactionState.CONFIRMED)
                .build());

        List<StepStateSnapshot> pending = snapshot.getPendingSteps();
        assertEquals(1, pending.size());
        assertEquals("step-2", pending.get(0).getStepId());
    }

    @Test
    void testFlowStateSnapshotUpdateStep() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .build();

        snapshot.addStep(StepStateSnapshot.pending("step-1"));

        // Update existing step
        StepStateSnapshot updated = StepStateSnapshot.submitted("step-1", "tx-123");
        snapshot.updateStep(updated);

        StepStateSnapshot retrieved = snapshot.getStep("step-1");
        assertEquals("tx-123", retrieved.getTransactionHash());
        assertEquals(TransactionState.SUBMITTED, retrieved.getState());
    }

    // ==================== FlowStateStore Interface Tests ====================

    @Test
    void testSaveAndGetFlowState() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build();

        stateStore.saveFlowState(snapshot);

        Optional<FlowStateSnapshot> retrieved = stateStore.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals("flow-1", retrieved.get().getFlowId());
    }

    @Test
    void testGetFlowStateNotFound() {
        Optional<FlowStateSnapshot> retrieved = stateStore.getFlowState("non-existent");
        assertFalse(retrieved.isPresent());
    }

    @Test
    void testLoadPendingFlows() {
        // Save multiple flows with different statuses
        stateStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build());
        stateStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-2")
                .status(FlowStatus.PENDING)
                .build());
        stateStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-3")
                .status(FlowStatus.COMPLETED)
                .build());
        stateStore.saveFlowState(FlowStateSnapshot.builder()
                .flowId("flow-4")
                .status(FlowStatus.FAILED)
                .build());

        List<FlowStateSnapshot> pending = stateStore.loadPendingFlows();
        assertEquals(2, pending.size());
        assertTrue(pending.stream().anyMatch(f -> f.getFlowId().equals("flow-1")));
        assertTrue(pending.stream().anyMatch(f -> f.getFlowId().equals("flow-2")));
    }

    @Test
    void testUpdateTransactionState() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.addStep(StepStateSnapshot.pending("step-1"));
        stateStore.saveFlowState(snapshot);

        // Update transaction state with details
        TransactionStateDetails details = TransactionStateDetails.submitted(Instant.now());
        stateStore.updateTransactionState("flow-1", "step-1", "tx-123", details);

        Optional<FlowStateSnapshot> retrieved = stateStore.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        StepStateSnapshot step = retrieved.get().getStep("step-1");
        assertEquals("tx-123", step.getTransactionHash());
        assertEquals(TransactionState.SUBMITTED, step.getState());
    }

    @Test
    void testUpdateTransactionStateWithBlockDetails() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        snapshot.addStep(StepStateSnapshot.submitted("step-1", "tx-123"));
        stateStore.saveFlowState(snapshot);

        // Update to confirmed with block details
        TransactionStateDetails details = TransactionStateDetails.confirmed(12345L, 6, Instant.now());
        stateStore.updateTransactionState("flow-1", "step-1", "tx-123", details);

        Optional<FlowStateSnapshot> retrieved = stateStore.getFlowState("flow-1");
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
        stateStore.saveFlowState(snapshot);

        // Update to rolled back with error
        TransactionStateDetails details = TransactionStateDetails.rolledBack(
                12340L, "Block was rolled back", Instant.now());
        stateStore.updateTransactionState("flow-1", "step-1", "tx-123", details);

        Optional<FlowStateSnapshot> retrieved = stateStore.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        StepStateSnapshot step = retrieved.get().getStep("step-1");
        assertEquals(TransactionState.ROLLED_BACK, step.getState());
        assertEquals(12340L, step.getBlockHeight());
        assertEquals("Block was rolled back", step.getErrorMessage());
    }

    @Test
    void testMarkFlowComplete() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        stateStore.saveFlowState(snapshot);

        stateStore.markFlowComplete("flow-1", FlowStatus.COMPLETED);

        Optional<FlowStateSnapshot> retrieved = stateStore.getFlowState("flow-1");
        assertTrue(retrieved.isPresent());
        assertEquals(FlowStatus.COMPLETED, retrieved.get().getStatus());
        assertNotNull(retrieved.get().getCompletedAt());
    }

    @Test
    void testDeleteFlow() {
        FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
                .flowId("flow-1")
                .status(FlowStatus.IN_PROGRESS)
                .build();
        stateStore.saveFlowState(snapshot);

        assertTrue(stateStore.deleteFlow("flow-1"));
        assertFalse(stateStore.getFlowState("flow-1").isPresent());
        assertFalse(stateStore.deleteFlow("flow-1")); // Already deleted
    }

    // ==================== RecoveryCallback Tests ====================

    @Test
    void testRecoveryCallbackContinueAll() {
        FlowStateSnapshot flow = FlowStateSnapshot.builder().flowId("flow-1").build();
        StepStateSnapshot step = StepStateSnapshot.submitted("step-1", "tx-123");

        assertEquals(RecoveryCallback.RecoveryAction.CONTINUE_TRACKING,
                RecoveryCallback.CONTINUE_ALL.onPendingTransaction(flow, step));
    }

    @Test
    void testRecoveryCallbackSkipAll() {
        FlowStateSnapshot flow = FlowStateSnapshot.builder().flowId("flow-1").build();
        StepStateSnapshot step = StepStateSnapshot.submitted("step-1", "tx-123");

        assertEquals(RecoveryCallback.RecoveryAction.SKIP,
                RecoveryCallback.SKIP_ALL.onPendingTransaction(flow, step));
    }

    @Test
    void testRecoveryCallbackFailAll() {
        FlowStateSnapshot flow = FlowStateSnapshot.builder().flowId("flow-1").build();
        StepStateSnapshot step = StepStateSnapshot.submitted("step-1", "tx-123");

        assertEquals(RecoveryCallback.RecoveryAction.FAIL_FLOW,
                RecoveryCallback.FAIL_ALL.onPendingTransaction(flow, step));
    }

    // ==================== NOOP FlowStateStore Tests ====================

    @Test
    void testNoopFlowStateStore() {
        FlowStateStore noop = FlowStateStore.NOOP;

        // Should not throw
        noop.saveFlowState(FlowStateSnapshot.builder().flowId("test").build());
        noop.updateTransactionState("flow-1", "step-1", "tx-123",
                TransactionStateDetails.submitted(Instant.now()));
        noop.markFlowComplete("flow-1", FlowStatus.COMPLETED);

        // Should return empty results
        assertTrue(noop.loadPendingFlows().isEmpty());
        assertFalse(noop.getFlowState("test").isPresent());
        assertFalse(noop.deleteFlow("test"));
    }

    // ==================== TransactionStateDetails Tests ====================

    @Test
    void testTransactionStateDetailsSubmitted() {
        Instant now = Instant.now();
        TransactionStateDetails details = TransactionStateDetails.submitted(now);

        assertEquals(TransactionState.SUBMITTED, details.getState());
        assertEquals(now, details.getTimestamp());
        assertNull(details.getBlockHeight());
        assertNull(details.getConfirmationDepth());
        assertNull(details.getErrorMessage());
    }

    @Test
    void testTransactionStateDetailsInBlock() {
        Instant now = Instant.now();
        TransactionStateDetails details = TransactionStateDetails.inBlock(12345L, 1, now);

        assertEquals(TransactionState.IN_BLOCK, details.getState());
        assertEquals(12345L, details.getBlockHeight());
        assertEquals(1, details.getConfirmationDepth());
        assertEquals(now, details.getTimestamp());
        assertNull(details.getErrorMessage());
    }

    @Test
    void testTransactionStateDetailsConfirmed() {
        Instant now = Instant.now();
        TransactionStateDetails details = TransactionStateDetails.confirmed(12345L, 6, now);

        assertEquals(TransactionState.CONFIRMED, details.getState());
        assertEquals(12345L, details.getBlockHeight());
        assertEquals(6, details.getConfirmationDepth());
        assertEquals(now, details.getTimestamp());
        assertNull(details.getErrorMessage());
    }

    @Test
    void testTransactionStateDetailsRolledBack() {
        Instant now = Instant.now();
        TransactionStateDetails details = TransactionStateDetails.rolledBack(
                12340L, "Chain fork detected", now);

        assertEquals(TransactionState.ROLLED_BACK, details.getState());
        assertEquals(12340L, details.getBlockHeight());
        assertEquals("Chain fork detected", details.getErrorMessage());
        assertEquals(now, details.getTimestamp());
        assertNull(details.getConfirmationDepth());
    }

    // ==================== Mock Implementation ====================

    /**
     * In-memory mock implementation of FlowStateStore for testing.
     */
    private static class MockFlowStateStore implements FlowStateStore {
        private final Map<String, FlowStateSnapshot> store = new ConcurrentHashMap<>();

        @Override
        public void saveFlowState(FlowStateSnapshot snapshot) {
            store.put(snapshot.getFlowId(), deepCopy(snapshot));
        }

        @Override
        public List<FlowStateSnapshot> loadPendingFlows() {
            return store.values().stream()
                    .filter(FlowStateSnapshot::isInProgress)
                    .map(this::deepCopy)
                    .collect(Collectors.toList());
        }

        @Override
        public void updateTransactionState(String flowId, String stepId,
                                           String txHash, TransactionStateDetails details) {
            FlowStateSnapshot snapshot = store.get(flowId);
            if (snapshot != null) {
                StepStateSnapshot step = snapshot.getStep(stepId);
                if (step != null) {
                    step.setTransactionHash(txHash);
                    step.setState(details.getState());
                    step.setBlockHeight(details.getBlockHeight());
                    step.setConfirmationDepth(details.getConfirmationDepth());
                    step.setLastChecked(details.getTimestamp());
                    step.setErrorMessage(details.getErrorMessage());
                    // Set confirmedAt when confirmed
                    if (details.getState() == TransactionState.CONFIRMED) {
                        step.setConfirmedAt(details.getTimestamp());
                    }
                } else {
                    // Add new step
                    StepStateSnapshot.StepStateSnapshotBuilder builder = StepStateSnapshot.builder()
                            .stepId(stepId)
                            .transactionHash(txHash)
                            .state(details.getState())
                            .blockHeight(details.getBlockHeight())
                            .confirmationDepth(details.getConfirmationDepth())
                            .lastChecked(details.getTimestamp())
                            .errorMessage(details.getErrorMessage());
                    if (details.getState() == TransactionState.CONFIRMED) {
                        builder.confirmedAt(details.getTimestamp());
                    }
                    snapshot.addStep(builder.build());
                }
            }
        }

        @Override
        public void markFlowComplete(String flowId, FlowStatus status) {
            FlowStateSnapshot snapshot = store.get(flowId);
            if (snapshot != null) {
                snapshot.setStatus(status);
                snapshot.setCompletedAt(Instant.now());
            }
        }

        @Override
        public Optional<FlowStateSnapshot> getFlowState(String flowId) {
            FlowStateSnapshot snapshot = store.get(flowId);
            return Optional.ofNullable(snapshot != null ? deepCopy(snapshot) : null);
        }

        @Override
        public boolean deleteFlow(String flowId) {
            return store.remove(flowId) != null;
        }

        private FlowStateSnapshot deepCopy(FlowStateSnapshot snapshot) {
            // Simple deep copy for testing
            List<StepStateSnapshot> stepsCopy = new ArrayList<>();
            if (snapshot.getSteps() != null) {
                for (StepStateSnapshot step : snapshot.getSteps()) {
                    stepsCopy.add(StepStateSnapshot.builder()
                            .stepId(step.getStepId())
                            .transactionHash(step.getTransactionHash())
                            .state(step.getState())
                            .submittedAt(step.getSubmittedAt())
                            .blockHeight(step.getBlockHeight())
                            .confirmationDepth(step.getConfirmationDepth())
                            .lastChecked(step.getLastChecked())
                            .confirmedAt(step.getConfirmedAt())
                            .errorMessage(step.getErrorMessage())
                            .build());
                }
            }

            return FlowStateSnapshot.builder()
                    .flowId(snapshot.getFlowId())
                    .status(snapshot.getStatus())
                    .startedAt(snapshot.getStartedAt())
                    .completedAt(snapshot.getCompletedAt())
                    .steps(stepsCopy)
                    .variables(snapshot.getVariables() != null ? new HashMap<>(snapshot.getVariables()) : new HashMap<>())
                    .description(snapshot.getDescription())
                    .totalSteps(snapshot.getTotalSteps())
                    .completedSteps(snapshot.getCompletedSteps())
                    .metadata(snapshot.getMetadata() != null ? new HashMap<>(snapshot.getMetadata()) : new HashMap<>())
                    .build();
        }
    }
}
