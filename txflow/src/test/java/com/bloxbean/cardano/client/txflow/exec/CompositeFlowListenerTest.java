package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CompositeFlowListenerTest {

    private TxFlow createTestFlow() {
        return TxFlow.builder("test-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();
    }

    private FlowStep createTestStep() {
        return FlowStep.builder("step1")
                .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                .build();
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onFlowStarted() {
        AtomicInteger callCount = new AtomicInteger(0);
        TxFlow flow = createTestFlow();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow f) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow f) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        // Should not throw, and both listeners should be called
        assertDoesNotThrow(() -> composite.onFlowStarted(flow));
        assertEquals(2, callCount.get(), "Both listeners should be called");
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onFlowCompleted() {
        AtomicInteger callCount = new AtomicInteger(0);
        TxFlow flow = createTestFlow();
        FlowResult result = FlowResult.builder("test-flow")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .success();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onFlowCompleted(TxFlow f, FlowResult r) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onFlowCompleted(TxFlow f, FlowResult r) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onFlowCompleted(flow, result));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onFlowFailed() {
        AtomicInteger callCount = new AtomicInteger(0);
        TxFlow flow = createTestFlow();
        FlowResult result = FlowResult.builder("test-flow")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .failure(new RuntimeException("test error"));

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onFlowFailed(TxFlow f, FlowResult r) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onFlowFailed(TxFlow f, FlowResult r) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onFlowFailed(flow, result));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onStepStarted() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onStepStarted(FlowStep s, int stepIndex, int totalSteps) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onStepStarted(FlowStep s, int stepIndex, int totalSteps) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onStepStarted(step, 0, 3));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onStepCompleted() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();
        FlowStepResult stepResult = FlowStepResult.success("step1", "txHash123", List.of(), List.of());

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onStepCompleted(FlowStep s, FlowStepResult r) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onStepCompleted(FlowStep s, FlowStepResult r) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onStepCompleted(step, stepResult));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onStepFailed() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();
        FlowStepResult stepResult = FlowStepResult.failure("step1", new RuntimeException("test error"));

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onStepFailed(FlowStep s, FlowStepResult r) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onStepFailed(FlowStep s, FlowStepResult r) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onStepFailed(step, stepResult));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onTransactionSubmitted() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onTransactionSubmitted(FlowStep s, String txHash) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onTransactionSubmitted(FlowStep s, String txHash) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onTransactionSubmitted(step, "txHash123"));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onTransactionConfirmed() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onTransactionConfirmed(FlowStep s, String txHash) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onTransactionConfirmed(FlowStep s, String txHash) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onTransactionConfirmed(step, "txHash123"));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onStepRetry() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onStepRetry(FlowStep s, int attemptNumber, int maxAttempts, Throwable lastError) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onStepRetry(FlowStep s, int attemptNumber, int maxAttempts, Throwable lastError) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onStepRetry(step, 1, 3, new RuntimeException("test")));
        assertEquals(2, callCount.get());
    }

    @Test
    void testThrowingListenerDoesNotStopOthers_onStepRetryExhausted() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onStepRetryExhausted(FlowStep s, int totalAttempts, Throwable lastError) {
                callCount.incrementAndGet();
                throw new RuntimeException("Listener error");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onStepRetryExhausted(FlowStep s, int totalAttempts, Throwable lastError) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onStepRetryExhausted(step, 3, new RuntimeException("test")));
        assertEquals(2, callCount.get());
    }

    @Test
    void testMultipleThrowingListenersAllCalled() {
        List<String> calledListeners = new ArrayList<>();
        TxFlow flow = createTestFlow();

        FlowListener listener1 = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow f) {
                calledListeners.add("listener1");
                throw new RuntimeException("Error from listener1");
            }
        };

        FlowListener listener2 = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow f) {
                calledListeners.add("listener2");
                throw new RuntimeException("Error from listener2");
            }
        };

        FlowListener listener3 = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow f) {
                calledListeners.add("listener3");
                throw new RuntimeException("Error from listener3");
            }
        };

        FlowListener composite = FlowListener.composite(listener1, listener2, listener3);

        assertDoesNotThrow(() -> composite.onFlowStarted(flow));

        // All three listeners should have been called despite exceptions
        assertEquals(3, calledListeners.size());
        assertTrue(calledListeners.contains("listener1"));
        assertTrue(calledListeners.contains("listener2"));
        assertTrue(calledListeners.contains("listener3"));
    }

    @Test
    void testCompositeWithSingleListener() {
        AtomicInteger callCount = new AtomicInteger(0);
        TxFlow flow = createTestFlow();

        FlowListener singleListener = new FlowListener() {
            @Override
            public void onFlowStarted(TxFlow f) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(singleListener);

        composite.onFlowStarted(flow);
        assertEquals(1, callCount.get());
    }

    @Test
    void testCompositeWithNoListeners() {
        TxFlow flow = createTestFlow();

        FlowListener composite = FlowListener.composite();

        // Should not throw and should behave like NOOP
        assertDoesNotThrow(() -> composite.onFlowStarted(flow));
    }

    @Test
    void testCompositeWithNullListeners() {
        FlowListener composite = FlowListener.composite((FlowListener[]) null);

        // Should return NOOP listener
        assertSame(FlowListener.NOOP, composite);
    }
}
