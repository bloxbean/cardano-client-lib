package com.bloxbean.cardano.client.watcher.visualizer.json;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.visualizer.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JsonSerializerTest {
    
    @Test
    void testSerializeAndDeserializeCompleteModel() {
        // Create a complete model for testing
        ChainVisualizationModel originalModel = createTestModel();
        
        // Serialize to JSON
        String json = JsonSerializer.serializePretty(originalModel);
        assertNotNull(json);
        assertFalse(json.isEmpty());
        assertTrue(json.contains("test-chain-id"));
        assertTrue(json.contains("step1"));
        assertTrue(json.contains("step2"));
        
        // Deserialize back to model
        ChainVisualizationModel deserializedModel = JsonSerializer.deserialize(json);
        assertNotNull(deserializedModel);
        
        // Verify metadata
        assertEquals(originalModel.getMetadata().getChainId(), 
                    deserializedModel.getMetadata().getChainId());
        assertEquals(originalModel.getMetadata().getDescription(), 
                    deserializedModel.getMetadata().getDescription());
        assertEquals(originalModel.getMetadata().getTotalSteps(), 
                    deserializedModel.getMetadata().getTotalSteps());
        
        // Verify execution state
        assertEquals(originalModel.getExecution().getStatus(), 
                    deserializedModel.getExecution().getStatus());
        assertEquals(originalModel.getExecution().getProgress(), 
                    deserializedModel.getExecution().getProgress());
        assertEquals(originalModel.getExecution().getStepsCompleted(), 
                    deserializedModel.getExecution().getStepsCompleted());
        
        // Verify steps
        assertEquals(originalModel.getSteps().size(), deserializedModel.getSteps().size());
        for (int i = 0; i < originalModel.getSteps().size(); i++) {
            StepVisualizationModel originalStep = originalModel.getSteps().get(i);
            StepVisualizationModel deserializedStep = deserializedModel.getSteps().get(i);
            
            assertEquals(originalStep.getStepId(), deserializedStep.getStepId());
            assertEquals(originalStep.getStatus(), deserializedStep.getStatus());
            assertEquals(originalStep.getDescription(), deserializedStep.getDescription());
        }
        
        // Verify error state
        assertEquals(originalModel.getErrorState().isHasErrors(), 
                    deserializedModel.getErrorState().isHasErrors());
    }
    
    @Test
    void testSerializeCompact() {
        ChainVisualizationModel model = createSimpleModel();
        
        String compactJson = JsonSerializer.serializeCompact(model);
        String prettyJson = JsonSerializer.serializePretty(model);
        
        assertNotNull(compactJson);
        assertNotNull(prettyJson);
        
        // Compact should be shorter (no whitespace formatting)
        assertTrue(compactJson.length() < prettyJson.length());
        
        // Both should deserialize to the same model
        ChainVisualizationModel fromCompact = JsonSerializer.deserialize(compactJson);
        ChainVisualizationModel fromPretty = JsonSerializer.deserialize(prettyJson);
        
        assertEquals(fromCompact.getMetadata().getChainId(), fromPretty.getMetadata().getChainId());
        assertEquals(fromCompact.getSteps().size(), fromPretty.getSteps().size());
    }
    
    @Test
    void testSerializeWithNullValues() {
        // Test model with minimal required fields
        ChainVisualizationModel.ChainMetadata metadata = 
            ChainVisualizationModel.ChainMetadata.builder()
                .chainId("minimal-chain")
                .description(null)
                .totalSteps(1)
                .timestamp(Instant.now())
                .build();
            
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.PENDING)
            .progress(0.0)
            .build();
        
        StepVisualizationModel step = StepVisualizationModel.builder()
            .stepId("minimal-step")
            .status(WatchStatus.PENDING)
            .position(0)
            .build();
        
        ChainVisualizationModel model = ChainVisualizationModel.builder()
            .metadata(metadata)
            .execution(execution)
            .steps(Collections.singletonList(step))
            .errorState(ErrorStateModel.noErrors())
            .build();
        
        String json = JsonSerializer.serialize(model);
        assertNotNull(json);
        
        // Should be able to deserialize without issues
        ChainVisualizationModel deserialized = JsonSerializer.deserialize(json);
        assertNotNull(deserialized);
        assertEquals("minimal-chain", deserialized.getMetadata().getChainId());
    }
    
    @Test
    void testDeserializeWithInvalidJson() {
        // Test with invalid JSON
        assertThrows(JsonSerializer.JsonDeserializationException.class, () -> {
            JsonSerializer.deserialize("{ invalid json }");
        });
        
        // Test with null
        assertThrows(IllegalArgumentException.class, () -> {
            JsonSerializer.deserialize((String) null);
        });
        
        // Test with empty string
        assertThrows(IllegalArgumentException.class, () -> {
            JsonSerializer.deserialize("");
        });
    }
    
    @Test
    void testSerializeWithNullModel() {
        assertThrows(IllegalArgumentException.class, () -> {
            JsonSerializer.serialize(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            JsonSerializer.serializePretty(null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            JsonSerializer.serializeCompact(null);
        });
    }
    
    @Test
    void testJsonValidation() {
        ChainVisualizationModel model = createSimpleModel();
        String validJson = JsonSerializer.serialize(model);
        
        assertTrue(JsonSerializer.isValid(validJson));
        assertFalse(JsonSerializer.isValid("{ invalid }"));
        assertFalse(JsonSerializer.isValid(null));
        assertFalse(JsonSerializer.isValid(""));
        assertFalse(JsonSerializer.isValid("   "));
    }
    
    @Test
    void testSerializeWithComplexErrorState() {
        ErrorStateModel errorState = ErrorStateModel.builder()
            .hasErrors(true)
            .failedStepId("complex-step")
            .errorType("ComplexException")
            .errorMessage("Complex error occurred")
            .occurredAt(Instant.now())
            .isRetryable(false)
            .build();
        
        ChainVisualizationModel model = ChainVisualizationModel.builder()
            .metadata(ChainVisualizationModel.ChainMetadata.builder()
                .chainId("error-chain")
                .description("Error test")
                .totalSteps(1)
                .timestamp(Instant.now())
                .build())
            .execution(ExecutionStateModel.builder()
                .status(ExecutionStateModel.ExecutionStatus.FAILED)
                .progress(0.3)
                .build())
            .steps(Collections.singletonList(
                StepVisualizationModel.builder()
                    .stepId("complex-step")
                    .status(WatchStatus.FAILED)
                    .position(0)
                    .build()
            ))
            .errorState(errorState)
            .build();
        
        String json = JsonSerializer.serializePretty(model);
        assertNotNull(json);
        assertTrue(json.contains("ComplexException"));
        // Verify deserialization
        ChainVisualizationModel deserialized = JsonSerializer.deserialize(json);
        assertTrue(deserialized.getErrorState().isHasErrors());
    }
    
    // Helper methods to create test models
    
    private ChainVisualizationModel createTestModel() {
        Instant now = Instant.now();
        
        ChainVisualizationModel.ChainMetadata metadata = 
            ChainVisualizationModel.ChainMetadata.builder()
                .chainId("test-chain-id")
                .description("Test Chain Description")
                .totalSteps(2)
                .timestamp(now)
                .build();
        
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.RUNNING)
            .progress(0.6)
            .startedAt(now.minusSeconds(30))
            .stepsCompleted(1)
            .stepsTotal(2)
            .currentStep("step2")
            .build();
        
        StepVisualizationModel step1 = StepVisualizationModel.builder()
            .stepId("step1")
            .name("First Step")
            .description("First step in the chain")
            .position(0)
            .status(WatchStatus.CONFIRMED)
            .dependencies(Collections.emptyList())
            .execution(StepVisualizationModel.StepExecutionModel.builder()
                .startedAt(now.minusSeconds(30))
                .completedAt(now.minusSeconds(10))
                .retryCount(0)
                .build())
            .transaction(StepVisualizationModel.TransactionModel.builder()
                .hash("abc123def456789")
                .blockNumber(12345L)
                .slot(567890L)
                .build())
            .build();
        
        StepVisualizationModel step2 = StepVisualizationModel.builder()
            .stepId("step2")
            .name("Second Step")
            .description("Second step in the chain")
            .position(1)
            .status(WatchStatus.WATCHING)
            .dependencies(Arrays.asList("step1"))
            .execution(StepVisualizationModel.StepExecutionModel.builder()
                .startedAt(now.minusSeconds(10))
                .completedAt(null)
                .retryCount(0)
                .build())
            .build();
        
        DependencyModel dependency = DependencyModel.builder()
            .fromStepId("step1")
            .toStepId("step2")
            .type(DependencyModel.DependencyType.UTXO)
            .utxoIndex(0)
            .build();
        
        return ChainVisualizationModel.builder()
            .metadata(metadata)
            .execution(execution)
            .steps(Arrays.asList(step1, step2))
            .dependencies(Collections.singletonList(dependency))
            .errorState(ErrorStateModel.noErrors())
            .build();
    }
    
    private ChainVisualizationModel createSimpleModel() {
        return ChainVisualizationModel.builder()
            .metadata(ChainVisualizationModel.ChainMetadata.builder()
                .chainId("simple-chain")
                .description("Simple test")
                .totalSteps(1)
                .timestamp(Instant.now())
                .build())
            .execution(ExecutionStateModel.builder()
                .status(ExecutionStateModel.ExecutionStatus.PENDING)
                .progress(0.0)
                .build())
            .steps(Collections.singletonList(
                StepVisualizationModel.builder()
                    .stepId("simple-step")
                    .status(WatchStatus.PENDING)
                    .position(0)
                    .build()
            ))
            .errorState(ErrorStateModel.noErrors())
            .build();
    }
}