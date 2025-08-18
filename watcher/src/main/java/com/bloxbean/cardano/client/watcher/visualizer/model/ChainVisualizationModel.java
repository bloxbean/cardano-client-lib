package com.bloxbean.cardano.client.watcher.visualizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Complete abstract representation of a transaction chain for visualization purposes.
 * 
 * This model is platform-agnostic and can be serialized to JSON for external tool integration,
 * or used by different renderers (ASCII, SVG, HTML) to generate visualizations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChainVisualizationModel {
    
    @JsonProperty("metadata")
    private ChainMetadata metadata;
    
    @JsonProperty("execution")
    private ExecutionStateModel execution;
    
    @JsonProperty("steps")
    private List<StepVisualizationModel> steps;
    
    @JsonProperty("dependencies")
    private List<DependencyModel> dependencies;
    
    @JsonProperty("utxoFlow")
    private UtxoFlowModel utxoFlow;
    
    @JsonProperty("errorState")
    private ErrorStateModel errorState;
    
    /**
     * Chain metadata information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChainMetadata {
        @JsonProperty("chainId")
        private String chainId;
        
        @JsonProperty("description") 
        private String description;
        
        @JsonProperty("version")
        @Builder.Default
        private String version = "1.0";
        
        @JsonProperty("timestamp")
        private Instant timestamp;
        
        @JsonProperty("totalSteps")
        private int totalSteps;
    }
}