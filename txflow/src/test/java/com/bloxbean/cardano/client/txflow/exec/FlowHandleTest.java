package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FlowHandleTest {

    @Test
    void testConcurrentIncrement() throws InterruptedException {
        // Create a mock flow with multiple steps
        TxFlow flow = TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step3")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step4")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step5")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        int numThreads = 10;
        int incrementsPerThread = 100;
        int expectedTotal = numThreads * incrementsPerThread;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Start multiple threads that all increment concurrently
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < incrementsPerThread; j++) {
                        handle.incrementCompletedSteps();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads should complete within timeout");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify no increments were lost
        assertEquals(expectedTotal, handle.getCompletedStepCount(),
                "Should have exactly " + expectedTotal + " increments without any lost updates");
    }

    @Test
    void testGetCompletedStepCount() {
        TxFlow flow = TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        assertEquals(0, handle.getCompletedStepCount());

        handle.incrementCompletedSteps();
        assertEquals(1, handle.getCompletedStepCount());

        handle.incrementCompletedSteps();
        assertEquals(2, handle.getCompletedStepCount());
    }

    @Test
    void testStatusUpdate() {
        TxFlow flow = TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        assertEquals(FlowStatus.PENDING, handle.getStatus());

        handle.updateStatus(FlowStatus.IN_PROGRESS);
        assertEquals(FlowStatus.IN_PROGRESS, handle.getStatus());

        handle.updateStatus(FlowStatus.COMPLETED);
        assertEquals(FlowStatus.COMPLETED, handle.getStatus());
    }

    @Test
    void testCurrentStepUpdate() {
        TxFlow flow = TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        assertTrue(handle.getCurrentStepId().isEmpty());

        handle.updateCurrentStep("step1");
        assertEquals("step1", handle.getCurrentStepId().orElse(null));

        handle.updateCurrentStep("step2");
        assertEquals("step2", handle.getCurrentStepId().orElse(null));
    }

    @Test
    void testToString() {
        TxFlow flow = TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        handle.updateStatus(FlowStatus.IN_PROGRESS);
        handle.updateCurrentStep("step1");
        handle.incrementCompletedSteps();

        String str = handle.toString();
        assertTrue(str.contains("test-flow"));
        assertTrue(str.contains("IN_PROGRESS"));
        assertTrue(str.contains("step1"));
        assertTrue(str.contains("1/2")); // progress
    }

    @Test
    void testCancellation() {
        TxFlow flow = TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        // Initially not cancelled
        assertFalse(handle.isCancelled());
        assertEquals(FlowStatus.PENDING, handle.getStatus());

        // Cancel
        boolean result = handle.cancel();

        assertTrue(handle.isCancelled());
        assertEquals(FlowStatus.CANCELLED, handle.getStatus());
        assertTrue(future.isCancelled(), "Underlying future should be cancelled");
    }

    @Test
    void testConcurrentStatusAndIncrement() throws InterruptedException {
        TxFlow flow = TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        CompletableFuture<FlowResult> future = new CompletableFuture<>();
        FlowHandle handle = new FlowHandle(flow, future);

        int numIterations = 1000;
        List<Exception> exceptions = new ArrayList<>();

        Thread incrementThread = new Thread(() -> {
            try {
                for (int i = 0; i < numIterations; i++) {
                    handle.incrementCompletedSteps();
                }
            } catch (Exception e) {
                synchronized (exceptions) {
                    exceptions.add(e);
                }
            }
        });

        Thread statusThread = new Thread(() -> {
            try {
                for (int i = 0; i < numIterations; i++) {
                    handle.updateStatus(i % 2 == 0 ? FlowStatus.IN_PROGRESS : FlowStatus.PENDING);
                    handle.getCompletedStepCount(); // Read while incrementing
                }
            } catch (Exception e) {
                synchronized (exceptions) {
                    exceptions.add(e);
                }
            }
        });

        incrementThread.start();
        statusThread.start();

        incrementThread.join(10000);
        statusThread.join(10000);

        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent access");
        assertEquals(numIterations, handle.getCompletedStepCount());
    }
}
