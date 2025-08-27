package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Intention for collecting UTXOs as inputs for a transaction.
 * Stores only UTXO keys (txHash + outputIndex) for efficient serialization.
 * Full UTXO details will be fetched from UtxoSupplier when building the transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectFromIntention implements TxIntention {
    
    /**
     * UTXO input references (txHash + outputIndex)
     */
    @JsonProperty("inputs")
    private List<UtxoInput> inputs;
    
    @JsonProperty("collection_type") 
    private String collectionType; // "list" or "set"
    
    /**
     * Represents a UTXO input reference for serialization
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UtxoInput {
        @JsonProperty("tx_hash")
        private String txHash;
        
        @JsonProperty("output_index")
        private Integer outputIndex;
        
        public static UtxoInput fromUtxo(Utxo utxo) {
            return new UtxoInput(utxo.getTxHash(), utxo.getOutputIndex());
        }
        
        public TransactionInput toTransactionInput() {
            return TransactionInput.builder()
                .transactionId(txHash)
                .index(outputIndex)
                .build();
        }
    }
    
    public CollectFromIntention(List<Utxo> utxos) {
        this.inputs = utxos.stream()
            .map(UtxoInput::fromUtxo)
            .collect(Collectors.toList());
        this.collectionType = "list";
    }
    
    public CollectFromIntention(Set<Utxo> utxos) {
        this.inputs = utxos.stream()
            .map(UtxoInput::fromUtxo)
            .collect(Collectors.toList());
        this.collectionType = "set";
    }
    
    @Override
    public String getType() {
        return "collect_from";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Note: At this point we only have UTXO references (txHash + outputIndex).
        // We need to resolve these to full UTXOs using UtxoSupplier when building the transaction.
        // For now, we store the references and handle full UTXO resolution at build time
        
        // Convert UTXO inputs to TransactionInputs for now
        // TODO: Enhance to fetch full UTXOs from UtxoSupplier in TxDslBuilder
        List<TransactionInput> transactionInputs = inputs.stream()
            .map(UtxoInput::toTransactionInput)
            .collect(Collectors.toList());
        
        // For immediate implementation, we need to work with what we have
        // This will need enhancement in TxDslBuilder to resolve UTXOs
        // For now, just store the inputs - actual UTXO resolution happens at build time
        // Note: collectFrom with TransactionInputs is not directly available on Tx
        // This functionality will need to be handled at the QuickTxBuilder level
    }
    
    /**
     * Get transaction inputs for immediate use
     */
    public List<TransactionInput> getTransactionInputs() {
        return inputs.stream()
            .map(UtxoInput::toTransactionInput)
            .collect(Collectors.toList());
    }
}