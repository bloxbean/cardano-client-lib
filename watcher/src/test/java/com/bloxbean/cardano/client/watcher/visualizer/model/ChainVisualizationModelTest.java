package com.bloxbean.cardano.client.watcher.visualizer.model;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ChainVisualizationModelTest {
    
    @Test
    void testChainVisualizationModelBuilder() {
        ChainVisualizationModel.ChainMetadata metadata = ChainVisualizationModel.ChainMetadata.builder()
            .chainId("test-chain")
            .description("Test chain description")
            .totalSteps(2)
            .timestamp(Instant.now())
            .build();
        
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.RUNNING)
            .progress(0.5)
            .stepsCompleted(1)
            .stepsTotal(2)
            .build();
            
        StepVisualizationModel step1 = StepVisualizationModel.builder()
            .stepId("step1")
            .name("First Step")
            .description("First step description")
            .position(0)
            .status(WatchStatus.CONFIRMED)
            .dependencies(Collections.emptyList())
            .build();
            
        StepVisualizationModel step2 = StepVisualizationModel.builder()
            .stepId("step2")
            .name("Second Step")
            .description("Second step description")
            .position(1)
            .status(WatchStatus.WATCHING)
            .dependencies(Arrays.asList("step1"))
            .build();
        
        ChainVisualizationModel model = ChainVisualizationModel.builder()
            .metadata(metadata)
            .execution(execution)
            .steps(Arrays.asList(step1, step2))
            .dependencies(Collections.emptyList())
            .errorState(ErrorStateModel.noErrors())
            .build();
        
        assertNotNull(model);
        assertEquals("test-chain", model.getMetadata().getChainId());
        assertEquals("Test chain description", model.getMetadata().getDescription());
        assertEquals(2, model.getMetadata().getTotalSteps());
        assertEquals(ExecutionStateModel.ExecutionStatus.RUNNING, model.getExecution().getStatus());
        assertEquals(0.5, model.getExecution().getProgress());
        assertEquals(2, model.getSteps().size());
        assertFalse(model.getErrorState().isHasErrors());
    }
    
    @Test
    void testStepVisualizationModelWithExecution() {
        Instant startTime = Instant.now().minusSeconds(30);
        Instant completedTime = Instant.now();
        
        StepVisualizationModel.StepExecutionModel execution = 
            StepVisualizationModel.StepExecutionModel.builder()
                .startedAt(startTime)
                .completedAt(completedTime)
                .retryCount(0)
                .build();
            
        StepVisualizationModel.TransactionModel transaction = 
            StepVisualizationModel.TransactionModel.builder()
                .hash("abc123def456")
                .blockNumber(12345L)
                .slot(567890L)
                .build();
        
        StepVisualizationModel step = StepVisualizationModel.builder()
            .stepId("test-step")
            .name("Test Step")
            .status(WatchStatus.CONFIRMED)
            .execution(execution)
            .transaction(transaction)
            .build();
        
        assertNotNull(step);
        assertEquals("test-step", step.getStepId());
        assertEquals(WatchStatus.CONFIRMED, step.getStatus());
        assertEquals(startTime, step.getExecution().getStartedAt());
        assertEquals(completedTime, step.getExecution().getCompletedAt());
        assertEquals("abc123def456", step.getTransaction().getHash());
        assertEquals(Long.valueOf(12345L), step.getTransaction().getBlockNumber());
    }
    
    @Test
    void testStepVisualizationModelWithError() {
        Instant errorTime = Instant.now();
        
        StepVisualizationModel.ErrorModel error = 
            StepVisualizationModel.ErrorModel.builder()
                .type("RuntimeException")
                .message("Test error message")
                .occurredAt(errorTime)
                .retryable(true)
                .stackTrace("Detailed error information")
                .build();
        
        StepVisualizationModel step = StepVisualizationModel.builder()
            .stepId("failed-step")
            .status(WatchStatus.FAILED)
            .error(error)
            .build();
        
        assertNotNull(step);
        assertEquals("failed-step", step.getStepId());
        assertEquals(WatchStatus.FAILED, step.getStatus());
        assertNotNull(step.getError());
        assertEquals("RuntimeException", step.getError().getType());
        assertEquals("Test error message", step.getError().getMessage());
        assertTrue(step.getError().getRetryable());
    }
    
    @Test
    void testExecutionStateModelProgress() {
        ExecutionStateModel execution = ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.RUNNING)
            .progress(1.5) // Raw progress value - no clamping in model
            .build();
        
        assertEquals(1.5, execution.getProgress()); // Raw value
        
        execution = ExecutionStateModel.builder()
            .progress(-0.1) // Raw progress value - no clamping in model
            .build();
        
        assertEquals(-0.1, execution.getProgress()); // Raw value
        
        // Test typical valid progress values
        execution = ExecutionStateModel.builder()
            .progress(0.75)
            .build();
        
        assertEquals(0.75, execution.getProgress());
    }
    
    @Test
    void testDependencyModel() {
        DependencyModel dependency = DependencyModel.builder()
            .fromStepId("step1")
            .toStepId("step2")
            .type(DependencyModel.DependencyType.UTXO)
            .utxoIndex(0)
            .description("UTXO dependency from step1 to step2")
            .build();
        
        assertNotNull(dependency);
        assertEquals("step1", dependency.getFromStepId());
        assertEquals("step2", dependency.getToStepId());
        assertEquals(DependencyModel.DependencyType.UTXO, dependency.getType());
        assertEquals(Integer.valueOf(0), dependency.getUtxoIndex());
        assertEquals("UTXO dependency from step1 to step2", dependency.getDescription());
    }
    
    @Test
    void testErrorStateModel() {
        // Test successful state
        ErrorStateModel noErrors = ErrorStateModel.noErrors();
        assertFalse(noErrors.isHasErrors());
        assertNull(noErrors.getFailedStepId());
        
        // Test failed state
        Instant errorTime = Instant.now();
        ErrorStateModel failedState = ErrorStateModel.failed(
            "step2", 
            "TransactionBuildException", 
            "Insufficient funds",
            errorTime
        );
        
        assertTrue(failedState.isHasErrors());
        assertEquals("step2", failedState.getFailedStepId());
        assertEquals("TransactionBuildException", failedState.getErrorType());
        assertEquals("Insufficient funds", failedState.getErrorMessage());
        assertEquals(errorTime, failedState.getOccurredAt());
        
        // Test builder pattern
        ErrorStateModel builderError = ErrorStateModel.builder()
            .hasErrors(true)
            .failedStepId("step3")
            .errorType("NetworkException")
            .errorMessage("Network timeout")
            .isRetryable(true)
            .build();
        
        assertTrue(builderError.isHasErrors());
        assertTrue(builderError.getIsRetryable());
    }
    
    @Test
    void testChainMetadata() {
        Instant timestamp = Instant.now();
        ChainVisualizationModel.ChainMetadata metadata = 
            ChainVisualizationModel.ChainMetadata.builder()
                .chainId("chain-1")
                .description("Test Chain")
                .totalSteps(3)
                .timestamp(timestamp)
                .build();
        
        assertEquals("chain-1", metadata.getChainId());
        assertEquals("Test Chain", metadata.getDescription());
        assertEquals("1.0", metadata.getVersion()); // Default version
        assertEquals(3, metadata.getTotalSteps());
        assertEquals(timestamp, metadata.getTimestamp());
    }
}