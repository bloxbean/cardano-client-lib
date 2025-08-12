package com.bloxbean.cardano.client.watcher.visualizer.extraction;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.bloxbean.cardano.client.watcher.chain.BasicWatchHandle;
import com.bloxbean.cardano.client.watcher.chain.StepResult;
import com.bloxbean.cardano.client.watcher.chain.Watcher;
import com.bloxbean.cardano.client.watcher.quicktx.StepOutputDependency;
import com.bloxbean.cardano.client.watcher.quicktx.WatchableStep;
import com.bloxbean.cardano.client.watcher.visualizer.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts abstract visualization models from Watcher domain objects.
 * 
 * This class is responsible for converting the runtime state of transaction chains
 * into platform-agnostic visualization models that can be serialized or rendered
 * by different visualization engines.
 */
public class ChainModelExtractor {
    
    /**
     * Extract complete visualization model from a BasicWatchHandle
     */
    public static ChainVisualizationModel extractModel(BasicWatchHandle handle) {
        if (handle == null) {
            throw new IllegalArgumentException("Handle cannot be null");
        }
        
        return ChainVisualizationModel.builder()
            .metadata(extractMetadata(handle))
            .execution(extractExecutionState(handle))
            .steps(extractSteps(handle))
            .dependencies(extractDependencies(handle))
            .utxoFlow(extractUtxoFlow(handle))
            .errorState(extractErrorState(handle))
            .build();
    }
    
    /**
     * Extract visualization model from a WatcherBuilder (before execution)
     */
    public static ChainVisualizationModel extractModel(Watcher.WatcherBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("Builder cannot be null");
        }
        
        return ChainVisualizationModel.builder()
            .metadata(extractMetadata(builder))
            .execution(extractExecutionState(builder))
            .steps(extractSteps(builder))
            .dependencies(extractDependencies(builder))
            .utxoFlow(extractUtxoFlow(builder))
            .errorState(ErrorStateModel.noErrors()) // No errors before execution
            .build();
    }
    
    // Private extraction methods for BasicWatchHandle
    
    private static ChainVisualizationModel.ChainMetadata extractMetadata(BasicWatchHandle handle) {
        return ChainVisualizationModel.ChainMetadata.builder()
            .chainId(handle.getChainId())
            .description(null) // BasicWatchHandle doesn't expose description directly
            .totalSteps(getStepCount(handle))
            .timestamp(handle.getStartedAt())
            .build();
    }
    
    private static ExecutionStateModel extractExecutionState(BasicWatchHandle handle) {
        Map<String, WatchStatus> stepStatuses = handle.getStepStatuses();
        
        return ExecutionStateModel.builder()
            .status(mapExecutionStatus(handle.getStatus()))
            .progress(handle.getProgress())
            .startedAt(handle.getStartedAt())
            .completedAt(handle.isCompleted() ? findLatestCompletionTime(handle) : null)
            .currentStep(findCurrentStep(stepStatuses))
            .stepsCompleted(countCompletedSteps(stepStatuses))
            .stepsTotal(stepStatuses.size())
            .totalDuration(calculateTotalDuration(handle))
            .build();
    }
    
    private static List<StepVisualizationModel> extractSteps(BasicWatchHandle handle) {
        Map<String, WatchStatus> stepStatuses = handle.getStepStatuses();
        Map<String, StepResult> stepResults = handle.getStepResults();
        List<String> stepOrder = handle.getStepOrder();
        
        List<StepVisualizationModel> steps = new ArrayList<>();
        
        // Use the original step order if available, otherwise fall back to stepStatuses keySet
        List<String> orderedStepIds = !stepOrder.isEmpty() ? stepOrder : new ArrayList<>(stepStatuses.keySet());
        
        for (int position = 0; position < orderedStepIds.size(); position++) {
            String stepId = orderedStepIds.get(position);
            WatchStatus status = stepStatuses.get(stepId);
            StepResult result = stepResults.get(stepId);
            
            // Skip steps that don't exist in stepStatuses (shouldn't happen but defensive)
            if (status == null) continue;
            
            StepVisualizationModel step = StepVisualizationModel.builder()
                .stepId(stepId)
                .name(stepId) // Use stepId as name if no other name available
                .position(position)
                .status(status)
                .dependencies(Collections.emptyList()) // Dependencies not directly available from handle
                .execution(extractStepExecution(result))
                .transaction(extractTransaction(result))
                .error(extractStepError(result))
                .build();
                
            steps.add(step);
        }
        
        return steps;
    }
    
    private static List<DependencyModel> extractDependencies(BasicWatchHandle handle) {
        // Dependencies not directly available from BasicWatchHandle
        // This would need to be populated from the original builder or stored separately
        return Collections.emptyList();
    }
    
    private static UtxoFlowModel extractUtxoFlow(BasicWatchHandle handle) {
        // UTXO flow extraction would require access to transaction details
        // This is a simplified implementation
        Map<String, StepResult> stepResults = handle.getStepResults();
        
        List<UtxoFlowModel.UtxoNode> nodes = new ArrayList<>();
        List<UtxoFlowModel.UtxoEdge> edges = new ArrayList<>();
        
        for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
            String stepId = entry.getKey();
            StepResult result = entry.getValue();
            
            // Create node with step ID and include transaction hash if available
            String nodeName = stepId;
            if (result.getTransactionHash() != null) {
                // Truncate transaction hash for display
                String txHash = result.getTransactionHash();
                if (txHash.length() > 10) {
                    txHash = txHash.substring(0, 8) + "..";
                }
                nodeName = stepId + "\nTx: " + txHash;
            }
            
            UtxoFlowModel.UtxoNode node = new UtxoFlowModel.UtxoNode(stepId, nodeName);
            nodes.add(node);
        }
        
        return UtxoFlowModel.builder()
            .nodes(nodes)
            .edges(edges)
            .build();
    }
    
    private static ErrorStateModel extractErrorState(BasicWatchHandle handle) {
        // Check if any step has failed
        Map<String, StepResult> stepResults = handle.getStepResults();
        
        for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
            StepResult result = entry.getValue();
            if (result != null && !result.isSuccessful() && result.getError() != null) {
                return ErrorStateModel.failed(
                    entry.getKey(),
                    result.getError().getClass().getSimpleName(),
                    result.getError().getMessage(),
                    Instant.now() // Use current time if completion time not available
                );
            }
        }
        
        return ErrorStateModel.noErrors();
    }
    
    // Private extraction methods for WatcherBuilder
    
    private static ChainVisualizationModel.ChainMetadata extractMetadata(Watcher.WatcherBuilder builder) {
        return ChainVisualizationModel.ChainMetadata.builder()
            .chainId(builder.getChainId())
            .description(builder.getDescription())
            .totalSteps(builder.getSteps().size())
            .timestamp(Instant.now())
            .build();
    }
    
    private static ExecutionStateModel extractExecutionState(Watcher.WatcherBuilder builder) {
        return ExecutionStateModel.builder()
            .status(ExecutionStateModel.ExecutionStatus.PENDING)
            .progress(0.0)
            .stepsCompleted(0)
            .stepsTotal(builder.getSteps().size())
            .build();
    }
    
    private static List<StepVisualizationModel> extractSteps(Watcher.WatcherBuilder builder) {
        List<WatchableStep> steps = builder.getSteps();
        List<StepVisualizationModel> result = new ArrayList<>();
        
        for (int i = 0; i < steps.size(); i++) {
            WatchableStep step = steps.get(i);
            
            List<String> dependencies = step.getTxContext().getUtxoDependencies()
                .stream()
                .map(StepOutputDependency::getStepId)
                .collect(Collectors.toList());
            
            StepVisualizationModel stepModel = StepVisualizationModel.builder()
                .stepId(step.getStepId())
                .name(step.getStepId())
                .description(step.getDescription())
                .position(i)
                .status(WatchStatus.PENDING)
                .dependencies(dependencies)
                .build();
                
            result.add(stepModel);
        }
        
        return result;
    }
    
    private static List<DependencyModel> extractDependencies(Watcher.WatcherBuilder builder) {
        List<DependencyModel> dependencies = new ArrayList<>();
        
        for (WatchableStep step : builder.getSteps()) {
            for (StepOutputDependency dep : step.getTxContext().getUtxoDependencies()) {
                DependencyModel dependency = DependencyModel.builder()
                    .fromStepId(dep.getStepId())
                    .toStepId(step.getStepId())
                    .type(DependencyModel.DependencyType.UTXO)
                    .utxoIndex(null) // StepOutputDependency doesn't have a simple index, it uses selection strategy
                    .description("Uses " + dep.getSelectionStrategy().getClass().getSimpleName() + " strategy")
                    .build();
                dependencies.add(dependency);
            }
        }
        
        return dependencies;
    }
    
    private static UtxoFlowModel extractUtxoFlow(Watcher.WatcherBuilder builder) {
        // Pre-execution UTXO flow based on declared dependencies
        List<UtxoFlowModel.UtxoNode> nodes = new ArrayList<>();
        List<UtxoFlowModel.UtxoEdge> edges = new ArrayList<>();
        
        for (WatchableStep step : builder.getSteps()) {
            UtxoFlowModel.UtxoNode node = new UtxoFlowModel.UtxoNode(step.getStepId(), step.getDescription());
            nodes.add(node);
            
            // Create edges for dependencies
            for (StepOutputDependency dep : step.getTxContext().getUtxoDependencies()) {
                UtxoFlowModel.UtxoEdge edge = new UtxoFlowModel.UtxoEdge(
                    dep.getStepId(),
                    step.getStepId(),
                    "depends on " + dep.getStepId()
                );
                // No simple utxoIndex, using selection strategy instead
                edge.setDescription(dep.getSelectionStrategy().getClass().getSimpleName() + " selection");
                edges.add(edge);
            }
        }
        
        return UtxoFlowModel.builder()
            .nodes(nodes)
            .edges(edges)
            .build();
    }
    
    // Helper methods
    
    private static ExecutionStateModel.ExecutionStatus mapExecutionStatus(WatchStatus status) {
        if (status == null) return ExecutionStateModel.ExecutionStatus.PENDING;
        
        switch (status) {
            case PENDING:
            case BUILDING:
                return ExecutionStateModel.ExecutionStatus.PENDING;
            case WATCHING:
            case SUBMITTED:
            case RETRYING:
            case REBUILDING:
                return ExecutionStateModel.ExecutionStatus.RUNNING;
            case CONFIRMED:
                return ExecutionStateModel.ExecutionStatus.COMPLETED;
            case FAILED:
                return ExecutionStateModel.ExecutionStatus.FAILED;
            case CANCELLED:
                return ExecutionStateModel.ExecutionStatus.CANCELLED;
            default:
                return ExecutionStateModel.ExecutionStatus.PENDING;
        }
    }
    
    private static String findCurrentStep(Map<String, WatchStatus> stepStatuses) {
        // Find first step that's not completed
        return stepStatuses.entrySet().stream()
            .filter(entry -> entry.getValue() != WatchStatus.CONFIRMED && entry.getValue() != WatchStatus.FAILED)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
    
    private static int countCompletedSteps(Map<String, WatchStatus> stepStatuses) {
        return (int) stepStatuses.values().stream()
            .filter(status -> status == WatchStatus.CONFIRMED)
            .count();
    }
    
    private static Instant findLatestCompletionTime(BasicWatchHandle handle) {
        return handle.getStepResults().values().stream()
            .filter(result -> result != null && result.getCompletedAt() != null)
            .map(StepResult::getCompletedAt)
            .max(Instant::compareTo)
            .orElse(null);
    }
    
    private static Duration calculateTotalDuration(BasicWatchHandle handle) {
        if (handle.getStartedAt() == null) return null;
        
        Instant endTime = handle.isCompleted() ? findLatestCompletionTime(handle) : Instant.now();
        return endTime != null ? Duration.between(handle.getStartedAt(), endTime) : null;
    }
    
    private static int getStepCount(BasicWatchHandle handle) {
        return handle.getStepStatuses().size();
    }
    
    private static StepVisualizationModel.StepExecutionModel extractStepExecution(StepResult result) {
        if (result == null) return null;
        
        return StepVisualizationModel.StepExecutionModel.builder()
            .startedAt(null) // Start time not available in StepResult
            .completedAt(result.getCompletedAt())
            .retryCount(0) // Retry count not available
            .build();
    }
    
    private static StepVisualizationModel.TransactionModel extractTransaction(StepResult result) {
        if (result == null || !result.isSuccessful()) return null;
        
        return StepVisualizationModel.TransactionModel.builder()
            .hash(result.getTransactionHash())
            .blockNumber(null) // Block number not available
            .slot(null) // Slot not available
            .build();
    }
    
    private static StepVisualizationModel.ErrorModel extractStepError(StepResult result) {
        if (result == null || result.isSuccessful() || result.getError() == null) {
            return null;
        }
        
        Throwable error = result.getError();
        return StepVisualizationModel.ErrorModel.builder()
            .type(error.getClass().getSimpleName())
            .message(error.getMessage())
            .occurredAt(Instant.now()) // Use current time if not available
            .build();
    }
}