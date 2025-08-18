package com.bloxbean.cardano.client.watcher.quicktx;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.watcher.chain.ChainContext;

import java.util.Collections;
import java.util.List;

/**
 * UTXO selection strategy that selects a specific UTXO by index.
 * 
 * This strategy is useful when you need to reference a specific output
 * from a previous step, such as the first output (index 0) or change output.
 */
public class IndexedUtxoSelectionStrategy implements UtxoSelectionStrategy {
    private final int index;
    
    /**
     * Create an indexed selection strategy.
     * 
     * @param index the index of the UTXO to select (0-based)
     */
    public IndexedUtxoSelectionStrategy(int index) {
        this.index = index;
    }
    
    @Override
    public List<Utxo> selectUtxos(List<Utxo> availableUtxos, ChainContext context) {
        if (index >= 0 && index < availableUtxos.size()) {
            return List.of(availableUtxos.get(index));
        }
        return Collections.emptyList();
    }
    
    /**
     * Get the index that this strategy selects.
     * 
     * @return the UTXO index
     */
    public int getIndex() {
        return index;
    }
}