package com.bloxbean.cardano.client.watcher.visualizer;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.BasicWatchHandle;
import com.bloxbean.cardano.client.watcher.chain.StepResult;
import com.bloxbean.cardano.client.watcher.chain.Watcher;
import com.bloxbean.cardano.client.watcher.quicktx.StepOutputDependency;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableQuickTxBuilder;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableStep;
import com.bloxbean.cardano.client.watcher.quicktx.UtxoSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChainVisualizerTest {

    @Mock
    private WatchableQuickTxBuilder.WatchableTxContext mockTxContext1;

    @Mock
    private WatchableQuickTxBuilder.WatchableTxContext mockTxContext2;

    @Mock
    private BasicWatchHandle mockHandle;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Reset terminal capabilities cache for testing
        TerminalCapabilities.resetCache();
    }

    @Test
    void testVisualizeStructure_SimpleChain() {
        // Setup mock steps
        WatchableStep step1 = createMockStep("deposit", "Send 100 ADA to pool", Collections.emptyList());
        WatchableStep step2 = createMockStep("stake", "Stake tokens from step 1",
            List.of(new StepOutputDependency("deposit", UtxoSelectionStrategy.ALL)));

        // Create builder
        Watcher.WatcherBuilder builder = createMockBuilder("test-chain", List.of(step1, step2));

        // Test visualization
        String result = ChainVisualizer.visualizeStructure(builder, VisualizationStyle.SIMPLE_ASCII);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Should contain chain info
        assertTrue(result.contains("Chain: test-chain"));
        assertTrue(result.contains("Steps: 2"));

        // Should contain step names
        assertTrue(result.contains("deposit"));
        assertTrue(result.contains("stake"));

        // Should contain dependency info
        assertTrue(result.contains("Dependencies: 1"));
    }

    @Test
    void testVisualizeStructure_CompactStyle() {
        WatchableStep step1 = createMockStep("step1", "First step", Collections.emptyList());
        WatchableStep step2 = createMockStep("step2", "Second step",
            List.of(new StepOutputDependency("step1", UtxoSelectionStrategy.ALL)));

        Watcher.WatcherBuilder builder = createMockBuilder("compact-test", List.of(step1, step2));

        String result = ChainVisualizer.visualizeStructure(builder, VisualizationStyle.COMPACT);

        assertNotNull(result);
        // Compact style should be much shorter
        assertTrue(result.split("\n").length < 10);

        // Should show dependency markers
        assertTrue(result.contains("step2*")); // * indicates dependency
    }

    @Test
    void testVisualizeProgress_WithMockHandle() {
        // Setup mock handle
        when(mockHandle.getChainId()).thenReturn("test-chain");
        when(mockHandle.getStartedAt()).thenReturn(Instant.now().minusSeconds(5));
        when(mockHandle.getProgress()).thenReturn(0.6); // 60% complete

        // Mock step statuses
        Map<String, WatchStatus> stepStatuses = Map.of(
            "step1", WatchStatus.CONFIRMED,
            "step2", WatchStatus.WATCHING,
            "step3", WatchStatus.PENDING
        );
        when(mockHandle.getStepStatuses()).thenReturn(stepStatuses);

        // Mock step results
        StepResult result1 = createMockStepResult("step1", true, "abc123hash");
        Map<String, StepResult> stepResults = Map.of("step1", result1);
        when(mockHandle.getStepResults()).thenReturn(stepResults);

        String result = ChainVisualizer.visualizeProgress(mockHandle, VisualizationStyle.SIMPLE_ASCII);

        assertNotNull(result);

        // Should contain progress info
        assertTrue(result.contains("Chain: test-chain"));
        assertTrue(result.contains("60.0%"));

        // Should contain step statuses
        assertTrue(result.contains("step1"));
        assertTrue(result.contains("step2"));
        assertTrue(result.contains("step3"));

        // Should contain transaction hash
        assertTrue(result.contains("abc123ha.."));
    }

    @Test
    void testVisualizeProgress_CompactStyle() {
        when(mockHandle.getChainId()).thenReturn("compact-chain");
        when(mockHandle.getStartedAt()).thenReturn(Instant.now().minusSeconds(1));
        when(mockHandle.getProgress()).thenReturn(0.5);

        Map<String, WatchStatus> stepStatuses = Map.of(
            "s1", WatchStatus.CONFIRMED,
            "s2", WatchStatus.FAILED
        );
        when(mockHandle.getStepStatuses()).thenReturn(stepStatuses);
        when(mockHandle.getStepResults()).thenReturn(Collections.emptyMap());

        String result = ChainVisualizer.visualizeProgress(mockHandle, VisualizationStyle.COMPACT);

        assertNotNull(result);

        // Compact style should be shorter
        assertTrue(result.split("\n").length < 15);

        // Should contain compact status symbols
        assertTrue(result.contains("s1"));
        assertTrue(result.contains("s2"));
    }

    @Test
    void testVisualizeUtxoFlow() {
        when(mockHandle.getChainId()).thenReturn("utxo-test");

        // Mock step results with transaction hashes
        StepResult result1 = createMockStepResult("step1", true, "abcdef123456");
        StepResult result2 = createMockStepResult("step2", true, "fedcba654321");

        Map<String, StepResult> stepResults = Map.of(
            "step1", result1,
            "step2", result2
        );
        when(mockHandle.getStepResults()).thenReturn(stepResults);

        String result = ChainVisualizer.visualizeUtxoFlow(mockHandle, VisualizationStyle.UNICODE_BOX);

        assertNotNull(result);

        // Should contain UTXO flow header
        assertTrue(result.contains("UTXO Flow Diagram"));

        // Should contain step information
        assertTrue(result.contains("step1"));
        assertTrue(result.contains("step2"));

        // Should contain truncated transaction hashes
        assertTrue(result.contains("abcdef12..") || result.contains("abcdef123456"),
                   "Expected to find transaction hash 'abcdef12..' or 'abcdef123456' but got: " + result);
        assertTrue(result.contains("fedcba65..") || result.contains("fedcba654321"),
                   "Expected to find transaction hash 'fedcba65..' or 'fedcba654321' but got: " + result);
    }

    @Test
    void testStatusSymbols_DifferentStyles() {
        // Test ASCII symbols
        String asciiPending = StatusSymbols.getSymbol(WatchStatus.PENDING, VisualizationStyle.SIMPLE_ASCII);
        assertEquals("[ ]", asciiPending);

        String asciiConfirmed = StatusSymbols.getSymbol(WatchStatus.CONFIRMED, VisualizationStyle.SIMPLE_ASCII);
        assertEquals("[✓]", asciiConfirmed);

        // Test Unicode symbols
        String unicodePending = StatusSymbols.getSymbol(WatchStatus.PENDING, VisualizationStyle.UNICODE_BOX);
        assertEquals("⏳", unicodePending);

        String unicodeConfirmed = StatusSymbols.getSymbol(WatchStatus.CONFIRMED, VisualizationStyle.UNICODE_BOX);
        assertEquals("✅", unicodeConfirmed);

        // Test compact symbols
        String compactPending = StatusSymbols.getSymbol(WatchStatus.PENDING, VisualizationStyle.COMPACT);
        assertEquals(".", compactPending);

        // Test detailed symbols
        String detailedPending = StatusSymbols.getSymbol(WatchStatus.PENDING, VisualizationStyle.DETAILED);
        assertTrue(detailedPending.contains("PENDING"));
    }

    @Test
    void testTerminalCapabilities() {
        // Test that capabilities can be detected (results may vary by environment)
        boolean unicodeSupport = TerminalCapabilities.supportsUnicode();
        boolean ansiSupport = TerminalCapabilities.supportsAnsiColors();

        // Should return boolean values (not null)
        assertNotNull(unicodeSupport);
        assertNotNull(ansiSupport);

        // Test getting best style
        VisualizationStyle bestStyle = TerminalCapabilities.getBestStyle();
        assertNotNull(bestStyle);
        assertTrue(bestStyle == VisualizationStyle.UNICODE_BOX ||
                  bestStyle == VisualizationStyle.SIMPLE_ASCII);

        // Test terminal width
        int width = TerminalCapabilities.getTerminalWidth();
        assertTrue(width > 0);
        assertTrue(width <= 200); // Reasonable upper bound
    }

    @Test
    void testChainDiagram_BoxDrawing() {
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII);

        diagram.drawBox("Test", "Content", 15);
        String result = diagram.build();

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Should contain box characters
        assertTrue(result.contains("+"));
        assertTrue(result.contains("-"));
        assertTrue(result.contains("|"));
        assertTrue(result.contains("Test"));
        assertTrue(result.contains("Content"));
    }

    @Test
    void testChainDiagram_ProgressBar() {
        ChainDiagram diagram = new ChainDiagram(VisualizationStyle.SIMPLE_ASCII);

        diagram.drawProgressBar(0.75, 20);
        String result = diagram.build();

        assertNotNull(result);

        // Should contain progress bar elements
        assertTrue(result.contains("["));
        assertTrue(result.contains("]"));
        assertTrue(result.contains("75.0%"));

        // Should have appropriate number of filled characters
        long filledCount = result.chars().filter(ch -> ch == '=').count();
        assertEquals(15, filledCount); // 75% of 20
    }

    // Helper methods for creating mocks

    private WatchableStep createMockStep(String stepId, String description, List<StepOutputDependency> dependencies) {
        WatchableQuickTxBuilder.WatchableTxContext txContext = mock(WatchableQuickTxBuilder.WatchableTxContext.class);
        
        // Configure the mock BEFORE creating the WatchableStep
        when(txContext.getStepId()).thenReturn(stepId);
        when(txContext.getDescription()).thenReturn(description);
        when(txContext.getUtxoDependencies()).thenReturn(dependencies);

        WatchableStep step = new WatchableStep(txContext);
        return step;
    }

    private Watcher.WatcherBuilder createMockBuilder(String chainId, List<WatchableStep> steps) {
        Watcher.WatcherBuilder builder = mock(Watcher.WatcherBuilder.class);
        when(builder.getChainId()).thenReturn(chainId);
        when(builder.getSteps()).thenReturn(steps);
        when(builder.getDescription()).thenReturn("Test chain description");
        return builder;
    }

    private StepResult createMockStepResult(String stepId, boolean successful, String txHash) {
        StepResult result = mock(StepResult.class);
        when(result.getStepId()).thenReturn(stepId);
        when(result.isSuccessful()).thenReturn(successful);
        when(result.getTransactionHash()).thenReturn(txHash);
        when(result.getCompletedAt()).thenReturn(Instant.now());
        when(result.getStatus()).thenReturn(successful ? WatchStatus.CONFIRMED : WatchStatus.FAILED);

        if (!successful) {
            RuntimeException error = new RuntimeException("Test error");
            when(result.getError()).thenReturn(error);
        }

        return result;
    }
}
