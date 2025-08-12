package com.bloxbean.cardano.client.watcher.visualizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents the current execution state of a transaction chain.
 * 
 * This model captures timing information, progress, and overall status
 * that is useful for progress visualization and monitoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionStateModel {
    
    @JsonProperty("status")
    private ExecutionStatus status;
    
    @JsonProperty("progress")
    private double progress;
    
    @JsonProperty("startedAt")
    private Instant startedAt;
    
    @JsonProperty("completedAt")
    private Instant completedAt;
    
    @JsonProperty("currentStep")
    private String currentStep;
    
    @JsonProperty("estimatedCompletion")
    private Instant estimatedCompletion;
    
    @JsonProperty("totalDuration")
    private Duration totalDuration;
    
    @JsonProperty("stepsCompleted")
    private int stepsCompleted;
    
    @JsonProperty("stepsTotal")
    private int stepsTotal;
    
    /**
     * Execution status enumeration for high-level chain state
     */
    public enum ExecutionStatus {
        @JsonProperty("pending")
        PENDING,
        
        @JsonProperty("running") 
        RUNNING,
        
        @JsonProperty("completed")
        COMPLETED,
        
        @JsonProperty("failed")
        FAILED,
        
        @JsonProperty("cancelled")
        CANCELLED
    }
}