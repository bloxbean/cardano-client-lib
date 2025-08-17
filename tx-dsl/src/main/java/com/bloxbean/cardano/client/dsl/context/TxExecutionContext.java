package com.bloxbean.cardano.client.dsl.context;

import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;

import java.util.Map;

/**
 * Stores execution context metadata for DSL transactions.
 * This context applies to the entire blockchain transaction composition.
 * MVP version with essential properties only.
 */
public class TxExecutionContext {
    private String feePayer;          // Can be static address or variable like ${treasury}
    private String collateralPayer;   // Can be static address or variable like ${treasury}
    private String utxoSelectionStrategy; // Key that resolves to UtxoSelectionStrategy instance
    private String signer;            // Key that resolves to TxSigner instance
    
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
    
    /**
     * Apply this context to a QuickTxBuilder.TxContext
     * @param txContext the TxContext to apply to
     * @param variables optional variables map for resolving ${variable} syntax
     * @return the updated TxContext
     */
    public QuickTxBuilder.TxContext applyTo(QuickTxBuilder.TxContext txContext, Map<String, Object> variables) {
        // Apply fee payer (can be static address or variable)
        if (feePayer != null) {
            txContext.feePayer(resolveVariable(feePayer, variables));
        }
        
        // Apply collateral payer (can be static address or variable)
        if (collateralPayer != null) {
            txContext.collateralPayer(resolveVariable(collateralPayer, variables));
        }
        
        // Apply UTXO selection strategy (key resolves to runtime instance)
        if (utxoSelectionStrategy != null && TxHandlerRegistry.exists(utxoSelectionStrategy)) {
            UtxoSelectionStrategy strategy = TxHandlerRegistry.get(utxoSelectionStrategy, UtxoSelectionStrategy.class);
            txContext.withUtxoSelectionStrategy(strategy);
        }
        
        // Apply signer (key resolves to runtime instance)
        if (signer != null && TxHandlerRegistry.exists(signer)) {
            TxSigner txSigner = TxHandlerRegistry.get(signer, TxSigner.class);
            txContext.withSigner(txSigner);
        }
        
        return txContext;
    }
    
    /**
     * Resolve variable substitution in values
     * @param value the value that may contain ${variable} syntax
     * @param variables map of variables for substitution
     * @return resolved value
     */
    private String resolveVariable(String value, Map<String, Object> variables) {
        if (value == null || variables == null) {
            return value;
        }
        
        // Handle ${variable} syntax
        if (value.startsWith("${") && value.endsWith("}")) {
            String variableName = value.substring(2, value.length() - 1);
            Object variableValue = variables.get(variableName);
            return variableValue != null ? variableValue.toString() : value;
        }
        
        return value;
    }
}