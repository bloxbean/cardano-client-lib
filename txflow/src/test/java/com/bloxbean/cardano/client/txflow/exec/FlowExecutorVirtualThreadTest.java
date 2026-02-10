package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Virtual thread stress test for FlowExecutor.
 * <p>
 * Validates that FlowExecutor can handle 10,000 concurrent flows using
 * virtual threads without deadlock, thread exhaustion, or OOM.
 * <p>
 * This test is automatically skipped on Java versions below 21.
 * All Java 21+ APIs are accessed via reflection so this class compiles on Java 11.
 */
class FlowExecutorVirtualThreadTest {

    private static final int CONCURRENT_FLOWS = 10_000;

    private static Executor virtualThreadExecutor;

    @Mock
    private UtxoSupplier utxoSupplier;
    @Mock
    private ProtocolParamsSupplier protocolParamsSupplier;
    @Mock
    private TransactionProcessor transactionProcessor;
    @Mock
    private ChainDataSupplier chainDataSupplier;

    @BeforeAll
    static void checkVirtualThreadSupport() {
        // Skip entire test class if virtual threads are not available (Java < 21)
        try {
            java.lang.reflect.Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            virtualThreadExecutor = (Executor) method.invoke(null);
        } catch (Exception e) {
            virtualThreadExecutor = null;
        }
        Assumptions.assumeTrue(virtualThreadExecutor != null,
                "Virtual threads not available (requires Java 21+)");
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testConcurrentFlows_withVirtualThreads_noDeadlockOrOOM() throws Exception {
        // Track completion
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_FLOWS);

        long startTime = System.nanoTime();

        // Launch 10k concurrent flows
        List<FlowHandle> handles = new ArrayList<>(CONCURRENT_FLOWS);
        for (int i = 0; i < CONCURRENT_FLOWS; i++) {
            FlowExecutor executor = FlowExecutor.create(
                    utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier)
                    .withExecutor(virtualThreadExecutor);

            String flowId = "vt-flow-" + i;
            TxFlow flow = TxFlow.builder(flowId)
                    .addStep(FlowStep.builder("step1")
                            .withTxContext(builder -> builder.compose(new com.bloxbean.cardano.client.quicktx.Tx().from("addr1")))
                            .build())
                    .build();

            FlowHandle handle = executor.execute(flow);
            handles.add(handle);

            // Monitor completion asynchronously
            handle.getResultFuture().whenComplete((result, error) -> {
                if (error != null) {
                    failed.incrementAndGet();
                } else {
                    completed.incrementAndGet();
                }
                latch.countDown();
            });
        }

        // Wait for all flows to complete (they should fail fast with mocked backends)
        boolean allDone = latch.await(60, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - startTime;

        // Assertions
        assertTrue(allDone, "All flows should complete within 60s (deadlock detected?)");
        assertEquals(CONCURRENT_FLOWS, completed.get() + failed.get(),
                "All flows should reach terminal state");

        // All flows should fail (mocked backends not configured to succeed) — that's expected.
        // The point is they all complete without deadlock or OOM.
        assertEquals(CONCURRENT_FLOWS, failed.get(),
                "All flows should fail with mocked backends (validates fast error propagation)");

        // Verify all handles have terminal status
        for (FlowHandle handle : handles) {
            FlowStatus status = handle.getStatus();
            assertTrue(status == FlowStatus.FAILED || status == FlowStatus.COMPLETED,
                    "Handle should have terminal status, got: " + status);
        }

        System.out.printf("[VirtualThread Test] %,d flows completed in %.2f seconds (%.0f flows/sec)%n",
                CONCURRENT_FLOWS, elapsed / 1_000_000_000.0, CONCURRENT_FLOWS / (elapsed / 1_000_000_000.0));
    }
}
