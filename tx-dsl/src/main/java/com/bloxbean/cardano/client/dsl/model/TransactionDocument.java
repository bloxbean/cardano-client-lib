package com.bloxbean.cardano.client.dsl.model;

import com.bloxbean.cardano.client.dsl.intention.TxIntention;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root document structure for serialized transactions.
 * Uses unified structure with context support.
 */
public class TransactionDocument {
    
    @JsonProperty("version")
    private String version = "1.0";
    
    // Unified structure - transaction is always a list
    @JsonProperty("transaction")
    private List<TxEntry> transaction = new ArrayList<>();
    
    @JsonProperty("context")
    private ContextSection context;
    
    @JsonProperty("variables")
    private Map<String, Object> variables = new HashMap<>();
    
    public TransactionDocument() {
    }
    
    public TransactionDocument(List<TxIntention> intentions) {
        // Create a single tx entry for backward compatibility
        TxEntry txEntry = new TxEntry();
        txEntry.setTx(new TxSection(intentions));
        this.transaction.add(txEntry);
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public List<TxEntry> getTransaction() {
        return transaction;
    }
    
    public void setTransaction(List<TxEntry> transaction) {
        this.transaction = transaction;
    }
    
    public ContextSection getContext() {
        return context;
    }
    
    public void setContext(ContextSection context) {
        this.context = context;
    }
    
    public Map<String, Object> getVariables() {
        return variables;
    }
    
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
    
    /**
     * Entry in the transaction list
     */
    public static class TxEntry {
        @JsonProperty("tx")
        private TxSection tx;
        
        public TxEntry() {
        }
        
        public TxEntry(TxSection tx) {
            this.tx = tx;
        }
        
        public TxSection getTx() {
            return tx;
        }
        
        public void setTx(TxSection tx) {
            this.tx = tx;
        }
    }
    
    /**
     * Section containing Tx intentions.
     */
    public static class TxSection {
        @JsonProperty("intentions")
        private List<TxIntention> intentions;
        
        public TxSection() {
        }
        
        public TxSection(List<TxIntention> intentions) {
            this.intentions = intentions;
        }
        
        public List<TxIntention> getIntentions() {
            return intentions;
        }
        
        public void setIntentions(List<TxIntention> intentions) {
            this.intentions = intentions;
        }
    }
    
    /**
     * Context section for execution metadata
     */
    public static class ContextSection {
        @JsonProperty("feePayer")
        private String feePayer;
        
        @JsonProperty("collateralPayer")
        private String collateralPayer;
        
        @JsonProperty("utxoSelectionStrategy")
        private String utxoSelectionStrategy;
        
        @JsonProperty("signer")
        private String signer;
        
        public ContextSection() {
        }
        
        public String getFeePayer() {
            return feePayer;
        }
        
        public void setFeePayer(String feePayer) {
            this.feePayer = feePayer;
        }
        
        public String getCollateralPayer() {
            return collateralPayer;
        }
        
        public void setCollateralPayer(String collateralPayer) {
            this.collateralPayer = collateralPayer;
        }
        
        public String getUtxoSelectionStrategy() {
            return utxoSelectionStrategy;
        }
        
        public void setUtxoSelectionStrategy(String utxoSelectionStrategy) {
            this.utxoSelectionStrategy = utxoSelectionStrategy;
        }
        
        public String getSigner() {
            return signer;
        }
        
        public void setSigner(String signer) {
            this.signer = signer;
        }
    }
}