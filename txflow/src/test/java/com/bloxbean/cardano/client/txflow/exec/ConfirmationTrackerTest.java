package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmationTrackerTest {

    @Mock
    private ChainDataSupplier chainDataSupplier;

    private ConfirmationTracker tracker;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCheckStatus_Submitted() throws Exception {
        // Setup: Transaction not found in any block
        tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());

        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123")).thenReturn(Optional.empty());

        ConfirmationResult result = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.SUBMITTED, result.getStatus());
        assertEquals(-1, result.getConfirmationDepth());
        assertEquals(1000L, result.getCurrentTipHeight());
        assertNull(result.getBlockHeight());
    }

    @Test
    void testCheckStatus_InBlock() throws Exception {
        // Setup: Transaction in block with shallow depth (less than minConfirmations)
        tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());

        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(995L)  // 5 confirmations (less than default 10)
                        .blockHash("blockHash123")
                        .build()));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.IN_BLOCK, result.getStatus());
        assertEquals(5, result.getConfirmationDepth());
        assertEquals(995L, result.getBlockHeight());
        assertEquals("blockHash123", result.getBlockHash());
    }

    @Test
    void testCheckStatus_Confirmed() throws Exception {
        // Setup: Transaction with depth >= minConfirmations but < safeConfirmations
        tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());

        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(980L)  // 20 confirmations (>= 10, < 2160)
                        .blockHash("blockHash123")
                        .build()));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
        assertEquals(20, result.getConfirmationDepth());
    }

    @Test
    void testCheckStatus_Finalized() throws Exception {
        // Setup: Transaction with depth >= safeConfirmations
        tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());

        when(chainDataSupplier.getChainTipHeight()).thenReturn(5000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(2500L)  // 2500 confirmations (>= 2160)
                        .blockHash("blockHash123")
                        .build()));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.FINALIZED, result.getStatus());
        assertEquals(2500, result.getConfirmationDepth());
    }

    @Test
    void testCheckStatus_RollbackDetected() throws Exception {
        // Setup: Transaction was previously seen in a block but now not found
        tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());

        // First check: transaction is in block
        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(995L)
                        .blockHash("blockHash123")
                        .build()));

        ConfirmationResult firstResult = tracker.checkStatus("txHash123");
        assertEquals(ConfirmationStatus.IN_BLOCK, firstResult.getStatus());

        // Second check: transaction is now missing (simulating rollback)
        when(chainDataSupplier.getTransactionInfo("txHash123")).thenReturn(Optional.empty());

        ConfirmationResult secondResult = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.ROLLED_BACK, secondResult.getStatus());
        assertTrue(secondResult.isRolledBack());
        assertNotNull(secondResult.getError());
    }

    @Test
    void testCheckStatus_WithCustomConfig() throws Exception {
        // Setup with custom config where minConfirmations = 3
        ConfirmationConfig customConfig = ConfirmationConfig.builder()
                .minConfirmations(3)
                .safeConfirmations(50)
                .build();
        tracker = new ConfirmationTracker(chainDataSupplier, customConfig);

        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(996L)  // 4 confirmations (>= 3)
                        .blockHash("blockHash123")
                        .build()));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        // With custom config (minConfirmations=3), 4 confirmations should be CONFIRMED
        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void testStopTracking() throws Exception {
        tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());

        // Track a transaction
        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(995L)
                        .blockHash("blockHash123")
                        .build()));

        tracker.checkStatus("txHash123");
        assertEquals(1, tracker.getTrackedCount());

        // Stop tracking
        tracker.stopTracking("txHash123");
        assertEquals(0, tracker.getTrackedCount());
    }

    @Test
    void testClearTracking() throws Exception {
        tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());

        // Track multiple transactions
        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo(anyString()))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .blockHeight(995L)
                        .blockHash("blockHash123")
                        .build()));

        tracker.checkStatus("tx1");
        tracker.checkStatus("tx2");
        assertEquals(2, tracker.getTrackedCount());

        // Clear all
        tracker.clearTracking();
        assertEquals(0, tracker.getTrackedCount());
    }

    @Test
    void testGetConfig() {
        ConfirmationConfig config = ConfirmationConfig.testnet();
        tracker = new ConfirmationTracker(chainDataSupplier, config);

        assertSame(config, tracker.getConfig());
    }

    @Test
    void testDefaultConfigUsedWhenNull() {
        tracker = new ConfirmationTracker(chainDataSupplier, null);

        // Should use default config
        assertEquals(10, tracker.getConfig().getMinConfirmations());
        assertEquals(2160, tracker.getConfig().getSafeConfirmations());
    }

    @Test
    void testWaitForConfirmation_ImmediateSuccess() throws Exception {
        // Setup: Transaction already confirmed
        ConfirmationConfig quickConfig = ConfirmationConfig.builder()
                .minConfirmations(3)
                .checkInterval(Duration.ofMillis(10))
                .timeout(Duration.ofSeconds(5))
                .build();
        tracker = new ConfirmationTracker(chainDataSupplier, quickConfig);

        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(990L)  // 10 confirmations (>= 3)
                        .blockHash("blockHash123")
                        .build()));

        ConfirmationResult result = tracker.waitForConfirmation("txHash123", ConfirmationStatus.CONFIRMED);

        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
        assertTrue(result.hasReached(ConfirmationStatus.CONFIRMED));
    }

    @Test
    void testWaitForConfirmation_WithProgressCallback() throws Exception {
        // Setup
        ConfirmationConfig quickConfig = ConfirmationConfig.builder()
                .minConfirmations(3)
                .checkInterval(Duration.ofMillis(10))
                .timeout(Duration.ofSeconds(5))
                .build();
        tracker = new ConfirmationTracker(chainDataSupplier, quickConfig);

        when(chainDataSupplier.getChainTipHeight()).thenReturn(1000L);
        when(chainDataSupplier.getTransactionInfo("txHash123"))
                .thenReturn(Optional.of(TransactionInfo.builder()
                        .txHash("txHash123")
                        .blockHeight(990L)
                        .blockHash("blockHash123")
                        .build()));

        AtomicInteger callbackCount = new AtomicInteger(0);
        ConfirmationResult result = tracker.waitForConfirmation("txHash123", ConfirmationStatus.CONFIRMED,
                (hash, res) -> callbackCount.incrementAndGet());

        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
        assertTrue(callbackCount.get() >= 1, "Callback should be invoked at least once");
    }
}
