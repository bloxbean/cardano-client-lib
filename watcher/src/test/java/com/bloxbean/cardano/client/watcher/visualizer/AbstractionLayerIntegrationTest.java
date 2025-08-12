package com.bloxbean.cardano.client.watcher.visualizer;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.BasicWatchHandle;
import com.bloxbean.cardano.client.watcher.chain.StepResult;
import com.bloxbean.cardano.client.watcher.chain.Watcher;
import com.bloxbean.cardano.client.watcher.quicktx.StepOutputDependency;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableQuickTxBuilder;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableStep;
import com.bloxbean.cardano.client.watcher.visualizer.extraction.ChainModelExtractor;
import com.bloxbean.cardano.client.watcher.visualizer.json.JsonSerializer;
import com.bloxbean.cardano.client.watcher.visualizer.model.ChainVisualizationModel;
import com.bloxbean.cardano.client.watcher.visualizer.model.ExecutionStateModel;
import com.bloxbean.cardano.client.watcher.quicktx.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.watcher.visualizer.renderers.AsciiChainRenderer;
import com.bloxbean.cardano.client.watcher.visualizer.renderers.SvgChainRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the complete abstraction layer architecture.
 * 
 * Tests the end-to-end flow from domain objects → abstract model → renderers.
 */
class AbstractionLayerIntegrationTest {
    
    @Mock
    private BasicWatchHandle mockHandle;
    
    @Mock
    private Watcher.WatcherBuilder mockBuilder;
    
    @Mock
    private WatchableQuickTxBuilder.WatchableTxContext mockTxContext1;
    
    @Mock
    private WatchableQuickTxBuilder.WatchableTxContext mockTxContext2;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testCompleteAbstractionLayerFlow() {
        // Arrange: Set up mock domain objects
        setupMockBasicWatchHandle();
        
        // Act: Extract model from domain objects
        ChainVisualizationModel model = ChainModelExtractor.extractModel(mockHandle);
        
        // Assert: Verify model extraction
        assertNotNull(model);
        assertEquals("test-chain-id", model.getMetadata().getChainId());
        assertEquals(2, model.getMetadata().getTotalSteps());
        assertEquals(2, model.getSteps().size());
        
        // Act: Serialize to JSON
        String json = JsonSerializer.serializePretty(model);
        assertNotNull(json);
        assertTrue(json.contains("test-chain-id"));
        
        // Act: Deserialize back from JSON
        ChainVisualizationModel deserializedModel = JsonSerializer.deserialize(json);
        assertNotNull(deserializedModel);
        assertEquals(model.getMetadata().getChainId(), deserializedModel.getMetadata().getChainId());
        
        // Act: Render with ASCII renderer
        String asciiOutput = AsciiChainRenderer.renderStructure(model, VisualizationStyle.SIMPLE_ASCII);
        assertNotNull(asciiOutput);
        assertTrue(asciiOutput.contains("test-chain-id"));
        assertTrue(asciiOutput.contains("step1"));
        assertTrue(asciiOutput.contains("step2"));
        
        // Act: Render with SVG renderer
        String svgOutput = SvgChainRenderer.renderStructure(model);
        assertNotNull(svgOutput);
        assertTrue(svgOutput.contains("<svg"));
        assertTrue(svgOutput.contains("test-chain-id"));
        assertTrue(svgOutput.contains("step1"));
        assertTrue(svgOutput.contains("step2"));
        assertTrue(svgOutput.contains("</svg>"));
        
        System.out.println("✅ Complete abstraction layer flow tested successfully");
    }
    
    @Test
    void testBuilderToRendererFlow() {
        // Arrange: Set up mock builder
        setupMockWatcherBuilder();
        
        // Act: Extract model from builder
        ChainVisualizationModel model = ChainModelExtractor.extractModel(mockBuilder);
        
        // Assert: Verify builder model extraction
        assertNotNull(model);
        assertEquals("builder-chain", model.getMetadata().getChainId());
        assertEquals("Test chain from builder", model.getMetadata().getDescription());
        assertEquals(2, model.getSteps().size());
        
        // Verify execution state for pre-execution model
        assertEquals(ExecutionStateModel.ExecutionStatus.PENDING, model.getExecution().getStatus());
        assertEquals(0.0, model.getExecution().getProgress());
        
        // Verify dependencies are captured
        assertEquals(1, model.getDependencies().size());
        assertEquals("step1", model.getDependencies().get(0).getFromStepId());
        assertEquals("step2", model.getDependencies().get(0).getToStepId());
        
        // Act: Render structure
        String structure = AsciiChainRenderer.renderStructure(model, VisualizationStyle.DETAILED);
        assertNotNull(structure);
        assertTrue(structure.contains("Dependencies: 1"));
        assertTrue(structure.contains("step1"));
        
        System.out.println("✅ Builder to renderer flow tested successfully");
    }
    
    @Test
    void testChainVisualizerAbstractionIntegration() {
        // Test that ChainVisualizer uses the abstraction layer correctly
        setupMockBasicWatchHandle();
        
        // Test model export
        ChainVisualizationModel model = ChainVisualizer.exportModel(mockHandle);
        assertNotNull(model);
        assertEquals("test-chain-id", model.getMetadata().getChainId());
        
        // Test JSON export
        String json = ChainVisualizer.exportJson(mockHandle);
        assertNotNull(json);
        assertTrue(json.contains("test-chain-id"));
        assertTrue(ChainVisualizer.isValidJson(json));
        
        // Test compact JSON export
        String compactJson = ChainVisualizer.exportJsonCompact(mockHandle);
        assertNotNull(compactJson);
        assertTrue(compactJson.length() < json.length()); // Should be more compact
        assertTrue(ChainVisualizer.isValidJson(compactJson));
        
        // Test SVG export
        String svg = ChainVisualizer.exportSvg(mockHandle);
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("test-chain-id"));
        
        // Test that existing visualization methods still work (backward compatibility)
        String structure = ChainVisualizer.visualizeStructure(mockBuilder);
        assertNotNull(structure);
        
        String progress = ChainVisualizer.visualizeProgress(mockHandle);
        assertNotNull(progress);
        
        String utxoFlow = ChainVisualizer.visualizeUtxoFlow(mockHandle);
        assertNotNull(utxoFlow);
        
        System.out.println("✅ ChainVisualizer abstraction integration tested successfully");
    }
    
    @Test
    void testRoundTripDataIntegrity() {
        // Test that data survives the complete round trip:
        // Domain Objects → Model → JSON → Model → Renderer
        
        setupMockBasicWatchHandle();
        
        // Step 1: Domain Objects → Model
        ChainVisualizationModel originalModel = ChainModelExtractor.extractModel(mockHandle);
        
        // Step 2: Model → JSON
        String json = JsonSerializer.serializePretty(originalModel);
        
        // Step 3: JSON → Model
        ChainVisualizationModel deserializedModel = JsonSerializer.deserialize(json);
        
        // Step 4: Model → Renderer
        String originalRender = AsciiChainRenderer.renderStructure(originalModel, VisualizationStyle.SIMPLE_ASCII);
        String deserializedRender = AsciiChainRenderer.renderStructure(deserializedModel, VisualizationStyle.SIMPLE_ASCII);
        
        // Assert: Both renders should be identical (data integrity preserved)
        assertEquals(originalRender, deserializedRender);
        
        // Verify key data points are preserved
        assertEquals(originalModel.getMetadata().getChainId(), 
                    deserializedModel.getMetadata().getChainId());
        assertEquals(originalModel.getExecution().getProgress(), 
                    deserializedModel.getExecution().getProgress());
        assertEquals(originalModel.getSteps().size(), 
                    deserializedModel.getSteps().size());
        
        System.out.println("✅ Round trip data integrity verified");
    }
    
    @Test
    void testMultipleRenderersFromSameModel() {
        // Test that multiple renderers can work with the same model
        setupMockBasicWatchHandle();
        
        ChainVisualizationModel model = ChainModelExtractor.extractModel(mockHandle);
        
        // Test multiple ASCII styles
        String asciiSimple = AsciiChainRenderer.renderStructure(model, VisualizationStyle.SIMPLE_ASCII);
        String asciiUnicode = AsciiChainRenderer.renderStructure(model, VisualizationStyle.UNICODE_BOX);
        String asciiCompact = AsciiChainRenderer.renderStructure(model, VisualizationStyle.COMPACT);
        String asciiDetailed = AsciiChainRenderer.renderStructure(model, VisualizationStyle.DETAILED);
        
        // All should be valid and different
        assertNotNull(asciiSimple);
        assertNotNull(asciiUnicode);
        assertNotNull(asciiCompact);
        assertNotNull(asciiDetailed);
        
        // Compact should be shorter than detailed
        assertTrue(asciiCompact.length() < asciiDetailed.length());
        
        // Test SVG renderer
        String svg = SvgChainRenderer.renderStructure(model);
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        
        // All should contain the chain ID
        assertTrue(asciiSimple.contains("test-chain-id"));
        assertTrue(asciiUnicode.contains("test-chain-id"));
        assertTrue(asciiCompact.contains("test-chain-id"));
        assertTrue(asciiDetailed.contains("test-chain-id"));
        assertTrue(svg.contains("test-chain-id"));
        
        System.out.println("✅ Multiple renderers from same model tested successfully");
    }
    
    @Test
    void testErrorHandlingInAbstractionLayer() {
        // Test error handling throughout the abstraction layer
        
        // Test null inputs
        assertThrows(IllegalArgumentException.class, () -> 
            ChainModelExtractor.extractModel((BasicWatchHandle) null));
        assertThrows(IllegalArgumentException.class, () -> 
            AsciiChainRenderer.renderStructure(null, VisualizationStyle.SIMPLE_ASCII));
        assertThrows(IllegalArgumentException.class, () -> 
            JsonSerializer.serialize(null));
        
        // Test invalid JSON
        assertThrows(JsonSerializer.JsonDeserializationException.class, () ->
            JsonSerializer.deserialize("{ invalid json }"));
        
        // Test model with error state
        setupMockBasicWatchHandleWithError();
        ChainVisualizationModel errorModel = ChainModelExtractor.extractModel(mockHandle);
        
        // Should handle error state gracefully
        assertTrue(errorModel.getErrorState().isHasErrors());
        assertEquals("failed-step", errorModel.getErrorState().getFailedStepId());
        
        // Renderers should handle error model without throwing
        String errorRender = AsciiChainRenderer.renderProgress(errorModel, VisualizationStyle.DETAILED);
        assertNotNull(errorRender);
        assertTrue(errorRender.contains("FAILED") || errorRender.contains("Error"));
        
        System.out.println("✅ Error handling in abstraction layer tested successfully");
    }
    
    // Helper methods to set up mock objects
    
    private void setupMockBasicWatchHandle() {
        when(mockHandle.getChainId()).thenReturn("test-chain-id");
        when(mockHandle.getStartedAt()).thenReturn(Instant.now().minusSeconds(30));
        when(mockHandle.getProgress()).thenReturn(0.6);
        when(mockHandle.getStatus()).thenReturn(WatchStatus.WATCHING);
        when(mockHandle.isCompleted()).thenReturn(false);
        
        Map<String, WatchStatus> stepStatuses = new LinkedHashMap<>();
        stepStatuses.put("step1", WatchStatus.CONFIRMED);
        stepStatuses.put("step2", WatchStatus.WATCHING);
        when(mockHandle.getStepStatuses()).thenReturn(stepStatuses);
        
        StepResult step1Result = mock(StepResult.class);
        when(step1Result.isSuccessful()).thenReturn(true);
        when(step1Result.getTransactionHash()).thenReturn("abc123def456");
        when(step1Result.getCompletedAt()).thenReturn(Instant.now().minusSeconds(10));
        
        StepResult step2Result = mock(StepResult.class);
        when(step2Result.isSuccessful()).thenReturn(false);
        when(step2Result.getTransactionHash()).thenReturn(null);
        when(step2Result.getCompletedAt()).thenReturn(null);
        
        Map<String, StepResult> stepResults = new HashMap<>();
        stepResults.put("step1", step1Result);
        stepResults.put("step2", step2Result);
        when(mockHandle.getStepResults()).thenReturn(stepResults);
    }
    
    private void setupMockBasicWatchHandleWithError() {
        when(mockHandle.getChainId()).thenReturn("error-chain-id");
        when(mockHandle.getStartedAt()).thenReturn(Instant.now().minusSeconds(10));
        when(mockHandle.getProgress()).thenReturn(0.3);
        when(mockHandle.getStatus()).thenReturn(WatchStatus.FAILED);
        when(mockHandle.isCompleted()).thenReturn(true);
        
        Map<String, WatchStatus> stepStatuses = new LinkedHashMap<>();
        stepStatuses.put("failed-step", WatchStatus.FAILED);
        when(mockHandle.getStepStatuses()).thenReturn(stepStatuses);
        
        StepResult failedResult = mock(StepResult.class);
        when(failedResult.isSuccessful()).thenReturn(false);
        when(failedResult.getError()).thenReturn(new RuntimeException("Test error"));
        when(failedResult.getCompletedAt()).thenReturn(Instant.now());
        
        Map<String, StepResult> stepResults = new HashMap<>();
        stepResults.put("failed-step", failedResult);
        when(mockHandle.getStepResults()).thenReturn(stepResults);
    }
    
    private void setupMockWatcherBuilder() {
        when(mockBuilder.getChainId()).thenReturn("builder-chain");
        when(mockBuilder.getDescription()).thenReturn("Test chain from builder");
        
        WatchableStep step1 = createMockStep("step1", "First step", Collections.emptyList());
        WatchableStep step2 = createMockStep("step2", "Second step", 
            Arrays.asList(new StepOutputDependency("step1", UtxoSelectionStrategy.ALL)));
        
        when(mockBuilder.getSteps()).thenReturn(Arrays.asList(step1, step2));
    }
    
    private WatchableStep createMockStep(String stepId, String description, List<StepOutputDependency> dependencies) {
        WatchableQuickTxBuilder.WatchableTxContext txContext = mock(WatchableQuickTxBuilder.WatchableTxContext.class);
        when(txContext.getStepId()).thenReturn(stepId);
        when(txContext.getDescription()).thenReturn(description);
        when(txContext.getUtxoDependencies()).thenReturn(dependencies);
        
        WatchableStep step = new WatchableStep(txContext);
        return step;
    }
}