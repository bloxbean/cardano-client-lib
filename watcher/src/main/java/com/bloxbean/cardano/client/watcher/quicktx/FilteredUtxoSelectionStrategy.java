package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.watcher.chain.ChainContext;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * UTXO selection strategy that filters UTXOs based on a predicate.
 * 
 * This strategy allows fine-grained control over which UTXOs from a previous
 * step are selected for use in the current step.
 */
public class FilteredUtxoSelectionStrategy implements UtxoSelectionStrategy {
    private final Predicate<Utxo> filter;
    
    /**
     * Create a filtered selection strategy.
     * 
     * @param filter the predicate to filter UTXOs
     */
    public FilteredUtxoSelectionStrategy(Predicate<Utxo> filter) {
        this.filter = filter;
    }
    
    @Override
    public List<Utxo> selectUtxos(List<Utxo> availableUtxos, ChainContext context) {
        return availableUtxos.stream()
            .filter(filter)
            .collect(Collectors.toList());
    }
    
    /**
     * Get the filter predicate.
     * 
     * @return the filter predicate
     */
    public Predicate<Utxo> getFilter() {
        return filter;
    }
}