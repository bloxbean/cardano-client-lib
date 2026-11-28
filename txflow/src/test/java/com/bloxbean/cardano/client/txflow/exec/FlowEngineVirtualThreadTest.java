package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Virtual thread stress test for {@link FlowEngine}.
 *
 * <p>Validates that {@link FlowEngine} can handle 10,000 concurrent flows using
 * virtual threads without deadlock, thread exhaustion, or OOM.</p>
 *
 * <p>This test is automatically skipped on Java versions below 21.
 * All Java 21+ APIs are accessed via reflection so this class compiles on Java 17.</p>
 */
@Slf4j
class FlowEngineVirtualThreadTest {

    private static final int CONCURRENT_FLOWS = 10_000;
    private static final String TEST_SENDER =
            "addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2"
                    + "k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp";

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
        try {
            Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
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
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_FLOWS);

        FlowEngine engine = FlowEngine.builder(
                        utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier)
                .executor(virtualThreadExecutor)
                .build();

        TxFlow templateFlow = TxFlow.builder("vt-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxPlan(TxPlan.from(new Tx()
                                .from(TEST_SENDER)
                                .payToAddress(TEST_SENDER, Amount.ada(1))))
                        .build())
                .build();

        long startTime = System.nanoTime();

        List<FlowExecutionHandle> handles = new ArrayList<>(CONCURRENT_FLOWS);
        for (int i = 0; i < CONCURRENT_FLOWS; i++) {
            FlowExecutionRequest request = FlowExecutionRequest.builder(templateFlow)
                    .executionId("vt-exec-" + i)
                    .build();

            FlowExecutionHandle handle = engine.start(request);
            handles.add(handle);

            handle.completion().whenComplete((result, error) -> {
                if (error != null || (result != null && !result.isSuccessful())) {
                    failed.incrementAndGet();
                } else {
                    completed.incrementAndGet();
                }
                latch.countDown();
            });
        }

        boolean allDone = latch.await(60, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - startTime;

        assertTrue(allDone, "All flows should complete within 60s (deadlock detected?)");
        assertEquals(CONCURRENT_FLOWS, completed.get() + failed.get(),
                "All flows should reach terminal state");
        assertEquals(CONCURRENT_FLOWS, failed.get(),
                "All flows should fail with mocked backends (validates fast error propagation)");

        for (FlowExecutionHandle handle : handles) {
            assertTrue(handle.isDone(), "Handle should be done");
            FlowExecutionState state = handle.await().state();
            assertTrue(state == FlowExecutionState.FAILED || state == FlowExecutionState.COMPLETED,
                    "Handle should have terminal state, got: " + state);
        }

        double seconds = elapsed / 1_000_000_000.0;
        log.info("[VirtualThread Test] {} flows completed in {}s ({} flows/sec)",
                CONCURRENT_FLOWS, String.format("%.2f", seconds),
                String.format("%.0f", CONCURRENT_FLOWS / seconds));
    }
}
