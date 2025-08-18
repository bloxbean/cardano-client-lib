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
 * Represents error information for a transaction chain.
 * 
 * This model aggregates error information across all steps in a chain,
 * providing a high-level view of what went wrong and when.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorStateModel {
    
    @JsonProperty("hasErrors")
    private boolean hasErrors;
    
    @JsonProperty("failedStepId")
    private String failedStepId;
    
    @JsonProperty("errorType")
    private String errorType;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    @JsonProperty("occurredAt")
    private Instant occurredAt;
    
    @JsonProperty("stackTrace")
    private String stackTrace;
    
    @JsonProperty("retryCount")
    private Integer retryCount;
    
    @JsonProperty("isRetryable")
    private Boolean isRetryable;
    
    @JsonProperty("errorHistory")
    private List<ErrorRecord> errorHistory;
    
    /**
     * Create an error state model for a failed chain
     */
    public static ErrorStateModel failed(String stepId, String errorType, String message, Instant occurredAt) {
        return ErrorStateModel.builder()
            .hasErrors(true)
            .failedStepId(stepId)
            .errorType(errorType)
            .errorMessage(message)
            .occurredAt(occurredAt)
            .build();
    }
    
    /**
     * Create an error state model for a successful chain
     */
    public static ErrorStateModel noErrors() {
        return ErrorStateModel.builder()
            .hasErrors(false)
            .build();
    }
    
    /**
     * Individual error record for error history
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorRecord {
        @JsonProperty("stepId")
        private String stepId;
        
        @JsonProperty("errorType")
        private String errorType;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("occurredAt")
        private Instant occurredAt;
        
        @JsonProperty("resolved")
        private Boolean resolved;
    }
}