package com.bloxbean.cardano.client.watcher.visualizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents UTXO flow between steps in a transaction chain.
 * 
 * This model captures how UTXOs are created, consumed, and flow through
 * the different steps of a transaction chain, enabling detailed analysis
 * and visualization of fund movements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UtxoFlowModel {
    
    @JsonProperty("nodes")
    private List<UtxoNode> nodes;
    
    @JsonProperty("edges")
    private List<UtxoEdge> edges;
    
    @JsonProperty("totalInputValue")
    private BigDecimal totalInputValue;
    
    @JsonProperty("totalOutputValue")
    private BigDecimal totalOutputValue;
    
    @JsonProperty("totalFees")
    private BigDecimal totalFees;
    
    /**
     * Represents a step node in the UTXO flow graph
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UtxoNode {
        @JsonProperty("stepId")
        private String stepId;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("inputValue")
        private BigDecimal inputValue;
        
        @JsonProperty("outputValue")
        private BigDecimal outputValue;
        
        @JsonProperty("utxoCount")
        private Integer utxoCount;
        
        // Convenience constructor for simple nodes
        public UtxoNode(String stepId, String name) {
            this.stepId = stepId;
            this.name = name;
        }
    }
    
    /**
     * Represents a UTXO flow edge between steps
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UtxoEdge {
        @JsonProperty("fromStep")
        private String fromStep;
        
        @JsonProperty("toStep")
        private String toStep;
        
        @JsonProperty("label")
        private String label;
        
        @JsonProperty("value")
        private BigDecimal value;
        
        @JsonProperty("utxoIds")
        private List<String> utxoIds;
        
        @JsonProperty("description")
        private String description;
        
        // Convenience constructor for simple edges
        public UtxoEdge(String fromStep, String toStep, String label) {
            this.fromStep = fromStep;
            this.toStep = toStep;
            this.label = label;
        }
    }
}