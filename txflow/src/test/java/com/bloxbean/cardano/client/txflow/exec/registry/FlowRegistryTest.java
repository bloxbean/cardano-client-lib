package com.bloxbean.cardano.client.txflow.exec.registry;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.exec.FlowHandle;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FlowRegistryTest {

    private InMemoryFlowRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryFlowRegistry();
    }

    @Test
    void testRegisterAndGetFlow() {
        TxFlow flow = createTestFlow("test-1");
        FlowHandle handle = createHandle(flow);

        registry.register("test-1", handle);

        Optional<FlowHandle> retrieved = registry.getFlow("test-1");
        assertTrue(retrieved.isPresent());
        assertEquals("test-1", retrieved.get().getFlow().getId());
    }

    @Test
    void testRegisterReplacesPrevious() {
        TxFlow flow1 = createTestFlow("test-1");
        TxFlow flow2 = createTestFlow("test-1");
        FlowHandle handle1 = createHandle(flow1);
        FlowHandle handle2 = createHandle(flow2);

        registry.register("test-1", handle1);
        assertEquals(1, registry.size());

        registry.register("test-1", handle2);
        assertEquals(1, registry.size());

        Optional<FlowHandle> retrieved = registry.getFlow("test-1");
        assertTrue(retrieved.isPresent());
        assertSame(handle2, retrieved.get());
    }

    @Test
    void testUnregister() {
        TxFlow flow = createTestFlow("test-1");
        FlowHandle handle = createHandle(flow);

        registry.register("test-1", handle);
        assertEquals(1, registry.size());

        Optional<FlowHandle> removed = registry.unregister("test-1");
        assertTrue(removed.isPresent());
        assertSame(handle, removed.get());
        assertEquals(0, registry.size());
    }

    @Test
    void testUnregisterNonExistent() {
        Optional<FlowHandle> removed = registry.unregister("non-existent");
        assertFalse(removed.isPresent());
    }

    @Test
    void testGetAllFlows() {
        registry.register("test-1", createHandle(createTestFlow("test-1")));
        registry.register("test-2", createHandle(createTestFlow("test-2")));
        registry.register("test-3", createHandle(createTestFlow("test-3")));

        Collection<FlowHandle> all = registry.getAllFlows();
        assertEquals(3, all.size());
    }

    @Test
    void testGetActiveFlows() {
        // Create handles with different statuses
        FlowHandle active1 = createHandleWithStatus(createTestFlow("active-1"), FlowStatus.IN_PROGRESS);
        FlowHandle active2 = createHandleWithStatus(createTestFlow("active-2"), FlowStatus.IN_PROGRESS);
        FlowHandle pending = createHandleWithStatus(createTestFlow("pending"), FlowStatus.PENDING);
        FlowHandle completed = createCompletedHandle(createTestFlow("completed"));

        registry.register("active-1", active1);
        registry.register("active-2", active2);
        registry.register("pending", pending);
        registry.register("completed", completed);

        Collection<FlowHandle> active = registry.getActiveFlows();
        assertEquals(2, active.size());
    }

    @Test
    void testGetFlowsByStatus() {
        FlowHandle inProgress = createHandleWithStatus(createTestFlow("in-progress"), FlowStatus.IN_PROGRESS);
        FlowHandle pending1 = createHandleWithStatus(createTestFlow("pending-1"), FlowStatus.PENDING);
        FlowHandle pending2 = createHandleWithStatus(createTestFlow("pending-2"), FlowStatus.PENDING);

        registry.register("in-progress", inProgress);
        registry.register("pending-1", pending1);
        registry.register("pending-2", pending2);

        Collection<FlowHandle> pendingFlows = registry.getFlowsByStatus(FlowStatus.PENDING);
        assertEquals(2, pendingFlows.size());

        Collection<FlowHandle> inProgressFlows = registry.getFlowsByStatus(FlowStatus.IN_PROGRESS);
        assertEquals(1, inProgressFlows.size());
    }

    @Test
    void testContains() {
        registry.register("test-1", createHandle(createTestFlow("test-1")));

        assertTrue(registry.contains("test-1"));
        assertFalse(registry.contains("test-2"));
    }

    @Test
    void testClear() {
        registry.register("test-1", createHandle(createTestFlow("test-1")));
        registry.register("test-2", createHandle(createTestFlow("test-2")));
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
    }

    @Test
    void testActiveCount() {
        FlowHandle active1 = createHandleWithStatus(createTestFlow("active-1"), FlowStatus.IN_PROGRESS);
        FlowHandle active2 = createHandleWithStatus(createTestFlow("active-2"), FlowStatus.IN_PROGRESS);
        FlowHandle pending = createHandleWithStatus(createTestFlow("pending"), FlowStatus.PENDING);

        registry.register("active-1", active1);
        registry.register("active-2", active2);
        registry.register("pending", pending);

        assertEquals(3, registry.size());
        assertEquals(2, registry.activeCount());
    }

    @Test
    void testLifecycleListenerOnRegister() {
        AtomicInteger registerCount = new AtomicInteger(0);
        AtomicInteger unregisterCount = new AtomicInteger(0);

        registry.addLifecycleListener(new FlowLifecycleListener() {
            @Override
            public void onFlowRegistered(String flowId, FlowHandle handle) {
                registerCount.incrementAndGet();
            }

            @Override
            public void onFlowUnregistered(String flowId, FlowHandle handle) {
                unregisterCount.incrementAndGet();
            }
        });

        registry.register("test-1", createHandle(createTestFlow("test-1")));
        registry.register("test-2", createHandle(createTestFlow("test-2")));
        registry.unregister("test-1");

        assertEquals(2, registerCount.get());
        assertEquals(1, unregisterCount.get());
    }

    @Test
    void testLifecycleListenerOnCompletion() throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicInteger completionCount = new AtomicInteger(0);

        registry.addLifecycleListener(new FlowLifecycleListener() {
            @Override
            public void onFlowCompleted(String flowId, FlowHandle handle, FlowResult result) {
                completionCount.incrementAndGet();
                completionLatch.countDown();
            }
        });

        TxFlow flow = createTestFlow("test-1");
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        registry.register("test-1", handle);

        // Complete the flow
        FlowResult result = FlowResult.builder("test-1")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .success();
        future.complete(result);

        // Wait for callback
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, completionCount.get());
    }

    @Test
    void testRemoveLifecycleListener() {
        AtomicInteger registerCount = new AtomicInteger(0);

        FlowLifecycleListener listener = new FlowLifecycleListener() {
            @Override
            public void onFlowRegistered(String flowId, FlowHandle handle) {
                registerCount.incrementAndGet();
            }
        };

        registry.addLifecycleListener(listener);
        registry.register("test-1", createHandle(createTestFlow("test-1")));
        assertEquals(1, registerCount.get());

        registry.removeLifecycleListener(listener);
        registry.register("test-2", createHandle(createTestFlow("test-2")));
        assertEquals(1, registerCount.get()); // Should not increment
    }

    @Test
    void testConcurrentRegistration() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int flowCount = 100;
        CountDownLatch latch = new CountDownLatch(flowCount);

        for (int i = 0; i < flowCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    registry.register("flow-" + index, createHandle(createTestFlow("flow-" + index)));
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(flowCount, registry.size());

        executor.shutdown();
    }

    @Test
    void testConcurrentLookup() throws Exception {
        // Pre-populate registry
        for (int i = 0; i < 100; i++) {
            registry.register("flow-" + i, createHandle(createTestFlow("flow-" + i)));
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int lookupCount = 1000;
        CountDownLatch latch = new CountDownLatch(lookupCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < lookupCount; i++) {
            int index = i % 100;
            executor.submit(() -> {
                try {
                    Optional<FlowHandle> handle = registry.getFlow("flow-" + index);
                    if (handle.isPresent()) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(lookupCount, successCount.get());

        executor.shutdown();
    }

    @Test
    void testListenerExceptionDoesNotAffectOthers() {
        AtomicInteger successCount = new AtomicInteger(0);

        // First listener throws exception
        registry.addLifecycleListener(new FlowLifecycleListener() {
            @Override
            public void onFlowRegistered(String flowId, FlowHandle handle) {
                throw new RuntimeException("Test exception");
            }
        });

        // Second listener should still be called
        registry.addLifecycleListener(new FlowLifecycleListener() {
            @Override
            public void onFlowRegistered(String flowId, FlowHandle handle) {
                successCount.incrementAndGet();
            }
        });

        registry.register("test-1", createHandle(createTestFlow("test-1")));
        assertEquals(1, successCount.get()); // Second listener was still called
    }

    @Test
    void testBuilderWithAutoCleanup() throws Exception {
        InMemoryFlowRegistry cleanupRegistry = InMemoryFlowRegistry.builder()
                .withAutoCleanup(Duration.ofMillis(100))
                .build();

        TxFlow flow = createTestFlow("test-1");
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        cleanupRegistry.register("test-1", handle);
        assertEquals(1, cleanupRegistry.size());

        // Complete the flow
        FlowResult result = FlowResult.builder("test-1")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .success();
        future.complete(result);

        // Wait for auto-cleanup (100ms + buffer)
        Thread.sleep(300);

        // Flow should be auto-cleaned
        assertEquals(0, cleanupRegistry.size());

        cleanupRegistry.shutdown();
    }

    @Test
    void testNullFlowIdThrowsException() {
        FlowHandle handle = createHandle(createTestFlow("test"));
        assertThrows(NullPointerException.class, () -> registry.register(null, handle));
    }

    @Test
    void testNullHandleThrowsException() {
        assertThrows(NullPointerException.class, () -> registry.register("test", null));
    }

    // Helper methods

    private TxFlow createTestFlow(String id) {
        return TxFlow.builder(id)
                .withDescription("Test flow")
                .addStep(FlowStep.builder("step1")
                        .withDescription("Step 1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr_test")))
                        .build())
                .build();
    }

    private FlowHandle createHandle(TxFlow flow) {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        return new FlowHandle(flow, future);
    }

    /**
     * Creates a handle with simulated status based on whether the future is completed.
     * Note: FlowHandle.updateStatus is package-private, so we simulate status via future state.
     */
    private FlowHandle createHandleWithStatus(TxFlow flow, FlowStatus status) {
        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new TestableFlowHandle(flow, future, status);
        return handle;
    }

    private FlowHandle createCompletedHandle(TxFlow flow) {
        FlowResult result = FlowResult.builder(flow.getId())
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .success();
        CompletableFuture<FlowResult> future = CompletableFuture.completedFuture(result);
        return new TestableFlowHandle(flow, future, FlowStatus.COMPLETED);
    }

    /**
     * Test helper class that overrides getStatus() to return a fixed status.
     */
    private static class TestableFlowHandle extends FlowHandle {
        private final FlowStatus fixedStatus;

        TestableFlowHandle(TxFlow flow, CompletableFuture<FlowResult> future, FlowStatus status) {
            super(flow, future);
            this.fixedStatus = status;
        }

        @Override
        public FlowStatus getStatus() {
            return fixedStatus;
        }
    }
}
