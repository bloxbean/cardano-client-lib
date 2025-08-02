package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.watcher.chain.ChainContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A chain-aware UTXO supplier that combines UTXOs from a base supplier 
 * with pending UTXOs from previous steps in a transaction chain.
 * 
 * This supplier enables transaction chaining by making pending UTXOs from 
 * dependent steps available as inputs for the current transaction, even 
 * before they are confirmed on the blockchain.
 * 
 * Usage:
 * - Step 1: Uses regular UtxoSupplier (no dependencies)  
 * - Step 2: Uses ChainAwareUtxoSupplier that includes outputs from Step 1
 * 
 * This solves the "insufficient funds" issue when step 2 depends on 
 * outputs from step 1 that haven't been confirmed yet.
 */
public class ChainAwareUtxoSupplier implements UtxoSupplier {
    
    private final UtxoSupplier baseSupplier;
    private final ChainContext chainContext;
    private final List<StepOutputDependency> dependencies;
    
    /**
     * Create a chain-aware UTXO supplier.
     * 
     * @param baseSupplier the underlying UTXO supplier to delegate to
     * @param chainContext the chain context containing step results
     * @param dependencies the list of step dependencies for UTXO resolution
     */
    public ChainAwareUtxoSupplier(UtxoSupplier baseSupplier, 
                                  ChainContext chainContext, 
                                  List<StepOutputDependency> dependencies) {
        this.baseSupplier = baseSupplier;
        this.chainContext = chainContext;
        this.dependencies = new ArrayList<>(dependencies);
    }
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        // Get UTXOs from base supplier
        List<Utxo> baseUtxos = baseSupplier.getPage(address, nrOfItems, page, order);
        
        // Add pending UTXOs from dependent steps for this address
        List<Utxo> pendingUtxos = resolvePendingUtxosForAddress(address);
        
        // Combine and return (base UTXOs first, then pending)
        List<Utxo> combinedUtxos = new ArrayList<>(baseUtxos);
        combinedUtxos.addAll(pendingUtxos);
        
        return combinedUtxos;
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        // First try to get from base supplier
        Optional<Utxo> baseResult = baseSupplier.getTxOutput(txHash, outputIndex);
        if (baseResult.isPresent()) {
            return baseResult;
        }
        
        // If not found in base, check pending UTXOs from chain context
        return findPendingUtxo(txHash, outputIndex);
    }
    
    @Override
    public List<Utxo> getAll(String address) {
        // Get all UTXOs from base supplier
        List<Utxo> baseUtxos = baseSupplier.getAll(address);
        
        // Add all pending UTXOs for this address
        List<Utxo> pendingUtxos = resolvePendingUtxosForAddress(address);
        
        // Combine and return
        List<Utxo> allUtxos = new ArrayList<>(baseUtxos);
        allUtxos.addAll(pendingUtxos);
        
        return allUtxos;
    }
    
    @Override
    public boolean isUsedAddress(Address address) {
        // Delegate to base supplier
        return baseSupplier.isUsedAddress(address);
    }
    
    @Override
    public void setSearchByAddressVkh(boolean flag) {
        // Delegate to base supplier
        baseSupplier.setSearchByAddressVkh(flag);
    }
    
    /**
     * Resolve pending UTXOs from dependent steps that belong to the specified address.
     * 
     * @param address the address to filter UTXOs for
     * @return list of pending UTXOs for the address
     */
    private List<Utxo> resolvePendingUtxosForAddress(String address) {
        List<Utxo> pendingUtxos = new ArrayList<>();
        
        for (StepOutputDependency dependency : dependencies) {
            try {
                // Get all UTXOs from the dependency step
                List<Utxo> stepOutputs = chainContext.getStepOutputs(dependency.getStepId());
                
                if (stepOutputs == null || stepOutputs.isEmpty()) {
                    if (!dependency.isOptional()) {
                        System.out.println("⚠️  Warning: Required dependency step '" 
                            + dependency.getStepId() + "' has no outputs yet");
                    }
                    continue;
                }
                
                // Apply selection strategy to get the right UTXOs
                List<Utxo> selectedUtxos = dependency.getSelectionStrategy()
                    .selectUtxos(stepOutputs, chainContext);
                
                // Filter for the specific address
                for (Utxo utxo : selectedUtxos) {
                    if (address.equals(utxo.getAddress())) {
                        pendingUtxos.add(utxo);
                        System.out.println("🔗 Adding pending UTXO from step '" 
                            + dependency.getStepId() + "': " + utxo.getTxHash() 
                            + "#" + utxo.getOutputIndex() + " → " + address);
                    }
                }
                
            } catch (Exception e) {
                if (!dependency.isOptional()) {
                    System.out.println("❌ Failed to resolve required dependency '" 
                        + dependency.getStepId() + "': " + e.getMessage());
                } else {
                    System.out.println("⚠️  Optional dependency '" 
                        + dependency.getStepId() + "' failed: " + e.getMessage());
                }
            }
        }
        
        return pendingUtxos;
    }
    
    /**
     * Find a specific pending UTXO by transaction hash and output index.
     * 
     * @param txHash the transaction hash
     * @param outputIndex the output index
     * @return the UTXO if found in pending outputs
     */
    private Optional<Utxo> findPendingUtxo(String txHash, int outputIndex) {
        for (StepOutputDependency dependency : dependencies) {
            try {
                List<Utxo> stepOutputs = chainContext.getStepOutputs(dependency.getStepId());
                
                for (Utxo utxo : stepOutputs) {
                    if (txHash.equals(utxo.getTxHash()) && outputIndex == utxo.getOutputIndex()) {
                        return Optional.of(utxo);
                    }
                }
            } catch (Exception e) {
                // Continue searching in other dependencies
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Get the base supplier that this chain-aware supplier wraps.
     * 
     * @return the base UTXO supplier
     */
    public UtxoSupplier getBaseSupplier() {
        return baseSupplier;
    }
    
    /**
     * Get the chain context used for dependency resolution.
     * 
     * @return the chain context
     */
    public ChainContext getChainContext() {
        return chainContext;
    }
    
    /**
     * Get the list of dependencies this supplier resolves.
     * 
     * @return the list of step output dependencies
     */
    public List<StepOutputDependency> getDependencies() {
        return new ArrayList<>(dependencies);
    }
}