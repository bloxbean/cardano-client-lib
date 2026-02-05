package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfirmationTrackerTest {

    @Mock
    private BackendService backendService;

    @Mock
    private BlockService blockService;

    @Mock
    private TransactionService transactionService;

    private ConfirmationTracker tracker;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(backendService.getBlockService()).thenReturn(blockService);
        when(backendService.getTransactionService()).thenReturn(transactionService);
    }

    @Test
    void testCheckStatus_Submitted() throws Exception {
        // Setup: Transaction not found in any block
        tracker = new ConfirmationTracker(backendService, ConfirmationConfig.defaults());

        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(null));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.SUBMITTED, result.getStatus());
        assertEquals(-1, result.getConfirmationDepth());
        assertEquals(1000L, result.getCurrentTipHeight());
        assertNull(result.getBlockHeight());
    }

    @Test
    void testCheckStatus_InBlock() throws Exception {
        // Setup: Transaction in block with shallow depth (less than minConfirmations)
        tracker = new ConfirmationTracker(backendService, ConfirmationConfig.defaults());

        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(995L)  // 5 confirmations (less than default 10)
                                .block("blockHash123")
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
        tracker = new ConfirmationTracker(backendService, ConfirmationConfig.defaults());

        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(980L)  // 20 confirmations (>= 10, < 2160)
                                .block("blockHash123")
                                .build()));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
        assertEquals(20, result.getConfirmationDepth());
    }

    @Test
    void testCheckStatus_Finalized() throws Exception {
        // Setup: Transaction with depth >= safeConfirmations
        tracker = new ConfirmationTracker(backendService, ConfirmationConfig.defaults());

        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(5000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(2500L)  // 2500 confirmations (>= 2160)
                                .block("blockHash123")
                                .build()));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        assertEquals(ConfirmationStatus.FINALIZED, result.getStatus());
        assertEquals(2500, result.getConfirmationDepth());
    }

    @Test
    void testCheckStatus_RollbackDetected() throws Exception {
        // Setup: Transaction was previously seen in a block but now not found
        tracker = new ConfirmationTracker(backendService, ConfirmationConfig.defaults());

        // First check: transaction is in block
        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(995L)
                                .block("blockHash123")
                                .build()));

        ConfirmationResult firstResult = tracker.checkStatus("txHash123");
        assertEquals(ConfirmationStatus.IN_BLOCK, firstResult.getStatus());

        // Second check: transaction is now missing (simulating rollback)
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(null));

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
        tracker = new ConfirmationTracker(backendService, customConfig);

        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(996L)  // 4 confirmations (>= 3)
                                .block("blockHash123")
                                .build()));

        ConfirmationResult result = tracker.checkStatus("txHash123");

        // With custom config (minConfirmations=3), 4 confirmations should be CONFIRMED
        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void testStopTracking() throws Exception {
        tracker = new ConfirmationTracker(backendService, ConfirmationConfig.defaults());

        // Track a transaction
        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(995L)
                                .block("blockHash123")
                                .build()));

        tracker.checkStatus("txHash123");
        assertEquals(1, tracker.getTrackedCount());

        // Stop tracking
        tracker.stopTracking("txHash123");
        assertEquals(0, tracker.getTrackedCount());
    }

    @Test
    void testClearTracking() throws Exception {
        tracker = new ConfirmationTracker(backendService, ConfirmationConfig.defaults());

        // Track multiple transactions
        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction(anyString()))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .blockHeight(995L)
                                .block("blockHash123")
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
        tracker = new ConfirmationTracker(backendService, config);

        assertSame(config, tracker.getConfig());
    }

    @Test
    void testDefaultConfigUsedWhenNull() {
        tracker = new ConfirmationTracker(backendService, null);

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
        tracker = new ConfirmationTracker(backendService, quickConfig);

        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(990L)  // 10 confirmations (>= 3)
                                .block("blockHash123")
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
        tracker = new ConfirmationTracker(backendService, quickConfig);

        when(blockService.getLatestBlock())
                .thenReturn(Result.success("ok").withValue(Block.builder().height(1000L).build()));
        when(transactionService.getTransaction("txHash123"))
                .thenReturn(Result.success("ok").withValue(
                        TransactionContent.builder()
                                .hash("txHash123")
                                .blockHeight(990L)
                                .block("blockHash123")
                                .build()));

        AtomicInteger callbackCount = new AtomicInteger(0);
        ConfirmationResult result = tracker.waitForConfirmation("txHash123", ConfirmationStatus.CONFIRMED,
                (hash, res) -> callbackCount.incrementAndGet());

        assertEquals(ConfirmationStatus.CONFIRMED, result.getStatus());
        assertTrue(callbackCount.get() >= 1, "Callback should be invoked at least once");
    }
}
