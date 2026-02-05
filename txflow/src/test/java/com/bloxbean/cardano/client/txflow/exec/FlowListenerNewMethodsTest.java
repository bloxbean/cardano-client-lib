package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the new FlowListener methods added for confirmation tracking.
 */
class FlowListenerNewMethodsTest {

    private FlowStep createTestStep() {
        return FlowStep.builder("step1")
                .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                .build();
    }

    @Test
    void testOnTransactionInBlock() {
        AtomicInteger callCount = new AtomicInteger(0);
        long[] capturedHeight = {0};
        String[] capturedHash = {null};

        FlowListener listener = new FlowListener() {
            @Override
            public void onTransactionInBlock(FlowStep step, String transactionHash, long blockHeight) {
                callCount.incrementAndGet();
                capturedHash[0] = transactionHash;
                capturedHeight[0] = blockHeight;
            }
        };

        FlowStep step = createTestStep();
        listener.onTransactionInBlock(step, "txHash123", 1000L);

        assertEquals(1, callCount.get());
        assertEquals("txHash123", capturedHash[0]);
        assertEquals(1000L, capturedHeight[0]);
    }

    @Test
    void testOnConfirmationDepthChanged() {
        AtomicInteger callCount = new AtomicInteger(0);
        int[] capturedDepth = {0};
        ConfirmationStatus[] capturedStatus = {null};

        FlowListener listener = new FlowListener() {
            @Override
            public void onConfirmationDepthChanged(FlowStep step, String transactionHash,
                                                   int depth, ConfirmationStatus status) {
                callCount.incrementAndGet();
                capturedDepth[0] = depth;
                capturedStatus[0] = status;
            }
        };

        FlowStep step = createTestStep();
        listener.onConfirmationDepthChanged(step, "txHash123", 15, ConfirmationStatus.CONFIRMED);

        assertEquals(1, callCount.get());
        assertEquals(15, capturedDepth[0]);
        assertEquals(ConfirmationStatus.CONFIRMED, capturedStatus[0]);
    }

    @Test
    void testOnTransactionFinalized() {
        AtomicInteger callCount = new AtomicInteger(0);
        String[] capturedHash = {null};

        FlowListener listener = new FlowListener() {
            @Override
            public void onTransactionFinalized(FlowStep step, String transactionHash) {
                callCount.incrementAndGet();
                capturedHash[0] = transactionHash;
            }
        };

        FlowStep step = createTestStep();
        listener.onTransactionFinalized(step, "txHash123");

        assertEquals(1, callCount.get());
        assertEquals("txHash123", capturedHash[0]);
    }

    @Test
    void testOnTransactionRolledBack() {
        AtomicInteger callCount = new AtomicInteger(0);
        long[] capturedHeight = {0};
        String[] capturedHash = {null};

        FlowListener listener = new FlowListener() {
            @Override
            public void onTransactionRolledBack(FlowStep step, String transactionHash, long previousBlockHeight) {
                callCount.incrementAndGet();
                capturedHash[0] = transactionHash;
                capturedHeight[0] = previousBlockHeight;
            }
        };

        FlowStep step = createTestStep();
        listener.onTransactionRolledBack(step, "txHash123", 950L);

        assertEquals(1, callCount.get());
        assertEquals("txHash123", capturedHash[0]);
        assertEquals(950L, capturedHeight[0]);
    }

    @Test
    void testCompositeListener_OnTransactionInBlock() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onTransactionInBlock(FlowStep s, String txHash, long blockHeight) {
                callCount.incrementAndGet();
                throw new RuntimeException("Test exception");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onTransactionInBlock(FlowStep s, String txHash, long blockHeight) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onTransactionInBlock(step, "txHash123", 1000L));
        assertEquals(2, callCount.get(), "Both listeners should be called");
    }

    @Test
    void testCompositeListener_OnConfirmationDepthChanged() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onConfirmationDepthChanged(FlowStep s, String txHash, int depth, ConfirmationStatus status) {
                callCount.incrementAndGet();
                throw new RuntimeException("Test exception");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onConfirmationDepthChanged(FlowStep s, String txHash, int depth, ConfirmationStatus status) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onConfirmationDepthChanged(step, "txHash123", 15, ConfirmationStatus.CONFIRMED));
        assertEquals(2, callCount.get(), "Both listeners should be called");
    }

    @Test
    void testCompositeListener_OnTransactionFinalized() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onTransactionFinalized(FlowStep s, String txHash) {
                callCount.incrementAndGet();
                throw new RuntimeException("Test exception");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onTransactionFinalized(FlowStep s, String txHash) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onTransactionFinalized(step, "txHash123"));
        assertEquals(2, callCount.get(), "Both listeners should be called");
    }

    @Test
    void testCompositeListener_OnTransactionRolledBack() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onTransactionRolledBack(FlowStep s, String txHash, long previousBlockHeight) {
                callCount.incrementAndGet();
                throw new RuntimeException("Test exception");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onTransactionRolledBack(FlowStep s, String txHash, long previousBlockHeight) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onTransactionRolledBack(step, "txHash123", 950L));
        assertEquals(2, callCount.get(), "Both listeners should be called");
    }

    @Test
    void testDefaultImplementationsAreNoOp() {
        // Default implementations should not throw
        FlowListener noopListener = FlowListener.NOOP;
        FlowStep step = createTestStep();
        TxFlow flow = mock(TxFlow.class);

        assertDoesNotThrow(() -> noopListener.onTransactionInBlock(step, "tx", 100L));
        assertDoesNotThrow(() -> noopListener.onConfirmationDepthChanged(step, "tx", 10, ConfirmationStatus.IN_BLOCK));
        assertDoesNotThrow(() -> noopListener.onTransactionFinalized(step, "tx"));
        assertDoesNotThrow(() -> noopListener.onTransactionRolledBack(step, "tx", 100L));
        assertDoesNotThrow(() -> noopListener.onStepRebuilding(step, 1, 3, "rollback"));
        assertDoesNotThrow(() -> noopListener.onFlowRestarting(flow, 1, 3, "rollback"));
    }

    @Test
    void testOnStepRebuilding() {
        AtomicInteger callCount = new AtomicInteger(0);
        int[] capturedAttempt = {0};
        int[] capturedMax = {0};
        String[] capturedReason = {null};

        FlowListener listener = new FlowListener() {
            @Override
            public void onStepRebuilding(FlowStep step, int attemptNumber, int maxAttempts, String reason) {
                callCount.incrementAndGet();
                capturedAttempt[0] = attemptNumber;
                capturedMax[0] = maxAttempts;
                capturedReason[0] = reason;
            }
        };

        FlowStep step = createTestStep();
        listener.onStepRebuilding(step, 2, 5, "Transaction rolled back");

        assertEquals(1, callCount.get());
        assertEquals(2, capturedAttempt[0]);
        assertEquals(5, capturedMax[0]);
        assertEquals("Transaction rolled back", capturedReason[0]);
    }

    @Test
    void testOnFlowRestarting() {
        AtomicInteger callCount = new AtomicInteger(0);
        int[] capturedAttempt = {0};
        int[] capturedMax = {0};
        String[] capturedReason = {null};

        FlowListener listener = new FlowListener() {
            @Override
            public void onFlowRestarting(TxFlow flow, int attemptNumber, int maxAttempts, String reason) {
                callCount.incrementAndGet();
                capturedAttempt[0] = attemptNumber;
                capturedMax[0] = maxAttempts;
                capturedReason[0] = reason;
            }
        };

        TxFlow flow = mock(TxFlow.class);
        listener.onFlowRestarting(flow, 1, 3, "Rollback detected at step 'deposit'");

        assertEquals(1, callCount.get());
        assertEquals(1, capturedAttempt[0]);
        assertEquals(3, capturedMax[0]);
        assertEquals("Rollback detected at step 'deposit'", capturedReason[0]);
    }

    @Test
    void testCompositeListener_OnStepRebuilding() {
        AtomicInteger callCount = new AtomicInteger(0);
        FlowStep step = createTestStep();

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onStepRebuilding(FlowStep s, int attemptNumber, int maxAttempts, String reason) {
                callCount.incrementAndGet();
                throw new RuntimeException("Test exception");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onStepRebuilding(FlowStep s, int attemptNumber, int maxAttempts, String reason) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onStepRebuilding(step, 1, 3, "rollback"));
        assertEquals(2, callCount.get(), "Both listeners should be called");
    }

    @Test
    void testCompositeListener_OnFlowRestarting() {
        AtomicInteger callCount = new AtomicInteger(0);
        TxFlow flow = mock(TxFlow.class);

        FlowListener throwingListener = new FlowListener() {
            @Override
            public void onFlowRestarting(TxFlow f, int attemptNumber, int maxAttempts, String reason) {
                callCount.incrementAndGet();
                throw new RuntimeException("Test exception");
            }
        };

        FlowListener normalListener = new FlowListener() {
            @Override
            public void onFlowRestarting(TxFlow f, int attemptNumber, int maxAttempts, String reason) {
                callCount.incrementAndGet();
            }
        };

        FlowListener composite = FlowListener.composite(throwingListener, normalListener);

        assertDoesNotThrow(() -> composite.onFlowRestarting(flow, 1, 3, "rollback"));
        assertEquals(2, callCount.get(), "Both listeners should be called");
    }
}
