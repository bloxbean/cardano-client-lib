package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.config.FlowExecutionPolicy;
import com.bloxbean.cardano.client.txflow.config.SpendingContentionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpendingResourceCoordinatorTest {
    @Test
    void rejectPolicySerializesOverlappingCanonicalResources() {
        SpendingResourceCoordinator coordinator = new SpendingResourceCoordinator(FlowScheduler.system());
        FlowExecutionPolicy policy = FlowExecutionPolicy.builder()
                .spendingContention(SpendingContentionPolicy.REJECT).build();
        try (SpendingResourceCoordinator.Acquisition first = coordinator.acquire(
                Set.of("address://treasury"), policy, false, new AtomicBoolean())) {
            SpendingResourceBusyException busy = CompletableFuture.supplyAsync(() -> {
                try (SpendingResourceCoordinator.Acquisition ignored = coordinator.acquire(
                        Set.of("address://treasury"), policy, false, new AtomicBoolean())) {
                    return null;
                } catch (SpendingResourceBusyException failure) {
                    return failure;
                }
            }).join();
            assertTrue(busy != null);
            assertTrue(busy.getMessage().contains("address://treasury"));
        }
        try (SpendingResourceCoordinator.Acquisition next = coordinator.acquire(
                Set.of("address://treasury"), policy, false, new AtomicBoolean())) {
            assertEquals(1, next.identities().size());
        }
        assertEquals(0, coordinator.retainedLockCount());
    }

    @Test
    void cancellationStopsBoundedQueueWithoutLeakingEarlierLocks() {
        SpendingResourceCoordinator coordinator = new SpendingResourceCoordinator(FlowScheduler.system());
        FlowExecutionPolicy policy = FlowExecutionPolicy.builder()
                .maxQueueWait(Duration.ofSeconds(1)).build();
        AtomicBoolean cancelled = new AtomicBoolean(true);
        try (SpendingResourceCoordinator.Acquisition acquisition = coordinator.acquire(
                Set.of("a", "b"), policy, false, cancelled)) {
            assertTrue(acquisition.cancelled());
        }
        assertEquals(0, coordinator.retainedLockCount());
    }

    @Test
    void uniqueResourceLocksAreReclaimedAfterUse() {
        SpendingResourceCoordinator coordinator = new SpendingResourceCoordinator(FlowScheduler.system());
        FlowExecutionPolicy policy = FlowExecutionPolicy.builder()
                .spendingContention(SpendingContentionPolicy.REJECT).build();

        for (int index = 0; index < 1_000; index++) {
            try (SpendingResourceCoordinator.Acquisition ignored = coordinator.acquire(
                    Set.of("resource-" + index), policy, false, new AtomicBoolean())) {
                assertEquals(1, coordinator.retainedLockCount());
            }
        }

        assertEquals(0, coordinator.retainedLockCount());
    }
}
