package com.bloxbean.cardano.client.watcher.visualizer.model;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Represents a single step in a transaction chain for visualization purposes.
 * 
 * Contains both static information (ID, description, dependencies) and 
 * dynamic execution state (status, timing, transaction details).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepVisualizationModel {
    
    @JsonProperty("stepId")
    private String stepId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("position")
    private int position;
    
    @JsonProperty("status")
    private WatchStatus status;
    
    @JsonProperty("dependencies")
    private List<String> dependencies;
    
    @JsonProperty("execution")
    private StepExecutionModel execution;
    
    @JsonProperty("transaction")
    private TransactionModel transaction;
    
    @JsonProperty("error")
    private ErrorModel error;
    
    /**
     * Execution timing and retry information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StepExecutionModel {
        @JsonProperty("startedAt")
        private Instant startedAt;
        
        @JsonProperty("completedAt")
        private Instant completedAt;
        
        @JsonProperty("duration")
        private Duration duration;
        
        @JsonProperty("retryCount")
        private int retryCount;
        
        @JsonProperty("retryDelays")
        private List<Duration> retryDelays;
    }
    
    /**
     * Transaction details for completed steps
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionModel {
        @JsonProperty("hash")
        private String hash;
        
        @JsonProperty("blockNumber")
        private Long blockNumber;
        
        @JsonProperty("slot")
        private Long slot;
        
        @JsonProperty("fees")
        private String fees;
        
        @JsonProperty("size")
        private Integer size;
    }
    
    /**
     * Error information for failed steps
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorModel {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("occurredAt")
        private Instant occurredAt;
        
        @JsonProperty("retryable")
        private Boolean retryable;
        
        @JsonProperty("stackTrace")
        private String stackTrace;
    }
}