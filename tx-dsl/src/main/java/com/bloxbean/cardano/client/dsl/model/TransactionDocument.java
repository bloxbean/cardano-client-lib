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
        TxContent content = new TxContent();
        content.setIntentions(intentions);
        txEntry.setTx(content);
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
     * Entry in the transaction list - supports both tx and scriptTx
     */
    public static class TxEntry {
        @JsonProperty("tx")
        private TxContent tx;
        
        @JsonProperty("scriptTx")
        private TxContent scriptTx;
        
        public TxEntry() {
        }
        
        public TxEntry(TxContent tx) {
            this.tx = tx;
        }
        
        public TxContent getTx() {
            return tx;
        }
        
        public void setTx(TxContent tx) {
            this.tx = tx;
        }
        
        public TxContent getScriptTx() {
            return scriptTx;
        }
        
        public void setScriptTx(TxContent scriptTx) {
            this.scriptTx = scriptTx;
        }
    }
    
    /**
     * Transaction content that separates attributes from intentions.
     * Attributes are configuration properties, intentions are actions.
     */
    public static class TxContent {
        // Transaction attributes (configuration)
        @JsonProperty("from")
        private String from;
        
        @JsonProperty("from_wallet")
        private String fromWallet;
        
        @JsonProperty("change_address")
        private String changeAddress;
        
        @JsonProperty("collect_from")
        private List<UtxoInput> collectFrom;
        
        // Transaction intentions (actions)
        @JsonProperty("intentions")
        private List<TxIntention> intentions;
        
        public TxContent() {
        }
        
        public TxContent(List<TxIntention> intentions) {
            this.intentions = intentions;
        }
        
        // Getters and setters for attributes
        public String getFrom() {
            return from;
        }
        
        public void setFrom(String from) {
            this.from = from;
        }
        
        public String getFromWallet() {
            return fromWallet;
        }
        
        public void setFromWallet(String fromWallet) {
            this.fromWallet = fromWallet;
        }
        
        public String getChangeAddress() {
            return changeAddress;
        }
        
        public void setChangeAddress(String changeAddress) {
            this.changeAddress = changeAddress;
        }
        
        public List<UtxoInput> getCollectFrom() {
            return collectFrom;
        }
        
        public void setCollectFrom(List<UtxoInput> collectFrom) {
            this.collectFrom = collectFrom;
        }
        
        // Getters and setters for intentions
        public List<TxIntention> getIntentions() {
            return intentions;
        }
        
        public void setIntentions(List<TxIntention> intentions) {
            this.intentions = intentions;
        }
    }
    
    /**
     * UTXO input reference for serialization
     */
    public static class UtxoInput {
        @JsonProperty("tx_hash")
        private String txHash;
        
        @JsonProperty("output_index")
        private Integer outputIndex;
        
        public UtxoInput() {
        }
        
        public UtxoInput(String txHash, Integer outputIndex) {
            this.txHash = txHash;
            this.outputIndex = outputIndex;
        }
        
        public String getTxHash() {
            return txHash;
        }
        
        public void setTxHash(String txHash) {
            this.txHash = txHash;
        }
        
        public Integer getOutputIndex() {
            return outputIndex;
        }
        
        public void setOutputIndex(Integer outputIndex) {
            this.outputIndex = outputIndex;
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