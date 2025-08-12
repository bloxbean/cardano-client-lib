package com.bloxbean.cardano.client.watcher.visualizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a dependency between steps in a transaction chain.
 * 
 * This model captures how steps depend on each other, specifically
 * focusing on UTXO dependencies and conditional execution flows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependencyModel {
    
    @JsonProperty("from")
    private String fromStepId;
    
    @JsonProperty("to")
    private String toStepId;
    
    @JsonProperty("type")
    private DependencyType type;
    
    @JsonProperty("utxoIndex")
    private Integer utxoIndex;
    
    @JsonProperty("condition")
    private String condition;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("isOptional")
    private Boolean isOptional;
    
    /**
     * Types of dependencies between transaction steps
     */
    public enum DependencyType {
        @JsonProperty("utxo")
        UTXO,
        
        @JsonProperty("sequential")
        SEQUENTIAL,
        
        @JsonProperty("conditional")
        CONDITIONAL,
        
        @JsonProperty("data")
        DATA
    }
}