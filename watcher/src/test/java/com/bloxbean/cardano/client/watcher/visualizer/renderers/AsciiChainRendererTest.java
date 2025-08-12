package com.bloxbean.cardano.client.watcher.visualizer.renderers;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.visualizer.VisualizationStyle;
import com.bloxbean.cardano.client.watcher.visualizer.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AsciiChainRendererTest {
    
    @Test
    void testRenderStructureWithValidModel() {
        ChainVisualizationModel model = createTestModel();
        
        String result = AsciiChainRenderer.renderStructure(model, VisualizationStyle.SIMPLE_ASCII);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // Should contain chain information
        assertTrue(result.contains("Chain: test-chain"));
        assertTrue(result.contains("Steps: 2"));
        
        // Should contain step information
        assertTrue(result.contains("step1"));
        assertTrue(result.contains("step2"));
        
        // Should contain dependency information
        assertTrue(result.contains("Dependencies: 1"));
        assertTrue(result.contains("step1"));
    }
    
    @Test
    void testRenderStructureCompactStyle() {
        ChainVisualizationModel model = createTestModel();
        
        String result = AsciiChainRenderer.renderStructure(model, VisualizationStyle.COMPACT);
        
        assertNotNull(result);
        // Compact style should be shorter
        assertTrue(result.split("\n").length < 10);
        
        // Should contain step indicators with dependencies
        assertTrue(result.contains("step2*")); // * indicates dependency
        assertTrue(result.contains("->"));     // Arrow connector
        assertTrue(result.contains("UTXO dependencies"));  // Legend
    }
    
    @Test
    void testRenderStructureUnicodeStyle() {
        ChainVisualizationModel model = createTestModel();
        
        String result = AsciiChainRenderer.renderStructure(model, VisualizationStyle.UNICODE_BOX);
        
        assertNotNull(result);
        
        // Unicode style should contain box drawing characters
        if (result.contains("┌") || result.contains("└")) {
            // Unicode is supported in test environment
            assertTrue(result.contains("┌") || result.contains("╔"));
            assertTrue(result.contains("│") || result.contains("║"));
            assertTrue(result.contains("└") || result.contains("╚"));
        }
        
        // Should still contain the basic content
        assertTrue(result.contains("step1"));
        assertTrue(result.contains("step2"));
    }
    
    @Test
    void testRenderProgressWithRunningChain() {
        ChainVisualizationModel model = createRunningChainModel();
        
        String result = AsciiChainRenderer.renderProgress(model, VisualizationStyle.DETAILED);
        
        assertNotNull(result);
        
        // Should contain progress information
        assertTrue(result.contains("Progress:"));
        assertTrue(result.contains("60.0%"));  // Progress bar
        
        // Should contain timing information
        assertTrue(result.contains("⏱")); // Clock emoji for timing
        
        // Should contain step statuses
        assertTrue(result.contains("step1"));
        assertTrue(result.contains("step2"));
        assertTrue(result.contains("CONFIRMED"));
        assertTrue(result.contains("WATCHING"));
    }
    
    @Test
    void testRenderProgressCompactStyle() {
        ChainVisualizationModel model = createRunningChainModel();
        
        String result = AsciiChainRenderer.renderProgress(model, VisualizationStyle.COMPACT);
        
        assertNotNull(result);
        
        // Compact style should show step statuses in a single line
        assertTrue(result.contains("[step1:"));
        assertTrue(result.contains("[step2:"));
        
        // Should be much shorter
        assertTrue(result.split("\n").length < 10);
    }
    
    @Test
    void testRenderProgressWithCompletedChain() {
        ChainVisualizationModel model = createCompletedChainModel();
        
        String result = AsciiChainRenderer.renderProgress(model, VisualizationStyle.DETAILED);
        
        assertNotNull(result);
        
        // Should show 100% progress
        assertTrue(result.contains("100.0%"));
        
        // Should show all steps as confirmed
        assertTrue(result.contains("CONFIRMED"));
        
        // Should contain transaction hashes (truncated - 8 chars + ..)
        assertTrue(result.contains("Tx: abc123de.."));
    }
    
    @Test
    void testRenderProgressWithFailedChain() {
        ChainVisualizationModel model = createFailedChainModel();
        
        String result = AsciiChainRenderer.renderProgress(model, VisualizationStyle.DETAILED);
        
        assertNotNull(result);
        
        // Should contain error information
        assertTrue(result.contains("FAILED"));
        assertTrue(result.contains("Error:") || result.contains("Insufficient"));
    }
    
    @Test
    void testRenderUtxoFlowWithValidModel() {
        ChainVisualizationModel model = createModelWithUtxoFlow();
        
        String result = AsciiChainRenderer.renderUtxoFlow(model, VisualizationStyle.UNICODE_BOX);
        
        assertNotNull(result);
        
        // Should contain UTXO flow header
        assertTrue(result.contains("UTXO Flow Diagram"));
        
        // Should contain step information
        assertTrue(result.contains("step1"));
        assertTrue(result.contains("step2"));
        
        // Should contain value information if available
        if (model.getUtxoFlow().getNodes().get(0).getInputValue() != null) {
            assertTrue(result.contains("In:") || result.contains("Out:"));
        }
    }
    
    @Test
    void testRenderUtxoFlowWithEmptyFlow() {
        ChainVisualizationModel model = createModelWithEmptyUtxoFlow();
        
        String result = AsciiChainRenderer.renderUtxoFlow(model, VisualizationStyle.SIMPLE_ASCII);
        
        assertNotNull(result);
        
        // Should contain header
        assertTrue(result.contains("UTXO Flow Diagram"));
        
        // Should contain empty message
        assertTrue(result.contains("No UTXO flow data available"));
    }
    
    @Test
    void testRenderWithNullModel() {
        assertThrows(IllegalArgumentException.class, () -> {
            AsciiChainRenderer.renderStructure(null, VisualizationStyle.SIMPLE_ASCII);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            AsciiChainRenderer.renderProgress(null, VisualizationStyle.SIMPLE_ASCII);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            AsciiChainRenderer.renderUtxoFlow(null, VisualizationStyle.SIMPLE_ASCII);
        });
    }
    
    @Test
    void testRenderWithAllVisualizationStyles() {
        ChainVisualizationModel model = createTestModel();
        
        // Test all styles work without throwing exceptions
        for (VisualizationStyle style : VisualizationStyle.values()) {
            String result = AsciiChainRenderer.renderStructure(model, style);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            
            // Each style should contain the basic chain information
            assertTrue(result.contains("step1"));
            assertTrue(result.contains("step2"));
        }
    }
    
    // Helper methods to create test models
    
    private ChainVisualizationModel createTestModel() {
        ChainVisualizationModel.ChainMetadata metadata = 
            ChainVisualizationModel.ChainMetadata.builder()
                .chainId("test-chain")
                .description("Test chain description")
                .totalSteps(2)
                .timestamp(Instant.now())
                .build();
        
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.PENDING)
            .progress(0.0)
            .build();
        
        StepVisualizationModel step1 = StepVisualizationModel.builder()
            .stepId("step1")
            .description("First step")
            .position(0)
            .status(WatchStatus.PENDING)
            .dependencies(Collections.emptyList())
            .build();
        
        StepVisualizationModel step2 = StepVisualizationModel.builder()
            .stepId("step2")
            .description("Second step with dependencies")
            .position(1)
            .status(WatchStatus.PENDING)
            .dependencies(Arrays.asList("step1"))
            .build();
        
        return ChainVisualizationModel.builder()
            .metadata(metadata)
            .execution(execution)
            .steps(Arrays.asList(step1, step2))
            .errorState(ErrorStateModel.noErrors())
            .build();
    }
    
    private ChainVisualizationModel createRunningChainModel() {
        ChainVisualizationModel.ChainMetadata metadata = ChainVisualizationModel.ChainMetadata.builder()
            .chainId("running-chain")
            .description("Running test")
            .totalSteps(2)
            .timestamp(Instant.now())
            .build();
        
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.RUNNING)
            .progress(0.6)
            .startedAt(Instant.now().minusSeconds(30))
            .build();
        
        StepVisualizationModel step1 = StepVisualizationModel.builder()
            .stepId("step1")
            .status(WatchStatus.CONFIRMED)
            .position(0)
            .transaction(StepVisualizationModel.TransactionModel.builder()
                .hash("abc123def456")
                .blockNumber(12345L)
                .slot(67890L)
                .build())
            .build();
        
        StepVisualizationModel step2 = StepVisualizationModel.builder()
            .stepId("step2")
            .status(WatchStatus.WATCHING)
            .position(1)
            .build();
        
        return ChainVisualizationModel.builder()
            .metadata(metadata)
            .execution(execution)
            .steps(Arrays.asList(step1, step2))
            .errorState(ErrorStateModel.noErrors())
            .build();
    }
    
    private ChainVisualizationModel createCompletedChainModel() {
        ChainVisualizationModel.ChainMetadata metadata = 
            ChainVisualizationModel.ChainMetadata.builder()
                .chainId("completed-chain")
                .description("Completed test")
                .totalSteps(2)
                .timestamp(Instant.now())
                .build();
        
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.COMPLETED)
            .progress(1.0)
            .stepsCompleted(2)
            .stepsTotal(2)
            .build();
        
        StepVisualizationModel step1 = StepVisualizationModel.builder()
            .stepId("step1")
            .status(WatchStatus.CONFIRMED)
            .position(0)
            .transaction(StepVisualizationModel.TransactionModel.builder()
                .hash("abc123def456")
                .blockNumber(12345L)
                .slot(67890L)
                .build())
            .build();
        
        StepVisualizationModel step2 = StepVisualizationModel.builder()
            .stepId("step2")
            .status(WatchStatus.CONFIRMED)
            .position(1)
            .transaction(StepVisualizationModel.TransactionModel.builder()
                .hash("def456abc123")
                .blockNumber(12346L)
                .slot(67891L)
                .build())
            .build();
        
        return ChainVisualizationModel.builder()
            .metadata(metadata)
            .execution(execution)
            .steps(Arrays.asList(step1, step2))
            .errorState(ErrorStateModel.noErrors())
            .build();
    }
    
    private ChainVisualizationModel createFailedChainModel() {
        ChainVisualizationModel.ChainMetadata metadata = 
            ChainVisualizationModel.ChainMetadata.builder()
                .chainId("failed-chain")
                .description("Failed test")
                .totalSteps(2)
                .timestamp(Instant.now())
                .build();
        
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.FAILED)
            .progress(0.3)
            .build();
        
        StepVisualizationModel step1 = StepVisualizationModel.builder()
            .stepId("step1")
            .status(WatchStatus.FAILED)
            .position(0)
            .error(StepVisualizationModel.ErrorModel.builder()
                .type("InsufficientFundsException")
                .message("Insufficient funds for transaction")
                .occurredAt(Instant.now())
                .build())
            .build();
        
        return ChainVisualizationModel.builder()
            .metadata(metadata)
            .execution(execution)
            .steps(Collections.singletonList(step1))
            .errorState(ErrorStateModel.failed("step1", "InsufficientFundsException", 
                                             "Insufficient funds", Instant.now()))
            .build();
    }
    
    private ChainVisualizationModel createModelWithUtxoFlow() {
        ChainVisualizationModel model = createTestModel();
        
        UtxoFlowModel.UtxoNode node1 = UtxoFlowModel.UtxoNode.builder()
            .stepId("step1")
            .name("First step")
            .build();
        
        UtxoFlowModel.UtxoNode node2 = UtxoFlowModel.UtxoNode.builder()
            .stepId("step2")
            .name("Second step")
            .build();
        
        UtxoFlowModel utxoFlow = UtxoFlowModel.builder()
            .nodes(Arrays.asList(node1, node2))
            .edges(Collections.singletonList(UtxoFlowModel.UtxoEdge.builder()
                .fromStep("step1")
                .toStep("step2")
                .label("95 ADA")
                .build()))
            .build();
        
        return ChainVisualizationModel.builder()
            .metadata(model.getMetadata())
            .execution(model.getExecution())
            .steps(model.getSteps())
            .utxoFlow(utxoFlow)
            .errorState(model.getErrorState())
            .build();
    }
    
    private ChainVisualizationModel createModelWithEmptyUtxoFlow() {
        ChainVisualizationModel model = createTestModel();
        
        UtxoFlowModel emptyUtxoFlow = UtxoFlowModel.builder()
            .nodes(Collections.emptyList())
            .edges(Collections.emptyList())
            .build();
        
        return ChainVisualizationModel.builder()
            .metadata(model.getMetadata())
            .execution(model.getExecution())
            .steps(model.getSteps())
            .utxoFlow(emptyUtxoFlow)
            .errorState(model.getErrorState())
            .build();
    }
}